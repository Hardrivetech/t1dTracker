package com.hardrivetech.t1dtracker.data

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.core.content.edit
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hardrivetech.t1dtracker.AppLog
import com.hardrivetech.t1dtracker.EncryptionUtil
import com.hardrivetech.t1dtracker.TelemetryUtil
import java.io.File
import java.io.FileInputStream
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(entities = [InsulinEntry::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun insulinDao(): InsulinDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from schema version 1 -> 2: add the optional `notes` column
        // to `insulin_entries`. This is non-destructive and will add a NULLabel
        // TEXT column for older databases.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE insulin_entries ADD COLUMN notes TEXT")
            }
        }

        /**
         * Migrate an existing plaintext DB to an encrypted SQLCipher DB using a
         * keystore-protected passphrase. This performs an export-import into a
         * temporary encrypted DB and then atomically replaces the original DB
         * files on disk. Caller should run this off the main thread.
         *
         * Returns true on success.
         */
        suspend fun migratePlaintextToEncrypted(context: Context, currentDb: AppDatabase): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    val shared = context.getSharedPreferences("t1d_crypto", Context.MODE_PRIVATE)
                    val allowLegacy = shared.getBoolean("allow_legacy_wrapped_encryption", false)
                    val keystoreAvailable = EncryptionUtil.isKeystoreUsable(context)
                    if (!keystoreAvailable || (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && !allowLegacy)) {
                        AppLog.w(
                            "AppDatabase",
                            "Keystore not usable or device API < M (and legacy not allowed); cannot migrate to encrypted DB"
                        )
                        return@withContext false
                    }

                    val dbFile = context.getDatabasePath("t1d_db")
                    fun isPlainSqlite(f: File): Boolean {
                        return try {
                            if (!f.exists() || f.length() < 16) return false
                            FileInputStream(f).use { fis ->
                                val header = ByteArray(16)
                                val read = fis.read(header)
                                if (read < 16) return false
                                String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
                            }
                        } catch (_: Exception) {
                            false
                        }
                    }

                    if (!dbFile.exists() || !isPlainSqlite(dbFile)) {
                        AppLog.i("AppDatabase", "No plaintext DB detected; migration not needed")
                        return@withContext false
                    }

                    // Export current data
                    val entries = try {
                        currentDb.insulinDao().getAll()
                    } catch (e: Exception) {
                        AppLog.e("AppDatabase", "Failed to read existing DB: ${e.message}", e)
                        TelemetryUtil.recordException(e, "migrate: read existing DB failed")
                        return@withContext false
                    }

                    // Generate a fresh random passphrase and persist encrypted in prefs
                    val prefs = context.getSharedPreferences("t1d_crypto", Context.MODE_PRIVATE)
                    val passBytes = ByteArray(32)
                    SecureRandom().nextBytes(passBytes)
                    val passB64 = Base64.encodeToString(passBytes, Base64.NO_WRAP)
                    val enc = EncryptionUtil.encryptString(context, passB64)
                    prefs.edit { putString("db_pass_enc", enc) }

                    // Initialize SQLCipher and open a temporary encrypted DB
                    try {
                        SQLiteDatabase.loadLibs(context)
                    } catch (_: Exception) {
                        // library init may fail; abort
                        AppLog.e("AppDatabase", "SQLCipher libs failed to load")
                        return@withContext false
                    }

                    val supportFactory = SupportFactory(passBytes)
                    try { passBytes.fill(0) } catch (_: Exception) { }

                    val tempName = "t1d_db_encrypted_tmp"
                    val tempDb = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, tempName)
                        .openHelperFactory(supportFactory)
                        .addMigrations(MIGRATION_1_2)
                        .build()

                    // Import entries into the temp encrypted DB
                    try {
                        // Use the suspend-friendly withTransaction so we can call suspend DAO methods
                        tempDb.withTransaction {
                            val dao = tempDb.insulinDao()
                            for (e in entries) {
                                val toInsert = e.copy(id = 0L)
                                dao.insert(toInsert)
                            }
                        }
                    } catch (e: Exception) {
                        AppLog.e("AppDatabase", "Failed to import into encrypted DB: ${e.message}", e)
                        TelemetryUtil.recordException(e, "migrate: import failed")
                        try { tempDb.close() } catch (_: Exception) { }
                        return@withContext false
                    }

                    // Close DBs and swap files
                    try { currentDb.close() } catch (_: Exception) { }
                    try { tempDb.close() } catch (_: Exception) { }

                    val origFiles = listOf(
                        dbFile,
                        File(dbFile.absolutePath + "-shm"),
                        File(dbFile.absolutePath + "-wal")
                    )
                    val tempFile = context.getDatabasePath(tempName)
                    val tempFiles = listOf(
                        tempFile,
                        File(tempFile.absolutePath + "-shm"),
                        File(tempFile.absolutePath + "-wal")
                    )

                    // Back up originals before replacing them
                    try {
                        val backupDir = File(context.filesDir, "db_migration_backups")
                        if (!backupDir.exists()) backupDir.mkdirs()
                        val ts = System.currentTimeMillis()
                        for (f in origFiles) {
                            if (f.exists()) {
                                try {
                                    val dst = File(backupDir, "${f.name}.$ts.bak")
                                    f.copyTo(dst, overwrite = true)
                                } catch (e: Exception) {
                                    AppLog.w("AppDatabase", "Failed to copy ${f.name} to backup: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        AppLog.w("AppDatabase", "Unable to create DB backups before migration: ${e.message}")
                    }

                    // Remove originals then move temp into place
                    try {
                        for (f in origFiles) if (f.exists()) f.delete()
                        for ((src, dst) in tempFiles.zip(origFiles)) {
                            if (src.exists()) {
                                if (!src.renameTo(dst)) {
                                    // fallback to copy
                                    src.copyTo(dst, overwrite = true)
                                    src.delete()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        AppLog.e("AppDatabase", "Failed to atomically replace DB files: ${e.message}", e)
                        TelemetryUtil.recordException(e, "migrate: file replace failed")
                        return@withContext false
                    }

                    // Clear singleton so next getInstance opens the encrypted DB
                    try { INSTANCE = null } catch (_: Exception) { }

                    AppLog.i("AppDatabase", "Migration to encrypted DB completed")
                    return@withContext true
                } catch (e: Exception) {
                    AppLog.e("AppDatabase", "migratePlaintextToEncrypted failed: ${e.message}", e)
                    TelemetryUtil.recordException(e, "migratePlaintextToEncrypted failed")
                    return@withContext false
                }
            }

            // migration backup helpers moved to DBMigrationApi.kt to avoid companion/static call issues
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Retrieve or generate an encrypted DB passphrase stored encrypted by keystore.
                // Only enable encrypted DB when the platform keystore is usable; otherwise
                // fall back to plaintext Room to avoid persisting raw keys insecurely.
                val prefs = context.getSharedPreferences("t1d_crypto", Context.MODE_PRIVATE)
                val encPass = prefs.getString("db_pass_enc", null)
                var passphraseBytes: ByteArray? = null
                // Only enable SQLCipher by default on devices API >= M (23).
                // For pre-M devices, using wrapped keys is less secure; do not enable by default.
                // Only enable SQLCipher by default on devices API >= M (23).
                // Allow override if the user explicitly opted into legacy wrapped-key encryption.
                val shared = context.getSharedPreferences("t1d_crypto", Context.MODE_PRIVATE)
                val allowLegacy = shared.getBoolean("allow_legacy_wrapped_encryption", false)
                val keystoreOk = EncryptionUtil.isKeystoreUsable(context) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M || allowLegacy)
                if (keystoreOk) {
                    if (encPass != null) {
                        passphraseBytes = try {
                            EncryptionUtil.decryptAndDecodeBase64(context, encPass)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (passphraseBytes == null) {
                        // Generate a 32-byte random passphrase and persist its Base64-encrypted form
                        val bytes = ByteArray(32)
                        SecureRandom().nextBytes(bytes)
                        val passphraseBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val enc = EncryptionUtil.encryptString(context, passphraseBase64)
                        prefs.edit { putString("db_pass_enc", enc) }
                        passphraseBytes = bytes
                    }
                } else {
                    AppLog.w("AppDatabase", "Keystore not usable; opening plaintext DB")
                }

                val instance: AppDatabase = try {
                    // If a plain SQLite DB already exists, use normal Room with migrations
                    val dbFile = context.getDatabasePath("t1d_db")
                    fun isPlainSqlite(f: File): Boolean {
                        return try {
                            if (!f.exists() || f.length() < 16) return false
                            FileInputStream(f).use { fis ->
                                val header = ByteArray(16)
                                val read = fis.read(header)
                                if (read < 16) return false
                                String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
                            }
                        } catch (_: Exception) {
                            false
                        }
                    }

                    if (dbFile.exists() && isPlainSqlite(dbFile)) {
                        // Existing DB is plaintext SQLite; use normal Room with migrations
                        Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "t1d_db")
                            .addMigrations(MIGRATION_1_2)
                            .build()
                    } else {
                        // If we have a passphrase (keystore usable), open encrypted DB; otherwise
                        // fall back to plaintext Room with migrations.
                        if (passphraseBytes != null) {
                            try {
                                // Initialize SQLCipher native libs and open encrypted DB
                                SQLiteDatabase.loadLibs(context)
                                val supportFactory = SupportFactory(passphraseBytes)
                                try { passphraseBytes.fill(0) } catch (_: Exception) { }
                                val builder = Room.databaseBuilder(
                                    context.applicationContext,
                                    AppDatabase::class.java,
                                    "t1d_db"
                                )
                                builder.openHelperFactory(supportFactory)
                                builder.addMigrations(MIGRATION_1_2).build()
                            } catch (_: Exception) {
                                // Failed to initialize SQLCipher; fall back to plaintext
                                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "t1d_db")
                                    .addMigrations(MIGRATION_1_2)
                                    .build()
                            }
                        } else {
                            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "t1d_db")
                                .addMigrations(MIGRATION_1_2)
                                .build()
                        }
                    }
                } catch (_: Exception) {
                    // Any error: fallback to plain Room with migrations
                    Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "t1d_db")
                        .addMigrations(MIGRATION_1_2)
                        .build()
                }

                INSTANCE = instance
                instance
            }
        }
    }
}

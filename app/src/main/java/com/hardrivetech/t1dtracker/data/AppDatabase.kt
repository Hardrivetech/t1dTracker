package com.hardrivetech.t1dtracker.data

import android.content.Context
import android.os.Build
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hardrivetech.t1dtracker.AppLog
import com.hardrivetech.t1dtracker.EncryptionUtil
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
@Database(entities = [InsulinEntry::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun insulinDao(): InsulinDao

    companion object {
        // Migration from schema version 1 -> 2: add the optional `notes` column
        // to `insulin_entries`. This is non-destructive and will add a NULLable
        // TEXT column for older databases.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE insulin_entries ADD COLUMN notes TEXT")
            }
        }

        private fun isPlainSqlite(dbFile: File): Boolean {
            if (!dbFile.exists()) return false
            return try {
                FileInputStream(dbFile).use { fis ->
                    val header = ByteArray(16)
                    if (fis.read(header) != 16) {
                        false
                    } else {
                        String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
                    }
                }
            } catch (_: IOException) {
                false
            }
        }

        // persistNewPassphrase moved to AppDatabaseHelpers to reduce companion size
        private fun tryLoadSqlCipher(context: Context): Boolean {
            return try {
                SQLiteDatabase.loadLibs(context)
                true
            } catch (e: UnsatisfiedLinkError) {
                AppLog.e("AppDatabase", "SQLCipher libs failed to load")
                false
            } catch (e: SecurityException) {
                AppLog.e("AppDatabase", "SQLCipher libs failed to load: ${e.message}")
                false
            }
        }

        private fun createTempEncryptedDb(context: Context, passBytes: ByteArray, tempName: String): AppDatabase? {
            return try {
                val supportFactory = SupportFactory(passBytes)
                val db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, tempName)
                    .openHelperFactory(supportFactory)
                    .addMigrations(MIGRATION_1_2)
                    .build()
                passBytes.fill(0)
                db
            } catch (e: UnsatisfiedLinkError) {
                AppLog.e("AppDatabase", "Failed to create encrypted temp DB: ${e.message}")
                null
            } catch (e: SecurityException) {
                AppLog.e("AppDatabase", "Failed to create encrypted temp DB: ${e.message}")
                null
            }
        }

        // importEntriesToDb moved to AppDatabaseMigrationHelpers to reduce companion size

        private fun backupAndReplaceFiles(context: Context, origFiles: List<File>, tempFiles: List<File>): Boolean {
            createDbMigrationBackups(context, origFiles)
            return replaceDbFiles(origFiles, tempFiles)
        }
        suspend fun migratePlaintextToEncrypted(context: Context, currentDb: AppDatabase): Boolean {
            return withContext(Dispatchers.IO) {
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
                if (!dbFile.exists() || !isPlainSqlite(dbFile)) {
                    AppLog.i("AppDatabase", "No plaintext DB detected; migration not needed")
                    return@withContext false
                }

                val entries = readEntriesSafe(currentDb) ?: return@withContext false

                val passBytes = persistNewPassphrase(context) ?: return@withContext false

                if (!tryLoadSqlCipher(context)) return@withContext false

                val tempName = "t1d_db_encrypted_tmp"
                val tempDb = createTempEncryptedDb(context, passBytes, tempName) ?: return@withContext false

                val imported = importEntriesToDb(tempDb, entries)
                if (!imported) return@withContext false

                try {
                    currentDb.close()
                } catch (e: IOException) {
                    AppLog.w("AppDatabase", "Failed closing currentDb: ${e.message}")
                }
                try {
                    tempDb.close()
                } catch (e: IOException) {
                    AppLog.w("AppDatabase", "Failed closing tempDb: ${e.message}")
                }

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

                val replaced = backupAndReplaceFiles(context, origFiles, tempFiles)
                if (!replaced) return@withContext false

                INSTANCE = null

                AppLog.i("AppDatabase", "Migration to encrypted DB completed")
                return@withContext true
            }

            // migration backup helpers moved to DBMigrationApi.kt to avoid companion/static call issues
        }
        private fun keystoreEnabled(context: Context): Boolean {
            val shared = context.getSharedPreferences("t1d_crypto", Context.MODE_PRIVATE)
            val allowLegacy = shared.getBoolean("allow_legacy_wrapped_encryption", false)
            return EncryptionUtil.isKeystoreUsable(context) &&
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M || allowLegacy)
        }

        private fun loadOrCreatePassphraseForKeystore(context: Context): ByteArray? {
            val prefs = context.getSharedPreferences("t1d_crypto", Context.MODE_PRIVATE)
            val encPass = prefs.getString("db_pass_enc", null)
            var passphraseBytes: ByteArray? = null
            if (encPass != null) {
                passphraseBytes = EncryptionUtil.decryptAndDecodeBase64(context, encPass)
            }
            if (passphraseBytes == null) {
                // Delegate to existing helper that generates and persists a passphrase
                passphraseBytes = persistNewPassphrase(context)
            }
            return passphraseBytes
        }

        private fun buildAppDatabase(context: Context, passphraseBytes: ByteArray?): AppDatabase {
            val dbFile = context.getDatabasePath("t1d_db")
            var instance: AppDatabase? = null

            if (dbFile.exists() && isPlainSqlite(dbFile)) {
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "t1d_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
            } else {
                if (passphraseBytes != null) {
                    try {
                        SQLiteDatabase.loadLibs(context)
                        val supportFactory = SupportFactory(passphraseBytes)
                        val builder = Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "t1d_db"
                        )
                        builder.openHelperFactory(supportFactory)
                        builder.addMigrations(MIGRATION_1_2)
                        instance = builder.build()
                        passphraseBytes.fill(0)
                    } catch (e: UnsatisfiedLinkError) {
                        AppLog.w("AppDatabase", "SQLCipher libs failed to load: ${e.message}")
                    } catch (e: SecurityException) {
                        AppLog.w("AppDatabase", "Security exception while initializing SQLCipher: ${e.message}")
                    }
                }

                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "t1d_db"
                    )
                        .addMigrations(MIGRATION_1_2)
                        .build()
                }
            }

            return instance
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphraseBytes = if (keystoreEnabled(context)) {
                    loadOrCreatePassphraseForKeystore(context)
                } else {
                    AppLog.w("AppDatabase", "Keystore not usable; opening plaintext DB")
                    null
                }

                val instance = buildAppDatabase(context, passphraseBytes)
                INSTANCE = instance
                instance
            }
        }
    }
}

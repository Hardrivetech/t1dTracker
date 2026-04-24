package com.hardrivetech.t1dtracker

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.hardrivetech.t1dtracker.data.InsulinEntry
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

fun escapeHtml(s: String): String {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .replace("'", "&#39;")
}

fun buildHtml(entries: List<InsulinEntry>): String {
    val sb = StringBuilder()
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    sb.append("<!doctype html><html><head>")
    sb.append("<meta charset=\"utf-8\"><title>t1dTracker export</title>")
    sb.append("</head><body>")
    sb.append("<h1>t1dTracker export</h1>")
    sb.append("<table border=\"1\" cellpadding=\"4\">")
    sb.append("<tr><th>id</th><th>timestamp</th><th>datetime</th>")
    sb.append("<th>carbs</th><th>icr</th><th>currentGlucose</th>")
    sb.append("<th>targetGlucose</th><th>isf</th><th>carbDose</th>")
    sb.append("<th>correctionDose</th><th>totalDose</th><th>notes</th></tr>")
    for (e in entries) {
        val date = sdf.format(java.util.Date(e.timestamp))
        val notesEscaped = escapeHtml(e.notes ?: "")
        sb.append("<tr>")
        sb.append("<td>").append(e.id).append("</td>")
        sb.append("<td>").append(e.timestamp).append("</td>")
        sb.append("<td>").append(escapeHtml(date)).append("</td>")
        sb.append("<td>").append(e.carbs).append("</td>")
        sb.append("<td>").append(e.icr).append("</td>")
        sb.append("<td>").append(e.currentGlucose).append("</td>")
        sb.append("<td>").append(e.targetGlucose).append("</td>")
        sb.append("<td>").append(e.isf).append("</td>")
        sb.append("<td>").append(e.carbDose).append("</td>")
        sb.append("<td>").append(e.correctionDose).append("</td>")
        sb.append("<td>").append(e.totalDose).append("</td>")
        sb.append("<td>").append(notesEscaped).append("</td>")
        sb.append("</tr>")
    }
    sb.append("</table></body></html>")
    return sb.toString()
}

fun buildJson(entries: List<InsulinEntry>): String {
    val sb = StringBuilder()
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    sb.append("[")
    entries.forEachIndexed { idx, e ->
        if (idx > 0) sb.append(',')
        val date = sdf.format(java.util.Date(e.timestamp))
        val notesEscaped = (e.notes ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
        sb.append("{")
        sb.append("\"id\":${e.id},")
        sb.append("\"timestamp\":${e.timestamp},")
        sb.append("\"datetime\":\"").append(date).append("\",")
        sb.append("\"carbs\":${e.carbs},")
        sb.append("\"icr\":${e.icr},")
        sb.append("\"currentGlucose\":${e.currentGlucose},")
        sb.append("\"targetGlucose\":${e.targetGlucose},")
        sb.append("\"isf\":${e.isf},")
        sb.append("\"carbDose\":${e.carbDose},")
        sb.append("\"correctionDose\":${e.correctionDose},")
        sb.append("\"totalDose\":${e.totalDose},")
        sb.append("\"notes\":\"").append(notesEscaped).append("\"")
        sb.append("}")
    }
    sb.append("]")
    return sb.toString()
}

fun buildPlainText(entries: List<InsulinEntry>): String {
    val sb = StringBuilder()
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    for (e in entries) {
        sb.append("Date: ").append(sdf.format(java.util.Date(e.timestamp))).append("\n")
        sb.append("Carbs: ").append(e.carbs).append(" g\n")
        sb.append("ICR: ").append(e.icr).append("\n")
        sb.append("Current: ").append(e.currentGlucose).append(" mg/dL\n")
        sb.append("Target: ").append(e.targetGlucose).append(" mg/dL\n")
        sb.append("ISF: ").append(e.isf).append("\n")
        sb.append("Carb dose: ").append(e.carbDose).append(" U\n")
        sb.append("Correction dose: ").append(e.correctionDose).append(" U\n")
        sb.append("Total dose: ").append(e.totalDose).append(" U\n")
        if (!e.notes.isNullOrBlank()) sb.append("Notes: ").append(e.notes).append("\n")
        sb.append("\n")
    }
    return sb.toString()
}

fun writeCacheAndShareFile(
    context: Context,
    filename: String,
    content: String,
    mimeType: String,
    subject: String
) {
    try {
        val file = File(context.cacheDir, filename)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    } catch (ex: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "Export failed: ${ex.message}", Toast.LENGTH_LONG).show()
    } catch (ex: SecurityException) {
        Toast.makeText(context, "Export failed: ${ex.message}", Toast.LENGTH_LONG).show()
    }
}

fun writeEncryptedCacheAndShareFile(
    context: Context,
    filename: String,
    contentPlain: String,
    mimeType: String,
    subject: String
) {
    try {
        val enc = EncryptionUtil.encryptString(context, contentPlain)
        val file = File(context.cacheDir, filename)
        file.writeText(enc)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    } catch (ex: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "Encrypted export failed: ${ex.message}", Toast.LENGTH_LONG).show()
    } catch (ex: SecurityException) {
        Toast.makeText(context, "Encrypted export failed: ${ex.message}", Toast.LENGTH_LONG).show()
    }
}

fun shareText(context: Context, content: String, subject: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    } catch (ex: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "Share failed: ${ex.message}", Toast.LENGTH_LONG).show()
    } catch (ex: SecurityException) {
        Toast.makeText(context, "Share failed: ${ex.message}", Toast.LENGTH_LONG).show()
    }
}

fun saveToDownloads(context: Context, filename: String, content: String, mimeType: String) {
    try {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val uri = resolver.insert(collection, values) ?: throw IOException("Failed to create MediaStore record")
        resolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("Failed to open output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        Toast.makeText(context, "Saved to Downloads: $filename", Toast.LENGTH_LONG).show()
    } catch (ex: IOException) {
        try {
            val f = File(context.cacheDir, filename)
            f.writeText(content)
            Toast.makeText(context, "Saved to app cache (Downloads failed): ${ex.message}", Toast.LENGTH_LONG).show()
        } catch (e2: IOException) {
            AppLog.w("HistoryExportHelpers", "Save fallback failed: ${e2.message}")
            TelemetryUtil.recordException(e2, "saveToDownloads fallback failed")
            Toast.makeText(context, "Save failed: ${e2.message}", Toast.LENGTH_LONG).show()
        }
    } catch (ex: SecurityException) {
        try {
            val f = File(context.cacheDir, filename)
            f.writeText(content)
            Toast.makeText(context, "Saved to app cache (Downloads failed): ${ex.message}", Toast.LENGTH_LONG).show()
        } catch (e2: IOException) {
            AppLog.w("HistoryExportHelpers", "Save fallback failed (security): ${e2.message}")
            TelemetryUtil.recordException(e2, "saveToDownloads fallback failed (security)")
            Toast.makeText(context, "Save failed: ${e2.message}", Toast.LENGTH_LONG).show()
        }
    }
}

fun savePublicLegacy(context: Context, filename: String, content: String) {
    try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val outFile = File(downloadsDir, filename)
        outFile.writeText(content)
        try {
            val uri = android.net.Uri.fromFile(outFile)
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
        } catch (e: SecurityException) {
            AppLog.w("HistoryExportHelpers", "Broadcast failed: ${e.message}")
            TelemetryUtil.recordException(e, "savePublicLegacy broadcast failed")
        }
        Toast.makeText(context, "Saved to Downloads: $filename", Toast.LENGTH_LONG).show()
    } catch (ex: IOException) {
        Toast.makeText(context, "Failed to save to Downloads: ${ex.message}", Toast.LENGTH_LONG).show()
    } catch (ex: SecurityException) {
        Toast.makeText(context, "Failed to save to Downloads: ${ex.message}", Toast.LENGTH_LONG).show()
    }
}

fun buildCsv(entries: List<InsulinEntry>): String {
    val sb = StringBuilder()
    sb.append("id,timestamp,datetime,carbs,icr,currentGlucose,")
    sb.append("targetGlucose,isf,carbDose,correctionDose,totalDose,notes\n")
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    for (e in entries) {
        val date = sdf.format(java.util.Date(e.timestamp))
        val notesEscaped = e.notes?.replace("\"", "\"\"") ?: ""
        sb.append("${e.id},${e.timestamp},\"${date}\",")
        sb.append("${e.carbs},${e.icr},${e.currentGlucose},${e.targetGlucose},${e.isf},")
        sb.append("${e.carbDose},${e.correctionDose},${e.totalDose},\"${notesEscaped}\"\n")
    }
    return sb.toString()
}

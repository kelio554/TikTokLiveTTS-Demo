package net.mkhalil.taktik.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mkhalil.taktik.data.db.entities.EventEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    private val tsFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    suspend fun exportSession(
        context: Context,
        sessionId: Long,
        hostHandle: String,
        events: List<EventEntity>,
    ): Intent = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "taktik_session_${sessionId}_${hostHandle}.csv")

        file.bufferedWriter().use { w ->
            w.write("timestamp,type,viewer_user_id,gift_id,gift_name,gift_count,gift_coins,like_count,comment_text")
            w.newLine()
            for (e in events) {
                w.write(tsFormat.format(Date(e.created_at))); w.write(",")
                w.write(e.type); w.write(",")
                w.write(e.viewer_user_id.orEmpty()); w.write(",")
                w.write(e.gift_id?.toString().orEmpty()); w.write(",")
                w.write(csvEscape(e.gift_name)); w.write(",")
                w.write(e.gift_count?.toString().orEmpty()); w.write(",")
                w.write(e.gift_coins?.toString().orEmpty()); w.write(",")
                w.write(e.like_count?.toString().orEmpty()); w.write(",")
                w.write(csvEscape(e.comment_text))
                w.newLine()
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "TakTik session @$hostHandle")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun csvEscape(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        val needsQuote = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuote) return s
        return "\"" + s.replace("\"", "\"\"") + "\""
    }
}

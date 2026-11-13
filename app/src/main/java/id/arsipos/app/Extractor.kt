package id.arsipos.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

data class Parsed(
    val type: String,
    val number: String,
    val date: String,
    val subject: String,
    val institution: String
)

object Extractor {
    private var pdfInit = false

    suspend fun text(ctx: Context, uri: Uri, mime: String, name: String): String =
        withContext(Dispatchers.IO) {
            val n = name.lowercase()
            try {
                when {
                    mime == "application/pdf" || n.endsWith(".pdf") -> pdf(ctx, uri)
                    mime.contains("wordprocessingml") || n.endsWith(".docx") -> docx(ctx, uri)
                    mime.startsWith("image/") -> ocr(InputImage.fromFilePath(ctx, uri))
                    else -> ""
                }
            } catch (e: Exception) {
                ""
            }
        }

    private suspend fun ocr(img: InputImage): String {
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            rec.process(img).await().text
        } finally {
            rec.close()
        }
    }

    private suspend fun pdf(ctx: Context, uri: Uri): String {
        if (!pdfInit) {
            PDFBoxResourceLoader.init(ctx.applicationContext)
            pdfInit = true
        }
        val t = try {
            ctx.contentResolver.openInputStream(uri)!!.use { input ->
                PDDocument.load(input).use { d ->
                    val s = PDFTextStripper()
                    s.startPage = 1
                    s.endPage = minOf(d.numberOfPages, 10)
                    s.getText(d)
                }
            }
        } catch (e: Exception) {
            ""
        }
        if (t.trim().length > 30) return t
        return ocrPdf(ctx, uri)
    }

    private suspend fun ocrPdf(ctx: Context, uri: Uri): String {
        val tmp = File.createTempFile("scan", ".pdf", ctx.cacheDir)
        try {
            ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
            val sb = StringBuilder()
            PdfRenderer(pfd).use { r ->
                for (i in 0 until minOf(3, r.pageCount)) {
                    r.openPage(i).use { p ->
                        val bmp = Bitmap.createBitmap(p.width * 2, p.height * 2, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        sb.append(ocr(InputImage.fromBitmap(bmp, 0))).append('\n')
                        bmp.recycle()
                    }
                }
            }
            return sb.toString()
        } finally {
            tmp.delete()
        }
    }

    private fun docx(ctx: Context, uri: Uri): String {
        ctx.contentResolver.openInputStream(uri)!!.use { raw ->
            ZipInputStream(raw).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (e.name == "word/document.xml") {
                        val xml = z.readBytes().toString(Charsets.UTF_8)
                        return xml
                            .replace("</w:p>", "\n")
                            .replace("<w:tab/>", " ")
                            .replace(Regex("<[^>]+>"), "")
                            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                            .replace("&quot;", "\"").replace("&apos;", "'")
                    }
                    e = z.nextEntry
                }
            }
        }
        return ""
    }

    private val monthMap = mapOf(
        "januari" to 1, "februari" to 2, "maret" to 3, "april" to 4, "mei" to 5, "juni" to 6,
        "juli" to 7, "agustus" to 8, "september" to 9, "oktober" to 10, "november" to 11, "desember" to 12
    )
    private val reDateId = Regex(
        "(\\d{1,2})\\s+(januari|februari|maret|april|mei|juni|juli|agustus|september|oktober|november|desember)\\s+(\\d{4})",
        RegexOption.IGNORE_CASE
    )
    private val reDateNum = Regex("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})")
    private val reNumber = Regex("(?i)(?:\\bnomor\\s*[:.]?|\\bno\\s*[:.])\\s*(\\S[^\\n]{2,59})")
    private val rePerihal = Regex("(?i)\\bperihal\\s*[:.]?\\s*(\\S[^\\n]{2,149})")
    private val reHal = Regex("(?i)\\bhal\\s*:\\s*(\\S[^\\n]{2,149})")
    private val reInst = Regex(
        "(?i)(pemerintah|universitas|institut|politeknik|fakultas|yayasan|dinas|kementerian|badan|himpunan|organisasi|komite|panitia|sekolah|\\bpt\\b|\\bcv\\b|\\bbem\\b|\\bukm\\b|pengurus|lembaga|kantor)"
    )

    fun parse(text: String, fileName: String): Parsed {
        val low = text.lowercase()
        val type = when {
            "surat keputusan" in low || "memutuskan" in low || Regex("\\bsk\\b").containsMatchIn(fileName.lowercase()) -> "SK"
            "pertanggungjawaban" in low || "pertanggung jawaban" in low || Regex("\\blpj\\b").containsMatchIn(low) -> "LPJ"
            "proposal" in low || "proposal" in fileName.lowercase() -> "Proposal"
            "nota" in low.take(400) || "kwitansi" in low || "invoice" in low || "bukti pembayaran" in low -> "Nota"
            "surat" in low || "kepada yth" in low || "lampiran" in low -> "Surat Masuk"
            else -> "Lainnya"
        }
        val number = reNumber.find(text)?.groupValues?.get(1)?.trim()?.trimEnd(',', ';') ?: ""
        val date = parseDate(text)
        val subject = (rePerihal.find(text) ?: reHal.find(text))?.groupValues?.get(1)?.trim()
            ?: fileName.substringBeforeLast('.')
        val lines = text.lines().map { it.trim() }.filter { it.length > 3 }.take(12)
        val institution = (lines.firstOrNull { reInst.containsMatchIn(it) } ?: lines.firstOrNull() ?: "").take(120)
        return Parsed(type, number, date, subject, institution)
    }

    private fun parseDate(text: String): String {
        reDateId.find(text)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = monthMap[m.groupValues[2].lowercase()] ?: return@let
            return "%04d-%02d-%02d".format(m.groupValues[3].toInt(), mo, d)
        }
        reDateNum.find(text)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            if (mo in 1..12 && d in 1..31) return "%04d-%02d-%02d".format(m.groupValues[3].toInt(), mo, d)
        }
        return ""
    }
}

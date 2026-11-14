package id.arsipos.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.max

class Compressed(val file: File, val name: String, val mime: String)

private class PageImg(val jpeg: ByteArray, val w: Int, val h: Int, val ptW: Int, val ptH: Int)

private class CountingOut(private val out: OutputStream) : OutputStream() {
    var n = 0L
    override fun write(b: Int) { out.write(b); n++ }
    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); n += len }
    override fun flush() = out.flush()
    override fun close() = out.close()
}

/** Kompresi di perangkat agar file turun di bawah batas 50 MB Bot API. Gambar -> JPEG, PDF -> PDF raster. */
object Compressor {
    private const val LIMIT = Telegram.MAX_UPLOAD - 512 * 1024

    fun supports(mime: String, name: String): Boolean =
        mime.startsWith("image/") || mime == "application/pdf" || name.lowercase().endsWith(".pdf")

    suspend fun compress(ctx: Context, uri: Uri, mime: String, name: String): Compressed? =
        withContext(Dispatchers.IO) {
            val outDir = File(ctx.cacheDir, "compressed").apply { mkdirs() }
            when {
                mime.startsWith("image/") -> image(ctx, uri, name, outDir)
                mime == "application/pdf" || name.lowercase().endsWith(".pdf") -> pdf(ctx, uri, name, outDir)
                else -> null
            }
        }

    private fun image(ctx: Context, uri: Uri, name: String, outDir: File): Compressed? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val dest = File(outDir, "${System.nanoTime()}.jpg")
        val base = name.substringBeforeLast('.')
        for (maxDim in intArrayOf(3000, 2200, 1600, 1200, 800, 600)) {
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: continue
            val scale = maxDim.toFloat() / max(bmp.width, bmp.height)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true
                )
            } else bmp
            if (scaled !== bmp) bmp.recycle()
            for (q in intArrayOf(85, 70, 55, 40)) {
                FileOutputStream(dest).use { scaled.compress(Bitmap.CompressFormat.JPEG, q, it) }
                if (dest.length() <= LIMIT) {
                    scaled.recycle()
                    return Compressed(dest, "$base.jpg", "image/jpeg")
                }
            }
            scaled.recycle()
        }
        dest.delete()
        return null
    }

    private fun pdf(ctx: Context, uri: Uri, name: String, outDir: File): Compressed? {
        val tmp = File.createTempFile("src", ".pdf", ctx.cacheDir)
        val dest = File(outDir, "${System.nanoTime()}.pdf")
        try {
            ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { r ->
                    for ((scale, q) in listOf(1.5f to 70, 1.2f to 60, 1.0f to 50, 0.8f to 40, 0.6f to 35)) {
                        val pages = ArrayList<PageImg>()
                        var total = 0L
                        var ok = true
                        for (i in 0 until r.pageCount) {
                            r.openPage(i).use { p ->
                                val w = (p.width * scale).toInt().coerceAtLeast(1)
                                val h = (p.height * scale).toInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                val bos = ByteArrayOutputStream()
                                bmp.compress(Bitmap.CompressFormat.JPEG, q, bos)
                                bmp.recycle()
                                val jpeg = bos.toByteArray()
                                total += jpeg.size
                                pages.add(PageImg(jpeg, w, h, p.width, p.height))
                            }
                            if (total > LIMIT) { ok = false; break }
                        }
                        if (ok && pages.isNotEmpty()) {
                            writePdf(pages, dest)
                            if (dest.length() <= LIMIT) return Compressed(dest, name, "application/pdf")
                        }
                    }
                }
            }
            dest.delete()
            return null
        } finally {
            tmp.delete()
        }
    }

    /** Penulis PDF minimal: satu gambar JPEG (DCTDecode) per halaman. */
    private fun writePdf(pages: List<PageImg>, dest: File) {
        CountingOut(BufferedOutputStream(FileOutputStream(dest))).use { o ->
            val offsets = ArrayList<Long>()
            fun w(s: String) = o.write(s.toByteArray(Charsets.ISO_8859_1))
            val total = 2 + 3 * pages.size
            w("%PDF-1.4\n")
            offsets.add(o.n); w("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
            val kids = pages.indices.joinToString(" ") { "${3 + 3 * it} 0 R" }
            offsets.add(o.n); w("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>\nendobj\n")
            pages.forEachIndexed { i, p ->
                val pg = 3 + 3 * i
                val ct = pg + 1
                val im = pg + 2
                offsets.add(o.n)
                w("$pg 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${p.ptW} ${p.ptH}] /Resources << /XObject << /Im0 $im 0 R >> >> /Contents $ct 0 R >>\nendobj\n")
                val content = "q ${p.ptW} 0 0 ${p.ptH} 0 0 cm /Im0 Do Q"
                offsets.add(o.n)
                w("$ct 0 obj\n<< /Length ${content.length} >>\nstream\n$content\nendstream\nendobj\n")
                offsets.add(o.n)
                w("$im 0 obj\n<< /Type /XObject /Subtype /Image /Width ${p.w} /Height ${p.h} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${p.jpeg.size} >>\nstream\n")
                o.write(p.jpeg)
                w("\nendstream\nendobj\n")
            }
            val xref = o.n
            w("xref\n0 ${total + 1}\n0000000000 65535 f \n")
            for (off in offsets) w(String.format(Locale.US, "%010d 00000 n \n", off))
            w("trailer\n<< /Size ${total + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        }
    }
}

package id.arsipos.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreTest {
    @Test fun tagsAreNormalized() {
        assertEquals("#keuangan #2026 #penting", normTags("Keuangan, 2026 #Penting #penting"))
    }

    @Test fun emptyTagsStayEmpty() {
        assertEquals("", normTags("  , # "))
    }

    @Test fun messageLink() {
        val r = Telegram.parseMessageLink("https://t.me/c/1234567890/42")
        assertEquals("-1001234567890", r?.first)
        assertEquals(42L, r?.second)
    }

    @Test fun messageLinkWithTopic() {
        val r = Telegram.parseMessageLink("https://t.me/c/1234567890/5/78")
        assertEquals(78L, r?.second)
    }

    @Test fun invalidLinkIsRejected() {
        assertNull(Telegram.parseMessageLink("https://example.com/post/1"))
    }

    @Test fun chatLinkOnlyForSupergroups() {
        assertEquals("https://t.me/c/1234567890/42", Telegram.chatLink("-1001234567890", 42L))
        assertNull(Telegram.chatLink("-12345", 42L))
        assertNull(Telegram.chatLink("-1001234567890", 0L))
    }

    @Test fun sizeFormat() {
        assertEquals("1.0 MB", formatSize(1048576L))
        assertEquals("500 B", formatSize(500L))
    }

    @Test fun lockFormat() {
        assertEquals("5 menit", fmtLock(300_000L))
        assertEquals("30 detik", fmtLock(30_000L))
    }

    @Test fun yearMonthParsing() {
        assertEquals(2026 to 11, yearMonthOf("2026-11-14"))
    }

    @Test fun captionStructure() {
        val c = caption("Judul", "Kantor", "#a #b")
        assertEquals(true, c.startsWith("📌 Judul\n"))
        assertEquals(true, c.contains("Kantor") && c.endsWith("#a #b"))
    }

    @Test fun extractorParsesLetter() {
        val t = "PEMERINTAH KOTA X\nNomor: 123/ABC/2026\nPerihal: Undangan rapat\n14 November 2026\nKepada Yth."
        val p = Extractor.parse(t, "surat.pdf")
        assertEquals("123/ABC/2026", p.number)
        assertEquals("Undangan rapat", p.subject)
        assertEquals("2026-11-14", p.date)
    }
}

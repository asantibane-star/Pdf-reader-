package dev.lector.app.data.intake

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameSanitizerTest {
    private val now = LocalDateTime.of(2026, 3, 9, 14, 5)

    @Test fun removesForbiddenCharacters() {
        assertEquals("abcdefghi", FileNameSanitizer.sanitize("a/b\\c:d*e?f\"g<h>i", now))
        assertEquals("ab", FileNameSanitizer.sanitize("a|b", now))
    }

    @Test fun removesControlCharactersAndLeadingDots() {
        assertEquals("oculto", FileNameSanitizer.sanitize("..\u0000oculto\n", now))
        assertEquals("a b", FileNameSanitizer.sanitize("   a b   ", now))
    }

    @Test fun capsLengthAt100() {
        assertEquals(100, FileNameSanitizer.sanitize("x".repeat(300), now).length)
    }

    @Test fun emptyFallsBackToTimestampName() {
        assertEquals("Escaneo 2026-03-09 1405", FileNameSanitizer.sanitize("", now))
        assertEquals("Escaneo 2026-03-09 1405", FileNameSanitizer.sanitize("///", now))
        assertEquals("Escaneo 2026-03-09 1405", FileNameSanitizer.sanitize("...", now))
    }

    @Test fun displayTitleStripsExtension() {
        assertEquals("Informe", FileNameSanitizer.displayTitle("Informe.pdf"))
        assertEquals("Informe final", FileNameSanitizer.displayTitle("Informe final.docx"))
        assertEquals("sin extension", FileNameSanitizer.displayTitle("sin extension"))
        assertEquals("Documento", FileNameSanitizer.displayTitle(""))
    }
}

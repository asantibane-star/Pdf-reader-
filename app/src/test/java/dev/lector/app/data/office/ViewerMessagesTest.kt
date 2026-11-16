package dev.lector.app.data.office

import dev.lector.app.core.result.DocError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerMessagesTest {
    @Test fun acceptsOnlyTheClosedList() {
        assertEquals(JsMessage.Ready, ViewerMessages.parse("ready"))
        assertEquals(JsMessage.Rendered, ViewerMessages.parse("rendered"))
        assertEquals(JsMessage.Sheets(3), ViewerMessages.parse("sheets:3"))
        assertEquals(JsMessage.Failed(DocError.RenderTimeout), ViewerMessages.parse("error:timeout"))
        assertEquals(JsMessage.Failed(DocError.Corrupt), ViewerMessages.parse("error:corrupt"))
        assertEquals(JsMessage.Failed(DocError.UnsupportedFormat), ViewerMessages.parse("error:unsupported"))
    }

    @Test fun discardsEverythingElse() {
        listOf(
            null, "", "READY", "ready ", "rendered\n", "error:", "error:boom", "sheets:", "sheets:0", "sheets:-1", "sheets:51",
            "sheets:abc", "<script>alert(1)</script>", "javascript:alert(1)", "x".repeat(65), "error:" + "x".repeat(60),
        ).forEach { assertNull("debía descartarse: $it", ViewerMessages.parse(it)) }
    }

    @Test fun limitsLengthTo64() {
        assertNull(ViewerMessages.parse("sheets:" + "1".repeat(ViewerMessages.MAX_LENGTH)))
    }
}

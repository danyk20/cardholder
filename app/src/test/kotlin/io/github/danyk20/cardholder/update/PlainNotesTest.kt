package io.github.danyk20.cardholder.update

import kotlin.test.assertEquals
import org.junit.Test

class PlainNotesTest {
    @Test
    fun `release notes read as plain text`() {
        val markdown = "### Fixed\n- Typing **quickly** works\n  - `nested`\n\n\n\n### Changed\n* Files"

        assertEquals("Fixed\n• Typing quickly works\n  • nested\n\nChanged\n• Files", plainNotes(markdown))
    }
}

package eu.kanade.translation.translator

import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationContext
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AICoreTranslatorTest {

    private fun createBlock(
        text: String,
        x: Float,
        y: Float,
        width: Float = 50f,
        height: Float = 30f,
    ): TranslationBlock {
        return TranslationBlock(
            text = text,
            translation = "",
            width = width,
            height = height,
            x = x,
            y = y,
            symHeight = 10f,
            symWidth = 8f,
            angle = 0f,
        )
    }

    @Test
    fun `sortBlocksInReadingOrder sorts RTL for Japanese`() {
        val blockA = createBlock("Right Balloon", x = 500f, y = 100f)
        val blockB = createBlock("Left Balloon", x = 100f, y = 105f)
        val blockC = createBlock("Bottom Balloon", x = 250f, y = 350f)

        val blocks = listOf(blockB, blockC, blockA)
        val sorted = AICoreTranslator.sortBlocksInReadingOrder(blocks, TextRecognizerLanguage.JAPANESE)

        assertEquals(3, sorted.size)
        assertEquals("Right Balloon", sorted[0].text)
        assertEquals("Left Balloon", sorted[1].text)
        assertEquals("Bottom Balloon", sorted[2].text)
    }

    @Test
    fun `sortBlocksInReadingOrder sorts LTR for Korean and Chinese`() {
        val blockA = createBlock("Right Balloon", x = 500f, y = 100f)
        val blockB = createBlock("Left Balloon", x = 100f, y = 105f)
        val blockC = createBlock("Bottom Balloon", x = 250f, y = 350f)

        val blocks = listOf(blockA, blockC, blockB)
        val sortedKorean = AICoreTranslator.sortBlocksInReadingOrder(blocks, TextRecognizerLanguage.KOREAN)
        val sortedChinese = AICoreTranslator.sortBlocksInReadingOrder(blocks, TextRecognizerLanguage.CHINESE)

        assertEquals(3, sortedKorean.size)
        assertEquals("Left Balloon", sortedKorean[0].text)
        assertEquals("Right Balloon", sortedKorean[1].text)
        assertEquals("Bottom Balloon", sortedKorean[2].text)

        assertEquals(3, sortedChinese.size)
        assertEquals("Left Balloon", sortedChinese[0].text)
        assertEquals("Right Balloon", sortedChinese[1].text)
        assertEquals("Bottom Balloon", sortedChinese[2].text)
    }

    @Test
    fun `truncateSynopsis normalizes whitespace and caps length`() {
        val messy = "  A quick    brown   fox\n\njumps over   \t the lazy dog.  "
        val normalized = AICoreTranslator.truncateSynopsis(messy, 100)
        assertEquals("A quick brown fox jumps over the lazy dog.", normalized)

        val longSynopsis = "A".repeat(500)
        val truncated = AICoreTranslator.truncateSynopsis(longSynopsis, 350)
        assertEquals(350, truncated.length)
    }

    @Test
    fun `buildContextualPrompt includes metadata, previous dialogs, and current texts`() {
        val context = TranslationContext(
            mangaTitle = "One Piece",
            chapterTitle = "Chapter 1000",
            genres = listOf("Action", "Adventure"),
            synopsis = "Luffy embarks on his journey to find the One Piece.",
        )
        val previousDialogs = listOf(
            "Hello" to "Halo",
            "Goodbye" to "Selamat tinggal",
        )
        val currentTexts = listOf("I will be the Pirate King!", "Let's go!")

        val prompt = AICoreTranslator.buildContextualPrompt(
            context = context,
            targetLanguage = "English",
            previousDialogs = previousDialogs,
            currentTexts = currentTexts,
        )

        assertTrue(prompt.contains("Title: One Piece"))
        assertTrue(prompt.contains("Chapter: Chapter 1000"))
        assertTrue(prompt.contains("Genres: Action, Adventure"))
        assertTrue(prompt.contains("Synopsis: Luffy embarks on his journey"))
        assertTrue(prompt.contains("\"Hello\" -> \"Halo\""))
        assertTrue(prompt.contains("\"Goodbye\" -> \"Selamat tinggal\""))
        assertTrue(prompt.contains("I will be the Pirate King!"))
        assertTrue(prompt.contains("Let's go!"))
    }

    @Test
    fun `parseJsonArrayResponse parses clean json array`() {
        val raw = """["Hello", "World", "Colamanga.com"]"""
        val parsed = AICoreTranslator.parseJsonArrayResponse(raw)

        assertNotNull(parsed)
        assertEquals(3, parsed!!.size)
        assertEquals("Hello", parsed[0])
        assertEquals("World", parsed[1])
        assertEquals("Colamanga.com", parsed[2])
    }

    @Test
    fun `parseJsonArrayResponse parses markdown fenced json`() {
        val raw = """
            ```json
            [
              "Translation 1",
              "Translation 2"
            ]
            ```
        """.trimIndent()
        val parsed = AICoreTranslator.parseJsonArrayResponse(raw)

        assertNotNull(parsed)
        assertEquals(2, parsed!!.size)
        assertEquals("Translation 1", parsed[0])
        assertEquals("Translation 2", parsed[1])
    }

    @Test
    fun `parseJsonArrayResponse extracts json array with surrounding text`() {
        val raw = """
            Here is the requested translation:
            ["Halo dunia", "Apa kabar?"]
            Hope this helps!
        """.trimIndent()
        val parsed = AICoreTranslator.parseJsonArrayResponse(raw)

        assertNotNull(parsed)
        assertEquals(2, parsed!!.size)
        assertEquals("Halo dunia", parsed[0])
        assertEquals("Apa kabar?", parsed[1])
    }

    @Test
    fun `parseJsonArrayResponse returns null for invalid json or empty input`() {
        assertNull(AICoreTranslator.parseJsonArrayResponse(null))
        assertNull(AICoreTranslator.parseJsonArrayResponse(""))
        assertNull(AICoreTranslator.parseJsonArrayResponse("   "))
        assertNull(AICoreTranslator.parseJsonArrayResponse("Just plain text with no array"))
        assertNull(AICoreTranslator.parseJsonArrayResponse("[unquoted, invalid json]"))
    }
}

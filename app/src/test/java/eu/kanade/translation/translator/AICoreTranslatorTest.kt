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

    @Test
    fun `parseJsonArrayResponse handles unescaped literal newlines inside strings`() {
        val raw = "[\"Baris satu\nBaris dua\", \"Teks normal\"]"
        val parsed = AICoreTranslator.parseJsonArrayResponse(raw)

        assertNotNull(parsed)
        assertEquals(2, parsed!!.size)
        assertEquals("Baris satu Baris dua", parsed[0])
        assertEquals("Teks normal", parsed[1])
    }

    @Test
    fun `parseJsonArrayResponse parses numbered lines fallback`() {
        val raw = """
            1. Hunter Sung Jin-Woo
            2. Gerbang Dungeon Rank S
            3. Hati-hati!
        """.trimIndent()
        val parsed = AICoreTranslator.parseJsonArrayResponse(raw, expectedCount = 3)

        assertNotNull(parsed)
        assertEquals(3, parsed!!.size)
        assertEquals("Hunter Sung Jin-Woo", parsed[0])
        assertEquals("Gerbang Dungeon Rank S", parsed[1])
        assertEquals("Hati-hati!", parsed[2])
    }

    @Test
    fun `normalizeOcrText handles hyphens, linebreaks, and collapses spaces`() {
        val messy = "GERB-\nANG  DUNGEON\r\nRANK   S"
        val normalized = AICoreTranslator.normalizeOcrText(messy)
        assertEquals("GERBANG DUNGEON RANK S", normalized)
    }

    @Test
    fun `smartMergeBlocks merges centered speech bubble lines`() {
        // Top line
        val top = TranslationBlock(
            text = "ARE YOU",
            translation = "",
            width = 60f,
            height = 20f,
            x = 50f,
            y = 100f,
            symHeight = 16f,
            symWidth = 10f,
            angle = 0f,
        )
        // Middle line (centered around x=80, wider)
        val mid = TranslationBlock(
            text = "READY FOR-",
            translation = "",
            width = 90f,
            height = 20f,
            x = 35f,
            y = 125f,
            symHeight = 16f,
            symWidth = 10f,
            angle = 0f,
        )
        // Bottom line (centered around x=80)
        val bot = TranslationBlock(
            text = "THIS?",
            translation = "",
            width = 50f,
            height = 20f,
            x = 55f,
            y = 150f,
            symHeight = 16f,
            symWidth = 10f,
            angle = 0f,
        )

        val merged = eu.kanade.translation.ChapterTranslator.smartMergeBlocks(
            blocks = listOf(top, bot, mid),
            widthThreshold = 50,
            xThreshold = 30,
            yThreshold = 30,
        )

        assertEquals(1, merged.size)
        assertEquals("ARE YOU READY FORTHIS?", merged[0].text)
        assertEquals(35f, merged[0].x)
        assertEquals(100f, merged[0].y)
        assertEquals(90f, merged[0].width)
        assertEquals(70f, merged[0].height)
    }
}

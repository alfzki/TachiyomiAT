package eu.kanade.translation.translator

import eu.kanade.translation.recognizer.TextRecognizerLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationPromptsTest {

    @Test
    fun `forLanguagePair Korean to English selects Korean manhwa prompt`() {
        val prompt = TranslationPrompts.forLanguagePair(
            TextRecognizerLanguage.KOREAN,
            TextTranslatorLanguage.ENGLISH,
        )

        assertTrue(prompt.contains("Korean Manhwa Translation"))
        assertTrue(prompt.contains("웹툰/만화"))
        assertTrue(prompt.contains("-님 (nim)"))
        assertTrue(prompt.contains("hyung"))
    }

    @Test
    fun `forLanguagePair Korean to Indonesian selects generic prompt with Indonesian target`() {
        val prompt = TranslationPrompts.forLanguagePair(
            TextRecognizerLanguage.KOREAN,
            TextTranslatorLanguage.INDONESIAN,
        )

        assertTrue(prompt.contains("Translate all text strings to the target language `Indonesian`"))
        assertTrue(prompt.contains("RTMTH"))
    }

    @Test
    fun `forLanguagePair Japanese to English selects Japanese manga prompt`() {
        val prompt = TranslationPrompts.forLanguagePair(
            TextRecognizerLanguage.JAPANESE,
            TextTranslatorLanguage.ENGLISH,
        )

        assertTrue(prompt.contains("Japanese Manga Translation"))
        assertTrue(prompt.contains("-san"))
        assertTrue(prompt.contains("-kun"))
        assertTrue(prompt.contains("dokidoki"))
    }

    @Test
    fun `forLanguagePair Japanese to Indonesian selects generic prompt with Indonesian target`() {
        val prompt = TranslationPrompts.forLanguagePair(
            TextRecognizerLanguage.JAPANESE,
            TextTranslatorLanguage.INDONESIAN,
        )

        assertTrue(prompt.contains("Translate all text strings to the target language `Indonesian`"))
        assertTrue(prompt.contains("RTMTH"))
    }

    @Test
    fun `forLanguagePair Chinese to English selects Chinese manhua prompt`() {
        val prompt = TranslationPrompts.forLanguagePair(
            TextRecognizerLanguage.CHINESE,
            TextTranslatorLanguage.ENGLISH,
        )

        assertTrue(prompt.contains("Chinese Manhua Translation"))
        assertTrue(prompt.contains("shifu"))
        assertTrue(prompt.contains("Golden Core"))
    }

    @Test
    fun `GeminiTranslator stripMarkdownCodeBlock strips markdown json codeblocks`() {
        val wrappedJson = """
            ```json
            {"001.jpg":["Hello","World"]}
            ```
        """.trimIndent()

        val stripped = GeminiTranslator.stripMarkdownCodeBlock(wrappedJson)
        assertEquals("""{"001.jpg":["Hello","World"]}""", stripped)
    }

    @Test
    fun `GeminiTranslator stripMarkdownCodeBlock strips generic markdown codeblocks`() {
        val wrapped = """
            ```
            {"001.jpg":["Line 1"]}
            ```
        """.trimIndent()

        val stripped = GeminiTranslator.stripMarkdownCodeBlock(wrapped)
        assertEquals("""{"001.jpg":["Line 1"]}""", stripped)
    }

    @Test
    fun `GeminiTranslator stripMarkdownCodeBlock leaves raw json untouched`() {
        val raw = """{"001.jpg":["Direct JSON"]}"""
        val stripped = GeminiTranslator.stripMarkdownCodeBlock(raw)
        assertEquals(raw, stripped)
    }

    @Test
    fun `GeminiTranslator stripMarkdownCodeBlock handles incomplete opening codeblocks`() {
        val incomplete = "```json\n{\"001.jpg\":[\"Unclosed\"]}"
        val stripped = GeminiTranslator.stripMarkdownCodeBlock(incomplete)
        assertEquals("{\"001.jpg\":[\"Unclosed\"]}", stripped)
    }

    @Test
    fun `ChapterTranslator getLanguageSpecificMergeThresholds returns correct thresholds`() {
        assertEquals(
            Triple(60, 35, 40),
            eu.kanade.translation.ChapterTranslator.getLanguageSpecificMergeThresholds(TextRecognizerLanguage.KOREAN),
        )
        assertEquals(
            Triple(50, 30, 30),
            eu.kanade.translation.ChapterTranslator.getLanguageSpecificMergeThresholds(TextRecognizerLanguage.JAPANESE),
        )
        assertEquals(
            Triple(45, 25, 28),
            eu.kanade.translation.ChapterTranslator.getLanguageSpecificMergeThresholds(TextRecognizerLanguage.CHINESE),
        )
        assertEquals(
            Triple(50, 30, 30),
            eu.kanade.translation.ChapterTranslator.getLanguageSpecificMergeThresholds(TextRecognizerLanguage.ENGLISH),
        )
    }

    @Test
    fun `TextTranslators GOOGLE_CLOUD exists and has correct label`() {
        val googleCloud = TextTranslators.GOOGLE_CLOUD
        assertEquals("Google Cloud Translation [API KEY]", googleCloud.label)
    }
}

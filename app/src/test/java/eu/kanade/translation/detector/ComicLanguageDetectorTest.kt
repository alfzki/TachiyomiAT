package eu.kanade.translation.detector

import eu.kanade.tachiyomi.source.Source
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

class ComicLanguageDetectorTest {

    @BeforeEach
    fun setUp() {
        ComicLanguageDetector.clearCache()
    }

    @Test
    fun `classifyScript returns KOREAN for Hangul text`() {
        assertEquals(TextRecognizerLanguage.KOREAN, ComicLanguageDetector.classifyScript("안녕하세요"))
        assertEquals(TextRecognizerLanguage.KOREAN, ComicLanguageDetector.classifyScript("ㅋㅋㅋ 대박"))
        assertEquals(TextRecognizerLanguage.KOREAN, ComicLanguageDetector.classifyScript("Level up! 나 혼자만 레벨업"))
    }

    @Test
    fun `classifyScript returns JAPANESE for Japanese text with Kana and Kanji`() {
        assertEquals(TextRecognizerLanguage.JAPANESE, ComicLanguageDetector.classifyScript("進撃の巨人"))
        assertEquals(TextRecognizerLanguage.JAPANESE, ComicLanguageDetector.classifyScript("スパイファミリー"))
        assertEquals(TextRecognizerLanguage.JAPANESE, ComicLanguageDetector.classifyScript("お前はもう死んでいる"))
    }

    @Test
    fun `classifyScript returns JAPANESE for Japanese text with Latin sound effect`() {
        assertEquals(TextRecognizerLanguage.JAPANESE, ComicLanguageDetector.classifyScript("BOOM! なんだこれは？"))
        assertEquals(TextRecognizerLanguage.JAPANESE, ComicLanguageDetector.classifyScript("DOKI DOKI! 好きです"))
    }

    @Test
    fun `classifyScript returns CHINESE for Chinese Hanzi text without Kana`() {
        assertEquals(TextRecognizerLanguage.CHINESE, ComicLanguageDetector.classifyScript("斗破苍穹"))
        assertEquals(TextRecognizerLanguage.CHINESE, ComicLanguageDetector.classifyScript("全职高手 第一章"))
        assertEquals(TextRecognizerLanguage.CHINESE, ComicLanguageDetector.classifyScript("修真聊天群"))
    }

    @Test
    fun `classifyScript returns ENGLISH for Latin text`() {
        assertEquals(
            TextRecognizerLanguage.ENGLISH,
            ComicLanguageDetector.classifyScript("Where are we going right now?"),
        )
        assertEquals(
            TextRecognizerLanguage.ENGLISH,
            ComicLanguageDetector.classifyScript("Aku tidak menyangka kamu akan datang ke sini."),
        )
        assertEquals(TextRecognizerLanguage.ENGLISH, ComicLanguageDetector.classifyScript("Hello world!"))
    }

    @Test
    fun `classifyScript returns null for empty or non-language text`() {
        assertNull(ComicLanguageDetector.classifyScript(""))
        assertNull(ComicLanguageDetector.classifyScript("   "))
        assertNull(ComicLanguageDetector.classifyScript("12345 67890 !@#$%^&*()"))
        assertNull(ComicLanguageDetector.classifyScript("... ??? !!!"))
    }

    @Test
    fun `detectFromMetadata handles sourceLang correctly`() {
        assertEquals(TextRecognizerLanguage.JAPANESE, ComicLanguageDetector.detectFromMetadata("ja", null, null))
        assertEquals(TextRecognizerLanguage.JAPANESE, ComicLanguageDetector.detectFromMetadata("ja-JP", null, null))
        assertEquals(TextRecognizerLanguage.KOREAN, ComicLanguageDetector.detectFromMetadata("ko", null, null))
        assertEquals(TextRecognizerLanguage.KOREAN, ComicLanguageDetector.detectFromMetadata("ko-KR", null, null))
        assertEquals(TextRecognizerLanguage.CHINESE, ComicLanguageDetector.detectFromMetadata("zh", null, null))
        assertEquals(TextRecognizerLanguage.CHINESE, ComicLanguageDetector.detectFromMetadata("zh-Hans", null, null))
        assertEquals(TextRecognizerLanguage.CHINESE, ComicLanguageDetector.detectFromMetadata("zh-Hant", null, null))

        val latinLangs = listOf("en", "en-US", "id", "es", "fr", "de", "it", "pt", "pt-BR", "ru")
        for (lang in latinLangs) {
            assertEquals(
                TextRecognizerLanguage.ENGLISH,
                ComicLanguageDetector.detectFromMetadata(lang, null, null),
                "Failed for lang: $lang",
            )
        }
    }

    @Test
    fun `detectFromMetadata falls back to title and description when sourceLang is unrecognized, all, or other`() {
        // Fallback to title
        assertEquals(
            TextRecognizerLanguage.KOREAN,
            ComicLanguageDetector.detectFromMetadata("all", "나 혼자만 레벨업", null),
        )
        assertEquals(
            TextRecognizerLanguage.JAPANESE,
            ComicLanguageDetector.detectFromMetadata("other", "進撃の巨人", null),
        )

        // Fallback to description when title has no script
        assertEquals(
            TextRecognizerLanguage.CHINESE,
            ComicLanguageDetector.detectFromMetadata(null, "123", "这是一个关于修炼的故事"),
        )

        // Null when nothing matches
        assertNull(ComicLanguageDetector.detectFromMetadata(null, "12345", "..."))
        assertNull(ComicLanguageDetector.detectFromMetadata("unknown", null, null))
    }

    @Test
    fun `detectLanguage returns cached language when already cached`() = runBlocking {
        ComicLanguageDetector.setCachedLanguage(1L, TextRecognizerLanguage.KOREAN)

        var providerCalled = false
        val lang = ComicLanguageDetector.detectLanguage(
            mangaId = 1L,
            sourceLang = "en",
            title = "Some English Title",
            description = null,
            sampleTextProvider = {
                providerCalled = true
                "Some sample text"
            },
        )

        assertEquals(TextRecognizerLanguage.KOREAN, lang)
        assertEquals(false, providerCalled)
    }

    @Test
    fun `detectLanguage prefers sampleText over metadata when sample text is classified`() = runBlocking {
        val lang = ComicLanguageDetector.detectLanguage(
            mangaId = 2L,
            sourceLang = "en",
            title = "Raw Japanese Manga Hosted on English Source",
            description = null,
            sampleTextProvider = { "これは日本語のテキストです！" },
        )

        assertEquals(TextRecognizerLanguage.JAPANESE, lang)
        // Verify result is now cached
        assertEquals(TextRecognizerLanguage.JAPANESE, ComicLanguageDetector.getCachedLanguage(2L))
    }

    @Test
    fun `detectLanguage falls back to metadata when sampleText is null or inconclusive`() = runBlocking {
        val lang = ComicLanguageDetector.detectLanguage(
            mangaId = 3L,
            sourceLang = "ko",
            title = "Korean Webtoon",
            description = null,
            sampleTextProvider = { "12345 !?" },
        )

        assertEquals(TextRecognizerLanguage.KOREAN, lang)
    }

    @Test
    fun `detectLanguage falls back to ENGLISH when metadata and sample are inconclusive`() = runBlocking {
        val lang = ComicLanguageDetector.detectLanguage(
            mangaId = 4L,
            sourceLang = null,
            title = "12345",
            description = null,
            sampleTextProvider = null,
        )

        assertEquals(TextRecognizerLanguage.ENGLISH, lang)
    }

    @Test
    fun `clearCache and setCachedLanguage manage cache correctly`() {
        ComicLanguageDetector.setCachedLanguage(10L, TextRecognizerLanguage.CHINESE)
        assertEquals(TextRecognizerLanguage.CHINESE, ComicLanguageDetector.getCachedLanguage(10L))

        ComicLanguageDetector.clearCache()
        assertNull(ComicLanguageDetector.getCachedLanguage(10L))
    }

    @Test
    fun `detectLanguage with Source and Manga delegates properly`() = runBlocking {
        val mockSource = mockk<Source> {
            every { lang } returns "ja"
        }
        val manga = Manga.create().copy(id = 50L, title = "進撃の巨人")

        val lang = ComicLanguageDetector.detectLanguage(mockSource, manga)
        assertEquals(TextRecognizerLanguage.JAPANESE, lang)
        assertEquals(TextRecognizerLanguage.JAPANESE, ComicLanguageDetector.getCachedLanguage(50L))
    }
}

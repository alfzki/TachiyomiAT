package eu.kanade.translation

import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.translator.TextTranslatorLanguage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationSerializationTest {

    @Test
    fun `serialize and deserialize page translation map`() {
        val block1 = TranslationBlock(
            text = "Hello world",
            translation = "Halo dunia",
            width = 100f,
            height = 50f,
            x = 10f,
            y = 20f,
            symHeight = 12f,
            symWidth = 8f,
            angle = 0f,
        )
        val page = PageTranslation(
            imgWidth = 1000f,
            imgHeight = 1500f,
            blocks = mutableListOf(block1),
        )
        val map = mapOf("page_001.jpg" to page)

        val jsonStr = Json.encodeToString(map)
        val decoded = Json.decodeFromString<Map<String, PageTranslation>>(jsonStr)

        assertEquals(1, decoded.size)
        assertTrue(decoded.containsKey("page_001.jpg"))
        val decodedPage = decoded["page_001.jpg"]!!
        assertEquals(1000f, decodedPage.imgWidth)
        assertEquals(1500f, decodedPage.imgHeight)
        assertEquals(1, decodedPage.blocks.size)

        val decodedBlock = decodedPage.blocks[0]
        assertEquals("Hello world", decodedBlock.text)
        assertEquals("Halo dunia", decodedBlock.translation)
        assertEquals(100f, decodedBlock.width)
        assertEquals(50f, decodedBlock.height)
        assertEquals(10f, decodedBlock.x)
        assertEquals(20f, decodedBlock.y)
    }

    @Test
    fun `mlkit supported languages are subset of all languages`() {
        val mlkitLangs = TextTranslatorLanguage.mlkitSupportedLanguages()
        assertFalse(mlkitLangs.isEmpty())
        assertTrue(mlkitLangs.any { it.code == "en" })
        assertTrue(mlkitLangs.any { it.code == "ja" })
        assertTrue(mlkitLangs.any { it.code == "zh" })
    }

    @Test
    fun `deleteBlock removes target block and serializes correctly`() {
        val block1 = TranslationBlock("First", "Pertama", 100f, 50f, 10f, 10f, 8f, 12f, 0f)
        val block2 = TranslationBlock("Second", "Kedua", 120f, 60f, 20f, 70f, 8f, 12f, 0f)
        val page = PageTranslation(
            imgWidth = 1000f,
            imgHeight = 1500f,
            sourceLanguage = "ko",
            targetLanguage = "id",
            blocks = mutableListOf(block1, block2),
        )

        assertEquals(2, page.blocks.size)
        val deleted = page.deleteBlock(0)
        assertTrue(deleted)
        assertEquals(1, page.blocks.size)
        assertEquals("Second", page.blocks[0].text)

        val map = mapOf("001.jpg" to page)
        val jsonStr = Json.encodeToString(map)
        val decoded = Json.decodeFromString<Map<String, PageTranslation>>(jsonStr)

        assertEquals("ko", decoded["001.jpg"]!!.sourceLanguage)
        assertEquals("id", decoded["001.jpg"]!!.targetLanguage)
        assertEquals(1, decoded["001.jpg"]!!.blocks.size)
        assertEquals("Second", decoded["001.jpg"]!!.blocks[0].text)
    }

    @Test
    fun `PageTranslation language codes derive expected ProfileType`() {
        val pageKorean = PageTranslation(sourceLanguage = "ko", targetLanguage = "en")
        assertEquals(tachiyomi.domain.translation.model.ProfileType.CJK, pageKorean.profileType)

        val pageJapanese = PageTranslation(sourceLanguage = "ja", targetLanguage = "en")
        assertEquals(tachiyomi.domain.translation.model.ProfileType.CJK, pageJapanese.profileType)

        val pageChinese = PageTranslation(sourceLanguage = "zh", targetLanguage = "en")
        assertEquals(tachiyomi.domain.translation.model.ProfileType.CJK, pageChinese.profileType)

        val pageEnglish = PageTranslation(sourceLanguage = "en", targetLanguage = "id")
        assertEquals(tachiyomi.domain.translation.model.ProfileType.LATIN, pageEnglish.profileType)
    }
}

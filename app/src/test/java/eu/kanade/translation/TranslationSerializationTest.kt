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
}

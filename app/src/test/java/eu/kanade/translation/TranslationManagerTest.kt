package eu.kanade.translation

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.translation.data.TranslationProvider
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationBlock
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.TranslationPreferences
import java.io.File

class TranslationManagerTest {

    @Test
    fun `deleteTranslationBlock deletes block and persists changes to disk`(@TempDir tempDir: File) {
        val jsonFile = File(tempDir, "chapter_trans.json")
        val block1 = TranslationBlock("Text 1", "Trans 1", 10f, 10f, 0f, 0f, 5f, 5f, 0f)
        val block2 = TranslationBlock("Text 2", "Trans 2", 20f, 20f, 10f, 10f, 5f, 5f, 0f)
        val initialPage = PageTranslation(
            imgWidth = 800f,
            imgHeight = 1200f,
            sourceLanguage = "ko",
            targetLanguage = "en",
            blocks = mutableListOf(block1, block2),
        )
        val initialMap = mapOf("001.jpg" to initialPage)
        jsonFile.writeText(Json.encodeToString(initialMap))

        val uniFile = UniFile.fromFile(jsonFile)
        val context = mockk<Context>(relaxed = true)
        val provider = mockk<TranslationProvider> {
            every { findTranslationFile(any(), any(), any(), any()) } returns uniFile
        }
        val downloadProvider = mockk<DownloadProvider>(relaxed = true)
        val sourceManager = mockk<SourceManager>(relaxed = true)
        val fromLangPref = mockk<tachiyomi.core.common.preference.Preference<String>> {
            every { get() } returns "AUTO"
            every { set(any()) } returns Unit
        }
        val toLangPref = mockk<tachiyomi.core.common.preference.Preference<String>> {
            every { get() } returns "ENGLISH"
            every { set(any()) } returns Unit
        }
        val enginePref = mockk<tachiyomi.core.common.preference.Preference<Int>> {
            every { get() } returns 0
            every { set(any()) } returns Unit
        }
        val translationPreferences = mockk<TranslationPreferences>(relaxed = true) {
            every { translateFromLanguage() } returns fromLangPref
            every { translateToLanguage() } returns toLangPref
            every { translationEngine() } returns enginePref
        }

        val manager = TranslationManager(
            context = context,
            provider = provider,
            downloadProvider = downloadProvider,
            sourceManager = sourceManager,
            translationPreferences = translationPreferences,
        )

        val chapter = mockk<Chapter> {
            every { name } returns "Chapter 1"
            every { scanlator } returns null
        }
        val manga = mockk<Manga> {
            every { title } returns "Test Manga"
        }
        val source = mockk<Source> {
            every { id } returns 1L
        }

        val result = manager.deleteTranslationBlock(
            chapter = chapter,
            manga = manga,
            source = source,
            pageFileName = "001.jpg",
            blockIndex = 0,
        )

        assertTrue(result)

        // Read file back and verify
        val updatedMap = Json.decodeFromString<Map<String, PageTranslation>>(jsonFile.readText())
        val updatedPage = updatedMap["001.jpg"]!!
        assertEquals(1, updatedPage.blocks.size)
        assertEquals("Text 2", updatedPage.blocks[0].text)

        // Test invalid block index
        val invalidResult = manager.deleteTranslationBlock(
            chapter = chapter,
            manga = manga,
            source = source,
            pageFileName = "001.jpg",
            blockIndex = 99,
        )
        assertFalse(invalidResult)
    }
}

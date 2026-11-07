package eu.kanade.translation.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.model.ProfileType
import tachiyomi.domain.translation.model.TranslationProfile

class LanguageRenderModuleTest {

    @Test
    fun `fromSourceLanguage correctly returns CJKModule or LatinModule`() {
        val koModule = LanguageRenderModule.fromSourceLanguage("ko")
        assertTrue(koModule is LanguageRenderModule.CJKModule)
        assertEquals(1.15f, koModule.paddingMultiplier)

        val jaModule = LanguageRenderModule.fromSourceLanguage("ja")
        assertTrue(jaModule is LanguageRenderModule.CJKModule)
        assertEquals(1.2f, jaModule.paddingMultiplier)

        val zhModule = LanguageRenderModule.fromSourceLanguage("zh")
        assertTrue(zhModule is LanguageRenderModule.CJKModule)
        assertEquals(1.1f, zhModule.paddingMultiplier)

        val esModule = LanguageRenderModule.fromSourceLanguage("es")
        assertTrue(esModule is LanguageRenderModule.LatinModule)
        assertEquals(1.0f, esModule.paddingMultiplier)

        val idModule = LanguageRenderModule.fromSourceLanguage("id")
        assertTrue(idModule is LanguageRenderModule.LatinModule)

        val enModule = LanguageRenderModule.fromSourceLanguage("en")
        assertTrue(enModule is LanguageRenderModule.LatinModule)
    }

    @Test
    fun `CJKModule expansion constants for Korean distinguish cloud vs local`() {
        val cjkModule = LanguageRenderModule.CJKModule("ko")
        val cloudConstants = cjkModule.getKoreanExpansionConstants(isCloudTranslation = true)
        assertEquals(2.2f to 3.0f, cloudConstants)

        val localConstants = cjkModule.getKoreanExpansionConstants(isCloudTranslation = false)
        assertEquals(1.8f to 2.5f, localConstants)
    }

    @Test
    fun `LatinModule uses hybrid overflow and flexible font bounds`() {
        val latinModule = LanguageRenderModule.LatinModule()
        assertTrue(latinModule.useHybridOverflow)
        assertEquals(10, latinModule.minFontSize)
        assertEquals(12, latinModule.maxFontSize)
        assertEquals(0.95f, latinModule.ovalHeightMargin)
    }

    @Test
    fun `fromProfile constructs modules from TranslationProfile`() {
        val cjkProfile = TranslationProfile(
            type = ProfileType.CJK,
            textMargin = 3,
            paddingMultiplier = 1.25f,
            ovalHeightMargin = 0.72f,
            horizontalPadding = 8,
            verticalPadding = 2,
            minFontSize = 12,
            maxFontSize = 12,
            lineHeightMultiplier = 1.4f,
            minExpansion = 1.8f,
            maxExpansion = 2.5f,
            useHybridOverflow = false,
        )
        val moduleFromCjk = LanguageRenderModule.fromProfile(cjkProfile, "ko")
        assertTrue(moduleFromCjk is LanguageRenderModule.CJKModule)
        assertEquals(1.25f, moduleFromCjk.paddingMultiplier)
        assertEquals(0.72f, moduleFromCjk.ovalHeightMargin)
        assertFalse(moduleFromCjk.useHybridOverflow)

        val latinProfile = TranslationProfile(
            type = ProfileType.LATIN,
            textMargin = 1,
            paddingMultiplier = 1.05f,
            ovalHeightMargin = 0.96f,
            horizontalPadding = 7,
            verticalPadding = 5,
            minFontSize = 9,
            maxFontSize = 13,
            lineHeightMultiplier = 1.2f,
            minExpansion = 1.0f,
            maxExpansion = 1.35f,
            useHybridOverflow = true,
        )
        val moduleFromLatin = LanguageRenderModule.fromProfile(latinProfile)
        assertTrue(moduleFromLatin is LanguageRenderModule.LatinModule)
        assertEquals(1.05f, moduleFromLatin.paddingMultiplier)
        assertEquals(0.96f, moduleFromLatin.ovalHeightMargin)
        assertEquals(7, moduleFromLatin.horizontalPaddingDp)
        assertEquals(5, moduleFromLatin.verticalPaddingDp)
        assertEquals(9, moduleFromLatin.minFontSize)
        assertEquals(13, moduleFromLatin.maxFontSize)
        assertTrue(moduleFromLatin.useHybridOverflow)
    }

    @Test
    fun `helper predicates check language codes properly`() {
        assertTrue(LanguageRenderModule.isCJKLanguage("ko"))
        assertTrue(LanguageRenderModule.isCJKLanguage("ja"))
        assertTrue(LanguageRenderModule.isCJKLanguage("zh-Hans"))
        assertFalse(LanguageRenderModule.isCJKLanguage("en"))

        assertTrue(LanguageRenderModule.isLatinLanguage("es"))
        assertTrue(LanguageRenderModule.isLatinLanguage("id"))
        assertFalse(LanguageRenderModule.isLatinLanguage("ja"))

        assertTrue(LanguageRenderModule.isSpanishOrIndonesianToEnglish("es", "en"))
        assertTrue(LanguageRenderModule.isSpanishOrIndonesianToEnglish("id", "english"))
        assertFalse(LanguageRenderModule.isSpanishOrIndonesianToEnglish("ko", "en"))

        assertTrue(LanguageRenderModule.isKoreanToEnglish("ko", "en"))
        assertTrue(LanguageRenderModule.isKoreanToEnglish("korean", "english"))
        assertFalse(LanguageRenderModule.isKoreanToEnglish("ja", "en"))
    }
}

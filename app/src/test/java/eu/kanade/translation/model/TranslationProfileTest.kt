package eu.kanade.translation.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.domain.translation.model.ProfileType
import tachiyomi.domain.translation.model.TranslationProfile

class TranslationProfileTest {
    @Test
    fun `ProfileType fromSourceLanguage identifies CJK and Latin correctly`() {
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("ja"))
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("ko"))
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("zh"))
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("zh-cn"))
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("zh-tw"))
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("zh-hant"))
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("zh-hans"))
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("korean"))
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("kor"))
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("japanese"))
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("jpn"))
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("chinese"))
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("zho"))
        assertEquals(ProfileType.LATIN, ProfileType.fromSourceLanguage("en"))
        assertEquals(ProfileType.LATIN, ProfileType.fromSourceLanguage("id"))
        assertEquals(ProfileType.LATIN, ProfileType.fromSourceLanguage("es"))
        assertEquals(ProfileType.LATIN, ProfileType.fromSourceLanguage("unknown"))
    }

    @Test
    fun `default profiles have correct defaults`() {
        val cjk = TranslationProfile.defaultCjk()
        assertEquals(ProfileType.CJK, cjk.type)
        assertEquals(2, cjk.textMargin)
        assertEquals(1.2f, cjk.paddingMultiplier)
        assertEquals(0.70f, cjk.ovalHeightMargin)
        assertEquals(8, cjk.horizontalPadding)
        assertEquals(6, cjk.verticalPadding)
        assertEquals(12, cjk.minFontSize)
        assertEquals(12, cjk.maxFontSize)
        assertEquals(1.3f, cjk.lineHeightMultiplier)
        assertEquals(1.2f, cjk.minExpansion)
        assertEquals(1.5f, cjk.maxExpansion)
        assertFalse(cjk.useHybridOverflow)

        val latin = TranslationProfile.defaultLatin()
        assertEquals(ProfileType.LATIN, latin.type)
        assertEquals(1, latin.textMargin)
        assertEquals(1.0f, latin.paddingMultiplier)
        assertEquals(0.95f, latin.ovalHeightMargin)
        assertEquals(4, latin.horizontalPadding)
        assertEquals(3, latin.verticalPadding)
        assertEquals(10, latin.minFontSize)
        assertEquals(14, latin.maxFontSize)
        assertEquals(1.15f, latin.lineHeightMultiplier)
        assertEquals(1.0f, latin.minExpansion)
        assertEquals(1.3f, latin.maxExpansion)
        assertTrue(latin.useHybridOverflow)
    }

    @Test
    fun `TranslationPreferences constructs TranslationProfile and resets defaults correctly`() {
        val store = InMemoryPreferenceStore()
        val prefs = TranslationPreferences(store)

        val cjkProfile = prefs.getProfile(ProfileType.CJK)
        assertEquals(ProfileType.CJK, cjkProfile.type)
        assertEquals(2, cjkProfile.textMargin)
        assertEquals(1.2f, cjkProfile.paddingMultiplier)
        assertEquals(0.70f, cjkProfile.ovalHeightMargin)
        assertEquals(8, cjkProfile.horizontalPadding)
        assertEquals(6, cjkProfile.verticalPadding)
        assertEquals(12, cjkProfile.minFontSize)
        assertEquals(12, cjkProfile.maxFontSize)
        assertEquals(1.3f, cjkProfile.lineHeightMultiplier)
        assertEquals(1.2f, cjkProfile.minExpansion)
        assertEquals(1.5f, cjkProfile.maxExpansion)
        assertFalse(cjkProfile.useHybridOverflow)

        val latinProfile = prefs.getProfile(ProfileType.LATIN)
        assertEquals(ProfileType.LATIN, latinProfile.type)
        assertEquals(1, latinProfile.textMargin)
        assertEquals(1.0f, latinProfile.paddingMultiplier)
        assertEquals(0.95f, latinProfile.ovalHeightMargin)
        assertEquals(4, latinProfile.horizontalPadding)
        assertEquals(3, latinProfile.verticalPadding)
        assertEquals(10, latinProfile.minFontSize)
        assertEquals(14, latinProfile.maxFontSize)
        assertEquals(1.15f, latinProfile.lineHeightMultiplier)
        assertEquals(1.0f, latinProfile.minExpansion)
        assertEquals(1.3f, latinProfile.maxExpansion)
        assertTrue(latinProfile.useHybridOverflow)

        // Modify and reset CJK
        prefs.cjkTextMargin.set(5)
        assertEquals(5, prefs.getProfile(ProfileType.CJK).textMargin)
        prefs.resetCJKDefaults()
        assertEquals(2, prefs.getProfile(ProfileType.CJK).textMargin)

        // Modify and reset Latin
        prefs.latinTextMargin.set(7)
        assertEquals(7, prefs.getProfile(ProfileType.LATIN).textMargin)
        prefs.resetLatinDefaults()
        assertEquals(1, prefs.getProfile(ProfileType.LATIN).textMargin)
    }
}

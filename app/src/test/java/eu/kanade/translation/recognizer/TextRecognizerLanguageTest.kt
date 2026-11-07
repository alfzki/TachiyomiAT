package eu.kanade.translation.recognizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class TextRecognizerLanguageTest {

    @Test
    fun `AUTO language entry exists and has correct label and code`() {
        assertEquals("Auto Detect", TextRecognizerLanguage.AUTO.label)
        assertEquals("", TextRecognizerLanguage.AUTO.code)
    }

    @Test
    fun `fromPref returns AUTO when preference is set to AUTO`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getString("translate_language_from", "AUTO")
        assertEquals(TextRecognizerLanguage.AUTO, TextRecognizerLanguage.fromPref(pref))
    }

    @Test
    fun `fromPref returns AUTO and sets preference when preference value is unknown or unset`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getString("translate_language_from", "UNKNOWN")
        assertEquals(TextRecognizerLanguage.AUTO, TextRecognizerLanguage.fromPref(pref))
        assertEquals("AUTO", pref.get())
    }

    @Test
    fun `fromPref returns matching language when preference is valid`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getString("translate_language_from", "JAPANESE")
        assertEquals(TextRecognizerLanguage.JAPANESE, TextRecognizerLanguage.fromPref(pref))
    }
}

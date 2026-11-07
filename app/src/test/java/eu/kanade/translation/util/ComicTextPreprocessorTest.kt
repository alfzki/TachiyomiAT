package eu.kanade.translation.util

import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.TextTranslatorLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComicTextPreprocessorTest {

    @Test
    fun `fixCommonComicOcrTypos replaces comic OCR artifacts`() {
        val input = "ANYNATSP! CONSERUENCESP! SD WHY PEAL WITH THIS? " +
            "HON AM I SUPPOSED TO LEAD VY TEAM SLDNLH? 1'M READY."
        val fixed = ComicTextPreprocessor.fixCommonComicOcrTypos(input)

        val expected = "ANYWAYS?! CONSEQUENCES?! SO WHY DEAL WITH THIS? " +
            "HOW AM I SUPPOSED TO LEAD MY TEAM SLOWLY? I'M READY."
        assertEquals(expected, fixed)
    }

    @Test
    fun `toSentenceCaseForNmt converts all-caps text to natural sentence case`() {
        val input = "IT'S WHY PEOPLE ALWAYS TREMBLE IN FEAR. I'M SORRY, MR. SUNG!"
        val result = ComicTextPreprocessor.toSentenceCaseForNmt(input)

        assertEquals("It's why people always tremble in fear. I'm sorry, Mr. Sung!", result)
    }

    @Test
    fun `toSentenceCaseForNmt preserves mixed case text`() {
        val input = "Hello World! This is already mixed case."
        val result = ComicTextPreprocessor.toSentenceCaseForNmt(input)

        assertEquals("Hello World! This is already mixed case.", result)
    }

    @Test
    fun `fixCommonComicOcrTypos fixes Martial Peak OCR corruptions`() {
        val input = "AT OTHEER SBGTS AT LEAST STL DISGIPLES EXUEND A HAND IN FRENDSHIP " +
            "BUT THATS NON EXISTENT HERE"
        val fixed = ComicTextPreprocessor.fixCommonComicOcrTypos(input)

        val expected = "AT OTHER SECTS AT LEAST STILL DISCIPLES EXTEND A HAND IN FRIENDSHIP " +
            "BUT THAT'S NON-EXISTENT HERE"
        assertEquals(expected, fixed)
    }

    @Test
    fun `fixCommonComicOcrTypos fixes contractions and stroke confusions`() {
        val input = "THEYVB TRIED TO EVIGT MB BEFORÉ, OBMOUSLY I DONT WANT " +
            "THAT TO CAPPEN AGAIN"
        val fixed = ComicTextPreprocessor.fixCommonComicOcrTypos(input)

        val expected = "THEY'VE TRIED TO EVICT ME BEFORE, OBVIOUSLY I DON'T WANT " +
            "THAT TO HAPPEN AGAIN"
        assertEquals(expected, fixed)
    }

    @Test
    fun `isLikelyOcrNoiseOrWatermark filters noise and watermarks`() {
        // Noise and artifacts
        assertTrue(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("I"))
        assertTrue(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("A"))
        assertTrue(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("."))
        assertTrue(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark(","))
        assertTrue(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("   "))
        assertTrue(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("RTMTH"))
        assertTrue(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("rtmth"))
        assertTrue(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("www.kakamanga.com"))
        assertTrue(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("Read at manga-scans.me"))
        assertTrue(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("00eeeoss 00ee00ed 0e0004"))
        assertTrue(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("000000 000000 00000"))

        // Valid comic speech
        assertFalse(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("..."))
        assertFalse(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("!?"))
        assertFalse(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("?!"))
        assertFalse(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("!"))
        assertFalse(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("?"))
        assertFalse(ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark("Hello hunter!"))
    }

    @Test
    fun `postProcessNmtOutput restores comic glossary terms for Indonesian`() {
        val source = "The Hunter enters the Dungeon with his Guild for a Raid."
        val rawIndo = "Pemburu memasuki penjara bawah tanah bersama serikat pekerja untuk penggerebekan."

        val processed = ComicTextPreprocessor.postProcessNmtOutput(
            sourceText = source,
            translatedText = rawIndo,
            toLang = TextTranslatorLanguage.INDONESIAN,
        )

        assertEquals("Hunter memasuki Dungeon bersama Guild untuk Raid.", processed)
    }

    @Test
    fun `postProcessNmtOutput strips leading trailing hyphens and fixes Tempered Body for Indonesian`() {
        val source = "He trained the Tempered Body technique."
        val rawIndo = "- Dia melatih teknik tubuh yang marah -"

        val processed = ComicTextPreprocessor.postProcessNmtOutput(
            sourceText = source,
            translatedText = rawIndo,
            toLang = TextTranslatorLanguage.INDONESIAN,
        )

        assertEquals("Dia melatih teknik Tempered Body", processed)
    }

    @Test
    fun `splitJoinedTranslation splits by delimiter or proportional word counts`() {
        val (a1, b1) = ComicTextPreprocessor.splitJoinedTranslation(
            joinedTranslation = "Apakah itu -- karena Asosiasi Hunter?",
            lenA = 5,
            lenB = 30,
            delimiter = " -- ",
        )
        assertEquals("Apakah itu", a1)
        assertEquals("karena Asosiasi Hunter?", b1)

        val (a2, b2) = ComicTextPreprocessor.splitJoinedTranslation(
            joinedTranslation = "Apakah itu karena Asosiasi Hunter?",
            lenA = 5,
            lenB = 30,
            delimiter = " -- ",
        )
        assertFalse(a2.isBlank())
        assertFalse(b2.isBlank())
        assertEquals("Apakah itu karena Asosiasi Hunter?", "$a2 $b2")

        val (a3, b3) = ComicTextPreprocessor.splitJoinedTranslation(
            joinedTranslation = "Bagian pertama - Bagian kedua",
            lenA = 10,
            lenB = 10,
        )
        assertEquals("Bagian pertama", a3)
        assertEquals("Bagian kedua", b3)
    }
}

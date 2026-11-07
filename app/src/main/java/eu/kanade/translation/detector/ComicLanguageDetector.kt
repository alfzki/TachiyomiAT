package eu.kanade.translation.detector

import eu.kanade.tachiyomi.source.Source
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import tachiyomi.domain.manga.model.Manga
import java.util.concurrent.ConcurrentHashMap

object ComicLanguageDetector {

    private val cache = ConcurrentHashMap<Long, TextRecognizerLanguage>()

    private val latinSourceLangs = setOf("en", "id", "es", "fr", "de", "it", "pt", "ru")

    fun isHangul(c: Char): Boolean =
        c in '\uAC00'..'\uD7AF' || c in '\u1100'..'\u11FF' || c in '\u3130'..'\u318F'

    fun isKana(c: Char): Boolean =
        c in '\u3040'..'\u309F' || c in '\u30A0'..'\u30FF' || c in '\u31F0'..'\u31FF'

    fun isCjkUnified(c: Char): Boolean =
        c in '\u4E00'..'\u9FFF' || c in '\u3400'..'\u4DBF' || c in '\uF900'..'\uFAFF'

    fun isLatin(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '\u00C0'..'\u024F'

    fun classifyScript(text: String): TextRecognizerLanguage? {
        if (text.isBlank()) return null

        var hangulCount = 0
        var kanaCount = 0
        var cjkCount = 0
        var latinCount = 0

        for (c in text) {
            when {
                isHangul(c) -> hangulCount++
                isKana(c) -> kanaCount++
                isCjkUnified(c) -> cjkCount++
                isLatin(c) -> latinCount++
            }
        }

        val totalRelevant = hangulCount + kanaCount + cjkCount + latinCount
        if (totalRelevant == 0) return null

        return when {
            hangulCount > 0 && hangulCount >= kanaCount ->
                TextRecognizerLanguage.KOREAN

            kanaCount >= 2 || (kanaCount > 0 && kanaCount + cjkCount > latinCount) ->
                TextRecognizerLanguage.JAPANESE

            cjkCount > 0 && kanaCount == 0 && hangulCount == 0 && cjkCount >= latinCount ->
                TextRecognizerLanguage.CHINESE

            latinCount > totalRelevant * 0.5f ->
                TextRecognizerLanguage.ENGLISH

            else -> null
        }
    }

    fun detectFromMetadata(sourceLang: String?, title: String?, description: String?): TextRecognizerLanguage? {
        val normalizedLang = sourceLang?.trim()?.lowercase()
        if (!normalizedLang.isNullOrEmpty()) {
            if (normalizedLang.startsWith("ja")) return TextRecognizerLanguage.JAPANESE
            if (normalizedLang.startsWith("ko")) return TextRecognizerLanguage.KOREAN
            if (normalizedLang.startsWith("zh")) return TextRecognizerLanguage.CHINESE

            val baseLang = normalizedLang.substringBefore("-").substringBefore("_")
            if (baseLang in latinSourceLangs) {
                return TextRecognizerLanguage.ENGLISH
            }
        }

        if (!title.isNullOrBlank()) {
            classifyScript(title)?.let { return it }
        }

        if (!description.isNullOrBlank()) {
            classifyScript(description)?.let { return it }
        }

        return null
    }

    suspend fun detectLanguage(
        mangaId: Long,
        sourceLang: String?,
        title: String?,
        description: String?,
        sampleTextProvider: (suspend () -> String?)? = null,
    ): TextRecognizerLanguage {
        cache[mangaId]?.let { return it }

        if (sampleTextProvider != null) {
            val sampleText = try {
                sampleTextProvider()
            } catch (_: Throwable) {
                null
            }
            if (!sampleText.isNullOrBlank()) {
                val detected = classifyScript(sampleText)
                if (detected != null) {
                    cache[mangaId] = detected
                    return detected
                }
            }
        }

        val metaDetected = detectFromMetadata(sourceLang, title, description)
        val finalLang = metaDetected ?: TextRecognizerLanguage.ENGLISH
        cache[mangaId] = finalLang
        return finalLang
    }

    suspend fun detectLanguage(
        source: Source?,
        manga: Manga,
        sampleTextProvider: (suspend () -> String?)? = null,
    ): TextRecognizerLanguage = detectLanguage(
        mangaId = manga.id,
        sourceLang = source?.lang,
        title = manga.title,
        description = manga.description,
        sampleTextProvider = sampleTextProvider,
    )

    fun getCachedLanguage(mangaId: Long): TextRecognizerLanguage? = cache[mangaId]

    fun setCachedLanguage(mangaId: Long, lang: TextRecognizerLanguage) {
        cache[mangaId] = lang
    }

    fun clearCache() {
        cache.clear()
    }
}

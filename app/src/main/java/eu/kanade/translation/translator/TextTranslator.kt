package eu.kanade.translation.translator

import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationContext
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.translation.TranslationPreferences
import java.io.Closeable

interface TextTranslator : Closeable {
    val fromLang: TextRecognizerLanguage
    val toLang: TextTranslatorLanguage
    suspend fun translate(pages: MutableMap<String, PageTranslation>)
    suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        context: TranslationContext,
    ) = translate(pages)
}

enum class TextTranslators(val label: String) {
    MLKIT("MlKit (On Device)"),
    GOOGLE("Google Translate"),
    GEMINI("Gemini AI [API KEY]"),
    OPENROUTER("OpenRouter [API KEY]"),
    AICORE("AICore (On Device NPU)"),
    ;

    fun build(
        pref: TranslationPreferences,
        fromLang: TextRecognizerLanguage = TextRecognizerLanguage.fromPref(pref.translateFromLanguage()),
        toLang: TextTranslatorLanguage = TextTranslatorLanguage.fromPref(pref.translateToLanguage()),
    ): TextTranslator {
        val maxOutputTokens = pref.translationEngineMaxOutputTokens().get().toIntOrNull() ?: 8914
        val temperature = pref.translationEngineTemperature().get().toFloatOrNull() ?: 1.0f
        val modelName = pref.translationEngineModel().get()
        val apiKey = pref.translationEngineApiKey().get()
        return when (this) {
            MLKIT -> MLKitTranslator(fromLang, toLang)
            GOOGLE -> GoogleTranslator(fromLang, toLang)
            GEMINI -> GeminiTranslator(fromLang, toLang, apiKey, modelName, maxOutputTokens, temperature)
            OPENROUTER -> OpenRouterTranslator(fromLang, toLang, apiKey, modelName, maxOutputTokens, temperature)
            AICORE -> AICoreTranslator(
                fromLang,
                toLang,
                maxOutputTokens,
                temperature,
                pref.aicoreUsePageImageContext().get(),
                AICoreModelVariant.fromPref(pref.aicoreModelVariant().get()),
            )
        }
    }

    companion object {
        fun fromPref(pref: Preference<Int>): TextTranslators {
            var translator = entries.getOrNull(pref.get())
            if (translator == null) {
                pref.set(0)
                return MLKIT
            }
            return translator
        }
    }
}

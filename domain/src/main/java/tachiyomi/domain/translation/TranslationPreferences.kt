package tachiyomi.domain.translation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {
    val autoTranslateAfterDownload: Preference<Boolean> =
        preferenceStore.getBoolean("auto_translate_after_download", false)

    val translateFromLanguage: Preference<String> =
        preferenceStore.getString("translate_language_from", "CHINESE")

    val translateToLanguage: Preference<String> =
        preferenceStore.getString("translate_language_to", "ENGLISH")

    val translationFont: Preference<Int> =
        preferenceStore.getInt("translation_font", 0)

    val translationEngine: Preference<Int> =
        preferenceStore.getInt("translation_engine", 0)

    val translationEngineModel: Preference<String> =
        preferenceStore.getString("translation_engine_model", "gemini-1.5-pro")

    val translationEngineApiKey: Preference<String> =
        preferenceStore.getString("translation_engine_api_key", "")

    val translationEngineTemperature: Preference<String> =
        preferenceStore.getString("translation_engine_temperature", "1")

    val translationEngineMaxOutputTokens: Preference<String> =
        preferenceStore.getString("translation_engine_output_tokens", "8192")

    val aicoreUsePageImageContext: Preference<Boolean> =
        preferenceStore.getBoolean("aicore_use_page_image_context", false)

    fun autoTranslateAfterDownload() = autoTranslateAfterDownload
    fun translateFromLanguage() = translateFromLanguage
    fun translateToLanguage() = translateToLanguage
    fun translationFont() = translationFont
    fun translationEngine() = translationEngine
    fun translationEngineModel() = translationEngineModel
    fun translationEngineApiKey() = translationEngineApiKey
    fun translationEngineTemperature() = translationEngineTemperature
    fun translationEngineMaxOutputTokens() = translationEngineMaxOutputTokens
    fun aicoreUsePageImageContext() = aicoreUsePageImageContext
}

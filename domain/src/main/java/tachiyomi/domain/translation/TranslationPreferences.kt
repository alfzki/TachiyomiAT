package tachiyomi.domain.translation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.translation.model.ProfileType
import tachiyomi.domain.translation.model.TranslationProfile

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
        preferenceStore.getString("translation_engine_model", "gemini-3.5-flash-lite")

    val translationEngineApiKey: Preference<String> =
        preferenceStore.getString("translation_engine_api_key", "")

    val translationEngineTemperature: Preference<String> =
        preferenceStore.getString("translation_engine_temperature", "1")

    val translationEngineMaxOutputTokens: Preference<String> =
        preferenceStore.getString("translation_engine_output_tokens", "8192")

    val aicoreUsePageImageContext: Preference<Boolean> =
        preferenceStore.getBoolean("aicore_use_page_image_context", false)

    val aicoreModelVariant: Preference<Int> =
        preferenceStore.getInt("aicore_model_variant", 2)

    // Google Cloud Translation preferences
    val cloudTranslationApiKey: Preference<String> =
        preferenceStore.getString("cloud_translation_api_key", "")

    val cloudTranslationDetectionMethod: Preference<String> =
        preferenceStore.getString("cloud_translation_detection_method", "mlkit")

    // CJK Profile Preferences
    val cjkTextMargin: Preference<Int> =
        preferenceStore.getInt("cjk_text_margin", 2)

    val cjkPaddingMultiplier: Preference<Float> =
        preferenceStore.getFloat("cjk_padding_multiplier", 1.2f)

    val cjkOvalHeightMargin: Preference<Float> =
        preferenceStore.getFloat("cjk_oval_height_margin", 0.70f)

    val cjkHorizontalPadding: Preference<Int> =
        preferenceStore.getInt("cjk_horizontal_padding", 8)

    val cjkVerticalPadding: Preference<Int> =
        preferenceStore.getInt("cjk_vertical_padding", 6)

    val cjkMinFontSize: Preference<Int> =
        preferenceStore.getInt("cjk_min_font_size", 12)

    val cjkMaxFontSize: Preference<Int> =
        preferenceStore.getInt("cjk_max_font_size", 12)

    val cjkLineHeightMultiplier: Preference<Float> =
        preferenceStore.getFloat("cjk_line_height_multiplier", 1.3f)

    val cjkMinExpansion: Preference<Float> =
        preferenceStore.getFloat("cjk_min_expansion", 1.2f)

    val cjkMaxExpansion: Preference<Float> =
        preferenceStore.getFloat("cjk_max_expansion", 1.5f)

    val cjkUseHybridOverflow: Preference<Boolean> =
        preferenceStore.getBoolean("cjk_use_hybrid_overflow", false)

    // Latin Profile Preferences
    val latinTextMargin: Preference<Int> =
        preferenceStore.getInt("latin_text_margin", 1)

    val latinPaddingMultiplier: Preference<Float> =
        preferenceStore.getFloat("latin_padding_multiplier", 1.0f)

    val latinOvalHeightMargin: Preference<Float> =
        preferenceStore.getFloat("latin_oval_height_margin", 0.95f)

    val latinHorizontalPadding: Preference<Int> =
        preferenceStore.getInt("latin_horizontal_padding", 4)

    val latinVerticalPadding: Preference<Int> =
        preferenceStore.getInt("latin_vertical_padding", 3)

    val latinMinFontSize: Preference<Int> =
        preferenceStore.getInt("latin_min_font_size", 10)

    val latinMaxFontSize: Preference<Int> =
        preferenceStore.getInt("latin_max_font_size", 14)

    val latinLineHeightMultiplier: Preference<Float> =
        preferenceStore.getFloat("latin_line_height_multiplier", 1.15f)

    val latinMinExpansion: Preference<Float> =
        preferenceStore.getFloat("latin_min_expansion", 1.0f)

    val latinMaxExpansion: Preference<Float> =
        preferenceStore.getFloat("latin_max_expansion", 1.3f)

    val latinUseHybridOverflow: Preference<Boolean> =
        preferenceStore.getBoolean("latin_use_hybrid_overflow", true)

    // Function accessors for backwards compatibility and clean API
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
    fun aicoreModelVariant() = aicoreModelVariant

    fun cloudTranslationApiKey() = cloudTranslationApiKey
    fun cloudTranslationDetectionMethod() = cloudTranslationDetectionMethod
    fun translationLanguageDetectionMethod() = cloudTranslationDetectionMethod

    fun cjkTextMargin() = cjkTextMargin
    fun cjkPaddingMultiplier() = cjkPaddingMultiplier
    fun cjkOvalHeightMargin() = cjkOvalHeightMargin
    fun cjkHorizontalPadding() = cjkHorizontalPadding
    fun cjkVerticalPadding() = cjkVerticalPadding
    fun cjkMinFontSize() = cjkMinFontSize
    fun cjkMaxFontSize() = cjkMaxFontSize
    fun cjkLineHeightMultiplier() = cjkLineHeightMultiplier
    fun cjkMinExpansion() = cjkMinExpansion
    fun cjkMaxExpansion() = cjkMaxExpansion
    fun cjkUseHybridOverflow() = cjkUseHybridOverflow

    fun latinTextMargin() = latinTextMargin
    fun latinPaddingMultiplier() = latinPaddingMultiplier
    fun latinOvalHeightMargin() = latinOvalHeightMargin
    fun latinHorizontalPadding() = latinHorizontalPadding
    fun latinVerticalPadding() = latinVerticalPadding
    fun latinMinFontSize() = latinMinFontSize
    fun latinMaxFontSize() = latinMaxFontSize
    fun latinLineHeightMultiplier() = latinLineHeightMultiplier
    fun latinMinExpansion() = latinMinExpansion
    fun latinMaxExpansion() = latinMaxExpansion
    fun latinUseHybridOverflow() = latinUseHybridOverflow

    /**
     * Constructs a TranslationProfile from current preferences.
     */
    fun getProfile(type: ProfileType): TranslationProfile = when (type) {
        ProfileType.CJK -> TranslationProfile(
            type = ProfileType.CJK,
            textMargin = cjkTextMargin.get(),
            paddingMultiplier = cjkPaddingMultiplier.get(),
            ovalHeightMargin = cjkOvalHeightMargin.get(),
            horizontalPadding = cjkHorizontalPadding.get(),
            verticalPadding = cjkVerticalPadding.get(),
            minFontSize = cjkMinFontSize.get(),
            maxFontSize = cjkMaxFontSize.get(),
            lineHeightMultiplier = cjkLineHeightMultiplier.get(),
            minExpansion = cjkMinExpansion.get(),
            maxExpansion = cjkMaxExpansion.get(),
            useHybridOverflow = cjkUseHybridOverflow.get(),
        )
        ProfileType.LATIN -> TranslationProfile(
            type = ProfileType.LATIN,
            textMargin = latinTextMargin.get(),
            paddingMultiplier = latinPaddingMultiplier.get(),
            ovalHeightMargin = latinOvalHeightMargin.get(),
            horizontalPadding = latinHorizontalPadding.get(),
            verticalPadding = latinVerticalPadding.get(),
            minFontSize = latinMinFontSize.get(),
            maxFontSize = latinMaxFontSize.get(),
            lineHeightMultiplier = latinLineHeightMultiplier.get(),
            minExpansion = latinMinExpansion.get(),
            maxExpansion = latinMaxExpansion.get(),
            useHybridOverflow = latinUseHybridOverflow.get(),
        )
    }

    /**
     * Get CJK TranslationProfile snapshot.
     */
    fun getCJKProfile(): TranslationProfile = getProfile(ProfileType.CJK)

    /**
     * Get Latin TranslationProfile snapshot.
     */
    fun getLatinProfile(): TranslationProfile = getProfile(ProfileType.LATIN)

    /**
     * Get TranslationProfile for a given source language.
     */
    fun getProfileForLanguage(sourceLanguage: String): TranslationProfile {
        return getProfile(ProfileType.fromSourceLanguage(sourceLanguage))
    }

    /**
     * Flow of CJK profile emitting whenever any CJK preference changes.
     */
    fun cjkProfileChanges(): Flow<TranslationProfile> {
        val flows: List<Flow<Any?>> = listOf(
            cjkTextMargin.changes(),
            cjkPaddingMultiplier.changes(),
            cjkOvalHeightMargin.changes(),
            cjkHorizontalPadding.changes(),
            cjkVerticalPadding.changes(),
            cjkMinFontSize.changes(),
            cjkMaxFontSize.changes(),
            cjkLineHeightMultiplier.changes(),
            cjkMinExpansion.changes(),
            cjkMaxExpansion.changes(),
            cjkUseHybridOverflow.changes(),
        )
        return combine(flows) {
            getProfile(ProfileType.CJK)
        }
    }

    /**
     * Flow of Latin profile emitting whenever any Latin preference changes.
     */
    fun latinProfileChanges(): Flow<TranslationProfile> {
        val flows: List<Flow<Any?>> = listOf(
            latinTextMargin.changes(),
            latinPaddingMultiplier.changes(),
            latinOvalHeightMargin.changes(),
            latinHorizontalPadding.changes(),
            latinVerticalPadding.changes(),
            latinMinFontSize.changes(),
            latinMaxFontSize.changes(),
            latinLineHeightMultiplier.changes(),
            latinMinExpansion.changes(),
            latinMaxExpansion.changes(),
            latinUseHybridOverflow.changes(),
        )
        return combine(flows) {
            getProfile(ProfileType.LATIN)
        }
    }

    /**
     * Flow of TranslationProfile for a given source language emitting on preference changes.
     */
    fun profileChangesForLanguage(sourceLanguage: String): Flow<TranslationProfile> {
        return when (ProfileType.fromSourceLanguage(sourceLanguage)) {
            ProfileType.CJK -> cjkProfileChanges()
            ProfileType.LATIN -> latinProfileChanges()
        }
    }

    /**
     * Reset CJK display preferences to defaults.
     */
    fun resetCJKDefaults() {
        cjkTextMargin.set(2)
        cjkPaddingMultiplier.set(1.2f)
        cjkOvalHeightMargin.set(0.70f)
        cjkHorizontalPadding.set(8)
        cjkVerticalPadding.set(6)
        cjkMinFontSize.set(12)
        cjkMaxFontSize.set(12)
        cjkLineHeightMultiplier.set(1.3f)
        cjkMinExpansion.set(1.2f)
        cjkMaxExpansion.set(1.5f)
        cjkUseHybridOverflow.set(false)
    }

    /**
     * Reset Latin display preferences to defaults.
     */
    fun resetLatinDefaults() {
        latinTextMargin.set(1)
        latinPaddingMultiplier.set(1.0f)
        latinOvalHeightMargin.set(0.95f)
        latinHorizontalPadding.set(4)
        latinVerticalPadding.set(3)
        latinMinFontSize.set(10)
        latinMaxFontSize.set(14)
        latinLineHeightMultiplier.set(1.15f)
        latinMinExpansion.set(1.0f)
        latinMaxExpansion.set(1.3f)
        latinUseHybridOverflow.set(true)
    }

    companion object {
        const val DEFAULT_CJK_TEXT_MARGIN = 2
        const val DEFAULT_CJK_PADDING_MULTIPLIER = 1.2f
        const val DEFAULT_CJK_OVAL_HEIGHT_MARGIN = 0.70f
        const val DEFAULT_CJK_HORIZONTAL_PADDING = 8
        const val DEFAULT_CJK_VERTICAL_PADDING = 6

        const val DEFAULT_LATIN_TEXT_MARGIN = 1
        const val DEFAULT_LATIN_PADDING_MULTIPLIER = 1.0f
        const val DEFAULT_LATIN_OVAL_HEIGHT_MARGIN = 0.95f
        const val DEFAULT_LATIN_HORIZONTAL_PADDING = 4
        const val DEFAULT_LATIN_VERTICAL_PADDING = 3
    }
}

package eu.kanade.translation.presentation

import tachiyomi.domain.translation.model.ProfileType
import tachiyomi.domain.translation.model.TranslationProfile

/**
 * Language-specific rendering module for translation bubbles.
 *
 * This sealed class provides separate rendering configurations for:
 * - CJK languages (Korean, Japanese, Chinese) - More conservative margins, fixed 12dp font
 * - Latin languages (Spanish, Indonesian) - More generous margins, hybrid overflow strategy
 *
 * The module system ensures CJK behavior remains intact while fixing text clipping
 * issues for Spanish/Indonesian translations.
 *
 * Modules can be created from a TranslationProfile for preference-backed configuration.
 */
sealed class LanguageRenderModule {
    // Padding multiplier applied to symbol dimensions
    abstract val paddingMultiplier: Float

    // Safety margins for bubble shapes
    abstract val ovalHeightMargin: Float
    abstract val verticalOvalWidthMargin: Float
    abstract val rectHeightMargin: Float
    abstract val rectWidthMargin: Float

    // Line height multiplier for text layout
    abstract val lineHeightMultiplier: Float

    // Bubble expansion range
    abstract val minExpansion: Float
    abstract val maxExpansion: Float

    // Padding values in dp
    abstract val horizontalPaddingDp: Int
    abstract val verticalPaddingDp: Int
    abstract val ovalVerticalPaddingDp: Int
    abstract val ovalHorizontalPaddingDp: Int

    // Font size constraints
    abstract val maxFontSize: Int
    abstract val minFontSize: Int

    // Whether to use hybrid overflow (expand bubble first, then reduce font)
    abstract val useHybridOverflow: Boolean

    /**
     * CJK Module for Korean, Japanese, and Chinese languages.
     *
     * Uses conservative height margins (70%) to avoid curved edges in ovals.
     * Font size stays fixed at 12dp with aggressive bubble expansion (1.8-3.0x).
     */
    data class CJKModule(
        private val sourceLanguage: String = "",
        override val paddingMultiplier: Float = when (sourceLanguage.lowercase()) {
            "ko", "korean", "kor" -> 1.15f
            "ja", "japanese", "jpn" -> 1.2f
            else -> 1.1f // Chinese, etc.
        },
        override val ovalHeightMargin: Float = 0.70f,
        override val verticalOvalWidthMargin: Float = 0.70f,
        override val rectHeightMargin: Float = 0.99f,
        override val rectWidthMargin: Float = 0.98f,
        override val lineHeightMultiplier: Float = 1.4f,
        override val minExpansion: Float = 1.8f,
        override val maxExpansion: Float = 2.5f,
        override val horizontalPaddingDp: Int = 8,
        override val verticalPaddingDp: Int = 2,
        override val ovalVerticalPaddingDp: Int = 10,
        override val ovalHorizontalPaddingDp: Int = 4,
        override val maxFontSize: Int = 12,
        override val minFontSize: Int = 12, // CJK keeps fixed 12dp font
        override val useHybridOverflow: Boolean = false,
    ) : LanguageRenderModule() {

        /**
         * Get expansion constants for Korean translations specifically.
         * Cloud Translation produces longer output, so applies additional multiplier.
         */
        fun getKoreanExpansionConstants(isCloudTranslation: Boolean): Pair<Float, Float> {
            return if (isCloudTranslation) {
                2.2f to 3.0f // Cloud Translation: more aggressive expansion
            } else {
                1.8f to 2.5f // Gemini/OpenRouter: standard expansion
            }
        }
    }

    /**
     * Latin Module for Spanish, Indonesian, and other Latin-alphabet languages.
     *
     * Uses generous height margins (95%) to prevent text clipping.
     * Implements hybrid overflow: expand bubble up to 1.3x first, then reduce font to 10dp if needed.
     * Tighter line height (1.2x) to pack lines closer together.
     */
    data class LatinModule(
        override val paddingMultiplier: Float = 1.0f,
        override val ovalHeightMargin: Float = 0.95f, // Much more generous than CJK
        override val verticalOvalWidthMargin: Float = 0.75f,
        override val rectHeightMargin: Float = 0.99f,
        override val rectWidthMargin: Float = 0.98f,
        override val lineHeightMultiplier: Float = 1.2f, // Tighter line height
        override val minExpansion: Float = 1.0f, // Start at base size
        override val maxExpansion: Float = 1.3f, // Max 1.3x expansion
        override val horizontalPaddingDp: Int = 6, // Slightly less horizontal padding
        override val verticalPaddingDp: Int = 4, // More vertical padding for Latin chars
        override val ovalVerticalPaddingDp: Int = 6, // Less aggressive oval padding
        override val ovalHorizontalPaddingDp: Int = 4,
        override val maxFontSize: Int = 12, // Start with 12dp
        override val minFontSize: Int = 10, // Can reduce to 10dp as fallback
        override val useHybridOverflow: Boolean = true,
    ) : LanguageRenderModule()

    companion object {
        // CJK language codes
        private val CJK_LANGUAGES = listOf(
            "ko", "korean", "kor",
            "ja", "japanese", "jpn",
            "zh", "chinese", "zho", "zh-cn", "zh-tw", "zh-hant", "zh-hans",
        )

        // Latin languages explicitly handled (Spanish, Indonesian)
        private val LATIN_LANGUAGES = listOf(
            "es",
            "spanish",
            "spa",
            "id",
            "in",
            "indonesian",
            "ind",
        )

        /**
         * Create the appropriate language module based on the source language.
         *
         * @param language The source language code from OCR/detection
         * @return CJKModule for CJK languages, LatinModule for Spanish/Indonesian and others
         */
        fun fromSourceLanguage(language: String): LanguageRenderModule {
            val lang = language.lowercase().trim()
            return when {
                lang in CJK_LANGUAGES -> CJKModule(lang)
                else -> LatinModule() // Spanish, Indonesian, English, etc.
            }
        }

        /**
         * Create a language module from a TranslationProfile.
         * This enables preference-backed configuration of rendering parameters.
         *
         * @param profile The TranslationProfile containing user preferences
         * @param sourceLanguage The source language code (for CJK-specific adjustments)
         * @return LanguageRenderModule configured with profile values
         */
        fun fromProfile(profile: TranslationProfile, sourceLanguage: String = ""): LanguageRenderModule {
            return when (profile.type) {
                ProfileType.CJK -> CJKModule(
                    sourceLanguage = sourceLanguage,
                    paddingMultiplier = profile.paddingMultiplier,
                    ovalHeightMargin = profile.ovalHeightMargin,
                    lineHeightMultiplier = profile.lineHeightMultiplier,
                    minExpansion = profile.minExpansion,
                    maxExpansion = profile.maxExpansion,
                    horizontalPaddingDp = profile.horizontalPadding,
                    verticalPaddingDp = profile.verticalPadding,
                    maxFontSize = profile.maxFontSize,
                    minFontSize = profile.minFontSize,
                    useHybridOverflow = profile.useHybridOverflow,
                )
                ProfileType.LATIN -> LatinModule(
                    paddingMultiplier = profile.paddingMultiplier,
                    ovalHeightMargin = profile.ovalHeightMargin,
                    lineHeightMultiplier = profile.lineHeightMultiplier,
                    minExpansion = profile.minExpansion,
                    maxExpansion = profile.maxExpansion,
                    horizontalPaddingDp = profile.horizontalPadding,
                    verticalPaddingDp = profile.verticalPadding,
                    maxFontSize = profile.maxFontSize,
                    minFontSize = profile.minFontSize,
                    useHybridOverflow = profile.useHybridOverflow,
                )
            }
        }

        /**
         * Check if a language is CJK (Korean, Japanese, Chinese).
         */
        fun isCJKLanguage(language: String): Boolean {
            return language.lowercase().trim() in CJK_LANGUAGES
        }

        /**
         * Check if a language is explicitly Latin (Spanish, Indonesian).
         */
        fun isLatinLanguage(language: String): Boolean {
            return language.lowercase().trim() in LATIN_LANGUAGES
        }

        /**
         * Check if this is a Spanish or Indonesian source translating to English.
         * Used for special handling of these language pairs.
         */
        fun isSpanishOrIndonesianToEnglish(sourceLanguage: String, targetLanguage: String): Boolean {
            val source = sourceLanguage.lowercase().trim()
            val target = targetLanguage.lowercase().trim()

            val isLatinSource = source in listOf("es", "spanish", "spa", "id", "in", "indonesian", "ind")
            val isEnglishTarget = target in listOf("en", "english", "eng")

            return isLatinSource && isEnglishTarget
        }

        /**
         * Check if this is a Korean to English translation pair.
         */
        fun isKoreanToEnglish(sourceLanguage: String, targetLanguage: String): Boolean {
            val source = sourceLanguage.lowercase().trim()
            val target = targetLanguage.lowercase().trim()

            val isKoreanSource = source in listOf("ko", "korean", "kor")
            val isEnglishTarget = target in listOf("en", "english", "eng")

            return isKoreanSource && isEnglishTarget
        }
    }
}

/**
 * Result class for hybrid overflow calculation in Latin module.
 */
data class LatinBubbleResult(
    val width: Float,
    val height: Float,
    val fontSize: Int,
    val expansionRatio: Float,
)

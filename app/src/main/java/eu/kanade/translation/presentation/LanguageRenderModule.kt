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
     * Latin Module for Spanish, Indonesian, English, and other Latin-alphabet languages.
     *
     * Uses generous height margins (95%) to prevent text clipping.
     * Implements hybrid overflow with adaptive font sizing (8sp - 13sp).
     * Tighter line height (1.15x) to pack lines closer together.
     */
    data class LatinModule(
        override val paddingMultiplier: Float = 1.0f,
        override val ovalHeightMargin: Float = 0.95f, // Generous height margin
        override val verticalOvalWidthMargin: Float = 0.85f, // Generous width margin for vertical bubbles
        override val rectHeightMargin: Float = 0.99f,
        override val rectWidthMargin: Float = 0.98f,
        override val lineHeightMultiplier: Float = 1.15f, // Tighter line height for comic readability
        override val minExpansion: Float = 1.0f, // Start at base size
        override val maxExpansion: Float = 1.4f, // Allow up to 1.4x expansion
        override val horizontalPaddingDp: Int = 4, // Clean horizontal padding
        override val verticalPaddingDp: Int = 3, // Clean vertical padding
        override val ovalVerticalPaddingDp: Int = 4, // Clean oval padding
        override val ovalHorizontalPaddingDp: Int = 4,
        override val maxFontSize: Int = 13, // Start with 13sp
        override val minFontSize: Int = 8, // Allow down to 8sp to prevent mid-word breaking
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
            "en",
            "english",
            "eng",
            "fr",
            "french",
            "de",
            "german",
            "it",
            "italian",
            "pt",
            "portuguese",
        )

        /**
         * Create the appropriate language module based on the target language and rendered text.
         * The rendered text script (target language) dictates typography and constraints.
         */
        fun forTranslation(
            targetLanguage: String,
            sourceLanguage: String = "",
            textSample: String = "",
        ): LanguageRenderModule {
            val target = targetLanguage.lowercase().trim()
            val isTargetCJK = target in CJK_LANGUAGES
            val hasCJKChars = textSample.any { char ->
                val code = char.code
                (code in 0x4E00..0x9FFF) || (code in 0xAC00..0xD7AF) || (code in 0x3040..0x30FF)
            }
            return when {
                isTargetCJK || hasCJKChars -> CJKModule(if (isTargetCJK) target else sourceLanguage)
                else -> LatinModule()
            }
        }

        /**
         * Create the appropriate language module based on the source language.
         * Kept for backward compatibility.
         */
        fun fromSourceLanguage(language: String): LanguageRenderModule {
            val lang = language.lowercase().trim()
            return when {
                lang in CJK_LANGUAGES -> CJKModule(lang)
                else -> LatinModule()
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

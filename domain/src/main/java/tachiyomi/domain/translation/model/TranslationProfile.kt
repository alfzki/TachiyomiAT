package tachiyomi.domain.translation.model

/**
 * Type of translation profile based on source language characteristics.
 *
 * CJK: Korean, Japanese, Chinese - Fixed font size, aggressive bubble expansion
 * LATIN: Spanish, Indonesian, English, etc. - Flexible font, conservative expansion
 */
enum class ProfileType {
    CJK,
    LATIN;

    companion object {
        /**
         * Determine profile type from source language code or name.
         */
        fun fromSourceLanguage(sourceLanguage: String): ProfileType = when (sourceLanguage.lowercase()) {
            "ko", "korean", "kor",
            "ja", "japanese", "jpn",
            "zh", "chinese", "zho", "zh-cn", "zh-tw", "zh-hant", "zh-hans" -> CJK
            else -> LATIN
        }
    }
}

/**
 * Translation profile containing all bubble rendering parameters.
 *
 * This is a computed snapshot of preferences - not persisted directly.
 * Changes to individual preferences are reflected through the profile factory.
 *
 * @property type Profile type (CJK or LATIN)
 * @property textMargin Margin from bubble edges in pixels (0-8)
 * @property paddingMultiplier Symbol dimension padding multiplier (1.0-1.5)
 * @property ovalHeightMargin Percentage of bubble height for text in ovals (0.5-0.99)
 * @property horizontalPadding Horizontal padding in dp (2-12)
 * @property verticalPadding Vertical padding in dp (2-10)
 * @property minFontSize Minimum font size in dp (8-12)
 * @property maxFontSize Maximum font size in dp (12-16)
 * @property lineHeightMultiplier Line height multiplier (1.0-1.6)
 * @property minExpansion Minimum bubble expansion ratio (1.0-2.0)
 * @property maxExpansion Maximum bubble expansion ratio (1.5-3.0)
 * @property useHybridOverflow Whether to expand bubble first, then reduce font
 */
data class TranslationProfile(
    val type: ProfileType,
    val textMargin: Int,
    val paddingMultiplier: Float,
    val ovalHeightMargin: Float,
    val horizontalPadding: Int,
    val verticalPadding: Int,
    val minFontSize: Int,
    val maxFontSize: Int,
    val lineHeightMultiplier: Float,
    val minExpansion: Float,
    val maxExpansion: Float,
    val useHybridOverflow: Boolean,
) {
    companion object {
        /**
         * Default CJK profile (Korean/Japanese/Chinese)
         * - Fixed font size at 12dp
         * - Aggressive bubble expansion (1.2x - 1.5x)
         * - Conservative 70% oval height margin
         */
        fun defaultCjk(): TranslationProfile = TranslationProfile(
            type = ProfileType.CJK,
            textMargin = 2,
            paddingMultiplier = 1.2f,
            ovalHeightMargin = 0.70f,
            horizontalPadding = 8,
            verticalPadding = 6,
            minFontSize = 12,
            maxFontSize = 12,
            lineHeightMultiplier = 1.3f,
            minExpansion = 1.2f,
            maxExpansion = 1.5f,
            useHybridOverflow = false,
        )

        /**
         * Default Latin profile (Spanish/Indonesian/English/etc.)
         * - Flexible font size (10-14dp)
         * - Conservative bubble expansion (1.0x - 1.3x)
         * - Generous 95% oval height margin
         */
        fun defaultLatin(): TranslationProfile = TranslationProfile(
            type = ProfileType.LATIN,
            textMargin = 1,
            paddingMultiplier = 1.0f,
            ovalHeightMargin = 0.95f,
            horizontalPadding = 4,
            verticalPadding = 3,
            minFontSize = 10,
            maxFontSize = 14,
            lineHeightMultiplier = 1.15f,
            minExpansion = 1.0f,
            maxExpansion = 1.3f,
            useHybridOverflow = true,
        )

        val DEFAULT_CJK: TranslationProfile = defaultCjk()
        val DEFAULT_LATIN: TranslationProfile = defaultLatin()
    }
}

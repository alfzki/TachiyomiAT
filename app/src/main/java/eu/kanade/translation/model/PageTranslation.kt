package eu.kanade.translation.model

import kotlinx.serialization.Serializable
import tachiyomi.domain.translation.model.ProfileType

@Serializable
data class PageTranslation(
    var blocks: MutableList<TranslationBlock> = mutableListOf(),
    var imgWidth: Float = 0f,
    var imgHeight: Float = 0f,
    var sourceLanguage: String = "auto", // Store source language for rendering optimizations
    var targetLanguage: String = "en", // Store target language for translation pair optimizations
    // Store translator engine type (cloud, gemini, mlkit, openrouter) for rendering optimizations
    var translatorType: String = "gemini",
) {
    /**
     * Computed property that returns the profile type based on source language.
     * CJK languages (Korean, Japanese, Chinese) use the CJK profile,
     * all others (Spanish, Indonesian, etc.) use the Latin profile.
     */
    val profileType: ProfileType
        get() = ProfileType.fromSourceLanguage(sourceLanguage)

    /**
     * Active (non-deleted and non-empty) blocks on the page.
     */
    val activeBlocks: List<TranslationBlock>
        get() = blocks.filter { it.translation.isNotBlank() }

    /**
     * Delete a block by index.
     * @return true if the block was found and removed, false otherwise.
     */
    fun deleteBlock(index: Int): Boolean {
        if (index in blocks.indices) {
            blocks.removeAt(index)
            return true
        }
        return false
    }

    /**
     * Delete a block by reference.
     * @return true if the block was found and removed, false otherwise.
     */
    fun deleteBlock(block: TranslationBlock): Boolean {
        return blocks.remove(block)
    }

    companion object {
        val EMPTY = PageTranslation()
    }
}

@Serializable
data class TranslationBlock(
    var text: String,
    var translation: String = "",
    var width: Float,
    var height: Float,
    var x: Float,
    var y: Float,
    var symHeight: Float,
    var symWidth: Float,
    val angle: Float,
) {
    /**
     * Calculate aspect ratio of this bubble
     * width/height > 1 = horizontal bubble
     * width/height < 1 = vertical bubble
     */
    fun getAspectRatio(): Float = width / height.coerceAtLeast(1f)

    /**
     * Detect bubble shape based on aspect ratio
     * Horizontal ovals (Korean common case): width >> height
     * Vertical ovals: height >> width
     * Rectangles/Squares: width ≈ height
     */
    fun detectShape(): BubbleShape {
        val aspectRatio = getAspectRatio()
        return when {
            aspectRatio > 1.8f -> BubbleShape.HORIZONTAL_OVAL
            aspectRatio > 1.2f -> BubbleShape.HORIZONTAL_RECT
            aspectRatio > 0.8f -> BubbleShape.SQUARE
            aspectRatio > 0.6f -> BubbleShape.VERTICAL_RECT
            else -> BubbleShape.VERTICAL_OVAL
        }
    }

    val bubbleShape: BubbleShape
        get() = detectShape()

    /**
     * Detect if bubble is near image edges
     * Used to prevent expansion into black panel strips
     */
    fun getEdgeProximity(imgHeight: Float, threshold: Float = 0.10f): EdgeProximity {
        val topEdgeThreshold = imgHeight * threshold
        val bottomEdgeThreshold = imgHeight * (1 - threshold)

        return when {
            y < topEdgeThreshold -> EdgeProximity.NEAR_TOP
            (y + height) > bottomEdgeThreshold -> EdgeProximity.NEAR_BOTTOM
            else -> EdgeProximity.CENTER
        }
    }
}

/**
 * Bubble shape classification based on aspect ratio
 * Used for adaptive text constraints and background rendering
 */
enum class BubbleShape {
    HORIZONTAL_OVAL, // width/height > 1.8 (Korean common case)
    HORIZONTAL_RECT, // 1.2 < width/height < 1.8
    SQUARE, // 0.8 < width/height < 1.2
    VERTICAL_RECT, // 0.6 < width/height < 0.8
    VERTICAL_OVAL, // width/height < 0.6
}

/**
 * Edge proximity classification
 * Used to limit expansion near black panel strips
 */
enum class EdgeProximity {
    NEAR_TOP, // Bubble in top 10% of image
    NEAR_BOTTOM, // Bubble in bottom 10% of image
    CENTER, // Bubble in middle 80% of image
}

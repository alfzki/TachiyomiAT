package eu.kanade.translation.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.translation.model.BubbleShape
import eu.kanade.translation.model.TranslationBlock
import logcat.LogPriority
import logcat.logcat
import mihon.app.di.appGraph
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.presentation.core.util.collectAsState
import kotlin.math.max

// ============================================================================
// CJK Constants (preserved from original - DO NOT CHANGE)
// ============================================================================
private const val MIN_FONT_SIZE = 12f
private const val CJK_MAX_FONT_SIZE = 12f

// CJK Bubble expansion
private const val BUBBLE_EXPANSION_RATIO = 1.2f
private const val MAX_BUBBLE_EXPANSION = 1.5f

// Korean -> English specific constants
private const val KOREAN_TO_ENGLISH_MIN_EXPANSION = 1.8f
private const val KOREAN_TO_ENGLISH_MAX_EXPANSION = 2.5f
private const val KOREAN_LINE_HEIGHT_MULTIPLIER = 1.4f

// Cloud Translation specific constants (longer translations)
private const val CLOUD_KOREAN_TO_ENGLISH_MIN_EXPANSION = 2.2f
private const val CLOUD_KOREAN_TO_ENGLISH_MAX_EXPANSION = 3.0f
private const val CLOUD_TRANSLATION_EXPANSION_MULTIPLIER = 1.15f

// CJK Padding constants
private const val CJK_HORIZONTAL_PADDING_DP = 8
private const val CJK_VERTICAL_PADDING_DP = 2
private const val CJK_OVAL_VERTICAL_PADDING_DP = 10
private const val CJK_OVAL_HORIZONTAL_PADDING_DP = 4

// CJK Safety margins
private const val CJK_WIDTH_SAFETY_MARGIN = 0.98f
private const val CJK_HEIGHT_SAFETY_MARGIN = 0.99f
private const val CJK_OVAL_WIDTH_SAFETY_MARGIN = 0.98f
private const val CJK_OVAL_HEIGHT_SAFETY_MARGIN = 0.70f
private const val CJK_VERTICAL_OVAL_WIDTH_SAFETY_MARGIN = 0.70f
private const val CJK_VERTICAL_OVAL_HEIGHT_SAFETY_MARGIN = 0.95f

// ============================================================================
// Latin Constants (new values for Spanish/Indonesian)
// ============================================================================
private const val LATIN_LINE_HEIGHT_MULTIPLIER = 1.2f // Tighter line height

// Latin Padding constants (more generous)
private const val LATIN_HORIZONTAL_PADDING_DP = 6
private const val LATIN_VERTICAL_PADDING_DP = 4
private const val LATIN_OVAL_VERTICAL_PADDING_DP = 6
private const val LATIN_OVAL_HORIZONTAL_PADDING_DP = 4

// Latin Safety margins (more generous to prevent clipping)
private const val LATIN_WIDTH_SAFETY_MARGIN = 0.98f
private const val LATIN_HEIGHT_SAFETY_MARGIN = 0.99f
private const val LATIN_OVAL_WIDTH_SAFETY_MARGIN = 0.98f
private const val LATIN_OVAL_HEIGHT_SAFETY_MARGIN = 0.95f // Much more generous than CJK 70%
private const val LATIN_VERTICAL_OVAL_WIDTH_SAFETY_MARGIN = 0.92f
private const val LATIN_VERTICAL_OVAL_HEIGHT_SAFETY_MARGIN = 0.90f

// Latin Expansion (hybrid overflow)
private const val LATIN_MAX_EXPANSION = 1.35f
private const val LATIN_MIN_FONT_SIZE = 8 // Can reduce to 8sp to prevent mid-word breaking
private const val LATIN_MAX_FONT_SIZE = 13

// ============================================================================
// Common Constants
// ============================================================================
private const val HARD_WIDTH_CONSTRAINT = 0.90f // 90% of bubble width

/**
 * Helper data class for shape-specific padding and margin configurations
 */
private data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

@Composable
fun SmartTranslationBlock(
    modifier: Modifier = Modifier,
    block: TranslationBlock,
    scaleFactor: Float,
    fontFamily: FontFamily,
    sourceLanguage: String = "auto",
    targetLanguage: String = "en",
    translatorType: String = "gemini",
    onBlockClick: (() -> Unit)? = null,
) {
    // Determine language module based on target translation language and script
    val languageModule = remember(sourceLanguage, targetLanguage, block.translation) {
        LanguageRenderModule.forTranslation(
            targetLanguage = targetLanguage,
            sourceLanguage = sourceLanguage,
            textSample = block.translation,
        )
    }

    // Get preferences from Metro DI via LocalContext
    val context = LocalContext.current
    val translationPreferences = remember(context) { context.appGraph.translationPreferences }

    // Detect if CJK or Latin module
    val isCJKModule = languageModule is LanguageRenderModule.CJKModule

    // Get module-specific settings REACTIVELY from preferences using collectAsState
    // CJK preferences
    val cjkTextMargin by translationPreferences.cjkTextMargin().collectAsState()
    val cjkPaddingMult by translationPreferences.cjkPaddingMultiplier().collectAsState()
    val cjkOvalHeight by translationPreferences.cjkOvalHeightMargin().collectAsState()

    // Latin preferences
    val latinTextMargin by translationPreferences.latinTextMargin().collectAsState()
    val latinPaddingMult by translationPreferences.latinPaddingMultiplier().collectAsState()
    val latinOvalHeight by translationPreferences.latinOvalHeightMargin().collectAsState()
    val latinHPadding by translationPreferences.latinHorizontalPadding().collectAsState()
    val latinVPadding by translationPreferences.latinVerticalPadding().collectAsState()

    // Select values based on current module
    val userMarginPx = if (isCJKModule) cjkTextMargin else latinTextMargin
    val paddingMultiplier = (if (isCJKModule) cjkPaddingMult else latinPaddingMult).let {
        if (it > 10f) it / 100f else it
    }
    val ovalHeightMargin = (if (isCJKModule) cjkOvalHeight else latinOvalHeight).let {
        if (it > 1f) it / 100f else it
    }
    val latinHorizontalPadding = if (isCJKModule) LATIN_HORIZONTAL_PADDING_DP else latinHPadding
    val latinVerticalPadding = if (isCJKModule) LATIN_VERTICAL_PADDING_DP else latinVPadding

    // Apply padding multiplier to calculate symbol padding
    val padX = (block.symWidth * 2) * paddingMultiplier
    val padY = block.symHeight * paddingMultiplier
    val xPx = max((block.x - padX / 2) * scaleFactor, 0.0f)
    val yPx = max((block.y - padY / 2) * scaleFactor, 0.0f)

    // Calculate raw bubble dimensions (100% size for background)
    val rawWidth = ((block.width + padX) * scaleFactor).pxToDp()
    val rawHeight = ((block.height + padY) * scaleFactor).pxToDp()
    val isVertical = block.angle > 85

    // Detect language-specific translation pairs
    val isKoreanToEnglish = LanguageRenderModule.isKoreanToEnglish(sourceLanguage, targetLanguage)

    // Calculate optimal dimensions with expansion
    // Note: This recalculates when preference values change due to collectAsState
    val calculatedDimensions = remember(
        block.translation,
        isKoreanToEnglish,
        translatorType,
        sourceLanguage,
        targetLanguage,
        languageModule,
        paddingMultiplier,
        ovalHeightMargin,
    ) {
        if (isCJKModule) {
            // CJK path: Use original expansion logic
            val expandedDimensions = calculateCJKOptimalDimensions(
                text = block.translation,
                originalText = block.text,
                baseWidth = rawWidth,
                baseHeight = rawHeight,
                isKoreanToEnglish = isKoreanToEnglish,
                translatorType = translatorType,
            )

            // Apply hard 90% width constraint
            BubbleDimensions(
                width = minOf(expandedDimensions.width, rawWidth * HARD_WIDTH_CONSTRAINT),
                height = expandedDimensions.height,
                expansionRatio = expandedDimensions.expansionRatio,
                fontSize = CJK_MAX_FONT_SIZE.toInt(),
            )
        } else {
            // Latin path: Use hybrid overflow logic
            calculateLatinOptimalDimensions(
                text = block.translation,
                baseWidth = rawWidth,
                baseHeight = rawHeight,
            )
        }
    }

    Box(
        modifier = modifier
            .then(
                if (onBlockClick != null) {
                    Modifier.clickable(
                        onClick = { onBlockClick() },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    )
                } else {
                    Modifier
                },
            )
            .wrapContentSize(Alignment.CenterStart, true)
            .offset(xPx.pxToDp(), yPx.pxToDp())
            .requiredSize(calculatedDimensions.width, calculatedDimensions.height),
    ) {
        val density = LocalDensity.current

        SubcomposeLayout { constraints ->
            val bubbleShape = block.detectShape()

            // Get shape-specific padding and margins based on language module
            val (horizontalPaddingDp, verticalPaddingDp, widthMargin, heightMargin) = if (isCJKModule) {
                getCJKShapeConfig(bubbleShape)
            } else {
                getLatinShapeConfig(bubbleShape, ovalHeightMargin, latinHorizontalPadding, latinVerticalPadding)
            }

            // Apply edge padding
            val horizontalPaddingPx = with(density) { (horizontalPaddingDp.dp).roundToPx() }
            val verticalPaddingPx = with(density) { (verticalPaddingDp.dp).roundToPx() }

            // Calculate available text area
            val totalHorizontalPadding = horizontalPaddingPx * 2
            val totalVerticalPadding = verticalPaddingPx * 2

            val maxWidthPx = with(density) {
                ((calculatedDimensions.width.roundToPx() - totalHorizontalPadding) * widthMargin).toInt()
                    .coerceAtLeast(0)
            }
            val maxHeightPx = with(density) {
                ((calculatedDimensions.height.roundToPx() - totalVerticalPadding) * heightMargin).toInt()
                    .coerceAtLeast(0)
            }

            // Apply user-configurable margin
            val finalMaxWidthPx = (maxWidthPx - userMarginPx * 2).coerceAtLeast(0)
            val finalMaxHeightPx = (maxHeightPx - userMarginPx * 2).coerceAtLeast(0)

            val constrainedWidthDp = with(density) { finalMaxWidthPx.toDp() }

            // Safety check: If text area is too small, skip rendering
            val minTextAreaWidth = 30
            val minTextAreaHeight = 20
            if (finalMaxWidthPx < minTextAreaWidth || finalMaxHeightPx < minTextAreaHeight) {
                logcat(LogPriority.WARN) {
                    "SmartTranslationBlock: Bubble too small (${finalMaxWidthPx}x${finalMaxHeightPx}px). " +
                        "Skipping: '${block.translation.take(30)}...'"
                }
                return@SubcomposeLayout layout(constraints.maxWidth, constraints.maxHeight) {}
            }

            // Determine font size based on module
            val fontSize = if (isCJKModule) {
                // CJK: Binary search for optimal size, capped at 12dp
                findOptimalFontSizeCJK(
                    block = block,
                    fontFamily = fontFamily,
                    finalMaxWidthPx = finalMaxWidthPx,
                    finalMaxHeightPx = finalMaxHeightPx,
                    constrainedWidthDp = constrainedWidthDp,
                    isVertical = isVertical,
                    isKoreanToEnglish = isKoreanToEnglish,
                    subcompose = { size, content -> subcompose(size, content) },
                    density = density,
                )
            } else {
                // Latin: Use pre-calculated font size from hybrid overflow, or find optimal
                if (calculatedDimensions.fontSize > 0) {
                    calculatedDimensions.fontSize
                } else {
                    findOptimalFontSizeLatin(
                        block = block,
                        fontFamily = fontFamily,
                        finalMaxWidthPx = finalMaxWidthPx,
                        finalMaxHeightPx = finalMaxHeightPx,
                        constrainedWidthDp = constrainedWidthDp,
                        isVertical = isVertical,
                        subcompose = { size, content -> subcompose(size, content) },
                        density = density,
                    )
                }
            }

            val finalFontSize = fontSize.coerceAtLeast(
                if (isCJKModule) MIN_FONT_SIZE.toInt() else LATIN_MIN_FONT_SIZE,
            ).sp

            // Calculate max lines based on module with pixel-accurate line height
            val lineHeightMultiplier = if (isCJKModule) KOREAN_LINE_HEIGHT_MULTIPLIER else LATIN_LINE_HEIGHT_MULTIPLIER
            val maxLines = calculateMaxLines(finalMaxHeightPx, fontSize, lineHeightMultiplier, density)

            // Render text cleanly centered inside bubble without duplicate background
            val textPlaceable = subcompose(Unit) {
                Text(
                    text = block.translation,
                    fontSize = finalFontSize,
                    fontFamily = fontFamily,
                    color = Color.Black,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                    maxLines = maxLines,
                    modifier = Modifier
                        .widthIn(max = constrainedWidthDp)
                        .rotate(if (isVertical) 0f else block.angle),
                )
            }[0].measure(
                Constraints(minWidth = 0, maxWidth = finalMaxWidthPx, minHeight = 0, maxHeight = finalMaxHeightPx),
            )

            layout(constraints.maxWidth, constraints.maxHeight) {
                val placeX = ((constraints.maxWidth - textPlaceable.width) / 2).coerceAtLeast(0)
                val placeY = ((constraints.maxHeight - textPlaceable.height) / 2).coerceAtLeast(0)
                textPlaceable.place(placeX, placeY)
            }
        }
    }
}

// ============================================================================
// CJK-specific functions (preserved from original)
// ============================================================================

/**
 * Get shape-specific configuration for CJK languages.
 * These values are PRESERVED from the original implementation.
 */
private fun getCJKShapeConfig(bubbleShape: BubbleShape): Quadruple<Int, Int, Float, Float> {
    return when (bubbleShape) {
        BubbleShape.HORIZONTAL_OVAL -> Quadruple(
            CJK_HORIZONTAL_PADDING_DP,
            CJK_OVAL_VERTICAL_PADDING_DP,
            CJK_OVAL_WIDTH_SAFETY_MARGIN,
            CJK_OVAL_HEIGHT_SAFETY_MARGIN, // 70% - conservative
        )
        BubbleShape.VERTICAL_OVAL -> Quadruple(
            CJK_OVAL_HORIZONTAL_PADDING_DP,
            CJK_VERTICAL_PADDING_DP,
            CJK_VERTICAL_OVAL_WIDTH_SAFETY_MARGIN,
            CJK_VERTICAL_OVAL_HEIGHT_SAFETY_MARGIN,
        )
        else -> Quadruple(
            CJK_HORIZONTAL_PADDING_DP,
            CJK_VERTICAL_PADDING_DP,
            CJK_WIDTH_SAFETY_MARGIN,
            CJK_HEIGHT_SAFETY_MARGIN,
        )
    }
}

/**
 * Calculate optimal dimensions for CJK translations.
 * PRESERVED from original implementation.
 */
private fun calculateCJKOptimalDimensions(
    text: String,
    originalText: String,
    baseWidth: Dp,
    baseHeight: Dp,
    isKoreanToEnglish: Boolean,
    translatorType: String = "gemini",
): BubbleDimensions {
    // Korean -> English: Specialized expansion
    if (isKoreanToEnglish) {
        return calculateKoreanToEnglishDimensions(
            englishText = text,
            koreanText = originalText,
            baseWidth = baseWidth,
            baseHeight = baseHeight,
            translatorType = translatorType,
        )
    }

    // Short text: Use base dimensions
    if (text.length < 20) {
        return BubbleDimensions(baseWidth, baseHeight, 1.0f, CJK_MAX_FONT_SIZE.toInt())
    }

    // Longer text: Apply expansion
    val isCloudTranslation = translatorType.lowercase() == "cloud"
    val cloudMultiplier = if (isCloudTranslation) CLOUD_TRANSLATION_EXPANSION_MULTIPLIER else 1.0f

    val estimatedExpansionNeeded = when {
        text.length > 200 -> MAX_BUBBLE_EXPANSION * cloudMultiplier
        text.length > 100 -> BUBBLE_EXPANSION_RATIO * cloudMultiplier
        else -> 1.0f
    }

    val maxExpansion = if (isCloudTranslation) {
        (MAX_BUBBLE_EXPANSION * CLOUD_TRANSLATION_EXPANSION_MULTIPLIER).coerceAtMost(2.0f)
    } else {
        MAX_BUBBLE_EXPANSION
    }

    val expansionRatio = estimatedExpansionNeeded.coerceAtMost(maxExpansion)

    return BubbleDimensions(
        width = baseWidth * expansionRatio,
        height = baseHeight * expansionRatio,
        expansionRatio = expansionRatio,
        fontSize = CJK_MAX_FONT_SIZE.toInt(),
    )
}

/**
 * Calculate Korean -> English dimensions.
 * PRESERVED from original implementation.
 */
private fun calculateKoreanToEnglishDimensions(
    englishText: String,
    koreanText: String,
    baseWidth: Dp,
    baseHeight: Dp,
    translatorType: String = "gemini",
): BubbleDimensions {
    val isCloudTranslation = translatorType.lowercase() == "cloud"
    val minExpansion = if (isCloudTranslation) {
        CLOUD_KOREAN_TO_ENGLISH_MIN_EXPANSION
    } else {
        KOREAN_TO_ENGLISH_MIN_EXPANSION
    }
    val maxExpansion = if (isCloudTranslation) {
        CLOUD_KOREAN_TO_ENGLISH_MAX_EXPANSION
    } else {
        KOREAN_TO_ENGLISH_MAX_EXPANSION
    }

    val lengthRatio = if (koreanText.isNotEmpty()) {
        englishText.length.toFloat() / koreanText.length.toFloat()
    } else {
        2.0f
    }

    val expansionRatio = when {
        englishText.length > 200 -> maxExpansion
        lengthRatio > 3.0f -> maxExpansion
        lengthRatio > 2.0f -> maxExpansion * 0.9f
        englishText.length > 100 -> minExpansion * 1.2f
        englishText.length > 50 -> minExpansion
        englishText.length < 20 -> 1.3f
        else -> minExpansion
    }.coerceAtMost(maxExpansion)

    val minWidthForKorean = baseWidth * 1.4f
    val expandedWidth = (baseWidth * expansionRatio).coerceAtLeast(minWidthForKorean)

    return BubbleDimensions(
        width = expandedWidth,
        height = baseHeight * expansionRatio,
        expansionRatio = expansionRatio,
        fontSize = CJK_MAX_FONT_SIZE.toInt(),
    )
}

/**
 * Find optimal font size for CJK using binary search.
 */
private fun findOptimalFontSizeCJK(
    block: TranslationBlock,
    fontFamily: FontFamily,
    finalMaxWidthPx: Int,
    finalMaxHeightPx: Int,
    constrainedWidthDp: Dp,
    isVertical: Boolean,
    isKoreanToEnglish: Boolean,
    subcompose: (Any, @Composable () -> Unit) -> List<androidx.compose.ui.layout.Measurable>,
    density: Density,
): Int {
    var low = MIN_FONT_SIZE.toInt()
    var high = CJK_MAX_FONT_SIZE.toInt()
    var bestSize = low

    while (low <= high) {
        val mid = ((low + high) / 2).coerceAtLeast(MIN_FONT_SIZE.toInt())
        val textLayoutResult = subcompose(mid.sp) {
            Text(
                text = block.translation,
                fontSize = mid.sp,
                fontFamily = fontFamily,
                color = Color.Black,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
                maxLines = calculateMaxLines(finalMaxHeightPx, mid, KOREAN_LINE_HEIGHT_MULTIPLIER, density),
                softWrap = true,
                modifier = Modifier
                    .width(constrainedWidthDp)
                    .rotate(if (isVertical) 0f else block.angle),
            )
        }[0].measure(Constraints(maxWidth = finalMaxWidthPx))

        if (textLayoutResult.height <= finalMaxHeightPx && textLayoutResult.width <= finalMaxWidthPx) {
            bestSize = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    return bestSize.coerceAtLeast(MIN_FONT_SIZE.toInt())
}

// ============================================================================
// Latin-specific functions (new implementation)
// ============================================================================

/**
 * Get shape-specific configuration for Latin languages.
 * Uses more generous margins to prevent text clipping.
 */
private fun getLatinShapeConfig(
    bubbleShape: BubbleShape,
    ovalHeightMargin: Float,
    horizontalPadding: Int,
    verticalPadding: Int,
): Quadruple<Int, Int, Float, Float> {
    return when (bubbleShape) {
        BubbleShape.HORIZONTAL_OVAL -> Quadruple(
            horizontalPadding,
            LATIN_OVAL_VERTICAL_PADDING_DP,
            LATIN_OVAL_WIDTH_SAFETY_MARGIN,
            ovalHeightMargin, // User-configurable, default 95%
        )
        BubbleShape.VERTICAL_OVAL -> Quadruple(
            LATIN_OVAL_HORIZONTAL_PADDING_DP,
            verticalPadding,
            LATIN_VERTICAL_OVAL_WIDTH_SAFETY_MARGIN,
            LATIN_VERTICAL_OVAL_HEIGHT_SAFETY_MARGIN,
        )
        else -> Quadruple(
            horizontalPadding,
            verticalPadding,
            LATIN_WIDTH_SAFETY_MARGIN,
            LATIN_HEIGHT_SAFETY_MARGIN,
        )
    }
}

/**
 * Calculate optimal dimensions for Latin translations using HYBRID OVERFLOW STRATEGY.
 *
 * Strategy:
 * 1. For vertical bubbles, adjust aspect ratio slightly so horizontal Latin words fit.
 * 2. Expand bubble moderately depending on text length.
 * 3. Return fontSize = 0 to dynamically find the optimal font size (8sp - 13sp) preventing text clipping.
 */
private fun calculateLatinOptimalDimensions(
    text: String,
    baseWidth: Dp,
    baseHeight: Dp,
): BubbleDimensions {
    // If the bubble is strongly vertical (tall manga bubble), adjust width slightly so horizontal words fit
    val isVerticalShape = baseHeight > baseWidth * 1.3f
    val widthMultiplier = if (isVerticalShape) 1.25f else 1.0f
    val heightMultiplier = if (isVerticalShape) 0.95f else 1.0f

    val expansion = when {
        text.length > 150 -> LATIN_MAX_EXPANSION
        text.length > 70 -> 1.2f
        text.length > 30 -> 1.1f
        else -> 1.0f
    }

    return BubbleDimensions(
        width = baseWidth * widthMultiplier * expansion,
        height = baseHeight * heightMultiplier * expansion,
        expansionRatio = expansion,
        fontSize = 0, // Always use font size finder to guarantee optimal fit without clipping or mid-word breaking
    )
}

/**
 * Find optimal font size for Latin using binary search.
 * Adapts between 8sp and 13sp, ensuring words fit without breaking mid-word.
 */
private fun findOptimalFontSizeLatin(
    block: TranslationBlock,
    fontFamily: FontFamily,
    finalMaxWidthPx: Int,
    finalMaxHeightPx: Int,
    constrainedWidthDp: Dp,
    isVertical: Boolean,
    subcompose: (Any, @Composable () -> Unit) -> List<androidx.compose.ui.layout.Measurable>,
    density: Density,
): Int {
    var low = LATIN_MIN_FONT_SIZE
    var high = LATIN_MAX_FONT_SIZE
    var bestSize = low

    // Identify the longest word to make sure it doesn't get chopped mid-word
    val words = block.translation.split(Regex("\\s+")).filter { it.isNotBlank() }
    val longestWordLength = words.maxOfOrNull { it.length } ?: 0

    while (low <= high) {
        val mid = ((low + high) / 2).coerceAtLeast(LATIN_MIN_FONT_SIZE)

        // Estimated character width in pixels: if longest word alone exceeds maxWidth, reduce font
        val estWordWidthPx = longestWordLength * (mid * 1.4f)
        if (longestWordLength > 4 && estWordWidthPx > finalMaxWidthPx * 1.1f) {
            high = mid - 1
            continue
        }

        val textLayoutResult = subcompose("latin_$mid") {
            Text(
                text = block.translation,
                fontSize = mid.sp,
                fontFamily = fontFamily,
                color = Color.Black,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
                maxLines = calculateMaxLines(finalMaxHeightPx, mid, LATIN_LINE_HEIGHT_MULTIPLIER, density),
                softWrap = true,
                modifier = Modifier
                    .width(constrainedWidthDp)
                    .rotate(if (isVertical) 0f else block.angle),
            )
        }[0].measure(Constraints(maxWidth = finalMaxWidthPx))

        if (textLayoutResult.height <= finalMaxHeightPx && textLayoutResult.width <= finalMaxWidthPx) {
            bestSize = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    return bestSize.coerceAtLeast(LATIN_MIN_FONT_SIZE)
}

// ============================================================================
// Common functions
// ============================================================================

/**
 * Calculate maximum lines based on bubble height and font size.
 */
private fun calculateMaxLines(heightPx: Int, fontSize: Int, lineHeightMultiplier: Float, density: Density): Int {
    val approximateLineHeightPx = with(density) { (fontSize.sp * lineHeightMultiplier).toPx() }
    val maxLines = (heightPx / approximateLineHeightPx).toInt().coerceAtLeast(1)
    return maxLines.coerceAtMost(25)
}

/**
 * Data class to hold calculated bubble dimensions.
 */
private data class BubbleDimensions(
    val width: Dp,
    val height: Dp,
    val expansionRatio: Float,
    val fontSize: Int = 12,
)

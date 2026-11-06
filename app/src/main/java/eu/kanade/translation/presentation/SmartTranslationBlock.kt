package eu.kanade.translation.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import eu.kanade.translation.model.TranslationBlock
import kotlin.math.max

@Composable
fun SmartTranslationBlock(
    modifier: Modifier = Modifier,
    block: TranslationBlock,
    scaleFactor: Float,
    fontFamily: FontFamily,

) {
    val padX = block.symWidth * 2
    val padY = block.symHeight
    val xPx = max((block.x - padX / 2) * scaleFactor, 0.0f)
    val yPx = max((block.y - padY / 2) * scaleFactor, 0.0f)
    val width = ((block.width + padX) * scaleFactor).pxToDp()
    val height = ((block.height + padY) * scaleFactor).pxToDp()
    val isVertical = block.angle > 85
    Box(
        modifier = modifier
            .wrapContentSize(Alignment.CenterStart, true)
            .offset(xPx.pxToDp(), yPx.pxToDp())
            .requiredSize(width, height),
    ) {
        val density = LocalDensity.current
        val words = block.translation.split(Regex("\\s+")).filter { it.isNotBlank() }
        val longestWord = words.maxByOrNull { it.length } ?: ""

        SubcomposeLayout { constraints ->
            val maxWidthPx = maxOf(1, with(density) { width.roundToPx() })
            val maxHeightPx = maxOf(1, with(density) { height.roundToPx() })

            // Binary search for optimal font size (capped at 28sp for natural comic typography)
            var low = 1
            var high = 28
            var bestSize = low

            while (low <= high) {
                val mid = (low + high) / 2
                val textLayoutResult = subcompose(mid.sp) {
                    Text(
                        text = block.translation,
                        fontSize = mid.sp,
                        lineHeight = (mid * 1.15f).sp,
                        fontFamily = fontFamily,
                        color = Color.Black,
                        overflow = TextOverflow.Visible,
                        textAlign = TextAlign.Center,
                        maxLines = Int.MAX_VALUE,
                        softWrap = true,
                        modifier = Modifier
                            .width(width)
                            .rotate(if (isVertical) 0f else block.angle)
                            .align(Alignment.Center),
                    )
                }[0].measure(Constraints(maxWidth = maxWidthPx))

                val wordFits = if (longestWord.isNotEmpty() && longestWord.length <= 12) {
                    val wordLayoutResult = subcompose("word_${longestWord}_$mid") {
                        Text(
                            text = longestWord,
                            fontSize = mid.sp,
                            lineHeight = (mid * 1.15f).sp,
                            fontFamily = fontFamily,
                            softWrap = false,
                            maxLines = 1,
                        )
                    }[0].measure(Constraints())
                    wordLayoutResult.width <= maxWidthPx
                } else {
                    true
                }

                if (textLayoutResult.height <= maxHeightPx && wordFits) {
                    bestSize = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }

            // Measure final layout
            val textPlaceable = subcompose(Unit) {
                Text(
                    text = block.translation,
                    fontSize = bestSize.sp,
                    lineHeight = (bestSize * 1.15f).sp,
                    fontFamily = fontFamily,
                    color = Color.Black,
                    softWrap = true,
                    overflow = TextOverflow.Visible,
                    textAlign = TextAlign.Center,
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier
                        .width(width)
                        .rotate(if (isVertical) 0f else block.angle)
                        .align(Alignment.Center),
                )
            }[0].measure(constraints)

            layout(textPlaceable.width, textPlaceable.height) {
                textPlaceable.place(0, 0)
            }
        }
    }
}

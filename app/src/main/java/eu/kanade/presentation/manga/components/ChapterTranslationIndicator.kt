package eu.kanade.presentation.manga.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.R
import eu.kanade.translation.model.Translation
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Error
import tachiyomi.i18n.MR
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.components.material.IconButtonTokens
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

enum class ChapterTranslationAction {
    START,
    CANCEL,
    DELETE,
}

@Composable
fun ChapterTranslationIndicator(
    enabled: Boolean,
    translationStateProvider: () -> Translation.State,
    onClick: (ChapterTranslationAction) -> Unit,
    modifier: Modifier = Modifier,
    translationProgressProvider: () -> Pair<Int, Int> = { 0 to 0 },
    translationErrorProvider: () -> String? = { null },
) {
    when (translationStateProvider()) {
        Translation.State.NOT_TRANSLATED -> NotTranslatedIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
        )
        Translation.State.QUEUE, Translation.State.TRANSLATING -> TranslatingIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
            progressProvider = translationProgressProvider,
        )
        Translation.State.TRANSLATED -> TranslatedIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
        )
        Translation.State.ERROR -> ErrorIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
            errorProvider = translationErrorProvider,
        )
    }
}

@Composable
private fun NotTranslatedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ChapterTranslationAction) -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(ChapterTranslationAction.START) },
                onClick = { onClick(ChapterTranslationAction.START) },
            )
            .secondaryItemAlpha(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_translate_circle),
            contentDescription = stringResource(ATMR.strings.manga_translate),
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TranslatingIndicator(
    enabled: Boolean,
    onClick: (ChapterTranslationAction) -> Unit,
    modifier: Modifier = Modifier,
    progressProvider: () -> Pair<Int, Int> = { 0 to 0 },
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(ChapterTranslationAction.CANCEL) },
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val strokeColor = MaterialTheme.colorScheme.onSurfaceVariant
        val (progress, totalPages) = progressProvider()

        if (totalPages > 0) {
            CircularProgressIndicator(
                progress = { (progress.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f) },
                modifier = IndicatorModifier,
                color = strokeColor,
                strokeWidth = IndicatorStrokeWidth,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
        } else {
            CircularProgressIndicator(
                modifier = IndicatorModifier,
                color = strokeColor,
                strokeWidth = IndicatorStrokeWidth,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
            )
        }

        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            if (totalPages > 0) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(ATMR.strings.translation_pages_progress, progress, totalPages),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {},
                    enabled = false,
                )
            }
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_cancel)) },
                onClick = {
                    onClick(ChapterTranslationAction.CANCEL)
                    isMenuExpanded = false
                },
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_translate),
            contentDescription = null,
            modifier = TranslatingModifier,
            tint = strokeColor,
        )
    }
}

@Composable
private fun TranslatedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ChapterTranslationAction) -> Unit,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { isMenuExpanded = true },
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_translate_circle_filled),
            contentDescription = null,
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_delete)) },
                onClick = {
                    onClick(ChapterTranslationAction.DELETE)
                    isMenuExpanded = false
                },
            )
        }
    }
}

@Composable
private fun ErrorIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ChapterTranslationAction) -> Unit,
    errorProvider: () -> String? = { null },
) {
    var showErrorDialog by remember { mutableStateOf(false) }

    if (showErrorDialog) {
        val errorMessage = errorProvider() ?: stringResource(MR.strings.unknown_error)
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = {
                Text(text = stringResource(ATMR.strings.translation_error_title))
            },
            text = {
                Text(text = errorMessage)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showErrorDialog = false
                        onClick(ChapterTranslationAction.START)
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { showErrorDialog = true },
                onClick = { showErrorDialog = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Error,
            contentDescription = stringResource(MR.strings.chapter_error),
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

private fun Modifier.commonClickable(
    enabled: Boolean,
    hapticFeedback: HapticFeedback,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) = this.combinedClickable(
    enabled = enabled,
    onLongClick = {
        onLongClick()
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    },
    onClick = onClick,
    role = Role.Button,
    interactionSource = null,
    indication = ripple(
        bounded = false,
        radius = IconButtonTokens.StateLayerSize / 2,
    ),
)

private val IndicatorSize = 23.dp
private val IndicatorPadding = 2.dp

// To match composable parameter name when used later
private val IndicatorStrokeWidth = IndicatorPadding

private val IndicatorModifier = Modifier
    .size(IndicatorSize)
    .padding(IndicatorPadding)
private val TranslatingModifier = Modifier
    .size(IndicatorSize - 7.dp)

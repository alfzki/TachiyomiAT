package eu.kanade.presentation.reader.appbars

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.R
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Bookmark
import mihon.icons.materialsymbols.roundedfilled.Bookmark
import tachiyomi.i18n.MR
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderTopBar(
    mangaTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    showTranslations: Boolean,
    onToggleShowTranslations: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AppBar(
        modifier = modifier,
        backgroundColor = Color.Transparent,
        title = mangaTitle,
        subtitle = chapterTitle,
        navigateUp = navigateUp,
        actions = {
            AppBarActions(
                actions = buildList {
                    add(
                        AppBar.Action(
                            title = stringResource(
                                if (showTranslations) {
                                    ATMR.strings.action_hide_translations
                                } else {
                                    ATMR.strings.action_show_translations
                                },
                            ),
                            icon = ImageVector.vectorResource(
                                if (showTranslations) {
                                    R.drawable.ic_translate_reader
                                } else {
                                    R.drawable.ic_translate_reader_off
                                },
                            ),
                            iconTint = if (showTranslations) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            },
                            onClick = onToggleShowTranslations,
                        ),
                    )
                    add(
                        AppBar.Action(
                            title = stringResource(
                                if (bookmarked) {
                                    MR.strings.action_remove_bookmark
                                } else {
                                    MR.strings.action_bookmark
                                },
                            ),
                            icon = if (bookmarked) {
                                MaterialSymbols.RoundedFilled.Bookmark
                            } else {
                                MaterialSymbols.Rounded.Bookmark
                            },
                            onClick = onToggleBookmarked,
                        ),
                    )
                    onOpenInWebView?.let {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_open_in_web_view),
                                onClick = it,
                            ),
                        )
                    }
                    onOpenInBrowser?.let {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_open_in_browser),
                                onClick = it,
                            ),
                        )
                    }
                    onShare?.let {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_share),
                                onClick = it,
                            ),
                        )
                    }
                },
            )
        },
    )
}

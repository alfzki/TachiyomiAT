package eu.kanade.tachiyomi.ui.translation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.translation.model.Translation
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.roundedfilled.Pause
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import tachiyomi.i18n.MR
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

object TranslationQueueScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<TranslationQueueViewModel>()
        val queueList by viewModel.queueState.collectAsStateWithLifecycle()
        val queueCount by remember {
            derivedStateOf { queueList.size }
        }

        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

        Scaffold(
            topBar = {
                AppBar(
                    titleContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(ATMR.strings.label_translation_queue),
                                maxLines = 1,
                                modifier = Modifier.weight(1f, false),
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (queueCount > 0) {
                                val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f
                                Pill(
                                    text = "$queueCount",
                                    modifier = Modifier.padding(start = 4.dp),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = pillAlpha),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    },
                    navigateUp = navigator::pop,
                    actions = {
                        if (queueList.isNotEmpty()) {
                            AppBarActions(
                                listOf(
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_cancel_all),
                                        onClick = { viewModel.clearQueue() },
                                    ),
                                ),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                if (queueList.isNotEmpty()) {
                    val isRunning = viewModel.isRunning
                    SmallExtendedFloatingActionButton(
                        text = {
                            val id = if (isRunning) MR.strings.action_pause else MR.strings.action_resume
                            Text(text = stringResource(id))
                        },
                        icon = {
                            val icon = if (isRunning) {
                                MaterialSymbols.RoundedFilled.Pause
                            } else {
                                MaterialSymbols.RoundedFilled.PlayArrow
                            }
                            Icon(imageVector = icon, contentDescription = null)
                        },
                        onClick = {
                            if (isRunning) {
                                viewModel.pauseTranslation()
                            } else {
                                viewModel.startTranslation()
                            }
                        },
                    )
                }
            },
        ) { contentPadding ->
            if (queueList.isEmpty()) {
                EmptyScreen(
                    stringRes = ATMR.strings.translation_queue_empty,
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                val sections = remember(queueList) {
                    queueList.groupBy { it.manga.id }.map { (_, list) ->
                        TranslationQueueSection(
                            mangaId = list.first().manga.id,
                            mangaTitle = list.first().manga.title,
                            translations = list,
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding() + 80.dp,
                    ),
                ) {
                    sections.forEach { section ->
                        item(key = "section_${section.mangaId}") {
                            ListGroupHeader(
                                text = section.mangaTitle,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        items(
                            items = section.translations,
                            key = { "trans_${it.chapter.id}" },
                        ) { translation ->
                            TranslationQueueItem(
                                translation = translation,
                                onCancel = { viewModel.cancel(translation) },
                                onRetry = { viewModel.retry(translation) },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TranslationQueueItem(
        translation: Translation,
        onCancel: () -> Unit,
        onRetry: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val status by translation.statusFlow.collectAsState()
        val progress by translation.progressFlow.collectAsState()
        val totalPages by translation.totalPagesFlow.collectAsState()
        val error by translation.errorFlow.collectAsState()

        ElevatedCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = translation.chapter.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Close,
                            contentDescription = stringResource(MR.strings.action_cancel),
                        )
                    }
                }

                when (status) {
                    Translation.State.TRANSLATING -> {
                        if (totalPages > 0) {
                            Text(
                                text = stringResource(ATMR.strings.translation_pages_progress, progress, totalPages),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            LinearProgressIndicator(
                                progress = { (progress.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                strokeCap = StrokeCap.Round,
                            )
                        } else {
                            Text(
                                text = stringResource(MR.strings.loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                    Translation.State.QUEUE -> {
                        Text(
                            text = stringResource(MR.strings.paused),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Translation.State.ERROR -> {
                        Text(
                            text = error ?: stringResource(MR.strings.unknown_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = onRetry,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                Text(text = stringResource(MR.strings.action_retry))
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

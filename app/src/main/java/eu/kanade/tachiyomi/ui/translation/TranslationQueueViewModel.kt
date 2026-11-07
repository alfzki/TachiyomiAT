package eu.kanade.tachiyomi.ui.translation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.translation.TranslationManager
import eu.kanade.translation.model.Translation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.seconds

@Immutable
data class TranslationQueueSection(
    val mangaId: Long,
    val mangaTitle: String,
    val translations: List<Translation>,
)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class TranslationQueueViewModel(
    private val translationManager: TranslationManager,
) : ViewModel() {

    val queueState: StateFlow<List<Translation>> = translationManager.queueState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), emptyList())

    val isRunning: Boolean
        get() = translationManager.isRunning

    fun startTranslation() {
        translationManager.startTranslation()
    }

    fun pauseTranslation() {
        translationManager.pauseTranslation()
    }

    fun clearQueue() {
        translationManager.clearQueue()
    }

    fun cancel(translation: Translation) {
        translationManager.cancelQueuedTranslation(translation)
    }

    fun retry(translation: Translation) {
        translationManager.retryTranslation(translation)
    }

    fun reorder(translations: List<Translation>) {
        translationManager.reorderQueue(translations)
    }
}

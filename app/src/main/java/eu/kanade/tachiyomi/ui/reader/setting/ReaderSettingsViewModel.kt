package eu.kanade.tachiyomi.ui.reader.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mihon.app.di.appGraph
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.domain.translation.model.ProfileType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReaderSettingsViewModel(
    readerState: StateFlow<ReaderViewModel.State>,
    val onChangeReadingMode: (ReadingMode) -> Unit,
    val onChangeOrientation: (ReaderOrientation) -> Unit,
    val preferences: ReaderPreferences,
    val translationPreferences: TranslationPreferences = Injekt.get<Context>().appGraph.translationPreferences,
) : ViewModel() {

    val viewerFlow = readerState
        .map { it.viewer }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val mangaFlow = readerState
        .map { it.manga }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * Flow that emits true if the current chapter has translations.
     * Used to conditionally show the Translation settings tab.
     */
    val hasTranslationsFlow: StateFlow<Boolean> = readerState
        .map { state ->
            state.viewerChapters?.currChapter?.pages?.any { page ->
                page.translation?.blocks?.isNotEmpty() == true
            } ?: false
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    /**
     * Flow that emits the current profile type based on the source language
     * of translations in the current chapter.
     */
    val currentProfileTypeFlow: StateFlow<ProfileType> = readerState
        .map { state ->
            val sourceLang = state.viewerChapters?.currChapter?.pages
                ?.firstNotNullOfOrNull { page ->
                    page.translation?.sourceLanguage
                }
            ProfileType.fromSourceLanguage(sourceLang ?: "auto")
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, ProfileType.LATIN)
}

package eu.kanade.translation.model

import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.TextTranslatorLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
data class Translation(
    val source: HttpSource,
    val manga: Manga,
    val chapter: Chapter,
    val fromLang: TextRecognizerLanguage = TextRecognizerLanguage.CHINESE,
    val toLang: TextTranslatorLanguage = TextTranslatorLanguage.ENGLISH,
) {
    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_TRANSLATED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(status) {
            _statusFlow.value = status
        }

    @Transient
    private val _progressFlow = MutableStateFlow(0)

    @Transient
    val progressFlow = _progressFlow.asStateFlow()
    var progress: Int
        get() = _progressFlow.value
        set(value) {
            _progressFlow.value = value
        }

    @Transient
    private val _totalPagesFlow = MutableStateFlow(0)

    @Transient
    val totalPagesFlow = _totalPagesFlow.asStateFlow()
    var totalPages: Int
        get() = _totalPagesFlow.value
        set(value) {
            _totalPagesFlow.value = value
        }

    @Transient
    private val _errorFlow = MutableStateFlow<String?>(null)

    @Transient
    val errorFlow = _errorFlow.asStateFlow()
    var error: String?
        get() = _errorFlow.value
        set(value) {
            _errorFlow.value = value
        }

    enum class State(val value: Int) {
        NOT_TRANSLATED(0),
        QUEUE(1),
        TRANSLATING(2),
        TRANSLATED(3),
        ERROR(4),
    }
}

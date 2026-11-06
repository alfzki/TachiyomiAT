package eu.kanade.translation

import android.content.Context
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.translation.data.TranslationProvider
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.Translation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.TranslationPreferences

@Inject
@SingleIn(AppScope::class)
class TranslationManager(
    private val context: Context,
    private val provider: TranslationProvider,
    private val downloadProvider: DownloadProvider,
    private val sourceManager: SourceManager,
    private val translationPreferences: TranslationPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _changes = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    val changes: Flow<Unit> = _changes.receiveAsFlow()
        .onStart { emit(Unit) }
        .shareIn(scope, kotlinx.coroutines.flow.SharingStarted.Lazily, 1)

    fun invalidate() {
        _changes.trySend(Unit)
    }

    private val translator = ChapterTranslator(
        context = context,
        provider = provider,
        downloadProvider = downloadProvider,
        sourceManager = sourceManager,
        translationPreferences = translationPreferences,
    )

    val isRunning: Boolean
        get() = translator.isRunning

    val queueState
        get() = translator.queueState

    fun translatorStart() = translator.start()
    fun translatorStop(reason: String? = null) = translator.stop(reason)

    fun startTranslation() {
        if (translator.isRunning) return
        translator.start()
    }

    fun pauseTranslation() {
        translator.pause()
        translator.stop()
    }

    fun clearQueue() {
        translator.clearQueue()
        translator.stop()
    }

    fun getQueuedTranslationOrNull(chapterId: Long): Translation? {
        return queueState.value.find { it.chapter.id == chapterId }
    }

    suspend fun translateChapter(manga: Manga, chapters: Chapter) {
        translator.queueChapter(manga, chapters)
        startTranslation()
    }

    fun getChapterTranslationStatus(
        chapterId: Long,
        chapterName: String,
        scanlator: String?,
        title: String,
        source: Source,
    ): Translation.State {
        val translation = getQueuedTranslationOrNull(chapterId)
        if (translation != null) return translation.status
        if (isChapterTranslated(chapterName, scanlator, title, source)) return Translation.State.TRANSLATED
        return Translation.State.NOT_TRANSLATED
    }

    fun getChapterTranslationStatus(
        chapterId: Long,
        chapterName: String,
        scanlator: String?,
        title: String,
        sourceId: Long,
    ): Translation.State {
        val translation = getQueuedTranslationOrNull(chapterId)
        if (translation != null) return translation.status
        if (isChapterTranslated(chapterName, scanlator, title, sourceId)) return Translation.State.TRANSLATED
        return Translation.State.NOT_TRANSLATED
    }

    fun isChapterTranslated(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        source: Source,
    ): Boolean {
        val file = provider.findTranslationFile(chapterName, chapterScanlator, mangaTitle, source)
        return file?.exists() == true
    }

    fun isChapterTranslated(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        sourceId: Long,
    ): Boolean {
        val source = runBlocking { sourceManager.get(sourceId) } ?: return false
        return isChapterTranslated(chapterName, chapterScanlator, mangaTitle, source)
    }

    fun getChapterTranslation(
        chapterName: String,
        scanlator: String?,
        title: String,
        source: Source,
    ): Map<String, PageTranslation> {
        try {
            val file = provider.findTranslationFile(
                chapterName,
                scanlator,
                title,
                source,
            ) ?: return emptyMap()
            return getChapterTranslation(file)
        } catch (_: Exception) {
        }
        return emptyMap()
    }

    fun getChapterTranslation(
        file: UniFile,
    ): Map<String, PageTranslation> {
        try {
            return Json.decodeFromStream<Map<String, PageTranslation>>(file.openInputStream())
        } catch (e: Exception) {
            file.delete()
        }
        return emptyMap()
    }

    fun deleteTranslation(chapter: Chapter, manga: Manga, source: Source) {
        scope.launch {
            removeFromTranslationQueue(chapter)
            val file = provider.findTranslationFile(chapter.name, chapter.scanlator, manga.title, source)
            file?.delete()
            invalidate()
        }
    }

    fun deleteManga(manga: Manga, source: Source, removeQueued: Boolean = true) {
        scope.launch {
            if (removeQueued) {
                translator.removeFromQueue(manga)
            }
            provider.findMangaDir(manga.title, source)?.delete()
            val sourceDir = provider.findSourceDir(source)
            if (sourceDir?.listFiles()?.isEmpty() == true) {
                sourceDir.delete()
            }
            invalidate()
        }
    }

    fun cancelQueuedTranslation(translation: Translation) {
        removeFromTranslationQueue(translation.chapter)
    }

    private fun removeFromTranslationQueue(chapter: Chapter) {
        val wasRunning = translator.isRunning
        if (wasRunning) {
            translator.pause()
        }
        translator.removeFromQueue(chapter)
        if (wasRunning) {
            if (queueState.value.isEmpty()) {
                translator.stop()
            } else if (queueState.value.isNotEmpty()) {
                translator.start()
            }
        }
    }

    fun statusFlow(): Flow<Translation> = queueState
        .flatMapLatest { translations ->
            translations
                .map { translation ->
                    translation.statusFlow.drop(1).map { translation }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { translation -> translation.status == Translation.State.TRANSLATING }.asFlow(),
            )
        }
}

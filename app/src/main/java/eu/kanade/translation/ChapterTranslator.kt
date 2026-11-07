package eu.kanade.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.translation.data.TranslationProvider
import eu.kanade.translation.detector.ComicLanguageDetector
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.Translation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationContext
import eu.kanade.translation.recognizer.TextRecognizer
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.AICoreModelVariant
import eu.kanade.translation.translator.AICoreTranslator
import eu.kanade.translation.translator.AICoreUnavailableException
import eu.kanade.translation.translator.TextTranslator
import eu.kanade.translation.translator.TextTranslatorLanguage
import eu.kanade.translation.translator.TextTranslators
import eu.kanade.translation.util.ComicTextPreprocessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import logcat.LogPriority
import mihon.core.archive.archiveReader
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.i18n.at.ATMR
import java.io.File
import java.io.InputStream
import kotlin.math.abs

class ChapterTranslator(
    private val context: Context,
    private val provider: TranslationProvider,
    private val downloadProvider: DownloadProvider,
    private val sourceManager: SourceManager,
    private val translationPreferences: TranslationPreferences,
) {

    private val _queueState = MutableStateFlow<List<Translation>>(emptyList())
    val queueState = _queueState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var translationJob: Job? = null

    val isRunning: Boolean
        get() = translationJob?.isActive == true

    @Volatile
    var isPaused: Boolean = false

    private var textRecognizer: TextRecognizer? = null
    private var textTranslator: TextTranslator? = null
    private var currentEngine: TextTranslators? = null
    private val languageDetector = ComicLanguageDetector

    init {
        // Recognizer and translator are initialized on-demand when translation jobs execute.
    }

    fun start(): Boolean {
        if (isRunning || queueState.value.isEmpty()) {
            return false
        }

        val pending = queueState.value.filter { it.status != Translation.State.TRANSLATED }
        pending.forEach { if (it.status != Translation.State.QUEUE) it.status = Translation.State.QUEUE }
        isPaused = false
        launchTranslatorJob()
        return pending.isNotEmpty()
    }

    fun stop(reason: String? = null) {
        cancelTranslatorJob()
        queueState.value.filter { it.status == Translation.State.TRANSLATING }
            .forEach { it.status = Translation.State.ERROR }
        if (reason != null) return
        isPaused = false
    }

    fun pause() {
        cancelTranslatorJob()
        queueState.value.filter { it.status == Translation.State.TRANSLATING }
            .forEach { it.status = Translation.State.QUEUE }
        isPaused = true
    }

    fun clearQueue() {
        cancelTranslatorJob()
        internalClearQueue()
    }

    fun destroy() {
        cancelTranslatorJob()
        textRecognizer?.close()
        textRecognizer = null
        textTranslator?.close()
        textTranslator = null
    }

    private fun launchTranslatorJob() {
        if (isRunning) return

        translationJob = scope.launch {
            val activeTranslationFlow = queueState.transformLatest { queue ->
                while (true) {
                    val activeTranslations =
                        queue.asSequence().filter { it.status.value <= Translation.State.TRANSLATING.value }
                            .groupBy { it.source }.toList().take(5).map { (_, translations) -> translations.first() }
                    emit(activeTranslations)

                    if (activeTranslations.isEmpty()) break
                    val activeTranslationsErroredFlow =
                        combine(activeTranslations.map(Translation::statusFlow)) { states ->
                            states.contains(Translation.State.ERROR)
                        }.filter { it }
                    activeTranslationsErroredFlow.first()
                }
            }.distinctUntilChanged()
            supervisorScope {
                val translationJobs = mutableMapOf<Translation, Job>()

                activeTranslationFlow.collectLatest { activeTranslations ->
                    val translationJobsToStop = translationJobs.filter { it.key !in activeTranslations }
                    translationJobsToStop.forEach { (download, job) ->
                        job.cancel()
                        translationJobs.remove(download)
                    }

                    val translationsToStart = activeTranslations.filter { it !in translationJobs }
                    translationsToStart.forEach { translation ->
                        translationJobs[translation] = launchTranslationJob(translation)
                    }
                }
            }
        }
    }

    private fun CoroutineScope.launchTranslationJob(translation: Translation) = launch {
        try {
            translateChapter(translation)
            if (translation.status == Translation.State.TRANSLATED) {
                removeFromQueue(translation)
            }
            if (areAllTranslationsFinished()) {
                stop()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e)
            stop()
        }
    }

    private fun cancelTranslatorJob() {
        translationJob?.cancel()
        translationJob = null
    }

    suspend fun queueChapter(manga: Manga, chapter: Chapter) {
        val source = sourceManager.get(manga.source) as? HttpSource ?: return
        val existingFile = provider.findTranslationFile(chapter.name, chapter.scanlator, manga.title, source)
        if (existingFile != null && existingFile.exists()) {
            val isComplete = try {
                if (existingFile.length() == 0L) {
                    false
                } else {
                    val content = existingFile.openInputStream()?.bufferedReader()?.use { it.readText() }
                    if (content.isNullOrBlank()) {
                        false
                    } else {
                        val parsed = Json.decodeFromString<Map<String, PageTranslation>>(content)
                        parsed.isNotEmpty() && parsed.values.any { page ->
                            page.blocks.isNotEmpty() && page.blocks.any {
                                it.translation.isNotBlank() && it.translation != it.text
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                false
            }
            if (isComplete) {
                return
            } else {
                runCatching { existingFile.delete() }
            }
        }
        val existing = queueState.value.find { it.chapter.id == chapter.id }
        if (existing != null) {
            if (existing.status == Translation.State.ERROR) {
                existing.status = Translation.State.QUEUE
                start()
            }
            return
        }
        val fromLang = TextRecognizerLanguage.fromPref(translationPreferences.translateFromLanguage())
        val toLang = TextTranslatorLanguage.fromPref(translationPreferences.translateToLanguage())
        val engine = TextTranslators.fromPref(translationPreferences.translationEngine())
        if (engine == TextTranslators.MLKIT && !TextTranslatorLanguage.mlkitSupportedLanguages().contains(toLang)) {
            withContext(Dispatchers.Main) {
                context.toast(ATMR.strings.error_mlkit_language_unsupported)
            }
            return
        }
        val translation = Translation(source, manga, chapter, fromLang, toLang)
        addToQueue(translation)
    }

    private suspend fun translateChapter(translation: Translation) {
        try {
            translation.error = null
            translation.progress = 0

            // Directory where chapter images is stored
            val chapterPath = downloadProvider.findChapterDir(
                chapterName = translation.chapter.name,
                chapterScanlator = translation.chapter.scanlator,
                chapterUrl = translation.chapter.url,
                mangaTitle = translation.manga.title,
                source = translation.source,
            ) ?: run {
                translation.error = context.stringResource(ATMR.strings.translation_error_not_downloaded)
                translation.status = Translation.State.ERROR
                return
            }

            // Resolve effective source language (handling AUTO detection)
            val effectiveFromLang = if (translation.fromLang == TextRecognizerLanguage.AUTO) {
                val resolved = languageDetector.detectLanguage(
                    source = translation.source,
                    manga = translation.manga,
                    sampleTextProvider = { sampleChapterText(chapterPath) },
                )
                logcat(LogPriority.INFO) {
                    "Auto-detected comic language for ${translation.manga.title}: $resolved"
                }
                resolved
            } else {
                translation.fromLang
            }

            // Check if recognizer reinitialization is needed
            val recognizer = textRecognizer.takeIf { it?.language == effectiveFromLang } ?: run {
                textRecognizer?.close()
                TextRecognizer(effectiveFromLang).also { textRecognizer = it }
            }
            // Check if translator reinitialization is needed
            val selectedEngine = TextTranslators.fromPref(translationPreferences.translationEngine())

            // Validate API key if selected engine requires it
            if (selectedEngine in
                listOf(TextTranslators.GOOGLE_CLOUD, TextTranslators.GEMINI, TextTranslators.OPENROUTER)
            ) {
                val apiKey: String = when (selectedEngine) {
                    TextTranslators.GOOGLE_CLOUD -> translationPreferences.cloudTranslationApiKey().get()
                    else -> translationPreferences.translationEngineApiKey().get()
                }
                if (apiKey.isBlank()) {
                    translation.error = context.stringResource(ATMR.strings.translation_error_api_key_missing)
                    translation.status = Translation.State.ERROR
                    return
                }
            }

            val currentAICoreTranslator = textTranslator as? AICoreTranslator
            val aicoreParamsChanged = currentAICoreTranslator != null && (
                currentAICoreTranslator.modelVariant !=
                    AICoreModelVariant.fromPref(translationPreferences.aicoreModelVariant().get()) ||
                    currentAICoreTranslator.useImageContext != translationPreferences.aicoreUsePageImageContext().get()
                )
            val translator = textTranslator.takeIf {
                it != null &&
                    it.fromLang == effectiveFromLang &&
                    it.toLang == translation.toLang &&
                    currentEngine == selectedEngine &&
                    !aicoreParamsChanged
            } ?: run {
                withContext(Dispatchers.IO) {
                    textTranslator?.close()
                }
                currentEngine = selectedEngine
                selectedEngine.build(
                    translationPreferences,
                    effectiveFromLang,
                    translation.toLang,
                ).also { textTranslator = it }
            }
            // Directory where translations for a manga is stored
            val translationMangaDir = provider.getMangaDir(translation.manga.title, translation.source)

            // translations save file
            val saveFile = provider.getTranslationFileName(translation.chapter.name, translation.chapter.scanlator)

            val pages = mutableMapOf<String, PageTranslation>()
            val tmpFile = java.io.File.createTempFile("at_trans_", ".tmp", context.cacheDir)
            try {
                withContext(Dispatchers.IO) {
                    if (chapterPath.isFile) {
                        chapterPath.archiveReader(context).use { reader ->
                            val entries = reader.useEntries { entries ->
                                entries.filter {
                                    it.isFile &&
                                        ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! }
                                }
                                    .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                                    .map { it.name }
                                    .toList()
                            }
                            translation.totalPages = entries.size
                            for ((index, entryName) in entries.withIndex()) {
                                coroutineContext.ensureActive()
                                reader.getInputStream(entryName)!!.use { inStream ->
                                    tmpFile.outputStream().use { outStream -> inStream.copyTo(outStream) }
                                }
                                val ocrResult = recognizeFileWithUpscaling(tmpFile, recognizer)
                                val pageTranslation =
                                    convertToPageTranslation(
                                        blocks = ocrResult.blocks,
                                        width = ocrResult.origWidth,
                                        height = ocrResult.origHeight,
                                        fromLang = effectiveFromLang,
                                        toLang = translation.toLang,
                                        engine = selectedEngine,
                                        ocrScale = ocrResult.scaleFactor,
                                    )
                                if (pageTranslation.blocks.isNotEmpty()) pages[entryName] = pageTranslation
                                translation.progress = index + 1
                            }

                            val translationContext = TranslationContext(
                                mangaTitle = translation.manga.title,
                                chapterTitle = translation.chapter.name,
                                genres = translation.manga.genre.orEmpty(),
                                synopsis = translation.manga.description.orEmpty(),
                                pageBitmapLoader = { pageKey ->
                                    decodeSampledBitmap(maxDimension = 768) {
                                        reader.getInputStream(pageKey)
                                    }
                                },
                            )
                            translator.translate(pages, translationContext)
                        }
                    } else {
                        val files = chapterPath.listFiles().orEmpty()
                            .filter { ImageUtil.isImage(it.name) }
                            .sortedWith { f1, f2 ->
                                f1.name.orEmpty().compareToCaseInsensitiveNaturalOrder(f2.name.orEmpty())
                            }
                        translation.totalPages = files.size
                        for ((index, file) in files.withIndex()) {
                            coroutineContext.ensureActive()
                            file.openInputStream().use { inStream ->
                                tmpFile.outputStream().use { outStream -> inStream.copyTo(outStream) }
                            }
                            val ocrResult = recognizeFileWithUpscaling(tmpFile, recognizer)
                            val pageTranslation =
                                convertToPageTranslation(
                                    blocks = ocrResult.blocks,
                                    width = ocrResult.origWidth,
                                    height = ocrResult.origHeight,
                                    fromLang = effectiveFromLang,
                                    toLang = translation.toLang,
                                    engine = selectedEngine,
                                    ocrScale = ocrResult.scaleFactor,
                                )
                            if (pageTranslation.blocks.isNotEmpty()) pages[file.name!!] = pageTranslation
                            translation.progress = index + 1
                        }

                        val translationContext = TranslationContext(
                            mangaTitle = translation.manga.title,
                            chapterTitle = translation.chapter.name,
                            genres = translation.manga.genre.orEmpty(),
                            synopsis = translation.manga.description.orEmpty(),
                            pageBitmapLoader = { pageKey ->
                                files.find { it.name == pageKey }?.let { file ->
                                    decodeSampledBitmap(maxDimension = 768) {
                                        file.openInputStream()
                                    }
                                }
                            },
                        )
                        translator.translate(pages, translationContext)
                    }
                }
            } finally {
                tmpFile.delete()
            }
            // Serialize the Map and save to translations json file
            val targetFile = translationMangaDir.findFile(saveFile) ?: translationMangaDir.createFile(saveFile)!!
            Json.encodeToStream(pages, targetFile.openOutputStream())
            translation.error = null
            translation.progress = translation.totalPages
            translation.status = Translation.State.TRANSLATED
        } catch (e: AICoreUnavailableException) {
            withContext(Dispatchers.Main) {
                context.toast(ATMR.strings.error_aicore_unavailable)
            }
            translation.error = context.stringResource(ATMR.strings.error_aicore_unavailable)
            translation.status = Translation.State.ERROR
            logcat(LogPriority.ERROR, e)
        } catch (error: Throwable) {
            val message = error.message.orEmpty()
            val userMsg = when {
                message.contains("429") || message.contains("quota", ignoreCase = true) ||
                    message.contains("rate", ignoreCase = true) ->
                    context.stringResource(ATMR.strings.translation_error_quota_exceeded)
                message.contains("network", ignoreCase = true) || message.contains("timeout", ignoreCase = true) ||
                    message.contains("connect", ignoreCase = true) ->
                    context.stringResource(ATMR.strings.translation_error_network)
                message.isNotBlank() -> message
                else -> context.stringResource(ATMR.strings.translation_error_generic, "Unknown error")
            }
            translation.error = userMsg
            translation.status = Translation.State.ERROR
            logcat(LogPriority.ERROR, error)
        }
    }

    private suspend fun sampleChapterText(chapterPath: UniFile): String? = withContext(Dispatchers.IO) {
        try {
            val tmpFile = java.io.File.createTempFile("at_sample_", ".tmp", context.cacheDir)
            try {
                if (chapterPath.isFile) {
                    chapterPath.archiveReader(context).use { reader ->
                        val entries = reader.useEntries { entries ->
                            entries.filter {
                                it.isFile && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! }
                            }
                                .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                                .map { it.name }
                                .toList()
                        }
                        if (entries.isEmpty()) return@withContext null
                        val candidateIndices = when {
                            entries.size > 2 -> listOf(1, 2, 0)
                            entries.size > 1 -> listOf(1, 0)
                            else -> listOf(0)
                        }
                        var fallbackText: String? = null
                        for (idx in candidateIndices) {
                            val entryName = entries[idx]
                            val copied = reader.getInputStream(entryName)?.use { inStream ->
                                tmpFile.outputStream().use { outStream -> inStream.copyTo(outStream) }
                                true
                            } ?: false
                            if (copied) {
                                val image = InputImage.fromFilePath(context, android.net.Uri.fromFile(tmpFile))
                                val probed = runProbeOcr(image)
                                if (!probed.isNullOrBlank()) {
                                    if (probed.length >= 10) {
                                        return@withContext probed
                                    }
                                    if (fallbackText == null) {
                                        fallbackText = probed
                                    }
                                }
                            }
                        }
                        fallbackText
                    }
                } else {
                    val files = chapterPath.listFiles().orEmpty()
                        .filter { ImageUtil.isImage(it.name) }
                        .sortedWith { f1, f2 ->
                            f1.name.orEmpty().compareToCaseInsensitiveNaturalOrder(f2.name.orEmpty())
                        }
                    if (files.isEmpty()) return@withContext null
                    val candidateIndices = when {
                        files.size > 2 -> listOf(1, 2, 0)
                        files.size > 1 -> listOf(1, 0)
                        else -> listOf(0)
                    }
                    var fallbackText: String? = null
                    for (idx in candidateIndices) {
                        val file = files[idx]
                        val copied = try {
                            file.openInputStream().use { inStream ->
                                tmpFile.outputStream().use { outStream -> inStream.copyTo(outStream) }
                                true
                            }
                        } catch (_: Throwable) {
                            false
                        }
                        if (copied) {
                            val image = InputImage.fromFilePath(context, android.net.Uri.fromFile(tmpFile))
                            val probed = runProbeOcr(image)
                            if (!probed.isNullOrBlank()) {
                                if (probed.length >= 10) {
                                    return@withContext probed
                                }
                                if (fallbackText == null) {
                                    fallbackText = probed
                                }
                            }
                        }
                    }
                    fallbackText
                }
            } finally {
                tmpFile.delete()
            }
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "Failed to extract sample text from chapter images" }
            null
        }
    }

    private fun runProbeOcr(image: InputImage): String? {
        val japaneseResult = try {
            TextRecognizer(TextRecognizerLanguage.JAPANESE).use { recognizer ->
                recognizer.recognize(image).text
            }
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "Probe OCR with Japanese recognizer failed" }
            null
        }

        val trimmedJapanese = japaneseResult?.trim().orEmpty()
        val hasJapaneseOrChinese = trimmedJapanese.any {
            ComicLanguageDetector.isKana(it) || ComicLanguageDetector.isCjkUnified(it)
        }

        if (hasJapaneseOrChinese && trimmedJapanese.length >= 5) {
            return trimmedJapanese
        }

        val koreanResult = try {
            TextRecognizer(TextRecognizerLanguage.KOREAN).use { recognizer ->
                recognizer.recognize(image).text
            }
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "Probe OCR with Korean recognizer failed" }
            null
        }

        val trimmedKorean = koreanResult?.trim().orEmpty()
        val hasHangul = trimmedKorean.any { ComicLanguageDetector.isHangul(it) }

        return when {
            hasHangul -> trimmedKorean
            hasJapaneseOrChinese -> trimmedJapanese
            trimmedKorean.length > trimmedJapanese.length && trimmedKorean.isNotEmpty() -> trimmedKorean
            trimmedJapanese.isNotEmpty() -> trimmedJapanese
            trimmedKorean.isNotEmpty() -> trimmedKorean
            else -> null
        }
    }

    private fun decodeSampledBitmap(
        maxDimension: Int = 768,
        streamProvider: () -> InputStream?,
    ): Bitmap? {
        val bytes = try {
            streamProvider()?.use { it.readBytes() }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to read bytes for bitmap" }
            null
        } ?: return null

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to decode image bounds" }
            return null
        }

        val origWidth = options.outWidth
        val origHeight = options.outHeight
        if (origWidth <= 0 || origHeight <= 0) return null

        var inSampleSize = 1
        val maxSide = maxOf(origWidth, origHeight)
        while (maxSide / inSampleSize > maxDimension) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to decode sampled image bitmap" }
            null
        }
    }

    private data class OcrRecognitionResult(
        val blocks: List<Text.TextBlock>,
        val origWidth: Int,
        val origHeight: Int,
        val scaleFactor: Float,
    )

    private fun recognizeFileWithUpscaling(
        file: File,
        recognizer: TextRecognizer,
    ): OcrRecognitionResult {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        try {
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "Failed to decode image bounds for OCR" }
        }
        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight

        if (origWidth in 1..599 && origHeight in 1..4096) {
            var rawBitmap: Bitmap? = null
            var scaledBitmap: Bitmap? = null
            try {
                val decodeOptions = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                rawBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                if (rawBitmap != null) {
                    val targetWidth = origWidth * 2
                    val targetHeight = origHeight * 2
                    scaledBitmap = Bitmap.createScaledBitmap(rawBitmap, targetWidth, targetHeight, true)
                    if (scaledBitmap !== rawBitmap) {
                        rawBitmap.recycle()
                        rawBitmap = null
                    }
                    val inputImage = InputImage.fromBitmap(scaledBitmap, 0)
                    val result = recognizer.recognize(inputImage)
                    val blocks = result.textBlocks.filter { it.boundingBox != null && it.text.length > 1 }
                    return OcrRecognitionResult(
                        blocks = blocks,
                        origWidth = origWidth,
                        origHeight = origHeight,
                        scaleFactor = 2.0f,
                    )
                }
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, e) { "Failed to upscale image for OCR, falling back to original file" }
            } finally {
                if (rawBitmap != null && !rawBitmap.isRecycled) {
                    rawBitmap.recycle()
                }
                if (scaledBitmap != null && !scaledBitmap.isRecycled) {
                    scaledBitmap.recycle()
                }
            }
        }

        val image = InputImage.fromFilePath(context, android.net.Uri.fromFile(file))
        val result = recognizer.recognize(image)
        val blocks = result.textBlocks.filter { it.boundingBox != null && it.text.length > 1 }
        return OcrRecognitionResult(
            blocks = blocks,
            origWidth = image.width,
            origHeight = image.height,
            scaleFactor = 1.0f,
        )
    }

    private fun convertToPageTranslation(
        blocks: List<Text.TextBlock>,
        width: Int,
        height: Int,
        fromLang: TextRecognizerLanguage,
        toLang: TextTranslatorLanguage = TextTranslatorLanguage.ENGLISH,
        engine: TextTranslators? = null,
        ocrScale: Float = 1.0f,
    ): PageTranslation {
        val translation = PageTranslation(
            imgWidth = width.toFloat(),
            imgHeight = height.toFloat(),
            sourceLanguage = fromLang.toCloudApiCode(),
            targetLanguage = toLang.toCloudApiCode(),
            translatorType = engine?.name?.lowercase() ?: "gemini",
        )
        val safeScale = if (ocrScale > 0f) ocrScale else 1.0f
        for (block in blocks) {
            val bounds = block.boundingBox ?: continue
            val cleanText = ComicTextPreprocessor.preprocessOcrBlockText(block.text)
            if (ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark(cleanText)) continue

            val firstLine = block.lines.firstOrNull()
            val firstElement = firstLine?.elements?.firstOrNull()
            val firstSymbol = firstElement?.symbols?.firstOrNull()
            val symBounds = firstSymbol?.boundingBox ?: firstElement?.boundingBox
            val lineCount = maxOf(1, block.lines.size)
            val rawSymWidth = (symBounds?.width()?.toFloat() ?: (bounds.width().toFloat() / maxOf(1, cleanText.length))) / safeScale
            val rawSymHeight = (symBounds?.height()?.toFloat() ?: (bounds.height().toFloat() / lineCount)) / safeScale
            val symWidth = rawSymWidth.coerceIn(8f, 36f)
            val symHeight = rawSymHeight.coerceIn(12f, 48f)
            val angle = firstLine?.angle ?: 0f

            translation.blocks.add(
                TranslationBlock(
                    text = cleanText,
                    width = bounds.width().toFloat() / safeScale,
                    height = bounds.height().toFloat() / safeScale,
                    symWidth = symWidth,
                    symHeight = symHeight,
                    angle = angle,
                    x = bounds.left.toFloat() / safeScale,
                    y = bounds.top.toFloat() / safeScale,
                ),
            )
        }
        // Smart merge overlapping text blocks with language-specific thresholds
        val (widthThresh, xThresh, yThresh) = getLanguageSpecificMergeThresholds(fromLang)
        val mergedBlocks = smartMergeBlocks(translation.blocks, widthThresh, xThresh, yThresh)
        val cleanedBlocks = mergedBlocks.mapNotNull { block ->
            val cleaned = ComicTextPreprocessor.preprocessOcrBlockText(block.text)
            if (ComicTextPreprocessor.isLikelyOcrNoiseOrWatermark(cleaned)) {
                null
            } else {
                block.copy(text = cleaned)
            }
        }
        // Sort in comic reading order
        translation.blocks = AICoreTranslator.sortBlocksInReadingOrder(cleanedBlocks, fromLang).toMutableList()

        return translation
    }

    private fun areAllTranslationsFinished(): Boolean {
        return queueState.value.none { it.status.value <= Translation.State.TRANSLATING.value }
    }

    private fun addToQueue(translation: Translation) {
        translation.error = null
        translation.progress = 0
        translation.status = Translation.State.QUEUE
        _queueState.update {
            it + translation
        }
    }

    private fun removeFromQueue(translation: Translation) {
        _queueState.update {
            if (translation.status == Translation.State.TRANSLATING ||
                translation.status == Translation.State.QUEUE ||
                translation.status == Translation.State.ERROR
            ) {
                translation.status = Translation.State.NOT_TRANSLATED
            }
            it - translation
        }
    }

    private inline fun removeFromQueueIf(predicate: (Translation) -> Boolean) {
        _queueState.update { queue ->
            val translations = queue.filter { predicate(it) }
            translations.forEach { translation ->
                if (translation.status == Translation.State.TRANSLATING ||
                    translation.status == Translation.State.QUEUE ||
                    translation.status == Translation.State.ERROR
                ) {
                    translation.status = Translation.State.NOT_TRANSLATED
                }
            }
            queue - translations
        }
    }

    fun removeFromQueue(chapter: Chapter) {
        removeFromQueueIf { it.chapter.id == chapter.id }
    }

    fun removeFromQueue(manga: Manga) {
        removeFromQueueIf { it.manga.id == manga.id }
    }

    fun reorderQueue(translations: List<Translation>) {
        _queueState.update { currentQueue ->
            val set = translations.toSet()
            val remaining = currentQueue.filterNot { it in set }
            translations + remaining
        }
    }

    private fun internalClearQueue() {
        _queueState.update {
            it.forEach { translation ->
                if (translation.status == Translation.State.TRANSLATING ||
                    translation.status == Translation.State.QUEUE ||
                    translation.status == Translation.State.ERROR
                ) {
                    translation.status = Translation.State.NOT_TRANSLATED
                }
            }
            emptyList()
        }
    }

    companion object {
        /**
         * Get language-specific text merging thresholds.
         * Korean text has different spacing patterns than Japanese/Chinese.
         * Returns (widthThreshold, xThreshold, yThreshold)
         */
        internal fun getLanguageSpecificMergeThresholds(language: TextRecognizerLanguage): Triple<Int, Int, Int> {
            return when (language) {
                // Korean needs larger thresholds due to spacing differences in Hangul
                TextRecognizerLanguage.KOREAN -> Triple(60, 35, 40)
                // Japanese with mixed Kanji/Hiragana
                TextRecognizerLanguage.JAPANESE -> Triple(50, 30, 30)
                // Chinese characters are more uniform
                TextRecognizerLanguage.CHINESE -> Triple(45, 25, 28)
                // Default for other languages
                else -> Triple(50, 30, 30)
            }
        }

        internal fun smartMergeBlocks(
            blocks: List<TranslationBlock>,
            widthThreshold: Int,
            xThreshold: Int,
            yThreshold: Int,
        ): MutableList<TranslationBlock> {
            if (blocks.size <= 1) return blocks.toMutableList()

            var list = blocks.toMutableList()
            list.sortBy { it.y }

            do {
                var mergedInPass = false
                val nextPass = mutableListOf<TranslationBlock>()
                val skip = BooleanArray(list.size)

                for (i in 0 until list.size) {
                    if (skip[i]) continue
                    var current = list[i]
                    for (j in (i + 1) until list.size) {
                        if (skip[j]) continue
                        val next = list[j]
                        if (shouldMergeTextBlock(current, next, widthThreshold, xThreshold, yThreshold)) {
                            current = mergeTextBlock(current, next)
                            skip[j] = true
                            mergedInPass = true
                        }
                    }
                    nextPass.add(current)
                }
                list = nextPass
                list.sortBy { it.y }
            } while (mergedInPass)

            return list
        }

        internal fun shouldMergeTextBlock(
            a: TranslationBlock,
            b: TranslationBlock,
            widthThreshold: Int,
            xThreshold: Int,
            yThreshold: Int,
        ): Boolean {
            val top = if (a.y <= b.y) a else b
            val bottom = if (a.y <= b.y) b else a

            val verticalGap = bottom.y - (top.y + top.height)
            val maxLineHeight = maxOf(top.symHeight, bottom.symHeight, 20f)
            val allowedYGap = maxOf(yThreshold.toFloat(), maxLineHeight * 1.6f)
            val isYClose = verticalGap >= -maxLineHeight * 0.6f && verticalGap <= allowedYGap

            val centerXa = top.x + top.width / 2f
            val centerXb = bottom.x + bottom.width / 2f
            val maxW = maxOf(top.width, bottom.width)
            val isCenteredAligned = kotlin.math.abs(centerXa - centerXb) <= maxOf(xThreshold.toFloat(), maxW * 0.35f)

            val overlapLeft = maxOf(top.x, bottom.x)
            val overlapRight = minOf(top.x + top.width, bottom.x + bottom.width)
            val overlapWidth = (overlapRight - overlapLeft).coerceAtLeast(0f)
            val minW = minOf(top.width, bottom.width).coerceAtLeast(1f)
            val hasHorizontalOverlap = (overlapWidth / minW) >= 0.45f

            val isLeftAligned = kotlin.math.abs(top.x - bottom.x) < xThreshold &&
                ((bottom.width < top.width) || kotlin.math.abs(top.width - bottom.width) < widthThreshold)

            val isFontCompatible = maxOf(top.symHeight, bottom.symHeight) <=
                minOf(top.symHeight, bottom.symHeight).coerceAtLeast(1f) * 2.2f

            return isYClose && (isCenteredAligned || hasHorizontalOverlap || isLeftAligned) && isFontCompatible
        }

        internal fun mergeTextBlock(a: TranslationBlock, b: TranslationBlock): TranslationBlock {
            val top = if (a.y <= b.y) a else b
            val bottom = if (a.y <= b.y) b else a

            val newX = minOf(top.x, bottom.x)
            val newY = minOf(top.y, bottom.y)
            val newWidth = maxOf(top.x + top.width, bottom.x + bottom.width) - newX
            val newHeight = maxOf(top.y + top.height, bottom.y + bottom.height) - newY

            val cleanTopText = top.text.trim().replace(Regex("\\s*\\n\\s*"), " ")
            val cleanBottomText = bottom.text.trim().replace(Regex("\\s*\\n\\s*"), " ")

            val mergedText = if (cleanTopText.endsWith("-")) {
                cleanTopText.removeSuffix("-") + cleanBottomText
            } else {
                "$cleanTopText $cleanBottomText"
            }

            val cleanTopTrans = top.translation.trim().replace(Regex("\\s*\\n\\s*"), " ")
            val cleanBottomTrans = bottom.translation.trim().replace(Regex("\\s*\\n\\s*"), " ")
            val mergedTranslation = when {
                cleanTopTrans.isBlank() -> cleanBottomTrans
                cleanBottomTrans.isBlank() -> cleanTopTrans
                cleanTopTrans.endsWith("-") -> cleanTopTrans.removeSuffix("-") + cleanBottomTrans
                else -> "$cleanTopTrans $cleanBottomTrans"
            }

            val avgSymHeight = (top.symHeight + bottom.symHeight) / 2f
            val avgSymWidth = (top.symWidth + bottom.symWidth) / 2f

            return TranslationBlock(
                text = mergedText,
                translation = mergedTranslation,
                width = newWidth,
                height = newHeight,
                x = newX,
                y = newY,
                symHeight = avgSymHeight,
                symWidth = avgSymWidth,
                angle = top.angle,
            )
        }
    }
}

package eu.kanade.translation.translator

import android.graphics.Bitmap
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Feature
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationContext
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import logcat.LogPriority
import org.json.JSONArray
import tachiyomi.core.common.util.system.logcat
import kotlin.math.abs

class AICoreUnavailableException(
    message: String = "AICore is unavailable on this device",
    cause: Throwable? = null,
) : Exception(message, cause)

class AICoreTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    maxOutputTokens: Int,
    temperature: Float,
    private val useImageContext: Boolean = false,
) : TextTranslator {

    private val clampedMaxOutputTokens = maxOutputTokens.coerceIn(128, 1024)
    private val clampedTemperature = temperature.coerceIn(0.0f, 1.0f)

    private var model: GenerativeModel? = null
    private var multimediaSupported = true
    private var systemPromptSupported: Boolean? = null

    override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
        translate(pages, TranslationContext())
    }

    override suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        context: TranslationContext,
    ) {
        logcat(LogPriority.INFO) { "AICore: translate called for ${pages.size} pages" }
        val generativeModel = getOrInitModel()
        logcat(LogPriority.INFO) { "AICore: model obtained successfully" }
        val canUseSystemPrompt = systemPromptSupported ?: checkSystemPromptAvailable(generativeModel).also {
            systemPromptSupported = it
        }
        logcat(LogPriority.INFO) { "AICore: canUseSystemPrompt = $canUseSystemPrompt" }

        val systemInstructionText = buildSystemInstruction(toLang.label)

        val systemInstruction = if (canUseSystemPrompt) {
            SystemInstruction(systemInstructionText)
        } else {
            null
        }

        val sortedPageKeys = pages.keys.sortedWith { f1, f2 ->
            f1.compareToCaseInsensitiveNaturalOrder(f2)
        }

        var previousDialogs = emptyList<Pair<String, String>>()

        for (pageKey in sortedPageKeys) {
            val pageTranslation = pages[pageKey] ?: continue
            val sortedBlocks = sortBlocksInReadingOrder(pageTranslation.blocks, fromLang)
            if (sortedBlocks.isEmpty()) continue

            val currentTexts = sortedBlocks.map { normalizeOcrText(it.text) }
            val prompt = buildContextualPrompt(
                context = context,
                targetLanguage = toLang.label,
                previousDialogs = previousDialogs,
                currentTexts = currentTexts,
            )

            val fullPrompt = if (systemInstruction == null) {
                "$systemInstructionText\n\n$prompt"
            } else {
                prompt
            }

            val pageBitmap = if (useImageContext && multimediaSupported) {
                logcat(LogPriority.INFO) { "AICore: loading page bitmap for $pageKey..." }
                try {
                    context.pageBitmapLoader?.invoke(pageKey).also {
                        logcat(LogPriority.INFO) { "AICore: page bitmap loaded: ${it?.width}x${it?.height}" }
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to load page bitmap for $pageKey" }
                    null
                }
            } else {
                null
            }

            try {
                var translations: List<String>? = null
                try {
                    logcat(LogPriority.INFO) {
                        "AICore: executing page translation for $pageKey (${sortedBlocks.size} blocks)..."
                    }
                    val response = executePrompt(
                        prompt = fullPrompt,
                        pageBitmap = pageBitmap,
                        systemInstruction = systemInstruction,
                    )
                    val rawOutput = response?.candidates?.firstOrNull()?.text
                    logcat(LogPriority.INFO) { "AICore: page translation raw output: $rawOutput" }
                    val parsed = parseJsonArrayResponse(rawOutput, expectedCount = sortedBlocks.size)
                    if (parsed != null && parsed.isNotEmpty()) {
                        translations = parsed
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.WARN, e) { "AICore page translation failed, falling back to per-block" }
                }

                if (translations != null && translations.size == sortedBlocks.size) {
                    sortedBlocks.forEachIndexed { index, block ->
                        val trans = translations[index]
                        block.translation = if (trans.isNotBlank()) trans else block.text
                    }
                } else {
                    for ((index, block) in sortedBlocks.withIndex()) {
                        val existingTrans = translations?.getOrNull(index)?.takeIf { it.isNotBlank() }
                        if (existingTrans != null) {
                            block.translation = existingTrans
                            continue
                        }
                        val normalizedText = normalizeOcrText(block.text)
                        val singlePrompt = "Translate the following comic dialog into ${toLang.label}. " +
                            "Replace watermarks or site links with \"RTMTH\". " +
                            "Output ONLY the translation without quotes:\n$normalizedText"
                        val blockPrompt = if (systemInstruction == null) {
                            "$systemInstructionText\n\n$singlePrompt"
                        } else {
                            singlePrompt
                        }
                        try {
                            val blockResponse = executePrompt(
                                prompt = blockPrompt,
                                pageBitmap = pageBitmap,
                                systemInstruction = systemInstruction,
                            )
                            val trans = blockResponse?.candidates?.firstOrNull()?.text?.trim()
                            block.translation = if (!trans.isNullOrBlank()) trans else block.text
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN, e) { "AICore fallback block translation failed" }
                            block.translation = block.text
                        }
                    }
                }

                pageTranslation.blocks = sortedBlocks.filterNot {
                    it.translation.contains("RTMTH")
                }.toMutableList()

                val validDialogs = pageTranslation.blocks.filterNot {
                    it.translation.isBlank()
                }.map { it.text to it.translation }
                if (validDialogs.isNotEmpty()) {
                    previousDialogs = validDialogs.takeLast(4)
                }
            } finally {
                pageBitmap?.recycle()
                runCatching { generativeModel.clearImplicitCaches() }
                delay(100L)
            }
        }
    }

    private suspend fun checkSystemPromptAvailable(model: GenerativeModel): Boolean {
        logcat(LogPriority.INFO) { "AICore: checkSystemPromptAvailable start" }
        val result = try {
            model.getAvailableFeatures().contains(Feature.SYSTEM_PROMPT)
        } catch (t: Throwable) {
            logcat(LogPriority.WARN, t) { "AICore: getAvailableFeatures threw, trying fallback" }
            try {
                @Suppress("DEPRECATION")
                model.isSystemPromptAvailable()
            } catch (t2: Throwable) {
                logcat(LogPriority.WARN, t2) { "AICore: isSystemPromptAvailable threw, defaulting to false" }
                false
            }
        }
        logcat(LogPriority.INFO) { "AICore: checkSystemPromptAvailable result = $result" }
        return result
    }

    private suspend fun getOrInitModel(): GenerativeModel {
        val existing = model
        if (existing != null) return existing

        logcat(LogPriority.INFO) { "AICore: getOrInitModel - calling Generation.getClient()" }
        val generativeModel = try {
            Generation.getClient()
        } catch (t: Throwable) {
            logcat(LogPriority.ERROR, t) { "AICore: Generation.getClient() failed" }
            throw AICoreUnavailableException("Failed to initialize AICore client", t)
        }

        logcat(LogPriority.INFO) { "AICore: getOrInitModel - calling checkStatus()" }
        val status = try {
            generativeModel.checkStatus()
        } catch (t: Throwable) {
            logcat(LogPriority.ERROR, t) { "AICore: checkStatus() failed" }
            throw AICoreUnavailableException("Failed to check AICore status", t)
        }
        logcat(LogPriority.INFO) { "AICore: getOrInitModel - status = $status" }

        if (status == FeatureStatus.UNAVAILABLE) {
            throw AICoreUnavailableException()
        }
        if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
            logcat(LogPriority.INFO) { "AICore: getOrInitModel - downloading model..." }
            try {
                generativeModel.download().first { downloadStatus ->
                    logcat(LogPriority.INFO) { "AICore: download status: $downloadStatus" }
                    if (downloadStatus is DownloadStatus.DownloadFailed) {
                        throw AICoreUnavailableException("Failed to download AICore model", downloadStatus.e)
                    }
                    downloadStatus is DownloadStatus.DownloadCompleted
                }
            } catch (t: Throwable) {
                if (t is AICoreUnavailableException) throw t
                logcat(LogPriority.ERROR, t) { "AICore: download failed" }
                throw AICoreUnavailableException("Failed to download AICore model", t)
            }
        }

        logcat(LogPriority.INFO) { "AICore: getOrInitModel - calling warmup()..." }
        try {
            generativeModel.warmup()
            logcat(LogPriority.INFO) { "AICore: getOrInitModel - warmup() finished successfully" }
        } catch (t: Throwable) {
            logcat(LogPriority.WARN, t) { "AICore: warmup() threw exception, continuing without warmup" }
        }

        model = generativeModel
        return generativeModel
    }

    private suspend fun generateWithRetry(
        request: com.google.mlkit.genai.prompt.GenerateContentRequest,
    ): com.google.mlkit.genai.prompt.GenerateContentResponse? {
        var lastError: Throwable? = null
        for (attempt in 0..2) {
            if (attempt > 0) {
                delay(600L * attempt)
                val currentModel = model
                if (currentModel != null) {
                    runCatching { currentModel.clearImplicitCaches() }
                }
                if (attempt == 2) {
                    runCatching { model?.close() }
                    model = null
                }
            }
            try {
                val m = getOrInitModel()
                return m.generateContent(request)
            } catch (t: Throwable) {
                lastError = t
                logcat(LogPriority.WARN, t) { "AICore generateWithRetry attempt $attempt failed" }
            }
        }
        if (lastError != null) {
            logcat(LogPriority.ERROR, lastError) { "AICore generateWithRetry all attempts failed" }
        }
        return null
    }

    private suspend fun executePrompt(
        prompt: String,
        pageBitmap: Bitmap?,
        systemInstruction: SystemInstruction?,
    ): com.google.mlkit.genai.prompt.GenerateContentResponse? {
        logcat(LogPriority.INFO) {
            "AICore: executePrompt - prompt length=${prompt.length}, bitmap=${pageBitmap != null}"
        }
        val textPart = TextPart(prompt)
        if (pageBitmap != null && multimediaSupported) {
            logcat(LogPriority.INFO) { "AICore: attempting multimodal call..." }
            val imagePart = ImagePart(pageBitmap)
            val request = if (systemInstruction != null) {
                generateContentRequest(systemInstruction, imagePart, textPart) {
                    temperature = clampedTemperature
                    maxOutputTokens = clampedMaxOutputTokens
                }
            } else {
                generateContentRequest(imagePart, textPart) {
                    temperature = clampedTemperature
                    maxOutputTokens = clampedMaxOutputTokens
                }
            }
            val resp = generateWithRetry(request)
            if (resp != null) {
                logcat(LogPriority.INFO) { "AICore: multimodal call succeeded!" }
                return resp
            }
            logcat(LogPriority.WARN) { "AICore multimodal call failed with retry, falling back to text-only" }
            multimediaSupported = false
        }

        logcat(LogPriority.INFO) { "AICore: attempting text-only call..." }
        val request = if (systemInstruction != null) {
            generateContentRequest(systemInstruction, textPart) {
                temperature = clampedTemperature
                maxOutputTokens = clampedMaxOutputTokens
            }
        } else {
            generateContentRequest(textPart) {
                temperature = clampedTemperature
                maxOutputTokens = clampedMaxOutputTokens
            }
        }
        val resp = generateWithRetry(request)
        if (resp != null) {
            logcat(LogPriority.INFO) { "AICore: text-only call succeeded!" }
        }
        return resp
    }

    override fun close() {
        model?.close()
        model = null
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun normalizeOcrText(text: String): String {
            return text
                .replace(Regex("-\\s*\\n\\s*"), "")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun buildSystemInstruction(targetLangName: String): String {
            return """
                You are an expert comic translator specialized in manga, manhwa, and manhua.
                Translate dialog accurately, naturally, and in character into $targetLangName.

                Strict rules:
                1. Automatically infer and fix comic OCR character misreads (e.g. "SD WH" -> "SO WHY", "ANYNATSP" -> "ANYWAYS?", "VY" -> "MY", "HON" -> "HOW", "PEAL" -> "DEAL", "SLDNLH" -> "SLOWLY", confusing '0'/'O', '1'/'I'/'l').
                2. Translate into natural, idiomatic, conversational $targetLangName suitable for comics/manhwa (avoid stiff word-for-word translation, e.g. avoid translating "It's why" as "It adalah alasannya").
                3. Keep established comic/manhwa terminology natural and consistent (e.g., Hunter, Gate, Dungeon, Raid, Guild, Rank, Mana, Boss, Awakening, Status Window).
                4. Replace any watermarks, translator credits, or website URLs (e.g. scanlation credits or domain names like "*.com") with "RTMTH".
                5. Return ONLY a valid JSON array of strings matching the exact number and order of input blocks: ["translation 1", "translation 2", ...].
                6. Do NOT include markdown formatting, code fences (```), or explanations. Never include raw unescaped newlines inside the JSON strings.
            """.trimIndent()
        }

        fun sanitizeJsonStringLiterals(raw: String): String {
            val sb = StringBuilder(raw.length)
            var inString = false
            var escaped = false
            for (i in 0 until raw.length) {
                val c = raw[i]
                if (escaped) {
                    sb.append(c)
                    escaped = false
                    continue
                }
                if (c == '\\') {
                    sb.append(c)
                    escaped = true
                    continue
                }
                if (c == '"') {
                    inString = !inString
                    sb.append(c)
                    continue
                }
                if (inString) {
                    when (c) {
                        '\n', '\r', '\t' -> sb.append(' ')
                        else -> sb.append(c)
                    }
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        fun parseNumberedLinesResponse(rawText: String?, expectedCount: Int): List<String>? {
            if (rawText.isNullOrBlank()) return null
            val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
            val lineRegex = Regex("^(?:\\[?(\\d+)\\]?[:.]?|-|\\*)\\s*(.*)$")
            val items = mutableListOf<String>()
            for (line in lines) {
                val match = lineRegex.find(line)
                if (match != null) {
                    val content = match.groupValues[2].trim().removeSurrounding("\"")
                    items.add(content)
                }
            }
            return if (items.size == expectedCount) items else null
        }

        fun truncateSynopsis(synopsis: String, maxLength: Int = 350): String {
            val normalized = synopsis.replace(Regex("\\s+"), " ").trim()
            return if (normalized.length <= maxLength) {
                normalized
            } else {
                normalized.take(maxLength).trimEnd()
            }
        }

        fun sortBlocksInReadingOrder(
            blocks: List<TranslationBlock>,
            fromLang: TextRecognizerLanguage,
        ): List<TranslationBlock> {
            if (blocks.size <= 1) return blocks

            val sortedByY = blocks.sortedBy { it.y }
            val rows = mutableListOf<MutableList<TranslationBlock>>()

            for (block in sortedByY) {
                val matchingRow = rows.find { row ->
                    row.any { abs(it.y - block.y) <= minOf(it.height, block.height) * 0.5f }
                }
                if (matchingRow != null) {
                    matchingRow.add(block)
                } else {
                    rows.add(mutableListOf(block))
                }
            }

            rows.sortBy { row -> row.minOf { it.y } }

            val isRtl = fromLang == TextRecognizerLanguage.JAPANESE
            return rows.flatMap { row ->
                if (isRtl) {
                    row.sortedWith { a, b -> b.x.compareTo(a.x) }
                } else {
                    row.sortedWith { a, b -> a.x.compareTo(b.x) }
                }
            }
        }

        fun buildContextualPrompt(
            context: TranslationContext,
            targetLanguage: String,
            previousDialogs: List<Pair<String, String>>,
            currentTexts: List<String>,
        ): String {
            val builder = StringBuilder()

            val truncatedSynopsis = truncateSynopsis(context.synopsis)
            val hasComicContext = context.mangaTitle.isNotBlank() ||
                context.chapterTitle.isNotBlank() ||
                context.genres.isNotEmpty() ||
                truncatedSynopsis.isNotBlank()

            if (hasComicContext) {
                builder.append("### Comic Context:\n")
                if (context.mangaTitle.isNotBlank()) {
                    builder.append("- Title: ").append(context.mangaTitle).append("\n")
                }
                if (context.chapterTitle.isNotBlank()) {
                    builder.append("- Chapter: ").append(context.chapterTitle).append("\n")
                }
                if (context.genres.isNotEmpty()) {
                    builder.append("- Genres: ").append(context.genres.joinToString(", ")).append("\n")
                }
                if (truncatedSynopsis.isNotBlank()) {
                    builder.append("- Synopsis: ").append(truncatedSynopsis).append("\n")
                }
                builder.append("\n")
            }

            if (previousDialogs.isNotEmpty()) {
                builder.append("### Previous Page Dialogs (for story continuity):\n")
                for ((orig, trans) in previousDialogs.takeLast(4)) {
                    builder.append("- \"").append(orig).append("\" -> \"").append(trans).append("\"\n")
                }
                builder.append("\n")
            }

            val jsonArrayString = json.encodeToString(currentTexts)
            builder.append("### Current Page Texts (Translate to ").append(targetLanguage).append("):\n")
            builder.append("Translate each text into ").append(targetLanguage)
                .append(", replace watermarks/URLs with \"RTMTH\", and output ONLY a JSON array of strings of length ")
                .append(currentTexts.size).append(":\n")
            builder.append(jsonArrayString)

            return builder.toString()
        }

        fun parseJsonArrayResponse(rawText: String?, expectedCount: Int? = null): List<String>? {
            if (rawText.isNullOrBlank()) return null
            var cleaned = rawText.trim()

            if (cleaned.startsWith("```")) {
                cleaned = cleaned.removePrefix("```json").removePrefix("```").trim()
                if (cleaned.endsWith("```")) {
                    cleaned = cleaned.removeSuffix("```").trim()
                }
            }

            val startIndex = cleaned.indexOf('[')
            val endIndex = cleaned.lastIndexOf(']')
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                val jsonSubstr = sanitizeJsonStringLiterals(cleaned.substring(startIndex, endIndex + 1))
                val jsonResult = try {
                    val element = json.parseToJsonElement(jsonSubstr)
                    if (element is JsonArray) {
                        element.map {
                            if (it is JsonPrimitive) it.content else it.toString()
                        }
                    } else {
                        null
                    }
                } catch (_: Throwable) {
                    try {
                        val jsonArray = JSONArray(jsonSubstr)
                        val result = mutableListOf<String>()
                        for (i in 0 until jsonArray.length()) {
                            result.add(jsonArray.optString(i, ""))
                        }
                        result
                    } catch (_: Throwable) {
                        null
                    }
                }
                if (jsonResult != null) {
                    if (expectedCount == null || jsonResult.size == expectedCount || jsonResult.isNotEmpty()) {
                        return jsonResult
                    }
                }
            }

            if (expectedCount != null) {
                val numbered = parseNumberedLinesResponse(cleaned, expectedCount)
                if (numbered != null) return numbered
            }

            return null
        }
    }
}

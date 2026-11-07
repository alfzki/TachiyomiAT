package eu.kanade.translation.translator

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.delay
import logcat.LogPriority
import logcat.logcat
import org.json.JSONObject

@Suppress
class GeminiTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    private val apiKey: String,
    modelName: String,
    val maxOutputToken: Int,
    val temp: Float,
) : TextTranslator {

    private val actualModelName = modelName.ifEmpty { "gemini-3.5-flash-lite" }

    private var model: GenerativeModel = GenerativeModel(
        modelName = actualModelName,
        apiKey = apiKey,
        generationConfig = generationConfig {
            topK = 30
            topP = 0.5f
            temperature = temp
            maxOutputTokens = maxOutputToken
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE),
        ),
        systemInstruction = content {
            text(TranslationPrompts.forLanguagePair(fromLang, toLang))
        },
    )

    override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
        try {
            logcat { "GeminiTranslator: Starting translation from ${fromLang.label} to ${toLang.label}" }
            logcat { "GeminiTranslator: Using model $actualModelName with temp=$temp, maxTokens=$maxOutputToken" }

            // Validate API key
            if (apiKey.isBlank()) {
                throw IllegalStateException(
                    "Gemini API key is empty. Please add your API key in Settings > Translation > Engine API Key",
                )
            }

            val data = pages.mapValues { (_, v) -> v.blocks.map { b -> b.text } }
            val json = JSONObject(data)

            logcat {
                "GeminiTranslator: Sending ${pages.size} pages with ${data.values.sumOf { it.size }} blocks to API"
            }
            logcat(LogPriority.DEBUG) { "GeminiTranslator: Request JSON: ${json.toString().take(200)}..." }

            // Retry logic with exponential backoff for transient failures
            val maxRetries = 3
            var lastException: Exception? = null
            var response: com.google.ai.client.generativeai.type.GenerateContentResponse? = null

            for (attempt in 1..maxRetries) {
                try {
                    logcat { "GeminiTranslator: API call attempt $attempt/$maxRetries" }
                    response = model.generateContent(json.toString())
                    break // Success, exit retry loop
                } catch (e: Exception) {
                    lastException = e
                    val errorMessage = e.message ?: "Unknown error"

                    // Check if error is retryable (empty response, EOF, server errors)
                    val isRetryable = errorMessage.contains("EOF") ||
                        errorMessage.contains("empty") ||
                        errorMessage.contains("ServerException") ||
                        errorMessage.contains("timeout", ignoreCase = true) ||
                        errorMessage.contains("connection", ignoreCase = true)

                    logcat(LogPriority.WARN) {
                        "GeminiTranslator: Attempt $attempt/$maxRetries failed: $errorMessage" +
                            (if (isRetryable) " (retryable)" else " (non-retryable)")
                    }

                    if (!isRetryable || attempt == maxRetries) {
                        // Non-retryable error or last attempt - throw immediately
                        throw e
                    }

                    // Exponential backoff: 1s, 2s, 3s
                    val delayMs = attempt * 1000L
                    logcat { "GeminiTranslator: Retrying after ${delayMs}ms delay..." }
                    delay(delayMs)
                }
            }

            if (response == null) {
                throw lastException ?: Exception("Translation failed after $maxRetries attempts with unknown error")
            }

            logcat { "GeminiTranslator: Received response from Gemini API" }

            // Strip markdown code blocks if present (Gemini sometimes wraps JSON in ```json ... ```)
            val responseText = response.text?.let { stripMarkdownCodeBlock(it) }

            // Enhanced response validation to prevent JSON EOF errors
            when {
                responseText == null -> {
                    val candidatesCount = response.candidates.size
                    val finishReason = response.candidates.firstOrNull()?.finishReason?.toString() ?: "unknown"
                    val promptFeedback = response.promptFeedback?.toString() ?: "none"

                    logcat(LogPriority.ERROR) {
                        "GeminiTranslator: Response text is NULL\n" +
                            "Candidates: $candidatesCount\n" +
                            "FinishReason: $finishReason\n" +
                            "PromptFeedback: $promptFeedback"
                    }
                    throw Exception(
                        "Gemini API returned null response. " +
                            "This may indicate content filtering (FinishReason: $finishReason), " +
                            "API quota limits, or model errors. " +
                            "Check your API key and quota at https://makersuite.google.com",
                    )
                }

                responseText.isBlank() -> {
                    val finishReason = response.candidates.firstOrNull()?.finishReason?.toString() ?: "unknown"
                    val safetyRatings = response.candidates.firstOrNull()?.safetyRatings?.joinToString {
                        "${it.category}: ${it.probability}"
                    } ?: "none"

                    logcat(LogPriority.ERROR) {
                        "GeminiTranslator: Response text is EMPTY\n" +
                            "FinishReason: $finishReason\n" +
                            "SafetyRatings: $safetyRatings"
                    }
                    throw Exception(
                        "Gemini API returned empty response. " +
                            "FinishReason: $finishReason. " +
                            "This may indicate safety filtering, quota exceeded, or model timeout. " +
                            "If using gemini-3.5-flash-lite, try removing responseMimeType or use a different model.",
                    )
                }

                !responseText.trim().startsWith("{") -> {
                    logcat(LogPriority.ERROR) {
                        "GeminiTranslator: Response is not valid JSON\n" +
                            "First 200 chars: ${responseText.take(200)}"
                    }
                    throw Exception(
                        "Gemini API did not return JSON. " +
                            "Response starts with: '${responseText.take(50)}...'. " +
                            "The model may not be following the system prompt correctly.",
                    )
                }
            }

            logcat(LogPriority.DEBUG) { "GeminiTranslator: Response text: ${responseText.take(200)}..." }

            val resJson = try {
                JSONObject(responseText)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "GeminiTranslator: Failed to parse response as JSON: $responseText" }
                throw Exception("Gemini API returned invalid JSON. Response: ${responseText.take(500)}", e)
            }
            var totalBlocks = 0
            var translatedBlocks = 0
            var removedWatermarks = 0

            for ((k, v) in pages) {
                // Set translator type for rendering optimizations
                v.translatorType = "gemini"

                totalBlocks += v.blocks.size
                v.blocks.forEachIndexed { i, b ->
                    val res = resJson.optJSONArray(k)?.optString(i, "NULL")
                    b.translation = if (res == null || res == "NULL") b.text else res
                    if (res != null && res != "NULL") translatedBlocks++
                }
                val originalSize = v.blocks.size
                v.blocks =
                    v.blocks.filterNot { it.translation.contains("RTMTH") }.toMutableList()
                removedWatermarks += (originalSize - v.blocks.size)
            }

            logcat {
                "GeminiTranslator: Translation completed - $translatedBlocks/$totalBlocks blocks, " +
                    "removed $removedWatermarks watermark blocks"
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "GeminiTranslator: Translation error: ${e.message}\n${e.stackTraceToString()}"
            }
            throw e
        }
    }

    override fun close() {
    }

    companion object {
        /**
         * Strips markdown code block formatting from a string.
         * Gemini sometimes wraps JSON responses in ```json ... ``` blocks.
         */
        internal fun stripMarkdownCodeBlock(text: String): String {
            val trimmed = text.trim()

            // Check for ```json or ``` at the start
            val codeBlockPattern = Regex("""^```(?:json)?\s*\n?([\s\S]*?)\n?```$""")
            val match = codeBlockPattern.find(trimmed)
            if (match != null) {
                logcat { "GeminiTranslator: Stripped markdown code block from response" }
                return match.groupValues[1].trim()
            }

            // Also handle case where only opening ``` is present (incomplete response)
            if (trimmed.startsWith("```json") || trimmed.startsWith("```")) {
                val startIndex = trimmed.indexOf('\n')
                if (startIndex != -1) {
                    val withoutOpening = trimmed.substring(startIndex + 1)
                    val endIndex = withoutOpening.lastIndexOf("```")
                    val result = if (endIndex != -1) {
                        withoutOpening.substring(0, endIndex)
                    } else {
                        withoutOpening
                    }
                    logcat { "GeminiTranslator: Stripped partial markdown code block from response" }
                    return result.trim()
                }
            }

            return trimmed
        }
    }
}

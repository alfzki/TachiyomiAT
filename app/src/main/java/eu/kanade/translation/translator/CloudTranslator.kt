package eu.kanade.translation.translator

import android.text.Html
import com.google.cloud.translate.Translate
import com.google.cloud.translate.TranslateOptions
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat

/**
 * Google Cloud Translation API translator with BYOK (Bring Your Own Key) model.
 *
 * Requires a valid Google Cloud API key with Translation API enabled.
 * Users must enable billing on their Google Cloud account.
 *
 * API Documentation: https://cloud.google.com/translate/docs
 */
class CloudTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    private val apiKey: String,
    private val detectionMethod: String = "mlkit",
) : TextTranslator {

    private var translateService: Translate? = null

    /**
     * Lazily initialize the translation service on the IO thread.
     * This prevents NetworkOnMainThreadException during object construction.
     */
    private suspend fun initializeServiceIfNeeded() = withContext(Dispatchers.IO) {
        if (translateService == null) {
            if (apiKey.isBlank()) {
                val errorMsg = "CloudTranslator: API key is empty"
                logcat(LogPriority.WARN) { errorMsg }
                throw IllegalStateException(
                    "Google Cloud Translation API key is not configured. " +
                        "Please add your API key in Settings > Translation > Engine API Key.",
                )
            }

            try {
                translateService = TranslateOptions.newBuilder()
                    .setApiKey(apiKey)
                    .build()
                    .service
                logcat { "CloudTranslator initialized successfully" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Failed to initialize CloudTranslator: ${e.message}\n${e.stackTraceToString()}"
                }
                throw IllegalStateException(
                    "Failed to initialize Google Cloud Translation API: ${e.message}. " +
                        "Please verify your API key and ensure the Translation API is enabled " +
                        "in Google Cloud Console with billing enabled.",
                    e,
                )
            }
        }
    }

    override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
        // Initialize service if needed (on IO thread)
        initializeServiceIfNeeded()

        try {
            // Convert to proper ISO 639-1 codes for Google Cloud API
            val sourceCode = fromLang.toCloudApiCode()
            val targetCode = toLang.toCloudApiCode()

            logcat { "Starting Cloud Translation: ${pages.size} pages" }
            logcat { "Languages: ${fromLang.label} ($sourceCode) -> ${toLang.label} ($targetCode)" }
            logcat {
                val modeDesc = if (fromLang == TextRecognizerLanguage.AUTO) {
                    "(active for AUTO mode)"
                } else {
                    "(not used - explicit language selected)"
                }
                "Detection Method: $detectionMethod $modeDesc"
            }

            withContext(Dispatchers.IO) {
                var totalBlocks = 0
                var translatedBlocks = 0
                var failedBlocks = 0

                for ((pageKey, pageTranslation) in pages) {
                    try {
                        // Set translator type for rendering optimizations
                        pageTranslation.translatorType = "cloud"

                        val blocks = pageTranslation.blocks
                        totalBlocks += blocks.size

                        // Determine actual source language for this page
                        // Detection method is configurable via user preference
                        val actualSourceCode = if (fromLang == TextRecognizerLanguage.AUTO) {
                            when (detectionMethod) {
                                "mlkit" -> {
                                    // Use MLKit's OCR-based language detection (more accurate for visual text)
                                    if (pageTranslation.sourceLanguage != "auto") {
                                        logcat {
                                            "AUTO mode (MLKit): Using MLKit detected language " +
                                                "'${pageTranslation.sourceLanguage}' for page $pageKey"
                                        }
                                        pageTranslation.sourceLanguage
                                    } else {
                                        logcat {
                                            "AUTO mode (MLKit): No language detected by MLKit, " +
                                                "falling back to Google Cloud detection for page $pageKey"
                                        }
                                        null // Let Google Cloud API detect as fallback
                                    }
                                }
                                "google_cloud" -> {
                                    // Use Google Cloud's auto-detection (better for Indonesian/Spanish, etc.)
                                    logcat {
                                        "AUTO mode (Google Cloud): Using Google Cloud auto-detection for page $pageKey"
                                    }
                                    null // Let Google Cloud API detect
                                }
                                else -> {
                                    // Unknown method, fall back to MLKit
                                    logcat(LogPriority.WARN) {
                                        "Unknown detection method '$detectionMethod', falling back to MLKit"
                                    }
                                    if (pageTranslation.sourceLanguage != "auto") {
                                        pageTranslation.sourceLanguage
                                    } else {
                                        null
                                    }
                                }
                            }
                        } else {
                            sourceCode // Use explicitly selected language
                        }

                        logcat {
                            "Translating page $pageKey: ${blocks.size} blocks, " +
                                "source: ${actualSourceCode ?: "auto-detect"}"
                        }

                        // Translate each block
                        for (block in blocks) {
                            try {
                                if (block.text.isNotBlank()) {
                                    // Build translation options with proper ISO 639-1 codes
                                    val options = mutableListOf<Translate.TranslateOption>(
                                        Translate.TranslateOption.targetLanguage(targetCode),
                                    )

                                    // Include source language if we have one (explicit or detected)
                                    if (actualSourceCode != null) {
                                        options.add(Translate.TranslateOption.sourceLanguage(actualSourceCode))
                                    }

                                    logcat {
                                        "CloudTranslator API call: source=${actualSourceCode ?: "auto"}, " +
                                            "target=$targetCode, text='${block.text.take(50)}...'"
                                    }

                                    val translation = translateService!!.translate(
                                        block.text,
                                        *options.toTypedArray(),
                                    )

                                    // Log API response details
                                    logcat {
                                        "CloudTranslator API response: " +
                                            "translatedText='${translation.translatedText?.take(50) ?: "null"}', " +
                                            "detectedSourceLang=${translation.sourceLanguage}"
                                    }

                                    // Decode HTML entities (&#39; -> ', &quot; -> ", etc.)
                                    val translatedText = translation.translatedText ?: block.text
                                    val decodedText = decodeHtmlEntities(translatedText)

                                    // Apply Korean-specific post-processing if source is Korean
                                    block.translation = if (
                                        isKoreanSource(actualSourceCode, pageTranslation.sourceLanguage)
                                    ) {
                                        logcat { "Applying Korean post-processing to: '$decodedText'" }
                                        postProcessKoreanTranslation(decodedText)
                                    } else {
                                        decodedText
                                    }
                                    translatedBlocks++

                                    // Log detected source language if Google Cloud did auto-detection
                                    val detectedLang = translation.sourceLanguage
                                    if (actualSourceCode == null && detectedLang != null) {
                                        logcat {
                                            "Google Cloud detected source language: $detectedLang " +
                                                "for block in page $pageKey"
                                        }
                                    }

                                    logcat {
                                        "Translated block: '${block.text.take(30)}...' -> " +
                                            "'${block.translation.take(30)}...'"
                                    }
                                } else {
                                    block.translation = ""
                                    logcat { "Skipped empty block" }
                                }
                            } catch (e: Exception) {
                                block.translation = block.text // Fallback to original text
                                failedBlocks++
                                logcat(LogPriority.ERROR) {
                                    "Failed to translate block in page $pageKey: ${e.message}\n" +
                                        e.stackTraceToString()
                                }
                            }
                        }

                        // Remove watermark blocks (blocks containing "RTMTH" or actual URL watermarks)
                        val originalSize = blocks.size
                        pageTranslation.blocks = blocks.filterNot { block ->
                            val text = block.translation
                            // Only filter RTMTH marker (set by AI translators for watermarks)
                            text.contains("RTMTH", ignoreCase = true) ||
                                // Only filter actual URLs/domains (not text that happens to contain ".com")
                                // Must be standalone URL-like pattern: http://, www., or isolated domain
                                text.matches(
                                    Regex(
                                        """^(https?://|www\.)?[\w.-]+\.(com|org|net|io)(/\S*)?$""",
                                        RegexOption.IGNORE_CASE,
                                    ),
                                ) ||
                                text.matches(Regex("""^\s*@?\w+\.(com|org|net|io)\s*$""", RegexOption.IGNORE_CASE))
                        }.toMutableList()

                        val removedWatermarks = originalSize - pageTranslation.blocks.size
                        if (removedWatermarks > 0) {
                            logcat { "CloudTranslator: Removed $removedWatermarks watermark blocks from page $pageKey" }
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR) {
                            "Error translating page $pageKey: ${e.message}\n${e.stackTraceToString()}"
                        }
                        throw e
                    }
                }

                logcat {
                    "Cloud Translation completed: $translatedBlocks/$totalBlocks blocks successful, " +
                        "$failedBlocks failed"
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "Cloud Translation Error: ${e.message}\n${e.stackTraceToString()}"
            }
            throw e
        }
    }

    override fun close() {
        translateService = null
        logcat { "CloudTranslator closed" }
    }

    /**
     * Decode HTML entities in translated text.
     * Converts &#39; -> ', &quot; -> ", &amp; -> &, etc.
     */
    private fun decodeHtmlEntities(text: String): String {
        return try {
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
        } catch (e: Exception) {
            logcat(LogPriority.WARN) {
                "Failed to decode HTML entities: ${e.message}"
            }
            text // Fallback to original text
        }
    }

    /**
     * Check if source language is Korean
     */
    private fun isKoreanSource(sourceCode: String?, detectedLanguage: String): Boolean {
        val isKorean = sourceCode == "ko" ||
            detectedLanguage.lowercase() == "ko" ||
            detectedLanguage.lowercase().contains("korean")
        logcat { "isKoreanSource check: sourceCode=$sourceCode, detectedLanguage=$detectedLanguage, result=$isKorean" }
        return isKorean
    }

    /**
     * Post-process Korean-to-English translations to fix common issues
     * Google Cloud Translation sometimes over-translates or mistranslates Korean particles
     */
    private fun postProcessKoreanTranslation(text: String): String {
        var result = text

        // Fix common over-translations of Korean particles
        // Google Cloud sometimes translates particles that should be omitted in English
        result = result.replace(Regex("\\s+of\\s+the\\s+"), " ") // Redundant particles
        result = result.replace(Regex("\\s{2,}"), " ") // Multiple spaces to single space

        // Case-insensitive replacement of mistranslations back to Korean terms
        // NOTE: This is a simplified approach - context-aware replacement would require NLP
        result = result.replace(Regex("\\b(older brother)\\b", RegexOption.IGNORE_CASE)) { "hyung" }
        result = result.replace(Regex("\\b(older sister)\\b", RegexOption.IGNORE_CASE)) { "unni" }
        result = result.replace(Regex("\\b(senior|upperclassman)\\b", RegexOption.IGNORE_CASE)) { "sunbae" }
        result = result.replace(Regex("\\b(junior|underclassman)\\b", RegexOption.IGNORE_CASE)) { "hubae" }
        result = result.replace(Regex("\\b(rice wine)\\b", RegexOption.IGNORE_CASE)) { "soju" }
        result = result.replace(Regex("\\b(pork belly)\\b", RegexOption.IGNORE_CASE)) { "samgyeopsal" }
        result = result.replace(Regex("\\b(internet cafe|PC room|PC cafe)\\b", RegexOption.IGNORE_CASE)) { "PC bang" }
        result = result.replace(Regex("\\b(karaoke room)\\b", RegexOption.IGNORE_CASE)) { "noraebang" }

        // Trim excess whitespace
        result = result.trim()

        logcat { "Korean post-processing result: '$result'" }

        return result
    }
}

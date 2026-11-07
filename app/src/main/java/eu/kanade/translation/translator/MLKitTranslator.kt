package eu.kanade.translation.translator

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.util.ComicTextPreprocessor

class MLKitTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
) : TextTranslator {

    private var translator = Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(fromLang.code)
            .setTargetLanguage(TranslateLanguage.fromLanguageTag(toLang.code) ?: TranslateLanguage.ENGLISH)
            .build(),
    )

    private var conditions = DownloadConditions.Builder().build()

    override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
        Tasks.await(translator.downloadModelIfNeeded(conditions))

        for ((_, page) in pages) {
            val blocks = page.blocks
            var i = 0
            while (i < blocks.size) {
                val current = blocks[i]
                val currentTrimmed = current.text.trim()

                // Check if current block ends without terminal punctuation and can be paired with next block
                val canPair = i + 1 < blocks.size &&
                    currentTrimmed.isNotEmpty() &&
                    !ComicTextPreprocessor.isTerminalPunctuation(currentTrimmed.lastOrNull())

                var paired = false
                if (canPair) {
                    val next = blocks[i + 1]
                    val verticalGap = next.y - (current.y + current.height)
                    if (verticalGap >= -50f && verticalGap < 350f) {
                        paired = true
                        val delimiter = " -- "
                        val textA = ComicTextPreprocessor.preprocessOcrBlockText(current.text)
                        val textB = ComicTextPreprocessor.preprocessOcrBlockText(next.text)
                        val combined = "$textA$delimiter$textB"
                        val prepared = ComicTextPreprocessor.prepareForNmt(combined, fromLang)

                        val rawTranslated = Tasks.await(translator.translate(prepared))
                        val postProcessed = ComicTextPreprocessor.postProcessNmtOutput(
                            sourceText = combined,
                            translatedText = rawTranslated,
                            toLang = toLang,
                        )

                        val (transA, transB) = ComicTextPreprocessor.splitJoinedTranslation(
                            joinedTranslation = postProcessed,
                            lenA = textA.length,
                            lenB = textB.length,
                            delimiter = delimiter,
                        )
                        current.translation = transA
                        next.translation = transB
                        i += 2
                    }
                }

                if (!paired) {
                    val cleanText = ComicTextPreprocessor.preprocessOcrBlockText(current.text)
                    val prepared = ComicTextPreprocessor.prepareForNmt(cleanText, fromLang)
                    val rawTranslated = Tasks.await(translator.translate(prepared))
                    val postProcessed = ComicTextPreprocessor.postProcessNmtOutput(
                        sourceText = cleanText,
                        translatedText = rawTranslated,
                        toLang = toLang,
                    )
                    current.translation = postProcessed
                    i += 1
                }
            }

            // Filter out blocks marked as "RTMTH" or blank
            page.blocks = page.blocks.filterNot {
                it.translation.isBlank() || it.translation.contains("RTMTH")
            }.toMutableList()
        }
    }

    override fun close() {
        translator.close()
    }
}

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
            for (block in page.blocks) {
                val cleanText = ComicTextPreprocessor.preprocessOcrBlockText(block.text)
                if (cleanText.isBlank()) continue
                val prepared = ComicTextPreprocessor.prepareForNmt(cleanText, fromLang)
                val rawTranslated = Tasks.await(translator.translate(prepared))
                val postProcessed = ComicTextPreprocessor.postProcessNmtOutput(
                    sourceText = cleanText,
                    translatedText = rawTranslated,
                    toLang = toLang,
                )
                block.translation = postProcessed
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

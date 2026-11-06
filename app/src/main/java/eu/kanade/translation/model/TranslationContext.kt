package eu.kanade.translation.model

import android.graphics.Bitmap

data class TranslationContext(
    val mangaTitle: String = "",
    val chapterTitle: String = "",
    val genres: List<String> = emptyList(),
    val synopsis: String = "",
    val pageBitmapLoader: (suspend (pageKey: String) -> Bitmap?)? = null,
)

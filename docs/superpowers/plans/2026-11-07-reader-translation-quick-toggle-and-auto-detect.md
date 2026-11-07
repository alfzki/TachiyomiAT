# Reader Translation Quick Toggle & Auto Language Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menambahkan tombol quick toggle visibilitas terjemahan pada reader top app bar (di samping bookmark) dan menambahkan opsi "Auto Detect" pada pengaturan "Translate From" yang secara otomatis mendeteksi apakah bahasa/skrip komik merupakan Latin, Jepang, China, atau Korea.

**Architecture:**
- **Reader Quick Toggle:** Mengintegrasikan `readerPreferences.showTranslations` ke dalam `ReaderTopBar` sebagai `AppBar.Action` dengan icon translate Material Symbols (berubah warna/status saat aktif vs nonaktif) untuk memicu `readerPreferences.showTranslations.toggle()`, yang langsung ditanggapi oleh `PagerPageHolder` dan `WebtoonPageHolder`.
- **Auto Script & Language Detection:** Menambahkan varian `AUTO` pada `TextRecognizerLanguage` serta membuat modul pendeteksi `ComicLanguageDetector` yang mengevaluasi skrip teks (Unicode Hangul untuk Korea, Hiragana/Katakana untuk Jepang, Hanzi murni untuk China, dan alfabet Latin untuk Latin/English) baik dari halaman sampel komik (OCR probing) maupun fallback metadata (source.lang & title), dengan cache per-manga agar deteksi hanya terjadi sekali per serial.
- **Translator Engine Integration:** Memperbarui `ChapterTranslator` agar saat `fromLang == AUTO`, bahasa komik di-resolve secara dinamis menjadi bahasa konkret sebelum eksekusi recognizer dan translasi (AICore, MLKit, Gemini, dsb.).

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, AndroidX, Google MLKit (Text Recognition: Latin, Chinese, Japanese, Korean), Kotlinx Coroutines, Moko-resources.

**Spec:** User prompt requesting: (1) quick toggle blok terjemahan di toolbar reader (area marker merah), (2) pembaruan fitur "Translate From" dengan deteksi bahasa otomatis untuk teks latin, jepang, china, atau korea.

## Global Constraints
- Target platform: Android, JVM 17.
- Zero host mutation: Jangan mengubah konfigurasi sistem operasi atau paket global.
- Dependencies: Gunakan library MLKit yang sudah terpasang di `gradle/libs.versions.toml` (`text-recognition`, `text-recognition-chinese`, `text-recognition-japanese`, `text-recognition-korean`).
- UI consistency: Gunakan Material Design 3 dan ikuti konvensi Mihon `AppBar.Action` serta Compose components yang ada.

## Review Focus
1. Komik dengan bahasa campuran (misalnya manga Jepang dengan sound effect kata Latin seperti "BOOM" atau "CRASH"): Pendeteksi harus memprioritaskan Kana/Kanji dan tidak salah mendeteksi sebagai Latin.
2. Manhua China yang menggunakan karakter Hanzi tanpa Hiragana/Katakana: Pendeteksi harus membedakan secara tegas antara China (Hanzi murni) dan Jepang (Kanji + Hiragana/Katakana).
3. Halaman pembuka komik yang tidak memiliki teks dialog (hanya gambar cover/art): Pendeteksi harus memeriksa halaman berikutnya (misal page 2/3) atau fallback ke judul manga/source metadata tanpa crash atau hang.
4. Komik lokal (LocalSource / download offline) di mana `source.lang` bernilai kosong atau "other": Deteksi skrip via OCR halaman sampel harus bekerja mandiri tanpa bergantung pada internet atau metadata sumber daring.
5. Performa: Deteksi bahasa tidak boleh mengulang OCR di setiap halaman; hasil deteksi harus di-cache per-manga/chapter sehingga tidak membebani memori dan proses translasi.

---

### Task 1: String Resources & Material Symbols Translate Icon

**Files:**
- Create: `icons/material-symbols/src/main/valkyrieResources/rounded/translate.svg`
- Modify: `i18n-at/src/commonMain/moko-resources/base/strings.xml`

**Interfaces:**
- Produces:
  - `mihon.icons.materialsymbols.rounded.Translate: ImageVector`
  - `ATMR.strings.action_show_translations`
  - `ATMR.strings.action_hide_translations`
  - `ATMR.strings.pref_translate_from_auto`

- [ ] **Step 1: Tambahkan string resources baru di `i18n-at`**

Edit `i18n-at/src/commonMain/moko-resources/base/strings.xml` dan tambahkan:
```xml
    <string name="action_show_translations">Show translations</string>
    <string name="action_hide_translations">Hide translations</string>
    <string name="pref_translate_from_auto">Auto Detect</string>
```

- [ ] **Step 2: Tambahkan vector icon `translate.svg` ke `icons:material-symbols`**

Buat file `icons/material-symbols/src/main/valkyrieResources/rounded/translate.svg` dengan format Material Symbols Rounded 24dp (viewBox `0 -960 960 960`).

- [ ] **Step 3: Jalankan build untuk memvalidasi pembuatan kode ImageVector & string resources**

Run: `./gradlew :icons:material-symbols:compileDebugKotlin :i18n-at:generateMRandroidMain`
Expected: BUILD SUCCESSFUL dan `MaterialSymbols.Rounded.Translate` tersedia.

- [ ] **Step 4: Commit perubahan icon dan string resources**

```bash
git add icons/material-symbols/src/main/valkyrieResources/rounded/translate.svg i18n-at/src/commonMain/moko-resources/base/strings.xml
git commit -m "feat(reader): add translate rounded icon and translation string resources"
```

---

### Task 2: Reader Quick Toggle Blok Terjemahan di Top Bar

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderTopBar.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:450-480`

**Interfaces:**
- Consumes:
  - `readerPreferences.showTranslations: Preference<Boolean>`
  - `MaterialSymbols.Rounded.Translate: ImageVector`
  - `ATMR.strings.action_show_translations`, `ATMR.strings.action_hide_translations`
- Produces:
  - Quick toggle button di samping Bookmark icon pada `ReaderTopBar`

- [ ] **Step 1: Perbarui parameter dan action di `ReaderTopBar.kt`**

Tambahkan parameter `showTranslations: Boolean` dan `onToggleShowTranslations: () -> Unit` ke `ReaderTopBar`.
Di dalam `AppBarActions`, tambahkan `AppBar.Action` sebelum bookmark action:
```kotlin
AppBar.Action(
    title = stringResource(
        if (showTranslations) {
            ATMR.strings.action_hide_translations
        } else {
            ATMR.strings.action_show_translations
        },
    ),
    icon = MaterialSymbols.Rounded.Translate,
    iconTint = if (showTranslations) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    },
    onClick = onToggleShowTranslations,
),
```

- [ ] **Step 2: Teruskan state dan callback di `ReaderAppBars.kt`**

Tambahkan parameter `showTranslations: Boolean` dan `onToggleShowTranslations: () -> Unit` ke fungsi `ReaderAppBars`, lalu teruskan ke pemanggilan `ReaderTopBar`.

- [ ] **Step 3: Hubungkan state dari `ReaderActivity.kt`**

Di dalam `ReaderActivity.AppBars(state: ReaderViewModel.State)`:
Collect `showTranslations`:
```kotlin
val showTranslations by readerPreferences.showTranslations.collectAsState()
```
Dan teruskan ke `ReaderAppBars`:
```kotlin
showTranslations = showTranslations,
onToggleShowTranslations = {
    readerPreferences.showTranslations.toggle()
},
```

- [ ] **Step 4: Verifikasi kompilasi dan integrasi**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL tanpa error.

- [ ] **Step 5: Commit perubahan quick toggle reader**

```bash
git add app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderTopBar.kt app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt
git commit -m "feat(reader): add translation visibility quick toggle to reader top bar"
```

---

### Task 3: TextRecognizerLanguage Auto Enum & UI Setting Update

**Files:**
- Modify: `app/src/main/java/eu/kanade/translation/recognizer/TextRecognizerLanguage.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTranslationScreen.kt:47-67`
- Test: `app/src/test/java/eu/kanade/translation/recognizer/TextRecognizerLanguageTest.kt`

**Interfaces:**
- Produces:
  - `TextRecognizerLanguage.AUTO`
  - `TextRecognizerLanguage.fromPref(pref: Preference<String>): TextRecognizerLanguage` yang mendukung `AUTO`

- [ ] **Step 1: Tulis unit test untuk `TextRecognizerLanguage`**

Buat `app/src/test/java/eu/kanade/translation/recognizer/TextRecognizerLanguageTest.kt`:
```kotlin
package eu.kanade.translation.recognizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextRecognizerLanguageTest {
    @Test
    fun `AUTO language entry exists and has correct label`() {
        assertEquals("Auto Detect", TextRecognizerLanguage.AUTO.label)
        assertEquals("", TextRecognizerLanguage.AUTO.code)
    }
}
```

- [ ] **Step 2: Jalankan test untuk memastikan test gagal (karena AUTO belum ada)**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.translation.recognizer.TextRecognizerLanguageTest"`
Expected: FAIL dengan unresolved reference `AUTO`.

- [ ] **Step 3: Tambahkan `AUTO` ke `TextRecognizerLanguage.kt`**

Perbarui enum `TextRecognizerLanguage`:
```kotlin
enum class TextRecognizerLanguage(var code: String, val label: String) {
    AUTO("", "Auto Detect"),
    CHINESE(TranslateLanguage.CHINESE, "Chinese (trad/sim)"),
    JAPANESE(TranslateLanguage.JAPANESE, "Japanese"),
    KOREAN(TranslateLanguage.KOREAN, "Korean"),
    ENGLISH(TranslateLanguage.ENGLISH, "English"),
    ;

    companion object {
        fun fromPref(pref: Preference<String>): TextRecognizerLanguage {
            val name = pref.get()
            var lang = entries.firstOrNull { it.name.equals(name, true) }
            if (lang == null) {
                pref.set(AUTO.name)
                return AUTO
            }
            return lang
        }
    }
}
```
Di `SettingsTranslationScreen.kt`, karena opsi `fromLangs` mengambil `TextRecognizerLanguage.entries`, pilihan `Auto Detect` akan secara otomatis muncul di urutan pertama pada dialog "Translate From".

- [ ] **Step 4: Jalankan test untuk memverifikasi test lolos**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.translation.recognizer.TextRecognizerLanguageTest"`
Expected: PASS.

- [ ] **Step 5: Commit perubahan TextRecognizerLanguage**

```bash
git add app/src/main/java/eu/kanade/translation/recognizer/TextRecognizerLanguage.kt app/src/test/java/eu/kanade/translation/recognizer/TextRecognizerLanguageTest.kt
git commit -m "feat(translation): add AUTO entry to TextRecognizerLanguage"
```

---

### Task 4: Deteksi Bahasa Komik Otomatis (ComicLanguageDetector)

**Files:**
- Create: `app/src/main/java/eu/kanade/translation/detector/ComicLanguageDetector.kt`
- Create: `app/src/test/java/eu/kanade/translation/detector/ComicLanguageDetectorTest.kt`

**Interfaces:**
- Consumes:
  - `TextRecognizerLanguage`
  - String teks OCR atau metadata judul komik/source language
- Produces:
  - `ComicLanguageDetector.classifyScript(text: String): TextRecognizerLanguage?`
  - `ComicLanguageDetector.detectFromMetadata(sourceLang: String?, title: String?, description: String?): TextRecognizerLanguage?`
  - `ComicLanguageDetector.detectLanguage(source: Source?, manga: Manga, sampleTextProvider: suspend () -> String?): TextRecognizerLanguage`

- [ ] **Step 1: Tulis unit test untuk `ComicLanguageDetectorTest`**

Buat `app/src/test/java/eu/kanade/translation/detector/ComicLanguageDetectorTest.kt` yang menguji:
1. Klasifikasi teks Hangul -> `KOREAN`
2. Klasifikasi teks dengan Hiragana/Katakana -> `JAPANESE`
3. Klasifikasi teks Kanji/Hanzi tanpa Kana -> `CHINESE`
4. Klasifikasi teks Latin (alfabet A-Z, percakapan English/Indo) -> `ENGLISH`
5. Teks Jepang dengan efek suara Latin ("BOOM! なんだこれは？") -> tetap terdeteksi `JAPANESE`
6. Deteksi fallback dari metadata (`source.lang` "ja" / "ko" / "zh" / "en") dan judul komik.

- [ ] **Step 2: Jalankan test untuk memastikan test gagal**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.translation.detector.ComicLanguageDetectorTest"`
Expected: FAIL (class `ComicLanguageDetector` belum ada).

- [ ] **Step 3: Implementasikan `ComicLanguageDetector`**

Buat `app/src/main/java/eu/kanade/translation/detector/ComicLanguageDetector.kt`:
- Logika klasifikasi karakter berbasis rentang Unicode:
  - `isHangul(c)`: `c in '\uAC00'..'\uD7AF' || c in '\u1100'..'\u11FF' || c in '\u3130'..'\u318F'`
  - `isKana(c)`: `c in '\u3040'..'\u309F' || c in '\u30A0'..'\u30FF' || c in '\u31F0'..'\u31FF'`
  - `isCjkUnified(c)`: `c in '\u4E00'..'\u9FFF' || c in '\u3400'..'\u4DBF' || c in '\uF900'..'\uFAFF'`
  - `isLatin(c)`: `c in 'a'..'z' || c in 'A'..'Z' || c in '\u00C0'..'\u024F'`
- Aturan inferensi teks:
  - Jika `hangulCount > 0 && hangulCount >= kanaCount`: `KOREAN`
  - Jika `kanaCount >= 2` atau (`kanaCount > 0 && kanaCount + cjkCount > latinCount`): `JAPANESE`
  - Jika `cjkCount > 0 && kanaCount == 0 && hangulCount == 0 && cjkCount >= latinCount`: `CHINESE`
  - Jika `latinCount > totalRelevant * 0.5f`: `ENGLISH`
- Aturan fallback metadata & caching hasil per `manga.id` via `ConcurrentHashMap<Long, TextRecognizerLanguage>`.

- [ ] **Step 4: Jalankan test untuk memverifikasi test lolos**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.translation.detector.ComicLanguageDetectorTest"`
Expected: PASS seluruh kasus uji.

- [ ] **Step 5: Commit implementasi ComicLanguageDetector**

```bash
git add app/src/main/java/eu/kanade/translation/detector/ComicLanguageDetector.kt app/src/test/java/eu/kanade/translation/detector/ComicLanguageDetectorTest.kt
git commit -m "feat(translation): add ComicLanguageDetector for automatic script and language detection"
```

---

### Task 5: Integrasi Deteksi Bahasa Otomatis di ChapterTranslator

**Files:**
- Modify: `app/src/main/java/eu/kanade/translation/recognizer/TextRecognizer.kt:13-25`
- Modify: `app/src/main/java/eu/kanade/translation/ChapterTranslator.kt`

**Interfaces:**
- Consumes:
  - `ComicLanguageDetector`
  - `TextRecognizerLanguage.AUTO`
- Produces:
  - Resolusi otomatis bahasa sumber komik saat antrean translasi dijalankan

- [ ] **Step 1: Tangani fallback safe di `TextRecognizer.kt`**

Di `TextRecognizer.kt`, jika `language == TextRecognizerLanguage.AUTO`, fallback default ke `TextRecognizerOptions.DEFAULT_OPTIONS` agar tidak melempar pengecualian:
```kotlin
when (language) {
    TextRecognizerLanguage.ENGLISH, TextRecognizerLanguage.AUTO -> TextRecognizerOptions.DEFAULT_OPTIONS
    TextRecognizerLanguage.CHINESE -> ChineseTextRecognizerOptions.Builder().build()
    TextRecognizerLanguage.JAPANESE -> JapaneseTextRecognizerOptions.Builder().build()
    TextRecognizerLanguage.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
}
```

- [ ] **Step 2: Sambungkan `ComicLanguageDetector` di `ChapterTranslator.kt`**

Di `ChapterTranslator.kt`:
1. Sediakan instance `private val languageDetector = ComicLanguageDetector()`
2. Di dalam `translateChapter(translation: Translation)`:
   - Jika `translation.fromLang == TextRecognizerLanguage.AUTO`:
     Jalankan deteksi resolusi bahasa:
     ```kotlin
     val resolvedFromLang = languageDetector.detectChapterLanguage(
         source = translation.source,
         manga = translation.manga,
         chapterPath = chapterPath,
         sampleRecognizer = { img ->
             // Probe OCR pada 1-2 halaman sampel menggunakan Japanese recognizer untuk cek Kana/CJK/Latin
             // dan Korean recognizer jika belum ada karakter terdeteksi
         }
     )
     ```
   - Gunakan `resolvedFromLang` untuk menginisialisasi ulang `textRecognizer` jika berbeda dengan `textRecognizer.language`.
   - Gunakan `resolvedFromLang` saat membangun `textTranslator`.
   - Gunakan `resolvedFromLang` pada `convertToPageTranslation(...)`.
   - Catat log resolusi bahasa: `logcat(LogPriority.INFO) { "Auto-detected comic language for ${translation.manga.title}: $resolvedFromLang" }`

- [ ] **Step 3: Jalankan seluruh unit test suite translation untuk memastikan tidak ada regresi**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.translation.*"`
Expected: Seluruh unit test PASS.

- [ ] **Step 4: Commit integrasi ChapterTranslator**

```bash
git add app/src/main/java/eu/kanade/translation/recognizer/TextRecognizer.kt app/src/main/java/eu/kanade/translation/ChapterTranslator.kt
git commit -m "feat(translation): integrate dynamic comic language auto-detection in ChapterTranslator"
```

---

## Plan Review & Handoff
Plan ini mencakup seluruh kebutuhan user:
1. **Quick toggle di toolbar reader**: Tombol ikon translate di samping bookmark pada `ReaderTopBar` yang memicu toggle `readerPreferences.showTranslations` secara instan.
2. **Translate From Auto Detect**: Penambahan opsi `Auto Detect` pada pengaturan "Translate From" yang secara cerdas mendeteksi skrip Latin, Jepang, China, dan Korea dari teks dialog komik maupun metadata pendukung.

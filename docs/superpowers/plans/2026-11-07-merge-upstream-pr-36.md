# Upstream PR #36 (Comprehensive Translation System) Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mengintegrasikan secara penuh seluruh fitur komprehensif dari pull request upstream [mannu691/TachiyomiAT#36](https://github.com/mannu691/TachiyomiAT/pull/36) ke repositori lokal Mihon-AT ini secara rapi tanpa merusak arsitektur modern Mihon v0.21.3 (Metro DI, Compose ViewModel, dan deteksi bahasa dinamis).

**Architecture & Scope Mapping:**
- **Analisis Kompatibilitas:** PR #36 dibuat di atas basis TachiyomiAT lama (Desember 2024, Injekt DI, Voyager ScreenModel). Repositori lokal ini telah dimigrasikan ke Mihon v0.21.3 (Metro DI, AndroidX ViewModel, Jetpack Compose, deteksi bahasa dinamis). Oleh karena itu, penggabungan dilakukan melalui strategi *feature porting & adaptation*: mengadopsi fungsionalitas PR #36 ke dalam konvensi arsitektur modern Mihon-AT, bukan menjalankan raw `git merge` yang akan menimbulkan konflik besar dan merusak sistem DI.
- **Pemetaan Fitur PR #36 ke Rencana Implementasi (100% Cakupan):**
  1. **Translation Engines:**
     - **Gemini AI:** Upgrade default model ke `gemini-3.5-flash-lite` dengan validasi respons, penghapusan markdown codeblock (````json ... ````), dan penanganan error JSON EOF.
     - **Google Cloud Translation:** Penambahan engine baru (`CloudTranslator.kt`) berbasis BYOK API key dengan decoding entitas HTML (`Html.fromHtml`) dan auto-detect language.
     - **OpenRouter & MLKit:** Penguatan parsing dan penanganan error bahasa.
  2. **Profile-Based Rendering:**
     - **CJK Profile:** Aturan render khusus teks Korea, Jepang, China (font tetap 12dp, ekspansi balon agresif 1.8x-2.5x, margin konservatif 70%).
     - **Latin Profile:** Aturan render khusus teks Latin seperti Spanyol, Indonesia, Inggris (ekspansi terbatas, margin 95%, strategi hybrid overflow balon dan font 12dp→10dp untuk mencegah text clipping).
     - **Language-Specific AI Prompts:** Modul `TranslationPrompts.kt` dengan prompt spesifik per pasangan bahasa (Korea/Jepang ke Indonesia/Inggris).
  3. **In-Reader Translation Settings:**
     - Tab baru "Translation" pada dialog pengaturan reader (`ReaderSettingsDialog`) yang muncul saat bab memiliki terjemahan.
     - Slider penyesuaian *real-time* untuk margin teks, padding balon, tinggi oval, dan rasio ekspansi.
     - Perubahan langsung aktif pada balon bacaan secara instan tanpa perlu memuat ulang bab (*live update*).
  4. **Interactive Bubble Controls & Touch Fixes:**
     - Ketuk balon terjemahan untuk membuka menu kontekstual (`TranslationBubbleMenu`) dan opsi hapus balon.
     - Perbaikan formula deteksi sentuhan pada Webtoon (`WebtoonTranslationsView`) agar padding `padX = (block.symWidth * 2) * paddingMultiplier` dan `padY` sama persis dengan perenderan `SmartTranslationBlock`, mencegah event sentuh terlewat ke RecyclerView navigasi.
  5. **Bug Fixes:**
     - Perbaikan text clipping pada balon lonjong (*oval bubbles*) untuk terjemahan Spanyol dan Indonesia.
     - Perbaikan crash pada balon teks berukuran sangat kecil (`isLikelyOcrNoiseOrWatermark` & bounds safety).
     - Perbaikan decoding entitas HTML pada output Cloud Translation.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Metro DI, MLKit OCR, Google Cloud Translation API, Gemini 2.5 Flash, Moko-resources.

**Spec:** PR https://github.com/mannu691/TachiyomiAT/pull/36 (commit `10c782836..c68f94a1b` pada ref `pr-36`).

## Global Constraints
- Target platform: Android, JVM 17.
- Arsitektur DI: Gunakan **Metro DI** (`@Inject`, `@SingleIn(AppScope::class)`, `AppGraph`). Dilarang menggunakan Injekt (`uy.kohesive.injekt`).
- Arsitektur UI: Gunakan AndroidX ViewModel (`ReaderSettingsViewModel`), Compose Material 3, dan string multiplatform `ATMR.strings.*`.
- Kompatibilitas Fitur Lokal: Pertahankan fitur lokal yang sudah ada (Quick Toggle reader, ComicLanguageDetector Auto, AICore Gemma 4 E4B NPU).

## Review Focus
1. **Pembersihan Dependensi Injekt:** Seluruh kode dari PR #36 yang memanggil `Injekt.get<...>()` harus diadaptasi menggunakan Metro DI (`AppGraph` atau parameter injeksi Compose).
2. **Migrasi ScreenModel ke ViewModel:** `TranslationSettingsPage` dari PR #36 yang memakai `ReaderSettingsScreenModel` harus disesuaikan ke `ReaderSettingsViewModel`.
3. **Persistensi & Caching Penghapusan Balon:** Menghapus balon dialog tidak boleh merusak struktur JSON `PageTranslation` atau menyebabkan crash `IndexOutOfBoundsException` saat halaman di-zoom/scroll.
4. **Kinerja Render:** `LanguageRenderModule` dan evaluasi bentuk oval (`isOvalBubble`) tidak boleh menyebabkan re-komposisi berlebihan atau lag pada Webtoon continuous scroll.
5. **Resiliensi Output API Gemini & Cloud:** Pembersihan markdown formatting (````json ... ````) dan parsing entity HTML pada translator harus aman dari string kosong atau malformed response.

---

### Task 1: Domain TranslationProfile & Extended Preferences

**Files:**
- Create: `domain/src/main/java/tachiyomi/domain/translation/model/TranslationProfile.kt`
- Modify: `domain/src/main/java/tachiyomi/domain/translation/TranslationPreferences.kt`
- Modify: `i18n-at/src/commonMain/moko-resources/base/strings.xml`
- Test: `app/src/test/java/eu/kanade/translation/model/TranslationProfileTest.kt`

**Interfaces:**
- Produces:
  - `ProfileType` (`CJK`, `LATIN`)
  - `TranslationProfile` data class
  - `TranslationPreferences.cjkTextMargin`, `latinTextMargin`, `cloudTranslationApiKey`, dll.
  - String resources untuk pengaturan profil dan reader settings dialog

- [ ] **Step 1: Tulis unit test untuk `TranslationProfile`**

Buat `app/src/test/java/eu/kanade/translation/model/TranslationProfileTest.kt`:
```kotlin
package eu.kanade.translation.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.model.ProfileType
import tachiyomi.domain.translation.model.TranslationProfile

class TranslationProfileTest {
    @Test
    fun `ProfileType fromSourceLanguage identifies CJK and Latin correctly`() {
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("ja"))
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("ko"))
        assertEquals(ProfileType.CJK, ProfileType.fromSourceLanguage("zh"))
        assertEquals(ProfileType.LATIN, ProfileType.fromSourceLanguage("en"))
        assertEquals(ProfileType.LATIN, ProfileType.fromSourceLanguage("id"))
        assertEquals(ProfileType.LATIN, ProfileType.fromSourceLanguage("es"))
    }
}
```

- [ ] **Step 2: Jalankan test untuk memverifikasi kegagalan (RED)**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.translation.model.TranslationProfileTest"`
Expected: FAIL dengan unresolved reference `TranslationProfile`.

- [ ] **Step 3: Implementasikan `TranslationProfile.kt` dan tambahkan preferensi baru**

1. Buat `domain/src/main/java/tachiyomi/domain/translation/model/TranslationProfile.kt` (port dari PR #36).
2. Tambahkan preferensi baru di `TranslationPreferences.kt`:
   - `cjkTextMargin`, `latinTextMargin`
   - `cjkPaddingMultiplier`, `latinPaddingMultiplier`
   - `cjkOvalHeightMargin`, `latinOvalHeightMargin`
   - `cjkHorizontalPadding`, `latinHorizontalPadding`
   - `cjkVerticalPadding`, `latinVerticalPadding`
   - `cjkMinFontSize`, `latinMinFontSize`
   - `cjkMaxFontSize`, `latinMaxFontSize`
   - `cjkLineHeightMultiplier`, `latinLineHeightMultiplier`
   - `cjkMinExpansion`, `latinMinExpansion`
   - `cjkMaxExpansion`, `latinMaxExpansion`
   - `cjkUseHybridOverflow`, `latinUseHybridOverflow`
   - `cloudTranslationApiKey`
   - Metode factory `getProfile(type: ProfileType): TranslationProfile`
3. Tambahkan string baru ke `i18n-at/src/commonMain/moko-resources/base/strings.xml`:
   - `pref_translation_profile_active`, `pref_translation_profile_cjk`, `pref_translation_profile_latin`
   - `pref_translation_no_translations`, `action_delete_bubble`, `pref_cloud_api_key`, dll.

- [ ] **Step 4: Jalankan test untuk memverifikasi keberhasilan (GREEN)**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.translation.model.TranslationProfileTest"`
Expected: PASS.

- [ ] **Step 5: Commit Task 1**

```bash
git add domain/src/main/java/tachiyomi/domain/translation/model/TranslationProfile.kt \
        domain/src/main/java/tachiyomi/domain/translation/TranslationPreferences.kt \
        i18n-at/src/commonMain/moko-resources/base/strings.xml \
        app/src/test/java/eu/kanade/translation/model/TranslationProfileTest.kt
git commit -m "feat(translation): port TranslationProfile and preference extensions from upstream PR #36"
```

---

### Task 2: PageTranslation Enhancements & Bubble Geometry

**Files:**
- Modify: `app/src/main/java/eu/kanade/translation/model/PageTranslation.kt`
- Test: `app/src/test/java/eu/kanade/translation/model/PageTranslationGeometryTest.kt`

**Interfaces:**
- Produces:
  - `BubbleShape` (`RECTANGULAR`, `OVAL`, `VERTICAL_OVAL`)
  - `TranslationBlock.bubbleShape: BubbleShape`
  - `PageTranslation.deletedBlockIndices: MutableSet<Int>`
  - `PageTranslation.activeBlocks: List<TranslationBlock>`
  - `PageTranslation.profileType: ProfileType`

- [ ] **Step 1: Tulis unit test untuk `BubbleShape` dan penghapusan blok**

Buat `app/src/test/java/eu/kanade/translation/model/PageTranslationGeometryTest.kt` menguji:
1. Blok dengan rasio lebar/tinggi seimbang terdeteksi sebagai `OVAL`.
2. Blok dengan rasio tinggi dominan terdeteksi sebagai `VERTICAL_OVAL`.
3. Menghapus blok via indeks atau ID mengabaikannya dari `activeBlocks`.

- [ ] **Step 2: Jalankan test untuk memverifikasi kegagalan (RED)**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.translation.model.PageTranslationGeometryTest"`
Expected: FAIL.

- [ ] **Step 3: Implementasikan ekstensi geometri pada `PageTranslation.kt`**

Porting penambahan geometri dari PR #36 ke `PageTranslation.kt`:
- Tambahkan enum `BubbleShape`.
- Tambahkan fungsi pembantu deteksi bentuk balon berdasarkan rasio `width / height` dan `angle`.
- Tambahkan properti penghapusan balon interaktif (`deleteBlock(block)`).

- [ ] **Step 4: Jalankan test untuk memverifikasi keberhasilan (GREEN)**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.translation.model.PageTranslationGeometryTest"`
Expected: PASS.

- [ ] **Step 5: Commit Task 2**

```bash
git add app/src/main/java/eu/kanade/translation/model/PageTranslation.kt \
        app/src/test/java/eu/kanade/translation/model/PageTranslationGeometryTest.kt
git commit -m "feat(translation): add bubble geometry shapes and block deletion support"
```

---

### Task 3: LanguageRenderModule, Interactive Bubble Menu & SmartTranslationBlock

**Files:**
- Create: `app/src/main/java/eu/kanade/translation/presentation/LanguageRenderModule.kt`
- Create: `app/src/main/java/eu/kanade/translation/presentation/TranslationBubbleMenu.kt`
- Modify: `app/src/main/java/eu/kanade/translation/presentation/SmartTranslationBlock.kt`
- Modify: `app/src/main/java/eu/kanade/translation/presentation/PagerTranslationsView.kt`
- Modify: `app/src/main/java/eu/kanade/translation/presentation/WebtoonTranslationsView.kt`

**Interfaces:**
- Consumes:
  - `TranslationProfile`
  - `PageTranslation.activeBlocks`
- Produces:
  - `LanguageRenderModule.fromProfile(profile: TranslationProfile): LanguageRenderModule`
  - Interaksi tap balon terjemahan memunculkan `TranslationBubbleMenu` untuk hapus balon
  - Rendering CJK vs Latin dengan margin dan font scaling adaptif

- [ ] **Step 1: Port `LanguageRenderModule.kt` dan `TranslationBubbleMenu.kt`**

1. Salin `LanguageRenderModule.kt` dari PR #36 ke `app/src/main/java/eu/kanade/translation/presentation/LanguageRenderModule.kt`. Bersihkan dependensi Injekt dan sesuaikan import.
2. Salin `TranslationBubbleMenu.kt` dari PR #36 ke `app/src/main/java/eu/kanade/translation/presentation/TranslationBubbleMenu.kt`.

- [ ] **Step 2: Perbarui `SmartTranslationBlock.kt`**

Port logika rendering canggih dari PR #36:
- Tambahkan pendeteksi bentuk balon oval dan perhitungan margin dinamis.
- Terapkan strategi overflow hybrid untuk Latin (English/Spanish/Indonesian) dan font tetap untuk CJK.
- Tambahkan penangan tap/klik pada balon untuk membuka `TranslationBubbleMenu` dengan callback `onDeleteBlock`.
- **Penting:** Ganti `Injekt.get<TranslationPreferences>()` dengan pengambilan preferensi via parameter atau `LocalContext.current.appGraph.translationPreferences`.

- [ ] **Step 3: Hubungkan penanganan interaktif dan perbaikan deteksi sentuhan di `PagerTranslationsView` dan `WebtoonTranslationsView`**

Perbarui kedua view:
1. Di `WebtoonTranslationsView.kt`:
   - Terapkan fungsi `calculateBubbleBounds(scaleFactor)` dengan formula padding presisi: `padX = (block.symWidth * 2) * paddingMultiplier` dan `padY = block.symHeight * paddingMultiplier` (memperbaiki *touch detection formula mismatch* dari versi upstream lama).
   - Override `onTouchEvent` untuk meng-intercept tap pada balon terjemahan sehingga tidak terlewat menjadi aksi navigasi/scroll pada RecyclerView pembaca webtoon.
   - Sambungkan callback tap untuk menampilkan `TranslationBubbleMenu` dan memanggil `onBlockDelete`.
2. Di `PagerTranslationsView.kt`:
   - Hubungkan penanganan klik/tap pada blok untuk menampilkan menu hapus balon terjemahan.

- [ ] **Step 4: Verifikasi kompilasi kode Compose**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL tanpa error.

- [ ] **Step 5: Commit Task 3**

```bash
git add app/src/main/java/eu/kanade/translation/presentation/LanguageRenderModule.kt \
        app/src/main/java/eu/kanade/translation/presentation/TranslationBubbleMenu.kt \
        app/src/main/java/eu/kanade/translation/presentation/SmartTranslationBlock.kt \
        app/src/main/java/eu/kanade/translation/presentation/PagerTranslationsView.kt \
        app/src/main/java/eu/kanade/translation/presentation/WebtoonTranslationsView.kt
git commit -m "feat(reader): integrate LanguageRenderModule and interactive bubble menu"
```

---

### Task 4: In-Reader Translation Settings Tab

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/reader/settings/TranslationSettingsPage.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderSettingsViewModel.kt`

**Interfaces:**
- Consumes:
  - `ReaderSettingsViewModel`
  - `TranslationPreferences`
- Produces:
  - Tab "Translation" pada `ReaderSettingsDialog` pembaca komik
  - Kontrol slider langsung untuk CJK & Latin profile margin, padding, font size, dan expansion ratio

- [ ] **Step 1: Implementasikan `TranslationSettingsPage.kt`**

Porting `TranslationSettingsPage.kt` dari PR #36, dengan penyesuaian:
- Ubah parameter dari `screenModel: ReaderSettingsScreenModel` menjadi `viewModel: ReaderSettingsViewModel`.
- Gunakan slider components Material 3 yang konsisten dengan halaman pengaturan reader lainnya.
- Tampilkan opsi pengaturan CJK dan Latin beserta tombol *"Reset to Defaults"*.

- [ ] **Step 2: Tambahkan tab ke-4 pada `ReaderSettingsDialog.kt`**

Di `ReaderSettingsDialog.kt`:
1. Tambahkan `stringResource(ATMR.strings.pref_category_translations)` ke daftar `tabTitles`.
2. Pada `when (page)`, tambahkan case `3 -> TranslationPage(viewModel)`.

- [ ] **Step 3: Sediakan state dan helper di `ReaderSettingsViewModel.kt`**

Expose `hasTranslationsFlow` dan `currentProfileTypeFlow` agar `TranslationPage` dapat menampilkan status profil komik yang sedang aktif.

- [ ] **Step 4: Verifikasi kompilasi dan integrasi UI**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit Task 4**

```bash
git add app/src/main/java/eu/kanade/presentation/reader/settings/TranslationSettingsPage.kt \
        app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt \
        app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderSettingsViewModel.kt
git commit -m "feat(reader): add in-reader translation settings tab in ReaderSettingsDialog"
```

---

### Task 5: Translation Engine Upgrades (Prompts & Google Cloud Translator)

**Files:**
- Create: `app/src/main/java/eu/kanade/translation/translator/TranslationPrompts.kt`
- Create: `app/src/main/java/eu/kanade/translation/translator/CloudTranslator.kt`
- Modify: `app/src/main/java/eu/kanade/translation/translator/GeminiTranslator.kt`
- Modify: `app/src/main/java/eu/kanade/translation/translator/TextTranslators.kt`
- Modify: `app/src/main/java/eu/kanade/translation/ChapterTranslator.kt`
- Test: `app/src/test/java/eu/kanade/translation/translator/TranslationPromptsTest.kt`

**Interfaces:**
- Produces:
  - `TextTranslators.GOOGLE_CLOUD`
  - `CloudTranslator` mengimplementasikan `TextTranslator` menggunakan Google Cloud Translation REST API
  - `TranslationPrompts` menghasilkan prompt kontekstual spesifik bahasa sumber dan target
  - `GeminiTranslator` otomatis membersihkan pembungkus markdown codeblock

- [ ] **Step 1: Tulis unit test untuk `TranslationPrompts` dan parser Gemini**

Buat `app/src/test/java/eu/kanade/translation/translator/TranslationPromptsTest.kt` menguji:
1. Pemilihan prompt bahasa Korea $\rightarrow$ English/Indonesian.
2. Pemilihan prompt bahasa Jepang $\rightarrow$ English/Indonesian.
3. Fungsi pembersih markdown codeblock pada output Gemini.

- [ ] **Step 2: Jalankan test untuk memverifikasi kegagalan (RED)**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.translation.translator.TranslationPromptsTest"`
Expected: FAIL.

- [ ] **Step 3: Implementasikan `TranslationPrompts.kt`, `CloudTranslator.kt`, dan perbarui engine**

1. Salin `TranslationPrompts.kt` dari PR #36 (menyediakan prompt teroptimasi per pasangan bahasa CJK dan Latin).
2. Salin `CloudTranslator.kt` dari PR #36:
   - Gunakan Google Cloud Translation REST API dengan BYOK API key.
   - Lakukan decoding entitas HTML pada teks hasil terjemahan (`Html.fromHtml(..., Html.FROM_HTML_MODE_LEGACY)`).
   - Inisialisasi service secara lazy pada `Dispatchers.IO` untuk mencegah `NetworkOnMainThreadException`.
3. Tambahkan `GOOGLE_CLOUD` ke `TextTranslators` enum.
4. Perbarui `GeminiTranslator.kt`:
   - Upgrade default model ke `gemini-3.5-flash-lite`.
   - Tambahkan fungsi helper `stripMarkdownCodeBlock(text: String)` untuk membersihkan pembungkus ````json ... ```` yang sering menyebabkan kegagalan parse.
   - Tangani kondisi respons kosong / JSON EOF dengan log detail mengenai alasan filter keamanan atau kuota.
5. Perbarui `ChapterTranslator.kt` dengan batas penggabungan blok (*smartMergeBlocks*) spesifik bahasa dari PR #36:
   - Korea: 60/35/40
   - Jepang: 50/30/30
   - China: 45/25/28
   - Tandai `v.translatorType` pada `PageTranslation` untuk optimalisasi rendering.

- [ ] **Step 4: Jalankan test untuk memverifikasi keberhasilan (GREEN)**

Run: `./gradlew :app:testDebugUnitTest --tests "eu.kanade.translation.translator.TranslationPromptsTest"`
Expected: PASS.

- [ ] **Step 5: Commit Task 5**

```bash
git add app/src/main/java/eu/kanade/translation/translator/TranslationPrompts.kt \
        app/src/main/java/eu/kanade/translation/translator/CloudTranslator.kt \
        app/src/main/java/eu/kanade/translation/translator/GeminiTranslator.kt \
        app/src/main/java/eu/kanade/translation/translator/TextTranslators.kt \
        app/src/main/java/eu/kanade/translation/ChapterTranslator.kt \
        app/src/test/java/eu/kanade/translation/translator/TranslationPromptsTest.kt
git commit -m "feat(translation): add TranslationPrompts and CloudTranslator engine from PR #36"
```

---

### Task 6: Verifikasi Menyeluruh & Testing Regresi

**Files:**
- Semua file terkait translasi dan reader

**Interfaces:**
- Memastikan tidak ada regresi pada seluruh test suite dan build APK

- [ ] **Step 1: Jalankan seluruh unit test suite proyek**

Run: `./gradlew :app:testDebugUnitTest`
Expected: Seluruh unit test PASS dengan 0 failure.

- [ ] **Step 2: Jalankan validasi Spotless format**

Run: `./gradlew spotlessCheck` (atau `./gradlew spotlessApply` jika ada formatting gap).
Expected: Spotless check PASS.

- [ ] **Step 3: Uji perakitan build APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL dan APK siap diuji coba.

- [ ] **Step 4: Commit dokumentasi arsitektur translasi**

Salin `TRANSLATION_PIPELINE.md` dari PR #36 ke `docs/translation/TRANSLATION_PIPELINE.md`.
```bash
git add docs/translation/TRANSLATION_PIPELINE.md
git commit -m "docs: add translation pipeline architecture documentation from PR #36"
```

---

## Plan Review & Handoff
Rencana ini mengadopsi seluruh nilai tambah dari PR upstream #36 (Interactive Bubbles, CJK vs Latin rendering, In-Reader Settings, Profile System, Prompts, Cloud Translator) secara bersih ke dalam arsitektur modern Mihon-AT (Metro DI & Jetpack Compose), menghindari konflik git merge warisan basis lama.

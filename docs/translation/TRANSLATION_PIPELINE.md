# Translation Pipeline Architecture

## Overview

This document describes the complete translation pipeline in Mihon-AT - how manga images are OCR'd, translated, and rendered with translated text overlays.

---

## Complete Pipeline Flowchart

```
┌─────────────────────────────────────────────────────────────────────┐
│                    1. TRIGGER TRANSLATION                           │
│   User taps "Translate" in reader menu                              │
│   OR auto-translate fires after chapter download                    │
│                                                                     │
│   Entry: TranslationManager.translateChapter(manga, chapter)        │
│   Injected via Metro DI into ReaderViewModel / App components       │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    2. QUEUE & LOAD IMAGES                           │
│   ChapterTranslator queues the job                                  │
│   Load chapter pages from disk/archive (downloaded images)          │
│   Status: QUEUE → RECOGNIZING                                       │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    3. OCR (TEXT RECOGNITION)                        │
│   For each page image:                                              │
│   ┌───────────────────────────────────────────────────────────────┐ │
│   │  MLKit TextRecognizer.recognize(image)                        │ │
│   │  • Uses language-specific model (Chinese/Japanese/Korean/Latin)│ │
│   │  • Returns Text.TextBlock objects with:                       │ │
│   │    - Bounding box (x, y, width, height)                       │ │
│   │    - Recognized text string                                    │ │
│   │    - Confidence score                                          │ │
│   │    - Detected language                                         │ │
│   └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                 4. LANGUAGE DETECTION & BLOCK MERGING               │
│   • Analyze recognized language from OCR results                    │
│   • Detect: Korean (ko), Japanese (ja), Chinese (zh), etc.          │
│   • smartMergeBlocks() - merge adjacent/overlapping text regions    │
│     with language-specific thresholds:                              │
│       Korean:   widthThresh=60, xThresh=35, yThresh=40              │
│       Japanese: widthThresh=50, xThresh=30, yThresh=30              │
│       Chinese:  widthThresh=45, xThresh=25, yThresh=28              │
│   • Create PageTranslation with TranslationBlock list               │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    5. TRANSLATION ENGINE                            │
│   Status: TRANSLATING                                               │
│   TextTranslator.translate(pages) - mutates block.translation       │
│                                                                     │
│   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐│
│   │   MLKit     │  │   Cloud     │  │   Gemini    │  │ OpenRouter ││
│   │ (On-device) │  │ Translation │  │    AI       │  │  (Multi-AI)││
│   │ No API key  │  │ Google API  │  │  3.5 Flash  │  │ GPT/Claude ││
│   │ Offline     │  │ BYOK        │  │ Context-aware│ │   BYOK     ││
│   └─────────────┘  └─────────────┘  └─────────────┘  └────────────┘│
│                                                                     │
│   AI engines (Gemini/OpenRouter):                                   │
│   • Manga-aware system prompts (TranslationPrompts)                 │
│   • Watermark detection → replaced with "RTMTH"                     │
│   • Batch translation via JSON structure                            │
│   • Default Gemini model: gemini-3.5-flash-lite                     │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    6. SAVE TO DISK                                  │
│   Status: SAVED                                                     │
│   Json.encodeToStream(pages, translationFile)                       │
│                                                                     │
│   Storage: translations/{source}/{manga}/{chapter}.json             │
│   Example: translations/MangaDex/One Piece/Chapter 1.json           │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│              7. DISPLAY IN READER (On Chapter Open)                 │
│   ReaderViewModel observes and loads chapter translation            │
│   Render PagerTranslationsView / WebtoonTranslationsView overlay    │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                 8. RENDER TRANSLATION BUBBLES                       │
│   For each TranslationBlock:                                        │
│   ┌───────────────────────────────────────────────────────────────┐ │
│   │  SmartTranslationBlock composable                             │ │
│   │  • Position at original (x, y) from OCR                       │ │
│   │  • Draw white background bubble                               │ │
│   │  • Render translated text on top                              │ │
│   │  • Apply LanguageRenderModule rules (CJK vs Latin)            │ │
│   │  • Tap to open TranslationBubbleMenu (interactive bubble)     │ │
│   └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│   CJK Rendering (Korean/Japanese/Chinese):                          │
│   • Fixed 12dp font size                                            │
│   • Aggressive bubble expansion (1.8x-2.5x)                         │
│   • Conservative margins (70%)                                      │
│                                                                     │
│   Latin Rendering (English/Spanish/Indonesian):                     │
│   • Hybrid: expand bubble first, then reduce font (12→10dp)         │
│   • Limited expansion (1.0x-1.3x)                                   │
│   • Generous margins (95%)                                          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Simplified Visual Summary

```
   Downloaded        OCR              Block           Translation       Rendered
   Manga Image  →  Detection  →    Extraction   →      Engine     →    Overlay

   ┌─────────┐    ┌─────────┐     ┌──────────┐     ┌──────────┐     ┌─────────┐
   │  JPG/   │    │ MLKit   │     │ Bounding │     │ MLKit/   │     │ White   │
   │  PNG    │ →  │ Text    │  →  │ Boxes +  │  →  │ Cloud/   │  →  │ bubble  │
   │  Image  │    │ Recog.  │     │ Text     │     │ Gemini/  │     │ with    │
   │         │    │         │     │          │     │ OpenRouter│    │ text    │
   └─────────┘    └─────────┘     └──────────┘     └──────────┘     └─────────┘
```

---

## Key Components & Architecture

| Layer | Component | Location / File | Purpose |
|-------|-----------|-----------------|---------|
| Dependency Injection | Metro DI | `AppModule.kt` | Provides singleton instances of `TranslationPreferences`, `TranslationManager`, and `ChapterTranslator`. |
| UI & State | `ReaderViewModel` | `ui/reader/ReaderViewModel.kt` | Manages reader translation state, reactive flows, and user action dispatches. |
| Settings UI | `SettingsTranslationScreen` & `TranslationSettingsPage` | `presentation/...` | Configuration UI for translation engines, API keys, fonts, and profiles. |
| Entry Point | `TranslationManager` | `translation/TranslationManager.kt` | Facade coordinating chapter downloads, disk caches, and translation status. |
| Orchestrator | `ChapterTranslator` | `translation/ChapterTranslator.kt` | Background job execution: orchestrates OCR, block merging, translation, and JSON persistence. |
| OCR | `TextRecognizer` | `translation/recognizer/TextRecognizer.kt` | MLKit OCR extraction with language routing. |
| Translation Engines | `TextTranslator` | `translation/translator/*.kt` | Concrete engine implementations: MLKit (offline), Google Cloud Translation API, Gemini AI (`gemini-3.5-flash-lite`), and OpenRouter. |
| Models | Data Models | `translation/model/*.kt` | `PageTranslation`, `TranslationBlock`, and translation state indicators. |
| Profile System | Domain Models | `domain/.../TranslationProfile.kt` | Per-source and per-manga translation profiles and custom system prompts. |
| Rendering | Smart Overlays | `translation/presentation/*.kt` | `SmartTranslationBlock`, `LanguageRenderModule` (CJK vs Latin typography rules), and `TranslationBubbleMenu`. |

---

## Key Insights

1. **OCR-based extraction** - MLKit Text Recognition extracts text with bounding boxes from manga images.
2. **Text-only translation** - Only recognized strings are translated, preserving original manga artwork and background details.
3. **Overlay rendering** - Translated text drawn on top of the original image at detected coordinates using Compose.
4. **Language-specific optimization** - Distinct rendering and bubble-expansion rules for CJK (compact, vertical-friendly) vs Latin (wider horizontal space).
5. **Interactive Bubbles** - Tapping translated bubbles triggers an interactive popup menu (`TranslationBubbleMenu`) allowing users to copy source text, copy translation, view OCR confidence, or adjust bubble parameters.
6. **Persistent caching** - Translations saved to JSON files (`translations/{source}/{manga}/{chapter}.json`) for instant re-display without repeated API costs.

---

## Translation State Machine

```
QUEUE → RECOGNIZING → TRANSLATING → SAVED
                 ↘         ↓
                   → → ERROR
```

| State | Description |
|-------|-------------|
| `QUEUE` | Translation request enqueued and awaiting resource execution |
| `RECOGNIZING` | MLKit OCR in progress across chapter images |
| `TRANSLATING` | Selected translation engine active (local MLKit, Cloud, Gemini, or OpenRouter) |
| `SAVED` | Translations successfully completed and written to local disk cache |
| `ERROR` | Encountered failure during OCR or translation (partial results preserved when feasible) |

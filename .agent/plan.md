# Project Plan

A standalone Android floating utility app called Floating Lens / Screen Translator, specifically tailored for Xiaomi Pad 6 (HyperOS tablet / large screen).
The app features a Two-Tier Architecture:
1. Host App:
   - Floating Overlay Controller via WindowManager (draggable FAB with bottom-center drag-to-dismiss target).
   - Silent Screen Capture using MediaProjection / AccessibilityService.
   - Multi-Script OCR via ML Kit (Japanese vertical script, Korean Hangul, Chinese Hanzi, Latin/Spanish).
   - Speech Bubble Segmentation (groups vertical RTL columns, strips furigana, consolidates lines into sentences).
   - Built-in Offline Engine (ML Kit Translate / local quantized models for 100% offline translation to English).
   - In-Place Visual Masking (renders solid white rounded pill patches over source ink and auto-scaled English text over original speech bubbles).
2. AI Engine Plugin (Companion APK):
   - Local companion service supporting lightweight Qwen LLM models (and Snapdragon 870 NNAPI acceleration).
   - Communicates with Host App via AIDL IPC (ITranslationPlugin.aidl), falling back to Host local engine if plugin absent.
Target form factor: Xiaomi Pad 6 tablet / large screen / HyperOS.

## Project Brief

# Project Brief: Floating Lens / Screen Translator

## Overview
A floating utility application tailored for Xiaomi Pad 6 (HyperOS tablet / large screen form factor) that provides real-time, on-screen translation through a two-tier architecture consisting of a Host Application and an optional AI Engine Companion Plugin.

## Features
1. **Draggable Floating Lens Overlay**: A persistent WindowManager-based floating action button (FAB) with drag-to-dismiss capabilities that initiates silent on-screen capture on tablet layouts.
2. **Multi-Script OCR & Speech Bubble Segmentation**: Fast on-device optical character recognition via ML Kit (supporting Japanese vertical/horizontal, Korean, Chinese, and Latin scripts) with automatic speech bubble grouping and furigana filtering.
3. **In-Place Visual Masking & Text Overlay**: Context-aware rendering engine that overlays rounded white patches over source text and places auto-scaled translated English text directly inside original screen speech bubbles.
4. **Dual-Mode Translation Engine**: Built-in offline translation via ML Kit Translate, with extensible AIDL IPC (`ITranslationPlugin.aidl`) allowing dynamic fallback to an AI Engine Companion Service supporting local lightweight Qwen LLM inference.

## High-Level Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Navigation & Adaptive Strategy**: Jetpack Navigation 3 (state-driven) and Compose Material Adaptive library
- **Asynchronous / Concurrency**: Kotlin Coroutines & Flow
- **Core Architectural Components**: ViewModel, AIDL IPC Service communication
- **ML & System APIs**: Google ML Kit Text Recognition v2, ML Kit Translate, Android WindowManager, MediaProjection API / Accessibility Service

## Implementation Steps
**Total Duration:** 22m 26s

### Task_1_OverlayAndScreenCapture: Implement Floating Overlay Service with draggable WindowManager FAB, permission handling (Overlay and MediaProjection), and silent screen capture engine using MediaProjection API.
- **Status:** COMPLETED
- **Updates:** Implemented FloatingOverlayService with WindowManager overlay, drag-to-dismiss FAB, MediaProjection ScreenCaptureEngine, permissions, and MainActivity toggle controls. Verified build passed with assembleDebug.
- **Acceptance Criteria:**
  - Floating FAB overlay renders over system apps
  - MediaProjection screen capture captures current screen buffer successfully
  - build pass
- **Duration:** 5m 12s

### Task_2_OCRAndTranslationEngine: Implement multi-script OCR engine using Google ML Kit Text Recognition v2, speech bubble text grouping, offline ML Kit Translate engine, and AIDL IPC interface (ITranslationPlugin.aidl) for external companion translation fallback.
- **Status:** COMPLETED
- **Updates:** Implemented ML Kit OCR v2 (MultiScriptOcrEngine), SpeechBubbleSegmenter (RTL sorting, furigana stripping, spatial grouping), OfflineTranslationEngine (ML Kit Translate), AIDL IPC (ITranslationPlugin.aidl & TranslationPluginManager), UnifiedTranslationManager, and ScreenTranslationPipeline. Unit tests passed and assembleDebug passed.
- **Acceptance Criteria:**
  - ML Kit OCR extracts text and bounding boxes from captured screen images
  - ML Kit Translate and AIDL IPC interface return translated English text
  - build pass
- **Duration:** 11m 14s

### Task_3_InPlaceMaskingAndOverlayUI: Implement Compose/WindowManager overlay rendering engine for in-place rounded pill masking and auto-scaled English text rendering over screen speech bubbles. Implement Main Activity and Settings UI for service toggle and OCR/translation preferences.
- **Status:** COMPLETED
- **Updates:** Implemented SpeechBubbleMaskOverlay, AutoResizedText, TranslationOverlayManager, OverlaySettingsRepository, updated FloatingOverlayService, and MainActivity with adaptive tablet/phone layout. Unit tests and assembleDebug passed.
- **Acceptance Criteria:**
  - In-place rounded white pill masking renders over source text bubbles
  - Auto-scaled translated English text displays inside masked bubbles
  - Main Settings screen enables toggling service and setting translation preferences
  - build pass
- **Duration:** 4m 27s

### Task_4_RunAndVerify: Run and verify the complete Floating Lens translator app. Instruct critic_agent to verify application stability (no crashes), confirm alignment with user requirements, and report critical UI issues.
- **Status:** COMPLETED
- **Updates:** Completed final quality assurance with critic_agent on tablet emulator. Verified stability (0 crashes), Material 3 adaptive navigation rail layout, floating overlay service, AIDL plugin integration (Qwen Companion AI Engine), ML Kit offline translation, and speech bubble masking settings. All acceptance criteria met.
- **Acceptance Criteria:**
  - make sure all existing tests pass
  - build pass
  - app does not crash
  - Floating Lens overlay, screen capture, OCR, translation, and text overlay function without crashes
- **Duration:** 1m 33s


# PDF ALL IN ONE Pro+Ai

A native, high-performance, offline-first Android PDF reader and toolkit built with modern Android standards: Kotlin, Jetpack Compose, Material 3, Room, and PdfBox-Android.

---

## 🌟 Key Features

### 1. Form Filling & Signatures (NEW)
- **AcroForm Detection & Interactive Overlays (`com.tom-roush:pdfbox-android`)**:
  - Automatically detects AcroForm fields: Text fields, Checkboxes, Radio buttons, Dropdown combo boxes, List boxes, and Date fields.
  - Renders pixel-accurate, touch-friendly interactive Jetpack Compose controls directly over document pages.
  - Field Navigator: Floating pill with Prev and Next field buttons, field counter, and automatic smooth-scroll to field page.
  - Saves updated values back into standard PDF form structures (`PDAcroForm`) with optional one-click Flattening.
- **Fill and Sign Toolkit**:
  - **Draw Signature**: Smooth drawing canvas with finger/stylus stroke capture, ink color palette (Navy Blue, Crisp Black, Signature Red), Undo, and Clear.
  - **Type Signature**: Type a name or initials and preview in 4 elegant handwriting/cursive typography styles.
  - **Import Signature**: Photo picker with automated white-background luminosity removal and sensitivity control.
  - **Private Storage**: Stores up to 3 saved signatures and 3 saved initials in app-private storage (`context.filesDir`).
  - **Interactive Placement**: Freely drag, reposition, and resize signatures and initials anywhere on a page with corner handles.
- **Quick-Fill Tools for Flat (Non-Form) PDFs**:
  - Place text anywhere with custom font styling.
  - Checkmark (✓) and Cross (✕) stamp shortcuts.
  - Today's Date shortcut.
  - User profile autofill snippets (Full Name, Initials, Email, Phone, Address) managed in Profile/Settings.
- **Form Field Creator**:
  - Add new Text fields, Checkboxes, Radio buttons, or Dropdown menus to any page with custom field name, default value, and required flag.
- **Safe Save & Overwrite Control**:
  - "Save as copy" via SAF `CREATE_DOCUMENT`.
  - Overwrite original with confirmation prompt.
  - Flatten document option to make all fields, signatures, and marks permanent.

### 2. Tools Tab & Page Tools
- **Searchable Grid of Tools**: Search bar filtering tools by keyword, plus category tabs (`All`, `Page Tools`, `Organize`, `Convert`, `AI Intelligence`).
- **Page Organizer (from Viewer and from Tools)**:
  - Interactive thumbnail grid with continuous previews.
  - **Reorder**: Quick move left/right buttons and drag-and-drop ordering.
  - **Rotate**: 90° clockwise and counter-clockwise rotation steps.
  - **Delete & Duplicate**: Remove unwanted pages or duplicate templates.
  - **Multi-Select Mode**: Select multiple pages for bulk actions.
  - **Insert Blank Page**: Add blank pages at the beginning, end, or custom positions.
  - **Insert from another PDF**: Select another PDF via SAF and append or insert its pages.
  - **Session Undo & Redo**: Unlimited history tracking for all page operations before saving.
  - **Safe Save**: Overwrite confirmation warning or "Save as copy" via `CREATE_DOCUMENT`.
- **Merge Documents**:
  - Combine multiple PDFs and images into a single unified PDF.
  - Reorder items up and down.
  - Optional custom page ranges per file (e.g. `1-3, 5`).
- **Split PDF**:
  - Split by custom page ranges (e.g. `1-3, 5, 7-9`).
  - Split every N pages (equal-sized document chunks).
  - Split by document bookmarks / table of contents chapters.
  - Save destination: Single packaged `.zip` archive or directory chosen via `OPEN_DOCUMENT_TREE`.
- **Extract Pages**:
  - Isolate specific pages and save them into a new independent PDF.
  - Quick filter chips for odd, even, and custom range inputs.
- **Images to PDF**:
  - Convert gallery photos or new camera captures into a multi-page PDF.
  - Reorder images before generation.
  - Page size presets: A4, US Letter, Fit to Image, US Legal.
  - Margins: None (0pt), Small (18pt), Medium (36pt), Large (54pt).
  - Orientation: Auto-detect, Portrait, Landscape.
  - Image quality compression presets (High, Medium, Low) with memory-safe bitmap downscaling.
- **PDF to Images**:
  - Render and export document pages as JPG or PNG images.
  - Selectable DPI: 72 DPI (Screen), 150 DPI (Balanced), 300 DPI (Print quality).
  - Output to single `.zip` file or SAF directory.
- **Crop Pages and Change Page Size**:
  - Visual crop margins in points (Left, Top, Right, Bottom).
  - Standardize paper dimensions (A4, Letter, Legal).
  - Scope: All Pages, Current Page Only, Odd Pages, Even Pages.
- **Background Operations & Foreground Service**:
  - Every operation executes asynchronously on `Dispatchers.IO`.
  - Android 14+ Foreground Service (`PdfOperationService`) with `dataSync` type and status bar notification progress.
  - Real-time in-app progress dialog with percentage indicator and Cancel button.
  - Success result dialog with direct "Open PDF" and "Share" (`ACTION_SEND` via FileProvider) actions.
  - Low-memory streaming architecture that avoids loading entire PDFs into memory.

### 2. Comprehensive PDF Annotation Tools
- **Bottom Annotation Toolbar**:
  - Color palette with 8 presets and thickness picker (1pt to 16pt).
  - **Text Markup**: Highlight, Underline, and Strikeout.
  - **Freehand Pen & Highlighter Pen**: Natural drawing with opacity tuning for highlighters.
  - **Eraser**: Tap or drag to erase strokes and marks.
  - **Stylus & Palm Rejection**: Distinguishes stylus from touch events, automatically ignoring palm touches while a stylus is in use. Hardware eraser tip support.
  - **Sticky Notes**: Tap anywhere on a page to drop a note pin, tap to view or edit comments.
  - **Free Text Box**: Place styled text anywhere on the document with custom font size and colors.
  - **Geometric Shapes**: Draw lines, directional arrows, rectangles, and ellipses.
  - **Unlimited Session Undo & Redo**: Stack-based history tracking.
- **Standards-Compliant PDF Annotation Engine (`com.tom-roush:pdfbox-android`)**:
  - Saves annotations as standard PDF specifications (`PDAnnotationTextMarkup`, `PDAnnotationMarkup`, `PDAnnotationSquareCircle`, `PDAnnotationLine`, `PDAnnotationText`) so third-party viewers render them.
  - **Existing Annotations Support**: Parses and displays pre-existing annotations inside loaded PDFs.
  - **Safe Save & Overwrite Protection**: Overwrite warning dialog, "Save as copy" via SAF, and "Flatten Annotations" option.
- **Annotations List Screen**:
  - Modal sheet grouping all annotations by page with type icons, color previews, and tap-to-jump navigation.

### 3. Advanced Reading Features
- **PdfBox-Android Engine (`com.tom-roush:pdfbox-android`, Apache 2.0)**: Background text extraction page by page.
- **Full-Document Asynchronous Text Search**: Live search bar, match snippets, and next/previous navigation.
- **Table of Contents (Outline) & User Bookmarks**: Document hierarchy viewing and persistent Room database bookmarks.
- **4 Reading Modes**: Continuous Scroll, Single Page (Swipe), Two-Page Spread (Tablets/Landscape), and Immersive Full-Screen.
- **Text-to-Speech (TTS)**: Native Android voice synthesis with speed controls (0.75x to 2.0x).
- **Auto-Scroll**: Hands-free 60fps smooth scrolling with adjustable speeds.
- **Settings Screen**: Theme switching (System, Light, Dark), default reading mode, and Keep Screen On.

### 4. Core Storage & PDF Viewer Engine
- **Storage Access Framework (SAF)**: Zero broad storage permissions (`MANAGE_EXTERNAL_STORAGE` is NOT requested).
- **System PDF Handler**: Registered for `android.intent.action.VIEW` and `android.intent.action.SEND`.
- **Memory-Safe Renderer (`android.graphics.pdf.PdfRenderer`)**: Handles 200 MB+ PDFs smoothly on 2 GB RAM devices with an `LruCache` memory ceiling.

---

## 🏗️ Architecture Overview

- **Pattern**: Single-Activity, Model-View-ViewModel (MVVM) architecture with Clean separation of concerns.
- **UI Framework**: 100% Jetpack Compose with Material 3 (M3) dynamic color theming.
- **Data Persistence**: Room 2.7.0 (with KSP) exposing reactive Kotlin `Flow<List<RecentPdfEntity>>` and `Flow<List<BookmarkEntity>>`.
- **Concurrency & Services**: Kotlin Coroutines (`viewModelScope`, `Dispatchers.IO`) and Android Foreground Service (`PdfOperationService`) with `dataSync` type on Android 14+.
- **Target & Min SDK**:
  - `minSdk`: 26 (Android 8.0 Oreo) - strictly maintained.
  - `targetSdk`: 36 (Android 16 / latest stable)
- **Offline Guarantee**: 100% on-device processing. No external API keys or trackers.

---

## 📜 Libraries & Permissive License Attributions

This project exclusively utilizes **permissively licensed** libraries (Apache 2.0 / MIT / BSD). **Zero AGPL or GPL libraries** (such as iText or MuPDF) are included or referenced.

| Library / Component | Purpose | License |
| :--- | :--- | :--- |
| **PdfBox-Android** (`com.tom-roush:pdfbox-android`) | Native text extraction, outline, search, merge, split, and standard annotations | **Apache License 2.0** |
| **Android Jetpack Compose** (`androidx.compose.*`) | Declarative UI toolkit & animation | **Apache License 2.0** |
| **Material 3 (M3)** (`androidx.compose.material3`) | Material You design components & color system | **Apache License 2.0** |
| **AndroidX Room Persistence** (`androidx.room.*`) | SQLite abstraction for recents & bookmarks | **Apache License 2.0** |
| **Kotlin Coroutines & Flow** (`kotlinx.coroutines.*`) | Asynchronous background processing | **Apache License 2.0** |
| **AndroidX Core KTX & Activity** (`androidx.core`, `activity-compose`) | Android platform bindings & edge-to-edge | **Apache License 2.0** |
| **Android Lifecycle** (`androidx.lifecycle.*`) | Lifecycle-aware ViewModels and StateFlow collection | **Apache License 2.0** |
| **Android DocumentFile** (`androidx.documentfile`) | SAF tree and document writing | **Apache License 2.0** |
| **Android PdfRenderer SDK** (`android.graphics.pdf`) | Native on-device PDF rasterizer | **Apache License 2.0** |
| **Android TextToSpeech** (`android.speech.tts`) | System voice synthesis engine | **Apache License 2.0 / AOSP** |

---

## 🔒 Security & Privacy

- **Zero Broad Permissions**: Operates using the Android Storage Access Framework (`OPEN_DOCUMENT`, `CREATE_DOCUMENT`, `OPEN_DOCUMENT_TREE`).
- **100% On-Device Processing**: Document texts, search queries, notes, and operations never leave the device.

# Claude Photo Sorter (Android)

Native Android photo sorter built with Kotlin, Jetpack Compose, and on-device Google ML Kit Image Labeling.

## Included in this initial version
- Pick multiple images through Android's system photo/document picker.
- Analyze images locally and suggest broad categories.
- Show ML Kit labels for transparency.
- Detect exact duplicates in the current selection using SHA-256.
- Keep original files unchanged. No automatic deletion.

Categories are heuristic groupings of ML Kit's visual labels. ML Kit does not identify family relationships or travel intent reliably. Review suggestions before acting.

## Build
1. Open this repository in Android Studio.
2. Install Android SDK 35 and use JDK 17.
3. Sync Gradle and run the app on Android 8.0 or later.

This repository contains source code, not a prebuilt APK. Build and test on a device before using it with important photos.

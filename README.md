# Claude Photo Sorter (Android)

Native Android photo organizer built with Kotlin, Jetpack Compose, Android Storage Access Framework, and on-device Google ML Kit Image Labeling.

## Features
- Pick multiple images through Android's system picker.
- Analyze images locally and suggest broad categories.
- Browse results by tapping category filter chips.
- Copy analyzed images into category-named folders under a folder you choose.
- Resolve same-name files by adding a numbered suffix.
- Show ML Kit labels and exact duplicate matches (SHA-256) for the current selection.
- Original files are never moved, modified, or deleted.

Categories are heuristic groupings of ML Kit's visual labels. ML Kit does not identify family relationships or travel intent reliably. Review suggestions before sorting. Sorting copies every analyzed image, including duplicate copies, into the chosen destination.

## Build
1. Open this repository in Android Studio.
2. Install Android SDK 36 and use JDK 17.
3. Sync Gradle and run the app on Android 8.0 or later.

The GitHub Actions workflow builds a debug APK and uploads it as an artifact. Build and test on a device before using it with important photos.

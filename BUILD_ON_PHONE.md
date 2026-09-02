# GIF2TinyMP4 — build package

This project is intentionally dependency-light: it uses the Android SDK APIs and Java only.

## Android Studio / Android IDE

1. Extract the ZIP.
2. Open the `Gif2TinyMp4` folder as an existing Gradle project.
3. Use JDK 17.
4. Ensure Android SDK 35 is installed.
5. Build `app` → `assembleDebug`.
6. Install `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub cloud build (recommended if building only on a phone)

1. Create a GitHub repository and upload the contents of this folder.
2. Open **Actions**.
3. Select **Build APK**.
4. Run it manually with **Run workflow**.
5. When it finishes, open the workflow run and download `Gif2TinyMp4-debug`.

The workflow installs Gradle 8.9 and Android SDK 35 itself, so the repository does not need a Gradle wrapper JAR.

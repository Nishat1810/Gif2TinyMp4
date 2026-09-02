# GIF2TinyMP4

A small Android app that receives GIFs directly from a compatible keyboard such as Gboard, converts them locally to compact H.264 MP4, and saves/shares the result.

## Product goal

The app is **not** intended to reproduce WhatsApp's exact encoder. The priority is a very small file that remains reasonably watchable and can be sent quickly over slow connections.

## Current behavior

- Gboard/content-commit GIF input
- Local/offline conversion
- H.264/AVC MP4
- 10 FPS target
- No audio
- Even dimensions for YUV420 compatibility
- Adaptive low bitrate
- Save to `Movies/GIF2TinyMP4`
- Share through Android's share sheet

## Build

- Compile/target SDK: 35
- Min SDK: 29
- JDK: 17
- Android Gradle Plugin: 8.7.3
- Gradle: 8.9

The GitHub Actions workflow can build the APK without a checked-in Gradle wrapper.

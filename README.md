# Comix

Native Android reader for Comix Originals and comics you already own.

Package `to.comix.app`. Kotlin, Jetpack Compose, Material 3. Min SDK 26.

## What it does

- Home, browse, library, and a full-screen reader
- Vertical scroll for manhwa and manhua, paged right-to-left for manga, paged left-to-right for comics
- Continue reading, follows, and page progress stored on the device
- Open a CBZ or ZIP of page images. The archive is unpacked into app storage and never uploaded
- Six original series ship inside the app

## What it does not do

This is not a client for the comix.to website. That site hosts other people’s chapters. Comix does not call its API, scrape it, or rehost it.

## Build

Open the project in Android Studio (JDK 17) and run the `app` configuration.

From the command line, with the Android SDK installed:

```bash
./gradlew assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions on `main` builds the same debug APK.

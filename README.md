# On This Day — Photos

An Android app that shows all photos taken on today's date in previous years,
grouped by year. A folder picker (toolbar menu) lets you persistently filter
which folders (Camera, Downloads, Pictures, etc.) are included.

## Requirements

- Android 8.0 (API 26) or higher
- Targets Android 15 / API 35
- Tested on Pixel 7 Pro running Android 16

## Getting Started

### 1 — Clone / drop into a Git repo

```bash
git init
git add .
git commit -m "Initial commit"
```

### 2 — Add the Gradle wrapper JAR

The `gradle-wrapper.jar` binary is not included in source control.
Run **one** of the following to generate it:

**Option A — you have Gradle installed locally**

```bash
gradle wrapper --gradle-version 8.6 --distribution-type bin
```

**Option B — use Android Studio**

Open the project in Android Studio; it will automatically download the
wrapper and sync the project.

**Option C — use the GitHub Actions CI (see below)**

The CI workflow uses `actions/setup-java` and calls `./gradlew` directly.
GitHub's runner already has a JDK and can bootstrap the wrapper from the
properties file automatically when `gradle-wrapper.jar` is present.
To add the jar without a local Gradle install:

```bash
# Download the jar via curl (run once after cloning)
curl -Lo gradle/wrapper/gradle-wrapper.jar \
  https://github.com/gradle/gradle/raw/v8.6.0/gradle/wrapper/gradle-wrapper.jar
```

Then commit it:

```bash
git add gradle/wrapper/gradle-wrapper.jar
git commit -m "Add gradle wrapper jar"
```

### 3 — Build locally

```bash
chmod +x gradlew
./gradlew assembleRelease
# APK → app/build/outputs/apk/release/app-release-unsigned.apk
```

### 4 — CI (GitHub Actions)

Push to `main` or `master`. The workflow at
`.github/workflows/build.yml` will:

1. Check out the code
2. Set up JDK 17
3. Run `./gradlew assembleRelease`
4. Upload `app-release-unsigned.apk` as a downloadable artifact
   (retained for 30 days)

## Signing the APK for sideloading

```bash
# Generate a key (once)
keytool -genkey -v -keystore my-release-key.jks \
  -alias onthisday -keyalg RSA -keysize 2048 -validity 10000

# Sign the unsigned APK
apksigner sign \
  --ks my-release-key.jks \
  --out app-release-signed.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

## Project Structure

```
OnThisDay/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/onthisday/app/
│       │   ├── data/
│       │   │   ├── MediaRepository.kt   # MediaStore queries
│       │   │   ├── Photo.kt             # Data models
│       │   │   └── Prefs.kt             # SharedPreferences helper
│       │   └── ui/
│       │       ├── MainActivity.kt
│       │       ├── MainViewModel.kt
│       │       ├── GalleryAdapter.kt
│       │       └── PhotoViewActivity.kt
│       └── res/                         # Layouts, themes, strings
├── .github/workflows/build.yml          # CI pipeline
├── build.gradle
├── settings.gradle
└── gradlew
```

## Permissions

| Permission | Reason |
|---|---|
| `READ_MEDIA_IMAGES` (API 33+) | Read photos from device storage |
| `READ_EXTERNAL_STORAGE` (API ≤ 32) | Legacy fallback |

## Features

- Displays all photos taken on today's month/day across all previous years
- Photos grouped by year with a bold year header and photo count
- 3-column square-thumbnail grid
- Tap a photo to view it full-screen
- Folder filter: persistent tick-list of all image folders found on device
- Light and dark theme support (follows system setting)
- Material 3 design

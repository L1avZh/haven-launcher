# Building from source

## Requirements

- JDK 17 or newer. The project is built with JDK 21.
- Android SDK with platform 35 (build-tools 35.0.0 worked here; Gradle fetches missing components when the SDK licenses are accepted). Point `ANDROID_HOME` at it, or create `local.properties` with `sdk.dir=...` (that file is git-ignored).
- Python 3, only if you want to regenerate the language files.

The Gradle wrapper (Gradle 9.8) and Android Gradle Plugin 9.5 download themselves.

## Build

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

The release build uses R8 shrinking and is signed with the standard debug key so it installs without extra setup. This is fine for personal use and testing; for a store build create your own keystore and signing config.

Other useful tasks:

```bash
./gradlew lintRelease       # report in app/build/reports/
./gradlew assembleDebug
```

## Package and versions

| | |
|---|---|
| Application id | `dev.l1avzh.haven` |
| minSdk / targetSdk / compileSdk | 26 / 34 / 35 |
| Language | Kotlin, plain Android views (no Compose, no AppCompat) |
| Dependencies | `androidx.recyclerview:recyclerview:1.4.0` (Kotlin stdlib comes with the build) |

## Languages

The string resources for all 50 languages are generated:

```bash
python3 tools/gen_i18n.py
```

It reads `tools/i18n_data_*.py` and rewrites `app/src/main/res/values-*/strings.xml`, `app/src/main/res/xml/locales_config.xml`, `Langs.kt` and `TRANSLATIONS.md`. It checks that every language has every string and that placeholders such as `%1$s` are present. Edit the data files, not the generated XML.

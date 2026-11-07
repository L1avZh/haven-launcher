<p align="center">
  <img src="docs/images/banners/banner.png" alt="Haven" width="720">
</p>

<h1 align="center">Haven</h1>

<p align="center">
  A fast, ad-free launcher for Android TV. No accounts, no tracking. The network is used only by an optional weather widget.
</p>

<p align="center">
  <a href="https://github.com/L1avZh/haven-launcher/actions/workflows/build.yml"><img alt="Build" src="https://github.com/L1avZh/haven-launcher/actions/workflows/build.yml/badge.svg"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white">
  <img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-blue">
  <img alt="Languages" src="https://img.shields.io/badge/Languages-50-orange">
</p>

Haven replaces the usual Android TV home screen with something quieter: a typographic list of your apps on the left, and a stage on the right with big artwork and the name of whatever is focused. Behind it, a live background drifts in the colors of the focused app's own artwork. It is written in plain Kotlin views with a single dependency (`androidx.recyclerview`), has one permission (internet, for the optional weather widget), and makes no network request unless you choose a weather city.

I built and tested it on a Xiaomi TV Box S (3rd Gen) running Android 14. Other Android TV devices should work, but I haven't tried them (see [Known limitations](#known-limitations)).

<p align="center">
  <img src="docs/images/screenshots/home.png" alt="Home screen" width="860">
</p>

## Screenshots

All screenshots were captured on the TV itself.

| | |
|---|---|
| ![Scope](docs/images/screenshots/scope.png) | ![Widgets](docs/images/screenshots/widgets.png) |
| Scope header with a moving light sweep | Widgets: choose what shows where |
| ![Settings](docs/images/screenshots/settings.png) | ![Applications](docs/images/screenshots/applications.png) |
| Settings: categories, Appearance open | Applications: manage, hide, sort, shortcuts |
| ![Remote control](docs/images/screenshots/remote-control.png) | ![Languages](docs/images/screenshots/language.png) |
| Remote control: button test, mappings, input devices | Language picker (native names) |
| ![Hebrew](docs/images/screenshots/hebrew-rtl.png) | ![Display](docs/images/screenshots/display.png) |
| Hebrew, right-to-left | Display & animation |
| ![AdGuard](docs/images/screenshots/adguard.png) | ![Privacy](docs/images/screenshots/privacy.png) |
| AdGuard status page | Privacy & security |
| ![About](docs/images/screenshots/about.png) | ![Easter egg](docs/images/screenshots/easter-egg.png) |
| About & credits | The hidden signature |

<p align="center">
  <img src="docs/images/animations/easter-egg.gif" alt="Easter egg animation" width="560"><br>
  <sub>The hidden signature, recorded on the TV.</sub>
</p>

## Features

- **Home screen ("Stage & Rail")**: three scopes, Favorites, Recent and Library, as a list of app names. Each app name uses one of five typefaces, so the list has character. The stage shows large artwork and actions for the focused app. When a scope tab has focus instead of an app, the stage shows the scope's name with a moving light sweep.
- **Live background**: soft color fields and drifting motes that take their palette from the focused app and speed up briefly when focus changes. Rendered at low resolution at 30 fps and paused when not visible; static in performance mode.
- **Widgets**: choose what appears on the home screen (up to 3) and on the screensaver (up to 4): greeting, weather, share of the day, share of the year, moon illumination, free storage, free memory, uptime. Everything except weather is computed on the device. Weather is opt-in: pick a city in Settings and Haven fetches the current temperature and sky from open-meteo.com over HTTPS every 30 minutes. Only that city's coordinates are sent (no key, no account, no device identifier, no location access). The icon is animated, and rain, snow and storms also fall across the whole background.
- **Screensaver**: after 2 to 20 minutes without input (or off), a black screen with the live background, a drifting clock and your screensaver widgets. The clock moves every 25 seconds to spare OLED panels. Any button wakes it. It lives inside Haven and changes no system setting.
- **Typefaces**: Outfit for the interface, Unbounded for the clock and big titles, Space Grotesk, Manrope, Sora and Urbanist for app names. All bundled, all SIL Open Font License (see `docs/OFL-*.txt`).
- **App management**: favorites, hide apps without uninstalling them, reorder favorites, show or hide system apps, sort A to Z or newest first, refresh the list. New and removed apps are picked up automatically.
- **Settings**: a full-screen page with ten categories (General, Appearance, Widgets, Applications, Remote control, Language & region, Display & animation, AdGuard, Privacy & security, About & credits). Everything is reachable with a D-pad, OK and Back.
- **Appearance**: six wallpapers, five accent colors plus auto, background dimming, three artwork sizes, 12 or 24-hour clock, screensaver delay.
- **Motion**: focus scaling, an entrance animation, glow cross-fades, fading menus. Animation can be full, subtle or off, and there is a performance mode. The system animator scale is respected.
- **50 languages** with native names in the picker and a System default option. Hebrew, Arabic, Persian and Urdu are laid out right-to-left. See [docs/localization.md](docs/localization.md) for what is and isn't verified.
- **Remote control tools**: a button tester that shows what Android actually receives (key, code, device, source, long press), custom button mappings, number keys that open favorites, and an input device list. Details in [docs/remote-control.md](docs/remote-control.md).
- **AdGuard shortcut**: if the official AdGuard app is installed, it shows up like any other app. If not, a tile points to AdGuard's official page. Haven never installs it, never changes VPN or DNS settings, and can't tell whether AdGuard's protection is on.
- **Privacy**: one permission (internet), used only by the optional weather widget and only after you pick a city. No analytics, no ads, no accounts. Preferences live in app-private storage.
- **A hidden signature**: there is an Easter egg. One way to find it is in About & credits.

## Requirements

- Android 8.0 (API 26) or newer. Built against API 35, targeting API 34.
- A D-pad remote. No touch or mouse is needed anywhere.
- Tested on: Xiaomi TV Box S (3rd Gen), Android 14. That is the only device I have verified.

## Install

Download `haven-<version>.apk` from the [Releases](../../releases) page, then:

```bash
adb connect <tv-ip>:<port>        # wireless debugging, see docs/installation.md
adb install -r haven-<version>.apk
adb shell am start -n dev.l1avzh.haven/.MainActivity
```

Haven registers a TV launcher entry, so it should also appear in the TV's app list. More detail, including how to make it the Home app, is in [docs/installation.md](docs/installation.md).

> Release APKs are signed with an ordinary debug key. If you build the app yourself you will have a different key, and Android will refuse to install it over the release APK. Uninstall first in that case.

## Build from source

You need JDK 17 or newer (I use 21) and the Android SDK with platform 35 and build-tools. Then:

```bash
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Full steps are in [docs/building.md](docs/building.md).

## Remote control

Haven works with any remote that sends ordinary D-pad, OK and Back keys.

I use an LG Magic Remote with the box connected over HDMI. The TV forwards only a few buttons to the box (HDMI-CEC): the four arrows, OK and Back. I captured the box's key log while pressing every button; the digits, color keys, app buttons, Assistant, Home and volume never arrived. The page [docs/remote-control.md](docs/remote-control.md) has the table, what that means for getting back to Haven from another app, and the things that are not possible without Accessibility access.

## Project layout

```
app/src/main/java/dev/l1avzh/haven/
  MainActivity.kt   home screen, settings content, key handling
  SettingsUi.kt     full-screen settings: categories, rows, switches
  Ui.kt             tiles, icon loader, shared menu dialog, layout helpers
  Tokens.kt         colors, type, spacing, shape and motion values
  HomeUi.kt         app rail, stage buttons, scope hero
  LiveBackground.kt animated background
  Saver.kt          screensaver
  Widgets.kt        widget definitions and strip
  Model.kt          app discovery and preferences
  Remote.kt         key tester and mapping overlay
  Egg.kt            the Easter egg animation
  Lang.kt           per-app language and script-aware text helpers
  Langs.kt          generated language list
app/src/main/res/   strings for 50 languages, vector icons, locale config
tools/              translation data and generator, haven-home.sh
docs/               guides and images
```

## Known limitations

- Only tested on one device, one Android version, and one remote.
- The firmware on my box ignores a third-party Home app: the Home button still opens Google's launcher. Haven registers as a Home app, but that is up to the device.
- With an LG remote over HDMI-CEC, only arrows, OK and Back reach the box. There is no remote shortcut that works while another app is in front.
- There is no floating Home button. An overlay can't be reached with a D-pad without taking input away from the app underneath.
- Strings added in the latest redesign (screensaver, widgets, greetings) exist in English only and appear in English in the other 49 languages.
- Home, scope, widgets and settings screenshots show the current design. The other screenshots were taken before the redesign and need retaking.
- Translations are machine-assisted drafts. None have been checked by a native speaker.
- Custom wallpapers from files, custom categories and settings export are not implemented.

## Roadmap

- Native review of the translations, starting with the lower-confidence languages.
- A real device matrix: more Android TV and Google TV boxes.
- Settings export and import.
- Optional categories for the library.

## License

MIT, see [LICENSE](LICENSE). Dependencies: AndroidX RecyclerView and the Kotlin standard library, both Apache License 2.0. Bundled fonts (Outfit, Unbounded, Space Grotesk, Manrope, Sora, Urbanist): SIL Open Font License 1.1, texts in `docs/`. AdGuard is a trademark of its owner and this project is not affiliated with it. App names and artwork shown in screenshots belong to their owners.

## Credits

Made By [L1avZh](https://github.com/L1avZh).

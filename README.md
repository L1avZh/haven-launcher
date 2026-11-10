# Haven

A fast, ad-free launcher for Android TV. No accounts, no tracking; the only network use is an optional weather widget.

![Haven's home screen: a 3D character on the left, the focused app in the middle, the app list on the right](docs/images/screenshots/home.png)

Haven replaces the usual Android TV home screen with something quieter: a 3D character in a small scene that follows the time of day and the weather, big artwork and the name of whatever is focused in the middle, and a typographic list of your apps at the end. Plain Kotlin views plus Google Filament for the 3D; one permission (internet, for the weather widget, used only after you pick a city).

## Highlights

- **Stage and rail home screen** with Favorites, Recent and Library, live background colors taken from the focused app, and full D-pad and remote control.
- **A 3D character** (Human, Wizard or Robot) with outfits and props you choose in the Character Studio. It reacts to the remote, sleeps in a bed at night, and holds an umbrella in rain, snow, storms and cold.
- **Day and night** (automatic, or always day / always night) with a sunny or starlit scene, 3D rain and snow.
- **Widgets** for the home screen and the screensaver: greeting, weather, day and year progress, moon, storage, memory, uptime.
- **Screensaver** that moves every 25 seconds to spare OLED panels.
- **50 languages** (right-to-left included), reduced-motion and performance modes.

## Install

1. Download `haven-<version>.apk` from the [Releases](../../releases) page.
2. Install it on your Android device or TV (for example with `adb install -r haven-<version>.apk`, or from a file manager).
3. Open Haven; to use it as your Home app see [docs/installation.md](docs/installation.md), which also covers wireless debugging.

You can give an AI assistant this GitHub repository link and ask it for installation steps for your specific Android device.

Requires Android 8.0 or newer and a D-pad remote. I have tested it on one device only, a Xiaomi TV Box S (3rd Gen) on Android 14.

## More

[Building](docs/building.md) · [Architecture](docs/architecture.md) · [Characters and scene](docs/character.md) · [Day, night and weather](docs/day-night.md) · [Settings](docs/settings.md) · [Remote control](docs/remote-control.md) · [Known limitations](docs/limitations.md) · [Localization](docs/localization.md) · [Asset pipeline](docs/assets/README.md) · [Changelog](CHANGELOG.md)

## License and credits

MIT, see [LICENSE](LICENSE). The 3D models (characters, props, bed, scenery) are CC0 works by [Quaternius](https://quaternius.com) and TCCDev, modified by this project, and are not MIT-licensed; sources and changes are in [docs/assets/ASSET_CREDITS.md](docs/assets/ASSET_CREDITS.md). Other dependencies: AndroidX RecyclerView and Google Filament (Apache 2.0), bundled fonts (SIL OFL 1.1). AdGuard is a trademark of its owner and this project is not affiliated with it. App names and artwork in the screenshot belong to their owners.

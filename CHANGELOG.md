## 1.6

- New home composition: a rigged 3D character ("the Warden") on the start side, the stage in the middle and the app rail at the end. Character Studio for outfits and props, input-driven reactions, saved locally. Adds Filament (about +10 MB, native libraries for armeabi-v7a and arm64-v8a).
- "Haven Aurora" visual pass: obsidian ground with cyan/violet aurora light that blends with the focused app's accent, glass tabs and capsule focus on the rail, a lit artwork card with a soft halo, restyled settings and menus, press feedback on OK, and a slide-in when the scope changes.
- Fast D-pad bursts on the app rail now land exactly where expected, and a crash when focus reached a scope tab mid-scroll is fixed.
- All widget, screensaver and weather strings translated into the 50 languages; the weather widget shows the city name in the app's language.
- Weather widget (opt-in, Settings → Widgets → Weather city): animated icon, plus rain, snow and storm falling across the background. Adds the `INTERNET` permission; requests go only to open-meteo.com and only carry the chosen city's coordinates.
- Widgets for the home screen and the screensaver, chosen in Settings → Widgets.
- Scope hero (animated scope name) when a tab has focus instead of an app.
- Per-app typefaces in the rail and stage title (Outfit, Space Grotesk, Manrope, Sora, Urbanist).
- Screensaver (Settings → Appearance): drifting clock over the live background, starts after 2–20 min idle, any button wakes it.
- Live animated background that follows the focused app's colours.
- Unbounded display face for the clock and stage title (SIL OFL, `docs/OFL-Unbounded.txt`).
- New "Stage & Rail" home: typographic app rail with a stage showing artwork for the focused app, Favorites/Recent/Library scopes, Outfit typeface.

# Changelog

## 1.5
- "Go to Home" action for custom mappings, and long-press bindings: hold a button while learning it to bind a long press (a quick Back still cancels; hold Back to bind Back).
- A bound long press of OK takes priority over the long-press app menu.
- Mappings list shows long presses ("DPAD_CENTER · Long press").
- Translations for the three new strings in all 50 languages.

## 1.4
- Full-screen Settings with nine categories, row descriptions and animated switches.
- Shared dialog style with fade in and out.
- New settings: accent color, background dimming, card size, time format, system app visibility, sort order, animation level, performance mode, return to start on Home, restore defaults.
- Manage applications list.
- AdGuard page with a real installed-package check.
- Privacy page with live counts of what is stored.
- About & credits page with the developer credit, licences and a hidden entry to the Easter egg.
- 92 new strings in all 50 languages.

## 1.3
- Remote control settings: button test, custom mappings, number keys open favorites, input device list.
- Home chip in the header.
- Documented what an LG Magic Remote delivers over HDMI-CEC.

## 1.2
- 50 languages, per-app language, right-to-left layout.
- Hidden signature animation.
- AdGuard shortcut tile.

## 1.1
- Redesigned home screen: hero title, favorites and library shelves, per-app accent glow, motion system.

## 1.0
- First working launcher: app discovery, favorites, hide, search, wallpapers.

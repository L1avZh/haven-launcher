# Changelog

## Unreleased
- Screensaver redesign: the home screen's bundled landscape (darker, calmer, tinted by the wallpaper) replaces the dark gradient and floating dots; the clock, date and your widgets keep their layout and order; the selected character now stands on the end side by day (or sleeps on the bed at night, as before) with a soft contact shadow and no rain or snow particles. Anti-burn-in motion: the text block still hops every 25 s, the photo glides a few percent each 25 s, and the figure shifts slightly; reduced motion makes the glide barely perceptible and the character still. The setting is now worded "Character on screensaver" (same `saver_char` key, 50 languages). The ground disc under the bed is gone too.
- Home screen polish: a bundled Unsplash landscape (Nguyen Bui, Unsplash License; 190 KB WebP, offline) replaces the blurred gradient as the backdrop, tinted by the wallpaper colour, washed darker at night, with soft scrims behind the header, the shelves and the character (the Black wallpaper still gives plain black). The large preview card is gone, so a focused app's artwork appears once (as the enlarged card in its shelf) with its name, last-opened line and actions above it; Recent hides apps already on the Favorites shelf and folds away when nothing is left. The diorama (tree, bush, grass, ground disc, sun, moon) is no longer drawn; the character stands on a contact shadow. Header buttons are darker glass, widgets slightly lighter, the accent glow fainter, accent text lightened for contrast. Card sizes are now 260/300/340 px.
- Home screen redesign: the vertical typographic rail and scope tabs became three shelves of artwork cards (Favorites first, then Recent and the Library; two show at a time and the stack slides to the third) under a hero with the focused app's artwork, name, last-opened line and the same Open, Favorite and More actions. The character stays on the start side beside the shelves in every state. Left/Right move along a shelf, Up/Down move between shelves (empty ones are skipped and show a one-line hint), Up from the first shelf reaches the hero actions, OK on a card launches it and long OK opens its menu as before. Cards load artwork lazily at card size through the shared loader (its queue grew from 8 to 64 so visible cards are never dropped). Card size in Settings now sizes the shelf cards. Removed the centered "Browsing …" waiting label (its two strings are now unused). Favorites, Recent, hidden apps, sorting and the number-key shortcuts are unchanged.
- Settings redesign: the 11 categories became 6 task-based sections (Home screen; Character & atmosphere; Applications; Remote & Home app; System; About & credits). Appearance and Display merged; AdGuard moved under Applications; Widgets became a summary page with "Home" and "Screensaver" subpages, with the weather city and unit right under the Weather switch; Privacy and Language moved under System; Reset All now exists once (System). Weather city and the night hours use a list picker with the current value highlighted instead of cycling with OK; night start/end only appear in Automatic mode. Back leaves a subpage, then the rows, then Settings; focus returns to the row you came from, also after the dialogs and after the activity restarts for card size, the character switch or a language change. No preference key, default or value changed; the only new key is `settings_section` (the old `settings_cat` indexed the previous list and is ignored). 9 new strings in 50 languages (machine-drafted, not native-reviewed). Inventory and old-to-new map: [docs/settings.md](docs/settings.md).
- Default Home app: Settings > General shows whether Android lists Haven as the Home app. Android 10+ uses the system consent dialog for the Home role; Android 8 and 9 (or a missing role) open the system Home or default-apps screen; devices with neither get manual steps. The state is re-read when you return, never assumed. New strings in 50 languages and `HomeRoleTest`. The manifest filters are unchanged; a HOME `<queries>` entry was added so the current handler can be read.
- Pose composition: explicit arm ownership (a prop or the umbrella owns the right arm, the weather pose owns the left); gestures are dropped on an owned arm instead of fighting it, while head and eyes still react. Swapping the saved prop goes through an empty hand. The umbrella got its own grip row (`PropGrips.UMBRELLA`) and a smaller canopy. Filament assets are destroyed if loading fails partway; entity bookkeeping is cleared when the engine is destroyed.
- Pose and props: props are now baked with their own grip (shaft along the knuckle row) and carried with a per-prop arm pose and closing fingers; sockets are computed from each model's hand. One-handed gestures move to the free hand while the right one carries a prop; the waves were replaced by a nod and an open hand. The prop, umbrella and bed scale with the pose weights (no popping), with a clear priority (sleep, then weather umbrella, then the saved prop). Sleeping is a new sequence: the bed grows under the standing character, who lies on their back on the mattress with the head on the pillow, knees softly bent and arms resting; waking reverses it. The camera follows. New policy tests.
- Scene and props: a small 3D diorama behind the character that follows day, night and weather (sun, tree, bush and grass by day; moon at night; pines and 3D flakes in snow; 3D drops in rain and storms; the twin bed with pillow and blanket while sleeping), using CC0 models by Quaternius and TCCDev. Real 3D props: Sword, Axe, Candle, Book and the wizard Staff (the old Lantern, Tablet and Compass are gone; saved ones fall back to the default). The Canvas rain and snow lines were removed. One shared `world.glb` is loaded on demand.
- Fixes: a failed weather request was retried every minute tick; it is now retried at most every 5 minutes. Broadcast receivers are registered not-exported. The unused `warden.glb` (0.8 MiB) was removed.
- README cut down to one screenshot and a short overview; details moved to `docs/` (new `docs/limitations.md`). Unused screenshots, the GIF and the banner were removed.
- Characters: the Human, Wizard and Robot are now built from CC0 models by Quaternius (Universal Base Characters and Modular Character Outfits – Fantasy) instead of procedural primitives: real skinned PBR characters with hair, hands and clothing, re-rigged to Haven's contract by a reproducible Node pipeline (`tools/assets`). The Wizard is an indigo hooded mage and the Robot an armored android (no wizard hat or robe exists in the free pack). Only the selected model is kept in memory. Props, umbrella, blanket, pillow and pendant remain Haven's own simple meshes. Credits in `docs/assets/ASSET_CREDITS.md`, README and Settings → About & credits. Eyes no longer blink.
- Day and night: Settings → Appearance → Day and night (Automatic / Always day / Always night) with an editable night window. Automatic prefers the fresh `is_day` flag Open-Meteo returns for the chosen city and falls back to the local clock. Home, Settings, the Studio and the screensaver get a brighter day sky or the deep night sky with a short cross-fade; it reacts to clock, time-zone and weather changes and to the app resuming.
- Character and weather: with a fresh reading, rain, snow, storms and cold (8 °C or below on the Celsius value) put an umbrella in the hand with a stance for each; no, stale or unconfigured weather changes nothing; the saved prop is restored afterward. At night the character sleeps under a blanket on a pillow (Settings → Character). Works for all three characters and with reduced motion (pose only, no breathing).
- Screensaver redesign: black base, dimmed live background and sky, clock, date, rule and responsive widget cards, optional sleeping character at night; everything moves together every 25 s.
- Widgets are now glass cards laid out for 1 to 3 (home) or 1 to 4 (screensaver) widgets with shrink-to-fit text, a progress bar for share widgets and the animated icon for weather. Stored widget lists are unchanged.
- The weather request now also asks for `is_day`; the cache format gains an optional fifth field and still reads old entries.
- New settings strings in all 50 languages. New unit tests for day/night, weather classification, scene precedence, stale and missing data, Celsius/Fahrenheit independence, cache and JSON, sleep and stance poses and widget width.
- The sky is a hardware layer: it keeps the home screen at about 0.1% janky frames on the TV with the character awake.

## 1.6

- Three characters (Human, Wizard, Robot) with their own outfits and props, a hidden accessory, themed reactions to music and video apps, a centered waiting pose with a localized "Browsing …" label when nothing is selected, and Settings polished with the home screen's tokens (selected states, header icon, fade transition).
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

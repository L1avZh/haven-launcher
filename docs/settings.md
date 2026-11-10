# Settings

Settings has six sections. Everything works with the D-pad, OK and Back: Back leaves a subpage, then the rows, then Settings. Rows that open a page show `›` (mirrored in right-to-left languages) and show their current value when they have one.

| Section | Contents |
|---|---|
| Home screen | Wallpaper, accent color, background dimming, card size, clock, **Widgets** (page), screensaver delay, animation, accent glow, performance mode |
| Character & atmosphere | Show character, Character Studio, sleep at night, weather reactions, sleeping character on the screensaver, day/night mode and (Automatic only) night start and end |
| Applications | Manage applications, hidden apps, show system apps, sort, refresh app list, **AdGuard** (page) |
| Remote & Home app | Default Home app status, return to start on Home, button test, custom mappings, number keys open favorites, return shortcut, input devices, floating-home note |
| System | **Language & region** (page: language, time format, text direction, languages available), **Privacy & security** (page), restore default settings (the only Reset All, with its one confirmation) |
| About & credits | Version, developer, licenses, model credits, Easter eggs |

Widgets: the summary page opens **Home** and **Screensaver** pages. Each lists the widgets as switches; the weather city (a list) and temperature unit sit directly under the Weather switch on both pages. Picking a city still turns the home weather widget on.

## Inventory (what was audited)

Preference keys and defaults are unchanged. "Kind": C = common, A = advanced, D = diagnostic, I = informational.

| Setting | Key (default) | Was in | Now in | Kind |
|---|---|---|---|---|
| Return to start on Home | `return_start_home` | General | Remote & Home app | C |
| Default Home app | none (reads Android) | General | Remote & Home app | C |
| Restore defaults | clears all | General and Privacy (twice) | System (once) | A |
| Wallpaper / accent / dimming / card size | `wallpaper`, `accent_mode` (0), `dimming` (0), `card_size` (1) | Appearance | Home screen | C |
| Clock | `clock` (on) | Appearance | Home screen | C |
| Screensaver | `saver` (2 = 5 min) | Appearance | Home screen | C |
| Animation | `anim_level` (2; old `reduce_motion` still maps to 0) | Display | Home screen | C (accessibility) |
| Accent glow / performance | `glow` (on), `performance` (off) | Display | Home screen | A |
| Day and night | `daynight_mode` (0 = Automatic) | Appearance | Character & atmosphere | C |
| Night starts / ends | `night_from` (19), `night_to` (6) | Appearance | Character & atmosphere, Automatic only | A |
| Show character, sleep, weather reactions, screensaver character | `char_enabled`, `sleep_night`, `weather_reacts`, `saver_char` (all on) | Character | Character & atmosphere | C |
| Home / screensaver widgets | `widgets_home`, `widgets_saver` | Widgets | Home screen > Widgets | C |
| Weather city / unit | `weather_city` (0), `weather_unit` (locale) | Widgets | Home screen > Widgets, under Weather | C |
| Apps: manage, hidden, system apps, sort, refresh | `hidden`, `show_system_apps`, `sort_mode` | Applications | Applications | C |
| App shortcuts | `bindings` | Applications (duplicate of Custom mappings) | Remote & Home app > Custom mappings | C |
| Button test, mappings, number keys, return shortcut, devices | `mappings_enabled`, `numbers_open_favs`, `bindings` | Remote control | Remote & Home app | C |
| AdGuard status, launch, notes | none | AdGuard | Applications > AdGuard | I |
| Language, time format | `time_format` (0); language by the system locale service | Language & region | System > Language & region | C |
| Text direction, languages available | read-only | Language & region | System > Language & region | I |
| Privacy facts | read-only | Privacy & security | System > Privacy & security | D |
| About, credits, eggs | `star_unlocked` | About | About | I |

## Old to new

General → Remote & Home app (+ restore defaults in System) · Appearance → Home screen (day/night → Character & atmosphere) · Display & animation → Home screen · Character → Character & atmosphere · Widgets → Home screen > Widgets · Applications → Applications · Remote control → Remote & Home app · Language & region → System · AdGuard → Applications · Privacy & security → System · About & credits → About & credits.

## Behavior notes

- Night start and end only appear when Day and night is Automatic; in Day or Night mode they are hidden, not disabled, because they have no effect there.
- Focus: after a picker or a changed setting the same row stays focused; after a dialog or a system screen it returns to the row that opened it; after the activity restarts (card size, show character, language) the same section, page and row are restored (kept in memory only, so it is lost if the process is killed, and Settings then reopens in its section).
- Translations of the 9 new strings are machine drafts, not native-reviewed ([TRANSLATIONS.md](../TRANSLATIONS.md)).
- Fix: removing the focused row used to hand focus to the first section card, which could reselect a section mid-change; Settings now ignores that transient focus.

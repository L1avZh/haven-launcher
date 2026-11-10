# Day and night, and the weather

Haven decides once whether it is day or night and what the weather asks of the character, and every screen uses that one answer.
The rules are pure Kotlin in `DayWeather.kt` (unit tested in `DayWeatherTest.kt`); `Mood.kt` applies them to the app.

## Day and night

Settings → Appearance → **Day and night**: *Automatic* (default), *Always day*, *Always night*, plus **Night starts** and **Night ends** (hour of the
day, default 19:00 to 06:00, wrapping past midnight; equal hours mean "never night").

*Automatic* works like this, in order:

1. If a weather city is chosen and its last reading is **fresh** (under 90 minutes old) and carries Open-Meteo's `is_day` flag, that flag decides.
   `is_day` comes in the same `current=` request the weather widget already makes (`temperature_2m,weather_code,is_day`); it needs no
   location permission and no additional network request.
2. Otherwise the local clock and the night window decide. This is a plain schedule, not an astronomical calculation.

It is re-evaluated when the app resumes, every minute (`ACTION_TIME_TICK`), on manual clock changes (`ACTION_TIME_CHANGED`), on time-zone changes
(`ACTION_TIMEZONE_CHANGED`), when a new weather reading arrives, and when any of these settings changes. Nothing is polled.

What changes: the home sky (a brighter blue with a warm sun glow by day; the deep obsidian ground with a cool moon and a few faint stars at night),
the Settings and Studio backgrounds, the screensaver sky (at reduced strength), the weather icon (sun or moon), and the character. The text colors
and layout are the same in both, so contrast does not depend on the mode. The change cross-fades over about 1.4 s and is instant with reduced motion.
The stars never twinkle and the sky is drawn into a hardware layer, so it costs one textured quad per frame.

Stored value `daynight_mode` is an int 0..2; anything else (or nothing, as on every install before this feature) reads as Automatic, so there is
nothing to migrate.

## The scene

The backdrop is a bundled landscape photograph (credits in [assets/ASSET_CREDITS.md](assets/ASSET_CREDITS.md)). By day it is left bright with a light wash of the
wallpaper color; at night the wash deepens and the 2D stars and moon glow return, cross-fading over 1.4 s (instantly with reduced motion). The plain Black wallpaper turns the
photo off. The 3D diorama (sun, moon, tree, bush, grass and the ground disc) is no longer drawn on the home screen, so the character stands on a soft contact shadow in front of
the photo; the ground disc returns only under the bed. Rain and storm: 3D drops fall in two scrolling layers. Snow: 3D flakes drift down. Sleeping: the twin bed with pillow and
blanket, the character lying on the mattress, and no weather (it sleeps indoors in spirit). Weather in the scene
follows **Weather reactions**, like the umbrella. The earlier 2D rain lines drawn on the background canvas were removed. With reduced motion the
particles hold still.

## The character

| Situation (priority, highest first) | Result |
|---|---|
| Night and *Sleep at night* is on | Sleeps: lies on its side on the bed's mattress with its head by the pillow, slow breathing. No umbrella, no reactions. |
| Thunderstorm (WMO 95 to 99), fresh reading | Umbrella, leaning into the wind, other arm up |
| Snow (71 to 77, 85, 86) | Umbrella, head tilted up, a palm held out |
| Rain, drizzle, freezing rain, showers (51 to 67, 80 to 82) | Umbrella, a hand out to check |
| Cold: 8 °C or below (and none of the above) | Umbrella, the other arm pulled in, a slight shiver |
| Anything else, no city chosen, no reading, or a reading older than 90 minutes | Nothing changes: the saved prop and the usual poses |

* The cold threshold is compared against the **Celsius** value that was stored from the provider, never against the displayed string, so switching
  the widget to Fahrenheit does not change the behavior.
* Sleeping wins over weather, so the character never sleeps with an umbrella. With *Sleep at night* off, weather shows at night too.
* The umbrella is a temporary override. The prop saved in the Studio is not touched and comes back when the weather clears or the character wakes.
* Settings → Character has **Sleep at night**, **Weather reactions** (turns the umbrella and stances off) and **Character on screensaver**.
* The umbrella and the bed come from `world.glb` and attach to whichever character is active, so this works for every one of them.
* With reduced motion the held poses (lying down, umbrella arm) still show; only the breathing, shiver and gusting are removed.
* When the umbrella is up the camera backs off and rises a little for head-room; when sleeping it comes in to frame a lying figure.

## The screensaver

The home screen's bundled landscape (credits in [assets/ASSET_CREDITS.md](assets/ASSET_CREDITS.md)) under a darker, calmer wash than the home screen's (darker still at night),
then one block on the start side: the clock (Unbounded), the date, a thin rule, and the widget cards (your saved widgets, in your order). With the character enabled and
*Character on screensaver* on, the selected character stands on the end side (right in left-to-right languages), or lies on the bed at night when *Sleep at night* is on. It has
no falling rain or snow here and a soft contact shadow instead of a ground patch. The floating dots are gone. Nothing stays lit in one place: the text block fades and moves to a
new spot every 25 seconds, the photograph glides a few percent one way and back over each 25 seconds, and the figure shifts a few pixels. With reduced motion the glide is
barely perceptible and the character holds a still pose; performance mode shortens the glide and lowers the character's frame rate. Any key wakes it. The timeout, wake-up and
lifecycle are unchanged: no wake lock is taken, and the screensaver creates its own character view only while it is on screen and destroys it when it closes (the home character is paused meanwhile).

## Testing without waiting for the weather

Debug builds (`android:debuggable`) accept intent extras that are ignored by release builds:

```bash
adb shell "am start -n dev.l1avzh.haven/.MainActivity --es haven_weather '61|9|1' --es haven_daynight 1"
```

`haven_weather` is `code|celsius|isDay` (isDay `1`, `0` or empty), `off` clears it; `haven_daynight` is `0`, `1`, `2` or `off`;
`haven_character` picks human, wizard or robot; `--ef haven_yaw <deg>` turns the character to inspect poses from the side; `--ez haven_saver true` opens the screensaver. They are stored under `dbg_*` preference keys, which a
release build never reads.

## What was and was not verified

* Unit tests: modes, schedule wrap, provider-over-clock, fresh/stale/NaN readings, thresholds, precedence, Celsius independence, scene rules, cache
  format with and without `is_day`, JSON with and without `is_day`, sleep and stance poses including reduced motion, widget width for 1 to 4 cards.
* On the TV (Xiaomi TV Box S 3rd Gen, Android 14): the release build went to sleep by itself on the automatic night rule (the city's `is_day` was
  false) and woke through the Settings switch; Settings rows and the Day/Night, Sleep and Weather switches work with a D-pad; the three
  characters were observed in each weather stance and asleep, on a debug build with forced readings; the screensaver was opened with 4 widgets, by day
  and night; 1, 2 and 3 widgets on the home screen.
* Not verified: real rain, snow or cold in the weather city (forced readings were used); a time-zone change on the TV; widgets at the largest font
  scale or in right-to-left languages after this change (the cards use start/end-agnostic layout, but I did not capture them).

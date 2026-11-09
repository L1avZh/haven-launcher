# Haven's 3D characters

Three rigged and skinned 3D characters (**Human**, **Wizard** and **Robot**), built from CC0 models by Quaternius, are rendered on the home screen with
Google's Filament. They react to the remote, change outfit and prop in the Character Studio, and the look is kept between restarts.
Everything is local: no accounts, no downloads, no extra permissions.

## What is in the box

| Piece | Where |
|---|---|
| Models (GLB, 1.7 to 3.6 MiB each) | `app/src/main/assets/character/{human,wizard,robot}.glb` |
| Model build pipeline (Node, from the CC0 downloads) | `tools/assets/build.mjs`, see [assets/README.md](assets/README.md) |
| Credits and provenance | [assets/ASSET_CREDITS.md](assets/ASSET_CREDITS.md), `assets-source/quaternius/PROVENANCE.md` |
| v1 procedural generator (props only now) | `tools/character/gen_character.py` |
| Roster, input-to-reaction mapping, poses | `CharacterLogic.kt` (pure Kotlin, unit tested) |
| App kind classification | `AppKinds.kt` (pure Kotlin, unit tested) |
| Renderer and lifecycle | `CharacterView.kt` (the only file that touches Filament) |
| Studio screen | `CharacterStudio.kt` |
| Saved look | preferences `char_id`, `char_outfit`, `char_prop`, `char_enabled`, `star_unlocked`, `star_worn` |

## The models

All three share one skeleton (Quaternius' UE-style humanoid rig, 65 joints including fingers), so one pose system drives them. Haven drives 19 named
joints and the rest follow.

* **Human**: Universal Base Characters head with gray hair and beard, in the *City* (Male Peasant: linen shirt, trousers, boots) or *Field* (Male Ranger:
  hooded leather and cloth jerkin, bracers, boots) outfit.
* **Wizard**: the same head with long white hair and beard, in indigo-tinted versions of the Ranger (*Archmage*, hooded) and Peasant
  (*Apprentice*) outfits, with a staff. **There is no wizard hat or full robe**: the free pack contains neither (the Wizard outfit is in the paid
  tier), so this is a hooded sorcerer rather than a classic wizard.
* **Robot**: an armored android: the same head and the Ranger armor pieces without the hood, in solid metal (*Plated* silver, *Stealth* black) with
  glowing eyes. It is a recolored, re-materialed use of the same CC0 meshes, not a separate model.

**Props, bed and scenery** live in a separate `world.glb` (1.8 MiB, loaded once, shared by all characters): Sword, Axe, Candle and Book from the CC0
Fantasy Props MegaKit, the Staff by TCCDev, the twin bed (with pillow and blanket) and a small diorama (trees, bush, grass, flowers, pebbles; pines
for snow) from the CC0 Stylized Nature MegaKit. Haven's own plain shapes: the umbrella, the star pendant, the sun and moon, two ground discs and
the rain and snow particles. `CharacterView` parents the chosen prop entity to the active character's `attach_hand_r` and shows or hides entities by name.
Props per character: Human candle, book, sword; Wizard staff, book, candle; Robot sword, axe, candle (plus "none"). Older saved props (lantern,
tablet, compass) fall back to the character's default.

**Asset contract** (identical for every character, checked by `AssetContractTest`): joint nodes `root hips spine chest neck head clavicle_l/r
upperarm_l/r forearm_l/r hand_l/r thigh_l/r shin_l/r foot_l/r` with **identity rotations** (translation-only, so Haven's pose deltas act on world
axes; `build.mjs` bakes the T-pose into an arms-hanging bind pose to get there); nodes `attach_hand_r`, `attach_hand_l`, `acc_star`; `outfit_<id>` nodes for every outfit id in `Look.CHARACTERS` (the props are in `world.glb`:
`prop_<id>`, `prop_umbrella`, `bed`, `env_day`, `env_snow`, `ground_grass`, `ground_snow`, `env_sun`, `env_moon`, `fx_rain_0/1`, `fx_snow_0/1`); the head, eyes and brows as always-visible
skinned meshes. `Look.CHARACTERS` lists which ids exist per character and how the camera frames it. A model that fails to load is skipped (the next
character that loads is used); if none loads the character hides itself and the launcher carries on. Only the selected character's model is
kept in memory; choosing another one releases the previous model.

Regenerate the models: see [assets/README.md](assets/README.md).

## Quality: where it stands

The reference image (a professionally sculpted and painted wizard) is **not matched**. What changed is that the characters are now real, skinned,
PBR-textured production models (about 13k triangles for the body, authored normal and roughness maps, real hair, hands with fingers, eyes) from a
single creator and pack family, instead of procedural primitives, and they read as one consistent cast. They are still stylized game-ready
characters, not film-grade sculpts, and some limits are visible:

* No wizard hat or robe (paid-only), so the Wizard is a hooded mage; the Robot is armor on a human body, not a purpose-built robot.
* Textures are reduced to 1024 px (color) and 512 px (normal) for the TV box, which softens skin and cloth detail.
* **The eyes do not blink and are not closed while sleeping**: the source eyes are part of the skinned head, so Haven's blink (a scale of eye nodes) has
  nothing to act on. The sleeping pose, blanket and breathing still work.
* The umbrella, pendant, sun, moon, ground and weather particles are Haven's own plain shapes; no CC0 equivalents were found.

What would raise it further: the paid Quaternius Wizard outfit or any CC0 hat/robe set rigged to the same skeleton (drop it into `build.mjs` as another
`outfit_<id>` group), CC0 props in the same style, and eyelid blend shapes or a closed-eyes texture swap.

**Size, load and memory, measured on the TV box** (Xiaomi TV Box S 3rd Gen): see the figures at the end of this document.

## Reacting to the remote

`CharacterReactor` is the explicit mapping from launcher input to a short reaction (a *beat*):

| Input | Beat |
|---|---|
| Focus moves to something | glance (head and chest turn toward its screen position), at most one per 90 ms |
| A tab, category or screen changes | small chest lift and settle |
| OK pressed | nod, raised hand, widened eyes (key repeats never restart it) |
| OK held | both hands lift, once per hold |
| Back pressed | small step back and an open-hand dismissal |
| Focus on a music app | hands to the ears and a small nod, at most once every 1.8 s |
| Focus on a video app | turns toward the screen and presents a remote |
| Opening a music or video app | a bigger version of the same gesture |
| Returning to Haven after opening an app | a small wave |
| Studio: preview / equip / apply / cancel | quarter turn / full turn while the look swaps / bow / shrug |
| Nothing selected | relaxed waiting pose, eyes wandering |

Stronger beats interrupt weaker ones, never the other way round. Haven only *observes* keys and focus; it never consumes them, and the 3D view is
not focusable, so every control keeps working exactly as before.

### What Haven knows about apps

`AppKinds` classifies an app as music, video or other from, in this order: well-known package names (including the TV and the phone variants), the
category the app declares for itself (`ApplicationInfo.category`), then words in its label. It knows nothing about playback: the gestures are
poses for highlighting or opening an app, **not** a claim that anything is playing. There is no notification listener, media permission,
account or network request involved.

## Home states

The home screen is always in one of: an app is focused, a scope tab is focused, the scope is empty, apps are loading, or nothing is selected.
In every state except "an app is focused" the character glides to the middle of the screen in a waiting pose and a label names the active source
("Browsing Favorites", "Browsing Recent" or "Browsing Library") with distinct copy for loading, an empty scope and ordinary no-selection.

## Motion, performance and lifecycle

* Reduced motion (Animation: Off, or the system animator scale 0) gives a still pose and no reactions; nothing is rendered until something changes. The held poses of the weather and sleep scenes (lying down, umbrella arm) still apply, without breathing or shiver.
* Performance mode lowers the character to about 20 fps and shortens its motion. Normal mode is capped at about 30 fps.
* Rendering stops when the launcher is stopped or covered (Settings hides the whole scene) and resumes when it is visible again. Settings does not
  keep the 3D scene on screen on purpose: it would cost rendering time behind an opaque page and add nothing to its controls.
* **Settings → Character → Show character** turns the character off (and removes the Studio button).

## Dependencies and licences

* [Filament](https://github.com/google/filament) 1.69.5 (`filament-android` and `gltfio-android`), Apache License 2.0, text in
  `licenses/LICENSE-Filament-Apache-2.0.txt`. Native libraries for `armeabi-v7a` and `arm64-v8a` only.
  1.69.5 is used rather than the newest release because newer ones require compileSdk 37.
* The character models: original work, MIT like the rest of Haven.

## Measured on the TV box

Xiaomi TV Box S (3rd Gen), Android 14, release build, 2026-11-09:

| | Before (procedural v1) | Now (Quaternius-based) |
|---|---|---|
| `human.glb` / `wizard.glb` / `robot.glb` | 805 / 839 / 783 KiB | 3.3 / 3.6 / 1.7 MiB |
| Release APK | 11.95 MiB | 16.05 MiB (+4.1 MiB) |
| Frames over 20 s on the home screen | 1206, 1 janky (0.08%), GPU median 13 ms | 1205, 1 janky (0.08%), GPU median 12 ms |
| Total PSS with the home screen up | about 165 to 210 MiB | about 240 MiB (one model loaded; loading all three at once measured about 365 MiB, which is why only the selected one is kept) |

The frame figures are for the sleeping Human; the awake and umbrella poses were checked visually on the same build but their frame times were
measured only on debug builds, which are not comparable. `assets/character/warden.glb` (0.8 MiB) is an unreferenced leftover from the first
single-character version and could be deleted.


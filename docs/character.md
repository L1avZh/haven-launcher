# Haven's 3D characters

Three original, rigged and skinned 3D characters are rendered on the home screen with Google's Filament: **Human**, **Wizard** and
**Robot**. They react to the remote, change outfit and prop in the Character Studio, and the look is kept between restarts.
Everything is local: no accounts, no downloads, no extra permissions.

## What is in the box

| Piece | Where |
|---|---|
| Models (GLB, about 750 KiB each) | `app/src/main/assets/character/{human,wizard,robot}.glb` |
| Model generator (Python standard library only) | `tools/character/gen_character.py` |
| Roster, input-to-reaction mapping, poses | `CharacterLogic.kt` (pure Kotlin, unit tested) |
| App kind classification | `AppKinds.kt` (pure Kotlin, unit tested) |
| Renderer and lifecycle | `CharacterView.kt` (the only file that touches Filament) |
| Studio screen | `CharacterStudio.kt` |
| Saved look | preferences `char_id`, `char_outfit`, `char_prop`, `char_enabled`, `star_unlocked`, `star_worn` |

## The models

Each character has its own base mesh and skeleton proportions, built from primitives (ellipsoids, tapered tubes, boxes and rings) and
skinned to the same 17-joint rig, so one pose system drives all three:

* **Human**: natural proportions, a face (eyes, nose, lips, ears, brows), hair, hands with fingers, and two outfits (*City coat*, *Field jacket*).
* **Wizard**: beard, long hair, robe and hat, and two outfits (*Archmage*, *Apprentice*); the *Staff* prop is his.
* **Robot**: armored chassis with ribs, piston rods, a visor light bar and three-finger hands, and two outfits (*Plated*, *Stealth*).

Props (Lantern, Tablet, Compass, Staff) hang from the named node `attach_hand_r`. They are decoration only.

**Asset contract** (identical for every character): joint names `root hips spine chest neck head clavicle_l/r upperarm_l/r forearm_l/r hand_l/r
thigh_l/r shin_l/r foot_l/r`; nodes `body`, `eye_l`, `eye_r`, `attach_hand_r`, `attach_hand_l`, `acc_star`; `outfit_<id>` skinned meshes and
`prop_<id>` meshes. `Look.CHARACTERS` lists which ids exist for each character and how the camera frames it. A model that fails to load is
skipped; if none loads the character hides itself and the launcher carries on.

Regenerate or edit the models:

    python3 tools/character/gen_character.py

Honest note on quality: the models are stylized, procedurally generated figures. They are distinct and readable, but they are not
hand-sculpted production art.

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

* Reduced motion (Animation: Off, or the system animator scale 0) gives a still pose and no reactions; nothing is rendered until something changes.
* Performance mode lowers the character to about 20 fps and shortens its motion. Normal mode is capped at about 30 fps.
* Rendering stops when the launcher is stopped or covered (Settings hides the whole scene) and resumes when it is visible again. Settings does not
  keep the 3D scene on screen on purpose: it would cost rendering time behind an opaque page and add nothing to its controls.
* **Settings → Character → Show character** turns the character off (and removes the Studio button).

## Dependencies and licences

* [Filament](https://github.com/google/filament) 1.69.5 (`filament-android` and `gltfio-android`), Apache License 2.0, text in
  `licenses/LICENSE-Filament-Apache-2.0.txt`. Native libraries for `armeabi-v7a` and `arm64-v8a` only.
  1.69.5 is used rather than the newest release because newer ones require compileSdk 37.
* The character models: original work, MIT like the rest of Haven.

# The Warden: Haven's 3D character

The character on the home screen is an original, rigged and skinned 3D model rendered with Google's Filament. It reacts to the
remote, can change outfit and prop in the Character Studio, and keeps its look between restarts. Everything is local: no accounts,
no downloads, no extra permissions.

## What is in the box

| Piece | Where |
|---|---|
| Model (GLB, 765 KiB) | `app/src/main/assets/character/warden.glb` |
| Model generator (Python standard library only) | `tools/character/gen_character.py` |
| Renderer and lifecycle | `CharacterView.kt` (the only file that touches Filament) |
| Input to reaction mapping, idle and gesture poses | `CharacterLogic.kt` (pure Kotlin, unit tested) |
| Character Studio screen | `CharacterStudio.kt` |
| Saved look | preferences `char_outfit`, `char_prop`, `char_enabled` |

## The model

A 17-joint humanoid skeleton (hips, spine, chest, neck, head, clavicles, arms, hands, legs, feet) with smooth skin weights.
Three outfits are separate skinned meshes on the same skeleton, so they deform with the body: **Courier** (long coat with trim),
**Runner** (cropped jacket, pauldrons and knee pads), **Astral** (robe, cape and halo). Three hand-held props hang from the named
attachment node `attach_hand_r`: **Lantern**, **Tablet** and **Compass**, plus *None*. They are decoration only.

Regenerate or edit it:

    python3 tools/character/gen_character.py

Everything is built from primitives in that script, so there are no third-party models, textures or licences. To replace the model with
your own, keep the node names listed at the top of the script (joints, `outfit_*`, `prop_*`, `attach_hand_r`, `eye_l`, `eye_r`).

## Reacting to the remote

`CharacterReactor` is the explicit mapping from launcher input to a short reaction (a *beat*):

| Input | Beat | Notes |
|---|---|---|
| Focus moves to something | glance (head and chest turn toward its screen position) | repeats are coalesced, at most one beat per 90 ms |
| A tab, category or screen changes | small chest lift and settle | |
| OK pressed | nod, raised hand, widened eyes | key repeats never restart it |
| OK held | both hands lift | once per hold |
| Back pressed | small step back and an open-hand dismissal | |
| Studio: preview a look | quarter turn | |
| Studio: equip | full turn on the spot; the new look swaps in while turned away | |
| Studio: Apply / Cancel | small bow / shrug | |

Stronger beats interrupt weaker ones and a weaker one never interrupts a stronger one (equip > apply > confirm > cancel and long press
> back and preview > category > navigate). The launcher only *observes* keys and focus; it never consumes them, and the 3D view is not
focusable, so every control keeps working exactly as before.

## Motion, performance and lifecycle

* Reduced motion (Animation: Off, or the system animator scale 0) gives a still pose and no reactions; nothing is rendered until something changes.
* Performance mode lowers the character to about 20 fps and shortens its motion. Normal mode is capped at about 30 fps.
* Rendering stops when the launcher is stopped or covered (Settings hides the whole scene) and resumes when it is visible again.
* If the renderer or model cannot be created the character hides itself and the launcher carries on without it.
* **Settings → Character → Show character** turns the character off (and removes the Studio button).

## Dependencies and licences

* [Filament](https://github.com/google/filament) 1.69.5 (`filament-android` and `gltfio-android`), Apache License 2.0, text in
  `licenses/LICENSE-Filament-Apache-2.0.txt`. Native libraries are bundled for `armeabi-v7a` and `arm64-v8a` only.
  1.69.5 is used rather than the newest release because newer ones require compileSdk 37, which this project does not use.
* The Warden model: original work, MIT like the rest of Haven.

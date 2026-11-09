# Provenance: third-party 3D assets (Quaternius, TCCDev)

Both packs are by **Quaternius** (https://quaternius.com, https://quaternius.itch.io), released under **CC0 1.0 Universal** (public domain
dedication). Their own license files and the CC0 legal code are in [`licenses/`](licenses/).

| | Universal Base Characters [Standard] | Modular Character Outfits – Fantasy [Standard] |
|---|---|---|
| Asset page | https://quaternius.itch.io/universal-base-characters (also https://quaternius.com/packs/universalbasecharacters.html) | https://quaternius.itch.io/modular-character-outfits-fantasy (also https://quaternius.com/packs/modularcharacteroutfitsfantasy.html) |
| Version seen | Standard zip dated Dec 16, 2025 | v2.1 (Standard zip dated Jan 29, 2026) |
| License | CC0 1.0 Universal ("Asset license: Creative Commons Zero v1.0 Universal" on the itch.io page; "License: CC0 1.0 Universal" in `License_Standard.txt`) | same |
| Downloaded | 2026-11-09, itch.io "Name your own price" page, free option ("No thanks, just take me to the downloads"), no account, no payment | same |
| Original archive | `Universal Base Characters[Standard].zip`, 128,968,391 bytes, SHA-256 `fdbf1804c90dfc1ea03e992bff7da2dfd1a79318e13270a660180f9308455f40` | `Modular Character Outfits - Fantasy[Standard].zip`, SHA-256 `c3468b18871cc8c8f05ab14df7712baf22cb9f389cbd870babf130e595187f70` |
| Attribution | Not required by CC0; credited anyway (below) | same |

The archives are kept locally in this folder but are **not committed** (129 MB and 280 MB; `.gitignore`d). To rebuild, download the two free
"Standard" zips again from the pages above and check the hashes. The paid "Source" tiers were not used.

## Exactly which source files are used

From Universal Base Characters:
* `Base Characters/Godot - UE/Superhero_Male_FullBody.gltf` (+ `.bin`, `T_Superhero_Male_*`, `T_Eye_*`, `T_Hair_1_*` textures): the head, eyes and eyebrows
  (Human, Wizard) and the head of the android (Robot). Everything from the neck down is removed (the outfits supply it).
* `Hairstyles/Rigged to Head Bone/glTF (Godot -Unreal)/Hair_SimpleParted`, `Hair_Long`, `Hair_Beard` (+ `T_Hair_1_*`).

From Modular Character Outfits – Fantasy:
* `Exports/glTF (Godot-Unreal)/Outfits/Male_Peasant.gltf` and `Male_Ranger.gltf` (+ `.bin`, `T_Peasant_*`, `T_Ranger_*`, `T_Regular_Male_*`).
  The free tier ships only the Peasant and Ranger outfits; the Wizard and Noble outfits are in the paid Source tier and are **not** used.

## What Haven changed (all permitted by CC0)

Done by `tools/assets/build.mjs` (see `docs/assets/README.md`):
1. Re-bound the rigs: the T-pose was baked into an "arms hanging" pose (arms turned down about the shoulder and elbow with the skin weights, 7° away from
   the body), every joint was given an identity rotation (translation-only), inverse bind matrices recomputed, and 19 joints renamed to Haven's
   names (`pelvis`→`hips`, `spine_01`→`spine`, `spine_03`→`chest`, `lowerarm_*`→`forearm_*`, `calf_*`→`shin_*`, …). Fingers, toes and the
   extra spine bone ride along.
2. Removed the base body below the neck and merged the outfit and hair meshes into one renderable per outfit (`outfit_<id>`).
3. Wizard: the Ranger leather and the Peasant linen textures were hue-rotated / tinted indigo; the Wizard otherwise uses the same outfits as the Human.
4. Robot: the Ranger armor pieces (without the hood) and the head were given solid metal materials (silver and black finishes) with the eyes made
   emissive; the bare body mesh is not used.
5. Textures resized (color 1024 px, normal 512 px, roughness/ORM 512 px, hair 512 px, eyes 256 px) and re-encoded as JPEG; duplicate textures merged.
6. Added Haven nodes: `attach_hand_r/_l`, and the pendant, props, umbrella, blanket and pillow carried over from Haven's own v1 models (see below).

## Not from Quaternius

`acc_star`, `prop_lantern`, `prop_tablet`, `prop_compass`, `prop_staff`, `prop_umbrella`, `sleep_blanket`, `sleep_pillow` are Haven's own
procedural meshes (MIT, like the rest of Haven), taken from the first-generation models kept in `assets-source/procedural-v1/`. No suitable
CC0 versions of these were integrated.


---

# World assets: props, bed, scenery, staff (`world.glb`)

Built by `tools/assets/build_world.mjs`. All CC0 1.0 Universal; accessed 2026-11-09 (free options, no account, no payment; the staff is a plain public file on OpenGameArt).

| Used | From | Creator | License | Archive SHA-256 |
|---|---|---|---|---|
| `Sword_Bronze`, `Axe_Bronze`, `CandleStick`, `Book_7`, `Bed_Twin1` (the bed with its mattress, pillow and blanket) | [Fantasy Props MegaKit](https://quaternius.itch.io/fantasy-props-megakit) [Standard] (zip dated Jun 02, 2025) | Quaternius | CC0 1.0 (itch.io "Asset license: Creative Commons Zero v1.0 Universal"; `License_Standard.txt`) | `8b6f7e806d222e585478f0e1bdc6b271bbc7bc6f84dd6af8ca703a7c64f0cb1e` |
| `CommonTree_1`, `Pine_1`, `Bush_Common_Flowers`, `Grass_Common_Tall`, `Flower_3_Group`, `Pebble_Round_1` | [Stylized Nature MegaKit](https://quaternius.itch.io/stylized-nature-megakit) [Standard] (zip dated Jul 30, 2024) | Quaternius | CC0 1.0 | `298f6732b872e4cf7b30e6e7abf9641c7f6dc6b326df37ac089533ed7e3d58c9` |
| `SWS.glb` (the wizard staff) | [Stylized Wizard Staff](https://opengameart.org/content/stylized-wizard-staff), `sws_glb.zip` (527,820 bytes, SHA-256 `1273e921cd268ebf7857dc9e270388e5369cb01a618a60918a84df1a973c9379`) | TCCDev | CC0 ("Public Domain - Use as needed, no attribution required. Textures created in Blender using nodes - also CC0.") | see left |

The two Quaternius zips are kept locally in `assets-source/quaternius/` (not committed, 143 MB and 99 MB); the staff zip is committed (`assets-source/tccdev/`, 0.5 MB).
License files: `licenses/Fantasy-Props-MegaKit-License_Standard.txt`, `licenses/Stylized-Nature-MegaKit-License_Standard.txt`, `licenses/CC0-1.0-legalcode.txt`.

Changes made by Haven: each model baked at the scale and orientation it is held or placed (weapons and candle at the grip, tilted forward; bed laid
along x at half scale; scenery shrunk to a small diorama), merged into one glTF with grouped renderables (`env_day`, `env_snow`, props), textures
resized (512 px colour, 256 px normal) and re-encoded (JPEG; PNG with alpha for foliage), and the fully-metallic trim materials toned to 0.3 metallic
because they render black under Haven's soft lighting. The Fantasy Props free tier has no staff, lantern, tablet, compass or umbrella.

**Not from a third party (Haven's own plain shapes, MIT):** the umbrella (`assets-source/procedural-v1/human.glb`, from the v1 generator), the star
pendant, the sun and moon spheres, the two ground discs, and the rain drops and snow flakes (small 3D bipyramids and octahedra scrolled in the
renderer). No CC0 umbrella in a glTF-convertible form was found (the only CC0 one on OpenGameArt is a `.blend` file and needs Blender); no CC0 sun,
moon, cloud or ground model was found either.

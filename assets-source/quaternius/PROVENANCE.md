# Provenance: Quaternius character packs

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

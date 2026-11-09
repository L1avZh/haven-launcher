# Character asset pipeline

The runtime characters are built from the third-party CC0 downloads described in `assets-source/quaternius/PROVENANCE.md`.

Tools: Node 18+ and npm (no Blender needed). Everything lives in `tools/assets/` (`@gltf-transform` and `sharp`).

```bash
# 1. Put the two free "Standard" zips from the pages in PROVENANCE.md into assets-source/quaternius/ and unpack them:
mkdir -p .tmp-extract/outfits
unzip -q "assets-source/quaternius/Universal Base Characters[Standard].zip" -d .tmp-extract
unzip -q "assets-source/quaternius/Modular Character Outfits - Fantasy[Standard].zip" -d .tmp-extract/outfits

# World assets: unpack the Fantasy Props MegaKit [Standard] zip, the Stylized Nature MegaKit [Standard] zip and the staff the same way:
unzip -q "assets-source/quaternius/Fantasy Props MegaKit[Standard].zip" -d .tmp-extract/props      # gives .tmp-extract/props/Exports/glTF
unzip -q "assets-source/quaternius/Stylized Nature MegaKit[Standard].zip" -d .tmp-extract/nature   # gives .tmp-extract/nature/glTF
unzip -q assets-source/tccdev/sws_glb.zip -d .tmp-extract/staff                                    # gives .tmp-extract/staff/SWS.glb

# 2. Build the runtime GLBs into app/src/main/assets/character/
cd tools/assets && npm install && node build.mjs            # characters; or: node build.mjs wizard
node build_world.mjs                                         # props, bed, scenery, weather particles -> world.glb
```

What `build.mjs` does is listed in PROVENANCE.md. It keeps Haven's rig contract (`docs/character.md`), which `AssetContractTest` checks.
`probe*.mjs` print a model's joints, materials and texture memory.

`tools/character/gen_character.py` is the **v1 procedural generator**. It no longer produces any shipped character. Its output
(`assets-source/procedural-v1/*.glb`) is only the source of the star pendant (`build.mjs`) and the umbrella (`build_world.mjs`).

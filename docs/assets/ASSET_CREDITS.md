# Third-party asset credits

Haven's own code and original art are MIT-licensed (see `LICENSE`). The 3D character models below are **not** MIT: they are in the public domain
under CC0 1.0 Universal. Haven's MIT license does not apply to them and does not claim them.

| Asset | Creator | Source | License |
|---|---|---|---|
| Universal Base Characters (Standard) — heads, eyes, eyebrows, hair and beard | Quaternius | https://quaternius.itch.io/universal-base-characters | CC0 1.0 Universal |
| Modular Character Outfits – Fantasy (Standard) — Male Peasant and Male Ranger outfits | Quaternius | https://quaternius.itch.io/modular-character-outfits-fantasy | CC0 1.0 Universal |

| Fantasy Props MegaKit (Standard): sword, axe, candlestick, book, bed | Quaternius | https://quaternius.itch.io/fantasy-props-megakit | CC0 1.0 Universal |
| Stylized Nature MegaKit (Standard): trees, bush, grass, flowers, pebbles | Quaternius | https://quaternius.itch.io/stylized-nature-megakit | CC0 1.0 Universal |
| Stylized Wizard Staff | TCCDev | https://opengameart.org/content/stylized-wizard-staff | CC0 |

| Home screen background photograph: "Misty clouds obscure a steep mountain valley with trees" | Nguyen Bui | https://unsplash.com/photos/misty-clouds-obscure-a-steep-mountain-valley-with-trees-ghd8iAZKdjE (profile https://unsplash.com/@renichinguyen) | Unsplash License, https://unsplash.com/license (free for commercial and non-commercial use, no permission or attribution required, not to be resold without significant modification or used to build a competing image service) |

The photograph (published on Unsplash 2025-10-26, downloaded 2026-11-10 from the page above) is shipped as `app/src/main/res/drawable-nodpi/bg_valley.webp`: cropped to 16:9, resized to 1920x1080 and
compressed to WebP (about 190 KB). At run time Haven tints and darkens it in code and layers soft scrims on it; it is a bundled file and is never fetched from the network. It shows no people, brands or
watermarks. The Unsplash License is not MIT or CC0; Haven's MIT license does not apply to this photograph.

CC0 requires no attribution; Quaternius asks for support and credit when you can ("Consider supporting me on Patreon":
https://www.patreon.com/quaternius). Haven credits them in this file, in the README, and in Settings → About & credits.

The characters shipped in the app (`app/src/main/assets/character/{human,wizard,robot}.glb`) are modified derivatives of these assets
(re-rigged, re-textured and merged; see `assets-source/quaternius/PROVENANCE.md` for the full list of changes). The props, bed and scenery are modified (baked, resized, merged) copies of the assets above. The umbrella, the star pendant, the sun and moon,
the ground discs and the rain and snow particles are Haven's own simple shapes (MIT).

License text: `assets-source/quaternius/licenses/CC0-1.0-legalcode.txt`, https://creativecommons.org/publicdomain/zero/1.0/ ; the packs' own
license files are in the same folder. Accessed 2026-11-09.

Filament (Apache 2.0) and the bundled fonts (SIL OFL 1.1) are credited in the README and `licenses/`.

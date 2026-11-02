# Architecture notes

Short notes on decisions that aren't obvious from the code.

- **No framework UI toolkit.** The UI is built in code from plain views on a 1920x1080 layout grid (`Scale`) so it looks the same whatever density the TV reports. Colors, sizes, radii and motion values are in `Tokens.kt`.
- **App discovery** is a single `PackageManager` pass over leanback launcher activities, plus plain launcher activities from packages that don't have a leanback one. A broadcast receiver reloads it when packages change.
- **Icons** are decoded on two background threads into fixed-size bitmaps in a byte-bounded `LruCache`. The accent color of each app is a saturation-weighted average of its banner, computed once when the bitmap is made.
- **Focus** is handled by Android's focus search. A custom `LinearLayoutManager` keeps the focused tile on a fixed slot; the shelves block Left/Right at their ends so focus never jumps to the other shelf.
- **Settings** are a full-screen view inside the main activity (not a separate activity), built from small row models. Dialogs share one menu builder.
- **Motion** uses `ViewPropertyAnimator` and one color animator. Three durations and two curves are used everywhere; all of it scales down to zero for reduced motion. A full-screen vignette and focus shadows were tried and removed after measuring frame times.
- **Languages** are generated from `tools/i18n_data_*.py`.
- **Preferences** are one `SharedPreferences` file. Nothing is written elsewhere.

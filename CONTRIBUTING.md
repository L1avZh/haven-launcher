# Contributing

Bug reports, translation fixes and device test results are the most useful contributions.

- **Translations**: edit the data in `tools/i18n_data_*.py`, run `python3 tools/gen_i18n.py`, and include the regenerated files in your pull request. Keep placeholders like `%1$s` intact.
- **Device reports**: say the device, Android version and remote, and what worked. The Button test screen (Settings > Remote control) is a good thing to attach.
- **Code**: keep the project dependency-free where you can, avoid network access and permissions, and check `./gradlew assembleRelease lintRelease` before opening a pull request.

By contributing you agree that your changes are released under the MIT license.

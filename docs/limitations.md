# Known limitations

- Only tested on one device (Xiaomi TV Box S 3rd Gen, Android 14) and one remote (LG Magic Remote over HDMI-CEC).
- The firmware on that box ignores a third-party Home app: the Home button still opens Google's launcher. Haven registers as a Home app, but that is up to the device.
- With an LG remote over HDMI-CEC only arrows, OK and Back reach the box, so there is no shortcut that works while another app is in front. There is no floating Home button (an overlay cannot be reached with a D-pad without taking input from the app underneath). See [remote-control.md](remote-control.md).
- City names in the weather picker are English; the widget shows the name in the app's language once fetched.
- Translations are machine-assisted drafts, none checked by a native speaker ([localization.md](localization.md)).
- Custom wallpapers from files, custom categories and settings export are not implemented.
- 3D: the characters are stylized game models, not film-grade; the Wizard has no hat or robe (the free pack has none); the eyes do not blink; the umbrella, star pendant, sun, moon, ground and weather particles are Haven's own plain shapes; there are no 3D clouds. Details in [character.md](character.md).
- The weather scenes were verified with a debug-only forced reading, not with real rain or snow ([day-night.md](day-night.md)).
- Memory: about 240 MiB PSS with the home screen up (one character loaded); 3D is the main cost.

# Known limitations

- Only tested on one device (Xiaomi TV Box S 3rd Gen, Android 14) and one remote (LG Magic Remote over HDMI-CEC).
- Selected is not routed. Settings > Remote & Home app > Default Home app reports what Android has recorded (the Home role on Android 10+, the resolved Home handler before that). On the Xiaomi box the Home button still opens Google's launcher even when Haven holds the role: the firmware decides, and Haven does nothing to override it (no service, overlay, boot or screen-on receiver, key interception or standby control). Measured on that box: with Haven holding the role, an injected Home key and a standby/wake cycle both ended in Google's launcher (details in [installation.md](installation.md)); the LG remote's Home button, display off/on and reboot were not tested.
- With an LG remote over HDMI-CEC only arrows, OK and Back reach the box, so there is no shortcut that works while another app is in front. There is no floating Home button (an overlay cannot be reached with a D-pad without taking input from the app underneath). See [remote-control.md](remote-control.md).
- City names in the weather picker are English; the widget shows the name in the app's language once fetched.
- Translations are machine-assisted drafts, none checked by a native speaker ([localization.md](localization.md)).
- Custom wallpapers from files, custom categories and settings export are not implemented.
- 3D: the characters are stylized game models, not film-grade; the Wizard has no hat or robe (the free pack has none); the eyes do not blink; the umbrella, star pendant, sun, moon, ground and weather particles are Haven's own plain shapes; there are no 3D clouds. Details in [character.md](character.md).
- The weather scenes were verified with a debug-only forced reading, not with real rain or snow ([day-night.md](day-night.md)).
- Memory: about 240 MiB PSS with the home screen up (one character loaded); 3D is the main cost.

# Remote control

Haven needs only the arrow keys, OK and Back. Everything in the launcher, including every settings page, can be done with those.

## Tools inside Haven

Settings > Remote & Home app:

- **Button test**: shows every key Android delivers: key name and code, the input device, its source, repeat count, and whether it was a long press. Press Back three times to leave.
- **Custom mappings**: pick an action (open an app, search, settings, Go to Home) and press the remote button to bind it. Hold the button for about a second to bind a long press instead; a quick Back cancels, so hold Back to bind Back. Only buttons that actually reach the app can be bound.
- **Number keys open favorites**: on the home screen, 1 to 9 open favorites one to nine and 0 opens the tenth. This is on by default and only works when no menu is open.
- **Return to start shortcut** and **Input devices**.

Settings > Remote & Home app > Custom mappings is the one entry to the mapping list (the duplicate "App shortcuts" row under Applications was removed).

## Measured with an LG Magic Remote

I use an LG Magic Remote (2021 or later style, with Netflix, Prime Video, Disney+, Movies, Assistant and Alexa buttons) with the box on an HDMI input of the TV. The remote talks to the TV; the TV talks to the box over HDMI-CEC. I captured `logcat` on the box while pressing the remote's buttons.

| LG button | CEC user control | Android keycode |
|---|---|---|
| Up / Down / Left / Right | 0x01 / 0x02 / 0x03 / 0x04 | `DPAD_UP` / `DPAD_DOWN` / `DPAD_LEFT` / `DPAD_RIGHT` |
| OK | 0x00 Select | `DPAD_CENTER` |
| Back | 0x0D Exit | `BACK` |

Nothing else arrived in the capture: digits, color keys, the app buttons, Assistant, Alexa, Guide, Quick Access, Ad/SAP. Volume is handled by the TV. These events show up in Android as system-injected keys (device id -1, HDMI source).

The wheel is not separately identifiable. If it works it can only be sending Up and Down.

Long-press OK was not tested with this remote.

## Home

The LG Home button is handled by the TV. The box only sees the TV leave and later return to its input (CEC Active Source and Routing Change messages). No app can turn that into a key press.

## Getting back to Haven from another app

A Home shortcut (Settings > Remote & Home app > Return to start shortcut) is a mapping like any other. It was tested on the box: a long press of OK bound to Go to Home jumps back to the first favorite from anywhere in Haven. **It does not work while another app is in front.** With the Android TV Settings app open, holding OK did nothing and the app stayed in front; Android only delivers keys to the focused app.

What works:

1. **Back**. Haven launches apps in their own task, so Back eventually returns to Haven. How many presses it takes depends on the app (some need several).
2. The Home chip in Haven's header, and the "go back to the start" action.
3. From a computer: `tools/haven-home.sh`.

What does not work, and why:

- A remote shortcut that works while another app is in front. Android only delivers keys to the focused app; catching them elsewhere needs Accessibility access or system privileges, and this project avoids both.
- A floating Home button. The permission screen for overlays exists on the Xiaomi box, but a focusable overlay would take key focus away from the app below it, and a non-focusable one only reacts to touch or a mouse. Neither is usable with a D-pad.

## Things to try on the TV side

HDMI-CEC (SIMPLINK on LG) must stay on; it is how the arrow keys get through. LG's Universal Control setup can make the Magic Remote send infrared codes to a streaming device if the device brand is in LG's list. I haven't tried it, and menu names vary by webOS version.

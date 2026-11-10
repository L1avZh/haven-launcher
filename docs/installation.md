# Installation

## What you need

- An Android TV or Google TV device on Android 8.0 (API 26) or newer.
- The APK from the [Releases](../../../releases) page.
- A way to install it: wireless ADB from a computer (below), or any file-based sideloading method you already use.

Haven requests no runtime permissions. It declares the normal `INTERNET` permission, used only by the optional weather widget once a city is chosen in Settings.

## Install with wireless ADB

1. On the TV, enable developer options (Settings > System > About, press the build number seven times).
2. Under Developer options, turn on USB debugging, then Wireless debugging if your device has it.
3. If the TV shows a pairing code, pair first:

   ```bash
   adb pair <tv-ip>:<pairing-port>     # enter the six-digit code when asked
   ```

4. Connect and install:

   ```bash
   adb connect <tv-ip>:<port>
   adb -s <tv-ip>:<port> install -r haven-<version>.apk
   adb -s <tv-ip>:<port> shell am start -n dev.l1avzh.haven/.MainActivity
   ```

The port for `connect` is different from the pairing port and can change after a reboot. Replace `<tv-ip>:<port>` with the values your TV shows.

## Making Haven the Home app

**In Haven:** Settings > Remote & Home app > *Default Home app*. The row shows what Android reports and what pressing OK will do:

| Row | Meaning | OK does |
|---|---|---|
| Haven | Android lists Haven as the default Home app | explains that this is not a promise about the Home button (below) and offers the system screen to change it |
| Not set | Haven is not the default Home app | Android 10+: Android's own consent dialog for the Home role ("Set Haven as default Home app?"). Android 8 to 9, or if that dialog is missing: the system Home screen (`Settings.ACTION_HOME_SETTINGS`), then the default-apps screen if the first is absent |
| Unavailable | the device offers no Home app chooser | shows manual steps (system settings if your firmware has them, or the `adb` command below) |

Haven never selects itself: Android's dialog or screen always asks you. When you come back, Haven asks Android again and shows the real state; opening the
screen is not treated as having chosen Haven.

**Check it yourself** (the system's answer, independent of Haven's screen):

```bash
adb -s <tv-ip>:<port> shell cmd role get-role-holders android.app.role.HOME          # Android 10+: lists the Home role holder
adb -s <tv-ip>:<port> shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s <tv-ip>:<port> shell dumpsys activity activities | grep -i "home"            # what is actually in front after pressing Home
```

**Selected is not the same as routed.** "Haven is the default Home app" is a fact about Android's settings. Whether the Home button, the
power key or waking from standby then open Haven is decided by the device's firmware and, on a TV, by the TV: an HDMI-CEC remote's Home button is handled by the
TV, which only tells the box that its input changed ([remote-control.md](remote-control.md)). Haven does not try to override either; it
adds no background service, boot or screen-on receiver, overlay, accessibility service or key interception for this, and it keeps the screen-off and
standby behavior of the device untouched.

If you prefer the command line, this sets the Home activity directly:

```bash
adb -s <tv-ip>:<port> shell cmd package set-home-activity dev.l1avzh.haven/.MainActivity
```

On the Xiaomi TV Box S (3rd Gen, Android 14) this makes Haven the Home role holder, yet the Home button still opens Google's launcher: that is the
firmware's choice, and nothing inside Haven can change it.

**What was measured on that box (2026-11-10, release build, Haven already the role holder):**

| Check | Result |
|---|---|
| `cmd role get-role-holders android.app.role.HOME` | `dev.l1avzh.haven` |
| `cmd package resolve-activity` for a Home intent | `com.google.android.apps.tv.launcherx/.home.HomeActivity` (the resolver disagrees with the role) |
| Home key injected with `adb shell input keyevent KEYCODE_HOME` (this is a key delivered to Android, not the LG remote) | opened Google's launcher, not Haven |
| Standby (`KEYCODE_SLEEP`) then wake | the box dropped off the network and woke into Google's launcher; the role was still held by Haven afterwards |
| Settings > Remote & Home app > Default Home app | showed "Haven" with the firmware caveat |
| Real LG remote Home button, display off/on, reboot | **not tested** (no way to press the remote from here, and a reboot would drop wireless debugging) | To go back:

```bash
adb -s <tv-ip>:<port> shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
```

Haven does not disable or modify the stock launcher.

## Jump back to Haven from a computer

`tools/haven-home.sh` brings Haven to the front over ADB:

```bash
tools/haven-home.sh [ip:port]
```

## Updating

Install the new APK with `adb install -r`. Settings are kept as long as the signing key is the same. Release APKs use a debug key, so a build you make yourself will not install over a release APK; uninstall first.

## Uninstalling

```bash
adb -s <tv-ip>:<port> uninstall dev.l1avzh.haven
```

This removes Haven and its preferences. It does not touch any of your other apps.

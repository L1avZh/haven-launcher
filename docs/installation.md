# Installation

## What you need

- An Android TV or Google TV device on Android 8.0 (API 26) or newer.
- The APK from the [Releases](../../../releases) page.
- A way to install it: wireless ADB from a computer (below), or any file-based sideloading method you already use.

Haven requests no runtime permissions.

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

Android lets an app ask to be the default Home app. You can try:

```bash
adb -s <tv-ip>:<port> shell cmd package set-home-activity dev.l1avzh.haven/.MainActivity
```

On the Xiaomi TV Box S (3rd Gen) this changes the Home role (`cmd role get-role-holders android.app.role.HOME` lists Haven) but the Home button still opens Google's launcher. That is the firmware's choice. To go back:

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

# Brain Rot

An Android app that watches which app you are actually using, and drops an
animated crying brain on top of it once you have been in there past your own
time limit (10 minutes by default).

<p align="center">
  <img src="app/src/main/res/raw/crying_brain.gif" width="200" alt="Crying brain">
</p>

## What it does

- **Lists every app on the phone.** Anything with a launcher icon shows up, with
  its real icon and name, searchable, with a toggle for system apps.
- **You pick which apps to watch** and, optionally, a per-app limit. Apps with no
  limit of their own use the default (10 minutes).
- **A background watcher** polls the foreground app every 2 seconds and keeps a
  timer per app.
- **Past the limit, the crying brain appears** over the app you are in: the
  animated GIF, how long you have been in there, and two buttons - snooze, or
  "I'll stop", which sends you to the home screen and resets the timer.

The timer survives brief detours. Bouncing to the home screen and straight back
does not reset anything; the counter only clears once you have stayed out of the
app for five minutes.

## Permissions it asks for

| Permission | Why | Where it is granted |
| --- | --- | --- |
| Usage access | The only way to know which app is in the foreground | Settings → Special app access → Usage access |
| Display over other apps | To draw the brain on top of the offending app | Settings → Special app access → Display over other apps |
| Notifications | The watcher is a foreground service, which must show one | Standard runtime prompt |

Both special-access permissions are granted in system Settings, not in a dialog.
The app links straight to the right screen and re-checks when you come back.

## Building

Requires Android Studio (or a command-line Android SDK) with SDK 35 installed.

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Unit tests for the usage timer:

```
./gradlew test
```

Minimum Android version is 9 (API 28), which is what the platform GIF decoder
(`ImageDecoder` / `AnimatedImageDrawable`) needs.

## Regenerating the GIF

The crying brain is drawn in code rather than shipped as an opaque binary:

```
pip install Pillow
python3 tools/make_crying_brain_gif.py
```

That rewrites `app/src/main/res/raw/crying_brain.gif`. The GIF is opaque and its
background colour matches `overlay_card` in `res/values/colors.xml`, so it blends
into the card it sits on. Change one and change the other.

## How the pieces fit

| File | Role |
| --- | --- |
| `data/InstalledApps.kt` | Enumerates launchable apps via a manifest `<queries>` block |
| `data/SettingsStore.kt` | Watched apps, limits and snooze, shared by UI and service |
| `monitor/ForegroundAppDetector.kt` | Replays usage events to find the current foreground app |
| `monitor/UsageSessionTracker.kt` | Per-app timers, detour tolerance, doze-gap capping |
| `monitor/UsageMonitorService.kt` | The foreground service that ties it together |
| `overlay/CryingBrainOverlay.kt` | The `TYPE_APPLICATION_OVERLAY` window with the GIF |
| `ui/` | Compose UI: permissions, limits, app picker |

## Notes and limits

- Android only. iOS has no equivalent API for reading foreground app usage or
  drawing over another app, so this cannot be ported.
- Aggressive battery managers (Xiaomi, Oppo, Samsung and others) can kill the
  service. Exempt the app from battery optimisation if the brain stops showing up.
- `QUERY_ALL_PACKAGES` is deliberately not used, so apps with no launcher entry
  are not listed.

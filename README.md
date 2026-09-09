# Brain Rot

Watch the apps you choose, and show a crying brain when you have been in one of
them past your own time limit (10 minutes by default).

<p align="center">
  <img src="ios/BrainRot/Resources/crying_brain.gif" width="200" alt="Crying brain">
</p>

Two implementations live here:

- **`ios/`** - the iPhone app, built on Apple's Screen Time APIs.
- **`android/`** - the Android app, which can do the literal version of the idea.

Both are fed by one asset generator in `tools/`, so the character is the same on
both platforms.

## What iOS allows, and what it does not

iOS has no equivalent of the Android version, and the gap is not a matter of
effort. Three limits are enforced by the operating system:

| What you asked for | On iOS |
| --- | --- |
| Detect every app on the phone | Not possible. No API lists installed apps. Apple's own picker shows you your apps and hands the app anonymous tokens; it never learns their names or icons. |
| Show a GIF over the app you are using | Not possible. Nothing may draw over another app. The sanctioned interruption is a *shield*, a system screen that covers the blocked app, and it accepts a **still image only**. |
| Trigger after N minutes of use | Supported, with a catch: usage is counted per scheduled window, so the limit is minutes per day (or per hour), not minutes in one sitting. |

So the iPhone app does this instead:

1. You pick apps through Apple's `FamilyActivityPicker`.
2. Past the limit, the app is shielded behind a crying-brain screen with your
   limit written on it, plus two buttons: **I'll stop** and **N more minutes**.
3. At the same moment a notification arrives carrying the **animated** GIF as an
   attachment. Expanding the notification plays it. That is the one place iOS
   lets an animation reach you over another app.
4. The same GIF animates inside the app itself.

Snoozing lifts the shield and starts a fresh window worth of usage, so putting
the phone down does not burn the snooze.

If blocking is too blunt, turn **Block the app** off and you get the
notification alone.

## Building the iPhone app

Needs a Mac with Xcode 15 or newer, and an Apple Developer account.

```
brew install xcodegen
cd ios
xcodegen generate
open BrainRot.xcodeproj
```

Then, in Xcode:

1. Select your team under Signing & Capabilities for **all four targets**
   (`BrainRot`, `BrainRotMonitor`, `BrainRotShield`, `BrainRotShieldAction`).
2. Change the bundle identifiers from `com.brainrot.detector*` to something in
   your own namespace, and the App Group in the four `.entitlements` files plus
   `BrainRot.appGroup` in `ios/Shared/BrainRotShared.swift` to match.
3. Run on a physical device. Screen Time APIs do not work in the simulator.

The `.xcodeproj` is generated rather than committed, because four targets of
`project.pbxproj` is unreadable in a diff. `ios/project.yml` is the source of
truth. If you would rather not use XcodeGen, the same structure can be built by
hand: one app target and three app-extension targets, each with the App Group
and the Family Controls capability, with the extension point identifiers and
principal classes from the `Info.plist` files in this repo.

**About the entitlement:** Family Controls is a restricted capability. A paid
developer account can enable it for builds on your own device. Shipping to
anyone else, TestFlight included, needs Apple to approve a distribution
entitlement request first.

### The pieces

| File | Role |
| --- | --- |
| `Shared/BrainRotShared.swift` | Names, settings, and the App Group storage all four targets read |
| `BrainRot/ScreenTimeController.swift` | Authorization, the selection, and the device-activity schedule |
| `BrainRot/ContentView.swift` | The SwiftUI screen |
| `BrainRot/AnimatedGIF.swift` | Plays the GIF; SwiftUI's `Image` shows only its first frame |
| `BrainRotMonitor/` | Wakes on the threshold: applies the shield, posts the GIF notification |
| `BrainRotShield/` | How the blocking screen looks |
| `BrainRotShieldAction/` | The two buttons on it |

## Building the Android app

The Android version does the literal thing: it lists every launchable app,
polls the foreground app every two seconds, and draws the animated GIF in a
window on top of the offending app.

```
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew test
```

Minimum Android 9 (API 28). It asks for usage access, draw-over-other-apps, and
notifications; the first two are granted in system Settings, and the app links
straight to the right screens.

Unlike the iOS version, its timer is per sitting: it keeps running across brief
detours and only resets after five minutes away from the app.

| File | Role |
| --- | --- |
| `data/InstalledApps.kt` | Enumerates launchable apps via a manifest `<queries>` block |
| `data/SettingsStore.kt` | Watched apps, limits and snooze, shared by UI and service |
| `monitor/ForegroundAppDetector.kt` | Replays usage events to find the current foreground app |
| `monitor/UsageSessionTracker.kt` | Per-app timers, detour tolerance, doze-gap capping |
| `monitor/UsageMonitorService.kt` | The foreground service that ties it together |
| `overlay/CryingBrainOverlay.kt` | The overlay window with the GIF |

## Regenerating the artwork

The crying brain is drawn in code, not shipped as an opaque binary:

```
pip install Pillow
python3 tools/make_crying_brain_gif.py
```

One run writes every asset both platforms need: the GIF for the Android
resources and the two iOS bundles, a transparent PNG for the iOS shield, and
the 1024px app icon. The GIF is opaque and its background matches
`overlay_card` in the Android colors file, so it blends into the card it sits
on; change one and change the other.

## Status

Neither app has been compiled. The environment they were written in has no
Android SDK, no Mac, and no Xcode, and the network policy blocks Google's Maven
repository, so nothing here has been through a compiler. The logic and the APIs
were written carefully but are unverified. Expect to fix build errors on the
first run.

Two iOS details in particular are worth checking on a device, since their exact
behaviour is hard to confirm without one:

- `ShieldActionResponse.defer` is used to let the user through after a snooze.
- Threshold callbacks can lag; Apple does not promise the notification arrives
  the instant the limit is crossed.

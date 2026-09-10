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

Both apps compile in CI, on every push, via `.github/workflows/build.yml`.
That workflow exists because neither app can be built on a Windows machine:
Android needs the Android SDK, and iOS needs Xcode, which runs only on macOS.
Pushing a commit is how you reach a compiler.

| | State |
| --- | --- |
| Android | Compiles; unit tests pass; the debug APK is uploaded as a run artifact you can download and install |
| iOS | All four targets compile against the device SDK, unsigned |
| Either app running on real hardware | Not yet verified |

Building is not the same as working. The iOS app in particular has never been
installed on a phone, and a few things can only be confirmed there:

- `ShieldActionResponse.defer` is used to let you through after a snooze.
- Threshold callbacks can lag. Apple does not promise the notification arrives
  the instant the limit is crossed.
- The Family Controls entitlement has to be live on your developer account
  before a signed build will install.

## Signing the iOS app in CI

`.github/workflows/ios-release.yml` archives, signs and exports a real `.ipa`
on a GitHub macOS runner. You never need a Mac of your own. It does not run on
push; start it from the Actions tab once the secrets below exist.

**The one thing this cannot do for you.** Family Controls is a restricted
capability. Apple grants it automatically for builds you run from Xcode onto
your own device, but *any* other distribution, this CI pipeline included, needs
Apple to approve an entitlement request against your account first. Until that
approval lands, the App IDs below cannot carry the capability and the signed
build will not install. Request it early, then set up the rest while you wait:

> developer.apple.com → Account → **Family Controls (Distribution)** request form

### 1. Register four App IDs

One per target, each with the **Family Controls** and **App Groups**
capabilities ticked. Wildcards will not work; Family Controls needs explicit IDs.

| Target | Bundle ID |
| --- | --- |
| App | `com.brainrot.detector` |
| Monitor | `com.brainrot.detector.monitor` |
| Shield | `com.brainrot.detector.shield` |
| Shield action | `com.brainrot.detector.shieldaction` |

Use your own namespace instead of `com.brainrot`, and change it in
`ios/project.yml`, the four `.entitlements` files, and `BrainRot.appGroup` in
`ios/Shared/BrainRotShared.swift`. The signing script reads the bundle IDs back
out of `project.yml`, so it follows any renaming automatically.

### 2. Make a distribution certificate, on Windows

Apple's instructions assume Keychain Access. OpenSSL does the same job:

```powershell
openssl genrsa -out ios_dist.key 2048
openssl req -new -key ios_dist.key -out ios_dist.csr `
  -subj "/emailAddress=you@example.com/CN=Your Name/C=US"
```

Upload `ios_dist.csr` to developer.apple.com under Certificates → **Apple
Distribution**, download the resulting `ios_dist.cer`, then bundle key and
certificate into the `.p12` the workflow wants:

```powershell
openssl x509 -inform DER -outform PEM -in ios_dist.cer -out ios_dist.pem
openssl pkcs12 -export -inkey ios_dist.key -in ios_dist.pem `
  -out ios_dist.p12 -passout pass:CHOOSE_A_PASSWORD
```

### 3. Make four provisioning profiles

One per App ID. Pick **Ad Hoc** to install directly on your own phone, or **App
Store** to go through TestFlight. Ad Hoc profiles only work on devices whose
UDID you registered first; iTunes on Windows will show you your iPhone's UDID.

### 4. Add the secrets

Settings → Secrets and variables → Actions. Base64-encode each file first:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("ios_dist.p12")) | Set-Clipboard
```

| Secret | Contents |
| --- | --- |
| `APPLE_TEAM_ID` | Your 10-character team ID |
| `IOS_DIST_CERTIFICATE_P12` | base64 of `ios_dist.p12` |
| `IOS_DIST_CERTIFICATE_PASSWORD` | The password you chose above |
| `IOS_PROFILE_APP` | base64 of the app's `.mobileprovision` |
| `IOS_PROFILE_MONITOR` | base64 of the monitor's |
| `IOS_PROFILE_SHIELD` | base64 of the shield's |
| `IOS_PROFILE_SHIELD_ACTION` | base64 of the shield action's |

### 5. Run it

Actions → **iOS signed build** → Run workflow. Choose `release-testing` for an
ad-hoc build, or `app-store-connect` for TestFlight. The `.ipa` appears as a
run artifact.

To get an ad-hoc `.ipa` onto the phone from Windows, upload it to an
over-the-air install service such as Diawi and open the link on the phone. For
the App Store route, upload the `.ipa` to App Store Connect and install through
TestFlight.

The signing pipeline itself has not been run end to end, because it needs a
real Apple account. The build, archive and export steps are wired up and the
profile-matching script is tested, but expect to iterate on the first run.

## Getting the iOS app onto a phone

Three routes, in rough order of how much friction they carry:

- **CI signing**, above. No Mac at any point, but gated on Apple approving the
  Family Controls entitlement.
- **A Mac**, with the iPhone plugged in. The only route that skips the
  entitlement request, since Xcode grants Family Controls for local development
  builds. Borrowed or rented by the hour both work for the initial proof that
  the app behaves.
- **TestFlight**, which needs the same Apple approval as CI signing.

Neither an iPad nor a Windows PC can do this. Xcode does not exist for iPadOS,
and Swift Playgrounds cannot build app extensions or set the entitlements these
extensions need.

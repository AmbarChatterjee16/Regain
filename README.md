# Regain — focus timer + app blocker (Android clone)

A fully offline, sideloadable Android app inspired by **Regain: Study Timer for Focus** (EpowerX Labs).
It is a native Kotlin + Jetpack Compose app, not a web wrapper, so the blocking features actually work.

**APK:** `Regain-1.0.0.apk` (repo root) — also at `app/build/outputs/apk/release/app-release.apk`

### Your checklist, mapped to the app
| Requirement | Where it is |
| --- | --- |
| Installable APK | Signed `Regain-1.0.0.apk`, package `ai.regain.clone`, minSdk 26 |
| Real blocking, no simulation | `AccessibilityService` + full-screen `TYPE_APPLICATION_OVERLAY` wall + UsageStats watchdog |
| Free + Pro unlocked | Every Pro feature is on by default (`proUnlocked = true`); no paywalls or accounts |
| Block only Instagram Reels (not the app) | **Guard → Block Instagram Reels** |
| Block only YouTube Shorts (not the app) | **Guard → Block YouTube Shorts** (long-form still plays) |
| YouTube channel allowlist | **Guard → YouTube mode → Allowlist only** + add channels; non-allowed watch pages are walled |
| Tuning when Instagram/YouTube change their UI | **Guard → Detection readout** shows the exact accessible text |

---

## What's inside

### Focus engine
- Pomodoro timer: focus / short break / long break with custom lengths, auto-advance, rounds before a long break
- Stopwatch mode
- Subject tagging with colours, daily focus goal, streak, 7-day chart, per-subject breakdown, recent sessions
- Foreground service countdown + notification controls, survives leaving the app and process re-creation
- Strict mode: a focus session cannot be ended early (notification stop is removed too)
- Focus Guard: full-screen nudge when you leave the app mid-session
- Focus music (procedurally synthesised, no downloads): Lofi, Deep Focus, Rain, White Noise, Space, Binaural
- 5 themes: Midnight, Forest, Dusk, Ocean, Lofi
- Focus rooms + leaderboard (simulated peers in this offline build)
- Every "Pro" feature is unlocked

### Focus Guard (the blocker)
- Block any installed app (searchable picker, app icons, user/system filter)
- Full-screen focus wall drawn with `TYPE_APPLICATION_OVERLAY` over the blocked app
- Enforcement through an AccessibilityService (fast, screen-aware) **and** a UsageStats watchdog as a backup
- Kills the blocked app back to the home screen when you tap "Back to what matters"
- **Instagram Reels blocker** — Reels tab/viewer is detected from the accessibility tree; the feed stays usable
- **YouTube Shorts blocker** — Shorts player detected, long-form videos keep working
- **YouTube channel allowlist** — while active, a watch page is blocked unless the channel is on your list.
  Browse and search freely; only playback of non-allowed channels is walled off.
  Detected channel names are collected so you can add them with one tap.
- Strict mode also prevents leaving the blocker on for a blocked app, and can lock the Settings app during a session
- 5-minute temporary unlock escape hatch (disabled in strict mode)
- Detection readout: shows exactly what text/nodes the app sees, so rules can be verified on-device

### Android limitations, stated plainly
- Android has no public "which screen is this" API. Reels/Shorts/channel detection is **heuristic** based on the
  accessibility tree. It is the same approach real blockers use, but Instagram/YouTube change their UI often.
  If a rule misses, open **Guard → Detection readout** and tune with the text shown there.
- Some apps (mostly banking/DRM/system apps) declare `HIDE_OVERLAY_WINDOWS`, which tells Android to hide every
  non-system overlay. When Regain detects that its wall was force-hidden, it falls back to sending you Home, so
  the app is still blocked instead of silently staying usable.
- Some phone brands kill background services aggressively. Use **Settings → System → Battery optimisation**.
- Reels/Shorts/channel filtering needs the Accessibility service. Plain app blocking can also run on Usage access alone.

---

## Install on your phone

1. Copy `app/build/outputs/apk/release/app-release.apk` to the phone (USB, Drive, Telegram, whatever).
2. Tap it and allow "install unknown apps" for the file manager/browser you used.
3. Open Regain → **Guard** tab and grant, in this order:
   - **Display over other apps** (needed for the focus wall)
   - **Accessibility → Regain Focus Guard** (needed for Reels/Shorts/channel detection)
   - **Usage access** (backup foreground detection — recommended)
   - **Battery optimisation → Unrestricted** (recommended)
4. Flip the **Focus Guard** switch on.
5. Pick apps to block, or turn on Reels / Shorts / the YouTube allowlist.

No Google Play Services, no accounts, no network permission at all. Everything is stored in local SharedPreferences.

---

## Build from source

Requirements: JDK 17, Android SDK 35 (platform + build-tools 35.0.0), Gradle 8.9 (or use the wrapper).

```powershell
$env:JAVA_HOME="C:\Users\Dell\AppData\Local\Android\jdk-17.0.20.1+1"
$env:ANDROID_HOME="C:\Users\Dell\AppData\Local\Android\Sdk"
gradle assembleRelease
```

The release build is signed with `app/regain-release.jks` (store/key password `regainclone`) so the APK
installs directly. Replace it with your own keystore for real distribution.

### Project layout

```
app/src/main/java/ai/regain/clone/
├── MainActivity.kt              bottom-nav shell, permission refresh
├── RegainApp.kt                 startup wiring
├── data/Store.kt                settings, sessions, subjects, stats (SharedPreferences + JSON)
├── focus/FocusEngine.kt         timer state machine, session recording, persistence
├── focus/FocusService.kt        foreground countdown notification
├── audio/Ambience.kt            procedural focus sounds (AudioTrack)
├── block/BlockAccessibilityService.kt   screen inspection loop
├── block/Detector.kt            Reels / Shorts / channel heuristics + debug readout
├── block/Enforcer.kt            rule evaluation + overlay decisions
├── block/OverlayController.kt   the full-screen focus wall
├── block/WatchdogService.kt     UsageStats backup + keeper of the process
├── block/Perms.kt               permission checks & settings shortcuts
└── ui/                          Compose screens (Home, Focus, Guard, Stats, Settings, Rooms)
```

## Known gaps / next steps
- Reels/Shorts rules are version-sensitive; add package-specific selectors if your app build differs.
- Focus rooms and leaderboard peers are synthetic (no backend).
- No cloud sync, no AI study companion, no screen-time charts for other apps.

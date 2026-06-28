# Marvelous Marble — decompiled & migrated Android Studio project

Reverse-engineered from `com.breel.wallpapers18` (Google / Breel "Pixel Live
Wallpapers") with [jadx](https://github.com/skylot/jadx), then **migrated to real
Gradle dependencies** and **trimmed to the "Marvelous Marble" wallpaper** (the
`celestialBodies` Earth/Moon/Pluto family).

## Status
- ✅ Opens in Android Studio, Gradle sync works (wrapper 8.11.1 / AGP 8.10.1).
- ✅ `./gradlew assembleDebug` builds a working `app-debug.apk`.
- ✅ All 8 methods jadx originally failed on have been **restored** from
  `jadx --show-bad-code` + embedded smali (search `Restored from jadx`).

## Open / build
1. Android Studio → **File ▸ Open** → this folder, let Gradle sync.
2. Or CLI: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

## What changed vs. raw jadx output
- **Bundled libraries replaced by real dependencies** (decompiled copies deleted):
  see `app/build.gradle`.
  - libGDX **1.9.6** (`gdx`, `gdx-backend-android`)
  - Android Support **27.1.1** (`appcompat-v7`, `support-v4`)
  - Google Play Services **11.8.0** (`awareness`, `base`, `tasks`, `auth`)
  - Protobuf **lite 3.0.1** (for the decompiled `StormProtos` weather messages)
- **Trimmed to Marvelous Marble**: removed the other wallpaper packages (cities,
  dioramas, delight, miniman, soundviz, surfandturf, tactile) and their assets.
  App Java went 3108 → ~117 files; assets 302 MB → 172 MB.
- Fixed many jadx artefacts (mis-resolved constants, nested-generic Builders,
  `while(true)` loops missing `break`, broken try-with-resources, private
  framework attrs, broken nine-patches, duplicate resources).

## Native libraries (`app/src/main/jniLibs`) — KEEP THESE
| `.so` | What | Why it stays |
|---|---|---|
| `libwallpapers-breel-jni.so` | Breel's own native engine (`loadLibrary("wallpapers-breel-jni")`) | No source — core of the wallpaper |
| `libwallpapers-breel-2018-jni.so` | Breel's own native (2018 build) | No source |
| `libgdx.so` | libGDX 1.9.6 native (`loadLibrary("gdx")`) | Matches the gdx dependency |
| `libc++.so`, `libjpeg.so` | C++ / JPEG runtimes the natives need | Required by the above |

## Methods restored from bytecode (search `Restored from jadx`)
jadx originally emitted these 8 methods as undecompilable stubs; they were
re-implemented from `jadx --show-bad-code` cross-checked against the embedded
smali, and now contain real logic:
- `celestialBodies/EarthEngine.zoomIn(String, boolean)` — zoom interaction
- `celestialBodies/PlanetEngine.animateWakeUp(String, boolean)` — wake animation
- `gdxoverride/ParticleEmitter.addParticles(int)` — particle emission
- `view/UserAwareWallpaperService$UserAwareEngine.updateUserPresence(...)`
- `view/controller/UserPresenceController.onBroadcastReceived(...)` — screen on/off/present
- `weather/CloudsProvider$CloudUpdatingTask.doInBackground(...)` — cloud download
- `weather/StormsProvider$StormsUpdatingTask.downloadFile(...)` — storm download
- `location/UserLocation.loadCountriesLocations()` — country→location fallback

Note: the two weather downloaders hit Google/weather.com 2018 endpoints that may
no longer respond; the code is faithful but those features depend on live remote
data. Behaviour should still be verified on a device.

## Caveats
This is **decompiled** code: reconstructed from bytecode, no original comments,
some synthetic names. Good for study and as a maintenance base, but the original
source is gone. The custom native `.so` files are opaque binaries.

`_decompiled_libs_backup/` (one level up) holds the deleted decompiled library
sources for reference.

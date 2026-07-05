# Marble Marvelous

Android live wallpaper that renders a 3D Earth with Filament on the Vulkan
backend. The wallpaper shows seasonal day cube maps, night city lights,
atmosphere, ocean specular, live OpenWeather cloud cover with bundled detail
shading, an aurora curtain driven by the real planetary Kp index, and a "you
are here" location marker.

## Current State

- Kotlin Android app with a direct Filament live-wallpaper renderer.
- Dependency injection via Hilt (`@HiltAndroidApp` / `@AndroidEntryPoint`).
- Networking via Ktor (`ktor-client-cio`).
- JSON via `kotlinx.serialization.json`.
- Date/time math via `kotlinx-datetime`.
- Runtime permission screen is a plain `View`.
- Vulkan is requested through Filament's Android backend.
- Cached cloud masks are generated from OpenWeather `clouds_new` tiles.
- Aurora activity is polled from the NOAA SWPC planetary Kp-index feed while
  the wallpaper is visible, and paused otherwise.
- The location marker shares one `UserLocationEarth` instance (Hilt
  `@Singleton`) across every wallpaper engine (home, lock screen, preview) in
  the process, instead of each engine loading its own permission/location
  state.
- Release builds are signed with the debug signing config.
- Runtime checked on a connected Android phone after release install.
- Default app strings are English.

## Requirements

- JDK 21 for Gradle. The repo pins a local JDK path in `gradle.properties`; update
  that path or remove it when your default JDK is already 21.
- Android SDK API 37 installed.

## Build

Open the project in Android Studio, or build from the command line:

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Generated APKs:

- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

Set the wallpaper through Android's live wallpaper picker:

```sh
adb shell am start -a android.service.wallpaper.CHANGE_LIVE_WALLPAPER \
  -n com.android.wallpaper.livepicker/.LiveWallpaperChange \
  --ecn android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT \
  com.phuchienngo.marblemarvelous/.filament.FilamentWallpaperService
```

## Toolchain

| | Version |
|---|---|
| Gradle / AGP | **9.6.1** / **9.2.1** |
| Kotlin / KSP | **2.3.21** / **2.3.9** |
| compileSdk / targetSdk / minSdk | **37** / **37** / **36** |
| JVM target | **17** |
| Filament | **1.72.0** |
| Kotlin Coroutines | **1.11.0** |
| Hilt (Dagger) | **2.60** |
| Ktor client (CIO engine) | **3.5.1** |
| kotlinx.serialization | **1.11.0** |
| kotlinx-datetime | **0.8.0** |

Gradle application id and namespace: `com.phuchienngo.marblemarvelous`.

KSP does not yet support AGP 9's built-in Kotlin, so `gradle.properties` opts
out with `android.builtInKotlin=false` and `android.newDsl=false` to keep the
standalone Kotlin Android plugin. Google's AGP 9.0 release notes state both
opt-outs are removed in AGP 10.0 — drop them (and the standalone Kotlin
plugin) once KSP ships a release that supports built-in Kotlin.

## Architecture

- `MarbleApplication.kt` is the `@HiltAndroidApp` entry point.
- `di/MarbleModule.kt` provides app-level runtime dependencies: the Ktor
  `HttpClient`, the OpenWeather API key, and the IO dispatcher used for
  genuinely blocking work (bitmap decode, file I/O).
- `filament/FilamentWallpaperService.kt` is `@AndroidEntryPoint` and hosts the
  live wallpaper service; it drives the Choreographer frame loop and gates
  the aurora/cloud refresh coroutines on wallpaper visibility.
- `filament/FilamentEarthRenderer.kt` owns the Filament engine, Vulkan
  backend, swapchain, camera, scene, and frame rendering. Multiple engines
  (home, lock screen, preview) can exist in the same process, retaining the
  renderer across surface loss.
- `location/UserLocationEarth.kt` is a Hilt `@Singleton` wrapping
  `LocationManager`, permission state, and the bundled country-fallback
  table; injected once and shared by every renderer instance.
- `space/AuroraActivityProvider.kt` fetches the NOAA planetary Kp index and
  maps it to a 0..1 aurora activity level.
- `app/src/main/materials/*.mat` holds the Filament material sources, and
  `app/src/main/assets/filament/*.filamat` holds the precompiled Vulkan mobile
  material packages loaded by the renderer.
- `filament/FilamentKtxCubeTextureArrayLoader.kt` uploads compressed KTX cube
  faces as six-layer texture arrays for the Vulkan renderer.
- `filament/FilamentG3dbEarthMesh.kt` parses the bundled Earth mesh directly
  without a libGDX asset loader.
- `weather/OpenWeatherClouds.kt` builds cached raw cloud-mask faces from
  OpenWeather map tiles over Ktor.

## Weather And Clouds

The renderer uses two cloud inputs:

- `cloudMaskMap`: live OpenWeather coverage generated in the app cache.
- `cloudDetailMap`: bundled `app/src/main/assets/earth/clouds.ktx` detail.

Cloud mask cache:

- `OpenWeatherClouds` downloads OpenWeather `clouds_new` mercator tiles at
  `SRC_ZOOM = 3` over Ktor (CIO engine).
- Tiles are sampled into six cube faces: `px`, `nx`, `py`, `ny`, `pz`, `nz`.
- Each face is `512 x 512` and stored as single-channel raw bytes:
  `{face}-shape-v2.r8`.
- Row smoothing and edge shaping run while writing each raw face.
- `FilamentEarthTextures` validates the six raw faces, uploads a Filament `R8`
  texture array off the main thread, skips the rebuild entirely when the
  freshly generated faces are byte-identical (CRC32 check) to what's already
  shown, and keeps the direct upload buffer alive for the texture lifetime.
- Missing or invalid cached faces use the bundled `earth/clouds.ktx`.

Shader cloud composition:

- Samples the live mask and bundled detail through a continuous tangent-space
  blur, plus a 3-octave value-noise fbm for fine structure beyond the source
  resolution.
- Boosts thin masks with a square-root blend.
- Uses the weather mask for realtime cloud placement and the bundled detail
  for local cloud texture.
- Computes a shadow-offset sample for soft cloud shadow and relief, with a
  silver-lining edge highlight and a golden-hour tint near the terminator.

OpenWeather API key:

- Resource name: `openweather_api_key`
- Set `OPENWEATHER_API_KEY` as a Gradle property or environment variable before
  building.
- The default value is empty, so the APK ships without a committed key and uses
  the bundled cloud texture until a key is provided.

## Aurora And Location Marker

- `AuroraActivityProvider` polls
  `https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json` every
  30 minutes while the wallpaper is visible, and stops polling when it isn't.
  The feed can return either a newer (array-of-objects) or legacy
  (array-of-arrays-with-header) shape; both are parsed defensively.
- The shader renders an animated auroral-oval curtain on the night side,
  gated on a cheap night-side check so the day hemisphere skips the aurora
  math entirely.
- `UserLocationEarth` resolves the device's last-known location (with a
  timezone/country-table fallback when permission isn't granted) and the
  shader draws a "you are here" marker with an expanding sonar-ping
  animation, gated on a cheap dot-product check so only nearby fragments run
  the marker math.

## Rendering And Power

- The live wallpaper runs on Filament with the Vulkan backend.
- The wallpaper service renders through Android `Choreographer`, scheduling
  the next frame `postFrameCallbackDelayed` to keep the ~18 FPS idle target
  without extra vsync wake-ups.
- Rendering stops when the wallpaper is not visible; the Filament engine is
  paused rather than destroyed, and cloud/aurora refresh coroutines are
  cancelled on visibility loss.
- The engine and its GPU resources are retained across surface loss
  (lock/unlock, app switches) — only the swap chain is recreated — and the
  swap chain is torn down without a main-thread `flushAndWait()` to avoid an
  ANR during Surface teardown.
- Texture assets are uploaded as compressed KTX/raw six-layer texture arrays and
  stay out of the Java heap after upload.

## Native Libraries

The APK ships only the native libraries pulled by Filament for `arm64-v8a`.
Materials are precompiled with Filament `matc`, so the runtime APK does not
include `filamat-android`.

Regenerate the precompiled materials after editing any `app/src/main/materials`
source with:

```
./gradlew :app:compileFilamentMaterials -Pfilament.matc=/path/to/matc
```

This rewrites the `.filamat` assets and the recorded source checksums. The
`FilamentMaterialSourceChecksumTest` unit test fails if a material source is
changed without regenerating its `.filamat`, so the drift is caught in CI even
though `matc` runs manually.

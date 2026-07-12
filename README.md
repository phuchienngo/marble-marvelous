# Marvelous Marble

Android live wallpaper that renders a 3D Earth with Filament on the Vulkan
backend. The wallpaper shows seasonal day cube maps, night city lights,
atmosphere, ocean specular, NASA MODIS cloud cover with bundled detail
shading, an aurora curtain driven by the real planetary Kp index, and a "you
are here" location marker.

## Current State

- Kotlin Android app with a direct Filament live-wallpaper renderer.
- App-scoped dependencies are managed by Hilt.
- Networking runs directly on `Dispatchers.IO` through Android HTTPS connections.
- NOAA and bundled country data use small format-specific parsers.
- Date/time math uses `java.time`.
- Runtime permission screen is a plain `View`.
- Vulkan is requested through Filament's Android backend.
- Cached cloud masks are generated from NASA GIBS MODIS cloud-fraction imagery.
- Aurora activity is polled from the NOAA SWPC planetary Kp-index feed while
  the wallpaper is visible, and paused otherwise.
- The location marker shares one app-scoped `UserLocationEarth` instance
  across every wallpaper engine (home, lock screen, preview) in
  the process, instead of each engine loading its own permission/location
  state.
- Release builds are signed with the debug signing config.
- Runtime checked on a connected Android phone after release install.
- Default app strings are English.

## Requirements

- Bazel 9.1.1 (pinned in `.bazelversion`).
- JDK 17 for the Bazel Kotlin/Java toolchains.
- Android SDK API 36 installed (`android-36` platform and `36.0.0` build-tools).
- Android NDK 25b+ installed (arm64 CC toolchain for the platform-based build).
- `ANDROID_HOME` pointing to the Android SDK root and `ANDROID_NDK_HOME` to the NDK.

## Build

Copy the user-specific Bazel config template and fill in your local paths:

```sh
cp user.bazelrc.template user.bazelrc
# edit user.bazelrc
```

Then build the release APK:

```sh
bazel build //:app --compilation_mode=opt
adb install -r bazel-bin/app.apk
```

If you prefer to keep paths in environment variables instead of `user.bazelrc`,
export them in your shell (see the comments in `user.bazelrc.template`).

Generated APK:

- Release: `bazel-bin/app.apk`

Set the wallpaper through Android's live wallpaper picker:

```sh
adb shell am start -a android.service.wallpaper.LIVE_WALLPAPER_CHOOSER
```

Then choose **Marvelous Marble** and tap **Set wallpaper**.

## Test

Run the unit tests with Bazel:

```sh
bazel test //:app_test
```

## Toolchain

| | Version |
|---|---|
| Bazel | **9.1.1** |
| rules_android | **0.7.3** |
| rules_android_ndk | **0.1.5** |
| Android NDK | **27.2.12479018** |
| rules_kotlin | **2.4.0** |
| rules_jvm_external | **7.0** |
| Kotlin | **2.4.0** |
| compileSdk / targetSdk / minSdk | **36** / **36** / **36** |
| JVM target | **17** |
| Filament | **1.72.0** |
| Kotlin Coroutines | **1.11.0** |
| HTTP | Android `HttpsURLConnection` on `Dispatchers.IO` |
| Hilt (Dagger) | **2.60** |

Application id and package: `com.phuchienngo.marblemarvelous`.

## Architecture

- `MarbleApplication.kt` is the `@HiltAndroidApp` entry point and extends the
  KSP-generated `Hilt_MarbleApplication` base.
- `filament/FilamentWallpaperService.kt` is an `@AndroidEntryPoint` and hosts the
  live wallpaper service; it drives the Choreographer frame loop and gates
  the aurora/cloud refresh coroutines on wallpaper visibility.
- `filament/FilamentEarthRenderer.kt` owns the Filament engine, Vulkan
  backend, swapchain, camera, scene, and frame rendering. Multiple engines
  (home, lock screen, preview) can exist in the same process, retaining the
  renderer across surface loss.
- `location/UserLocationEarth.kt` is an app-scoped wrapper around
  `LocationManager`, permission state, and the bundled country-fallback
  table; injected once and shared by every renderer instance.
- `space/AuroraActivityProvider.kt` fetches the NOAA planetary Kp index and
  maps it to a 0..1 aurora activity level.
- `materials/*.mat` holds the Filament material sources, and
  `assets/filament/*.filamat` holds the precompiled Vulkan mobile
  material packages loaded by the renderer.
- `filament/FilamentKtxCubeTextureArrayLoader.kt` uploads compressed KTX cube
  faces as six-layer texture arrays for the Vulkan renderer.
- `filament/FilamentG3dbEarthMesh.kt` parses the bundled Earth mesh directly
  without a libGDX asset loader.
- `weather/NasaClouds.kt` builds cached raw cloud-mask faces from public NASA
  GIBS MODIS imagery over the Android HTTPS stack.

## Weather And Clouds

The renderer uses two cloud inputs:

- `cloudMaskMap`: recent NASA MODIS coverage generated in the app cache.
- `cloudDetailMap`: bundled `assets/earth/clouds.ktx` detail.

Cloud mask cache:

- `NasaClouds` downloads keyless 4096 x 2048 global WMS images for MODIS Aqua
  and Terra day/night cloud-fraction layers.
- The latest complete UTC day is merged first, then one older day fills any
  remaining satellite swath gaps. Complete six-face caches are reused for the
  rest of that observation day.
- The equirectangular source is sampled into six cube faces: `px`, `nx`, `py`,
  `ny`, `pz`, `nz`.
- Each face is `1024 x 1024` and stored as single-channel raw bytes:
  `{face}-nasa-v5.r8`.
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

NASA GIBS is public and requires no account, API key, generated secret resource,
or build-time environment variable. If imagery is temporarily unavailable, the
renderer keeps the last valid cache or the bundled `earth/clouds.ktx` fallback.

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

Regenerate the precompiled materials after editing any `materials`
source with the Filament `matc` command-line tool:

```sh
matc -p mobile -a vulkan -o assets/filament/earth.filamat \
  materials/earth.mat
matc -p mobile -a vulkan -o assets/filament/stars.filamat \
  materials/stars.mat
```

Then update `materials/checksums.sha256` with the new source
hashes. The `FilamentMaterialSourceChecksumTest` unit test fails if a material
source is changed without regenerating its `.filamat`, so the drift is caught in
CI even though `matc` runs manually.

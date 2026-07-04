# Marble Marvelous

Android live wallpaper that renders a 3D Earth with Filament on the Vulkan
backend. The current wallpaper shows seasonal day cube maps, night city lights,
atmosphere, ocean specular, and cloud shading from bundled detail plus an
optional cached OpenWeather cloud mask.

## Current State

- Kotlin Android app with a direct Filament live-wallpaper renderer.
- Runtime permission screen is built with Jetpack Compose.
- Vulkan is requested through Filament's Android backend.
- Cached cloud masks can be generated from OpenWeather `clouds_new` tiles.
- Release builds are signed with the debug signing config.
- Runtime checked on a connected Android phone after release install.
- Default app strings are English.

## Requirements

- JDK 21 for Gradle. The repo pins a local JDK path in `gradle.properties`; update
  that path or remove it when your default JDK is already 21.
- Android SDK API 36 installed.

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
| Gradle / AGP | **8.14.3** / **8.13.2** |
| Kotlin | **2.2.21** |
| compileSdk / targetSdk / minSdk | **36** / **36** / **36** |
| JVM target | **17** |
| Filament | **1.72.0** |
| Kotlin Coroutines | **1.11.0** |
| Dagger | **2.60** |
| OkHttp | **5.4.0** |
| Jetpack Compose | BOM **2026.06.01**, Activity Compose **1.13.0** |

Gradle application id and namespace: `com.phuchienngo.marblemarvelous`.

## Architecture

- `di/MarbleComponent.kt` defines the Dagger 2 graph for app-level runtime
  dependencies such as `OkHttpClient`, the OpenWeather API key, and IO
  dispatcher.
- `filament/FilamentWallpaperService.kt` hosts the live wallpaper service.
- `filament/FilamentEarthRenderer.kt` owns the Filament engine, Vulkan backend,
  swapchain, camera, scene, and frame rendering.
- `app/src/main/materials/*.mat` holds the Filament material sources, and
  `app/src/main/assets/filament/*.filamat` holds the precompiled Vulkan mobile
  material packages loaded by the renderer.
- `filament/FilamentKtxCubeTextureArrayLoader.kt` uploads compressed KTX cube
  faces as six-layer texture arrays for the Vulkan renderer.
- `filament/FilamentG3dbEarthMesh.kt` parses the bundled Earth mesh directly
  without a libGDX asset loader.
- `weather/OpenWeatherClouds.kt` can build cached raw cloud-mask faces from
  OpenWeather map tiles.

## Weather And Clouds

The renderer uses two cloud inputs:

- `cloudMaskMap`: live OpenWeather coverage generated in the app cache.
- `cloudDetailMap`: bundled `app/src/main/assets/earth/clouds.ktx` detail.

Cloud mask cache:

- `OpenWeatherClouds` downloads OpenWeather `clouds_new` mercator tiles at
  `SRC_ZOOM = 3`.
- Tiles are sampled into six cube faces: `px`, `nx`, `py`, `ny`, `pz`, `nz`.
- Each face is `512 x 512` and stored as single-channel raw bytes:
  `{face}-shape-v2.r8`.
- Row smoothing and edge shaping run while writing each raw face.
- `FilamentEarthTextures` validates the six raw faces, uploads a Filament `R8`
  texture array, and keeps the direct upload buffer alive for the texture
  lifetime.
- Missing or invalid cached faces use the bundled `earth/clouds.ktx`.

Shader cloud composition:

- Samples the live mask and bundled detail through a continuous tangent-space
  blur.
- Boosts thin masks with a square-root blend.
- Uses the weather mask for realtime cloud placement and the bundled detail for
  local cloud texture.
- Computes a shadow-offset sample for soft cloud shadow and relief.

OpenWeather API key:

- Resource name: `openweather_api_key`
- Set `OPENWEATHER_API_KEY` as a Gradle property or environment variable before
  building.
- The default value is empty, so the APK ships without a committed key and uses
  the bundled cloud texture until a key is provided.

## Rendering And Power

- The live wallpaper runs on Filament with the Vulkan backend.
- The wallpaper service renders through Android `Choreographer`.
- Idle render target is 18 FPS.
- Rendering stops when the wallpaper is not visible.
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

# Marble Marvelous

Android live wallpaper that renders a 3D Earth with libGDX and GLSL. The current
wallpaper shows day and night cube maps, city lights, atmosphere, ocean specular,
and a live OpenWeather cloud mask blended with bundled cloud detail.

## Current State

- Kotlin Android app with a libGDX wallpaper renderer.
- Runtime permission screen is built with Jetpack Compose.
- OpenGL ES 3.1 is required by the wallpaper manifest.
- Live cloud refresh uses OpenWeather `clouds_new` tiles.
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
  com.phuchienngo.marblemarvelous/.earth.EarthWallpaperService
```

## Toolchain

| | Version |
|---|---|
| Gradle / AGP | **8.14.3** / **8.13.2** |
| Kotlin / KSP | **2.2.21** / **2.2.21-2.0.5** |
| compileSdk / targetSdk / minSdk | **36** / **35** / **24** |
| JVM target | **17** |
| libGDX | **1.14.2** |
| Dagger 2 | **2.60** |
| Kotlin Coroutines | **1.11.0** |
| OkHttp | **5.4.0** |
| Jetpack Compose | BOM **2026.06.00**, Activity Compose **1.13.0** |

Gradle application id and namespace: `com.phuchienngo.marblemarvelous`.

## Architecture

- `earth/EarthWallpaperService` hosts the live wallpaper service.
- `wallpaper/BaseWallpaperEngine.kt` coordinates lifecycle, visibility, render
  requests, page swipes, and resume warmup.
- `earth/EarthEngine.kt` owns the libGDX scene, camera, assets, weather updates,
  and frame-rate target selection.
- `earth/earth.frag` renders day/night lighting, terminator color, atmosphere,
  city lights, specular ocean, cloud color, and cloud shadow.
- `weather/CloudsProvider.kt` supplies the cloud cubemap used by the renderer.
- `weather/OpenWeatherClouds.kt` builds the live cloud mask from OpenWeather
  map tiles.
- Dagger components wire wallpaper-scope and engine-scope dependencies.

## Weather And Clouds

The renderer uses two cloud inputs:

- `cloudMaskMap`: live OpenWeather coverage generated in the app cache.
- `cloudDetailMap`: bundled `app/src/main/assets/earth/clouds.ktx` detail.

Cloud refresh flow:

- `OpenWeatherClouds` downloads OpenWeather `clouds_new` mercator tiles at
  `SRC_ZOOM = 3`.
- Tiles are sampled into six cube faces: `px`, `nx`, `py`, `ny`, `pz`, `nz`.
- Each face is `512 x 512` and stored as single-channel raw bytes:
  `{face}-shape-v2.r8`.
- Row smoothing and edge shaping run while writing each raw face.
- `CloudsProvider.CloudCubeMap` validates the six raw faces, maps each file with
  `FileChannel.MapMode.READ_ONLY`, allocates `GL_R8` cube faces, and uploads with
  `glTexSubImage2D`.
- Cloud refresh interval is one hour.
- Missing API key, failed download, or invalid cached faces use the bundled
  `earth/clouds.ktx`.

Shader cloud composition:

- Samples the live mask and bundled detail through a continuous tangent-space
  blur.
- Boosts thin masks with a square-root blend.
- Uses the weather mask for realtime cloud placement and the bundled detail for
  local cloud texture.
- Computes a shadow-offset sample for soft cloud shadow and relief.

OpenWeather API key:

- Resource name: `openweather_api_key`
- Location: `app/src/main/res/values/strings.xml`
- The value is packaged into the APK.

## Rendering And Power

- Idle render target is 18 FPS.
- Page swipe target is 30 FPS.
- Resume, rotation, zoom, and AOD animations target 60 FPS.
- Power-save mode halves the selected target FPS.
- The renderer uses request-driven rendering with a dedicated `FPSThrottler`
  thread.
- `ResumeRenderWarmup` keeps resume rendering responsive for the first 750 ms.

## Native Libraries

`app/src/main/jniLibs` contains libGDX native `libgdx.so` for:

- `arm64-v8a`
- `armeabi-v7a`
- `x86`
- `x86_64`

# Lufoss

Lufoss is a native Android walking-navigation app written in Kotlin with Jetpack Compose. It is a full rewrite of
the single-file ArWalk web page: same features, same free keyless services, same own-built map engine.

## What it does

- Draws its own map from OpenStreetMap raster tiles. There is no mapping library: tile maths, caching, touch,
  rotation and the follow camera are all in `map/` and `ui/MapDraw.kt`.
- Searches places while you type (Photon), finds exact house numbers on submit (Nominatim, then Overpass), and can
  limit suggestions to your home country.
- Plans walking routes (OpenStreetMap Germany routing service) and round-trip walks of 10 to 60 minutes that avoid
  doubling back.
- Walks you along the route: follow camera, heading-up map, spoken turns at three distances, automatic re-routing,
  arrival detection, and a Preview mode that walks the route by itself without GPS.
- Accessible: screen reader live regions for progress and turns, custom map actions for TalkBack, hardware keyboard
  and mouse support, reduced-motion support (follows the system animation setting), 48 dp touch targets.
- Light and dark themes (the Aengus palette). Dark mode also darkens the map tiles.

No Google Play services, no API keys, no analytics. Location comes from the platform `LocationManager`; speech from
the system text-to-speech engine.

## Build without a command line

GitHub builds the app for you:

1. Create a new repository on github.com.
2. Unzip Lufoss.zip and upload everything inside the `Lufoss` folder (use **Add file > Upload files** and drag the
   contents in). Make sure the hidden `.github` folder comes too, because it holds the build workflow.
3. Open the **Actions** tab. The **Build** workflow starts by itself. You can also start it with **Run workflow**.
4. When the **Test and build** job finishes, open the run and download the **Lufoss-debug-apk** artifact.
5. Unzip it, copy the `.apk` to your phone and open it. Android asks once to allow installs from that app.

The app targets API 35 and runs on Android 17.

Prefer Android Studio? Open the folder, let it sync, and press Run. Nothing else is needed.

### Style (ktlint)

ktlint runs through the `org.jlleitschuh.gradle.ktlint` Gradle plugin. Rules are in `.editorconfig` (Android Kotlin
style, 120 columns, no trailing commas, Composable functions may be PascalCase). Style problems are reported by the
**ktlint** job but do not stop the APK building. In Android Studio, Code > Reformat Code fixes most of them.

### Continuous integration

`.github/workflows/build.yml` runs on every push and pull request:

1. **ktlint**: `gradle ktlintCheck`. A failure uploads the reports.
2. **Test and build** (in parallel): unit tests, then `assembleDebug`, then uploads the debug APK as an artifact.

`.github/dependabot.yml` keeps Gradle dependencies and the actions up to date weekly.

## Layout

```
app/src/main/java/io/github/ardalorn/lufoss/
  Cfg.kt                 every limit, timing and URL (ported from ArWalk's settings block)
  geo/GeoMath.kt         Web Mercator, bearings, distances
  data/                  models, HTTP, geocoding, routing, round-trip planner, preferences, wording
  device/                location, compass, speech
  map/                   MapController (camera, follow, heading) and TileStore (tile cache)
  nav/                   NavSession (turn by turn) and RouteMath (progress along a route)
  ui/                    ViewModel, Compose screen, sheets, dialogs, canvas drawing, gestures
```

## Notes

- Fonts: headings use the platform serif face. To match the web page exactly, add Fraunces and Atkinson
  Hyperlegible to `res/font` and point `ui/Theme.kt` at them.
- Walking only follows you while the app is on screen (the screen is kept awake). A foreground location service
  would be the next step for screen-off navigation.
- Please keep the user agent in `Cfg.USER_AGENT` accurate: the OpenStreetMap services require apps to identify
  themselves, and their usage policies apply (light, personal use; no bulk requests).

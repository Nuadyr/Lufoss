# Lufoss

Hobbyist project,  expect staggered updates and patches only at degradation of functionality. 

**Walking navigation that just works. No account, no API keys, no tracking.**

Lufoss is a free Android app that finds a walking route and talks you along it. Search for a place, tap Start, and follow the map. Or pick a time and let Lufoss plan a round-trip walk that brings you back to where you began.

---

## Features

### Find your way
- **Search as you type.** Place suggestions appear while you write.
- **Exact addresses.** Search for a house number and Lufoss finds the building, not just the street.
- **Tap to pin.** Tap anywhere on the map to drop a pin and get directions.
- **Home-country filter.** Limit suggestions to your own country, or search worldwide.

### Round-trip walks
- **Choose a time: 10, 20, 30, 40, 50 or 60 minutes.** Lufoss plans a loop that starts and ends at your saved start point.
- **Loops that avoid doubling back.** If a clean loop can't be found, Lufoss tells you.
- **Not keen on the route?** Tap **Another route** for a different loop.

### Turn-by-turn walking
- **Follow camera.** The map follows you, turns to face the way you're heading, and zooms in for turns.
- **Spoken directions.** Turns are announced at three distances, so you never have to look at the screen. Voice can be switched off at any time.
- **Automatic re-routing.** Take a wrong turn and Lufoss finds a new way.
- **Arrival detection.** You are told when you get there.
- **Preview mode.** Walk the route on screen without GPS. Good for checking a route before you leave.
- **Look around freely.** Drag the map to explore, and it returns to you after a few seconds.

### A map that feels good
- Smooth drag, pinch-to-zoom, two-finger rotate, double-tap zoom.
- Light and dark themes. Dark mode also darkens the map.
- Works in portrait and landscape, including wide screens.

### Accessible by design
- TalkBack support, including spoken progress and turn updates.
- Keyboard and mouse support.
- Respects the system "reduce animations" setting.
- Large 48 dp touch targets.

### Private by default
- No account, no ads, no analytics.
- No Google Play services needed.
- Only your chosen start point is saved, and only on your device. Live location is never stored.

---

## Getting started

1. **Install** the app (see [Install](#install)).
2. **Set your start point** on first launch. Search for an address, use your GPS position, or choose a spot on the map.
3. **Pick a destination** by searching or tapping the map. Or tap the **round-trip** button and choose how long you want to walk.
4. **Tap Start** and follow the voice and the map.

> Tip: tap **Steps** at any time to see the full list of turns, and **Voice** to mute or unmute spoken directions.

### Permissions

| Permission | Why |
| --- | --- |
| Location | Shows your position and guides you while walking. Only used when you pick GPS as your start point or press Start. |
| Internet | Loads map tiles, search results and routes. |

---

## Install

**Requirements:** Android 8.0 (API 26) or newer. A GPS is recommended but not required.

### Build it with GitHub (no command line)
1. Create a repository, upload `Lufoss.zip` to the `source` folder, and commit. The **Unpack and build** workflow unpacks the project and builds the app by itself. You can also start it with **Run workflow** in the **Actions** tab.
2. When the run finishes, open it and download the **Lufoss-release-apk** artifact (kept for 7 days).
3. Unzip it, copy the `.apk` to your phone and open it. Android asks once to allow installs from that source.

This is an optimised release build made from your own copy of the code, so install only APKs you built yourself or got from someone you trust. Android only installs signed APKs. The build signs the APK when a signing key is set up (see [Signing your own builds](#signing-your-own-builds)) and otherwise leaves it unsigned.

### Build it yourself
1. Install [Android Studio](https://developer.android.com/studio) and open the project folder.
2. Let Gradle sync, then press **Run**.

Or from the command line (JDK 17 and Gradle required):

```bash
gradle assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`.

---

## Good to know

- **Keep the screen on while walking.** Navigation follows you while Lufoss is on screen, and the screen is kept awake for you. Screen-off navigation is not supported yet.
- **Needs a data connection** for maps, search and routing. Recently viewed map tiles and results are cached on your device and expire after one hour.
- **Fix the map.** Spotted a missing path or wrong street? Use **About > Report or correct a map issue** to improve OpenStreetMap for everyone.

## Privacy and security

Lufoss has no server of its own. To work, it sends your search words and route end points to these free public services:

| Service | Used for |
| --- | --- |
| [OpenStreetMap](https://www.openstreetmap.org) | Map tiles |
| [Photon](https://photon.komoot.io) | Place suggestions (partial text is sent as you type) |
| [Nominatim](https://nominatim.openstreetmap.org) | Exact address search |
| [Overpass](https://overpass-api.de) | Numbered-building lookups |
| [OpenStreetMap Germany routing](https://routing.openstreetmap.de) | Walking routes |

Like any internet request, these also see your IP address. These services are run by volunteers and ask for light, personal use. Please don't use Lufoss for bulk requests.

**What stays on your device**
- **Your start point**, which may be your home address, is saved in the app's private storage. Remove it by changing your start point or clearing the app's data.
- **A short-lived cache** of map tiles and search and route responses. Entries expire after one hour, and the whole cache is wiped the next time the app goes online after that hour has passed. If you don't open the app, expired files stay until you do. Clearing the app's storage in Android settings removes them immediately.
- **Live location is never saved.**

**How the app protects you**
- All network traffic uses HTTPS. Plain HTTP is switched off.
- Android backup is disabled, so your start point isn't copied to cloud backups.
- Only location and internet permissions are requested, and location is used only while you are navigating or choosing a start point.
- No account, no ads, no analytics, no third-party SDKs.

**Reporting a vulnerability.** Please don't open a public issue. Use the repository's **Security > Report a vulnerability** option, or contact the maintainer privately.

---

## For developers

Lufoss is written in Kotlin with Jetpack Compose. The map engine is built in-house, with no mapping library.

```
app/src/main/java/io/github/nuadyr/lufoss/
  Cfg.kt        Every limit, timing and URL in one place (including the one-hour cache limit)
  geo/          Map and distance maths
  data/         Search, routing, round-trip planner, preferences
  device/       Location, compass, speech
  map/          Camera, follow mode, tile cache
  nav/          Turn-by-turn and route progress
  ui/           Screens, sheets, dialogs, map drawing, gestures
```

**Run checks locally**

```bash
gradle ktlintCheck          # code style
gradle testReleaseUnitTest  # unit tests
```

GitHub Actions runs style checks, unit tests and a release build on every push and pull request. Dependabot keeps dependencies up to date weekly.

## License

Released under the [GNU General Public License v3.0](LICENSE).

Map data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright).

# Lufoss

[![License: GNU GPL v3.0](https://img.shields.io/github/license/Nuadyr/Lufoss)](LICENSE)
[![Build status of the main branch](https://github.com/Nuadyr/Lufoss/actions/workflows/build.yml/badge.svg)](https://github.com/Nuadyr/Lufoss/actions/workflows/build.yml)
[![Requires Android 8.0 or newer](https://img.shields.io/badge/Android-8.0%2B-0d47a1)](#download)
[![Ads: none](https://img.shields.io/badge/Ads-none-1b5e20)](#privacy-and-data-safety)
[![Analytics: none](https://img.shields.io/badge/Analytics-none-1b5e20)](#privacy-and-data-safety)
[![Account: not required](https://img.shields.io/badge/Account-not_required-1b5e20)](#privacy-and-data-safety)
[![Network traffic: HTTPS only](https://img.shields.io/badge/Network-HTTPS_only-1b5e20)](#privacy-and-data-safety)

**Simple walking navigation with spoken directions and round-trip walks.**

No account, no API keys, no ads, no tracking.

Lufoss is a free Android app that finds a walking route and talks you along it. Search for a place, tap Start, and follow the map. Or pick a time and let Lufoss plan a round-trip walk that brings you back to where you began.

> Hobbyist project. Expect staggered updates, with patches only when functionality degrades.

## App details

| | |
| --- | --- |
| Platform | Android 8.0 (API 26) or newer |
| Price | Free, no ads, no in-app purchases |
| Current version | 1.0.3 |
| License | [GNU General Public License v3.0](LICENSE) |
| Package name | `io.github.nuadyr.lufoss` |
| Permissions | Location, Internet |
| Google Play services | Not needed |
| Languages | English |
| Source code | This repository |

## Download

Pick whichever route suits you. All three install the same signed APK from this repository's [GitHub Releases](https://github.com/Nuadyr/Lufoss/releases). Installing through Obtainium or Komi Store also gives you update notifications.

<p>
  <a href="https://github.com/Nuadyr/Lufoss/releases/latest"><img src="https://github.com/machiav3lli/oandbackupx/raw/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub: download the latest Lufoss APK" height="80"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.github.nuadyr.lufoss%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FNuadyr%2FLufoss%22%2C%22author%22%3A%22Nuadyr%22%2C%22name%22%3A%22Lufoss%22%7D"><img src="assets/badges/badge_obtainium.png" alt="Get it on Obtainium: add Lufoss to Obtainium" height="80"></a>
  <a href="https://github.com/kurikomi-labs/komi-store/releases/latest"><img src="assets/badges/install-with-komi-store.svg" alt="Install with Komi Store, formerly GitHub Store" height="80"></a>
</p>

- **GitHub Releases:** install the APK by hand.
- **Obtainium:** tracks releases straight from GitHub and tells you about updates.
- **Komi Store** (formerly GitHub Store): browse and install GitHub releases like an app store. The button gets you Komi Store itself.

### Install with Obtainium

1. Install [Obtainium](https://github.com/ImranR98/Obtainium/releases) if you do not have it.
2. Tap the **Add to Obtainium** button above on your phone. Obtainium opens with Lufoss ready to add.
3. Or add it by hand: open Obtainium, go to **Add app**, paste `https://github.com/Nuadyr/Lufoss` into **App source URL**, and tap **Add**.
4. Tap **Install**. Obtainium will tell you when a new version is out.

### Install with Komi Store

1. Install [Komi Store](https://github.com/kurikomi-labs/komi-store/releases/latest). It was called GitHub Store until version 1.9.1.
2. Open Komi Store and find **Nuadyr/Lufoss**.
3. Tap **Install**.

### Install the APK by hand

1. Open the [latest release](https://github.com/Nuadyr/Lufoss/releases/latest) on your phone.
2. Download `Lufoss-<version>.apk`.
3. Open the file. Android asks once to allow installs from your browser or file manager.

Each release also includes a `.sha256` file. Compare it with the checksum of your download if you want to check the file arrived intact.

> The release APK is signed with the project's own key, and all three routes above deliver that same file. An APK you built yourself is signed with your key (or not at all), so Android will not let a release APK update it. Uninstall the self-built copy first. Your start point is stored in the app, so you will set it again.

## Highlights

- **Walk-first navigation.** Turn-by-turn directions made for walking, with the map following you.
- **Round-trip walks.** Choose 10 to 60 minutes and get a loop back to where you started.
- **Spoken directions.** Hear each turn at three distances without looking at the screen.
- **Private by design.** No account, no ads, no analytics. Live location is never saved.
- **Accessible.** TalkBack, keyboard, mouse and reduced-motion support, with large touch targets.
- **Light and dark themes.** Dark mode darkens the map too.

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

## Getting started

1. **Install** the app (see [Download](#download)).
2. **Set your start point** on first launch. Search for an address, use your GPS position, or choose a spot on the map.
3. **Pick a destination** by searching or tapping the map. Or tap the **round-trip** button and choose how long you want to walk.
4. **Tap Start** and follow the voice and the map.

> Tip: tap **Steps** at any time to see the full list of turns, and **Voice** to mute or unmute spoken directions.

## Permissions

| Permission | Why |
| --- | --- |
| Location | Shows your position and guides you while walking. Only used when you pick GPS as your start point or press Start. |
| Internet | Loads map tiles, search results and routes. |

## Privacy and data safety

- No account, no ads, no analytics, no third-party SDKs.
- Only your chosen start point is saved, and only on your device. Live location is never stored.
- All network traffic uses HTTPS. Plain HTTP is switched off.
- Android backup is disabled, so your start point isn't copied to cloud backups.
- No Google Play services needed.

### Who sees what

Lufoss has no server of its own. To work, it sends your search words and route end points to these free public services:

| Service | Used for |
| --- | --- |
| [OpenStreetMap](https://www.openstreetmap.org) | Map tiles |
| [Photon](https://photon.komoot.io) | Place suggestions (partial text is sent as you type) |
| [Nominatim](https://nominatim.openstreetmap.org) | Exact address search |
| [Overpass](https://overpass-api.de) | Numbered-building lookups |
| [OpenStreetMap Germany routing](https://routing.openstreetmap.de) | Walking routes |

Like any internet request, these also see your IP address. These services are run by volunteers and ask for light, personal use. Please don't use Lufoss for bulk requests.

### What stays on your device

- **Your start point**, which may be your home address, is saved in the app's private storage. Remove it by changing your start point or clearing the app's data.
- **A short-lived cache** of map tiles and search and route responses. Entries expire after one hour, and the whole cache is wiped the next time the app goes online after that hour has passed. If you don't open the app, expired files stay until you do. Clearing the app's storage in Android settings removes them immediately.
- **Live location is never saved.**

## Good to know

- **Keep the screen on while walking.** Navigation follows you while Lufoss is on screen, and the screen is kept awake for you. Screen-off navigation is not supported yet.
- **Needs a data connection** for maps, search and routing. Recently viewed map tiles and results are cached on your device and expire after one hour.
- **Fix the map.** Spotted a missing path or wrong street? Use **About > Report or correct a map issue** to improve OpenStreetMap for everyone.

## What's new

### 1.0.3
Removes a leftover folder from the repository. No change to how the app works.

Earlier versions are listed in [`fastlane/metadata/android/en-US/changelogs`](fastlane/metadata/android/en-US/changelogs).

## Support and security

- **Bugs and ideas:** open an [issue](https://github.com/Nuadyr/Lufoss/issues).
- **Reporting a vulnerability:** please don't open a public issue. Use the repository's **Security > Report a vulnerability** option, or contact the maintainer privately.

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

### Build it yourself

1. Install [Android Studio](https://developer.android.com/studio) and open the project folder.
2. Let Gradle sync, then press **Run**.

Or from the command line (JDK 17 and Gradle required):

```bash
gradle assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`. Without a signing key set up, the release APK is left unsigned, and Android will not install an unsigned APK.

### Build it with GitHub (no command line)

1. Create a repository, upload `Lufoss.zip` to the `source` folder, and commit. The **Unpack and build** workflow unpacks the project and builds the app by itself. You can also start it with **Run workflow** in the **Actions** tab.
2. When the run finishes, open it and download the **Lufoss-release-apk** artifact (kept for 7 days).
3. Unzip it, copy the `.apk` to your phone and open it.

Install only APKs you built yourself or got from someone you trust.

### Publishing a release

Pushing a version tag such as `v1.0.3` runs the **Release** workflow. The tag must match `versionName` in `app/build.gradle.kts`. The workflow runs the style check and unit tests, builds and signs the APK, and attaches `Lufoss-<version>.apk` and a `.sha256` file to the GitHub release. It needs the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Obtainium and Komi Store both read these releases.

### Run checks locally

```bash
gradle ktlintCheck          # code style
gradle testReleaseUnitTest  # unit tests
```

GitHub Actions runs style checks, unit tests and a release build on every push and pull request. Dependabot keeps dependencies up to date weekly.

## License

Released under the [GNU General Public License v3.0](LICENSE).

Map data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright).

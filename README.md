<div align="center">

# Lufoss

**Simple walking navigation with spoken directions and round-trip walks.**

Free Android app. No account, no ads, no tracking.

<a href="https://github.com/Nuadyr/Lufoss/releases/latest"><img src="https://github.com/machiav3lli/oandbackupx/raw/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub: download the latest Lufoss APK" height="80"></a>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.github.nuadyr.lufoss%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FNuadyr%2FLufoss%22%2C%22author%22%3A%22Nuadyr%22%2C%22name%22%3A%22Lufoss%22%7D"><img src="assets/badges/badge_obtainium.png" alt="Get it on Obtainium: add Lufoss to Obtainium" height="80"></a>
<a href="https://github.com/kurikomi-labs/komi-store/releases/latest"><img src="assets/badges/install-with-komi-store.svg" alt="Install with Komi Store, formerly GitHub Store" height="80"></a>

<a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-0d47a1" alt="License: GNU GPL version 3" height="28"></a>
<a href="#details"><img src="https://img.shields.io/badge/Android-8.0%2B-0d47a1" alt="Requires Android 8.0 or newer" height="28"></a>
<a href="#privacy"><img src="https://img.shields.io/badge/Ads_and_analytics-none-1b5e20" alt="Ads and analytics: none" height="28"></a>
<a href="#privacy"><img src="https://img.shields.io/badge/Account-not_required-1b5e20" alt="Account: not required" height="28"></a>

</div>

Search for a place, tap Start, and follow the map. Or pick a time and let Lufoss plan a round-trip walk that brings you back to where you began.

## Install

All three buttons give you the same signed APK from [GitHub Releases](https://github.com/Nuadyr/Lufoss/releases).

- **Obtainium:** tap its button on your phone. You get update alerts.
- **Komi Store** (formerly GitHub Store): install it, then find **Nuadyr/Lufoss**. You get update alerts.
- **By hand:** open the latest release and download `Lufoss-<version>.apk`.

Obtainium not opening? Add `https://github.com/Nuadyr/Lufoss` under **Add app**.

## Features

### Find your way
- **Search as you type**, down to the house number
- **Tap the map** to drop a pin and get directions
- **Home-country filter**, or search worldwide

### Round-trip walks
- **Pick 10 to 60 minutes** in 10-minute steps
- **Loops that don't double back**, or a clear message if none fits
- **Another route** in one tap

### Turn-by-turn walking
- **Spoken directions** at three distances, with voice on or off
- **Follow camera** that faces your heading and zooms for turns
- **Auto re-routing** and arrival alerts
- **Preview mode** to walk a route on screen without GPS

### A map that feels good
- Drag, pinch, rotate and double-tap zoom
- Light and dark themes, with a darkened map
- Portrait and landscape

### Accessible by design
- TalkBack support, with spoken progress
- Keyboard and mouse support
- Respects "reduce animations"
- Large 48 dp touch targets

## Getting started

1. **Set your start point** by search, GPS or the map.
2. **Pick a destination**, or tap **round-trip** and choose a time.
3. **Tap Start.** Tap **Steps** for the full list of turns.

## Code choices

- **Its own map engine.** Kotlin and Jetpack Compose, no mapping library.
- **No Google Play services.** Android's own text-to-speech and compass.
- **A careful loop planner.** It adjusts a triangle loop until the walk matches your time, tries several directions, and never offers a U-turn.
- **Light on permissions.** Location and internet only. HTTPS only, backup off.
- **Light on memory.** Map tiles are decoded in a compact format.
- **One config file** for every limit, timing and URL.
- **Checked on every push** with unit tests and a style check.

## Privacy

- **Only your start point is saved**, privately on your device.
- **Live location is never stored.**
- **Results are cached for one hour at most.**
- **Permissions:** location (only when you pick GPS or press Start) and internet.

<details>
<summary>Which services see my searches?</summary>

Lufoss has no server. Your search words and route end points go to these free public services, which also see your IP address:

- [OpenStreetMap](https://www.openstreetmap.org) for map tiles
- [Photon](https://photon.komoot.io) for place suggestions
- [Nominatim](https://nominatim.openstreetmap.org) for exact addresses
- [Overpass](https://overpass-api.de) for numbered buildings
- [OpenStreetMap Germany routing](https://routing.openstreetmap.de) for walking routes

They are run by volunteers and ask for light, personal use. Please don't use Lufoss for bulk requests.

</details>

## Good to know

- **Keep the screen on while walking.** Lufoss does this for you. Screen-off navigation isn't supported yet.
- **Needs a data connection** for maps, search and routes.
- **Wrong street on the map?** Open **About**, then **Report or correct a map issue**, to fix OpenStreetMap for everyone.
- **Hobbyist project.** Expect staggered updates, with patches only when something breaks.

## Details

- **Platform:** Android 8.0 (API 26) or newer
- **Price:** free
- **Version:** 1.0.3
- **License:** [GNU GPL v3.0](LICENSE)
- **Bugs and ideas:** [open an issue](https://github.com/Nuadyr/Lufoss/issues)
- **Security:** don't open a public issue. Open this repository's **Security** tab and choose **Report a vulnerability**.
- **Build status:** see the [latest build runs](https://github.com/Nuadyr/Lufoss/actions/workflows/build.yml)

Map data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright).

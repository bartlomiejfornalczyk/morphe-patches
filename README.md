# 🗺️🎵 Maps&Music patches

Custom Morphe patches enabling seamless YouTube Music mini-player integration directly inside Google Maps navigation, alongside package renaming, map data restoration, and microG support.

## ❓ About

This patch bundle provides custom patches and extra features focused on enabling YouTube Music / YT Morphe mini-player integration directly inside Google Maps navigation, alongside core Google Maps functionality:
- **YouTube Music Mini Player in Google Maps**: Seamlessly integrates YouTube Music (YT Morphe) into Google Maps navigation without navigation start crashes.
- **External Media Browser Connections**: Unlocks YouTube Music's MediaBrowserService entitlement gate and allowlist so Google Maps, Android Auto, and external controllers can browse and play media.
- **Maps Enhancements & Customization**: Bundles all essential Maps patches (Customization screen, Black AMOLED theme, Power saving mode, Zoom controls, Location provider toggle, Play Services check bypass, and Telemetry removal).

> [!WARNING]
> **Compatibility Note**: This repository contains a curated, standalone set of Google Maps patches adapted directly from [bearinmindcat/morphe-patches](https://github.com/bearinmindcat/morphe-patches). Because this bundle focuses specifically on stable YouTube Music / YT Morphe integration, it uses a streamlined media provider hook to prevent crashes on navigation start. As a result, it is **probably not compatible with newer releases of bearinmindcat's patches** due to differences in how media players are handled. You should use this repository as an all-in-one source for the patches included here.

### 🙏 Credits & Attribution

Enormous credit and special thanks go to **[bearinmindcat](https://github.com/bearinmindcat/morphe-patches)** for creating and maintaining the upstream Google Maps patches adapted in this repository:
- **Customization screen**
- **Black theme** (AMOLED dark styling & resources)
- **Power saving mode**
- **Zoom controls in navigation**
- **Location provider toggle & Network location fallback**
- **Restore map data**
- **Change package name**
- **Bypass Play Services checks**
- **Remove telemetry & Remove permissions**
- **Add microG support**

## 🚀 Quick Setup & Usage Guide

### 1. Add Patches to Morphe Manager
1. Install [Morphe Manager](https://morphe.software) on your Android device.
2. Add this repository as a custom source:
   - **One-click link**: [Click here to add patches to Morphe Manager](https://morphe.software/add-source?github=bartlomiejfornalczyk/morphe-patches)
   - **Or manually**: Open Morphe Manager ➔ **Settings** ➔ **Sources** ➔ Add `https://github.com/bartlomiejfornalczyk/morphe-patches`

---

### 2. Patching YouTube Music & Customizing Driving Mode Tiles
1. In Morphe Manager, select **YouTube Music** and ensure the **Allow external media browser connections** patch is checked.
2. ⚙️ **Customizing Driving Mode Tiles (On/Off Sliders)**:
   - **Tap the gear / options icon next to "Allow external media browser connections" *before* tapping Patch.**
   - Every tile has an individual On/Off toggle:
     - `Tile: My Supermix` — Personalized endless radio
     - `Tile: Liked Music` — Your favorite songs library
     - `Tile: Like Current Song` — Quick thumbs-up button for the currently playing track
     - `Tile: Resume Playback` — Quick action to resume / unpause playback
     - `Tile: Downloaded Songs` — Offline library
     - `Tile: Mixed for You` — Commute & road trip rock hits
     - `Tile: Pop Certified` — Certified pop hits
     - `Tile: Dance Party Hits` — Upbeat dance & EDM
     - `Tile: Pump-Up Energy` — High energy driving tracks
     - `Tile: Trending 20` — Top 20 charts
     - `Tile: Trending Music Videos` — Popular video hits
     - `Tile: Popular Right Now` — Top trending hits
   - > [!TIP]
     > **Why 9 tiles?** Google Maps driving mode renders a strict **3x3 grid (maximum 9 tiles)**. By default, the top 9 most useful tiles (including Like Current Song & Resume Playback) are enabled, while the 3 trending chart playlists are disabled so nothing gets cut off. If you want "Trending 20", turn it ON and turn off another tile so it fits onto the 9-tile screen!
3. Tap **Patch** and install the generated APK.

---

### 3. Setting Up Google Maps Navigation Mini Player
1. Patch **Google Maps** with the **Allow Morphe YouTube Music mini player** and **Allow Morphe YouTube Music package visibility** patches.
2. Open Google Maps ➔ tap your **Profile icon** ➔ **Settings** ➔ **Navigation settings**:
   - Turn ON **Show media playback controls**.
   - Under **Assistant default media app** (or **Default media app**), select **YouTube Music**.
3. During turn-by-turn navigation:
   - Tap the **music icon** on the navigation controls.
   - Your 3x3 grid of media tiles appears with official covers.
   - Tap any playlist to start playback, or tap **Like Current Song** to thumbs-up the song currently playing without leaving navigation!

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.4.0](https://github.com/bartlomiejfornalczyk/morphe-patches/releases/tag/v1.4.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;16 patches total
<details open>
<summary>📦 Google Maps&nbsp;&nbsp;•&nbsp;&nbsp;14 patches</summary>
<br>

**🎯 Supported versions:**

| 26.36.04.973607363 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Add microG support](#add-microg-support) | Builds microG Maps, a separate app (org.ungoogled.android.apps.maps.microg) that signs in to your Google account through microG: saved places and lists, Timeline, location sharing, contributions and push messages. Remove sign-in prompts, Trim account menu and Remove permissions are left out of this build, Offline saved places keeps only its Local saved screen, which copies your account's saved lists to the phone (Pull from Google account), and its icon carries microG's C. Needs microG: MicroG-RE or ReVanced GmsCore. Not for root (mount) installs. |  |
| [Allow Morphe YouTube Music mini player](#allow-morphe-youtube-music-mini-player) | Enables YouTube Music and modded media apps as the navigation mini player. | • YouTube Music package name |
| [Allow Morphe YouTube Music package visibility](#allow-morphe-youtube-music-package-visibility) | Adds package queries and permission to AndroidManifest.xml for full media apps visibility. |  |
| [Black theme](#black-theme) | AMOLED-black theme. Pins Maps' own dark mode and its separate navigation colour scheme, and remaps colour resources, drawable fills and draw-time paints so no surface is left grey. |  |
| [Bypass Play Services checks](#bypass-play-services-checks) | Makes Maps' bundled Play services signature and availability checks always pass, so it runs re-signed and with Play services disabled or absent, and lets it load tiles, search and routing by sending Google's own package and certificate in the identity headers the Maps backend checks. Where Play services rejects the re-signed app, Maps degrades instead of crashing. |  |
| [Change package name](#change-package-name) | Installs alongside stock Google Maps under its own package name and adds MicroG spoofing. | • Package name |
| [Customization screen](#customization-screen) | Adds a Customization row under Settings on the account sheet, with switches for the patches here that can be turned back off inside the app. |  |
| [Elevate right navigation buttons](#elevate-right-navigation-buttons) | Elevates the right-side navigation buttons (Search, Sound, Report, Media) above the media player and bottom panel during turn-by-turn navigation in portrait mode. | • Elevation (dp) |
| [Location provider toggle](#location-provider-toggle) | Adds a Location source choice to the Customization screen: Android's own location providers, microG's (microg Services) or Google Play services' fused provider. With Android, neither is ever asked for a location. A source that is missing or disabled is never used, so location keeps working without it. Also keeps the network (Wi-Fi/cell) provider registered when no fused provider answers, instead of GPS only, so a fix does not go stale indoors. | • Default to Play services location |
| [Power saving mode](#power-saving-mode) | Brings the Pixel-only power saving mode to every phone: while driving with navigation, press the power button and Maps shows only key information such as the next turn on a black screen. Turn it on or off in Settings > Navigation > Power saving mode. Pixels that have it built in keep Google's own version unless Power saving mode is turned on in Power Saving Options, a row on the account sheet under Customization, which also has: a navigation button that opens the power saving screen without locking the phone, switching to it by itself when idle, a speedometer on it, a lower frame rate, and its black map in navigation or all over Maps. |  |
| [Remove permissions](#remove-permissions) | Removes permissions that only serve Google-account features or Google's data collection: background location, physical activity, contacts, microphone (voice search stops working), camera (Lens and Live View stop working), car speed, advertising ID, push messages and Google services settings. Left out with Add microG support, whose account features need them. |  |
| [Remove telemetry](#remove-telemetry) | Points the Firebase Installations and Play services compliance check-ins at an unresolvable host, stops every ad impression and click ping from being sent, and deregisters Google's logging, performance-monitoring, survey and Location History libraries and the on-device federated-learning services. |  |
| [Restore map data](#restore-map-data) | Lets a re-signed Maps load tiles, search and routing, by sending Google's own package and certificate. |  |
| [Zoom controls in navigation](#zoom-controls-in-navigation) | Adds +, − and reset tiles during turn-by-turn that change the navigation zoom while the camera keeps following the car. |  |

</details>

<details open>
<summary>📦 YouTube Music&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Allow external media browser connections](#allow-external-media-browser-connections) | Allows Google Maps, Android Auto, and third-party media controllers to connect to YouTube Music. | • Tile: My Supermix<br>• Tile: Liked Music<br>• Tile: Like Current Song<br>• Tile: Resume Playback<br>• Tile: Downloaded Songs<br>• Tile: Mixed for You<br>• Tile: Pop Certified<br>• Tile: Dance Party Hits<br>• Tile: Pump-Up Energy<br>• Tile: Trending 20<br>• Tile: Trending Music Videos<br>• Tile: Popular Right Now |
| [Allow external media browser launch](#allow-external-media-browser-launch) | Adds MAIN and DEFAULT intent-filters to MusicActivity so external controllers like Google Maps can open the app. |  |

</details>

<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## 📜 License
 
Morphe Patches are licensed under the [GNU General Public License v3.0](LICENSE)

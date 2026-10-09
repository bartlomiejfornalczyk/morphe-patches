# 🗺️🎵 Maps&Music patches

Custom Morphe patches enabling seamless YouTube Music mini-player integration directly inside Google Maps navigation, alongside package renaming, map data restoration, and microG support.

## ❓ About

This patch bundle provides custom patches and extra features built to work seamlessly alongside [bearinmindcat's patches](https://github.com/bearinmindcat/morphe-patches):
- **YouTube Music Mini Player in Google Maps**: Allows modded/re-signed YouTube Music apps to connect to Google Maps as the active media provider.
- **External Media Browser Connections**: Unlocks YouTube Music's MediaBrowserService entitlement gate and allowlist so Google Maps, Android Auto, and external controllers can browse and play media.
- **Package Renaming & Data Restoration**: Lets Google Maps run alongside the stock app with its own package name while keeping maps tile loading, search, and routing working.

> [!NOTE]
> These patches are fully compatible with [bearinmindcat/morphe-patches](https://github.com/bearinmindcat/morphe-patches) and serve as "extras" to enable navigation media playback integration.

### 🙏 Credits & Attribution

Special thanks and credit to **[bearinmindcat](https://github.com/bearinmindcat/morphe-patches)** for the original implementation of:
- **Restore map data**
- **Change package name**
- **Bypass Play Services checks**
- **Add microG support**

### How to use these patches
Install Morphe Manager if you have not yet: https://morphe.software

[Click here to add bartlomiejfornalczyk patches to Morphe Manager](https://morphe.software/add-source?github=bartlomiejfornalczyk/morphe-patches)

Select the app you want to patch inside Morphe Manager, follow all instructions shown.
Add this repository as a custom source in Morphe Manager:
`https://github.com/bartlomiejfornalczyk/morphe-patches`

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.1.0-dev.4](https://github.com/bartlomiejfornalczyk/morphe-patches/releases/tag/v1.1.0-dev.4)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;9 patches total
<details open>
<summary>📦 Google Maps&nbsp;&nbsp;•&nbsp;&nbsp;8 patches</summary>
<br>

**🎯 Supported versions:**

| 26.36.04.973607363 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Add microG support](#add-microg-support) | Builds microG Maps, a separate app (org.ungoogled.android.apps.maps.microg) that signs in to your Google account through microG: saved places and lists, Timeline, location sharing, contributions and push messages. Remove sign-in prompts, Trim account menu and Remove permissions are left out of this build, Offline saved places keeps only its Local saved screen, which copies your account's saved lists to the phone (Pull from Google account), and its icon carries microG's C. Needs microG: MicroG-RE or ReVanced GmsCore. Not for root (mount) installs. |  |
| [Allow Morphe YouTube Music mini player](#allow-morphe-youtube-music-mini-player) | Enables YouTube Music and modded media apps as the navigation mini player. | • YouTube Music package name |
| [Allow Morphe YouTube Music package visibility](#allow-morphe-youtube-music-package-visibility) | Adds package queries and permission to AndroidManifest.xml for full media apps visibility. |  |
| [Bypass Play Services checks](#bypass-play-services-checks) | Makes Maps' bundled Play services signature and availability checks always pass, so it runs re-signed and with Play services disabled or absent, and lets it load tiles, search and routing by sending Google's own package and certificate in the identity headers the Maps backend checks. Where Play services rejects the re-signed app, Maps degrades instead of crashing. |  |
| [Change package name](#change-package-name) | Installs alongside stock Google Maps under its own package name and adds MicroG spoofing. | • Package name |
| [Remove permissions](#remove-permissions) | Removes permissions that only serve Google-account features or Google's data collection: background location, physical activity, contacts, microphone (voice search stops working), camera (Lens and Live View stop working), car speed, advertising ID, push messages and Google services settings. Left out with Add microG support, whose account features need them. |  |
| [Remove telemetry](#remove-telemetry) | Points the Firebase Installations and Play services compliance check-ins at an unresolvable host, stops every ad impression and click ping from being sent, and deregisters Google's logging, performance-monitoring, survey and Location History libraries and the on-device federated-learning services. With Add microG support, Firebase Installations and Location History are left alone, so Timeline, account sync and push messages keep working. |  |
| [Restore map data](#restore-map-data) | Lets a re-signed Maps load tiles, search and routing, by sending Google's own package and certificate. |  |

</details>

<details open>
<summary>📦 YouTube Music&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Allow external media browser connections](#allow-external-media-browser-connections) | Allows Google Maps, Android Auto, and third-party media controllers to connect to YouTube Music. |  |

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

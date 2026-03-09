# ModVault

<p align="center">
  <img src="app/src/main/res/mipmap-xxhdpi/ic_launcher.png" width="100" alt="ModVault Logo"/>
</p>

<p align="center">
  <b>A Minecraft mod installer for Android</b><br/>
  Browse, download, and manage mods from Modrinth — directly on your phone.
</p>

<p align="center">
  <a href="https://github.com/copperlauncher/ModVault/actions"><img src="https://github.com/copperlauncher/ModVault/workflows/Android%20CI/badge.svg" alt="Android CI"/></a>
  <img src="https://img.shields.io/badge/platform-Android-green" alt="Platform"/>
  <img src="https://img.shields.io/badge/minSdk-26-blue" alt="Min SDK"/>
  <img src="https://github.com/CopperLauncher/CopperLauncher/workflows/Android%20CI/badge.svg" alt="Android CI"/>
  <img src="https://badges.crowdin.net/pojavlauncher/localized.svg" alt="Crowdin"/>
  <img src="https://img.shields.io/discord/1355213558631366897?color=5865F2&logo=discord&logoColor=white&label=&style=flat" alt="Discord"/>
  <img src="https://img.shields.io/badge/curseforge-maxjubayeryt-orange?logo=curseforge" alt="CurseForge"/>
  <img src="https://img.shields.io/badge/modrinth-maxjubayeryt-green?logo=modrinth" alt="Modrinth"/>
  <img src="https://img.shields.io/badge/modrinth-CopperLauncher-green?logo=modrinth" alt="Modrinth"/>
</p>

---

## Features

- 🔍 **Browse Mods** — Search thousands of mods from Modrinth
- 🎛️ **Filter** — Filter by Minecraft version and mod loader (Fabric, Forge, NeoForge, Quilt)
- ⬇️ **One-Tap Install** — Pick your version and install directly to your mods folder
- 📦 **Dependency Resolution** — Required dependencies are automatically downloaded
- ✅ **Installed Detection** — Already installed mods are clearly marked
- 🗂️ **Manage Mods** — View and delete installed mods from the Installed tab
- 📂 **Custom Folder** — Choose any mods folder on your device using Android's file picker
- 🌙 **Dark Theme** — Easy on the eyes

## Screenshots

> Coming soon

## Download

Grab the latest APK from the [Actions tab](../../actions) → latest build → **Artifacts**.

## Building

### Requirements
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 34

### Steps

```bash
git clone https://github.com/copperlauncher/ModVault.git
cd ModVault
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Usage

1. Install the APK on your Android device
2. Open ModVault
3. Tap **Settings** → **Choose Mods Folder** and select your launcher's `/mods` directory
4. Browse or search for mods in the **Browse** tab
5. Tap **Install** and select your desired version
6. Done! The mod is now in your mods folder

### Compatible Launchers
- CopperLauncher
- PojavLauncher
- Zalith Launcher
- Any Android Minecraft Java launcher that uses a `/mods` folder

## Tech Stack

| Library | Purpose |
|---|---|
| OkHttp 4 | Networking |
| Gson | JSON parsing |
| Glide | Mod icon loading |
| Material Components | UI |
| Modrinth API v2 | Mod data |

## Modrinth Compliance

ModVault fully complies with Modrinth's API Terms of Service:

- ✔️ Uses the official public Modrinth API
- ✔️ Does not bypass downloads or monetization
- ✔️ Does not redistribute or mirror mod files
- ✔️ Downloads are made directly from Modrinth's servers
- ✔️ No accounts required, no data collected

## License

Licensed under the [GNU LGPLv3](LICENSE).

## Credits

- [Modrinth](https://modrinth.com) — Mod platform and API
- [CopperLauncher](https://github.com/copperlauncher/CopperLauncher) — The launcher this was built for

---

<p align="center">Made with ☕ for the Android Minecraft community</p>

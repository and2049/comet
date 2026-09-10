# Comet

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

A Minecraft Java Client.

Maintained by [and2049](https://github.com/and2049). Contact: [res9nd@gmail.com](mailto:res9nd@gmail.com).

**Early development.** Comet ships three built-in instances: vanilla **1.7.10** and **1.8.9** (sharing one Comet-owned game folder, so settings and worlds carry across both), and a **1.16.1** speedrunning instance that installs Fabric and the upstream [MCSR Ranked](https://mcsrranked.com) RSG pack in its own folder. Custom instances can be created for any Minecraft version with vanilla, Fabric or Quilt, using either their own folder or the official launcher's `.minecraft`. Modrinth modpacks (`.mrpack`) and Prism Launcher, PolyMC or MultiMC exports (`.zip`) can be imported from a file or a link, and Modrinth modpacks can be searched and installed directly.

The two PvP instances launch through LaunchWrapper with OptiFine and the Comet client. OptiFine is not bundled: Comet downloads the pinned build (1.8.9 HD U M5, 1.7.10 HD U E7) from optifine.net onto your computer and verifies its checksum, or you can add the jar yourself from the instance page. The Comet client jars are published as assets of this repository's GitHub releases and are downloaded and checksum-verified on first launch. If either download fails, the instance still launches without that part. The client is built from `client/` (`gradlew build` with a JDK 21 writes `client/build/dist/comet-client-<version>.jar`). A development checkout uses those local jars automatically, or the folder named by `COMET_CLIENT_DIR`, instead of the GitHub release.

Comet supports the Modrinth ecosystem only: Modrinth modpacks with the Fabric or Quilt loader. CurseForge, Technic, Forge and NeoForge are out of scope by design, not planned features.

### In-game mods

In either Comet PvP instance, press **Right Shift** to open the mod menu. Search or filter the cards, click their enabled/disabled footer to toggle a mod, and use **Options** for its settings. **Edit HUD Layout** lets you drag counters, resize using their corner or the mouse wheel, and right-click to lock them.

The **Presets** sidebar saves mod toggles, options and HUD layouts together. **New preset** captures the current setup; click a preset to load its saved configuration. **Save changes** updates the selected preset, and its pencil button lets you rename or delete it. Unsaved changes are marked and survive restarting the game, but loading a preset replaces them. Presets are shared between 1.7.10 and 1.8.9, starting with a **Default** snapshot of your existing setup.

The **Settings** tab contains shared preferences that stay the same when switching presets:

- **HUD background** and **Text shadow** affect all Comet HUD labels and their editor previews. Both are on by default.
- **Borderless fullscreen** makes F11 use a desktop-sized borderless window. Changing this preference while fullscreen switches modes immediately; leaving fullscreen restores the window size and position.
- **Raw mouse input** uses Minecraft's existing JInput native backend for relative camera movement while preserving sensitivity and invert-mouse settings. If no relative device is available, Settings reports the fallback to normal input.
- **Disable hotbar scrolling** blocks wheel-based hotbar selection. Menu scrolling and number-key selection still work.

Fullscreen and input preferences are off by default. All settings are saved in `comet/settings.json` in the shared PvP game folder.

- **FPS** and **CPS** counters. CPS shows `[ L | R ]`, reserves two digits per side, and expands symmetrically around the separator for larger counts.
- **Keystrokes** shows your movement bindings, with optional mouse-button and jump rows. Indicators follow physical presses and rebound controls, including mouse bindings.
- **Coordinates** shows block X/Y/Z at your feet, rounding down at negative positions.
- **Ping** shows your server-reported latency, `Local` in singleplayer, and `-- ms` when unavailable.
- **Lighting** provides fullbright or a **1x–10x brightness multiplier**, adjusted with the minus/plus controls in Options. Turn Fullbright off to use the multiplier. It modifies the lightmap without changing your saved Minecraft gamma setting.
- **Motion Blur** uses **Hybrid** blur: camera-motion sampling combined with a short frame-history trail, stronger toward the edges and reduced near the crosshair. The HUD stays sharp. **Strength** 1-10 controls the blur duration. Hybrid is currently the only algorithm; the selector is retained for future methods. Requires OpenGL 2.0, floating-point textures and framebuffer objects.
- **Toggle Sprint / Sneak** follows your Minecraft sprint and sneak bindings. Automatic sprint on world entry is on by default; toggle sneak is opt-in under Options. Movement still follows vanilla sprint/sneak rules.
- **1.7 Visuals** on 1.8.9 restores first-person swings while blocking, eating, drinking or drawing a bow, with separate block-hitting and item-use switches. 1.7.10 uses its native animations.

Mod options and HUD positions are saved in `comet/settings.json` inside the game folder.

Keystrokes, Coordinates, Ping, Lighting and Motion Blur start disabled; enable them from their cards. The three HUD additions support the HUD editor and global background/text-shadow settings, and their configuration is included in mod presets.

## Run

Requires [Bun](https://bun.sh). Comet downloads the Java runtime Mojang declares for each version; a 64-bit Java executable can optionally be set as an override in Settings, and must match the Java version each instance needs.

```powershell
bun install
bun start
```

In Settings, enter your Microsoft application client ID and save. Sign-in requires Minecraft Services approval and an account that owns Minecraft: Java Edition. No client ID is bundled. **Install / verify files** works without signing in.

Comet's data lives in `%APPDATA%\Comet` on Windows, `~/.config/Comet` on Linux and `~/Library/Application Support/Comet` on macOS. Isolated instances keep their game folder under `instances/<id>/.minecraft`; the two Comet PvP versions share `shared/comet/.minecraft`; instances created with the official launcher folder use `%APPDATA%\.minecraft`, `~/.minecraft` or `~/Library/Application Support/minecraft`. Comet minimizes when the game process starts and restores when it exits.

### Linux notes

- Running from a checkout, Electron's sandbox helper needs the setuid bit or startup aborts with `The SUID sandbox helper binary was found, but is not configured correctly`. Fix it once per install instead of disabling the sandbox:

  ```sh
  sudo chown root:root node_modules/electron/dist/chrome-sandbox
  sudo chmod 4755 node_modules/electron/dist/chrome-sandbox
  ```

- Saved sign-ins need an unlocked keyring (gnome-keyring or KWallet). Without one, Comet refuses to store tokens rather than writing them in plain text.
- Apple Silicon Macs run the Intel Java runtime under Rosetta when Mojang publishes no arm64 build for a version's runtime.

## Development

```powershell
bun test
bun run typecheck
bun run build
bun run format
```

Additional checks: `bun run test:electron`, `bun run test:metadata`, `bun run test:install`, and `bun run test:client`, which launches each PvP version with OptiFine and the locally built Comet client and checks that Mixin transformed the game classes (it opens game windows briefly). The metadata check talks to Mojang, Fabric, Quilt, the MCSR pack host and Modrinth; the install check downloads all three built-in instances and can transfer several hundred MB.

`gradlew build` in `client/` also runs the Java core regression checks for mods, preset persistence, settings and menu interaction. Run `gradlew :core:regression` for those checks alone.

## Privacy

Sign-in happens in your own browser on Microsoft's page; Comet never sees your password. Tokens are stored locally, encrypted with the operating system's secure storage (DPAPI, Keychain or the Linux keyring), and sent only to Microsoft, Xbox Live and Mojang services for authentication. Game files and Java runtimes come from Mojang's official hosts; loaders come from fabricmc.net and quiltmc.org; modpack files come from Modrinth, GitHub or GitLab and are verified against the pack's checksums. OptiFine is downloaded from optifine.net and verified against a pinned checksum; the Comet client comes from this repository's GitHub releases and is verified against the checksum in the release manifest. Modrinth search queries go to api.modrinth.com with a User-Agent naming this project. Importing from a link downloads only the address you typed. Comet has no telemetry or developer-operated backend.

## License

Licensed under [GNU GPL version 3 only](LICENSE) (`GPL-3.0-only`).

# Comet

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

A Minecraft Java launcher for Windows x64, Linux x64 and macOS, built with Electron, React and TypeScript. Inspired by Prism's isolated instances and Lunar's interface.

Maintained by [and2049](https://github.com/and2049). Contact: [res9nd@gmail.com](mailto:res9nd@gmail.com).

**Early development.** Comet ships three built-in instances: vanilla **1.7.10** and **1.8.9** (sharing one Comet-owned game folder, so settings and worlds carry across both), and a **1.16.1** speedrunning instance that installs Fabric and the upstream [MCSR Ranked](https://mcsrranked.com) RSG pack in its own folder. Custom instances can be created for any Minecraft version with vanilla, Fabric or Quilt, using either their own folder or the official launcher's `.minecraft`. Modrinth modpacks (`.mrpack`) and Prism Launcher, PolyMC or MultiMC exports (`.zip`) can be imported from a file or a link, and Modrinth modpacks can be searched and installed directly.

The two PvP instances launch through LaunchWrapper with OptiFine and the Comet client. OptiFine is not bundled: Comet downloads the pinned build (1.8.9 HD U M5, 1.7.10 HD U E7) from optifine.net onto your computer and verifies its checksum, or you can add the jar yourself from the instance page. The Comet client jars are published as assets of this repository's GitHub releases and are downloaded and checksum-verified on first launch. If either download fails, the instance still launches without that part. The client is built from `client/` (`gradlew build` with a JDK 21 writes `client/build/dist/comet-client-<version>.jar`). A development checkout uses those local jars automatically, or the folder named by `COMET_CLIENT_DIR`, instead of the GitHub release.

Comet supports the Modrinth ecosystem only: Modrinth modpacks with the Fabric or Quilt loader. CurseForge, Technic, Forge and NeoForge are out of scope by design, not planned features.

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

## Privacy

Sign-in happens in your own browser on Microsoft's page; Comet never sees your password. Tokens are stored locally, encrypted with the operating system's secure storage (DPAPI, Keychain or the Linux keyring), and sent only to Microsoft, Xbox Live and Mojang services for authentication. Game files and Java runtimes come from Mojang's official hosts; loaders come from fabricmc.net and quiltmc.org; modpack files come from Modrinth, GitHub or GitLab and are verified against the pack's checksums. OptiFine is downloaded from optifine.net and verified against a pinned checksum; the Comet client comes from this repository's GitHub releases and is verified against the checksum in the release manifest. Modrinth search queries go to api.modrinth.com with a User-Agent naming this project. Importing from a link downloads only the address you typed. Comet has no telemetry or developer-operated backend.

## License

Licensed under [GNU GPL version 3 only](LICENSE) (`GPL-3.0-only`).

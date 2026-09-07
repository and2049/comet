# Comet

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

A Minecraft Java launcher for Windows x64, Linux x64 and macOS, built with Electron, React and TypeScript. Inspired by Prism's isolated instances and Lunar's interface.

Maintained by [and2049](https://github.com/and2049). Contact: [res9nd@gmail.com](mailto:res9nd@gmail.com).

**Early development:** supports vanilla **1.7.10 and 1.8.9**, and a **1.16.1** speedrunning instance that installs Fabric and the upstream [MCSR Ranked](https://mcsrranked.com) RSG pack. Built-in PvP mods and general Prism instance importing are not implemented yet. Minecraft Services application approval is pending review; authenticated game launches have not been verified.

## Run

Requires [Bun](https://bun.sh). Comet downloads the Java runtime Mojang declares for each version; a 64-bit Java 8 `java.exe` can optionally be set as an override in Settings.

```powershell
bun install
bun start
```

In Settings, enter your Microsoft application client ID and save. Sign-in requires Minecraft Services approval and an account that owns Minecraft: Java Edition. No client ID is bundled. **Install / verify files** works without signing in.

Instances have separate game directories under Comet's data folder: `%APPDATA%\Comet` on Windows, `~/.config/Comet` on Linux and `~/Library/Application Support/Comet` on macOS. Comet minimizes when the game process starts and restores when it exits.

### Linux notes

- Running from a checkout, Electron's sandbox helper needs the setuid bit or startup aborts with `The SUID sandbox helper binary was found, but is not configured correctly`. Fix it once per install instead of disabling the sandbox:

  ```sh
  sudo chown root:root node_modules/electron/dist/chrome-sandbox
  sudo chmod 4755 node_modules/electron/dist/chrome-sandbox
  ```

- Saved sign-ins need an unlocked keyring (gnome-keyring or KWallet). Without one, Comet refuses to store tokens rather than writing them in plain text.
- Apple Silicon Macs run the Intel Java runtime under Rosetta, because Mojang publishes no arm64 build of the Java 8 runtime these versions use.

## Development

```powershell
bun test
bun run typecheck
bun run build
```

Additional checks: `bun run test:electron`, `bun run test:metadata`, and `bun run test:install`. The install check downloads all three versions and can transfer several hundred MB.

## Privacy

Sign-in happens in your own browser on Microsoft's page; Comet never sees your password. Tokens are stored locally, encrypted with the operating system's secure storage (DPAPI, Keychain or the Linux keyring), and sent only to Microsoft, Xbox Live and Mojang services for authentication. Game files and Java runtimes come from Mojang's official hosts; the speedrunning instance also fetches Fabric from fabricmc.net and the MCSR Ranked pack's mods from GitHub and Modrinth, each verified against the pack's checksums. Comet has no telemetry or developer-operated backend.

## License

Licensed under [GNU GPL version 3 only](LICENSE) (`GPL-3.0-only`).

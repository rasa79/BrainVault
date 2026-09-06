# BrainVault

BrainVault is a local-first desktop Personal Knowledge Management (PKM) application. The source of
truth is plain Markdown files in a user-selected folder (the **vault**); everything else — the
SQLite full-text index, the links graph, tags, and favorites — is derived data that can be rebuilt
from the Markdown files at any time. It features a monospaced plain-text Markdown editor with a
side-by-side rendered HTML preview, search-as-you-type full-text search, wiki/Markdown backlinks,
tags, daily notes, favorites, filesystem watching, index rebuild, and Markdown import/export. It is
100% offline: no network calls, no accounts, no telemetry, and no AI features.

## Prerequisites

Both environments must report JDK **25.x**:

```bash
java --version    # must report 25.x
javac --version   # must report 25.x
```

Windows (PowerShell or cmd):

```bat
java --version
javac --version
```

## Command table

| Task | WSL (dev/test) | Windows (production) |
| --- | --- | --- |
| Create project root | `mkdir -p ~/dev2/BrainVault` | n/a (repo is shared) |
| Repo location | `~/dev2/BrainVault` | **Do not run Gradle from the UNC path** (`\\wsl$\Ubuntu\home\<user>\dev2\BrainVault`) — cmd.exe cannot hold a UNC working directory, and `net use`/`pushd` drive-mapping fails in practice (system error 64; the wrapper crashes even from a mapped drive). Instead **mirror the repo to a native Windows path** and run Gradle there:<br>`robocopy \\wsl$\Ubuntu\home\<user>\dev2\BrainVault C:\Users\<user>\dev\BrainVault /MIR /XD .gradle build .gradle-home .bootstrap-gradle .bootstrap-tmp .kotlin .idea`<br>WSL stays the master; **re-mirror** (re-run the same `robocopy`) after each change. |
| Bootstrap wrapper (once) | `gradle wrapper --gradle-version 9.6.1` | n/a — wrapper committed |
| Build + test | `./gradlew test` | `gradlew.bat test` (in the native mirror above) |
| Run app | `./gradlew run` | `gradlew.bat run` (in the native mirror above) |
| Clean | `./gradlew clean` | `gradlew.bat clean` (in the native mirror above) |

## Vault paths — do not mix

- **Production vault (Windows):** `%USERPROFILE%\Documents\BrainVault` (the default). The
  Windows-native app watches it with the native Windows `WatchService`, which works reliably.
- **WSL dev/test vault:** a throwaway vault inside the WSL filesystem, `~/brainvault-dev-vault`,
  created fresh for dev runs; tests always use temporary `@TempDir` fixtures instead.

**Warning:** during WSL-based development/testing, do **not** point the WSL instance at the Windows
vault via `/mnt/c/...` — filesystem watching across that bridge is unreliable and slow. The two
vaults must never be mixed.

## Application data locations

- **Index database:** `<vault>/.brainvault/index.db`. Delete it to force a full index rebuild.
- **Application settings:** `${user.home}/.brainvault/config.properties`
  (Windows: `%USERPROFILE%\.brainvault\config.properties`).

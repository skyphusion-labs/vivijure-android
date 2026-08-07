# vivijure-android

**License:** AGPL-3.0-only  
**App name:** Vivijure for Android (planned)  
**Status:** skeleton  
**applicationId:** `org.skyphusion.vivijure`  
**Studio API:** [vivijure-cf](https://github.com/skyphusion-labs/vivijure-cf) / [vivijure-local](https://github.com/skyphusion-labs/vivijure-local)  
**Sibling:** [vivijure-ios](https://github.com/skyphusion-labs/vivijure-ios)  
**Agent door:** [vivijure-mcp](https://github.com/skyphusion-labs/vivijure-mcp)

## What this is

AGPL **native Android client** for [Vivijure Studio](https://vivijure.com): the same film-studio
surface as the web panel, against a self-hosted or hosted studio Worker.

1. **`vivijure-kit`** (JVM library) -- HTTP clients for the studio CONTRACT.
2. **App shell** (Android, scaffold) -- projects, cast, storyboard, render, artifacts.

Commercial value remains hosted convenience (control plane, ops, billing), not a closed app layer.
The full stack stays AGPL.

## How the pieces fit together

```mermaid
flowchart TB
  subgraph device["Device: Vivijure for Android"]
    UI["App UI\nprojects · cast · film"]
    Kit["vivijure-kit\nStudio HTTP client"]
    Sec["Encrypted prefs\nAPI token"]
    UI --> Kit
    Kit --> Sec
  end

  subgraph studio["Studio host"]
    CF["vivijure-cf\nor vivijure-local"]
    Mods["Modules / GPU doors"]
    CF --> Mods
  end

  subgraph hosted["Optional hosted"]
    CP["vivijure-control-plane"]
    Tenant["tenant Worker\nslug.studio.vivijure.com"]
    CP --> Tenant
  end

  Kit -->|"Bearer token\nHTTPS /api/*"| CF
  Kit -->|"Bearer tenant token"| Tenant
```

## Status

Skeleton only. Kit sources + launcher Activity exist; full CONTRACT coverage and Compose panel are
not implemented. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Develop

```bash
# Vendor Gradle wrapper on first full build pass, then:
./gradlew :vivijure-kit:test :app:assembleDebug --no-daemon
```

## License

AGPL-3.0-only. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

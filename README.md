# vivijure-android

**License:** AGPL-3.0-only  
**App name:** Vivijure for Android  
**Status:** 0.1 WIP (kit + stepped Compose planner)  
**applicationId:** `org.skyphusion.vivijure`  
**Studio API:** [vivijure-cf](https://github.com/skyphusion-labs/vivijure-cf) / [vivijure-local](https://github.com/skyphusion-labs/vivijure-local)  
**Sibling:** [vivijure-ios](https://github.com/skyphusion-labs/vivijure-ios)  
**Agent door:** [vivijure-mcp](https://github.com/skyphusion-labs/vivijure-mcp)

## What this is

AGPL **native Android client** for Vivijure Studio -- the **mobile-friendly frontend to the
Storyboard Planner**. Same mandate as iOS: everything possible on the web planner must be possible
here against the same host and Bearer token.

1. **`vivijure-kit`** (JVM) -- OkHttp CONTRACT client.
2. **App shell** (Compose Material3) -- Plan → Cast & Bundle → Audio → Render → History + Cast /
   Modules / Settings tabs.

## Develop

```bash
./gradlew :vivijure-kit:test --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

Parity checklist: [docs/PARITY.md](docs/PARITY.md). Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## License

AGPL-3.0-only. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

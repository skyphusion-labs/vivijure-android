# Vivijure Android

This file is the guide for any coding agent and any contributor. Follow it.

## What this project is

This repo is the AGPL Android client for Vivijure Studio. It is a mobile frontend for the Storyboard Planner. It aims at parity with vivijure-ios and the web panel.

The client is a JVM kit (`vivijure-kit`) plus a Compose app. The `applicationId` is `org.skyphusion.vivijure`.

See [README.md](README.md) for status. Do not freeze a version in this file.

## Parity

| Source | Use for |
|--------|---------|
| Host `docs/CONTRACT.md` | Routes and JSON |
| Host `public/planner*.js` | UX stages |
| `vivijure-ios/docs/PARITY.md` | Feature checklist |
| This repo [docs/PARITY.md](docs/PARITY.md) | Android gaps |

## Layout

- `vivijure-kit` -- OkHttp client and serialization
- `app` -- Compose shell
- `docs/` -- architecture and parity

## Commands

```bash
./gradlew :vivijure-kit:test --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

CI runs the kit tests and a debug compile. See `.github/workflows/ci.yml`.

## Contract

Send `Authorization: Bearer <token>` only. Build capability UI from `GET /api/modules`.

Do not put a secret in a tracked file. The license is AGPL-3.0-only.

Open a pull request to `main`. Use a Conventional Commit. The body says why.

## Docs

Write Simplified Technical English. Use short sentences. Put one idea in each sentence. Use the active present tense. Use plain words. Use the same word every time.

Do not use an em dash (U+2014). Do not use an en dash (U+2013). Use a comma, a semicolon, parentheses, or two hyphens (`--`).

State the limit next to the capability. Do not turn a measured result into a promise.

Do not invent an API shape. Read the host contract.

# CLAUDE.md -- vivijure-android

Guidance for agents working in this repository.

## What this is

**AGPL Android client for Vivijure Studio** -- mobile frontend to the Storyboard Planner (parity with
vivijure-ios and the web panel). JVM kit (`vivijure-kit`) + Compose app.

**Status: 0.1 WIP.** `applicationId` **`org.skyphusion.vivijure`**.

## Parity authority

| Source | Use for |
|--------|---------|
| Host `docs/CONTRACT.md` | Routes / JSON |
| Host `public/planner*.js` | UX stages |
| Sibling `vivijure-ios/docs/PARITY.md` | Feature checklist |
| This repo `docs/PARITY.md` | Android gap tracker |

## Layout

- `vivijure-kit` -- OkHttp + serialization client
- `app` -- Compose shell
- `docs/` -- architecture + PARITY

## Commands

```bash
./gradlew :vivijure-kit:test --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

## Contract rules

- Auth: `Authorization: Bearer <token>` only
- Capability UI from `GET /api/modules`
- Never a plaintext secret in a tracked file
- AGPL-3.0-only; no em-dashes / en-dashes in prose
- Conventional Commits; aviation-grade `main`

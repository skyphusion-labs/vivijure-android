# CLAUDE.md -- vivijure-android

Guidance for agents working in this repository.

## What this is

**AGPL Android client for Vivijure Studio** -- native app interface to the same panel API as
`vivijure-cf` / `vivijure-local`. JVM kit (`vivijure-kit`) plus Android application shell.

**Status: skeleton (pre-0.1).** No Play Store release. `applicationId` **`org.skyphusion.vivijure`**.

## Related

| Repo | Role |
|------|------|
| [vivijure-cf](https://github.com/skyphusion-labs/vivijure-cf) | Cloudflare studio host + CONTRACT |
| [vivijure-local](https://github.com/skyphusion-labs/vivijure-local) | Self-host studio (API parity) |
| [vivijure-control-plane](https://github.com/skyphusion-labs/vivijure-control-plane) | Hosted multi-tenant provisioner |
| [vivijure-mcp](https://github.com/skyphusion-labs/vivijure-mcp) | Agent MCP door to the same API |
| [vivijure-ios](https://github.com/skyphusion-labs/vivijure-ios) | Sibling iOS app |
| [vivijure-core](https://github.com/skyphusion-labs/vivijure-core) | Shared orchestration types |

## Layout

- `vivijure-kit` -- JVM client (to grow OkHttp / serialization / coroutines)
- `app` -- Android application (Compose panel later)
- `docs/` -- architecture

## Commands

```bash
# After gradlew is vendored:
./gradlew :vivijure-kit:test --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

## Contract rules

- Auth: `Authorization: Bearer <token>` only
- Capability UI from `GET /api/modules`
- Package name for Play: `org.skyphusion.vivijure` (do not invent com.*)
- Never a plaintext secret in a tracked file
- AGPL-3.0-only; no em-dashes / en-dashes in prose
- Prefer parity with vivijure-ios

## Conventions

- Conventional Commits
- Aviation-grade `main` (org rulesets)

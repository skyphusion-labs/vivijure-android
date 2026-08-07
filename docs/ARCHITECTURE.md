# Architecture -- vivijure-android

## Product

**Vivijure for Android** is the mobile-friendly frontend to the Storyboard Planner (parity with
vivijure-ios and the web panel). AGPL-3.0-only.

## Layers

| Layer | Role |
|-------|------|
| `vivijure-kit` | JVM OkHttp + kotlinx.serialization client for studio CONTRACT |
| `app` | Compose Material3: onboarding, stepped planner, Cast, Modules, Settings |
| Token store | EncryptedSharedPreferences for URL + Bearer token |

## Backends

| Deploy | Auth |
|--------|------|
| Self-host CF / local | `Authorization: Bearer <STUDIO_API_TOKEN>` |
| Hosted tenant | tenant API token from control plane |

Wire shapes: host **`docs/CONTRACT.md`**. Do not invent routes.

## Status

**0.1 WIP.** Kit + Compose shell track the web/iOS planner CONTRACT (cast media, history
post-actions, modules install/config, demo, session restore). Not on Play Store yet. Parity:
[PARITY.md](PARITY.md).

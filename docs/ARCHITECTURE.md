# Architecture -- vivijure-android

## Status

**Skeleton.** Kit + minimal launcher Activity. No Play release.

## Targets

| Layer | Role |
|-------|------|
| `vivijure-kit` | JVM HTTP + models for studio CONTRACT |
| `app` | Android application (`applicationId` `org.skyphusion.vivijure`) |

## Backends

Same as vivijure-ios: studio Bearer token against `vivijure-cf` / `vivijure-local` or a hosted
tenant Worker. Wire shapes: host `docs/CONTRACT.md`.

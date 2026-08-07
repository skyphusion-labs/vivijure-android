# vivijure-android -- planner website parity

**Product mandate:** same as vivijure-ios -- mobile frontend to the Storyboard Planner against the
host CONTRACT (`docs/CONTRACT.md` on vivijure-cf / vivijure-local).

Sibling authority for UX stages: vivijure-ios `docs/PARITY.md` + host `public/planner*.js`.

## Status (0.1 vertical)

| Surface | Android |
|---------|---------|
| Auth / Keychain (EncryptedSharedPreferences) | **done** |
| Planner steps Plan → History | **done** (core path) |
| Cast library list/create/delete | **done** |
| Modules projection dump | **done** |
| Settings connection | **done** |

### Planner detail

| Capability | Status |
|------------|--------|
| Projects create/select | **done** |
| Plan + characters slots A–D | **done** |
| Refine | **done** |
| Scene prompt edit + apply | **done** |
| YAML preview | **done** |
| Preflight + castBindings | **done** |
| Bundle characterRefs from cast | **done** |
| Score-bed + suggest prompt | **done** |
| BPM snap | **done** |
| Quality / motion / keyframes-only / scatter | **done** |
| Schema knobs + expert JSON | **done** (basic) |
| Render submit + poll | **done** |
| History list / open artifact / load / delete | **done** |

### Still thinner than iOS

Cast media upload / train / generate-refs / vvcast; history tags/lock/regen/animate; install-scope
module config UI; demo mode; notifications; session restore blob.

## Slice history

| Slice | Notes |
|-------|--------|
| 0 skeleton | AGPL scaffold |
| 1 vertical | Kit CONTRACT client + Compose stepped planner shell |

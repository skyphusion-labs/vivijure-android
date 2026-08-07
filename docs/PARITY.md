# vivijure-android -- planner website parity

**Product mandate:** same as vivijure-ios -- mobile frontend to the Storyboard Planner against the
host CONTRACT (`docs/CONTRACT.md` on vivijure-cf / vivijure-local).

Sibling authority: vivijure-ios `docs/PARITY.md` + host `public/planner*.js`.

## Studio pages -- **done**

| Surface | Android |
|---------|---------|
| Auth (EncryptedSharedPreferences) | **done** |
| Planner stepped Plan → History | **done** |
| Cast library + detail | **done** |
| Modules (projection + install + config) | **done** |
| Settings (prefs, storage, demo, notify) | **done** |

## Planner stages -- **done**

| Stage | Notes |
|-------|--------|
| Plan | projects load/delete/save, models, slots A–D, plan/refine, scenes, YAML, session restore |
| Cast & Bundle | preflight, bindings, characterRefs, **scene start keyframes**, bundle |
| Audio | score-bed, **upload BYO**, analyze BPM, snap, suggest prompt |
| Render | quality, motion, keyframes-only, scatter, schema knobs, expert JSON |
| History | tags/label/delete, load, artifacts, add-audio/narration, finalize, **cloud/hybrid**, **lock/regen**, per-shot maps |

## Supporting -- **done**

| Capability | Status |
|------------|--------|
| Cast media upload (portrait/ref/source) | **done** |
| generate-refs + poll | **done** |
| train SDXL / Wan LoRA | **done** |
| `.vvcast` import/export | **done** |
| Module install/enable/uninstall | **done** (dispatch hosts) |
| Install-scope config JSON | **done** |
| Prefs GET/PATCH | **done** |
| Storage usage/reconcile | **done** |
| Demo menu/render/chat | **done** (when host enables) |
| Notify on render done | **done** |
| Session restore + poll resume | **done** |

## Slice history

| Slice | Notes |
|-------|--------|
| 0 skeleton | AGPLscaffold |
| 1 vertical | Kit + stepped Compose shell |
| 2 complete | Cast media/train/vvcast, history post-actions, modules, demo, notify, session |

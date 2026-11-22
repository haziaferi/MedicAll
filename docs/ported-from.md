# Code ported from other projects

Each row is code copied into MedicAll, with where it came from. Factotum is the owner's own project, and its code is relicensed here under GPL-3.0. The packages are renamed from `com.hazia.factotum` to `com.hazia.medicall`.

| MedicAll files | Source | Commit | Changes | Tests after the port |
|---|---|---|---|---|
| `core/src/commonMain/.../core/sync/{Stamp,Row,Merge}.kt` | Factotum `core/src/commonMain/.../core/sync/` | `6652e09` | package only | — |
| `core/src/commonTest/.../core/sync/{ClockAndIdTest,Device,KeepLoserMergeTest,ResolutionTest,SyncCasesTest}.kt` | Factotum `core/src/commonTest/.../core/sync/` | `6652e09` | package; the two ULID tests are replaced by `idsAreDistinct_SPEC_R15`, because MedicAll ids are UUIDs (`Ids.new()`) | 27 sync tests pass on the desktop target, 2026-11-22 |

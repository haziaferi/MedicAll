# MedicAll

MedicAll is a safe place to gather all medical information, for one or more people: medicines and doctor's appointments, symptoms, test results and the files that come with them. Everything is encrypted on the device and in backups, and MedicAll connects to the internet only when you ask it to update the medicine list. None of it is medical advice; always consult your doctor.

Phase 1 is built: the data layer, encryption, sync and backups, with a debug-only test screen and no app screens yet. The design is in [`SPEC.md`](SPEC.md), the build plans in [`docs/plans/`](docs/plans/), and the research behind it in [`docs/research/`](docs/research/).

- Platforms: Android 11 and later first; Windows and a watch module later.
- Stack: Kotlin Multiplatform with Compose Multiplatform.
- Distribution: GitHub only.

## How changes land

Every change reaches `main` through a pull request that the owner merges. CI runs the full gate on each pull request, and `main`'s ruleset blocks direct pushes, force pushes and deletion. No version tag is made before the first release (`SPEC.md`, R07).

Pick the merge method per pull request:

| Pull request | Method |
|---|---|
| A feature or a fix | **Squash**, the default: one commit on `main` |
| Commits deliberately kept clean and self-contained, each worth keeping on its own | **Rebase and merge** |
| Syncing one branch with another, never feature work | **Merge commit** |

Whoever opens a pull request names the method it expects in the description.

## Licence

GPL-3.0. See [`LICENSE`](LICENSE).

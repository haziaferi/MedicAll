# MedicAll

MedicAll is a safe place to gather all medical information, for one or more people: medicines and doctor's appointments, symptoms, test results and the files that come with them. Everything is encrypted on the device and in backups, and MedicAll connects to the internet only when you ask it to update the medicine list. None of it is medical advice; always consult your doctor.

Nothing is built yet. The design is in [`SPEC.md`](SPEC.md), and the research behind it is in [`docs/research/`](docs/research/).

- Platforms: Android 11 and later first; Windows and a watch module later.
- Stack: Kotlin Multiplatform with Compose Multiplatform.
- Distribution: GitHub only.

## How changes land

Every change reaches `main` through a pull request that the owner merges; CI runs the full gate on each pull request, `main` is protected with linear history, and no version tag is made before the first release (`SPEC.md`, R07).

## Licence

GPL-3.0. See [`LICENSE`](LICENSE).

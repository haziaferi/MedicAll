# Phase 2a walk: profiles, sync folders and the app lock

Plan: `docs/plans/2026-11-24-phase-2a-profiles-lock.md`, Task 8. Debug harness only (`HarnessActivity`); the screens are
phase 2b. Every name, passphrase and PIN is invented. Daytime only; no alarms are involved.

## Set-up

- Debug build of `phase-2a-lock` installed on each phone; the launcher checked in a separate command before each run.
- Syncthing shares made by the owner, as in the table below: `Documents/MedicAll-2a-A` and `Documents/MedicAll-2a-B`;
  backup folders `Documents/MedicAll-2a-backup-A` and `-B` on the 9 Pro.

| Folder | 9 Pro | 6T | Nord CE4 Lite |
|---|---|---|---|
| MedicAll-2a-A | joined | not joined | joined |
| MedicAll-2a-B | joined | joined | joined |

## Steps

| # | Step | Expected | 9 Pro | 6T | Nord CE4 Lite |
|---|---|---|---|---|---|
| 1 | 9 Pro joins A and B; adds Profile A (folder A) and Profile B (folder B) | both open; each profile in its own folder; reminders here on | | | |
| 2 | 6T joins only B; Sync now | shows Profile B only; reminders here off; switched on, still on after a restart | | | |
| 3 | 9 Pro switches Profile A's results module off; Nord joins A and B; Sync now on both | Nord shows results off for Profile A; reminders here off on the Nord | | | |
| 4 | Lock on the 9 Pro: PIN, background 30 s, then past 1 min; 5 wrong PINs; fingerprint; Forgot PIN with B's passphrase | open at 30 s; lock screen past 1 min with no profile visible; a wait after 5 wrong; fingerprint opens; the passphrase removes the PIN | | | |
| 5 | Change the PIN, then list both sync folders with `adb shell ls -l` before and after | no file in either folder changes | | | |

## Results

Not walked yet.

## Left for phase 2b

The screens, with the owner's layout and wording (R93, R95); the phase 2 gate (R14 and R11 on the three phones)
is checked with them.

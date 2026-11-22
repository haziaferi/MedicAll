# MedTimer vs Emivita: which medication base for MedicAll

As of 2026-11-12. Compared: MedTimer 1.26.5 (MIT, `Downloads/Builds/medTimer-main`) and Emivita master 5eab639 (GPL-3.0, exported to `_ref/emivita-5eab639`). The evidence is in two code maps with file:line citations (`flows-medtimer.md`, `flows-emivita.md`) and in a walk on the OnePlus 6T (Android 11, en-US, 24-hour clock), where every tap was logged.

## Verdict

**Neither app justifies porting as it stands. Take Emivita's platform-neutral engine and both apps' tests, and design the medication UI fresh.**

- **Set-up:** Emivita is measurably better, at 30 taps against 37, and its 30 includes stock that actually counts down. MedTimer needs 43 taps for the same result.
- **Settings:** Emivita does not beat MedTimer at reaching settings. Settings is one tap away, but changing one value costs more (8 taps against 5) because of the double Save.
- **Shared problems:** both apps share the friction you named: a weekly schedule needs a second screen, and stock lives apart from the medicine.
- **Code:** for a Kotlin Multiplatform app (Android + Windows), Emivita's scheduler, config, stock and backup code is already pure Kotlin. MedTimer's UI is Fragments, preference XML and Hilt, and none of it carries over to Windows.

## Measured on the 6T

Task: Ibuprofen 400 mg daily at 08:00 and 20:00, Vitamin D every Monday at 09:00, then a stock of 30 tablets for Ibuprofen. The counts include navigation between steps, clearing pre-filled fields and closing a keyboard that covered Save.

| Step | MedTimer | Emivita | Note |
|---|---|---|---|
| A. Ibuprofen, two times | 12 | 11 | Emivita's quick-add sheet makes the first reminder with the medicine |
| B. Vitamin D, Mondays | 18 | 11 | MedTimer starts with all 7 days ticked: 6 unticks on a separate screen |
| C. Stock 30 | 7 | 8 | MedTimer's stock never counts down without a dosage typed on each reminder (+6) |
| **Set-up total** | **37** (43 with working stock) | **30** | |
| Change snooze length | 5 | 8 | Emivita: a sheet inside a sheet, inner Save then outer Save |

What the walk found that the code maps missed:

- **Pre-filled fields are not selected on focus, in both apps.**
  - MedTimer's stock field holds "0", and typing 30 gave 300.
  - Emivita's time field holds "08:00", and typing appended: "08:002000".
  - Either way the user has to delete before typing.
- **Emivita's numeric keyboard covers Save**, and it has no ":" key. "2000" was accepted as 20:00, but nothing on screen says so.
- **MedTimer's debug build skips the intro**, so its first run (4 slides plus the notification prompt) comes from code only.
- **Confirmed on the 6T:**
  - After leaving MedTimer's Settings through a tab, Back lands on Overview, not on Settings.
  - MedTimer's Add reminder button has no text in the accessibility tree.

## Where each is better

| Area | MedTimer | Emivita |
|---|---|---|
| First run | 4-slide intro; nothing points to where you add a medicine | Disclaimer plus one sheet listing the missing permissions; Today has no add button either |
| Adding a medicine | Name dialog, then the edit page opens by itself | One sheet: name, dose, unit, time, daily |
| Weekly / specific days | Advanced settings screen, all days pre-ticked | Day chips inside the reminder form |
| Stock | Unit inferred from the dosage text; blank dosage means no tracking | Own form with a unit; "Runs out" date shown |
| Settings entry | ⋮ menu among 13 items, a list of 6 categories, then the value: 3+ deep | Bottom tab; toggles act on the row; one-line descriptions |
| Changing one value | Tap a choice and it saves | Sheet plus form, two Saves |
| Daily answers | 1 tap; **no undo** | 1 tap; 5-second Undo; Take all in the notification |
| Delete | Hard delete | Archive, then "Delete for good" |
| Extras | Interval reminder types, location snooze, PDF/CSV | Encrypted database, recovery, Medical ID card, weekly backups, a profile model |
| Tests | 585 (`@Test`), across 10 modules | 851, single module, 106 of 161 files under Robolectric |

## For MedicAll's code

| Take from | What | Why |
|---|---|---|
| Emivita | `ReminderConfig`, `SchedulingEngine`, `StockInfo`, `LowStockExpirationAlertEvaluator`, `BackupData`, `WeeklyBackups`, `BackupZipWriter` | No Android imports. The engine's constructor takes Room DAOs and DataStore, so it needs an interface in front of them |
| Emivita | its 42 MedTimer-parity tests plus the scheduler tests | They pin behaviour that already survived 146 ledger rows |
| MedTimer | its scheduler tests (`app/src/test/.../schedulertests`, 13 files), as a behaviour spec | MIT, so they can be copied with the notice |
| Neither | screens, navigation, settings structure | Both fail the set-up and settings bar. MedicAll adds profiles, a Windows peer and other modules, so the structure changes anyway |

## Design lessons for MedicAll's medication UI

These are the friction points the walk and the maps found, written as rules to test the new design against:

1. One form holds the medicine, all its times, its days and its stock, with nothing behind ⋮ and no second screen.
2. A pre-filled field selects its value on focus.
3. Save is never under the keyboard, and each change needs one Save.
4. Days are chips in the form, never a pre-ticked list to undo.
5. The empty start screen has the add-medicine action.
6. Every answer can be undone, as in Emivita.
7. A global setting is reachable in two taps, and the setting that governs a behaviour sits beside the others about it (MedTimer splits snooze over two categories, Emivita splits the notification tier over each medicine).

## Not covered

- Daily actions, maintenance and the settings tree beyond the snooze length come from code, not from the phone.
- MedTimer's release first run and Emivita's permission sheet were not seen on a fresh install.
- Tap counts follow one route each. A user could find a longer one, such as MedTimer's interval-reminder route for weekly doses (17 taps by code).
- Scoring on your axes was deferred at your request, so "better" here means fewer taps and fewer traps only.

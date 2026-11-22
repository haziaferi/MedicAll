# MedicAll spec

Scope: MedicAll, a private medical record and tracker for one or more people, on Android first and Windows later, with an optional watch module.

History: `SPEC-history.md`, one line per change. It is never a source of current rules.

Kinds: O owner rule · D decided · I inherited · A assumed · Q open.

## Rule index

| ID | Name | Section |
|---|---|---|
| O-R01 | Purpose statement | 1. Purpose and jobs |
| D-R02 | Jobs and how often | 1. Purpose and jobs |
| D-R03 | Platforms | 2. Platform and repository |
| D-R04 | Stack | 2. Platform and repository |
| D-R05 | Identity and licence | 2. Platform and repository |
| O-R06 | Public repository, private data | 2. Platform and repository |
| D-R07 | How changes land | 2. Platform and repository |
| D-R08 | Languages | 2. Platform and repository |
| D-R09 | Test devices | 2. Platform and repository |
| D-R10 | Encryption at rest | 3. Privacy and security |
| D-R109 | One passphrase for devices, sync and backups | 3. Privacy and security |
| D-R11 | App lock | 3. Privacy and security |
| D-R12 | Network use | 3. Privacy and security |
| D-R13 | Lock-screen privacy of notifications | 3. Privacy and security |
| D-R14 | Profiles | 4. Data and sync |
| D-R110 | Profiles per device and where reminders fire | 4. Data and sync |
| D-R15 | Record identity | 4. Data and sync |
| D-R16 | Sync through Syncthing | 4. Data and sync |
| D-R17 | History keeps its labels | 4. Data and sync |
| D-R18 | Bin | 4. Data and sync |
| O-R19 | Irreversible actions | 4. Data and sync |
| D-R20 | Units are stored canonically | 4. Data and sync |
| D-R21 | Attachments | 5. Attachments, backup and sharing |
| D-R22 | Backups | 5. Attachments, backup and sharing |
| D-R112 | 7-Zip writer | 5. Attachments, backup and sharing |
| D-R23 | File integrity | 5. Attachments, backup and sharing |
| D-R24 | Link to the original | 5. Attachments, backup and sharing |
| D-R25 | Sharing | 5. Attachments, backup and sharing |
| D-R26 | Medicine screens | 6. Medicines |
| D-R27 | Scheduling engine | 6. Medicines |
| D-R28 | Medicine options | 6. Medicines |
| D-R29 | Brand and product setting | 6. Medicines |
| O-R30 | Steady track | 6. Medicines |
| D-R31 | Excipients | 6. Medicines |
| D-R32 | Entry by scanning the box | 6. Medicines |
| A-R33 | Bollino barcode encoding | 6. Medicines |
| D-R34 | AIFA medicine list | 6. Medicines |
| D-R35 | Refresh of the medicine list | 6. Medicines |
| A-R36 | AIFA data licence | 6. Medicines |
| D-R37 | Prescribed, not yet collected | 6. Medicines |
| D-R38 | Holding a medicine before a test | 6. Medicines |
| D-R39 | Contraindications prompt | 6. Medicines |
| D-R40 | Lab report readers | 7. Results |
| D-R41 | Layouts in version 1 | 7. Results |
| D-R42 | Analyte list | 7. Results |
| D-R43 | Unit equivalences | 7. Results |
| D-R44 | Result line | 7. Results |
| D-R45 | Range kinds | 7. Results |
| D-R46 | Trends | 7. Results |
| D-R47 | Drug level and last dose | 7. Results |
| D-R48 | Blood pressure | 7. Results |
| Q-R49 | Irregular-heartbeat mark | 7. Results |
| A-R50 | Glucose | 7. Results |
| D-R51 | Daily log | 8. Daily log and trackers |
| D-R52 | Tracker kinds | 8. Daily log and trackers |
| D-R53 | Scale direction | 8. Daily log and trackers |
| D-R54 | "Nothing today" | 8. Daily log and trackers |
| O-R55 | Custom scale | 8. Daily log and trackers |
| D-R56 | Ready-made trackers | 8. Daily log and trackers |
| D-R113 | Tracker groups | 8. Daily log and trackers |
| D-R57 | Mood | 8. Daily log and trackers |
| D-R58 | Energy | 8. Daily log and trackers |
| D-R59 | Tension | 8. Daily log and trackers |
| D-R60 | Libido and sex | 8. Daily log and trackers |
| D-R61 | Pain | 8. Daily log and trackers |
| D-R62 | Sleep | 8. Daily log and trackers |
| D-R63 | Frequency class | 8. Daily log and trackers |
| O-R64 | Near-synonym pairs | 8. Daily log and trackers |
| D-R65 | Calendar | 9. Calendar and appointments |
| D-R66 | Appointment details | 9. Calendar and appointments |
| O-R67 | Emergency card content | 10. Emergency card |
| D-R68 | Android emergency information | 10. Emergency card |
| D-R111 | How the emergency card shows on the lock screen | 10. Emergency card |
| D-R69 | Wallet card | 10. Emergency card |
| O-R70 | All notifications are optional | 11. Notifications |
| D-R71 | Patterns are reported once | 11. Notifications |
| D-R72 | Empty reviews are skipped | 11. Notifications |
| D-R73 | Notification kinds | 11. Notifications |
| D-R74 | Name first | 11. Notifications |
| D-R75 | Doctor summary | 12. Doctor summary |
| D-R76 | Pattern types | 13. Patterns |
| O-R77 | Pattern wording | 13. Patterns |
| D-R78 | Counts | 13. Patterns |
| D-R79 | Drift | 13. Patterns |
| D-R80 | Watched patterns and search | 13. Patterns |
| D-R81 | Search safeguards | 13. Patterns |
| D-R82 | Entries behind a pattern | 13. Patterns |
| D-R83 | Episodes | 14. Episodes and experiments |
| D-R84 | Short illnesses | 14. Episodes and experiments |
| D-R85 | Experiments | 14. Episodes and experiments |
| D-R86 | Log-first stretch | 14. Episodes and experiments |
| D-R87 | Period module | 15. Period and bundles |
| D-R88 | Period trackers | 15. Period and bundles |
| O-R89 | Hormonal contraception switch | 15. Period and bundles |
| D-R90 | Pregnancy and fertility bundle | 15. Period and bundles |
| D-R91 | Bundles | 15. Period and bundles |
| Q-R92 | Condition bundle names | 15. Period and bundles |
| D-R93 | Visual identity | 16. Visual design |
| D-R94 | Today screen | 16. Visual design |
| D-R95 | Frontend decisions are the owner's | 16. Visual design |
| D-R96 | A dose confirmed on two devices | 17. Edge cases |
| D-R97 | A profile removed | 17. Edge cases |
| D-R98 | Time zones and daylight saving | 17. Edge cases |
| D-R99 | Test plan | 18. Tests and rollout |
| D-R100 | Rollout phases | 18. Tests and rollout |
| D-R101 | No interaction checks | Out of scope |
| D-R102 | No diagnosis or advice | Out of scope |
| D-R103 | No AI or LLM features | Out of scope |
| D-R104 | No cloud or server | Out of scope |
| D-R105 | No store distribution | Out of scope |
| D-R106 | No fertility-window advice | Out of scope |
| D-R107 | No wearable sync in version 1 | Out of scope |
| D-R108 | Standalone app | Out of scope |

## Glossary

| Term | Meaning |
|---|---|
| Profile | One person, or later one pet, whose records MedicAll keeps. Every record belongs to exactly one profile. |
| Carer | A person who manages several profiles on their own device. |
| Medicine | An active ingredient at a strength and form, as the person takes it, independent of the box it comes in. |
| Product | One pack of a medicine as sold: brand, manufacturer and pack size, identified by its AIC code. |
| AIC | The 9-digit Italian marketing authorisation code that identifies one pack. |
| Steady track | A stretch of time in which a medicine is taken as the same product at the same dose. |
| Dose event | One planned or as-needed dose and what happened to it: taken, skipped, late or missed, with its time and product. |
| Analyte | One thing a lab measures, under one chosen name with its synonyms. |
| Result line | One analyte's value from one report, with its unit, range, flags and source. |
| Tracker | One thing the person logs over time, of one fixed kind. |
| Scale | A tracker kind with 2 to 10 ordered steps and a declared direction. |
| Direction | Whether a higher step of a scale is better, worse or neither (neutral). |
| Frequency class | Whether a tracker is logged most days (frequent) or occasionally (episodic). |
| Episode | A stretch of time with a kind, an approximate or exact start, an ongoing state, an optional recovering phase and an end. |
| Bundle | A preset that switches on a set of trackers, episodes and measurements together. |
| Pattern | A relation between records over time that MedicAll shows as correlation, never as cause. |
| Watched pattern | A pattern the person chose to follow. |
| Experiment | A planned trial: one change, a start, a length and what to watch. |
| Bin | Where removed records wait for 30 days before they can be lost. |
| Emergency card | The lock-screen card for a paramedic or emergency doctor. |
| Wallet card | The optional printed card with fuller information. |
| Device log | One device's own append-only sync file in the shared folder. |

## 1. Purpose and jobs

People who live with several conditions keep medicines, appointments, lab reports and symptom notes in different apps, papers and files, and no single place shows how they relate. MedicAll gathers them, keeps them encrypted on the person's own devices, and shows them side by side without judging them.

### O-R01 · Purpose statement

**Rule:** The app states its purpose, in the About screen and the first-run screen, as the text in Values.

**Why:** The owner's own statement of what MedicAll is for, including that nothing in it is medical advice.

**Values:**

| Language | Text |
|---|---|
| English | "MedicAll is a safe place to gather all medical information, for one or more people. It covers a broad range, from keeping track of medicines and doctor's appointments to logging symptoms and test results. All information is encrypted on the device and in backups, and MedicAll connects to the internet only when you ask it to update the medicine list. Its charts can show several events and symptoms at once, for anyone who wants a visual picture of possible correlations and patterns. None of this is medical advice and must not be treated as such. Always consult your doctor and seek advice from a professional." |

**Acceptance:** The About screen and the first-run screen show the English text verbatim.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R02 · Jobs and how often

**Rule:** MedicAll serves the jobs below, as often as each row says. What opens first and what stays one tap away follow this order.

**Values:**

| Job | How often |
|---|---|
| Confirm one or several doses | several times a day |
| Log symptoms | several times a day |
| Log how the day went | daily |
| Review medicine tracking | weekly |
| Review symptom tracking | weekly |
| Log a period (optional module) | monthly |
| Add a lab report | monthly |
| Review patterns | monthly |
| Create a new profile | rarely |
| Set up a medicine | rarely |
| Prepare for an appointment | rarely |
| Use the emergency card | rarely |

**Acceptance:** none: this ranks jobs; the rules that apply it, such as R94, carry their own checks.

**Since:** 2026-11-22 · owner · own words

## 2. Platform and repository

### D-R03 · Platforms

**Rule:** MedicAll runs on the platforms below, each in the phase its row gives (R100).

**Values:**

| Platform | Minimum | Role | Phase |
|---|---|---|---|
| Android phone | Android 11 (API 30) | full app, with reminders | version 1 |
| Windows 10 and 11 | none set | full peer for records and review, never reminders | later |
| Wear OS (OnePlus Watch 3 43 mm) | none set | notification actions first, a Wear module after | later |

**Acceptance:** The Android build declares `minSdk = 30`; the Windows build schedules no reminder.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R04 · Stack

**Rule:** MedicAll is written in Kotlin Multiplatform with Compose Multiplatform, so Android and Windows share one codebase.

**Why:** Emivita's engine is pure Kotlin and moves over unchanged (R27).

**Acceptance:** The shared module compiles for the Android and the JVM desktop targets.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R05 · Identity and licence

**Rule:** The app is named "MedicAll", its package is `com.hazia.medicall`, and the code is GPL-3.0, published at `github.com/haziaferi/MedicAll`.

**Acceptance:** The manifest's `applicationId` is `com.hazia.medicall` and the repository root holds the GPL-3.0 `LICENSE`.

**Since:** 2026-11-22 · owner · agreed rewording

### O-R06 · Public repository, private data

**Rule:** The repository is public, so all personal and sensitive information is kept out of it by `.gitignore`. The SPEC and every example in code, tests and docs use neutral data only ("Medicine A", "Lab 1"), never the owner's own medicines, results or files.

**Why:** In the owner's words, "it is public, so all personal and sensitive information will have to be git ignored".

**Values:**

| Kept out of git | Why |
|---|---|
| `_ref/` | third-party sources, APKs, walk logs and screenshots |
| `local.properties` | local paths |
| the owner's lab PDFs and any personal reference | personal health data |
| signing keys and keystores | secrets |

**Acceptance:** The publish check passes on every push, and a search of the tracked files finds none of the owner's medicine names or file names.

**Since:** 2026-11-22 · owner · own words

### D-R07 · How changes land

**Rule:** Every change reaches `main` through a pull request that the owner merges. CI runs the full gate on each pull request, `main` is protected with linear history, and no version tag is made before the first release.

**Acceptance:** Branch protection on `main` requires a pull request and the CI check, and the README states this as its "How changes land" line.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R08 · Languages

**Rule:** The app follows the system language. English ships first and Italian follows. Italian bureaucracy fields (NRE, impegnativa, CUP, esenzione) appear as hints in the Italian locale, with generic English labels elsewhere.

**Acceptance:** With the phone set to Italian once Italian ships, every string is Italian; with it set to English, no Italian bureaucracy label appears.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R09 · Test devices

**Rule:** Device checks run on the phones below before a phase closes.

**Values:**

| Device | Android |
|---|---|
| OnePlus 6T | 11 |
| OnePlus 9 Pro | 14 |
| OnePlus Nord CE4 Lite | 14 or later |

**Evidence:** Not checked: the Nord CE4 Lite's Android version.

**Acceptance:** Each phase's walk record names all three phones.

**Since:** 2026-11-22 · owner · agreed rewording

## 3. Privacy and security

### D-R10 · Encryption at rest

**Rule:** The database and every attachment are encrypted on the device. The passphrase is entered once per device and then kept in the platform's key store.

**Acceptance:** The database file and an attachment copied off the device do not open without the key; a restart does not ask for the passphrase again.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R109 · One passphrase for devices, sync and backups

**Rule:** One passphrase, chosen by the person, protects the database on every device that syncs, the sync logs and the backups. It is entered once per device and kept in the key store, so sync and the restore of R23 never ask again. The person can change it; each other device then asks for the new one once.

**Why:** Separate passphrases would make the sync logs unreadable between devices or make the automatic restore ask each time.

**Acceptance:** A backup opens by hand with the passphrase; after a change, the next backup opens only with the new one, and a second device asks once for it.

**Not:** Separate passphrases for sync and backups.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R11 · App lock

**Rule:** One app lock guards every profile on the device.

**Evidence:** Not checked: the lock's method. The builder's default is a PIN with an optional fingerprint, as in Lunar.

**Acceptance:** With the lock on, no profile's data is visible after the app returns from the background past its timeout.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R12 · Network use

**Rule:** MedicAll connects to the internet only when the person asks it to refresh the medicine list (R35), and sends no personal data then. Sync between devices is done by Syncthing, outside the app.

**Acceptance:** The only network call site in the code is the medicine-list download, and it sends no profile data.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R13 · Lock-screen privacy of notifications

**Rule:** Every notification that names a medicine, a result, a test or a condition is private: the lock screen shows a public version with none of those names, and the full text shows after unlock.

**Values:**

| Full text | Public version on the lock screen |
|---|---|
| "Ada · blood test tomorrow · delay breakfast, Medicine A and Medicine B until after the test" | "Ada · blood test tomorrow · 3 preparation steps" |

**Acceptance:** On a locked phone, each notification of R73 shows no medicine, analyte, test or condition name.

**Since:** 2026-11-22 · owner · agreed rewording

## 4. Data and sync

### D-R14 · Profiles

**Rule:** MedicAll holds any number of profiles. A profile has a name, a birth date and the modules switched on for it. Each adult manages their own profile on their own phone; a carer may hold several.

**Values:**

| Field | Required | Note |
|---|---|---|
| name | yes | shown first in that profile's notifications (R74) |
| birth date | no | a date, never an age |
| kind | yes | person; pet is in the data model and not offered in version 1 |
| modules | yes | each module is on or off per profile |

**Acceptance:** Two profiles on one device keep separate records, and switching a module off for one leaves the other unchanged.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R110 · Profiles per device and where reminders fire

**Rule:** Each sync folder holds a chosen group of profiles, so one adult's records reach another adult's phone only if both join the same folder. Each device has a "profiles on this device" setting, and per profile a "reminders on this device" switch, on only on the device where the profile was created.

**Why:** A household sharing one folder would send every adult's records to every phone; a carer and the person both getting the same alarm would double every dose reminder.

**Acceptance:** A profile synced to a second device fires no reminder there until its switch is turned on.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R15 · Record identity

**Rule:** Every record has a UUID made on the device, an `updatedAt` instant and a `deletedAt` instant that is empty until removal, and belongs to one profile. Foreign keys are enforced.

**Acceptance:** A record created on two devices offline keeps two distinct ids, and a record whose profile does not exist cannot be written.

**Since:** 2026-11-22 · builder · own words

### D-R16 · Sync through Syncthing

**Rule:** Devices sync through a folder that Syncthing keeps in step. Each device appends full snapshots of what it writes to its own device log, reads every other device log in full, and keeps, per record, the snapshot with the later `updatedAt`. Nothing is lost by a merge: a removal is a snapshot too.

**Why:** The owner's rule is "later edit wins, non-destructive"; Lunar's device-log design does that without ever having two devices write one file.

**Values:**

| Guard | Behaviour |
|---|---|
| Device log name | `devices/<deviceId>.jsonl`, no dot in the id; Syncthing conflict copies are never read as a device |
| Clock set ahead | a snapshot stamped more than a day in the future waits until it is plausible |
| Clock set behind | a local edit is stamped at least 1 ms after the record it edits |
| Interrupted write | the log is written to a sibling file first and read back before it replaces the log |
| Folder unreachable | the app asks for the folder again and says nothing was lost |
| Encryption | every log line and every synced file is encrypted, with the key of R109 |
| Attachments | synced through the same folder, encrypted and named by their SHA-256 (R23), so a Windows peer can open them |

**Evidence:** Lunar's sync section, read 2026-11-14; its tests `ClockGuardTest` and `TornWriteGuardTest`, ported from `github.com/haziaferi/lunar` at a commit recorded when porting. The encryption and attachment rows are the builder's reading of R01 and R03.

**Acceptance:** Two devices editing the same record offline converge on the later edit after a sync, whatever order the logs are read in.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R17 · History keeps its labels

**Rule:** A past entry keeps the label, unit and dose it was recorded with. Renaming a tracker, a medicine or an analyte, or editing a plan, never rewrites past entries.

**Acceptance:** After a tracker is renamed, entries made before the rename still show the old label in history and export.

**Since:** 2026-11-22 · builder · own words

### D-R18 · Bin

**Rule:** Removing a record moves it to the bin, with no typed confirmation, and it can be restored for 30 days.

**Acceptance:** A removed medicine with its history is restored complete from the bin on day 29.

**Since:** 2026-11-22 · owner · agreed rewording

### O-R19 · Irreversible actions

**Rule:** An action that cannot be undone (emptying the bin, a permanent delete) first states in plain words what will be lost, then asks the person to type "DELETE".

**Why:** The owner wants the typed step only where nothing can be recovered, not for anything still in the bin.

**Acceptance:** Emptying the bin cannot complete until "DELETE" is typed; moving a record to the bin asks for no typing.

**Not:** A typed "DELETE" for every removal: the bin already makes those recoverable.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R20 · Units are stored canonically

**Rule:** Values are stored in one canonical unit per quantity, together with the unit as printed or typed. A display preference changes only how values are shown.

**Acceptance:** Changing the weight display from kg to lb changes no stored value.

**Since:** 2026-11-22 · builder · own words

## 5. Attachments, backup and sharing

### D-R21 · Attachments

**Rule:** Files of any type can be attached to records. MedicAll keeps an encrypted copy, and importing never deletes or moves the original.

**Acceptance:** After a PDF is imported, the original is unchanged in its folder and the copy opens from inside MedicAll.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R22 · Backups

**Rule:** MedicAll writes an automatic backup and a weekly dated one to a folder the person chooses. Each backup is a 7-Zip archive with AES-256 and encrypted file names, and opens without MedicAll.

**Values:**

| Inside each backup | Form |
|---|---|
| Attachments | the files, under readable names |
| Records | CSV and JSON |
| Instructions | a plain text file on how to open the archive |

**Acceptance:** A backup opens in 7-Zip with the passphrase, its file list is hidden without it, and the CSV holds every result line.

**Not:** An encrypted ZIP: ZIP shows the file names inside without the password.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R112 · 7-Zip writer

**Rule:** Backups are written with a native 7-Zip library for Android and Windows, chosen and licence-checked in phase 1.

**Why:** No pure-Kotlin library writes encrypted 7-Zip archives.

**Acceptance:** A backup written on Android and one written on Windows both open in desktop 7-Zip with the passphrase.

**Not:** An AES-256 ZIP holding one inner archive: it hides the names but takes two steps to open.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R23 · File integrity

**Rule:** Each attachment has a SHA-256 hash, checked at every backup and periodically. A damaged file is restored from the newest backup whose copy is intact.

**Acceptance:** A file altered by one byte is reported at the next check and restored from a backup holding the intact copy.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R24 · Link to the original

**Rule:** An attachment can optionally keep a link to its original file. MedicAll warns when the linked original changes or disappears.

**Acceptance:** Moving a linked original raises the R73 notification for a missing original.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R25 · Sharing

**Rule:** Sharing sends the lab's original file by default, one file or several at once, through the actions below.

**Values:**

| Platform | Action | Behaviour |
|---|---|---|
| Android | Share | the share sheet gets a temporary decrypted copy, deleted afterwards; a plain warning shows the first time |
| Android | Print | the system print dialog |
| Windows | "Save a copy to…" | a decrypted copy saved where the person chooses |
| Windows | Print | the system print dialog |

**Acceptance:** After a share, the temporary copy is gone from the cache; the warning shows once per device.

**Since:** 2026-11-22 · owner · agreed rewording

## 6. Medicines

### D-R26 · Medicine screens

**Rule:** The medicine screens meet the requirements below, which come from the MedTimer and Emivita walks.

**Values:**

| Requirement |
|---|
| one form holds the medicine, all its times, days and stock, with nothing behind a menu and no second screen |
| a pre-filled field selects its value on focus |
| Save is never under the keyboard, and each change needs one Save |
| days are chips in the form, never a pre-ticked list |
| the empty start screen has the add-medicine action |
| every answer to a dose can be undone |
| a global setting is reachable in two taps, beside the settings about the same behaviour |

**Evidence:** `docs/research/2026-11-12-medtimer-vs-emivita.md`, walked on the 6T 2026-11-12.

**Acceptance:** Setting up a daily medicine with stock takes one form and one Save, with Save visible while the keyboard is open.

**Since:** 2026-11-22 · builder · own words

### D-R27 · Scheduling engine

**Rule:** Reminders are built on Emivita's pure-Kotlin engine (`ReminderConfig`, `SchedulingEngine`, `StockInfo`, the low-stock and expiry evaluator), behind an interface in place of its Room and DataStore calls, and are tested with Emivita's scheduler tests and MedTimer's scheduler tests as a behaviour spec.

**Evidence:** `docs/research/2026-11-12-medtimer-vs-emivita.md`; sources `github.com/haziaferi/emivita` and `github.com/Futsch1/medTimer` (1.26.5), each at a commit recorded when porting; MedTimer's tests are MIT and are copied with their notice.

**Acceptance:** The ported scheduler tests pass in the shared module, including Emivita's daylight-saving cases.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R28 · Medicine options

**Rule:** Each option below can be switched on and tailored per medicine or per profile.

**Values:**

| Option | What it does |
|---|---|
| Dated regimen history | a change of dose or times starts a new dated plan; past plans stay as they were |
| Ingredient and product | each dose records the product it came from (R29) |
| Prescription link | a prescription with its expiry is linked to low stock and to a GP appointment |
| Late window | a per-medicine window after which a dose counts as late |
| As-needed follow-up | after an as-needed dose, a later prompt asks "did it help?" |
| Home time zone | reminders follow a chosen home time zone while travelling, or local time |
| Take all | one action confirms every dose due at once |
| Carer attribution | a dose records who confirmed it |

**Acceptance:** Each option, switched on for one medicine, changes that medicine only.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R29 · Brand and product setting

**Rule:** Each medicine has a product setting with three levels. A warning never blocks a dose. When the setting is "stick to product", MedicAll records who set it (doctor or person) and why.

**Values:**

| Level | Behaviour |
|---|---|
| Ingredient only | the product is recorded, nothing else |
| Track product | a product change adds a marker to the timeline, and symptoms can be split by product |
| Stick to product | as track product, and a different product scanned or chosen raises a warning |

**Acceptance:** Scanning a different product for a "stick to product" medicine shows the warning and still lets the dose be confirmed.

**Since:** 2026-11-22 · owner · agreed rewording

### O-R30 · Steady track

**Rule:** The timeline shows each steady track of a medicine (same product, same dose) as one band, under the name "steady track".

**Why:** The owner chose "steady track" so the word "period" keeps one meaning in the app.

**Acceptance:** A product change or a dose change ends one band and starts the next; no label in the app calls the band a period.

**Not:** "Steady period": the word "period" already means menstruation in the period module.

**Since:** 2026-11-22 · owner · own words

### D-R31 · Excipients

**Rule:** Each product has a free-text excipients field.

**Acceptance:** The field is saved with the product and shown on its page.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R32 · Entry by scanning the box

**Rule:** A medicine is usually entered by scanning its box with ZXing. The Data Matrix gives the AIC, expiry and batch; the bollino gives the AIC only. A typed AIC or a name search works too.

**Values:**

| Code | Carries | Read as |
|---|---|---|
| Data Matrix | GTIN (01), serial (21), batch (10), expiry (17), AIC (716) | GS1 application identifiers |
| Bollino | AIC | Code 32 inside Code 39 |

**Evidence:** `docs/research/2026-11-13-aifa-and-box-codes.md`; the bollino's encoding rests on R33.

**Acceptance:** Scanning a Data Matrix fills the product, batch and expiry; scanning a bollino fills the product.

**Since:** 2026-11-22 · owner · agreed rewording

### A-R33 · Bollino barcode encoding

**Rule:** The bollino's barcode is the AIC in base 32, printed in Code 39, and read as "A" followed by the AIC.

**Evidence:** Not checked: no official specification was found. Scan a real box before the bollino reader is built.

**Since:** 2026-11-22 · builder · own words

### D-R34 · AIFA medicine list

**Rule:** MedicAll carries an offline copy of the AIFA medicine list, trimmed to the columns the lookup needs, bundled with each release and refreshable on demand.

**Values:**

| Source file | Used for |
|---|---|
| `confezioni_fornitura.csv` | brand, manufacturer, pack, ATC, prescription class, by AIC |
| `PA_confezioni.csv` | active ingredients and quantities |
| `Lista_farmaci_equivalenti.csv` | interchangeable generics |
| `elenco_medicinali_carenti.csv` | shortages, for R73 |

**Evidence:** `docs/research/2026-11-13-aifa-and-box-codes.md`; the licence rests on R36; the trimmed size is not measured.

**Acceptance:** A scanned AIC is found with no network, and a refresh replaces the list only after the new copy is read whole.

**Since:** 2026-11-22 · owner · own words

### D-R35 · Refresh of the medicine list

**Rule:** The list is refreshed only when the person asks, or when a new release ships its copy.

**Acceptance:** No download happens without the person's action.

**Since:** 2026-11-22 · owner · own words

### A-R36 · AIFA data licence

**Rule:** AIFA's medicine datasets may be bundled and redistributed under CC-BY 4.0, with attribution.

**Evidence:** Not checked: AIFA's open-data page states CC-BY 4.0 in general but does not name these datasets. Confirm per dataset before the first release bundles them.

**Since:** 2026-11-22 · builder · own words

### D-R37 · Prescribed, not yet collected

**Rule:** A medicine entered from a prescription stays "prescribed, not yet collected" until its first box is scanned.

**Acceptance:** A prescribed medicine schedules no reminder until a box is scanned or added.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R38 · Holding a medicine before a test

**Rule:** Each medicine has a "hold before blood test" switch. On the morning of a blood test, that medicine's reminder says "after the test" in place of firing at its usual time.

**Acceptance:** With a blood test tomorrow and the switch on, the morning reminder is replaced by one worded "after the test".

**Since:** 2026-11-22 · owner · agreed rewording

### D-R39 · Contraindications prompt

**Rule:** When a medicine or condition is set up, MedicAll asks "anything that must not be given with this?". The answer feeds the emergency card (R67) in the person's own words.

**Acceptance:** An answer given at set-up appears on the emergency card without naming the medicine or condition.

**Since:** 2026-11-22 · owner · agreed rewording

## 7. Results

### D-R40 · Lab report readers

**Rule:** Text PDFs are read by a position-based reader written for each lab layout; paper reports go through OCR. Every reader produces draft result lines that the person confirms.

**Values:**

| Layout | Traits the reader handles |
|---|---|
| Lab 1 | text runs out of order, paired by vertical position; hidden filler words; ↑ ↓ glyph flags; HbA1c in % and mmol/mol; banded and sex-split ranges; urine results as text |
| Lab 2 | one table per analyte; `*` flag; method column filled; bare single-number ranges with no direction |

**Evidence:** Read from the owner's two lab PDFs on 2026-11-13, which stay outside the repository (R06). The builder's calls: OCR uses Tesseract through the owner's Tesseract4Android fork (4.10.1), which runs offline (R12); OCR text becomes draft lines line by line; the reader tests use synthetic PDFs that copy each layout with invented values, made on the owner's machine and never from real values.

**Acceptance:** Each reader turns its sample layout into draft lines that match the printed values, units, ranges and flags.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R41 · Layouts in version 1

**Rule:** Version 1 reads only the two layouts of R40, unless the owner finds a third before the results phase starts.

**Acceptance:** none: a scope boundary.

**Since:** 2026-11-22 · owner · own words

### D-R42 · Analyte list

**Rule:** Each analyte has one chosen name and any number of synonyms. A name not on the list becomes a draft line for the person to assign. A LOINC code is optional and hidden by default.

**Acceptance:** "Emoglobina" and "Hb" set as synonyms plot on one trend line; an unknown name stays a draft until assigned.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R43 · Unit equivalences

**Rule:** Units are matched at two levels: spellings that mean the same for every analyte, and conversions that belong to one analyte.

**Values:**

| Level | Examples |
|---|---|
| Same for every analyte | x10^9/L = x10^3/µL; UI/L = U/L; gamma/dL = µg/dL |
| Belongs to one analyte | HbA1c % ↔ mmol/mol; glucose mg/dL ↔ mmol/L; sodium mEq/L = mmol/L |

**Acceptance:** An HbA1c result in mmol/mol and one in % plot on the same trend after conversion.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R44 · Result line

**Rule:** A result line keeps the fields below.

**Values:**

| Field | Note |
|---|---|
| value | a number, a bound, a text outcome or "not performed" |
| printed text | exactly as printed |
| unit | as printed, with its canonical unit (R20) |
| method | when the lab prints it |
| lab flag | as printed (`*`, ↑, ↓), on its own number |
| app flag | MedicAll's own, with direction |
| range | exactly as printed, of a kind from R45 |
| source | typed, or which attachment and page |

**Acceptance:** A line read from a PDF shows the printed text and the lab flag unchanged beside the app flag.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R45 · Range kinds

**Rule:** A range is one of the kinds below.

**Values:**

| Kind | Example |
|---|---|
| Low to high | 12–16 |
| One bound | > 40 |
| A bound of unknown direction | 5, confirmed once per analyte and lab |
| Labelled bands | risk levels; values split by sex |

**Acceptance:** A bare "5" range asks once for its direction for that analyte and lab, and never again.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R46 · Trends

**Rule:** A trend plots each point against its own lab's range.

**Acceptance:** Two points from labs with different ranges each show their own range band.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R47 · Drug level and last dose

**Rule:** An analyte that measures a medicine's level can optionally be linked to that medicine; its result then shows the last dose taken before the sample was collected.

**Acceptance:** A linked result shows the time between the last recorded dose and the collection time.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R48 · Blood pressure

**Rule:** A blood-pressure entry holds systolic (max), diastolic (min) and pulse, with the date and time filled in. Arm, position and a note are optional. Several readings in one sitting show as their average, and ranges work as lab ranges do.

**Acceptance:** Three readings entered in one sitting show one averaged entry with the three readings beneath.

**Since:** 2026-11-22 · owner · agreed rewording

### Q-R49 · Irregular-heartbeat mark

**Rule:** Should a blood-pressure entry carry a yes or no for the irregular-heartbeat symbol?

**Default:** Yes, if the owner's machine shows the symbol. It would add one switch to the R48 entry.

**Why:** The owner is checking whether their machine shows it.

**Since:** 2026-11-22 · owner · agreed rewording

### A-R50 · Glucose

**Rule:** A glucose entry holds a value in mg/dL with its meal context (fasting, before a meal, 2 h after) and links to HbA1c results.

**Evidence:** Not checked: designed from common meters, not with a person who has diabetes. Check with one before the diabetes bundle (R91) is built.

**Since:** 2026-11-22 · owner · agreed rewording

## 8. Daily log and trackers

### D-R51 · Daily log

**Rule:** The daily log is free text and tags by default; every other field is optional. A day can hold several entries, each with its own time.

**Acceptance:** Two entries on one day keep their own times and neither overwrites the other.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R52 · Tracker kinds

**Rule:** A tracker is made of one or more parts, and each part is of one kind below, fixed when it is created. Patterns, "nothing today" and history count per part.

**Values:**

| Kind | Example |
|---|---|
| Scale | mood, energy |
| Pick one | flow |
| Pick several | mood words, digestion |
| Number | weight, sleep hours |
| Duration | exercise, meditation |
| Body region | pain |
| Happened or not | an event |

**Evidence:** Parts are the builder's call, so that trackers such as Mood (scale plus words) and Pain (region, 0–10, type) of R56 fit one model.

**Acceptance:** A part's kind cannot be edited after creation; the editor shows it, disabled; Mood stores its scale step and its words as two parts of one entry.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R53 · Scale direction

**Rule:** Every scale declares a direction: higher is better, higher is worse, or neutral. Wording and colour follow it, and a neutral scale never shows either end as good or bad.

**Acceptance:** A neutral scale is drawn with no green or red at either end.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R54 · "Nothing today"

**Rule:** Trackers offer an explicit "nothing today" answer, so a day logged as fine is told apart from a day not logged.

**Acceptance:** A day answered "nothing today" counts in the y of an "x of y" count; a day with no answer does not.

**Since:** 2026-11-22 · owner · agreed rewording

### O-R55 · Custom scale

**Rule:** A custom scale has 2 to 10 steps, and the person chooses each step's word. The preset is "none / low / mid / high". Preset words are never emotionally charged.

**Why:** The owner does not want charged words in a scale's preset.

**Acceptance:** A custom scale can be made with any number of steps from 2 to 10, and its preset reads "none / low / mid / high".

**Not:** Presets such as "shut down / very limited / full capacity": the words carry an emotional charge.

**Since:** 2026-11-22 · owner · own words

### D-R56 · Ready-made trackers

**Rule:** The ready-made trackers below can be switched on for any profile. Each comes with its kind, direction and frequency class.

**Values:**

| Tracker | Kind | Direction | Frequency |
|---|---|---|---|
| Mood | scale + words (R57) | higher is better | frequent |
| Energy | scale (R58) | neutral | frequent |
| Tension | scale (R59) | higher is worse | frequent |
| Libido | scale (R60) | neutral | frequent |
| Sex | pick several (R60) | none | episodic |
| Sleep | number + pick several (R62) | none | frequent |
| Pain | region + 0–10 + type (R61) | higher is worse | episodic |
| Headache | scale: mild, moderate, severe | higher is worse | episodic |
| Cramps | scale: mild, moderate, severe | higher is worse | episodic |
| Cravings | pick several: sweet, salty, greasy, spicy, carbs | none | episodic |
| Digestion | pick several: bloated, gassy, heartburn, nauseous, vomiting | none | episodic |
| Stool | pick several: constipation, diarrhea | none | episodic |
| Urine | pick several: frequent, burning, leaks | none | episodic |
| Skin | pick several: acne, dry, oily, itchy | none | episodic |
| Hair | pick several: oily scalp, dry scalp, hair loss | none | episodic |
| Mind | pick several: forgetful, brain fog, focused, distracted, motivated, unmotivated, productive | none | frequent |
| Social life | pick several: sociable, withdrawn, supportive, argumentative | none | frequent |
| Exercise | pick several + duration | none | episodic |
| Meditation | duration | none | episodic |
| Alcohol and cigarettes | pick several: alcohol, cigarettes, hangover | none | episodic |
| Travel | happened or not | none | episodic |
| Hot flashes | scale: none to very severe | higher is worse | episodic |
| Dizziness, palpitations, nose bleed, swollen feet | happened or not, each | none | episodic |
| Weight | number | none | episodic |

**Evidence:** From Lunar's defaults (`Models.kt`) and Clue's tracking list, read on the 6T 2026-11-14 (`docs/research/2026-11-14-clue-trackers.md`).

**Acceptance:** Each tracker in the table can be switched on, and comes with the kind, direction and frequency of its row.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R113 · Tracker groups

**Rule:** Ready-made trackers are offered in the groups below, which can be switched on together and are shown together in the log. Sleep belongs to no group. Tracker groups are separate from the condition bundles of R91.

**Values:**

| Group | Trackers |
|---|---|
| "Mind & mood" | Mood, Energy, Tension, Mind |
| "Intimacy" | Sex, Libido |
| "Digestion & bladder" | Cramps, Digestion, Stool, Urine, Cravings |
| "Skin & hair" | Skin, Hair |
| "Movement & practice" | Meditation, Exercise |
| "Pain" | Pain, Headache |
| "Life events" | Social life, Alcohol and cigarettes, Travel |
| "Body signs" | Weight, Hot flashes, Dizziness, Palpitations, Nose bleed, Swollen feet |

**Acceptance:** Switching a group on switches on each of its trackers; Sleep is switched on by itself.

**Not:** Sleep as a group of its own.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R57 · Mood

**Rule:** Mood is a scale plus optional words. The words can be picked together and are never placed on the scale.

**Values:**

| Part | Content |
|---|---|
| Scale | "Very low", "Low", "Neutral", "Good", "Very good" |
| Words | "numb", "anxious", "irritable", "calm", "overwhelmed", and the person's own |

**Acceptance:** An entry can hold one scale step and several words at once.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R58 · Energy

**Rule:** Energy is a neutral scale.

**Values:**

| Step | Word |
|---|---|
| 1 | "Exhausted" |
| 2 | "Low" |
| 3 | "Steady" |
| 4 | "High" |
| 5 | "Restless" |

**Acceptance:** The scale shows these five words and no end is coloured as better.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R59 · Tension

**Rule:** Tension is a scale of its own, where higher is worse, kept apart from the mood words so the two can be compared.

**Values:**

| Step | Word |
|---|---|
| 1 | "Relaxed" |
| 2 | "Mostly relaxed" |
| 3 | "Some tension" |
| 4 | "Tense" |
| 5 | "Very tense" |

**Acceptance:** Tension is logged apart from mood, and "anxious" never sets a tension step.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R60 · Libido and sex

**Rule:** Libido is a neutral scale of its own. Sex is a separate pick-several event tracker.

**Values:**

| Tracker | Content |
|---|---|
| Libido | "None", "Low", "Usual", "High", "Very high" |
| Sex | protected, unprotected, masturbation, orgasm, no orgasm, painful, and the person's own |

**Acceptance:** Libido can be logged on a day with no sex entry, and the reverse.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R61 · Pain

**Rule:** Pain is logged as a body region, a severity from 0 to 10 and a type. A chronic baseline can be logged once, with flares logged on top of it.

**Acceptance:** A flare entry shows against the region's baseline.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R62 · Sleep

**Rule:** Sleep is logged as hours plus quality words. Waking in the night is a timestamped event, not a tag.

**Acceptance:** Two night wakings on one night are two events with their own times.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R63 · Frequency class

**Rule:** Ready-made trackers come with their frequency class. A tracker the person creates asks once, "logged most days or now and then?", and the answer can be changed later.

**Acceptance:** Creating a tracker asks the question once; its answer is editable in the tracker's settings.

**Since:** 2026-11-22 · owner · agreed rewording

### O-R64 · Near-synonym pairs

**Rule:** Pairs that nearly mean the same thing (tense with anxious, very low mood with sad) are left out of patterns MedicAll proposes, until the person selects that pair. A built-in list covers the ready-made words; the person marks pairs among their own words.

**Why:** Such pairs always line up and would be "discovered" over and over.

**Acceptance:** With search on, no proposed pattern pairs two items marked as near-synonyms.

**Since:** 2026-11-22 · owner · agreed rewording

## 9. Calendar and appointments

### D-R65 · Calendar

**Rule:** Appointments live in a calendar of MedicAll's own on the device, two-way with other calendar apps such as Fossify Calendar, with MedicAll's copy as the master. Each event is marked in more than one way, an outside edit is detected by a stored fingerprint, a deletion made outside becomes a cancellation the person can undo, and MedicAll warns if the calendar disappears.

**Evidence:** `docs/research/2026-11-12-six-apps-notes.md` (Medical Calendarlog).

**Acceptance:** An appointment moved in Fossify Calendar is updated in MedicAll, and one deleted there shows as cancelled with an undo.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R66 · Appointment details

**Rule:** An appointment can hold a ward, the tests it needs, and free notes. Each kind of test has a preparation list, written once, of what to do and what to bring.

**Acceptance:** A blood test's preparation list, written once, appears in the R73 notification of every later blood test.

**Since:** 2026-11-22 · owner · agreed rewording

## 10. Emergency card

### O-R67 · Emergency card content

**Rule:** The lock-screen card is a "MUST NOT ADMINISTER / NON SOMMINISTRARE" list in Italian and English: substances or classes in the person's own words, allergies added with their severity, a few neutral instructions the person writes, emergency contacts, and a pointer to fuller information. It never names a reason, a medicine or a condition.

**Why:** Psychiatric medicines and medicines for venereal diseases must not be disclosed to a stranger, while a paramedic still needs to know what not to give.

**Values:**

| Part | Example |
|---|---|
| Must not administer | "Substance A", "class B" |
| Allergies | "Allergy C, severe" |
| Instructions | "check blood sugar" |
| Contacts | name and phone |
| Pointer | "full info: wallet card / ask contact" |

**Acceptance:** With every medicine and condition entered, the card text contains none of their names.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R68 · Android emergency information

**Rule:** MedicAll cannot fill Android's own emergency information; it generates the card text for the person to paste into Settings by hand. Its own lock-screen card is the main route.

**Evidence:** On the OnePlus 6T (EmergencyInfo_O2 3.2.0), both the provider and the edit screen require `com.oneplus.permission.emergencyinfo` (signature or privileged); even adb was refused, 2026-11-13. Other makers were not checked.

**Acceptance:** A "copy for Android emergency information" action puts the card text on the clipboard.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R111 · How the emergency card shows on the lock screen

**Rule:** The emergency card is a pinned notification whose lock-screen version is the card itself; tapping it opens a full-screen card over the lock screen. On a phone with several profiles, the card belongs to the profile marked as the phone's owner.

**Why:** Android 11 to 14 gives apps no lock-screen widget on a phone, and a wallpaper image would replace the person's wallpaper.

**Evidence:** The details are worked out when phase 8 starts.

**Acceptance:** On a locked phone, the pinned notification shows the card text, and a tap opens the full card without unlocking.

**Not:** A lock-screen wallpaper image: it replaces the person's wallpaper.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R69 · Wallet card

**Rule:** A printed wallet card can carry medicines and conditions, each chosen by its own switch. Every switch is off by default.

**Acceptance:** A new medicine is not on the wallet card until its switch is turned on.

**Since:** 2026-11-22 · owner · agreed rewording

## 11. Notifications

### O-R70 · All notifications are optional

**Rule:** Every notification can be turned off, and each is on by default unless R73 says otherwise.

**Why:** The owner sets the defaults; the weekly review is off because the app already sends many notifications.

**Acceptance:** Each notification kind of R73 has its own switch, set as its row's default on a fresh install.

**Since:** 2026-11-22 · owner · own words

### D-R71 · Patterns are reported once

**Rule:** A pattern is reported once: the monthly review lists what is new since the last review, and the appointment summary lists what is new since the last visit to that doctor.

**Acceptance:** A pattern shown in one monthly review does not appear in the next unless it changed.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R72 · Empty reviews are skipped

**Rule:** A month with nothing new sends no monthly review.

**Acceptance:** A month with no new pattern sends no review notification.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R73 · Notification kinds

**Rule:** MedicAll sends the notifications below, each private on the lock screen when R13 applies.

**Values:**

| Kind | When | Default | Example of the full text |
|---|---|---|---|
| Dose due | at each reminder time | on | "Ada · Medicine A 50 mg" |
| Tests to book | 4 and 3 weeks before an appointment that needs tests, while unbooked | on | "Ada · GP on 4 Dec · requires blood and urine tests - to book" |
| Tests booked | 4 and 3 weeks before, once booked | on | "Ada · GP on 4 Dec · blood and urine tests on 28 Oct" |
| Test tomorrow | the evening before any test | on | "Ada · urine test tomorrow · delay breakfast until after the test · remember to bring: sterile container, bottle of water" |
| Appointment tomorrow | the evening before | on | "Ada · GP tomorrow · summary ready · 3 items new since last visit" |
| Monthly review | once a month, when something is new | on | "Ada · Monthly review, October · 4 items" |
| Weekly review | once a week | off | "Ada · Weekly review · doses 26 of 28" |
| Backup check failed | when a file fails R23 | on | "Backup check failed · 1 file damaged · restore available" |
| Original moved or changed | when R24 detects it | on | "Original file moved or changed · 1 attachment" |
| Shortage listed | when a stick-to product appears in the shortage list or is withdrawn | on | "Ada · Shortage listed · 1 medicine you stick to" |
| Appointment changed | when an outside calendar moves or deletes it | on | "Ada · Appointment moved · 18 Nov → 20 Nov" |
| Experiment ended | at an experiment's planned end (R85) | on | "Ada · Experiment ended · review ready" |

**Acceptance:** Each row's notification fires at its trigger in a test with a fake clock, with its default switch state on a fresh install.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R74 · Name first

**Rule:** Every notification about one profile starts with that profile's name.

**Acceptance:** With two profiles, each profile's dose notification starts with its own name.

**Since:** 2026-11-22 · owner · agreed rewording

## 12. Doctor summary

### D-R75 · Doctor summary

**Rule:** The doctor summary is a PDF for a chosen timespan, preset per kind of doctor, holding the contents below and never the excluded items.

**Values:**

| Part | In the summary |
|---|---|
| Timespan preset | GP 1 month; gynecologist 3 to 6 months; editable per doctor |
| Medicines | current medicines with dose and adherence |
| Results | results in the period, with their ranges |
| Trackers | the chosen trackers as small charts |
| Patterns | watched patterns, once the patterns phase ships |
| Episodes | episodes in the timespan, once the episodes phase ships |
| Never | day notes; anything switched off for that doctor |

**Acceptance:** A summary made for a GP covers one month by default and contains no day-note text.

**Since:** 2026-11-22 · owner · agreed rewording

## 13. Patterns

### D-R76 · Pattern types

**Rule:** A pattern is a correlation, never a cause, and is one of the types below. Any type can be narrowed by an "only when" condition.

**Values:**

| Type | Shape | Example |
|---|---|---|
| Side by side | two or more things in the same timespan | a low result of Analyte X, "tired" often, heavy flow |
| Side by side, a value drifting | one value moving with what else changed in that timespan | HbA1c rising across a dose change |
| Before and after a change | one change, a window, a noticeable difference | a brand change, then more headaches |
| Followed by | one thing repeatedly followed by another within a gap | a late dose, then low mood the next day |
| Rhythm | one thing at the same point of a cycle | headache in the days before a period |
| Amounts together | two amounts moving together | sleep hours with mood |

**Acceptance:** none: the types are realised by R77 to R82, which carry their own checks.

**Since:** 2026-11-22 · owner · agreed rewording

### O-R77 · Pattern wording

**Rule:** Patterns are written schematically, with no subject and no verb of noticing: a short label for the type, then the facts with dates and counts. No causal word and no advice appears.

**Why:** "I have noticed…" is too personal, and "You recorded…" implies someone watching.

**Values:**

| Type | Wording |
|---|---|
| Side by side | "Same timespan, 1 Sep – 30 Oct · Analyte X 9 ng/mL ↓ (3 Oct) · "tired" 11 of 14 days · Flow: heavy, last 3 periods" |
| Value drifting | "Same timespan, Mar – Oct · HbA1c 5.6 → 5.9 → 6.2 % · Medicine A 500 → 1000 mg (14 Jun)" |
| Drift alone | "◦ Drift, Mar – Oct · HbA1c 5.6 → 5.9 → 6.2 % · no medicine or dose change in this timespan" |
| Before and after | "Brand change, 12 Oct (Brand A → Brand B) · Headache: 2 of 14 days before, 7 of 14 days after" |
| Followed by | "Late dose → low mood next day · 5 of 6 times, Sep – Oct" |
| Rhythm | "Headache, days −3 to −1 of period · 4 of the last 5 cycles" |
| Amounts together | "Sleep hours ↔ mood score · moved together, 42 days" |

**Acceptance:** No pattern text contains "because", "due to", "caused", "linked to", "may explain", "I", "you", or advice such as "talk to your doctor".

**Not:** "I have noticed…" and "You recorded…".

**Since:** 2026-11-22 · owner · agreed rewording

### D-R78 · Counts

**Rule:** Evidence is shown as "x of y", never as a percentage, and tags are shown in the person's own words.

**Acceptance:** No pattern text contains a "%" sign except inside a lab unit.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R79 · Drift

**Rule:** Drift is three or more results of one analyte moving the same way. It shows even when every value is inside the range, marked by a hollow dot in the normal text colour, with no alarm.

**Acceptance:** Three rising in-range results raise one drift line with a hollow dot and no red colour or warning icon.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R80 · Watched patterns and search

**Rule:** By default MedicAll follows only the patterns the person chooses. Searching for patterns and proposing them is an option, off by default; a proposal is followed only after the person taps "watch this".

**Acceptance:** On a fresh install no pattern is proposed; with search on, a proposal is not followed until accepted.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R81 · Search safeguards

**Rule:** A proposed pattern must pass every bar below. In a pair that mixes a frequent and an episodic item, the episodic item's bar applies.

**Values:**

| Bar | Requirement |
|---|---|
| Episodic items | 3 or more episodes |
| Frequent items | 4 or more weeks of entries, at least 2 on each side of a change |
| Both halves | the pattern holds in each half of the data, split by time |
| Usual rate | the count is shown with the person's usual rate |
| Many comparisons | the bar rises with the number of combinations checked |
| Repetition | the pattern shows again in a later month |

**Acceptance:** A pattern seen in only one half of the data, or on 2 episodes, is never proposed.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R82 · Entries behind a pattern

**Rule:** Tapping a pattern line opens the entries it counts.

**Acceptance:** The entries opened from a line add up to its "x of y".

**Since:** 2026-11-22 · owner · agreed rewording

## 14. Episodes and experiments

### D-R83 · Episodes

**Rule:** An episode has a kind, a start, an ongoing state, an optional recovering phase and an end. The start can be backdated and marked approximate; statistics then treat it as a range. Its length shows both parts.

**Values:**

| Example | Shown as |
|---|---|
| A burnout over 21 days with 18 days recovering | "21 days + 18 recovering" |
| A start marked approximate | "around early February" |

**Acceptance:** An episode with an approximate start enters statistics as a date range, and its length shows the recovering days apart.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R84 · Short illnesses

**Rule:** Short illnesses (cold or flu, allergy, fever, UTI, STI) are episodes. An STI is kept private in the same way as R67.

**Acceptance:** An STI episode never appears on the emergency card or in a lock-screen notification.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R85 · Experiments

**Rule:** An experiment is set up with one change, a start, a planned length and what to watch. At its end MedicAll shows before and after in the R77 style, lists any other change that happened during it, and records the person's verdict on the timeline.

**Values:**

| Part | Content |
|---|---|
| Change | a linked medicine or supplement (start, stop, dose or product), or free text |
| Watch | trackers, episodes, and optionally a lab value with a planned retest |
| Comparison | the same length of time before the start |
| Overlap | "also changed in this timespan" for any other change |
| Verdict | keep, stop or unclear, with a note |

**Acceptance:** A dose change made during an experiment is listed under "also changed in this timespan" in its review.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R86 · Log-first stretch

**Rule:** When there is too little data before an experiment, MedicAll offers a log-first stretch of logging before the change. The person can skip it; the comparison is then marked thin, for example "before: 5 days of data".

**Acceptance:** Skipping the log-first stretch produces a review marked with its days of data before.

**Since:** 2026-11-22 · owner · own words

## 15. Period and bundles

### D-R87 · Period module

**Rule:** Period tracking is an optional module, off by default. It holds flow and BBT, and uses Lunar's cycle maths as they are.

**Values:**

| Cycle rule | As in Lunar |
|---|---|
| Period start | a Light-or-heavier flow day with no flow on the 2 days before |
| Spotting | extends an open period, never opens one |
| Prediction | the median of the averaging window, half a day rounded down |
| Forecast | a window around the likeliest day |
| Long cycles | always counted; the person can exclude a cycle |
| Pause | predictions can be paused, with no reason stored |

**Evidence:** Lunar `SPEC.md` cycle sections and `CycleMath.kt`, read 2026-11-14; Not checked: the independent check of Lunar's cycle maths is still owed, and the period module's phase starts only after it.

**Acceptance:** Lunar's cycle-maths tests pass unchanged against MedicAll's port.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R88 · Period trackers

**Rule:** The period module adds the trackers below. Every universal tracker can be narrowed to "only during period"; a tracker is linked to periods only when the data shows it.

**Values:**

| Tracker | Content |
|---|---|
| Flow | spotting, light, medium, heavy, very heavy |
| Spotting colour | red, brown |
| Collection method | tampon, pad, liner, cup, period underwear, with how many were used per day |
| Discharge | none, sticky, creamy, eggwhite, atypical |
| BBT | number, off by default |

**Acceptance:** Switching the module on adds these trackers and leaves the universal ones as they were.

**Since:** 2026-11-22 · owner · agreed rewording

### O-R89 · Hormonal contraception switch

**Rule:** MedicAll suggests the hormonal-contraception switch only when a contraceptive is in the medicine list. It never infers it from logged flow. With the switch on, flow is still tracked, and no fertile or luteal band is computed.

**Why:** Watching flow for this feels like monitoring the person too closely.

**Acceptance:** Adding a contraceptive raises the suggestion; a change in logged flow alone never does.

**Not:** Suggesting the switch from a change in flow: too close a monitoring.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R90 · Pregnancy and fertility bundle

**Rule:** Pregnancy and fertility tracking is an optional bundle, off by default. Logging a pregnancy pauses predictions; the bundle brings ovulation and pregnancy tests and the pregnancy trackers, and never gives fertile-window advice.

**Acceptance:** With the bundle off, no pregnancy or ovulation item appears; logging a pregnancy pauses period predictions.

**Since:** 2026-11-22 · owner · own words

### D-R91 · Bundles

**Rule:** A bundle is a preset that switches on a set of trackers, episodes and measurements. Every bundle is off by default.

**Values:**

| Bundle | Contents |
|---|---|
| Diabetes | glucose with meal context (R50), insulin through the medicines module |
| Migraine | attack episodes, aura, triggers, as-needed follow-up |
| Chronic pain | pain with baseline (R61) |
| Mood episodes | low or high episodes, mood, energy, sleep hours |
| Autistic burnout | episodes, a custom capacity scale (R55), shutdown and meltdown as events |
| Pregnancy and fertility | R90 |

**Acceptance:** Turning a bundle on switches on its contents for that profile only, and turning it off hides them without deleting entries.

**Since:** 2026-11-22 · owner · agreed rewording

### Q-R92 · Condition bundle names

**Rule:** What is each condition bundle of R91 called in the app?

**Default:** none proposed: the owner will name the condition bundles; the working names in R91 hold until then. Tracker groups are named in R113.

**Since:** 2026-11-22 · owner · own words

## 16. Visual design

### D-R93 · Visual identity

**Rule:** MedicAll's visual identity is its own, decided after the codebase exists and before the first screen is built, with the visual-craft skill leading the process.

**Acceptance:** none: a process rule; no screen work starts before the identity is recorded in this spec.

**Not:** Reusing the palette or design of another of the owner's apps.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R94 · Today screen

**Rule:** The Today screen is the home screen, and it is designed last, once the screens it draws from are settled.

**Acceptance:** none: an ordering rule, held by R100.

**Since:** 2026-11-22 · owner · own words

### D-R95 · Frontend decisions are the owner's

**Rule:** Layout, wording, navigation and palette are discussed with the owner before they are built.

**Acceptance:** none: a working practice.

**Since:** 2026-11-22 · owner · agreed rewording

## 17. Edge cases

### D-R96 · A dose confirmed on two devices

**Rule:** A dose confirmed on two devices before they sync keeps the later confirmation, and the other becomes part of its history.

**Acceptance:** After the sync, the dose shows one confirmation and the stock falls once.

**Since:** 2026-11-22 · builder · own words

### D-R97 · A profile removed

**Rule:** Removing a profile moves the profile and all its records to the bin together; restoring it brings all of them back.

**Acceptance:** A removed and restored profile has the same record count as before.

**Since:** 2026-11-22 · builder · own words

### D-R98 · Time zones and daylight saving

**Rule:** Reminders follow local time, or the home time zone when R28's option is on, and keep their wall-clock time across daylight-saving changes.

**Acceptance:** Emivita's daylight-saving scheduler tests pass in the port.

**Since:** 2026-11-22 · owner · agreed rewording

## 18. Tests and rollout

### D-R99 · Test plan

**Rule:** Each level below runs as its row says before a pull request is merged.

**Values:**

| Level | Covers | Runs |
|---|---|---|
| Unit, shared module | scheduling, cycle maths, pattern bars, unit conversion, sync merge | every pull request |
| Reader tests | the two lab layouts, on neutral sample files that copy each layout | every pull request |
| Instrumented | encryption, backup, notifications, calendar | before a phase closes |
| Device walk | the phase's screens on the three phones of R09 | before a phase closes |
| speccheck | this spec | every pull request |

**Acceptance:** CI runs the unit, reader and speccheck levels on every pull request and fails the check on any failure.

**Since:** 2026-11-22 · builder · own words

### D-R100 · Rollout phases

**Rule:** Version 1 is built in the phases below, in order. A phase closes when its gate holds; a data-loss report sends work back to the phase that caused it.

**Values:**

| Phase | Content | Gate |
|---|---|---|
| 1 | data model, encryption, sync, backup | R16 converges and R22 restores, on two devices |
| 2 | profiles and app lock | R14 and R11 hold on the three phones |
| 3 | medicines | ported scheduler tests pass; device walk |
| 4 | daily log and trackers | device walk |
| 5 | results, the two lab readers, OCR drafts, blood pressure | both readers pass their tests |
| 6 | attachments and sharing | R23 and R25 hold |
| 7 | calendar, appointment notifications, doctor summary | R65 holds with Fossify Calendar; a summary is produced |
| 8 | emergency card and wallet card | R67 acceptance on the lock screen |
| 9 | Today screen | device walk |
| Later | patterns, monthly review and experiments; episodes; bundles with glucose; the period module; Windows; the watch | each its own gate, set before it starts |

**Acceptance:** none: the order and gates are checked as each phase closes.

**Since:** 2026-11-22 · owner · agreed rewording

## Acceptance list

- R01 · Purpose statement: The About screen and the first-run screen show the English text verbatim.
- R03 · Platforms: The Android build declares `minSdk = 30`; the Windows build schedules no reminder.
- R04 · Stack: The shared module compiles for the Android and the JVM desktop targets.
- R05 · Identity and licence: The manifest's `applicationId` is `com.hazia.medicall` and the repository root holds the GPL-3.0 `LICENSE`.
- R06 · Public repository, private data: The publish check passes on every push, and a search of the tracked files finds none of the owner's medicine names or file names.
- R07 · How changes land: Branch protection on `main` requires a pull request and the CI check, and the README states this as its "How changes land" line.
- R08 · Languages: With the phone set to Italian once Italian ships, every string is Italian; with it set to English, no Italian bureaucracy label appears.
- R09 · Test devices: Each phase's walk record names all three phones.
- R10 · Encryption at rest: The database file and an attachment copied off the device do not open without the key; a restart does not ask for the passphrase again.
- R109 · One passphrase for devices, sync and backups: A backup opens by hand with the passphrase; after a change, the next backup opens only with the new one, and a second device asks once for it.
- R11 · App lock: With the lock on, no profile's data is visible after the app returns from the background past its timeout.
- R12 · Network use: The only network call site in the code is the medicine-list download, and it sends no profile data.
- R13 · Lock-screen privacy of notifications: On a locked phone, each notification of R73 shows no medicine, analyte, test or condition name.
- R14 · Profiles: Two profiles on one device keep separate records, and switching a module off for one leaves the other unchanged.
- R110 · Profiles per device and where reminders fire: A profile synced to a second device fires no reminder there until its switch is turned on.
- R15 · Record identity: A record created on two devices offline keeps two distinct ids, and a record whose profile does not exist cannot be written.
- R16 · Sync through Syncthing: Two devices editing the same record offline converge on the later edit after a sync, whatever order the logs are read in.
- R17 · History keeps its labels: After a tracker is renamed, entries made before the rename still show the old label in history and export.
- R18 · Bin: A removed medicine with its history is restored complete from the bin on day 29.
- R19 · Irreversible actions: Emptying the bin cannot complete until "DELETE" is typed; moving a record to the bin asks for no typing.
- R20 · Units are stored canonically: Changing the weight display from kg to lb changes no stored value.
- R21 · Attachments: After a PDF is imported, the original is unchanged in its folder and the copy opens from inside MedicAll.
- R22 · Backups: A backup opens in 7-Zip with the passphrase, its file list is hidden without it, and the CSV holds every result line.
- R112 · 7-Zip writer: A backup written on Android and one written on Windows both open in desktop 7-Zip with the passphrase.
- R23 · File integrity: A file altered by one byte is reported at the next check and restored from a backup holding the intact copy.
- R24 · Link to the original: Moving a linked original raises the R73 notification for a missing original.
- R25 · Sharing: After a share, the temporary copy is gone from the cache; the warning shows once per device.
- R26 · Medicine screens: Setting up a daily medicine with stock takes one form and one Save, with Save visible while the keyboard is open.
- R27 · Scheduling engine: The ported scheduler tests pass in the shared module, including Emivita's daylight-saving cases.
- R28 · Medicine options: Each option, switched on for one medicine, changes that medicine only.
- R29 · Brand and product setting: Scanning a different product for a "stick to product" medicine shows the warning and still lets the dose be confirmed.
- R30 · Steady track: A product change or a dose change ends one band and starts the next; no label in the app calls the band a period.
- R31 · Excipients: The field is saved with the product and shown on its page.
- R32 · Entry by scanning the box: Scanning a Data Matrix fills the product, batch and expiry; scanning a bollino fills the product.
- R34 · AIFA medicine list: A scanned AIC is found with no network, and a refresh replaces the list only after the new copy is read whole.
- R35 · Refresh of the medicine list: No download happens without the person's action.
- R37 · Prescribed, not yet collected: A prescribed medicine schedules no reminder until a box is scanned or added.
- R38 · Holding a medicine before a test: With a blood test tomorrow and the switch on, the morning reminder is replaced by one worded "after the test".
- R39 · Contraindications prompt: An answer given at set-up appears on the emergency card without naming the medicine or condition.
- R40 · Lab report readers: Each reader turns its sample layout into draft lines that match the printed values, units, ranges and flags.
- R42 · Analyte list: "Emoglobina" and "Hb" set as synonyms plot on one trend line; an unknown name stays a draft until assigned.
- R43 · Unit equivalences: An HbA1c result in mmol/mol and one in % plot on the same trend after conversion.
- R44 · Result line: A line read from a PDF shows the printed text and the lab flag unchanged beside the app flag.
- R45 · Range kinds: A bare "5" range asks once for its direction for that analyte and lab, and never again.
- R46 · Trends: Two points from labs with different ranges each show their own range band.
- R47 · Drug level and last dose: A linked result shows the time between the last recorded dose and the collection time.
- R48 · Blood pressure: Three readings entered in one sitting show one averaged entry with the three readings beneath.
- R51 · Daily log: Two entries on one day keep their own times and neither overwrites the other.
- R52 · Tracker kinds: A part's kind cannot be edited after creation; the editor shows it, disabled; Mood stores its scale step and its words as two parts of one entry.
- R53 · Scale direction: A neutral scale is drawn with no green or red at either end.
- R54 · "Nothing today": A day answered "nothing today" counts in the y of an "x of y" count; a day with no answer does not.
- R55 · Custom scale: A custom scale can be made with any number of steps from 2 to 10, and its preset reads "none / low / mid / high".
- R56 · Ready-made trackers: Each tracker in the table can be switched on, and comes with the kind, direction and frequency of its row.
- R113 · Tracker groups: Switching a group on switches on each of its trackers; Sleep is switched on by itself.
- R57 · Mood: An entry can hold one scale step and several words at once.
- R58 · Energy: The scale shows these five words and no end is coloured as better.
- R59 · Tension: Tension is logged apart from mood, and "anxious" never sets a tension step.
- R60 · Libido and sex: Libido can be logged on a day with no sex entry, and the reverse.
- R61 · Pain: A flare entry shows against the region's baseline.
- R62 · Sleep: Two night wakings on one night are two events with their own times.
- R63 · Frequency class: Creating a tracker asks the question once; its answer is editable in the tracker's settings.
- R64 · Near-synonym pairs: With search on, no proposed pattern pairs two items marked as near-synonyms.
- R65 · Calendar: An appointment moved in Fossify Calendar is updated in MedicAll, and one deleted there shows as cancelled with an undo.
- R66 · Appointment details: A blood test's preparation list, written once, appears in the R73 notification of every later blood test.
- R67 · Emergency card content: With every medicine and condition entered, the card text contains none of their names.
- R68 · Android emergency information: A "copy for Android emergency information" action puts the card text on the clipboard.
- R111 · How the emergency card shows on the lock screen: On a locked phone, the pinned notification shows the card text, and a tap opens the full card without unlocking.
- R69 · Wallet card: A new medicine is not on the wallet card until its switch is turned on.
- R70 · All notifications are optional: Each notification kind of R73 has its own switch, set as its row's default on a fresh install.
- R71 · Patterns are reported once: A pattern shown in one monthly review does not appear in the next unless it changed.
- R72 · Empty reviews are skipped: A month with no new pattern sends no review notification.
- R73 · Notification kinds: Each row's notification fires at its trigger in a test with a fake clock, with its default switch state on a fresh install.
- R74 · Name first: With two profiles, each profile's dose notification starts with its own name.
- R75 · Doctor summary: A summary made for a GP covers one month by default and contains no day-note text.
- R77 · Pattern wording: No pattern text contains "because", "due to", "caused", "linked to", "may explain", "I", "you", or advice such as "talk to your doctor".
- R78 · Counts: No pattern text contains a "%" sign except inside a lab unit.
- R79 · Drift: Three rising in-range results raise one drift line with a hollow dot and no red colour or warning icon.
- R80 · Watched patterns and search: On a fresh install no pattern is proposed; with search on, a proposal is not followed until accepted.
- R81 · Search safeguards: A pattern seen in only one half of the data, or on 2 episodes, is never proposed.
- R82 · Entries behind a pattern: The entries opened from a line add up to its "x of y".
- R83 · Episodes: An episode with an approximate start enters statistics as a date range, and its length shows the recovering days apart.
- R84 · Short illnesses: An STI episode never appears on the emergency card or in a lock-screen notification.
- R85 · Experiments: A dose change made during an experiment is listed under "also changed in this timespan" in its review.
- R86 · Log-first stretch: Skipping the log-first stretch produces a review marked with its days of data before.
- R87 · Period module: Lunar's cycle-maths tests pass unchanged against MedicAll's port.
- R88 · Period trackers: Switching the module on adds these trackers and leaves the universal ones as they were.
- R89 · Hormonal contraception switch: Adding a contraceptive raises the suggestion; a change in logged flow alone never does.
- R90 · Pregnancy and fertility bundle: With the bundle off, no pregnancy or ovulation item appears; logging a pregnancy pauses period predictions.
- R91 · Bundles: Turning a bundle on switches on its contents for that profile only, and turning it off hides them without deleting entries.
- R96 · A dose confirmed on two devices: After the sync, the dose shows one confirmation and the stock falls once.
- R97 · A profile removed: A removed and restored profile has the same record count as before.
- R98 · Time zones and daylight saving: Emivita's daylight-saving scheduler tests pass in the port.
- R99 · Test plan: CI runs the unit, reader and speccheck levels on every pull request and fails the check on any failure.
- R101 · No interaction checks: No screen or notification states that two medicines interact.
- R102 · No diagnosis or advice: No text in the app recommends a treatment or names a condition from data.
- R103 · No AI or LLM features: The dependency list holds no machine-learning or language-model library, apart from ZXing and OCR.
- R104 · No cloud or server: No sign-in screen exists, and R12's single network call is the only one.
- R106 · No fertility-window advice: No screen shows a fertile window as advice, with the pregnancy bundle on or off.
- R107 · No wearable sync in version 1: The version 1 manifest requests no Health Connect permission.
- R108 · Standalone app: Mood and each other overlapping tracker can be switched off per profile.

## Out of scope

### D-R101 · No interaction checks

**Rule:** MedicAll never checks drug interactions.

**Why:** A reliable check needs a licensed drug database, and a wrong one is worse than none.

**Acceptance:** No screen or notification states that two medicines interact.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R102 · No diagnosis or advice

**Rule:** MedicAll never diagnoses, interprets a result as a condition, or gives medical advice.

**Why:** R01: none of it is medical advice.

**Acceptance:** No text in the app recommends a treatment or names a condition from data.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R103 · No AI or LLM features

**Rule:** MedicAll contains no AI or language-model feature; patterns are plain counting.

**Why:** Counting can be explained exactly and needs no network or large model.

**Acceptance:** The dependency list holds no machine-learning or language-model library, apart from ZXing and OCR.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R104 · No cloud or server

**Rule:** MedicAll has no account, no server and no cloud storage.

**Why:** The data stays on the person's own devices (R01).

**Acceptance:** No sign-in screen exists, and R12's single network call is the only one.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R105 · No store distribution

**Rule:** MedicAll is published on GitHub only, not on the Play Store.

**Why:** The owner's choice of distribution.

**Acceptance:** none: a distribution choice.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R106 · No fertility-window advice

**Rule:** MedicAll never advises on fertile windows or conception.

**Why:** The period module follows Lunar, a period tracker rather than a fertility app.

**Acceptance:** No screen shows a fertile window as advice, with the pregnancy bundle on or off.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R107 · No wearable sync in version 1

**Rule:** Version 1 does not read wearables or Health Connect.

**Why:** Kept out of the first version's scope.

**Acceptance:** The version 1 manifest requests no Health Connect permission.

**Since:** 2026-11-22 · owner · agreed rewording

### D-R108 · Standalone app

**Rule:** MedicAll is a standalone app, not a module of Factotum. Features that overlap with Factotum, such as mood, can be switched off so a person using both keeps them in one place.

**Why:** The owner's choice.

**Acceptance:** Mood and each other overlapping tracker can be switched off per profile.

**Since:** 2026-11-22 · owner · agreed rewording

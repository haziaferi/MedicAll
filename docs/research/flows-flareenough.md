# Flare Enough 0.1.5: symptom and daily-log flows, mapped from source

Source: a local copy of Flare Enough 0.1.5 (GPL-3.0, Kotlin/Compose). Read only: nothing was built, run or edited.
Every claim below comes from reading the source. Where a statement is my inference from the code rather than something the code states, it is marked **(inference)**. Where something is not in the code, the text says **not found** and lists the names grepped for (zero hits).

Short prefixes used in citations:

| Prefix | Expands to |
|---|---|
| `app/` | `app/src/main/java/club/hiraeth/flareenough/` |
| `str` | `app/src/main/res/values/strings.xml` |
| `sch/` | `schedule/src/main/kotlin/club/hiraeth/flareenough/schedule/` |
| `scht/` | `schedule/src/test/kotlin/club/hiraeth/flareenough/schedule/` |

How taps are counted (same rule as the MedTimer and Emivita notes): each click, toggle, selection, picker confirm, back or Add counts as 1 tap. Entering text counts as 1 tap to focus the field; the typing is not counted. Scrolling is not a tap but is noted where the target is far down a long list.

---

## 1. First run

1. **No intro, no onboarding, no permission prompt at launch.** `MainActivity` sets the content to `Gate()` (`app/MainActivity.kt:66-70`). App lock is off by default (`app/data/settings/SettingsRepository.kt:39-40`), so `Gate` goes straight to `FlareApp()` (`app/MainActivity.kt:90-97`). Not found: `onboard`, `Onboard`, `firstRun`, `first_run`, `Welcome` (zero hits in `app/`). The only `POST_NOTIFICATIONS` request lives on the Reminder health screen (`app/ui/reminders/ReminderHealthScreen.kt:95,135`).
2. **Start screen = Today tab** (`app/ui/FlareAppUi.kt:261-266`), with a bottom bar Today / Log / History / Stillness, icon plus word (`app/ui/navigation/TopDestination.kt:17-26`). Settings is a gear in the top bar (`app/ui/FlareAppUi.kt:229-240`).
3. **What Today shows on a fresh install** (`app/ui/today/TodayScreen.kt:83-126`):
   - an optional "Reminders may be late. Tap to fix." banner if exact alarms are not allowed (`:89-91`, `str:39`);
   - the Next dose card with "No medications yet. Add one in Settings." (`:192-198`, `str:26`);
   - a "How are you?" card, subtitle "Log how you feel in a few taps." (`:266-274`, `str:29-30`), which switches to the Log tab (`app/ui/FlareAppUi.kt:279`);
   - a Stillness shortcut card (`:276-284`).
4. **Log tab on a fresh install.** There are no trackers yet: none are seeded in the database (the create callback seeds body parts only, `app/data/db/FlareDatabase.kt:183-188`). Because `noTrackersAtAll` is true (`app/ui/symptoms/SymptomLogViewModel.kt:65-68`), the Log screen shows the **starter set card** at the top (`app/ui/symptoms/SymptomLogScreen.kt:77-79`): "Pick some trackers to start", "You can rename, add, or remove these any time." (`str:303-304`). It lists six starters, **all pre-ticked**: Pain, Stiffness, Fatigue, Mood, Anxiety, Sleep quality (`app/ui/symptoms/StarterAndAddTracker.kt:36-43,52`; `str:306-311`). "Add these" creates them all as 5-level trackers with the default labels None / Mild / Moderate / Bad / Severe (`StarterAndAddTracker.kt:76-89`; `app/ui/symptoms/TrackerLabels.kt:19-23`; `str:313-317`).
5. Below the starter card, the rest of the Log screen is already live: Flare day switch, Same as yesterday, the tracker cards, Add tracker, body map, notes and tags (`SymptomLogScreen.kt:81-124`).

**Taps to the first log entry from launch: 3** — "How are you?" card (1), "Add these" (1), one level on the Pain tracker (1). A user who ignores the starter card can log a flare in 2 taps (card, Flare switch).

---

## 2. Data model for logging

Room database `flare_enough.db`, version 4, 16 entities, `exportSchema = true` (`app/data/db/FlareDatabase.kt:41-62,75-76`). Enums are stored by name, not ordinal; string lists are joined with the unit-separator control character (`app/data/db/Converters.kt:13-35,87-89`).

### Tables used for the daily log

| Table (entity) | Fields (type) | Key / constraint | Cite |
|---|---|---|---|
| `symptom_trackers` (`SymptomTrackerEntity`) | `id` Long auto, `name` String, `type` TrackerType, `levelLabels` List<String>?, `durationOptions` List<String>?, `unit` String?, `hidden` Boolean, `sortOrder` Int, `createdAtMillis` Long | PK `id` only | `app/data/db/entity/SymptomEntity.kt:16-27` |
| `symptom_entries` (`SymptomEntryEntity`) | `id` Long auto, `trackerId` Long, `epochDay` Long, `valueInt` Int?, `valueText` String?, `createdAtMillis` Long | FK `trackerId` → trackers, **ON DELETE CASCADE**; indices on `trackerId`, `epochDay`; **no unique (trackerId, epochDay)** | `SymptomEntity.kt:36-56` |
| `flare_days` (`FlareDayEntity`) | `epochDay` Long PK, `flare` Boolean, `createdAtMillis` Long | one row per day | `SymptomEntity.kt:79-85` |
| `body_map_entries` (`BodyMapEntryEntity`) | `id`, `epochDay` Long, `region` String (part key), `state` BodyState, `createdAtMillis` | unique (`epochDay`, `region`) | `SymptomEntity.kt:67-77` |
| `period_days` (`PeriodDayEntity`) | `epochDay` Long PK, `flow` PeriodFlow, `createdAtMillis` | one row per bleeding day | `SymptomEntity.kt:92-97` |
| `day_notes` (`DayNoteEntity`) | `id`, `epochDay` Long, `text` String, `createdAtMillis` | many per day | `SymptomEntity.kt:99-106` |
| `tags` (`TagEntity`) | `id`, `name` String, `createdAtMillis` | unique `name` | `SymptomEntity.kt:108-114` |
| `day_tags` (`DayTagCrossRef`) | `epochDay` Long, `tagId` Long | PK (`epochDay`, `tagId`), FK cascade | `SymptomEntity.kt:116-133` |
| `body_part_categories`, `body_parts` | `key` (stable, unique), `name` String? (null = translated default), `visible`, `sortOrder`, `categoryId` | added in migration 3→4 | `app/data/db/entity/BodyPartEntity.kt:18-64`; `FlareDatabase.kt:122-153` |

Enums: `TrackerType` = FIVE_LEVEL, YES_NO, DURATION, NUMBER (`app/data/db/entity/Enums.kt:56-68`); `BodyState` = SORE, PAINFUL, SWOLLEN (`Enums.kt:99-103`); `PeriodFlow` = SPOTTING, LIGHT, MEDIUM, HEAVY (`Enums.kt:110-115`).

### How each tracker type is stored

One column holds every type: `valueInt` is "the level (1 to 5), a yes or no (1 or 0), a duration band index, or a number" (`SymptomEntity.kt:32-34`). Confirmed in the inputs:
- **5-level**: `onSelect(level)` for level 1..5 (`app/ui/symptoms/SymptomLogScreen.kt:297-300`). Labels are copied into the tracker row at creation (`StarterAndAddTracker.kt:84,145`), so the five words are frozen in the creation locale **(inference)**.
- **Yes/no**: 1 or 0 (`SymptomLogScreen.kt:329-341`).
- **Duration band**: the **index** into the tracker's own `durationOptions` list (`SymptomLogScreen.kt:346-355`); defaults "Under 15 min" … "Over 2 hours" (`str:390-396`), copied into the row at creation (`StarterAndAddTracker.kt:146`).
- **Number**: digits only, so whole non-negative integers (`SymptomLogScreen.kt:359-370`). No decimals, no negatives **(inference from `filter { it.isDigit() }`)**. The unit field exists but the Add dialog has no field to set it (`StarterAndAddTracker.kt:103-157`), so `unit` is always null for user-created trackers.
- `valueText` is written as `null` by every UI path (`SymptomLogScreen.kt:99`; `app/ui/dayedit/DayEditViewModel.kt:156-159`). It is read only as a fallback for duration display (`app/ui/history/HistoryScreen.kt:338`).

### Renaming and versioning of trackers

- **No versioning.** Entries point at the tracker by `id`, and display text is looked up from the tracker row **at read time**: History uses `tracker.name` and `tracker.levelLabels` / `durationOptions` (`app/ui/history/HistoryScreen.kt:294-298,330-339`); the CSV header uses current `tracker.name` (`app/export/LogExporter.kt:106`). So a rename would relabel all past entries everywhere, and editing a duration list would silently re-map old band indexes **(inference)**.
- In practice **renaming is not reachable**: see section 5.
- Body parts, by contrast, use a stable `key` and a nullable custom `name`, so "renaming a part never loses its history" (`BodyPartEntity.kt:35-39`), and the migration was written to never touch existing marks (`FlareDatabase.kt:116-121`).

### Tags

Free text, normalised into a list. `addTagToDay` trims the text, inserts it into `tags` with `OnConflictStrategy.IGNORE` (unique name), then links it to the day (`app/data/repository/DayRepository.kt:90-97`; `app/data/db/dao/DayDao.kt:78-91`). Matching is exact and case-sensitive (`WHERE name = :name`, `DayDao.kt:84`), so "Weather" and "weather" become two tags **(inference)**. The ViewModel exposes all existing tags (`app/ui/symptoms/SymptomLogViewModel.kt:84-86`), but the screen never passes them to `TagsSection` (`SymptomLogScreen.kt:124`): **no suggestions or pick list**, the user retypes the tag each day.

### One entry per day, or timestamped?

**One value per tracker per day, keyed by local `epochDay`.** `setValue` looks up the existing row for (tracker, day) and updates it, "so the latest value wins rather than piling up" (`app/data/repository/SymptomRepository.kt:41-66`). The uniqueness is enforced only in code, not by the schema (`SymptomEntity.kt:46`). Flare and period are one row per day by primary key. Body map is one state per part per day (unique index). **Notes are the only multi-entry item**, each with its own `createdAtMillis` (`DayRepository.kt:79-80`). There is no time-of-day field on symptom entries; `createdAtMillis` is the time the row was first created and is not changed on later edits (`SymptomRepository.kt:63-64`).

---

## 3. Flows, step by step

### (a) Log a bad day: flare + 2 symptoms + a note (from the Today start screen, trackers already set up)

| Step | Screen / composable | Taps |
|---|---|---|
| 1. Tap "How are you?" | `TodayScreen` → `HowAreYouCard` → switches to Log tab (`app/ui/today/TodayScreen.kt:120-122`; `app/ui/FlareAppUi.kt:279`) | 1 |
| 2. Flip "Flare day" switch | `SymptomLogScreen` → `FlareRow` (`SymptomLogScreen.kt:81,148-163`); `toggleFlare` writes immediately (`SymptomLogViewModel.kt:106-111`) | 1 |
| 3. Pick a level on symptom 1 | `TrackerCard` → `FiveLevelInput` (`SymptomLogScreen.kt:94-101,287-326`) | 1 |
| 4. Pick a level on symptom 2 | same | 1 |
| 5. Scroll past the remaining trackers, Add tracker, and the whole body map (21 default parts, `app/data/db/BodyPartDefaults.kt:16-39`, each a 56 dp row, `SymptomLogScreen.kt:422-437`) | `BodyMapSection` | scroll |
| 6. Focus "Add a note", type | `NotesSection` (`SymptomLogScreen.kt:493-499`) | 1 |
| 7. Tap Add | `NotesSection` Add button (`SymptomLogScreen.kt:500-504`) | 1 |

**Total: 6 taps** plus typing and one long scroll. Every control saves at once; there is no Save button and no Undo on this screen. A note typed but not confirmed with Add is held only in `remember` state (`SymptomLogScreen.kt:482`) and is lost on leaving the tab **(inference)**.

### (b) Add one symptom in the middle of the day, from the start screen

- Tracker already exists: "How are you?" (1) → scroll to the tracker → tap a value (1). **2 taps.** It **overwrites** the morning value for that tracker if one was set (`SymptomRepository.kt:52-65`). There is no way to record a second reading the same day, and no time is stored apart from the first-created timestamp.
- New symptom that has no tracker yet: "How are you?" (1) → scroll → "Add tracker" (1) → focus name (1), type → pick type chip (1, or 0 for the default 5-level) → Add (1) → tap a value on the new card (1). **5-6 taps.** New trackers get `sortOrder` 0 (`StarterAndAddTracker.kt:142-148`, field default `SymptomEntity.kt:25`), so they sort by name among other 0-order trackers (`app/data/db/dao/SymptomDao.kt:32`) **(inference: the starter set uses 0..5, so a new tracker lands near the top)**.

### (c) Correct yesterday's log

The Log tab is fixed to today (`SymptomLogViewModel.kt:41`); past days are edited through History.

| Step | Screen / composable | Taps |
|---|---|---|
| 1. History tab | bottom bar (`app/ui/FlareAppUi.kt:245-256`) | 1 |
| 2. Tap yesterday's cell (if yesterday is in last month: Previous month first, +1) | `HistoryScreen` → `CalendarGrid` / `DayCell` (`app/ui/history/HistoryScreen.kt:100-105,207-252`) | 1 (+1) |
| 3. Tap "Edit" | `HistoryScreen.kt:116-126` → route `day_edit/{epochDay}` (`app/ui/FlareAppUi.kt:76,144-168`) | 1 |
| 4. Change the value | `DayEditScreen` reuses `TrackerCard`, `FlareRow`, `BodyMapSection`, `NotesSection`, `TagsSection` (`app/ui/dayedit/DayEditScreen.kt:109-133`) | 1 |
| 5. Back | `DayEditScreen.kt:71-76` | 1 (optional) |

**Total: 4 taps** (5 with Back; 5-6 when yesterday falls in the previous month). Day edit also lets the user set each scheduled dose to Taken / Skipped / clear (`DayEditScreen.kt:138-168`; `DayEditViewModel.kt:96-112`).
Limits found in the code:
- A tracker value **cannot be cleared** once set: 5-level, yes/no and duration only ever set a value (`SymptomLogScreen.kt:297-355`); only a Number field emptied to blank writes null (`:362-364`).
- Day edit lists **visible** trackers only (`DayEditViewModel.kt:116-123`), so values from a hidden tracker would not be editable.
- An edit made today to a past day's new entry stores today's `createdAtMillis` (`DayEditViewModel.kt:158`), and the timeline shows that as the entry's time (`app/ui/history/HistoryViewModel.kt:207`): yesterday's entry would read as logged at, say, 14:05 **(inference)**.
- Day edit has no "Same as yesterday" button (`DayEditScreen.kt:85-134`).

### (d) See how the last two weeks went

| Step | Screen / composable | Taps |
|---|---|---|
| 1. History tab | calendar of the current month opens on today (`app/ui/history/HistoryModels.kt:62-67`) | 1 |
| 2. If the fortnight crosses into last month: Previous month | `MonthHeader` (`HistoryScreen.kt:140-157`) | +1 |
| 3. Read the shading, flare borders and dots on each cell | `DayCell` (`HistoryScreen.kt:207-252`) | 0 |
| 4. For detail, tap each day to load its timeline under the calendar | `TimelineRow` (`HistoryScreen.kt:128-136,254-275`) | 1 per day |

**Total: 1 tap for the overview (2 across a month boundary); 1 + 14 (+1) = 15-16 taps to read every day's detail.** There is no week or range view, no list of days, and no chart (section 4).

### "Same as yesterday"

Button on the Log screen (`SymptomLogScreen.kt:85-92`, `str:320`) → `sameAsYesterday()` → `copyDay(today - 1, today)` (`SymptomLogViewModel.kt:127-131`). `copyDay` reads **every `symptom_entries` row for yesterday** and calls `setValue` for each onto today (`SymptomRepository.kt:68-74`). So it:
- copies symptom tracker values only (`valueInt`, `valueText`), including hidden trackers' entries (the query has no `hidden` filter, `SymptomDao.kt:56-57`);
- **overwrites** today's value for any tracker that had a value yesterday; leaves today's value for trackers with none yesterday;
- does **not** copy the flare flag, body map, period flow, notes or tags (none are touched in `copyDay`);
- has no confirmation and no undo; tapping it with an empty yesterday does nothing, with no message.

---

## 4. History and trends

### Month calendar shading ("how heavy a day was")

`maxSymptomLevel = max(valueInt)` over that day's entries **whose tracker is FIVE_LEVEL**; yes/no, duration and number trackers are ignored (`app/ui/history/HistoryViewModel.kt:159-163`; field doc `HistoryModels.kt:16-17`). The cell colour is `shades[maxSymptomLevel - 1]` from five pastel tints, or the plain surface colour if there is no 5-level value (`HistoryScreen.kt:64-77,214-217`). So the formula is **the single worst 5-level score of the day**, not a sum or average. Note that this treats every 5-level tracker as "higher = worse", so a "Sleep quality" or "Mood" tracker where 5 is good would shade a good day as heavy **(inference; the starter labels are None … Severe, `str:313-317`)**.

Other cell marks:
- **Flare**: a 2 dp border in the theme's error colour (`HistoryScreen.kt:227-228`). The selected day's primary border replaces it (`:225-226`), so a selected flare day loses its flare border.
- **Activity dot**: shown if the day has any dose, symptom, stillness session, note, body mark or period day (`HistoryViewModel.kt:164-169`; `HistoryScreen.kt:242-249`). Tags alone do not count (not in the list).
- Week starts Monday (`HistoryScreen.kt:181`; `str:422-430`).

### Day timeline

Built for the selected day from doses, symptom entries, flare, period, body marks, notes and stillness sessions (`HistoryViewModel.kt:183-243`), sorted by time with untimed items last (`:244`). Each row is icon + title + subtitle + time (`HistoryScreen.kt:254-275`). Times come from `actualTimeMillis`/`scheduledTimeMillis` for doses and **`createdAtMillis`** for symptoms, period, body marks and notes (`HistoryViewModel.kt:196,207,219,224,232`); flare has no time (`:216`). Tags are not shown on the timeline (no `TimelineItem` for tags, `HistoryModels.kt:25-59`). "Nothing logged this day." when empty (`str:44`).

### Charts and dose/symptom correlation

**Not found.** There is no chart composable; the only hits for `chart`/`Chart` are comments and a leftover placeholder string ("Your calendar, timeline and charts will live here.", `str:41`, used nowhere). Grepped: `Chart`, `chart`, `trend`, `Canvas` in `app/ui/history/` and `correlat` across `app/` — the only `correlat` hits are comments stating there is none (`app/export/LogExporter.kt:22`; `app/export/PdfReporter.kt:24-25`). Doses and symptoms sit side by side on the same day timeline, nothing more.

### Wording that avoids interpretation (`str`)

- "Nothing logged this day." (`str:44`)
- "A plain summary to keep or show someone. A record only, with no advice." (`str:458`)
- "This records the day only." (period hint, `str:338`)
- "It does not diagnose, recommend treatments, adjust doses, check interactions, or interpret symptoms." (`str:187`)

---

## 5. Customisation

- **Default trackers**: none in the database. The six starters are offered, pre-ticked, only while there are zero trackers (section 1).
- **Add tracker**: name + one of four type chips (`StarterAndAddTracker.kt:100-157`). No way to set custom level labels, custom duration bands, or a unit.
- **Rename, reorder, hide, delete trackers: not reachable in the UI.** The repository has `updateTracker` and `deleteTracker` (`SymptomRepository.kt:21-23`), but grep finds **zero callers** outside the repository and DAO. Strings exist for a settings entry — "Symptom trackers", "Rename, hide, or remove trackers", "Hidden", "Show", "Hide" (`str:360-364`) — but none is referenced by any Kotlin file (grepped `settings_symptoms`, `tracker_hidden`, `tracker_show`, `tracker_hide`: hits only in `strings.xml`). `SettingsScreen` has no tracker row (`app/ui/settings/SettingsScreen.kt:65-118`). So the README line "Simple trackers you can add and rename" (`README.md:35`) and the starter subtitle "You can rename, add, or remove these any time" (`str:304`) are **not backed by the code**. If deletion were wired up, it would cascade-delete all the tracker's history (`SymptomEntity.kt:39-44`).
- **Body parts** are fully editable on their own screen: add category/part, rename, move up/down, show/hide, delete, restore defaults (`app/ui/settings/BodyPartsScreen.kt:64-470`; `app/data/repository/BodyPartRepository.kt:73-133`). Hiding keeps marks (`BodyPartEntity.kt:41-42`); deleting a part with marks deletes the marks, after a count-bearing warning (`app/data/db/dao/BodyPartDao.kt:73-76`; `BodyPartsScreen.kt:223-225`; `str:176-179`). Custom parts get a random key `part_<UUID>` (`BodyPartRepository.kt:64`).
- **Period tracker toggle**: Settings → "Period tracking", off by default (`app/data/settings/SettingsRepository.kt:23-32`; `SettingsScreen.kt:83-89`; `str:149-150`). When on, a four-level flow row appears on Log and Day edit (`SymptomLogScreen.kt:82-84`; `DayEditScreen.kt:111-113`). Tapping the selected level again clears it (`SymptomLogViewModel.kt:113-119`). Turning it off hides the row but keeps the data; History still shows period days (`HistoryViewModel.kt:218-220`) **(inference: History does not read the setting)**.
- **Other toggles**: App lock only (device unlock via BiometricPrompt, `SettingsScreen.kt:90-106`; `app/MainActivity.kt:39-48,73-78`). No toggle hides the body map, notes, tags, Stillness or medication modules (grepped `observe.*Enabled` in `SettingsRepository.kt`: only period and app lock).
- A "Daily check-in" notification channel is created (`app/reminders/NotificationChannels.kt:19,47-51`; `str:215-216`) but **nothing posts to it** (grep `DAILY_CHECK_IN`: only the channel definition).

---

## 6. Medication: two checks only

### (a) How a missed dose is recorded

**It is not recorded.** `DoseStatus.MISSED` exists (`app/data/db/entity/Enums.kt:40-45`) but **no code writes it**: grep for `MISSED` across the repo hits only the enum, the export label (`app/export/Labels.kt:23`) and the History status mapping (`app/ui/history/HistoryScreen.kt:290`). There is no job that turns an unanswered slot into a row.
- On **Today**, a past slot with no event stays `PLANNED` and is labelled "Due" (`app/ui/today/TodayViewModel.kt:63-67`; `TodayScreen.kt:239`; `str:35`). The Next dose card picks the **earliest overdue** planned slot before any future one (`TodayViewModel.kt:81-83`), so an unanswered morning dose keeps the card until acted on.
- **History** and the **CSV/PDF** only list dose events, so an unanswered dose simply does not appear (`HistoryViewModel.kt:193-201`; `LogExporter.kt:55-73`). If a MISSED row ever existed, History would label it "Skipped" (`HistoryScreen.kt:290`) and the CSV "Missed" (`Labels.kt:23`; `str:442`).
- So README "Missed doses are recorded gently" (`README.md:31`) is not backed by the code.

### (b) UK clock changes, DST and time-zone changes

- Dose times are stored as minutes past local midnight (`app/data/db/entity/MedicationEntity.kt:21-22,52-73`) and resolved per date with `LocalDateTime.atZone(zone)` (`sch/ScheduleEngine.kt:26-32`): a non-existent 01:30 in spring moves to 02:30; a repeated 01:30 in autumn fires once at the earlier offset (`sch/ScheduleEngine.kt:12-19`). `occurrencesBetween` scans one extra day either side (`:47-50`).
- **Tests cover UK DST**: 6 tests in `scht/DstTest.kt` on `Europe/London` for 29 March and 25 October 2026 — 08:00 keeps wall-clock time (`:23-34`), spring 01:30 → 02:30 (`:36-43`), exactly one occurrence on both change days (`:45-56`, `:66-77`), autumn fires at the first 01:30 (`:58-64`), `nextOccurrence` across spring forward (`:79-88`).
- **Time-zone change**: `RescheduleReceiver` listens for `TIME_SET` and `TIMEZONE_CHANGED` (plus boot, update, exact-alarm permission) and calls `rescheduleAll()` (`app/src/main/AndroidManifest.xml:65-75`; `app/reminders/receivers/RescheduleReceiver.kt:16-30`), which uses `ZoneId.systemDefault()` at that moment (`app/reminders/AlarmScheduler.kt:50`). **No test covers a zone change**: the only zones in tests are `Europe/London` (`scht/DstTest.kt:18`) and `UTC` (`scht/ScheduleEngineTest.kt:20`); there are no tests of the receivers.
- Risk **(inference)**: Today matches events to slots by exact scheduled instant (`TodayViewModel.kt:52-54,62`). After a zone change, today's slots are recomputed at new instants, so doses already logged before the change no longer match and show as "Due" again. Also `TodayViewModel` and `SymptomLogViewModel` capture `today` and the zone once at creation (`TodayViewModel.kt:30-33`; `SymptomLogViewModel.kt:41`), so a ViewModel that survives past midnight keeps writing to the previous day.

---

## 7. Export, backup, privacy

- **No internet permission**, by design (`app/src/main/AndroidManifest.xml:5-7`; `str:189`). `android:allowBackup="false"` (`AndroidManifest.xml:27`), so no Google cloud backup.
- **Backup** = a raw copy of the SQLite file after `PRAGMA wal_checkpoint(TRUNCATE)`, written to a user-picked document (`app/data/backup/BackupManager.kt:28-44`; picker `application/octet-stream`, `app/ui/data/DataScreen.kt:102`). Includes everything in the database (`BackupManager.kt:13-15`), but **not** DataStore settings (period toggle, app lock), which live outside the database (`SettingsRepository.kt:12-19`) **(inference)**.
- **Restore** reads the whole file, checks only the 16-byte SQLite header (`BackupManager.kt:51-58,71-79`), closes the DB, deletes WAL/SHM and overwrites the file; the app then restarts (`:59-67`; `str:463-464`). No schema-version check: restoring a backup from a newer app version is not guarded **(inference)**.
- **Encryption: none.** Grepped `SQLCipher`, `sqlcipher`, `encrypt`, `Encrypt`, `cipher`, `FLAG_SECURE`: zero hits. The backup file is plain SQLite.
- **CSV exports** (UTF-8, CRLF, quote-escaping, `app/export/LogExporter.kt:146-164`):
  - medication log, one row per dose event: date, planned time, name, strength, status, taken-at (`:38-76`);
  - daily log, one row per day with any data: date, flare (Yes/blank), period flow, **one column per tracker** (current names), joints "Part (state); …", notes joined " | ", tags joined ", " (`:78-144`). Includes hidden trackers (`SymptomDao.kt:38-40`). A 5-level value is exported as its **label word only** ("Moderate"), not the number, falling back to the number only when no label exists (`app/export/Labels.kt:46-48`); a duration as its band text (`:54-55`); a number with its unit (`:56-61`). History, by contrast, shows "3: Moderate" (`HistoryScreen.kt:332-335`).
  - No date-range choice; always the full history. No guard against spreadsheet formula injection (fields starting with `=` are not escaped, `LogExporter.kt:159-164`).
- **PDF** "report": title, generated date, "This is a record only, not medical advice.", medication names, then the daily log newest first (`app/export/PdfReporter.kt:36-80`; `str:482-487`). No charts, no date range.

---

## 8. Code qualities

- **Modules**: `:app` (Android) and `:schedule` (pure Kotlin/JVM, no Android plugin, JUnit 5, JVM toolchain 21; `schedule/build.gradle.kts:1-20`; `settings.gradle.kts`). Inside `:app`: `data/` (db, dao, entity, repository, settings, backup), `di/`, `export/`, `reminders/`, `stillness/`, `ui/<feature>/` (Screen + ViewModel per feature). About 10,000 lines of Kotlin in `app/src/main` (wc over all `*.kt`).
- **Tests** (`@Test` counted in `*.kt`; there are no `*.java` files):
  - `app/src/test`: **5**, all in `ConvertersTest.kt` (enum and string-list round trips).
  - `app/src/androidTest`: **3**, all in `FlareDatabaseTest.kt` (medication round trip, cascade delete, `findForSlot`; `:49-98`).
  - `schedule/src/test`: **22** (DstTest 6, ScheduleEngineTest 9, TimeOfDayTest 7).
  - **Zero tests touch symptom logging**: nothing for `SymptomRepository.setValue`, `copyDay`, `DayRepository`, history shading, exports or migrations. `room-testing` is wired (`gradle/libs.versions.toml:72`; `app/build.gradle.kts:127`) but no `MigrationTestHelper` test exists, and `app/schemas/` holds only a README, no exported JSON (`app/schemas/README.md`).
- **Platform-neutral logging code**: every entity, DAO, `Converters`, `BodyPartDefaults` and every repository except settings/backup has no `android.*` import (grep). They do depend on `androidx.room` annotations (Room 2.8.5, `gradle/libs.versions.toml:27`), which Room KMP can compile for non-Android targets, and on `java.time` **(inference)**. The Android-bound files are `FlareDatabase.kt` (Context), `SettingsRepository.kt` (DataStore/Context), `BackupManager.kt` (Uri). The shading and timeline logic sits in `HistoryViewModel` (AndroidX ViewModel). The truly portable, tested module is `:schedule`.
- **Architecture**: single Activity (`app/MainActivity.kt:28`), Compose Navigation 2.9.0 with a root NavHost plus a nested tab NavHost (`app/ui/FlareAppUi.kt:64-170,260-343`); one ViewModel per screen built with `viewModelFactory { initializer { … } }` (`FlareAppUi.kt:267-274`); StateFlows with `WhileSubscribed(5_000)`. **Manual DI**: `AppContainer` with lazy singletons, "There is no Hilt" (`app/di/AppContainer.kt:19-32`). Room version 4 with three hand-written migrations and no destructive fallback (`FlareDatabase.kt:78-153,190-204`). minSdk 26, targetSdk 36, AGP 8.13.0, Kotlin 2.3.21 (`gradle/libs.versions.toml:7-14`).
- Accessibility care is visible: 48-64 dp targets (`SymptomLogScreen.kt:88,301`; `StarterAndAddTracker.kt:65`), icons paired with words (`TopDestination.kt:12-16`), period and body states carry words not only colour (`SymptomLogScreen.kt:185-189`).

---

## 9. Ideas worth taking for MedicAll, and things to avoid

### Take

- **Day-keyed records with one value per tracker per day**, written immediately on tap with no Save step (`SymptomRepository.kt:41-66`; `SymptomLogViewModel.kt:100-104`). Fast and calm for a daily log. MedicAll should add a unique index (trackerId, day) that Flare Enough only enforces in code (`SymptomEntity.kt:46`).
- **One `valueInt` column for all tracker types** keeps the entry table simple (`SymptomEntity.kt:32-34`), but store the type and labels per entry or version the tracker (see Avoid).
- **Stable keys plus nullable custom names** for anything the user can rename, with translated defaults when the name is null (`BodyPartEntity.kt:12-16,35-39`). Apply this to trackers too.
- **Hide is non-destructive; delete warns with a count** of what will be lost (`BodyPartEntity.kt:41-42`; `str:176-179`).
- **Starter set, all pre-ticked, one tap to accept**, shown only while empty (`StarterAndAddTracker.kt:46-98`; `SymptomLogScreen.kt:77-79`). Gives a 3-tap path to the first entry.
- **Edit any past day with the same controls** as today (`DayEditScreen.kt:46-50,109-133`): one component set, one behaviour.
- **Body map as a list, tap to cycle states**, readable by screen readers (`DayRepository.kt:50-70`; `str:333`).
- **Optional period tracker, off by default, record-only wording** (`SettingsRepository.kt:23-28`; `str:338`).
- **Record-only wording** in the PDF and About (`str:187,458,484`).
- **DST-safe wall-clock scheduling in a pure JVM module with named-date tests** (`sch/ScheduleEngine.kt:9-32`; `scht/DstTest.kt`).
- **No-destructive-migration rule** written into the DB builder (`FlareDatabase.kt:190-204`).

### Avoid

- **Display labels resolved from the current tracker row** for past entries (`HistoryScreen.kt:330-339`; `LogExporter.kt:106`): a rename or an edited duration list rewrites history. Version trackers or snapshot labels.
- **Shipping strings and README claims for features that do not exist**: tracker rename/hide/delete (`str:304,360-364`; zero callers of `updateTracker`/`deleteTracker`), "Missed doses are recorded" (`README.md:31`; no writer of `MISSED`), a daily check-in channel with no sender (`NotificationChannels.kt:47-51`). MedicAll should gate claims on code, as this study did.
- **Missed shown as "Skipped"** in History (`HistoryScreen.kt:290`) while CSV says "Missed" (`Labels.kt:23`): one status, one word, everywhere.
- **"Heaviness" = max of 5-level scores** regardless of polarity (`HistoryViewModel.kt:159-163`): a high "Sleep quality" shades a day as bad. Let each tracker declare whether higher is worse, or shade by flare/symptoms only.
- **"Same as yesterday" that silently overwrites** symptom values, skips flare/body/notes/tags, and has no undo (`SymptomRepository.kt:68-74`). Say what it copies and offer Undo.
- **No way to clear a value** once set (`SymptomLogScreen.kt:297-355`), and no Undo on the Log screen (contrast the dose Undo snackbar, `TodayScreen.kt:71-81`).
- **`today` and zone captured once in ViewModels** (`SymptomLogViewModel.kt:41`; `TodayViewModel.kt:30-33`): recompute on day change and on `TIMEZONE_CHANGED`.
- **Slot matching by exact instant** (`TodayViewModel.kt:52-54`) without a zone-change test; MedicAll should store the local date + minutes of the slot as well, and test a zone change.
- **`createdAtMillis` used as the event time** on the timeline (`HistoryViewModel.kt:207,224,232`), so back-filled entries show the edit time. Keep "time it happened" separate from "time it was recorded".
- **Tags without suggestions** and case-sensitive matching (`SymptomLogScreen.kt:124`; `DayDao.kt:84`) produce near-duplicate tags, which the consistency axis penalises.
- **Unencrypted plain-SQLite backup with header-only validation** (`BackupManager.kt:51-79`), and settings left out of it.
- **No tests on the logging side** (section 8): MedicAll's daily log needs repository, copy-day, shading and migration tests from the start.

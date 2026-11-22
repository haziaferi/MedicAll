# Profiles, OCR, record types and calendar links: MediLog, RxDroid, Medical Calendarlog

Read-only code study, 2026-11-12. The three repos under `_ref/` were read with Read/Grep and nothing in them was built or run. Every claim below cites `path:line`. Paths start with `medilog/`, `rxdroid/` or `medicalcalendarlog/` (relative to `_ref/`). Where something is missing from the code, this note says "not found" and lists the names that were grepped. The READMEs were checked against the code, and where they disagree it is noted.

---

## 1. MediLog: profiles

**Data model.** Every person is one flat `Profiles` row: `id`, `name`, `age` (a plain integer, not a birth date), `sex` (free text: 'Male', 'Female', 'Other'), `emoji` (the avatar) and `createdAt` (`medilog/lib/data/database/tables/profiles.dart:3-10`). The create screen defaults sex to 'Male' (`medilog/lib/features/profiles/screens/profile_create_screen.dart:19`) and offers three options (`:124-128`).
- **Relationships** between people (parent, carer, guardian): not found. Grepped `relation`, `parent`, `guardian`, `birth`, `dateOfBirth`.
- **Pets**: not found. Grepped `pet`, `pets`, `animal`, `species`, `veterinar`, `vet` in all three repos.

**How records are tied to a person.** Each per-person table has `profileId` as a foreign key to `Profiles` with `onDelete: cascade`:
- visits (`medilog/lib/data/database/tables/doctor_visits.dart:6`)
- conditions (`health_conditions.dart:6`)
- insurances (`insurances.dart:6`)
- period entries (`period_entries.dart:6`)
- reminders (`reminders.dart:6`)
- test reports (`test_reports.dart:7`)
- vitals (`vital_readings.dart:6`)

All of those files are in `medilog/lib/data/database/tables/`. Prescriptions hang off a visit rather than a person (`prescriptions.dart:6`). Every repository query filters on `profileId`, for example `medilog/lib/data/repositories/visit_repository.dart:10-12` and `reminder_repository.dart:10-12`.
- **Caveat about the cascade.** SQLite only enforces foreign keys after `PRAGMA foreign_keys = ON`. The database opens with no migration strategy and no `beforeOpen` (`medilog/lib/data/database/database.dart:33-47`), so the cascade probably never runs. Grepped `foreign_keys`, `beforeOpen`, `MigrationStrategy`: not found. In practice deleting is moot: `deleteProfile` exists (`medilog/lib/features/profiles/controllers/profile_controller.dart:37-44`), but no screen calls it (grep of `deleteProfile(` finds only the controller and the repository). `updateProfile` is also unused (`medilog/lib/data/repositories/profile_repository.dart:25`).

**Switching person.** The app opens on a "Who is using MediLog?" picker (`medilog/lib/app.dart:38`, `medilog/lib/features/profiles/screens/profile_list_screen.dart:17`). Tapping a card sets one global `selectedProfileIdProvider`, which is held in memory only (`profile_controller.dart:52`), and replaces the screen with Home (`profile_list_screen.dart:111-116`). Home has a "Switch Profile" menu item that goes back to the picker (`medilog/lib/features/home/home_screen.dart:49-64`). Because the selection is never saved, every cold start asks again. The only per-person difference in the UI is that the Period Tracker tile is hidden when `sex == 'Male'` (`home_screen.dart:159`).

**Per-person privacy.** None. Anyone holding the phone can pick any profile. Grepped `encrypt`, `cipher`, `secure_storage`, `local_auth`, `biometric`, `password`, `pin` in `lib/`, the manifest and `pubspec.yaml`: not found.

**Encryption.** Not present. The database is a plain file, `medilog.sqlite`, opened with `NativeDatabase.createInBackground` in the app documents folder (`medilog/lib/data/database/database.dart:42-47`). The dependencies include `sqlite3_flutter_libs` (`medilog/pubspec.yaml:15`), not SQLCipher. There are no keys and so no key storage.

**What the README actually says.** The README does not claim encryption. It claims "100% Offline" and "No Cloud Sync" (`medilog/README.md:25-26`). The website's privacy text says security "depends on the security of your physical device" (`medilog/docs/index.html:88`). An "encrypted records" claim, if one exists, does not come from this repo.

**Other privacy claims.**
- "No proprietary dependencies" (`medilog/README.md:27`) is contradicted by Google ML Kit (`medilog/pubspec.yaml:28`).
- The release manifest declares no permissions; INTERNET appears only in the debug manifest (`medilog/android/app/src/debug/AndroidManifest.xml:6`).

## 2. MediLog: OCR

**Engine.** Google ML Kit text recognition, `google_mlkit_text_recognition: ^0.14.0` (`medilog/pubspec.yaml:28`), locked at 0.14.0 (`medilog/pubspec.lock:451-458`). It uses the Latin script only (`medilog/lib/services/ocr_service.dart:5`). Tesseract: not found.

**What it extracts.** Only the whole recognised text as one string, `recognizedText.text` (`ocr_service.dart:8-12`). It does not parse fields, medicine names, dates or lab values. Grepped `medicineId` assignments: `_selectedMedicineId` is declared (`medilog/lib/features/visits/screens/prescription_add_screen.dart:29`) and read (`:53`) but never set.

**On-device or network.** Recognition runs locally (`processImage`, `ocr_service.dart:11`), and the release build has no INTERNET permission (see section 1). ML Kit is a closed Google binary. Whether its model is bundled in the APK or downloaded through Play Services is decided in the plugin's own Gradle file, which is not in this repo, so it cannot be verified here.

**How results flow into records.**
- `ScanScreen` picks an image with `image_picker` from the camera or gallery (`medilog/lib/features/ocr/screens/scan_screen.dart:20-34`, `ocr_service.dart:19-21`). It shows the text and returns it on "Use Text" (`scan_screen.dart:44-48`, `:94-98`).
- In Add Report, the text is appended to the notes field as "Scanned Data:" (`medilog/lib/features/reports/screens/report_add_screen.dart:80-89`) and saved as `extractedText` (`:39`). The column is defined at `medilog/lib/data/database/tables/test_reports.dart:21`.
- The image itself is not kept: `_imagePath` is commented "not implemented yet" (`report_add_screen.dart:25`), so `filePath` is always null (`:40`).
- The Home "Scan Report" tile only shows a snackbar with the first 30 characters and saves nothing (`medilog/lib/features/home/home_screen.dart:122-129`). It would also throw on text shorter than 30 characters, because of `substring(0, 30)`.

**The ~100 MB medicine database.** It is not in the repo.
- The README claims "Offline database of 253,000+ Indian medicines" (`medilog/README.md:13`), "~100MB storage" (`:34`) and a file `assets/medicines.db` (`:89`).
- `assets/` holds only icons, screenshots, featured graphics and a `health_tips.json` that contains `[]`.
- `find -iname '*.db' -o -iname '*medicine*'` returns nothing, and no code opens or queries such a file (grepped `medicine`, `medicines.db`, `rootBundle`).
- Source and licence as stated, unverified: CREDITS names the "Indian Medicine Dataset" by JuniorAlive on GitHub, "Used under terms compatible with GPL v3" (`medilog/CREDITS.md:5-9`). No licence text for it is in the repo.
- It is irrelevant to MedicAll anyway: the data is India-specific and comes with pricing.

## 3. MediLog: record types

All tables below are in `medilog/lib/data/database/tables/` and registered at `medilog/lib/data/database/database.dart:21-32`.

| Type | Fields (besides id/profileId) | Cite |
|---|---|---|
| Doctor visit | doctorName, doctorContact, hospital, visitDate, notes, followUpDate | `doctor_visits.dart:4-13` |
| Prescription (per visit) | medicineId (never set), medicineName, dosage text, frequency text ("1-0-1"), durationDays, notes, startDate, isActive | `prescriptions.dart:4-19` |
| Test report | visitId (optional), testName, date, result, notes, filePath, extractedText | `test_reports.dart:5-22` |
| Vital reading | type (Weight/BP/HR/Temp/Sugar), unit, value1, value2 (for example diastolic), recordedAt, notes, context ("Before Meal") | `vital_readings.dart:4-22` |
| Health condition | name, type (Chronic/Acute), diagnosedAt, notes | `health_conditions.dart:4-11` |
| Insurance | providerName, policyNumber, premiumAmount, premiumDueDate, validUntil, coverageDetails, contactNumber, notes, policyDocumentPath | `insurances.dart:4-23` |
| Period entry | startDate, endDate, flowIntensity, symptoms (comma or JSON string), notes, mood, isPainful, isPrediction | `period_entries.dart:4-21` |
| Reminder | type (Medicine/FollowUp/Insurance/Vitals/Period), title, description, scheduledAt, repeatPattern, isActive, entityId | `reminders.dart:4-22` |
| Health tip (global) | category, title, content, source | `health_tips.dart:3-9` |

**Attachments.** There are path columns (`test_reports.dart:16-18`, `insurances.dart:21-22`), but no code ever writes a file. `policyDocumentPath` appears only in its table definition, and `filePath` is always null (section 2). No copy into app storage: grepped `copy(`, `getApplicationDocuments` outside the database open.

**Reminders.**
- One notification is scheduled per reminder. It uses the database id as the notification id, the title as typed, and the body `description ?? "It's time for your $type"` (`medilog/lib/features/reminders/controllers/reminder_controller.dart:48-52`).
- It carries no profile name, so on a shared phone the notification does not say who it is for.
- `repeatPattern` is saved (`:42`) but never passed to the scheduler: `matchDateTimeComponents` exists (`medilog/lib/services/notification_service.dart:45`, `:65`) but the controller omits it. Repeats therefore do not work.

## 4. RxDroid: several patients

**Verdict: the patient feature is a dormant stub.** The schema supports patients, but nothing in the UI creates or chooses one.

**Model.** `Patient` is a table `patients` with a unique, nullable `name` and an id (`rxdroid/src/main/java/at/jclehner/rxdroid/db/Patient.java:27-33`). The default patient has id 1 and `name == null` (`:30`, `:51-53`). `Drug` has a foreign `patient` field, marked `// XXX` (`rxdroid/src/main/java/at/jclehner/rxdroid/db/Drug.java:146-149`); when unset, `getPatientId()` falls back to the default (`Drug.java:596-597`). The schema upgrade created the table, inserted the default patient and set every existing drug to it (`rxdroid/src/main/java/at/jclehner/rxdroid/db/DatabaseUpgrader.java:104-108`). The patient is not part of drug equality (`Drug.java:799`, commented out). `DoseEvent` has no patient field of its own; it links only to the drug (`rxdroid/src/main/java/at/jclehner/rxdroid/db/DoseEvent.java:60-76`).

**Creating or choosing a patient.**
- The only place a `Patient` is created is the janitor, which creates the default one (`rxdroid/src/main/java/at/jclehner/rxdroid/DoseEventJanitor.java:67-68`).
- The drug editor carries a `patient` field and copies it back (`rxdroid/src/main/java/at/jclehner/rxdroid/DrugEditFragment.java:575`, `:611`, `:633`), but no preference shows it.
- The "Patient" menu item is `android:visible="false"` (`rxdroid/src/main/res/menu/drug_list_fragment.xml:34-38`). Its click handler is an empty case that falls through to "add drug" (`rxdroid/src/main/java/at/jclehner/rxdroid/DrugListActivity2.java:581-590`).
- The pager never passes a patient id: the line is commented out (`DrugListActivity2.java:258`). The fragment therefore reads 0 (`:474`), and `getAllDrugs(0)` returns only default-patient drugs (`rxdroid/src/main/java/at/jclehner/rxdroid/db/Entries.java:65-83`).
- `getAllPatientNames()` exists (`Entries.java:85-94`), but no caller was found (grepped `getAllPatientNames`).

**Schedule per patient.** There is none. Dose times are four global settings, morning, noon, evening and night (`rxdroid/src/main/java/at/jclehner/rxdroid/Settings.java:149`). One alarm chain covers all drugs (`rxdroid/src/main/java/at/jclehner/rxdroid/NotificationReceiver.java:304-374`).

**Who a dose is for, in the UI.** Not shown. Grepped `patient` in `res/layout`: no hits.

**Notification wording and grouping.**
- There is one summary notification for all drugs of all patients: `mAllDrugs = Database.getAll(Drug.class)` (`NotificationReceiver.java:172`).
- The text is counts only: the title is "Doses", the body is "%d due, %d missed" (`NotificationReceiver.java:507-520`, strings `rxdroid/src/main/res/values/strings.xml:57-63`, `:208`).
- Low supply names the first two drugs, "X and N others" (`NotificationReceiver.java:537-545`, `strings.xml:65-70`).
- Doses due at the same moment are grouped by construction, because a single notification covers a dose-time slot. It carries a "Take all" action (`NotificationReceiver.java:604-614`).
- Inconsistency: "Take all" marks only patient 0's doses (`NotificationReceiver.java:176-178`, `Entries.java:481-500`), while the counts include every drug.
- Android notification groups exist in code, but are off (`mUseGroups = false`, `NotificationReceiver.java:440-441`). They are enabled only through the wearable hack flag (`:415`, `:460`).

**Side note on the app lock.** RxDroid has a 4-digit PIN lockscreen (`rxdroid/src/main/java/at/jclehner/rxdroid/LockscreenActivity.java:41`). The PIN is stored in plain preferences and compared as a string (`:259-260`). Do not copy that.

## 5. Medical Calendarlog: calendar

**Core design.** The system calendar is the only store for logged events. The app keeps no local database of events, only templates and settings in DataStore (`medicalcalendarlog/app/src/main/java/de/ntdote/medicalcalendarlog/repository/PreferencesRepository.kt:34`, `:53-61`). Every screen, reminder and widget re-queries the calendar (for example `.../service/ReminderService.kt:162-199` and `.../service/HourlyReminderWorker.kt:212-231`). Below, `.../` stands for `medicalcalendarlog/app/src/main/java/de/ntdote/medicalcalendarlog/`.

**Own calendar?** No. It lists existing calendars, reading `_ID`, `CALENDAR_DISPLAY_NAME`, `ACCOUNT_NAME`, `OWNER_ACCOUNT` and `CALENDAR_ACCESS_LEVEL` (`.../repository/CalendarRepository.kt:85-143`).
- It auto-picks the first writable non-system calendar whose name contains "med" or "log", and otherwise any writable one (`CalendarRepository.kt:145-181`). "System" is guessed from names such as holiday, birthday and contacts (`.../data/CalendarEvent.kt:50-58`). Writable means access level 500 or higher (`CalendarEvent.kt:39-45`).
- Auto-pick happens only when nothing was ever selected (`.../viewmodel/MainViewModel.kt:126-131`). The id is stored as `selected_calendar_id` (`PreferencesRepository.kt:34`, `:124-126`).
- Grepped `ACCOUNT_TYPE_LOCAL`, `Calendars.CONTENT_URI` inserts, `CALLER_IS_SYNCADAPTER` inserts: not found. It never creates a calendar.

**Columns written.** On insert it writes `DTSTART` (now minus 1 minute), `DTEND` (now), `TITLE`, `CALENDAR_ID`, `EVENT_TIMEZONE` and an optional `DESCRIPTION` (`CalendarRepository.kt:223-235`).
- Events are always "now"; there is no backdating or future scheduling, and no other `DTSTART` write was found.
- The title is the template name plus an optional value, such as "Ibuprofen400" (`CalendarRepository.kt:324-329`). The description is user notes plus `#tags` (`:332-340`).
- Inserts are retried 3 times on binder or "frozen provider" errors (`:240-277`).
- Not written: `HAS_ALARM`, `ALL_DAY` (except on backups), colour, `UID_2445`, `_SYNC_ID`, `SYNC_DATA*`, ExtendedProperties, Reminders rows, `RRULE`. Grepped all of these: not found, except `ALL_DAY` in `.../utils/ConfigExportImport.kt:147`.

**How it identifies its own events.** By title text only. Hashtags are stripped and the title is NFC-normalised (`.../utils/EventMatching.kt:11`, `:39-41`).
- A template with input matches "name + numeric expression suffix"; one without input needs an exact title (`EventMatching.kt:50-66`).
- So any event in the chosen calendar with a matching title counts, including one typed by hand in another calendar app. There is no marker that says "created by MCL".
- Config backups are events titled `[MCL] Configuration Backup - <ts>`, with the template JSON in `DESCRIPTION` (`ConfigExportImport.kt:36`, `:139-150`). They are excluded from data queries with `TITLE NOT LIKE` that prefix (`CalendarRepository.kt:376-377`).

**Reading back edits made elsewhere.** Edits are picked up because nothing is cached.
- A `ContentObserver` on `Events.CONTENT_URI` (descendants on) triggers a debounced recalculation of reminders (`.../MedicalCalendarLogApplication.kt:98-147`).
- A CRC32 over `id:title:start:dosage` detects changes cheaply (`CalendarRepository.kt:423-438`).
- Change a title in Fossify and the event re-matches, or stops matching, on the next query. Move its time and the reminder maths follow.

**Gaps in the read query.**
- It reads the `Events` table, not `Instances`, so a recurring event counts once, at its first `DTSTART`. Grepped `Instances`, `RRULE`: not found.
- It does not filter `Events.DELETED` (selection at `CalendarRepository.kt:376`; grepped `DELETED`: not found). On a synced account, a row deleted but not yet synced keeps `DELETED=1` and would still be counted.

**Deletes.**
- Logged events are never deleted or updated by the app. Only `insert` exists for them; grepped `contentResolver.update`: not found.
- Backups are deleted with `CALLER_IS_SYNCADAPTER=true` plus the account name and type (`CalendarRepository.kt:590-604`). That bypasses the normal sync-deletion path, which is a questionable habit for a non-sync-adapter app. The code then checks that the row is gone (`:618-636`).
- `deleteConfigFromCalendar` uses a plain delete (`ConfigExportImport.kt:205-217`).

**Permissions.** `READ_CALENDAR`, `WRITE_CALENDAR`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM`. It explicitly removes `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE` (`medicalcalendarlog/app/src/main/AndroidManifest.xml:5-19`). The FOSS flavour adds `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (`medicalcalendarlog/app/src/foss/AndroidManifest.xml:6`). Both calendar permissions are needed (`CalendarRepository.kt:32-34`).

**If the user removes the calendar.** Nothing detects it.
- The stored id stays (`MainViewModel.kt:126-135` auto-picks only when the id is null). Queries quietly return no events.
- Time-based reminders then stop, because "no events" yields no reminder (`ReminderService.kt:446-451`).
- No "calendar missing" check was found (grepped `calendarExists`, `selectedCalendarId` checks).

**Two-way sync and conflict handling.** It does no sync, because there is no second copy. "Two-way" is achieved by making the calendar the only source, so no conflicts can arise. The cost: no stable identity (titles), no medical data beyond the title and description text, and data loss if the calendar or its account is removed.

## 6. Medical Calendarlog: other features

- **Event kinds** are user templates (`.../data/Template.kt:7-33`) of three types:
  - METRIC: an absolute value with a unit, such as weight or mood
  - DECAYING: medication with a concentration curve
  - GENERAL: plain logging

  The types are defined at `Template.kt:164-169`. A DECAYING template has drugs with drugType, factor, unit, and decay by HALF_LIFE or CONSTANT (`Template.kt:66-76`, `:171-175`). Values can be arithmetic expressions such as "8*3" (`.../data/CalendarEvent.kt:11-12`, `EventMatching.kt:12-14`).
- **Fields per event**: only title (name plus value), time and description (notes plus #tags). See section 5.
- **Reminders** are relative to the last log, not fixed times.
  - The next reminder is the last matching event plus `reminderThresholdMillis` (`ReminderService.kt:438-451`).
  - There is a per-drug concentration threshold (`ReminderService.kt:297-311`, `:454-461`).
  - Alarms use `setExactAndAllowWhileIdle` (`.../utils/AlarmScheduler.kt:79-81`), backed by an hourly worker (`HourlyReminderWorker.kt:212`).
  - Notification wording: "Reminder: %s", "Overlooked: %s", "Time to log %s" (`medicalcalendarlog/app/src/main/res/values/strings.xml:27-34`, used at `.../utils/NotificationBuilder.kt:90-97`).
  - You can log straight from a notification (`.../service/NotificationActionReceiver.kt:232-235`, `:311-314`).
- **Widgets** for templates (`.../service/TemplateWidgetProvider.kt`) and graphs (`.../ui/screens/GraphScreen.kt`).
- **Profiles**: none. Grepped `patient`, `profile`, `person`: no hits.

## 7. What MedicAll should take vs avoid

**Take**
- **One `profileId` foreign key on every record**, and every query filtered by it (`medilog/lib/data/database/tables/doctor_visits.dart:6`, `medilog/lib/data/repositories/visit_repository.dart:10-12`). Turn foreign keys on for real; MediLog never does (`medilog/lib/data/database/database.dart:33-47`).
- **A "who is this for?" picker as the entry point**, with a visible switch action (`medilog/lib/features/profiles/screens/profile_list_screen.dart:17`, `medilog/lib/features/home/home_screen.dart:49-64`). Remember the last profile, which MediLog does not (`medilog/lib/features/profiles/controllers/profile_controller.dart:52`). For a carer with 6 profiles, add an "everyone" view.
- **A default or self profile created on first run**, and existing rows backfilled to it when the patient concept was added later (`rxdroid/src/main/java/at/jclehner/rxdroid/db/DatabaseUpgrader.java:104-108`).
- **One notification per dose-time slot, with a "take all" action** (`rxdroid/src/main/java/at/jclehner/rxdroid/NotificationReceiver.java:507-520`, `:604-614`). Split it by profile and name the person.
- **Watching the calendar with a `ContentObserver`, debounced** (`medicalcalendarlog/app/src/main/java/de/ntdote/medicalcalendarlog/MedicalCalendarLogApplication.kt:98-147`). Add a cheap checksum to skip work when nothing changed (`.../repository/CalendarRepository.kt:423-438`).
- **Retrying inserts when the calendar provider is frozen** (`CalendarRepository.kt:240-277`). Check access level 500 or higher before writing (`.../data/CalendarEvent.kt:39-45`).
- **Logging from the notification** (`.../service/NotificationActionReceiver.kt:232-235`).
- **On-device OCR into an editable text field** that the user confirms (`medilog/lib/features/reports/screens/report_add_screen.dart:80-89`), with a FOSS engine (ML Kit is proprietary, `medilog/pubspec.yaml:28`) and with the source image kept (MediLog drops it, `report_add_screen.dart:25`).

**Avoid**
- **A plaintext database.** MediLog uses plain `NativeDatabase` (`medilog/lib/data/database/database.dart:45-46`). RxDroid stores its PIN in plain preferences (`rxdroid/src/main/java/at/jclehner/rxdroid/LockscreenActivity.java:259-260`).
- **`age` as an integer, and `sex` as the gate for features** (`medilog/lib/data/database/tables/profiles.dart:6-7`, `medilog/lib/features/home/home_screen.dart:159`). Store a birth date, and make modules opt-in per profile.
- **Patient-less notifications.** MediLog puts no name in the body (`medilog/lib/features/reminders/controllers/reminder_controller.dart:48-52`). RxDroid shows counts only, across all patients (`rxdroid/src/main/java/at/jclehner/rxdroid/NotificationReceiver.java:172`, `:507-520`), and its "take all" covers one patient only (`:178`).
- **Half-built schema features**: RxDroid's hidden patient menu (`rxdroid/src/main/res/menu/drug_list_fragment.xml:34-38`), MediLog's unwired `repeatPattern` (`medilog/lib/data/database/tables/reminders.dart:16`) and its never-set `medicineId` (`medilog/lib/features/visits/screens/prescription_add_screen.dart:29`).
- **Matching calendar events by title** (`medicalcalendarlog/app/src/main/java/de/ntdote/medicalcalendarlog/utils/EventMatching.kt:50-66`), and the calendar as the only store (`.../repository/PreferencesRepository.kt:53-61`).
- **Reading `Events` without `DELETED=0`, and without `Instances`** (`.../repository/CalendarRepository.kt:376`).
- **Deleting as a fake sync adapter** on someone else's account (`CalendarRepository.kt:590-600`).
- **Silent failure when the chosen calendar disappears** (`.../viewmodel/MainViewModel.kt:126-135`, `.../service/ReminderService.kt:446-451`).
- **Config backups as calendar events** (`.../utils/ConfigExportImport.kt:139-150`). They leak medical configuration into any synced calendar account.

### Sketch: profile model (plain words)

- **Profile**
  - id: a UUID, so it is stable across Syncthing devices
  - kind: person or pet
  - display name, avatar or colour
  - birth date, which may be approximate for pets
  - for a person: sex at birth or gender, both optional and never used to hide features
  - for a pet: species and breed, weight unit, vet contact
  - enabled modules: medications, appointments, vitals, period and so on, opt-in per profile
  - calendar link settings (below)
  - created and updated times, and a deleted flag (a soft delete, so it syncs)
- **Every record** (dose, appointment, visit, report, vital, attachment) carries `profileId`, a UUID, its own `updatedAt`, and its own `deleted` flag.
- **Devices.** One app lock covers the whole app and all its profiles. Each device has a "profiles on this device" list: an adult's phone holds one, the carer's holds about six. Syncthing shares the folder; which profiles a device shows and notifies is a per-device setting, not data.
- **Notifications** always start with the profile name: "Anna: Metformin 500 mg due". When several profiles are due in the same slot, group them per profile, with "take all for Anna".

### Sketch: two-way calendar link that survives Fossify edits

1. **A dedicated calendar.** MedicAll creates its own calendar, one per profile or one shared, as the owner chooses. It uses `ACCOUNT_TYPE_LOCAL`, inserted with `CALLER_IS_SYNCADAPTER=true`; this is the documented way to create a calendar, and Fossify lists LOCAL calendars. Remember the calendar's `_ID` and also tag it with `CAL_SYNC1 = "medicall:<profileUUID>"`, so it can be found again by tag if its id changes.
2. **MedicAll is the source of truth.** The calendar is a mirror plus an input channel. Each appointment row keeps `calendarEventId`, `lastSeenCalendarHash` and `updatedAt`.
3. **Writing.** Set TITLE, DTSTART, DTEND, EVENT_LOCATION, DESCRIPTION and EVENT_TIMEZONE. Put the stable identity where Fossify will not overwrite it:
   - `UID_2445 = <appointmentUUID>@medicall`
   - a sync column (`SYNC_DATA1 = appointmentUUID`, which needs the sync-adapter URI on our own LOCAL calendar)
   - an `ExtendedProperties` row `medicall.id`

   Use more than one marker, because a user edit can clear some fields but rarely all of them. Never rely on the title.
4. **Reading edits.** Register a `ContentObserver` on Events (debounced, like `MedicalCalendarLogApplication.kt:98-147`), run a scan at app start, and run a periodic worker. For each row in our calendar with `DELETED=0`:
   - find the appointment by `_ID`, falling back to `UID_2445` or `SYNC_DATA1`
   - hash the user-visible fields
   - if the hash differs from `lastSeenCalendarHash`, the user edited the event in Fossify: copy the time, title and location back into MedicAll, bump `updatedAt`, and store the new hash
5. **New events.** An event with no MedicAll marker in our calendar was created in Fossify. Import it as a new appointment for that calendar's profile, then write the markers onto it.
6. **Deletes.** A missing row, or `DELETED=1`, for an appointment that still exists in MedicAll means it was deleted in Fossify. Mark the appointment cancelled (soft delete) and show "removed in calendar, undo?". A delete in MedicAll deletes the row through the normal (non-sync-adapter) URI.
7. **Conflicts.** If both sides changed since the last hash, keep the MedicAll version, write it back to the calendar, and log the calendar version as a note the user can restore. This is last-writer-loses for the calendar side, but nothing is lost silently.
8. **Calendar removed.** If the calendar `_ID` and the `CAL_SYNC1` tag are both gone, tell the user and offer to recreate it and re-mirror. Never fail silently, which is MCL's behaviour (`.../service/ReminderService.kt:446-451`).
9. **Recurring appointments.** Read through `Instances`, and write `RRULE` only for series MedicAll owns. An edit Fossify makes to one occurrence creates an exception row (`ORIGINAL_ID`); map it back as a one-off change.
10. **Syncthing.** Calendar rows are per device. Only the device whose profile is local mirrors that profile into its own calendar, so the same appointment is not written to the calendar twice by two devices.

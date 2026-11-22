# Emivita: user-facing flows, mapped from source

Source: snapshot of `master` 5eab639 at `_ref/emivita-5eab639` (outside this repository), read only (nothing was built or run).

Citation convention:
- Kotlin paths are relative to `app/src/main/java/com/hazia/emivita/`.
- `strings:N` is a line in `app/src/main/res/values/strings.xml`.
- `manifest:N` is a line in `app/src/main/AndroidManifest.xml`.
- `test/...` is a path under `app/src/test/java/com/hazia/emivita/`.

Tap rule (from the brief): every click, toggle, selection, picker confirm, navigation back or Save counts 1. A text field counts 1 for the focus; typing is not counted, but the typed fields are listed. Swipe gestures are counted as 1 and marked as gestures.

The "main screen" is Today. The start page defaults to `StartPage.TODAY` (`core/data/settings/SettingsRepository.kt:316-317`) and the NavHost is rooted on it (`navigation/EmivitaNavHost.kt:117`, `:290-294`). The bottom bar is always visible and has four tabs: Today, Medicines, History, Settings (`navigation/EmivitaRoute.kt:68-73`; labels at strings:204-207).

---

## 1. First run

What a fresh install shows, in order:

1. **Splash screen.** It fades out over the first frame (`MainActivity.kt:24-27`, 200 ms at `:40`).
2. **Spinner, no onboarding.** While the profile list loads, a spinner shows. When the list is empty, the app silently creates one profile named "Me" (`navigation/EmivitaApp.kt:69-85`, constant at `:43`). There are no intro pages. The KDoc says onboarding was removed on purpose (`navigation/EmivitaApp.kt:50-54`).
3. **Disclaimer dialog.** Title "Before you start" (strings:665). It cannot be dismissed with Back or by tapping outside (`feature/disclaimer/DisclaimerDialog.kt:39-41`). The user ticks "I understand" (`:47-54`, strings:667), then taps **Continue**, which is disabled until the box is ticked (`:57`, strings:668). **2 taps.**
4. **Database-recovered dialog (conditional).** It shows only when a recovery marker exists (`navigation/EmivitaApp.kt:106-116`). It offers "Restore", which opens Data & Backup, or "continue empty" (`feature/recovery/DatabaseRecoveredDialog.kt:24`). A fresh install does not see it.
5. **App lock.** The biometric lock is off by default, so the gate renders the content straight away (`feature/lock/AppLockGate.kt:135-138`).
6. **Today, with the "Let reminders through" bottom sheet over it.**
   - The sheet shows once, and only when a grant is missing (`navigation/EmivitaApp.kt:117-120`, `:134-146`). Title strings:712; body strings:713.
   - It lists only the missing grants among four, in this fixed order: Notifications, Exact alarms, Full-screen alarms, Unrestricted battery (`feature/permissions/ReminderPermissions.kt:59-64`, `:73-79`, `:228-249`).
   - Each row has its own **Allow** button (strings:711). The foot shows **Later**, or **Done** once nothing is missing (`:240-246`, strings:714-715).
   - What each Allow opens (`feature/permissions/ReminderPermissions.kt:157-185`):
     - Notifications: the system runtime dialog on Android 13+ the first time (`:168-172`), the app's notification settings afterwards (`:174`).
     - Exact alarms: the system "alarms & reminders" page (`:176-179`). The manifest also declares `USE_EXACT_ALARM` (manifest:22), so this row is normally already granted and hidden *(unverified — needs device)*.
     - Full-screen alarms: the Android 14+ special-access page (`:124-132`, `:180`).
     - Unrestricted battery: the one-tap system "ignore battery optimisations" dialog, falling back to the list (`:137-139`, `:181`).
   - Each grant is re-read on resume, because the system pages return no result (`:98-110`).
   - **Typical cost:** Allow + system "Allow" for notifications (2), Allow + system "Allow" for battery (2), then Done (1), so about 5 taps. Which rows actually show depends on the device *(unverified — needs device)*.
   - "Later" leaves the remaining grants to a card on Today, "Some reminders may not arrive" (strings:716-718). It has Allow buttons and "Not now" (`feature/today/TodayScreen.kt:1178-1180`; `feature/permissions/ReminderPermissions.kt:253-267`).

What the main screen then shows on an empty install:
- The text "Nothing scheduled yet. Add a medicine and a reminder to see it here." (`feature/today/TodayScreen.kt:344-350`, strings:194).
- An extended FAB, **Log dose** (`feature/today/TodayScreen.kt:409-419`).

Today has **no add-medicine control**. Log dose with no medicines only says "No medicines yet — add one first." (strings:193). The user has to find the **Medicines** tab on their own.

Nothing in the first-run flow asks for a name, a language, a theme or any medicine data.

**Minimum from install to a usable Today:** 2 taps (disclaimer) + about 1-5 (permission sheet), so 3 to 7 taps.

---

## 2. Set-up task, step by step

Defaults that matter:

| Default | Value | Where |
|---|---|---|
| Quick-add sheet | dose "1", unit blank, time 08:00, repeat Daily | `feature/medicines/QuickAddMedicineSheet.kt:101-105`, `:146`, `:177` |
| Fixed-time form | 08:00, dose 1.0, Daily | `feature/reminders/AddReminderScreen.kt:126` |
| "Repeat until answered" | ON, every 30 min, max 3 | `feature/reminders/ReminderFormParts.kt:90-93` |
| Every other Advanced field | off or empty | `feature/reminders/ReminderFormParts.kt:86-103` |
| Medicine notification priority | Standard | `core/data/entity/Medicine.kt:35` |

Time fields are writable text fields. They accept "2000", "20:00", "20.00" or "800" (`ui/form/FormFields.kt:145-151`). The clock icon at the end of the field opens a Material TimePicker dialog instead (`ui/form/FormFields.kt:178-195`). So typing a time costs 1 tap, while the picker route costs 3 or more (icon, hour, OK).

### 2a. Ibuprofen 400 mg, daily at 08:00 and 20:00

| # | Screen | Tap | Code |
|---|---|---|---|
| 1 | Today | **Medicines** tab | `navigation/EmivitaNavHost.kt:84-97` |
| 2 | Medicines (empty: "No medicines yet. Tap + to add your first one.", strings:141) | **+** FAB, "Add medicine" | `feature/medicines/MedicinesScreen.kt:353-355` |
| 3 | "Add medicine" bottom sheet | focus **Name**, type "Ibuprofen 400 mg" | `QuickAddMedicineSheet.kt:131-132` |
| — | same sheet | Dose amount "1", Unit blank and Time 08:00 left as they are | `QuickAddMedicineSheet.kt:138-148` |
| 4 | same sheet | **Save** | `ui/form/FormFrame.kt:135`; `QuickAddMedicineSheet.kt:131` |
| — | Save writes the medicine and its first reminder (Fixed time, 08:00, Daily) in one transaction (`QuickAddMedicineSheet.kt:162-190`). The sheet closes and the user **stays on the Medicines list**: no navigation happens unless "Save and add details" was used (`MedicinesScreen.kt:176`). | | |
| 5 | Medicines list | **Ibuprofen card** | `MedicinesScreen.kt:321-323`; `navigation/EmivitaNavHost.kt:147` |
| 6 | Medicine page | **+** FAB, "Add reminder" | `feature/medicines/MedicineDetailScreen.kt:266-268` |
| 7 | "Add reminder" type picker: 4 cards, Fixed Time, Repeating Interval, Daily Window, Linked Following | **Fixed Time** | `feature/reminders/AddReminderScreen.kt:84-89`, `:390-418` |
| 8 | "Fixed Time" form | focus **Time**, type "20:00" over "08:00" | `feature/reminders/DoseReminderForms.kt:235` |
| 9 | same form | **Save** | `DoseReminderForms.kt:215-233`; Save pops back to the medicine page (`AddReminderScreen.kt:138`, `navigation/EmivitaNavHost.kt:262`) |

**Minimum: 9 taps.** Typed fields: Name and Time.

The defaults that make 9 possible:
- the sheet's 08:00 time covers the first reminder;
- dose 1 and Daily are already set;
- the 20:00 reminder only needs its time changed.

**Variant with 400 mg stored as Strength.** The sheet has no Strength field; Strength exists only on the full form (`feature/medicines/AddMedicineScreen.kt:418-424`) and is shown under the name on cards (`MedicinesScreen.kt:553-555`). The path:
1. Name "Ibuprofen".
2. Tap **"Save and add details"** instead of Save (`QuickAddMedicineSheet.kt:150-154`). The app pushes the medicine page and then the Edit form over it (`navigation/EmivitaNavHost.kt:149-152`).
3. Focus **Strength**, type "400 mg", then **Save**. Saving pops back to the medicine page (`navigation/EmivitaNavHost.kt:271`), so the card tap is no longer needed.

This costs **10 taps** in total.

**Leaving the current screen.**
- The sheet can make only one reminder. The second one needs the medicine page, then the type picker, then the form: three screens.
- The type picker's top bar has no back arrow (`AddReminderScreen.kt:392`).
- Cancel on the form returns to the type picker, not to the medicine page (`AddReminderScreen.kt:140`).

**Dialogs.** None on the happy path. The "Discard changes?" question (strings:661) appears only on Cancel, Back or a scrim tap with edits (`ui/form/FormFrame.kt:127`, `:138-141`; `ui/components/UnsavedChangesGuard.kt:27-32`).

**Fields shown that this task does not need.**
- *Sheet:* Dose amount and Unit (`QuickAddMedicineSheet.kt:138-142`).
- *Fixed-time form:*
  - Dose amount (`DoseReminderForms.kt:237`).
  - The five-cell Repeats grid: Daily, Specific days, Every N days, Active/pause cycle, Days of the month (`:86-92`, `:243-245`).
  - The "Advanced" block's four rows, always shown: When I take it, Notification, If I don't answer, Duration (`feature/reminders/ReminderFormParts.kt:170-180`; `feature/reminders/ReminderGroupSheets.kt:37-42`; strings:762-765).

### 2b. Vitamin D, once a week on Monday at 09:00

The quick-add sheet only makes **daily** reminders: the sheet's own help line says "Repeats: Daily" (`QuickAddMedicineSheet.kt:146`), and the saved pattern is `RepeatPattern.Daily` (`:177`). A weekly reminder therefore needs a second screen. This assumes the user starts from the Ibuprofen page left by 2a.

| # | Screen | Tap | Code |
|---|---|---|---|
| 1 | Ibuprofen page | **Back** arrow, to the Medicines list | `feature/settings/SettingsDetailScreens.kt:98-102`; `navigation/EmivitaNavHost.kt:242` |
| 2 | Medicines | **+** FAB | `MedicinesScreen.kt:353` |
| 3 | Add-medicine sheet | focus **Name**, type "Vitamin D" | `QuickAddMedicineSheet.kt:132` |
| 4 | same sheet | **Save** (time left at 08:00, see the alternatives below) | `QuickAddMedicineSheet.kt:131` |
| 5 | Medicines list | **Vitamin D card** | `MedicinesScreen.kt:321-323` |
| 6 | Vitamin D page | **the reminder row** ("08:00 · Daily · 1"), which opens Edit | `MedicineDetailScreen.kt:253-262`, `:321-327` |
| 7 | "Edit Fixed Time" form | focus **Time**, type "09:00" | `DoseReminderForms.kt:235` |
| 8 | same form | **"Specific days"** cell. It replaces Daily in one tap (`DoseReminderForms.kt:107-112`) and opens the weekday grid with no day selected (`:180`, `:254-257`). | `DoseReminderForms.kt:243-245` |
| 9 | same form | **Mon** in the weekday grid | `ui/form/FormGrid.kt:135-145` |
| 10 | same form | **Save**. It is enabled only once a day is chosen (`DoseReminderForms.kt:206`, `:220`). A "Next: …" line previews the dates (`:139-156`, `:290`). | `DoseReminderForms.kt:223-232` |

**Minimum: 10 taps** (the same 10 when starting from Today, with the Medicines tab replacing Back). Typed fields: Name and Time.

The time can be typed in the sheet instead of the form, at the same total. Going through the type picker (+ FAB on the page, then Fixed Time) would also need the auto-created daily 08:00 reminder deleted, so it costs more.

Between steps 4 and 10 the medicine briefly has a live **daily 08:00** reminder, because the sheet's Save schedules it straight away.

**Leaving the current screen.** Weekly is impossible in the sheet, so the user goes sheet → list → medicine page → Edit reminder.

**Dialogs.** None.

**Fields shown that this task does not need.** Dose amount, Unit, the other four Repeats cells, and the four Advanced rows.

### 2c. Stock of 30 tablets for Ibuprofen

Stock is **not** on the quick-add sheet or on the full Add/Edit medicine form (`AddMedicineScreen.kt:402-477` has no stock field). It lives only on "Stock & Expiration", which is reached from the medicine page's **⋮ overflow menu** (`MedicineDetailScreen.kt:128-136`). The route's KDoc states this as a rule: one entry point, no duplicated fields (`navigation/EmivitaRoute.kt:35-38`; `feature/medicines/StockExpirationScreen.kt:70-83`).

| # | Screen | Tap | Code |
|---|---|---|---|
| 1 | Vitamin D page (or Today, via the **Medicines** tab) | **Back** | `navigation/EmivitaNavHost.kt:242` |
| 2 | Medicines | **Ibuprofen card** | `MedicinesScreen.kt:321-323` |
| 3 | Ibuprofen page | **⋮ More** | `MedicineDetailScreen.kt:129-131` (strings:134) |
| 4 | Overflow menu | **"Stock & Expiration"** | `MedicineDetailScreen.kt:133-136` (strings:96) |
| 5 | "Stock & Expiration" form | focus **Quantity**, type "30". The field is pre-filled "0" (`StockExpirationScreen.kt:143`, `:298-302`). | `StockExpirationScreen.kt:300` |
| 6 | same form | focus **"Unit (e.g. tablet, mL)"**, type "tablet" | `StockExpirationScreen.kt:301`; strings:179 |
| 7 | same form | **Save**, which saves and goes back to the medicine page | `StockExpirationScreen.kt:192-207`, `:275`, `:281-288` |

**Minimum: 7 taps.** Typed fields: Quantity and Unit.

**The Unit field is required in practice.**
- Stock is tracked only when a stock unit is saved. Take does not decrement an untracked medicine (`core/data/repository/DoseEventRepository.kt:514-527`), and "Refill now" stays disabled (`StockExpirationScreen.kt:323-325`, `:339-345`, strings:475).
- The quick-add and full-form "Unit" is a different field (`Medicine.doseUnit`). It does **not** pre-fill the stock unit (`StockExpirationScreen.kt:144`).

**Defaults.** None help here: Quantity starts at 0, Unit is blank, and the alert sections are off.

**Dialogs.** None.

**Fields shown that this task does not need** (the form scrolls; `StockExpirationScreen.kt:294-411`):
- Refill amount (with its run-out help line), "Add to calendar" (shown once a run-out date exists) and "Refill now (+N)".
- The "Low-stock alert" section: the "Notify when stock runs low" switch, plus Threshold and Notify Once/Daily/Always when it is on.
- The "Expiration" section: the "Notify as expiration nears" switch, Production date, Expiration date (month/year), the Exact day switch, Opened on and "Use within this many days of opening". Lead days and Notify Once/Daily appear when its switch is on.

### Totals for the whole set-up (from a usable Today)

**9 + 10 + 7 = 26 taps.** That is 6 field focuses and 20 other taps, across 3 bottom sheets or forms and 4 screen types: list, medicine page, reminder form, stock form.

- Four of the taps (Back or card, ⋮, menu item) only move between screens.
- On Today, after the first medicine, a "No backup yet / Back up now" card appears (`feature/today/TodayScreen.kt:1182-1183`, strings:719-721).

---

## 3. Settings map

"Depth" = taps from Today to reach the control. Labels are the English strings. A sheet is a bottom sheet; a page is a pushed route.

### 3a. Global settings (Settings tab)

**Settings tab** (depth 1), title "Settings" (strings:170). It is a list of groups, each row with a description line (`feature/settings/SettingsScreen.kt:94-180`, `:211-276`). Rows respond in one of three ways: open a page, open a sheet, or toggle in place (`:76`, `:237-266`).

**Group "Reminders & Alarms"** (strings:304)

- **Notifications & Alerts** (page, depth 2). Description: "Sound, priority, how reminders behave" (strings:305-306). Opened at `navigation/EmivitaNavHost.kt:175`; built at `feature/settings/SettingsDetailScreens.kt:300-368`.
  - Three tier descriptions, Standard / High / Alarm. They are **read-only text**, with no control (`:324-326`, `:526-532`).
  - "What Android allows": four permission rows with **Fix** (depth 3) (`:534-541`; strings:676, 683).
  - Conditional cards with buttons (depth 3):
    - "Notifications blocked" with "Open notification settings" (`:484-524`);
    - "Alarm reminders can't take over the screen" with "Allow full-screen alarms" (`feature/settings/AppearanceSettingsScreens.kt:217-243`);
    - "Reminders can arrive late" with "Allow" or "Open battery settings", plus "Open auto-start settings" on some OEMs (`SettingsDetailScreens.kt:382-473`).
  - **Lock reminders until taken by default**, a toggle (depth 3) (`:337-355`; strings:480-481).
  - **Open system notification settings**, a button that leaves the app (depth 3) (`:364-366`; strings:155).
- **Alarms & Snooze** (sheet, depth 2). Description: "Alarm sound, vibration, snooze length" (strings:307-308). Built at `SettingsDetailScreens.kt:153-208`; hosted at `SettingsScreen.kt:278-311`.
  - **Snooze** row, summary "10 min, fixed length" (depth 3). It opens a sheet-in-sheet (`:221-235`, `:185-188`) with:
    - **Snooze duration (minutes)**, default 10 (depth 4) (`core/scheduling/AlarmScheduler.kt:26`);
    - **Ask how long each time** (depth 4).
  - **When I swipe a reminder away** row (depth 3). Its sheet has radios (depth 4): "Nothing — it stays due on Today", "Snooze it" (the default, `SettingsRepository.kt:309-310`), "Record it as skipped" (`SettingsDetailScreens.kt:237-245`; strings:525-529).
  - **Alarm tier** row (depth 3). Its sheet holds (`:247-283`; strings:534-544):
    - **Alarm sound**, which opens the system ringtone picker (depth 4);
    - **No sound when the phone is silent** (depth 4);
    - **No vibration when the phone is silent** (depth 4);
    - **Show the next reminder as an alarm** (depth 4).
  - **Send a test reminder**, a button (depth 3) (`:193`; strings:545).
  - **Every inner change needs two Saves**, the inner sheet's and then the outer sheet's. The inner Save only edits a draft (`:202-204`); the outer Save writes it (`:171-174`). Changing the snooze length therefore costs Settings, Alarms & Snooze, Snooze, field, Save, Save = **6 taps**.
- **Weekend Mode** (sheet, depth 2). Description: "Delay reminders on chosen days" (strings:309-310). Built at `feature/settings/AppearanceSettingsScreens.kt:110-210`. The sheet holds:
  - **Delay reminders on chosen days**, a switch (depth 3);
  - **Days**, a weekday grid (depth 3);
  - **Window start** / **Window end** (depth 3);
  - Save (depth 4).
  - The global switch is not enough: each reminder must also opt in with "Follow weekend mode" (see 3b) (`SettingsRepository.kt:225-231`).

**Group "Appearance"** (strings:311)

- **Appearance** (page, depth 2). Description: "Theme, palette, display preferences" (strings:312-313). Built at `AppearanceSettingsScreens.kt:255-365`. Everything applies on tap; there is no Save (`:276-279`).
  - **Theme**, a collapsible section (depth 3 to expand) (`:282-313`):
    - **Theme mode** chips Light/Dark/System/AMOLED (depth 4) (`ui/theme/Theme.kt:42`);
    - **Palette**, four cards (depth 4) (`ui/theme/Theme.kt:24-29`).
  - **Today layout**, four cards (depth 3): Timeline, Grouped, Compact, Hero (`AppearanceSettingsScreens.kt:316-325`; `SettingsRepository.kt:477`).
  - **Open on**, chips Today/Medicines/History (depth 3). It takes effect from the next launch (`:327-346`; strings:685-686).
  - **Language**, with **Change**, which opens a list dialog (depth 3 to open, depth 4 to pick) (`:390-447`). Shipped languages: en and it (`core/locale/AppLocale.kt:34`).
  - **Time format**, radios Follow the phone / 12-hour / 24-hour (depth 3) (`:413-435`; strings:656-659).

**Group "Privacy & Security"** (strings:314). All three rows are inline toggles (`SettingsScreen.kt:240-266`, `:342-374`).

- **Biometric lock** (depth 2). Description: "Require fingerprint, face, or the phone's PIN to open Emivita". It is disabled, with an explanation, when the device cannot authenticate (`SettingsScreen.kt:252-266`; strings:315-318, 660).
- **Hide medicine names in notifications and widgets** (depth 2) (strings:530-531).
- **Block screenshots and the Recents preview** (depth 2), off by default (strings:532-533).

**Group "Data & Backup"** (strings:319)

- **Data & Backup** (page, depth 2). Description: "Auto-backup, restore, PDF/CSV export" (strings:320-321). Built at `feature/settings/BackupSyncScreen.kt:91-376`.
  - A warning about the key (`:264`; strings:722).
  - **Manual backup**: the **Password-protect this backup** checkbox (depth 3), **Password** (depth 4) and **Export backup** (depth 3-5), which opens the system file picker and creates a `.zip` (`:119`, `:269-300`). Exporting without a password asks first (`:411-414`).
  - **Restore from backup**: **Choose backup file**, which opens the system file picker (depth 3). A confirmation follows with an "also restore settings" checkbox (`:307-315`, `:381-400`), plus a password dialog for encrypted files (`:636-653`).
  - **Auto-sync folder**: **Choose sync folder**, which opens the folder picker, then the "Set a sync password" dialog (depth 3, then 4-5); **Stop syncing** (depth 3) (`:326-355`, `:445-458`).
  - **Delete everything** (depth 3), with a confirmation checkbox (`:535-546`, `:660-679`).
  - **Set-aside database files**, only after a recovery (`:579-616`).
  - **Archived medicines**: **Restore** or **Delete for good** per medicine (depth 3; delete for good asks first) (`:484-531`; strings:606-612).
  - The "PDF/CSV export" named in the row's description is **not on this page**; it is on History (see section 6).
- **Medical ID** (page, depth 2). Description: "Blood type, allergies, doctor and pharmacy – printable card" (strings:322-323). Built at `feature/settings/MedicalIdScreen.kt:54-216`.
  - Fields (depth 3): Name, Blood type, Allergies, Doctor name/phone/address, Pharmacy name/phone (`:184-199`).
  - **Export card (PDF)** (depth 3) (`:116`, `:210`).
  - Save.

### 3b. Per-medicine and per-reminder settings

These are **not in Settings**. They live on the medicine page and its forms. The route is the **Medicines** tab (1), then the medicine card (2).

**Medicine page** (depth 2)

- **Reminder row Enabled switch**, which pauses or resumes one reminder (depth 3) (`MedicineDetailScreen.kt:347-353`).
- **Notes journal**: an add field and **Add** (depth 3) (`:364-419`).
- **⋮ More** (depth 3), a menu (`:128-171`) with:
  - **Stock & Expiration**, a page (depth 4; fields at depth 5, listed in 2c);
  - **Duplicate**;
  - **Duplicate with reminders**;
  - **Pause all reminders** / **Resume all reminders**;
  - **Delete** (strings:489-492, 132).
- **Edit medicine** pencil (depth 3), the full form (`AddMedicineScreen.kt:143-247`, `:402-477`), with these fields at depth 4:
  - **Name**;
  - **Unit** ("e.g. tablets, drops");
  - **Strength** ("e.g. 50 mg");
  - **Appearance**: an icon grid plus a colour swatch, which opens a colour dialog;
  - **Tags**: an add field plus chips;
  - **As needed** switch ("Stays on Today with its Log button");
  - **Dose limit**: Max doses / Per hours;
  - **Delete**, which archives;
  - **Advanced › Notification** (sheet, depth 4). It holds **Notification priority** Standard / High / Alarm, and **Lock until taken** Use global default / Always lock / Never lock (depth 5). That is sheet Save (6) and form Save (7) (`feature/medicines/MedicineGroupSheets.kt:75-94`; strings:462, 19, 24, 17, 21).
  - **Advanced › Details** (sheet, depth 4). It holds **Barcode** (with a scan icon), **What it's for**, **Storage**, **Prescription number** and **Notes** (depth 5) (`MedicineGroupSheets.kt:103-143`).
  - **This is the only place a medicine's notification tier (Standard/High/Alarm) is chosen.**

**Reminder form** (tap a reminder row on the medicine page, depth 3) (`feature/reminders/AddReminderScreen.kt:235-386`)

- **Time**, **Dose amount**, and **Repeats** (Daily / Specific days / Every N days / Active/pause cycle / Days of the month) with its sub-fields (depth 4) (`DoseReminderForms.kt:235-290`).
- Interval and window reminders have their own fields (`DoseReminderForms.kt:295-342`).
- **Delete reminder** at the end of the form (depth 4) (`ui/form/FormFrame.kt:106-111`).
- **Advanced**: four rows (depth 4), each opening a sheet with fields at depth 5. Changes need the sheet's Save (6) and the form's Save (7) (`feature/reminders/ReminderGroupSheets.kt:87-106`).
  - *When I take it:* **Ask the amount when taking**; **Instructions**, plus sample chips (`:114-132`; strings:742, 573).
  - *Notification:* **Follow weekend mode**; **Auto-resolve**; **Notification importance**, which lists only tiers above the medicine's own (`:147-170`; strings:36, 31, 798-799).
  - *If I don't answer:* **Repeat until answered** (default on) with **Every (min)** and **Max repeats**; **Stay until answered**; **Heads-up before the dose** with **Minutes before** (`:176-191`; strings:45, 33, 40, 740, 549-551).
  - *Duration:* **From** / **Until** dates; **A set number of doses** with **Doses remaining** (`:193-215`; strings:38, 51, 575-577).

**Medicines list itself** (depth 1), header actions at depth 2 (`MedicinesScreen.kt:213-271`):
- **Search medicines**;
- **Sort**: Manual order / Name / Next dose / Expiry and stock / Group by tag;
- **Scan to find**, through an external zxing scanner app;
- **⋮**, with **Pause all reminders**;
- tag **filter chips** and a **Hide empty** chip.

### 3c. What makes reaching settings fast or slow

Fast:
- **One tap to Settings.** Settings is a permanent bottom-bar tab (`navigation/EmivitaNavHost.kt:84-110`).
- **Toggles cost 2 taps.** The three privacy switches toggle on the row itself (`SettingsScreen.kt:240-266`).
- **Short forms are sheets.** Alarms & Snooze and Weekend Mode open as sheets rather than pushed pages (`SettingsScreen.kt:182-193`).
- **Rows explain themselves.** Every row carries a one-line description of what is inside (strings:305-323).
- **Appearance applies on tap**, with no Save (`AppearanceSettingsScreens.kt:276-279`).
- **Problems link to their fix.** Missing permissions and missed alarms surface on Today with a button to the fix (`TodayScreen.kt:1178-1180`; the missed-alarm card opens Notifications & Alerts, `navigation/EmivitaNavHost.kt:131`).

Slow:
- **Double Save.** Alarms & Snooze nests sheets two deep and needs an inner and an outer Save, so 5-6 taps for one value (`SettingsDetailScreens.kt:171-174`, `:200-206`). The same sheet-plus-form double Save applies to every medicine and reminder Advanced group (`ReminderGroupSheets.kt:96`; `MedicineGroupSheets.kt:81`).
- **Settings split by object.** The notification tier, lock, instructions, nag, weekend opt-in and course sit on each medicine or reminder, 5-7 taps deep, and not in Settings. "Notifications & Alerts" only *describes* the tiers (`SettingsDetailScreens.kt:324-326`).
- **Two-place weekend mode.** It needs the global sheet *and* a per-reminder opt-in (`SettingsRepository.kt:225-231`).
- **Stock behind ⋮.** Stock & Expiration is only in the medicine page's overflow menu (`MedicineDetailScreen.kt:133-136`).
- **Exports are not under Data & Backup.** History CSV/PDF export is on the History tab, although the Data & Backup row's description promises it (strings:321 vs `feature/history/HistoryScreen.kt:205-231`, `:248`).
- **Language is hard to find.** It is near the bottom of the Appearance page (`AppearanceSettingsScreens.kt:350-360`).
- **No settings search.**

---

## 4. Daily actions

### From the notification (Standard/High tier)

The actions are built at `core/notification/ReminderNotificationBuilder.kt:75-160`:
- **Take** is always present (`:133`).
- **Snooze** is present **only when swipe is not set to Snooze** (`:134`). Swipe defaults to Snooze (`SettingsRepository.kt:309-310`), so on a default install there is no Snooze button and swiping the notification away *is* the snooze (delete intent `:131`).
- **Skip** is present unless the medicine is locked until taken, or swipe is set to Skip (`:143-145`).

Tap counts:
- **Taken: 1 tap.** On a variable-amount reminder, Take opens `TakeAmountActivity` instead (`:85`).
- **Skipped: 1 tap.**
- **Snooze: 1 swipe gesture** (default), or 1 tap when the button is shown. With "Ask how long each time" it is 2 taps, through a picker offering 5/15/30/60 min or Other (`:104-106`; `feature/alarm/SnoozePickerActivity.kt:53`).

Whether the actions show without expanding the notification first depends on the launcher *(unverified — needs device)*.

### Alarm tier

A full-screen `AlarmActivity` shows **Take**, **Skip** and **Snooze N min**, 1 tap each (`feature/alarm/AlarmActivity.kt:147-189`). It rings until answered (`ReminderNotificationBuilder.kt:146-157`).

### Take all due together

- **Notification:** with two or more reminders showing, the group summary carries **Take all**, 1 tap. Doses that ask for an amount are left out (`ReminderNotificationBuilder.kt:166-223`).
- **App:** a **"Take all (N)"** chip appears on a Today time slot with two or more due doses (1), then a confirmation sheet button (2) (`feature/today/TodayScreen.kt:584-614`; `feature/today/TodaySheets.kt:44-75`).

### From the app (Today)

- **Due dose:** **Take** chip (1) or **Skip** chip (1). Skip is omitted when the medicine is locked (`TodayScreen.kt:821-843`).
- **Upcoming dose:** a **Take now** chip (1) (`:845-856`). Tapping the row opens a detail sheet with Take now / Skip (2) (`:676-698`; `TodaySheets.kt:87-120`).
- **No snooze in the app.** Nothing in `feature/today` offers it.

### Extra or as-needed dose

- **Log dose** FAB (1) opens a dialog (`TodayScreen.kt:409-419`, `:880-1100`).
  - The first medicine is preselected; another is picked through a dropdown (+2).
  - Dose amount is pre-filled with the last-used amount.
  - A "logged earlier" switch reveals Day and Time fields.
  - **Confirm** (2).
  - Typing a name that is not on the list logs a one-off dose (`:918-919`, `:943-946`).
- An **as-needed** medicine has its own row on Today with **Log** (1), which opens the dialog preselected, then Confirm (2) (`:357-372`, `:442-472`).
- **Warnings** may ask first: a second-dose check when a reminder slot is still open (`:955-1010`), and an "Over the as-needed limit" warning (`:1012-1025`; strings:604).

### Undo a wrong answer

- **Immediately:**
  - In the app, an **Undo** snackbar (1). It shows for 5 s plus 1 s (`TodayScreen.kt:281-301`; `UNDO_WINDOW_MS` = 5000 at `core/notification/ReminderNotificationBuilder.kt:44`).
  - From the notification, a silent "answered" notification with **Undo** (1) for 5 s (`ReminderNotificationBuilder.kt:249-276`).
- **Later:** tap the answered row on Today (1). This opens the "Edit dose" dialog (`TodayScreen.kt:676-686`, `:700-721`; `ui/components/DoseEditDialog.kt:52-240`), with these options:
  - **Status**: Taken / Skipped radio (2), then **Save** (3);
  - **Return to due** (2), which also re-posts the reminder;
  - **Delete this record**, which asks first (strings:569-572).

### Edit a past dose

- **History** tab (1), then tap an entry (2) in the List, Calendar (day list) or Table view (`feature/history/HistoryScreen.kt:348`, `:365`, `:380`).
- The same "Edit dose" dialog opens (`:386-395`): Status, Recorded time, Dose amount, Note; **Save** (3 or more).
- A dose that was never answered can also be **moved**: "Move and remind me then" (`DoseEditDialog.kt:120-144`).
- In the Calendar view a day may need tapping first, so 3 taps to reach the entry *(unverified — needs device)*.

---

## 5. Later maintenance

All routes start from Today.

| Task | Path | Taps | Code |
|---|---|---|---|
| Pause one reminder | Medicines → card → reminder row's switch | 3 | `MedicineDetailScreen.kt:347-353` |
| Pause all of one medicine | Medicines → card → ⋮ → Pause all reminders | 4 | `MedicineDetailScreen.kt:156-165` |
| Pause every medicine | Medicines → ⋮ → Pause all reminders. Afterwards a line "Every reminder is paused · Resume" restores only what pause-all switched off. | 3 | `MedicineListActions.kt:259-325` |
| Delete a reminder | Medicines → card → reminder row → Delete reminder (end of form) → Confirm | 5 | `AddReminderScreen.kt:265-283`; `FormFrame.kt:106-111` |
| Delete (archive) a medicine: swipe | Medicines → swipe card end-to-start → Confirm | 3 (1 gesture) | `MedicineListActions.kt:332-365`; `MedicinesScreen.kt:300`, `:320` |
| Delete (archive) a medicine: menu | Medicines → card → ⋮ → Delete → Confirm | 5 | `MedicineDetailScreen.kt:166-169`, `:271-295` |
| Delete for good, or restore | Settings → Data & Backup → Delete for good → Confirm (or Restore) | 4 (3) | `BackupSyncScreen.kt:484-531` |
| Change a time | Medicines → card → reminder row → Time (type) → Save | 5 | `DoseReminderForms.kt:235` |
| Refill stock | Medicines → card → ⋮ → Stock & Expiration → Refill amount (type, kept for next time) → Refill now (+N) → Back | 7, or 6 once the refill amount is saved | `StockExpirationScreen.kt:305-338`, `:146` |

Notes on these rows:
- Delete on a medicine **archives** it: the record stays and its reminders pause (`core/data/repository/MedicineRepository.kt:155-173`; strings:137).
- The swipe-to-delete control is accessible: TalkBack reaches it through a custom "Delete" action (`MedicineListActions.kt:343`).
- **Refill now writes straight away**, and is disabled until a stock unit is saved (`StockExpirationScreen.kt:323-338`).

---

## 6. Feature inventory

| Feature | Where | One line | Citation |
|---|---|---|---|
| Today timeline | Today tab | Doses by time with Take/Skip/Take now chips; 4 layouts | `feature/today/TodayScreen.kt:211`, `:552-579`; `SettingsRepository.kt:477` |
| Progress line | Today | Day's progress at the top of the list | `TodayScreen.kt:331` (`feature/today/TodayProgress.kt`) |
| History list + adherence chart | History › List | Entries plus a per-day adherence chart (Vico), pattern-filled for colour-blind reading | `feature/history/HistoryScreen.kt:124-129`, `:337`; `feature/history/AdherenceChart.kt:93-98`, `:199` |
| History calendar | History › Calendar | Month grid; tap a day to see its entries | `feature/history/HistoryCalendarView.kt:90-192`; strings:113 |
| History table | History › Table | Sortable date/medicine/status/dose table | `feature/history/HistoryTableView.kt`; strings:211-215 |
| Per-medicine figures | Medicine page | "N of M taken", last taken, a one-row day strip | `feature/medicines/MedicineFigures.kt:65-71` |
| History export | History › Export icon | CSV or PDF, with range (30/90 days, year, everything) and medicine filter | `HistoryScreen.kt:205-231`, `:248`, `:620`; `core/data/export/HistoryExporter.kt:32`, `:73` |
| Stock tracking | Medicine › ⋮ › Stock & Expiration | Quantity plus unit, auto-decrement on Take, run-out date, one-tap refill | `StockExpirationScreen.kt:294-346`; `core/data/dao/MedicineDao.kt:95-103` |
| Low-stock alert | same screen | Threshold; notify Once/Daily/Always | `StockExpirationScreen.kt:348-367` |
| Expiry | same screen | Month/year or exact day, opened-on plus use-within, lead-days alert | `StockExpirationScreen.kt:369-410`; `core/data/entity/StockInfo.kt` |
| Run-out to calendar | same screen | Calendar app insert intent; no calendar permission | `StockExpirationScreen.kt:310-320`, `:430-437` |
| Stock attention badge | Bottom bar | Medicines tab shows the count of empty/low/expired medicines | `navigation/EmivitaNavHost.kt:73-78`, `:99-101` |
| Tags + filters | Edit medicine; Medicines list | Tags per medicine; filter chips; group-by-tag sort; hide-empty chip | `AddMedicineScreen.kt:441-451`; `MedicinesScreen.kt:257-271`, `:282-306` |
| Search / sort / reorder | Medicines list | Search; 5 sorts; drag handle with accessibility move actions | `MedicinesScreen.kt:217-236`, `:459-506` |
| Barcode | Medicines list; Details sheet | Scan-to-find and a barcode field, through an external zxing scanner app | `MedicinesScreen.kt:237-253`; `MedicineGroupSheets.kt:110-133` |
| Reminder kinds | Add reminder | Fixed time, repeating interval, daily window, linked follow-up; low-stock and expiry alerts | `AddReminderScreen.kt:84-89`, `:121-223` |
| Repeat rules | Fixed-time form | Daily, weekdays, every N days, active/pause cycle, days of month; rules combine | `DoseReminderForms.kt:86-129` |
| Nag / sticky / pre-alarm / course | Reminder › Advanced | Repeat until answered, stay until answered, heads-up, N doses then stop | `ReminderGroupSheets.kt:176-215` |
| As-needed + limit | Edit medicine; Today | As-needed row with Log; max doses per hours with warning | `AddMedicineScreen.kt:455-470`; `TodayScreen.kt:357-372`, `:1012-1025` |
| One-off dose | Today › Log dose | Log a name not on the list | `TodayScreen.kt:918-946` |
| Notes journal | Medicine page | Dated notes, editable | `MedicineDetailScreen.kt:358-440` |
| Duplicate medicine | Medicine › ⋮ | With or without reminders | `MedicineDetailScreen.kt:138-155` |
| Archive / restore | Swipe or Delete; Data & Backup | Soft delete; restore or delete for good | `MedicineRepository.kt:155-173`; `BackupSyncScreen.kt:484-531` |
| Notification tiers | Edit medicine › Notification | Standard / High / Alarm (full-screen, rings until answered) | `MedicineGroupSheets.kt:75-94`; `ReminderNotificationBuilder.kt:146-157` |
| Lock until taken | Settings default + per medicine | Removes Skip | `SettingsDetailScreens.kt:337-355`; `ReminderNotificationBuilder.kt:143` |
| Weekend mode | Settings sheet + per reminder | Delays reminders in a window on chosen days | `AppearanceSettingsScreens.kt:110-210`; `ReminderGroupSheets.kt:150` |
| Undo | Today snackbar; notification | 5 s undo of Take/Skip | `TodayScreen.kt:281-301`; `ReminderNotificationBuilder.kt:249-276` |
| Backup (manual) | Data & Backup | JSON in a zip, optional AES-256 password | `BackupSyncScreen.kt:119`; `core/data/backup/BackupZipWriter.kt:24-44`; `core/data/backup/BackupData.kt:10-11` |
| Auto-sync + weekly copies | Data & Backup | Password zip rewritten after every change into a chosen folder; dated weekly copies, newest 4 kept | `core/data/backup/AutoSyncManager.kt:25-38`; `core/data/backup/WeeklyBackups.kt:4-13`; `core/data/backup/WeeklyBackupAlarm.kt:15-20` |
| Restore | Data & Backup | From a zip, optionally including settings | `BackupSyncScreen.kt:233`, `:381-400` |
| Delete everything | Data & Backup | Purge with a confirmation checkbox | `BackupSyncScreen.kt:535-546`, `:660-679` |
| Medical ID card | Settings › Medical ID | Profile medical info, PDF emergency card | `MedicalIdScreen.kt:54-216`; `core/data/export/EmergencyCardExporter.kt:154` |
| App lock | Settings toggle | Biometric or device credential; re-locks on background; fails open with no enrolment | `feature/lock/AppLockGate.kt:113-196` |
| Hide names | Settings toggle | Initial only in notifications and widgets | `core/format/DoseFormatting.kt:88`; strings:530-531 |
| Screenshot block | Settings toggle | FLAG_SECURE-style blank in screenshots and Recents | `core/security/ScreenshotBlock.kt:15`; `MainActivity.kt:32` |
| Encryption at rest | (none) | SQLCipher database, key held by Android | `core/data/db/EmivitaDatabase.kt:24`, `:71`; `core/data/db/PassphraseProvider.kt` |
| Recovery | Start-up dialog; Data & Backup | Corrupt or key-lost DB set aside, user told, restore offered | `navigation/EmivitaApp.kt:101-116`; `feature/recovery/DatabaseRecoveredDialog.kt:24`; `BackupSyncScreen.kt:579-616` |
| No-backup nudge | Today card | "No backup yet · Back up now" | `TodayScreen.kt:1182-1196` |
| Permission health | First-run sheet; Today card; Notifications & Alerts | Four grants with fix buttons; missed-alarm watchdog | `feature/permissions/ReminderPermissions.kt:228-267`; `core/scheduling/AlarmWatchdog.kt:24` |
| Widgets | Home screen | "Next doses" and "Stock levels" (Glance) | `feature/widget/NextRemindersWidget.kt:84`; `feature/widget/StockLevelsWidget.kt:61` |
| Wear / watch | (none) | No Wear module or manifest entries in the app (grep for "wear" finds nothing in `app/`) | `app/src/main/AndroidManifest.xml` |
| Profiles | (none visible) | One auto-created profile; no switcher | `navigation/EmivitaApp.kt:36-49`, `:81` |
| Languages | Appearance › Language | English, Italian; per-app locale | `core/locale/AppLocale.kt:34-36` |
| Theming | Appearance | 4 modes (incl. AMOLED), 4 palettes, 4 Today layouts, start page, time format | `ui/theme/Theme.kt:24-42`; `AppearanceSettingsScreens.kt:255-365` |
| Accessibility | Throughout | One merged node per control, state descriptions, custom actions (reorder, delete), font-scale capped nav labels, attention glyph alongside colour | `MedicinesScreen.kt:482-488`; `MedicineListActions.kt:343`; `navigation/EmivitaNavHost.kt:296-306`; `TodayScreen.kt:741-766` |
| Offline only | Manifest | No INTERNET permission; Android auto-backup off | manifest:55, 85, 121 |
| Disclaimer | First run | Mandatory, checkbox-gated | `feature/disclaimer/DisclaimerDialog.kt:36-58` |

---

## 7. Notable code-level qualities (for a Kotlin Multiplatform port, Android plus Windows desktop)

### Test count

`@Test` annotations, counted with grep:
- **841** in `app/src/test`, across 161 files;
- **10** in `app/src/androidTest`, across 7 files.

106 of the 161 unit-test files run under Robolectric (they reference `RobolectricTestRunner` / `AndroidJUnit4`), so most of the suite is Android-bound JVM tests, not pure JVM tests.

Largest packages:

| Package | Tests | Notes |
|---|---|---|
| `test/core/scheduling` | 119 | plus 42 in `scheduling/medtimer`: parity tests against MedTimer's calculator, engine and simulator |
| `test/feature/medicines` | 104 | |
| `test/core/scheduling/receivers` | 67 | |
| `test/feature/today` | 63 | |
| `test/core/data/repository` | 49 | |
| `test/core/data/backup` | 29 | |

### Cleanly separable and platform-neutral already

These files import no `android.*` or `androidx.*`:

| File | Lines | What it holds |
|---|---|---|
| `core/data/config/ReminderConfig.kt` | 169 | Reminder config model plus day-rule maths (`holdsOn`, `nextDates`, `isActiveOn`, at `:65-84`) |
| `core/data/config/ReminderConfigCodec.kt` | 19 | Config codec, kotlinx.serialization |
| `core/scheduling/NextOccurrence.kt` | 18 | |
| `core/scheduling/SchedulingEngine.kt` | 320 | The scheduler |
| `core/scheduling/LowStockExpirationAlertEvaluator.kt` | 91 | Stock and expiry alert rules |
| `core/data/entity/StockInfo.kt` | 31 | Stock arithmetic; effective-expiry rule |
| `core/data/backup/BackupData.kt` | 108 | Backup format: JSON through kotlinx.serialization, `decodeBackup` at `:51` |
| `core/data/backup/WeeklyBackups.kt` | 32 | Weekly-copy rules, pure and JVM-tested |
| `core/data/backup/BackupZipWriter.kt` | 75 | AES zip writer (zip4j, a JVM library; desktop-usable) |

Coupling and caveats:
- `SchedulingEngine` is pure Kotlin plus `java.time`, but its constructor takes `DoseEventDao`, `MedicineDao` and `SettingsRepository` (`core/scheduling/SchedulingEngine.kt:27-38`), so it is coupled to Room DAO interfaces and DataStore. A KMP port must abstract those, or move to Room-KMP and DataStore-KMP.
- `java.time` usage throughout would need `kotlinx-datetime` in common code, or remain on a JVM-only "desktop + Android" source set. Windows desktop on the JVM can keep `java.time`.
- `OccurrenceProjectionCache` (317 lines) and `MissedDoseBackfiller` import only `android.util.Log` (`core/scheduling/OccurrenceProjectionCache.kt`, `core/scheduling/MissedDoseBackfiller.kt`). They are nearly neutral, with run-out dates computed at `:200-219`, `:297`.
- **Stock arithmetic is split.** Decrement and increment are SQL in `MedicineDao` (`core/data/dao/MedicineDao.kt:95-103`). The rules (only when a unit is set, refill gating) are in `DoseEventRepository` (`core/data/repository/DoseEventRepository.kt:478-547`), which depends on Room's `withTransaction`.
- `BackupRepository` (108 lines) depends on `EmivitaDatabase` and Room's `withTransaction` (`core/data/backup/BackupRepository.kt:22-40`).

### Tied to Android APIs

| Area | Android APIs | Where |
|---|---|---|
| Alarms | `AlarmManager`, `PendingIntent` | `core/scheduling/AlarmScheduler.kt` (234 lines) |
| Receivers | Boot, time change, actions | `core/scheduling/*Receiver.kt` |
| Watchdog | | `core/scheduling/AlarmWatchdog.kt` |
| Notifications | `NotificationCompat`, channels | `core/notification/*` |
| Ringing | `AlarmRingService`, `AlarmActivity` | |
| Persistence | Room entities and DAOs; SQLCipher `SupportOpenHelperFactory` | `core/data/db/EmivitaDatabase.kt:24`, `:71` |
| Settings store | DataStore Preferences, with an Android `Context` | `core/data/settings/SettingsRepository.kt` |
| PDF exports | `android.graphics.pdf.PdfDocument` | `core/data/export/HistoryExporter.kt`; `EmergencyCardExporter.kt` |
| File access | SAF pickers | Backup, exports, Medical ID |
| Permissions | | `feature/permissions/ReminderPermissions.kt` |
| Lock | BiometricPrompt | `core/security/BiometricAuthenticator.kt` |
| Widgets | Glance | `feature/widget/*` |
| Barcode / calendar | zxing scanner intent and calendar intents | |
| OEM auto-start | | `core/platform/AutoStartSettings.kt:46` |

### UI

The UI is Jetpack Compose with Material 3: about 12.7k lines in `feature/`, plus `ui/form` and `ui/components`.

Mostly portable to Compose Multiplatform:
- The composables use standard M3 (sheets, `TimePicker`, `DatePicker`, `FilterChip`).
- The form kit is self-contained: `ui/form/FormFrame.kt`, `FormFields.kt`, `FormGrid.kt`, `DialogActions.kt`.

Android-only pieces mixed into screens:
- `LocalContext`, Activity result launchers and `startActivity` intents;
- `R.string` resources, which would need Compose Multiplatform resources;
- Navigation-Compose type-safe routes (`navigation/EmivitaRoute.kt`). A multiplatform version of Navigation-Compose exists, but the routes as written are Android code.

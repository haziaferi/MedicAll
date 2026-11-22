# MedTimer v1.26.5: user-facing flows, mapped from source

Source: a local copy of MedTimer 1.26.5 (read only; nothing was built or run).
Every path below comes from reading the source. Anything that depends on how a third-party widget behaves on a real device is marked **(unverified — needs device)**.

Short prefixes used in citations:

| Prefix | Expands to |
|---|---|
| `app/` | `app/src/main/java/com/futsch1/medtimer/` |
| `fui/` | `feature/ui/src/main/java/com/futsch1/medtimer/feature/ui/` |
| `frem/` | `feature/reminders/src/main/java/com/futsch1/medtimer/feature/reminders/` |
| `res/` | `feature/ui/src/main/res/` |
| `str` | `core/ui/src/main/res/values/strings.xml` |
| `arr` | `core/ui/src/main/res/values/arrays.xml` |
| `dom/` | `core/domain/src/main/java/com/futsch1/medtimer/core/domain/` |

How taps are counted (as the brief sets out): each click, toggle, selection, picker confirm, navigation back or Save counts as 1 tap. Entering text counts as 1 tap to focus the field, and the typing itself is not counted. Where the app focuses a field automatically, the rule still counts the focus tap, and the note "(auto-focus)" marks where the real count is one lower.

---

## 1. First run

Order of events on a fresh install of a release build:

1. **`MainActivity.onCreate`** applies the theme and screen-capture flag, then calls `showIntro()` (`app/MainActivity.kt:98-120`). If `introShown` is false and the build is not a debug build, it starts `MedTimerAppIntro` and sets `introShown = true` straight away (`app/MainActivity.kt:122-127`). This happens before the user finishes the intro, so killing the app mid-intro skips it next time.
2. **Intro (AppIntro 6.3.1, `gradle/libs.versions.toml:29`).** It has four slides, all text plus an image (`app/MedTimerAppIntro.kt:44-66`):
   1. "Welcome to MedTimer!": the app is free, open source and private, and needs notification permission (`str:192-193`).
   2. "Set up your first reminder": "Add a medicine first, then add a reminder for it…" plus a "Getting started" link that opens the offline help reader (`str:194-196`; link wiring `app/MedTimerAppIntro.kt:139-171`).
   3. "Overview": explains the week row and the state icon (`str:89,197`).
   4. "Notification permission" (`str:198-199`).
   - On Android 13 and later, `askForPermissions(POST_NOTIFICATIONS, slide 1, required = true)` (`app/MedTimerAppIntro.kt:68-74`) shows the system notification prompt when the user leaves slide 1. Whether `required = true` blocks moving on after a denial, or only asks again, is AppIntro library behaviour **(unverified — needs device)**.
   - The system back button is locked (`app/MedTimerAppIntro.kt:75`). Skip and Next/Done buttons are shown (`:76`).
   - **Skip** requests the notification permission (if not already granted) and closes the intro (`app/MedTimerAppIntro.kt:173-177`). If the user denies it, `showNotifications` is set to false (`app/MedTimerAppIntro.kt:34-35`).
   - **Done** closes the intro (`app/MedTimerAppIntro.kt:134-137`).
   - Minimum taps through the intro: **1** (Skip) plus the system permission dialog (1) = 2. With Next ×3, Done and the permission dialog it is 5.
3. **Authentication.** Biometric authentication runs only if "Require authentication to open the app" is on (`app/MainActivity.kt:142-147`). It is off on a fresh install (`res/xml/privacy_preferences.xml:10-15`).
4. **Main screen = Overview tab** (`res/navigation/navigation.xml:6,9`), with the bottom bar Overview / Medicine / Analysis (`app/AppNavigation.kt:92-114`). Below 600 dp it is a bottom bar; at 600 dp and wider it is a side rail (`app/AppNavigation.kt:76-81`). There is no Settings tab.
5. **Two warning cards can appear at the top of Overview** on first launch: "Battery optimization is enabled" (shown unless the app is already exempt) and "Exact reminders are disabled", which has **Enable** and OK buttons (`fui/overview/Warnings.kt:38-62`; `fui/overview/OverviewViewModel.kt:264-273`; `str:332-333,362-364`). Exact reminders default to off (`dom/model/UserPreferences.kt:69`). Tapping **Enable** turns exact reminders on and opens the system "alarms & reminders" permission page on Android 12 and later (`fui/overview/OverviewScreen.kt:101-104,126-132`).
6. The fresh Overview has **no "add your first medicine" call to action**. The only floating button is "Log additional dose" (`fui/overview/OverviewScreen.kt:277-285`). The empty-state setup card appears on the **Medicine** tab only (`fui/medicine/MedicinesScreen.kt:121-131`).
7. On later launches, the permission check runs only in the `else` branch, that is, after the intro has been seen (`app/MainActivity.kt:127-129,152-160`).

What first run asks, in order: notification permission (system dialog, during the intro), then nothing else. Exact alarms and battery optimisation are offered only as dismissible cards on Overview.

---

## 2. Set-up task, step by step

Defaults that matter here:

- A new reminder starts with **time 08:00** (`dom/model/ReminderTime.kt:21`, `DEFAULT_TIME = 480`; applied in `fui/medicine/NewReminderViewModel.kt:31-38`).
- Dosage starts **blank** (`fui/medicine/NewReminderState.kt:18`). A blank dosage is accepted on Create (`fui/medicine/NewReminderViewModel.kt:79`).
- A new reminder is active on every day: `days` empty means all days (`dom/model/Reminder.kt:89`; `fui/medicine/advancedReminderPreferences/ReminderDataStore.kt:169-174`).
- The creation dialog for a time-based reminder has **only two fields, Dosage and Time** (`fui/medicine/dialogs/NewReminderDialog.kt:254-272`). It has **no weekday choice**. Weekdays exist only on the reminder's "Advanced settings" screen (`res/xml/advanced_reminder_settings_root.xml:56-61`).
- Time picker: MaterialTimePicker in clock (dial) mode, 24-hour or 12-hour following the locale (`core/common/src/main/java/com/futsch1/medtimer/core/common/helpers/TimePickerDialogFactory.kt:17-19,28-32`). To set a whole hour, tap the hour on the dial and then OK: 2 taps on a 24-hour locale, 3 on a 12-hour locale (hour, then AM/PM, then OK). The minutes are already 00. The dial's exact layout is Material library behaviour **(unverified — needs device)**.
- Advanced reminder and medicine settings **save on every change**; there is no Save button. Each preference write calls `reminderRepository.update` (`fui/medicine/advancedReminderPreferences/ReminderDataStore.kt:65,149,164,188-219`).

### Task A: "Ibuprofen 400 mg", reminders at 08:00 and 20:00 (starting from Overview)

| # | Screen | Tap | Count | Cite |
|---|---|---|---|---|
| 1 | Overview | "Medicine" tab | 1 | `app/AppNavigation.kt:100-106` |
| 2 | Medicine list (empty; shows guidance card) | "Add medicine" (floating button, or the button on the guidance card) | 1 | `fui/medicine/MedicinesScreen.kt:95-106,121-131` |
| 3 | **Dialog** "Add medicine" | focus "Medicine name" (auto-focus, keyboard opens after 200 ms); type `Ibuprofen 400 mg` | 1 | `fui/medicine/MedicinesFragment.kt:138-158` |
| 4 | dialog | OK; the medicine is created and the **Edit medicine screen opens on its own** | 1 | `fui/medicine/MedicinesFragment.kt:167-176` |
| 5 | Edit medicine "Ibuprofen 400 mg" (shows the "Add reminder" guidance card) | "Add reminder" (floating button or card button) | 1 | `fui/medicine/EditMedicineScreen.kt:143-151,348-357` |
| 6 | **Dialog** "Add reminder": a list of 5 reminder types | "Time based reminder" | 1 | `fui/medicine/dialogs/NewReminderDialog.kt:56-62,103-104,143-168` |
| 7 | dialog "Time based reminder" (Dosage blank, Time 8:00) | "Create reminder" | 1 | `fui/medicine/dialogs/NewReminderDialog.kt:132-134` |
| 8 | Edit medicine | "Add reminder" | 1 | as #5 |
| 9 | dialog | "Time based reminder" | 1 | as #6 |
| 10 | dialog | "Time" button, which opens the **time-picker dialog** | 1 | `fui/medicine/dialogs/NewReminderDialog.kt:337-346` |
| 11 | time picker | tap 20 on the dial (12-hour locale: 8, then PM = 2) | 1 | `core/common/.../TimePickerDialogFactory.kt:28-32` |
| 12 | time picker | OK | 1 | `core/common/.../TimePickerDialogFactory.kt:37` |
| 13 | dialog | "Create reminder" | 1 | as #7 |

**Minimum: 13 taps** on a 24-hour locale, 14 on a 12-hour locale. Fields typed: medicine name.
The 13-tap minimum depends on three defaults: the 08:00 default time, a blank dosage being accepted, and the automatic move into Edit medicine after the name is entered.

To enter a dosage (needed for stock tracking, see Task C), add for each reminder: "Dosage" button, then focus (auto-focus), type `1 tablet`, then OK = **+3 per reminder, so +6, giving 19** (`fui/medicine/EditValueButton.kt:71-93,106-107,146`). Dosage can also be edited later on the reminder card (`fui/medicine/ReminderCard.kt:74-84`).

### Task B: "Vitamin D" once a week on Monday at 09:00 (continuing from Task A's Edit medicine screen)

| # | Screen | Tap | Count | Cite |
|---|---|---|---|---|
| 1 | Edit medicine (Ibuprofen) | back arrow in the top bar | 1 | `fui/medicine/EditMedicineFragment.kt:118-120` |
| 2 | Medicine list | "Add medicine" | 1 | `fui/medicine/MedicinesScreen.kt:95-106` |
| 3 | **Dialog** "Add medicine" | focus (auto-focus); type `Vitamin D` | 1 | `fui/medicine/MedicinesFragment.kt:138-158` |
| 4 | dialog | OK, which opens Edit medicine "Vitamin D" | 1 | `fui/medicine/MedicinesFragment.kt:167-176` |
| 5 | Edit medicine | "Add reminder" | 1 | `fui/medicine/EditMedicineScreen.kt:143-151` |
| 6 | **Dialog** | "Time based reminder" | 1 | `fui/medicine/dialogs/NewReminderDialog.kt:143-168` |
| 7 | dialog | "Time" | 1 | `fui/medicine/dialogs/NewReminderDialog.kt:270-272` |
| 8 | time picker | tap 9 | 1 | |
| 9 | time picker | OK | 1 | |
| 10 | dialog | "Create reminder"; the reminder is now **daily at 09:00** | 1 | `fui/medicine/NewReminderViewModel.kt:52-64` |
| 11 | Edit medicine, reminder card | "Advanced settings" button; **leaves the screen** for a new screen | 1 | `fui/medicine/ReminderCard.kt:86-89`; `fui/medicine/EditMedicineFragment.kt:138-154` |
| 12 | Advanced settings (preference screen) | "Remind on weekdays", which opens a **multi-select dialog with all 7 days ticked** | 1 | `res/xml/advanced_reminder_settings_root.xml:56-61`; `fui/medicine/advancedReminderPreferences/ReminderDataStore.kt:169-174` |
| 13-18 | weekday dialog | untick Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday | 6 | `arr:60-68` |
| 19 | weekday dialog | OK (saves at once) | 1 | `fui/medicine/advancedReminderPreferences/ReminderDataStore.kt:188-198` |
| (20) | Advanced settings | back (optional: the change is already saved) | (1) | |

**Minimum: 19 taps** (20 if counting the return to Edit medicine). Fields typed: medicine name.
This needs **one extra screen** (Advanced settings) and **one extra dialog** (weekdays), because the weekday choice is not in the creation dialog.
Starting from Overview instead of Task A's screen, swap step 1 for the "Medicine" tab; the count stays 19.

Alternative that avoids leaving the screen: a "Continuous interval reminder" with Interval = 1 week starting Monday at 09:00. That is Add reminder (1), type card (1), "Interval" (1), focus (1, type `1`), weeks toggle (1), OK (1), "Interval start time" (1), date picker: Monday (1) + OK (1), time picker: 9 (1) + OK (1), Create (1) = **12** in the dialog, plus 4 to create the medicine and 1 back = **17** (`fui/medicine/dialogs/NewReminderDialog.kt:274-322`; `res/layout/include_edit_interval.xml:24-62`). The meaning differs: it is an interval reminder, so its Advanced settings show the interval category instead of the weekday and cyclic options (`fui/medicine/advancedReminderPreferences/AdvancedReminderPreferencesRootFragment.kt:160-170`). Whether a user would find this route is **(unverified — needs device)**.

### Task C: stock of 30 tablets for Ibuprofen (starting from the Medicine list)

| # | Screen | Tap | Count | Cite |
|---|---|---|---|---|
| 1 | Medicine list | Ibuprofen card | 1 | `fui/medicine/MedicineCard.kt:64` |
| 2 | Edit medicine | pill-bottle icon "Medicine stock"; **leaves the screen** for the Stock screen | 1 | `fui/medicine/EditMedicineScreen.kt:278`; `fui/medicine/EditMedicineSubmenus.kt:53-63` |
| 3 | "Medicine stock settings" screen | "Remaining stock", which opens a **text dialog** (EditTextPreference) | 1 | `res/xml/stock_settings.xml:3-5` |
| 4 | dialog | focus; type `30` (numeric keyboard; the hint shows the unit if one is known) | 1 | `fui/medicine/medicineSettings/StockSettingsFragment.kt:230-236` |
| 5 | dialog | OK (saves at once) | 1 | |

**Minimum: 5 taps** (6 with back). Fields typed: remaining stock.
**The unit "tablets" cannot be set here.** The stock unit is taken from the reminders' dosage text (`core/common/src/main/java/com/futsch1/medtimer/core/common/helpers/MedicineHelper.kt:56-61`, which parses the first number plus the following word, `.../DoseAmountParser.kt:28-45`). With the blank dosages from Task A's minimum, stock shows as plain "30" and **is never reduced**, because a blank amount does not parse. For stock to mean "30 tablets" and count down, Task A needs the +6 dosage taps (`1 tablet` on each reminder). A warning card appears if reminders use different units (`fui/medicine/EditMedicineScreen.kt:309-340`).
Entry points to the same Stock screen: the pill-bottle icon, the capsule menu entry "Medicine stock settings" (`fui/medicine/EditMedicineActions.kt:129-131`), and "Edit medicine stock settings" on an out-of-stock reminder's settings (`res/xml/advanced_reminder_settings_stock.xml:31`).

### Totals

| Task | Minimum taps | With dosage "1 tablet" |
|---|---|---|
| A: Ibuprofen ×2 reminders | 13 | 19 |
| B: Vitamin D weekly | 19 (17 by the interval route) | 22 |
| C: Stock 30 | 5 | 5 |
| **All three** | **37** | **46** |

### Places where the user must leave the current screen

- Medicine list to Edit medicine: automatic after the name is entered (helpful).
- A weekday restriction needs the separate **Advanced settings** screen (`res/navigation/navigation.xml:66-82`).
- Stock needs the separate **Medicine stock settings** screen (`res/navigation/navigation.xml:107-114`).
- The intro's "Getting started" link and the guidance cards' "Getting started" button open the in-app help reader, which is a dialog over the app (`fui/medicine/SetupGuidance.kt:18-37`; `app/HelpReaderDialogFragment.kt:102,123`).

### Dialogs met in these tasks

"Add medicine" text dialog; "Add reminder" type chooser and details dialog; time picker (×3); weekday multi-select; "Remaining stock" text dialog. With dosages: a Dosage text dialog per reminder. That is **7 dialogs** at the minimum and 9 with dosages.

### Fields shown that these tasks do not need

- **Add reminder type chooser:** Continuous interval, Windowed interval, Out of stock and Expiration reminder cards (`fui/medicine/dialogs/NewReminderDialog.kt:56-62`), plus "Learn more about reminder types" (`:126-130`).
- **Time-based details:** "Dosage", which is optional at creation but needed for stock (`fui/medicine/dialogs/NewReminderDialog.kt:262-268`).
- **Edit medicine screen:** icon picker, name, buttons for Medicine settings / Notes / Tags / Stock / Calendar (`fui/medicine/EditMedicineScreen.kt:222-284`); the capsule menu and the ⋮ menu in the top bar (`fui/medicine/EditMedicineFragment.kt:118-131`); per reminder, the type-help icon and the summary text (`fui/medicine/ReminderCard.kt:60-66,90`).
- **Advanced settings (Task B):** Reminder enabled, Active date range, Dosing instructions, Add reminder for following dose, Cyclic reminder, Remind on days of month, Enter dosage when taken, Automatically mark as taken, Notification priority (`res/xml/advanced_reminder_settings_root.xml:4-87`).
- **Stock screen (Task C):** Estimated run out date, Refill amount, Refill now, Production date, Expiration date, Clear dates, Create expiration date reminder, Help; and, once a run-out date is known, "Add estimated run-out date to calendar" and "Create out of stock reminder" (`res/xml/stock_settings.xml:6-48`; `fui/medicine/medicineSettings/StockSettingsFragment.kt:156-172`).

---

## 3. Settings map

Depth means the taps from the Overview tab until the setting is **visible on screen**. Changing it costs +1, plus any dialog.
Labels are the English strings from `str`.

### 3.1 Global app settings

Entry: Overview top bar **⋮ "More options"** (1), then **"Settings"** (2) (`fui/AppOptionsMenu.kt:65-71,85`; `fui/AppOptionsMenuHost.kt:49-50`; `res/navigation/navigation.xml:147,184`). The same ⋮ menu is on the Medicine, Analysis and Edit medicine top bars (`fui/medicine/MedicinesFragment.kt:98-110`; `fui/medicine/EditMedicineFragment.kt:123-130`).

```
Settings  [depth 2]  (root_preferences.xml:3-27; only category links, no settings)
├─ Notification and reminder settings  [3]  (notification_settings.xml)
│   ├─ Notification settings default priority      → opens Android channel settings  :4-10
│   ├─ Notification settings high priority         → Android channel settings        :11-17
│   ├─ Notification settings out of stock          → Android channel settings        :18-24
│   ├─ Always play notification sound (switch)                                        :25-32
│   ├─ Repeat reminders  →  [4]  (repeat_reminders_preferences.xml)                  :33-39
│   │     ├─ Repeat reminders (switch)                                                :4-9
│   │     ├─ Number of repetitions (list, default 3)                                  :10-19
│   │     └─ Time between repetitions (list, default 10)                              :20-29
│   ├─ Action when dismissing notification (list: Skip dose / Snooze / Taken)        :40-47, arr:37-41
│   ├─ Reminders cannot be skipped (switch)                                           :48-55
│   ├─ Persistent reminders on lock screen (switch)                                   :56-63
│   ├─ Exact reminders (switch)                                                       :64-71
│   ├─ Ignore battery optimization → Android settings                                 :72-77; NotificationSettingsFragment.kt:146-154
│   └─ Notifications missing or late → help page                                      :78-84; NotificationSettingsFragment.kt:64-65
├─ Alarm settings  [3]  (alarm_settings.xml)
│   ├─ No alarm sound when silent                                                     :4-7
│   ├─ No vibration when silent                                                       :8-11
│   └─ Alarm ringtone (ringtone picker)                                               :12-17
├─ Snooze settings  [3]  (snooze_settings.xml)
│   ├─ Snooze duration (5/10/15/30/60 min / Custom… / Set duration…; default 15)     :4-10, arr:42-50
│   ├─ Location-based snooze (switch)                                                 :11-17
│   ├─ Home location                                                                  :18-22
│   └─ Show home location on map                                                      :23-27
├─ Display settings  [3]  (display_settings.xml)
│   ├─ Big notification buttons                                                       :4-8
│   ├─ Combine same time notifications                                                :9-14
│   ├─ Relative date/time                                                             :15-20
│   ├─ Show taken time in overview                                                    :21-25
│   ├─ Show reminded time in overview                                                 :26-30
│   ├─ Show stock changes in overview                                                 :31-35
│   ├─ Use system setting for date/time format                                        :36-41
│   └─ Theme (list; restarts the activity)                                            :43-50; PreferencesFragment.kt:65-79
├─ Privacy settings  [3]  (privacy_preferences.xml)
│   ├─ Block screen capture                                                           :4-9
│   ├─ Require authentication to open the app (enabled only if biometrics exist)      :10-15; PrivacyPreferencesFragment.kt:41-43
│   ├─ Hide medication name in notification                                           :16-20
│   └─ Disable home screen widgets                                                    :21-26
└─ Weekend mode  [3]  (weekend_mode_preferences.xml)
    ├─ Active (switch)                                                                :4-9
    ├─ Days (multi-select)                                                            :10-16
    ├─ Start time                                                                     :17-21
    └─ End time                                                                       :22-26
```
(XML files are in `res/xml/`; Kotlin files are in `fui/preferences/`.)

**Data and backup actions that sit in the ⋮ menu, not in Settings** (visible at depth 1, run at depth 2; `fui/AppOptionsMenu.kt:85-138`): Settings · Help · *Event data:* Export as PDF, Export as CSV, Clear all history · *Medicine data:* Export as PDF, Export as CSV · Automatic backup · Backup · Restore · Version (disabled) · MedTimer on GitHub.

**On the Overview screen itself** (depth 0-1): status filter chips (Taken / Skipped / Scheduled / Raised; `fui/overview/OverviewScreen.kt:203-224`; `dom/model/PersistentData.kt:14-15`), and the tag filter icon, shown only once tags exist (`fui/AppOptionsMenu.kt:54-63`).

### 3.2 Medicine-list settings

Medicine tab (1). The **capsule icon** in the top bar (2) opens: Activate all reminders · Deactivate all reminders · By name · By creation date (ascending) · By creation date (descending). This menu has no heading, and its icon is the same capsule as the tab (`fui/medicine/MedicinesMenu.kt:74-78,93-98`; labels `str:211-212`). Reordering by drag is also possible (`fui/medicine/MedicinesScreen.kt:133-141`).

### 3.3 Per-medicine settings

Medicine tab (1), then a medicine card (2) = **Edit medicine screen** (`res/navigation/navigation.xml:30-51`).

```
Edit medicine  [2]
├─ Icon (icon-pack dialog)                                       EditMedicineScreen.kt:245; EditMedicineFragment.kt:48-53
├─ Medicine name (text dialog)                                   EditMedicineScreen.kt:258-265
├─ gear "Medicine settings" → [3]  (medicine_settings.xml)       EditMedicineScreen.kt:268-270
│   ├─ Color (switch) / Select color (colour dialog)            :4-11; MedicineSettingsFragment.kt:24-38
│   ├─ Notification priority (Default / High / High + alarm)    :12-17
│   ├─ Medicine cannot be skipped                               :18-21
│   └─ Level estimate: Half-life (hours), Time to peak (hours)  :22-34
├─ "Notes" (dialog)                                              EditMedicineScreen.kt:272
├─ "Tags" (dialog)                                               EditMedicineScreen.kt:275; EditMedicineSubmenus.kt:46-51
├─ pill bottle "Medicine stock" → [3]  (stock_settings.xml)      EditMedicineScreen.kt:278
│   ├─ Remaining stock · Estimated run out date (read only)     :3-9
│   ├─ Add estimated run-out date to calendar / Create out of stock reminder (only once a run-out date exists)  :10-19
│   ├─ Stock refill: Refill amount · Refill now                 :20-28
│   ├─ Expiration settings: Production date · Expiration date · Clear dates · Create expiration date reminder  :29-44
│   └─ Help                                                     :45-48
├─ "Calendar" → [3] calendar of this medicine                    EditMedicineScreen.kt:281; EditMedicineSubmenus.kt:65-75
├─ capsule menu (top bar) [3 visible]: Medicine settings · Notes · Tags · Medicine stock settings · Calendar ·
│     Activate all reminders · Deactivate all reminders · Duplicate · Duplicate including reminders · Delete
│                                                                EditMedicineActions.kt:94-147
└─ ⋮ More options: the same global menu as 3.1                    EditMedicineFragment.kt:123-130
```
(XML files are in `res/xml/`; Kotlin files are in `fui/medicine/`, `fui/medicine/medicineSettings/`.)

### 3.4 Per-reminder settings

Inline on the reminder card on Edit medicine (depth 2): **Time** (time picker), **Dosage** (text dialog), and the type icon, which opens a help dialog (`fui/medicine/ReminderCard.kt:60-84`).
**"Advanced settings"** button on the card opens a new screen at depth 3 (`fui/medicine/ReminderCard.kt:86-89`):

```
Advanced settings (time-based or interval reminder) [3]  (advanced_reminder_settings_root.xml)
├─ Reminder enabled (switch: "Disable to pause all notifications for this reminder")  :4-9
├─ Active date range → [4]: Active from (switch) + Start date · Active until (switch) + End date  :10-13; advanced_reminder_settings_status.xml:3-14
├─ Dosing instructions → [4]: Dosing instructions (text) · Fill with sample instructions (list)  :14-17; advanced_reminder_settings_instructions.xml:4-13
├─ Add reminder for following dose (non-interval only)                                  :18-22; …RootFragment.kt:161
├─ Interval based reminder (interval types only): Interval · Interval start time · Daily start time · Daily end time · Interval start condition  :23-48
├─ Time based reminder (time-based only):
│   ├─ Cyclic reminder → [4]: First cycle start · Active days · Pause days             :52-55; advanced_reminder_settings_cyclic.xml:3-14
│   ├─ Remind on weekdays (multi-select)                                               :56-61
│   └─ Remind on days of month (multi-select 1-31)                                     :62-67
├─ Further settings: Enter dosage when taken · Automatically mark as taken · Notification priority (Same as medicine / Default / High / High + alarm)  :70-87; …RootFragment.kt:127-133
└─ ⋮ (top bar): Duplicate · Delete                                                      AdvancedReminderSettingsActions.kt:86-116

Advanced settings (out-of-stock or expiration reminder) [3]  (advanced_reminder_settings_stock.xml)
├─ Reminder enabled · Reminder threshold · Out of stock reminder · Expiration reminder · Remind days before expiration  :4-27
└─ Medicine stock: Remaining stock (read only) · Expiration date (read only) · Edit medicine stock settings → [4]  :28-32
```
(Screen routing by reminder type: `fui/medicine/EditMedicineFragment.kt:138-154`. XML files are in `res/xml/`.)

### 3.5 What makes reaching settings slow

1. **No Settings tab.** Global settings sit behind ⋮, as one entry among about 13 menu entries (exports, backup, history wipe, GitHub link) (`fui/AppOptionsMenu.kt:85-138`). The bottom bar has three tabs only (`app/AppNavigation.kt:92-114`).
2. **The root is only a list of categories.** `root_preferences.xml` holds six links and no settings, so every global setting is at depth 3 or more, and the repeat-reminder options are at 4 (`res/xml/root_preferences.xml:3-27`; `res/xml/notification_settings.xml:33-39`).
3. **Related settings are split across categories and levels.**
   - Skipping: "Reminders cannot be skipped" is global (Notification settings) and "Medicine cannot be skipped" is per medicine (`res/xml/notification_settings.xml:48-55`; `res/xml/medicine_settings.xml:18-21`).
   - Notification priority is set in three places: Android channels, per medicine, and per reminder (`res/xml/notification_settings.xml:4-24`; `res/xml/medicine_settings.xml:12-17`; `res/xml/advanced_reminder_settings_root.xml:81-86`).
   - The snooze length is in Snooze settings, but what swiping a notification away does is in Notification settings (`res/xml/snooze_settings.xml:4-10`; `res/xml/notification_settings.xml:40-47`).
   - Notification look ("Big notification buttons", "Combine same time notifications") is under Display settings, not Notification settings (`res/xml/display_settings.xml:4-14`).
4. **Unlabelled icon menus.** Edit medicine has two top-bar menus side by side: a capsule icon (medicine actions, accessibility name "Medicine") and ⋮ (app-wide) (`fui/medicine/EditMedicineActions.kt:94-101`; `fui/medicine/EditMedicineFragment.kt:121-131`). The Medicine tab's capsule icon hides bulk activation and sorting (`fui/medicine/MedicinesMenu.kt:74-78`). Per-medicine settings are icon-only buttons (gear, notes, tag, bottle, calendar) (`fui/medicine/EditMedicineScreen.kt:268-283`).
5. **Schedule rules sit behind "Advanced settings".** The weekday, day-of-month, cyclic and active-range options are one screen below the reminder, and some are two screens below (`res/xml/advanced_reminder_settings_root.xml`).
6. **Duplicate routes.** Medicine settings opens from the gear button and from the capsule menu; Stock opens from the bottle, the capsule menu and the stock reminder's page (`fui/medicine/EditMedicineActions.kt:120-133`; `res/xml/advanced_reminder_settings_stock.xml:31`).
7. **Settings drop out of the back stack on a tab switch.** Tapping any tab pops `preferencesFragment` (`app/MainActivity.kt:233`), so the user must go back through ⋮ → Settings → category.
8. **A theme change restarts the activity** (`fui/preferences/PreferencesFragment.kt:65-79`).

---

## 4. Daily actions

### From the notification

Defaults: "Action when dismissing notification" = **Skip dose** (`dom/model/UserPreferences.kt:76`; `res/xml/notification_settings.xml:40-47`). With that default the notification carries the buttons **[Taken] [Snooze]**, and swiping it away records a **skip** (`frem/notificationFactory/ReminderNotificationFactory.kt:96-102,56`; `frem/notificationFactory/NotificationIntentBuilder.kt:119-123`). Other choices of the setting give [Taken][Skipped] (dismiss = snooze) or [Skipped][Snooze] (dismiss = taken) (`ReminderNotificationFactory.kt:104-112`).

| Action | Taps | Cite |
|---|---|---|
| Taken | 1 | `ReminderNotificationFactory.kt:142-146` |
| Skipped | 1 swipe (no Skipped button by default) | as above |
| Snooze | 1 (default 15 minutes, `UserPreferences.kt:73`); if the duration is "Custom…", the app opens a minutes dialog: +focus, then OK | `app/CustomSnoozeHandler.kt:26-38`; `arr:42-50` |
| Snooze until home | 1, only if Location-based snooze is on | `ReminderNotificationFactory.kt:115,132-140` |
| "Enter dosage when taken" reminders | Taken opens a dosage dialog in the app | `fui/overview/VariableAmountHandler.kt:31`; `app/MainActivity.kt:280-282` |
| Tap the notification body | opens Overview at that day with the event in view | `ReminderNotificationFactory.kt:54`; `app/MainActivity.kt:288-291`; `fui/overview/ReminderNotificationTapHandler.kt:28-40` |
| High + alarm priority | full-screen alarm activity | `ReminderNotificationFactory.kt:66-68,72-85`; `frem/alarm/ReminderAlarmActivity.kt:33` |

### From the app (Overview)

Each event row has a round **state button**. Tapping it (1) opens an arc menu of the actions that apply, and choosing one is the second tap (`fui/overview/EventStateButton.kt:55-76`; `fui/overview/ArcActionMenu.kt`).

- Due or scheduled event: Taken · Skipped (hidden if it cannot be skipped) · Reschedule reminder (`fui/overview/actions/ScheduledReminderActions.kt:17-23`; `fui/overview/actions/ReminderEventActions.kt:25-35`).
- **Taken = 2 taps, Skipped = 2.** Future events can be marked as well (`fui/overview/actions/ActionsVisitor.kt:64-71,173-192`).
- **Snooze from the app = "Reschedule reminder" (2), then the time picker (hour + OK, 2) = 4** (`fui/overview/actions/ActionsVisitor.kt:89-123`).
- **Take all due together:** long-press a card (1) to enter selection mode, "Select all" (1), then the Taken icon in the selection bar (1) = **3** (`core/ui/src/main/java/com/futsch1/medtimer/core/ui/component/SelectableCard.kt:49-51`; `fui/overview/OverviewTopBar.kt:79-113`). The bar shows only the actions shared by every selected item (`fui/overview/actions/MultipleActions.kt:16-27`). With "Combine same time notifications" on, a long-press selects all events at the same time (`fui/overview/OverviewScreen.kt:105-111`), and the combined notification's Taken covers them all (`res/xml/display_settings.xml:9-14`).
- **Extra or as-needed dose:** the "Log additional dose" floating button (1) runs a 3-step guided dialog: Medicine (tap the medicine, 1) → Amount ("No amount", a listed amount or Custom…, 1) → Time ("Log at <now>", 1) = **4**. Recently used custom doses appear as one-tap "Quick log" rows on the first step, giving **2** (`fui/overview/OverviewScreen.kt:277-285`; `fui/overview/manualDose/ManualDoseDialog.kt:224-295,476-522`; `fui/overview/OverviewFragment.kt:99-103,115-144`). A medicine that is not in the list can be typed in with "Custom…" (`ManualDoseDialog.kt:239-244`).
- **Undo of a wrong answer:** there is **no undo or snackbar**; the source has no snackbar and no "undo" string. To correct, tap the state button and choose the other state (Taken ↔ Skipped) = **2** (`fui/overview/actions/ReminderEventActions.kt:25-30`). Alternatives:
  - "Re-raise event": deletes the event and notifies again, 2 + confirm = 3 (`fui/overview/actions/ActionsVisitor.kt:141-149`; `str:233`).
  - "Delete": 2 + confirm = 3 (`ActionsVisitor.kt:159-171`).
  - Stock is credited back on both (`ActionsVisitor.kt:151-157`).
- **Editing a past dose:** tap a processed event's card (1) to open the "Edit event" side sheet. It offers a Taken/Skipped toggle, Medicine name, Dosage, Reminded time and date, Taken time and date, and Notes, and **saves when the sheet is dismissed** (`fui/overview/OverviewFragment.kt:180-193`; `fui/overview/EditEventSheetDialogFragment.kt:77-84,158-172`; `res/layout/sheet_edit_event.xml:26-195`). Past days are reached through the week selector or by swiping the list sideways (`fui/overview/OverviewScreen.kt:196-238`). Events can also be edited from Analysis → table view (`fui/statistics/StatisticsFragment.kt:73,80`).
- Tapping a scheduled (future) event opens a read-only medicine sheet (`fui/overview/OverviewFragment.kt:189-191`).

---

## 5. Later maintenance

All counts start from Overview.

| Task | Path | Taps | Cite |
|---|---|---|---|
| Pause one reminder | Medicine tab → medicine → "Advanced settings" → "Reminder enabled" switch | 4 | `res/xml/advanced_reminder_settings_root.xml:4-9` |
| Pause all reminders of a medicine | Medicine tab → medicine → capsule menu → "Deactivate all reminders" | 4 | `fui/medicine/EditMedicineActions.kt:137-138` |
| Pause everything | Medicine tab → capsule menu → "Deactivate all reminders" | 3 | `fui/medicine/MedicinesMenu.kt:93-94` |
| Pause by date | … Advanced settings → Active date range → switch + date | 6+ | `res/xml/advanced_reminder_settings_status.xml` |
| Delete a reminder | Medicine tab → medicine → swipe the reminder card left → "Yes" | 4 | `fui/medicine/EditMedicineScreen.kt:360`; `fui/medicine/MedicinesScreen.kt:195-234` |
| (alt) | … Advanced settings → ⋮ → Delete → Yes (also deletes linked reminders) | 6 | `fui/medicine/advancedReminderPreferences/AdvancedReminderSettingsActions.kt:71-78`; `fui/medicine/LinkedReminderHandling.kt:66-81` |
| Delete a medicine | Medicine tab → swipe the card left → "Yes" | 3 | `fui/medicine/MedicinesScreen.kt:134,195-234`; `fui/medicine/MedicinesFragment.kt:134-136` |
| (alt) | Medicine tab → medicine → capsule menu → Delete → Yes | 5 | `fui/medicine/EditMedicineActions.kt:70-81,147` |
| Archive a medicine | **Does not exist**: only delete, or deactivate its reminders | — | the source has no "archive" string |
| Change a reminder time | Medicine tab → medicine → Time button on the card → hour → OK (+1 if the minutes change) | 5 | `fui/medicine/ReminderCard.kt:106-130` |
| Refill stock | Medicine tab → medicine → bottle → "Refill now" (adds "Refill amount"; that field defaults to 0, so set it first: +3) | 4 (7 the first time) | `res/xml/stock_settings.xml:20-28`; `fui/medicine/medicineSettings/StockSettingsFragment.kt:194-197`; `dom/model/Medicine.kt:70` |
| Refill from the notification | the out-of-stock notification has a "Refill" action | 1 | `frem/notificationFactory/OutOfStockNotificationFactory.kt:56-60` |
| Set stock directly | … bottle → Remaining stock → type → OK | 6 | `res/xml/stock_settings.xml:3-5` |

---

## 6. Feature inventory

| Feature | Where it lives | One line | Citation |
|---|---|---|---|
| Overview (day list) | Overview tab | Events for one day, with a week selector and sideways swipe between days | `fui/overview/OverviewScreen.kt:163,196-238` |
| Overview status filters | Overview | Chips for Taken / Skipped / Scheduled / Raised | `fui/overview/OverviewFilterRow.kt`; `dom/model/PersistentData.kt:14-15` |
| Batch actions | Overview, after a long-press | Multi-select with Select all; shared actions in the bar | `fui/overview/OverviewTopBar.kt:79-113` |
| Log additional dose | Overview floating button | Guided Medicine → Amount → Time logging, with quick-log rows for recent doses | `fui/overview/manualDose/ManualDoseDialog.kt:224-295,476-522` |
| Edit past event | Overview card tap; Analysis table | Change status, name, dose, times, notes | `fui/overview/EditEventSheetDialogFragment.kt:77-84` |
| Analysis: charts | Analysis tab | Bar charts | `fui/statistics/StatisticsScreen.kt:104,147` |
| Analysis: table | Analysis tab | Searchable event table | `fui/statistics/StatisticsScreen.kt:107,148-153` |
| Analysis: calendar | Analysis tab | Calendar of events | `fui/statistics/StatisticsScreen.kt:110,155` |
| Analysis: levels | Analysis tab | Estimated blood level curve (one-compartment model) | `fui/statistics/StatisticsScreen.kt:113,156-158`; `res/xml/medicine_settings.xml:22-34` |
| Per-medicine calendar | Edit medicine → calendar icon | 1 month back, 9 months ahead | `fui/medicine/EditMedicineSubmenus.kt:65-75` |
| Reminder types | Add reminder dialog | Time based, Continuous interval, Windowed interval, Out of stock, Expiration | `fui/medicine/dialogs/NewReminderDialog.kt:56-62` |
| Linked (follow-up) reminders | Advanced settings | "Add reminder for following dose" | `res/xml/advanced_reminder_settings_root.xml:18-22` |
| Weekdays / days of month / cyclic | Advanced settings | Schedule limits, e.g. birth-control cycles | `res/xml/advanced_reminder_settings_root.xml:52-67`; `res/xml/advanced_reminder_settings_cyclic.xml` |
| Active date range | Advanced settings → Active date range | First and last notification date | `res/xml/advanced_reminder_settings_status.xml` |
| Dosing instructions | Advanced settings → Dosing instructions | Free text plus sample list | `res/xml/advanced_reminder_settings_instructions.xml` |
| Variable dose / auto-taken | Advanced settings → Further settings | Ask the dose at intake; silent auto-taken for stock-only tracking | `res/xml/advanced_reminder_settings_root.xml:71-80` |
| Stock tracking | Edit medicine → bottle | Remaining stock, dose-unit parsing, estimated run-out date | `res/xml/stock_settings.xml:3-9`; `fui/medicine/medicineSettings/StockSettingsFragment.kt:156-172` |
| Run-out to calendar | Stock screen | Hands the date to the system calendar app | `fui/medicine/medicineSettings/StockSettingsFragment.kt:174-192` |
| Refill | Stock screen; out-of-stock notification | One-tap refill by the set amount | `fui/medicine/medicineSettings/StockSettingsFragment.kt:194-197`; `frem/notificationFactory/OutOfStockNotificationFactory.kt:56-60` |
| Expiry | Stock screen | Production and expiration dates, expiration reminder | `res/xml/stock_settings.xml:29-44` |
| Tags + filter | Edit medicine → tag; tag icon on top bars | Tag medicines, filter the lists by tag | `fui/medicine/tags/TagsFragment.kt:20-23`; `fui/AppOptionsMenu.kt:54-63` |
| Notes | Edit medicine | Per-medicine notes dialog | `fui/medicine/EditMedicineScreen.kt:272`; `fui/medicine/dialogs/NotesDialog.kt` |
| Icon / colour | Edit medicine; Medicine settings | Icon pack, colour swatch | `fui/medicine/EditMedicineFragment.kt:48-53`; `res/xml/medicine_settings.xml:4-11` |
| Duplicate medicine | Capsule menu | With or without reminders | `fui/medicine/EditMedicineActions.kt:141-146` |
| Sort / reorder medicines | Medicine tab | Capsule menu sorts; drag handle reorders | `fui/medicine/MedicinesMenu.kt:96-98`; `fui/medicine/MedicinesScreen.kt:133-141` |
| Repeat reminders | Settings → Notification | Re-notify N times every M minutes | `res/xml/repeat_reminders_preferences.xml` |
| Snooze, custom snooze | Notification; Settings → Snooze | Fixed or typed duration | `res/xml/snooze_settings.xml:4-10`; `app/CustomSnoozeHandler.kt:26-38` |
| Location snooze | Settings → Snooze | Re-notify on arriving home | `res/xml/snooze_settings.xml:11-27`; module `core/location` |
| Alarm mode | Medicine or reminder priority "High + alarm" | Full-screen alarm activity, ringtone, silent-mode options | `frem/alarm/ReminderAlarmActivity.kt:33`; `res/xml/alarm_settings.xml` |
| Weekend mode | Settings → Weekend mode | Delays notifications on chosen days | `res/xml/weekend_mode_preferences.xml` |
| Notification look | Settings → Display | Big buttons, combine same-time notifications | `res/xml/display_settings.xml:4-14` |
| App lock | Settings → Privacy | Biometric or device-credential lock on launch | `res/xml/privacy_preferences.xml:10-15`; `app/MainActivity.kt:142-147` |
| Screen-capture block | Settings → Privacy | FLAG_SECURE | `app/MainActivity.kt:107-109` |
| Hide name in notification | Settings → Privacy | — | `res/xml/privacy_preferences.xml:16-20` |
| Widgets | Home screen | "Next reminders" and "Latest reminders" widgets; can be disabled for privacy | `feature/reminders/src/main/AndroidManifest.xml:29-52`; `frem/widgets/` |
| Wear / watch | — | **None**: no Wear module or manifest entry; only a note about smartwatches in a setting summary | `settings.gradle.kts`; `str:289` |
| Export | ⋮ menu | Events and medicines as PDF or CSV | `fui/AppOptionsMenu.kt:89-113`; `fui/exporters/` |
| Backup / restore | ⋮ menu | JSON backup of medicines, events and settings; automatic backup Never/Daily/Weekly/Monthly | `app/database/backup/JSONBackup.kt`; `dom/model/UserPreferences.kt:18-19`; `fui/AppOptionsMenu.kt:116-118` |
| Clear history | ⋮ menu | Wipes all events | `fui/AppOptionsMenu.kt:100` |
| Help | ⋮ menu; guidance cards; intro | Offline help pages in a WebView from assets | `app/HelpReaderDialogFragment.kt:102,123` |
| Setup guidance | Empty Medicine list, empty reminder list | Cards with an action and "Getting started" | `fui/medicine/SetupGuidance.kt:18-37` |
| Battery / exact warnings | Overview | Dismissible cards | `fui/overview/Warnings.kt:38-62` |
| Profiles (several people) | — | **None**: there is no profile or patient model in `core/domain` | `dom/model/` |
| Languages | system / app locale | English plus 26 translations (ar, bg, cs, da, de, el, es, et, fi, fr, hu, is, it, iw, nb-rNO, nl, pl, pt, pt-rBR, ru, sv, ta, tr, uk, zh-rCN, zh-rTW) | `core/ui/src/main/res/values-*` |
| Theme | Settings → Display | Default or alternative theme | `res/xml/display_settings.xml:43-50`; `app/MainActivity.kt:102-104` |
| Accessibility | throughout | Content descriptions on icons and menus, descriptive names for the Export entries, adaptive bar/rail, big notification buttons; no dedicated accessibility settings | `app/AppNavigation.kt:143-150`; `fui/AppOptionsMenu.kt:143-160`; `res/xml/display_settings.xml:4-8` |
| Flavours | build | `full` and `foss` | `app/build.gradle.kts:37-42` |

---

## 7. Notable code-level qualities (if code were borrowed)

- **Modules** (`settings.gradle.kts`): `:app`, `:core:domain`, `:core:database`, `:core:common`, `:core:datastore`, `:core:ui`, `:core:location`, `:feature:reminders`, `:feature:reminders:api`, `:feature:ui`.
  - `core:domain` has no project dependencies (`core/domain/build.gradle.kts`, no `project(` lines).
  - `feature:ui` depends on `feature:reminders:api` but **not** on `feature:reminders` (`feature/ui/build.gradle.kts:64-70`). It talks to the engine through a command bus (`fui/overview/actions/ActionsVisitor.kt:36`).
  - Only `:app` wires everything together (`app/build.gradle.kts:202-209`). DI is Hilt throughout.
- **UI stack is hybrid.** Fragments with Navigation XML (`res/navigation/navigation.xml`) host Compose screens (Overview, Medicine list, Edit medicine, dialogs), while all settings screens are androidx.preference XML (`res/xml/*.xml`). The per-medicine and per-reminder preference screens write straight to repositories through custom `PreferenceDataStore`s (`fui/medicine/advancedReminderPreferences/ReminderDataStore.kt:24,188-219`; base `core/ui/src/main/java/com/futsch1/medtimer/core/ui/preferences/ModelDataPreferencesFragment.kt:27-155`).
- **Tests: 585 `@Test` annotations** (counted with the regex `@Test(\s|$|\()`, which leaves out `@TestInstallIn` and similar):

  | Location | Count |
  |---|---|
  | `app/src/test` | 128 (including `schedulertests/`, `processortests/`) |
  | `app/src/androidTest` | 62 (robot-pattern UI tests) |
  | `core/domain` | 59 |
  | `core/database` | 7 |
  | `core/common` | 15 |
  | `core/datastore` | 91 |
  | `core/ui` | 19 |
  | `core/location` | 0 |
  | `feature/reminders` (+api) | 0 |
  | `feature/ui` | 193 |

- **Cleanly separable:**
  - **Scheduler.** `feature/reminders/api/.../scheduling/` has 12 files (`StandardScheduling`, `IntervalScheduling`, `WindowedIntervalScheduling`, `LinkedScheduling`, `CyclesHelper`, `WeekendModeSchedulingDecorator`, `SchedulingSimulator`, …). It has no View or Compose imports and depends only on `core:domain/common/datastore` (`feature/reminders/api/build.gradle.kts:31-33`). Its tests live in `app/src/test/.../schedulertests` (13 files) rather than in the module.
  - **Domain models and backup DTOs** in `core/domain` (`dom/backup/`, 6 files).
  - **Dose-text parsing** (`core/common/.../helpers/DoseAmountParser.kt`).
- **Tangled with UI:**
  - **Backup.** `app/database/backup/BackupManager.kt` imports `Toast`, `MaterialAlertDialogBuilder`, `FragmentManager` and `ProgressDialogFragment` next to Gson (`BackupManager.kt:3-29`). The JSON serializers (`JSONBackup*.kt`) and `core/datastore/.../BackupMapper.kt` can be lifted out, but the manager cannot.
  - **Notification building** in `feature:reminders` reads preferences directly (`frem/notificationFactory/ReminderNotificationFactory.kt:96-116`).
  - **Medicine creation** is done inside `MedicinesFragment` (a View-based `MaterialAlertDialogBuilder`, `fui/medicine/MedicinesFragment.kt:138-179`), not in a ViewModel.
- **Licence:** MIT (`LICENSE`).

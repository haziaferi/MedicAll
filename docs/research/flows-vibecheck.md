# Vibecheck 1.0.3: mood, tag and insight flows, mapped from source

Source: `_ref/vibecheck`, outside this repository (GPL-3.0, React 19 + TypeScript + Capacitor 8, head commit `5d21a83`, versionName 1.0.3 at `android/app/build.gradle:22-23`). Read only: nothing was built, installed, run or edited. `node_modules` is absent, so library code (Capacitor plugins, Recharts) was not read.

Every claim cites `path:line` relative to that root. **(inference)** marks a conclusion drawn from the code rather than stated by it. **Not found** means zero hits for the listed names in `*.ts`, `*.tsx` and `*.java` outside `node_modules`.

How taps are counted (same rule as the MedTimer, Emivita and Flare Enough notes): each click, toggle, selection, picker confirm, back or Add counts as 1 tap. Entering text counts as 1 tap to focus the field; typing is not counted. System dialogs count as 1 tap.

**Dead code.** Some files are never imported by the running app, so they are left out of the flows below:
- `context/AppContext.tsx` (reducer, streak, widget sync): `AppProvider`/`useApp` are used only inside that file (grep over the repo); `index.tsx:12` renders `App` with no provider. `App.tsx` keeps its own state (`App.tsx:79-111`).
- `components/EntryForm/` (MoodPicker, TagSelector, CopingStrategies) and `components/Settings/` (AppearanceTab, NotificationsTab, DataTab) are exported from their `index.ts` files, but nothing imports those folders (grep for `EntryForm/` and `Settings/`: zero importers). The live screens are `components/EntryForm.tsx` and `components/SettingsView.tsx`.
- `utils/dateUtils.ts` is imported only by its own test (`utils/dateUtils.test.ts:3`).

---

## 1. First run to first entry

1. **Onboarding gate.** `App` reads `vibecheck_onboarding_done` (`App.tsx:116-117`, `services/storage.ts:70-73`). If it is false, `OnboardingScreen` takes over (`App.tsx:505-518`).
2. **Four screens** (`screens/OnboardingScreen.tsx`):
   - Welcome: logo, "100% Private & Offline", a lock box, and **Get started** (`:80-99`).
   - Step 1 of 3, the name: **required**. `handleName` returns early on an empty name, and Next is disabled until something is typed (`:55-59`, `:120-125`).
   - Step 2 of 3, appearance: 4 colour themes plus Light/Dark (`:10-15`, `:132-184`). **Next** saves them (`:61-65`).
   - Step 3 of 3, "Daily check-in?": a time field set to 20:00 (`:22`, `:199-205`). **Set reminder** asks for notification permission and schedules a daily reminder (`:67-77`). "No thanks, I'll set this later" switches the button to Continue (`:215-220`).
3. **Home** after onboarding: "Hi, {name}!" with a random emoji and a random reassuring line (`App.tsx:21-76`, `:194-199`, `:415-416`). The big "How are you? / Tap to log your mood" card shows only while **no entry exists today** (`App.tsx:404`, `:420-432`). Below it are the 5 newest entries (`App.tsx:469-473`).
4. **First entry** (`components/EntryForm.tsx`):
   - Step 1 is the mood picker, "How's the vibe today?", with 5 blobs (`:316-381`).
   - **Tapping a mood saves the entry at once**, with no tags and an empty note (`:177-189`), then opens step 2 "Details" (`:199`).
   - Step 2 shows "Logged: HH:MM" and the text "Feel free to add more details if you'd like" (`:696-704`). **Done!** closes the form (`:707-712`, `App.tsx:345-350`).

**Tap count, install to first saved entry:** Get started 1, name field 1, Next 1, Next 1, Set reminder 1, system permission 1, How are you? 1, mood 1 = **8 taps** (7 if you skip the reminder). The entry exists at tap 8; Done! is a 9th tap that only closes the form.

## 2. Data model

### Entry shape (`types.ts:24-32`)
`{ id, timestamp (Unix ms), mood: 1-5, tags: string[] (tag IDs), note: string, symptoms?: SymptomsData, customMoods?: string[] }`.
- **Timestamps:** yes, to the minute. Each entry has a date and a time the user can edit (`EntryForm.tsx:70-71`, `:466-491`). The saved timestamp is `new Date(date + 'T' + time)` (`:275`).
- **Several entries per day:** yes. Each new save gets `crypto.randomUUID()` (`App.tsx:318`). The calendar lists every entry of the chosen day (`components/CalendarView.tsx:82-85`, `:106-138`). Views that need one value per day **average the entries**: the calendar dot (`CalendarView.tsx:196-201`) and the 14-day chart (`components/InsightsView.tsx:88-114`).

### Mood scale
- One scale, 1-5, **higher = better**: `Terrible=1 ... Great=5` (`types.ts:1-7`).
- The labels are fixed in code, Rough / Not Great / Doin' Okay / Pretty Good / Feeling Fab (`constants.ts:4-45`, repeated at `EntryForm.tsx:10-16`). The user cannot rename them.
- Insights sorts tags "Best to Worst" by average mood (`InsightsView.tsx:152`) and colours teal for high, coral for low (`:298-304`). The direction is built into every chart.
- **Custom moods** ("Other Moods?") are `{label, color, icon?, value 0-5}`, where 0 means "descriptive only" (`types.ts:59-66`). The value comes from a 0-5 slider (`components/MoodManager.tsx:146-151`). Picking one sets the base mood to its value, or to 3 when the value is 0 (`EntryForm.tsx:406-412`). Only one can be chosen per entry (`:409-411`, `:537-557`).

### Energy, focus, sleep, caffeine, medication ("symptoms")
- `SymptomsData` declares `tookMedication`, `medicationTime`, `sleep: 'bad'|'ok'|'good'`, `energy 1-5`, `focus 1-5` and `coffeeCount` (`types.ts:34-41`). A `Symptom` type with `isHidden` exists too (`types.ts:43-49`).
- **No live screen writes them.** Every `onSubmit` call in `EntryForm.tsx` passes `undefined` for symptoms (`:180`, `:193`, `:281`, `:543`, `:553`). `HIDDEN_SYMPTOMS_KEY` is declared (`services/storageKeys.ts:8`) and imported (`services/storage.ts:12`) but never read or written. No `DEFAULT_SYMPTOMS` list exists (`constants.ts:1` imports `Symptom` and never uses it).
- In practice the "symptoms" are **tags**: Brain Fog, Poor Sleep, Meds Taken/Skipped, Overstimulated, Time Blindness and others (`constants.ts:47-75`). There is no severity. A tag is either on the entry or not.
- **(inference)** Editing an entry that has `symptoms` (for example one restored from a backup) erases them, because `App.handleEntrySubmit` rebuilds the whole entry from the arguments (`App.tsx:320-328`).

### Tags
- `Tag = {id, label, category, color?, isHidden?}` (`types.ts:16-22`). There are 20 defaults (`constants.ts:47-75`). Each tag belongs to one of 5 default categories, each with a colour (`constants.ts:226-232`). Users can add categories (`components/TagManagerModal.tsx:50-53`).
- **Free text plus a list.** "+ New" opens a text field that searches existing tags as you type (Fuse.js, threshold 0.3) and offers "Use existing" (`EntryForm.tsx:96`, `:236-261`, `:613-626`). In Manage Tags, a similar name triggers `confirm('A similar tag exists ...')` (`TagManagerModal.tsx:72-92`).
- **Order:** sorted by how often each tag has been used, then alphabetically (`EntryForm.tsx:141-148`). Only the first 15 show until "+ N" is tapped (`:631`, `:647-654`). Positions therefore shift as usage changes. The usage count goes up on every save that carries tags (`App.tsx:330-332`), including the in-form saves when a custom mood changes (`EntryForm.tsx:193`, `:543-556`), so edits inflate it **(inference)**.
- **Rename:** for defaults, the new label is stored in a label-override map; for custom tags, the stored label changes (`storage.ts:172-189`, `:208-221`). Entries hold only IDs, and every view looks up the **current** label when it draws (`components/TimelineView.tsx:216-222`, `InsightsView.tsx:143-146`). **So a rename rewrites history, the same weakness as Flare Enough.**
- **Delete = hide.** A default tag goes on a hidden list; a custom tag gets `isHidden: true` (`storage.ts:149-169`). The prompt says "Hide this tag? It will remain attached to past entries." (`TagManagerModal.tsx:124-126`). The ID does stay on the entries, but the Timeline loads only visible tags and **draws nothing for a hidden one** (`TimelineView.tsx:40`, `:217-222`). Insights shows the raw ID in place of the label (`InsightsView.tsx:49`, `:143-146`). So hiding a tag removes it from how history looks.
- **Merge:** not found (grepped `merge`, `Merge`; the only hits are code comments in `storage.ts:306` and `TagManagerModal.tsx:159`).
- **Recategorise a default tag:** saves a custom tag with the same ID (`TagManagerModal.tsx:156-160`). `getAllTags` then returns defaults plus customs with no dedupe (`storage.ts:203`), so the tag appears twice **(inference)**.
- **Custom mood delete is a hard delete** (`storage.ts:365-370`, `MoodManager.tsx:72-75`). Past entries keep the ID, and the Timeline falls back to the base mood's label (`TimelineView.tsx:165-170`).

### Notes
One free-text `note` per entry (`types.ts:29`). It is behind a "Note?" toggle that starts closed (`EntryForm.tsx:73`, `:673-691`). Notes can be searched in the Timeline and Insights filters (`TimelineView.tsx:30`, `InsightsView.tsx:31-36`).

### Storage
- **Capacitor Preferences** (Android SharedPreferences file "CapacitorStorage", `android/app/src/main/java/com/aradar/vibecheck/MainActivity.java:26-28`). This is not SQLite or IndexedDB.
- **All entries live in one JSON string** under `vibecheck_entries` (`constants.ts:190`). Each save reads the whole array, swaps or prepends one entry, re-sorts and writes it all back (`storage.ts:34-52`). Tags, categories, custom moods, label overrides and usage counts each have their own key (`services/storageKeys.ts:1-13`, `storage.ts:21`).
- Some settings use WebView `localStorage` instead: dark mode (`App.tsx:130-137`, `:277-287`) and medication reminder times (`components/SettingsView.tsx:106`, `:252`, `:260`).
- **Schema versioning and migrations:** not found (grepped `migrat`, `schemaVersion`). The only version is the backup's `version: '1.0'` (`services/driveBackupService.ts:39`). Restore checks that the field exists, not what it says (`storage.ts:375`).

## 3. Flows with tap counts

### (a) Log a bad day: mood + 2 symptoms + a note + a tag
The two symptoms are taken to be existing tags (Brain Fog, Poor Sleep), because that is what the app offers (section 2).

| # | Screen / component | Action | Ref |
|---|---|---|---|
| 1 | Home, "How are you?" card (or the centre + in the nav) | open the form | `App.tsx:420-432`, `:549-554` |
| 2 | EntryForm step 1 | tap Rough. **The entry is saved now.** | `EntryForm.tsx:338-344`, `:177-189` |
| 3-4 | EntryForm step 2, "What's happening?" | tap Brain Fog, tap Poor Sleep | `:630-645` |
| 5 | same | tap a context tag, e.g. Work/School | same |
| 6-7 | "Note?" | open the toggle, focus the text area, type | `:674-691` |
| 8 | bottom bar | Done! | `:707-712` |

**About 8 taps.** Add 1 if a tag sits below the first 15 (the "+ N" button, `:647-654`). A new tag costs 2 more: "+ New", then Add (`:665-668`, `:609-611`).

### (b) Add one more entry mid-day
Once today has an entry, the Home card turns into the Dopamine Menu (`App.tsx:420-439`), so the way in is the **centre +** (`App.tsx:549-554`). Tap + (1), tap a mood (2): the entry is saved with its own timestamp (`EntryForm.tsx:177-189`). Done! (3) closes the form. **2 taps to save, 3 to close.** Tags or a note add the taps from (a).

### (c) Correct yesterday
- **Entry is among the newest 5 on Home** (`App.tsx:470`): tap the card (1). The form opens on step 2 in edit mode (`EntryForm.tsx:37`, `TimelineView.tsx:182-183`). Change a tag or the note (2), then Done! (3). **3 taps.**
- **Older entry:** Calendar tab (1), tap the day (2), tap the entry (3), change it (4), Done! (5) (`CalendarView.tsx:205-207`, `:116-118`). **5 taps.**
- **Yesterday missing (backfill):** Calendar (1), day (2), "Add Entry" (3), mood (4), saved. The time is **00:00**, because the chosen day is built as midnight (`CalendarView.tsx:207`) and the form takes its time from it (`EntryForm.tsx:61`, `:71`). Setting a real time costs 3 more: the date chip, the time field and its picker (`:445-452`, `:481-489`). **4-8 taps.**
- **Data-loss trap when changing the mood of an existing entry.** `hasSavedRef` starts false even in edit mode (`EntryForm.tsx:77`). Tapping the mood card goes back to step 1 (`:494-495`), and picking a mood runs the first-save branch: it saves **tags `[]` and note `''`** under the entry's ID (`:177-183`, `App.tsx:318-334`). Done! puts them back, because the form state still holds them (`EntryForm.tsx:276-284`). Leaving with Android Back instead closes the form (`App.tsx:204-207`), so the tags and note stay erased **(inference from the code path)**.
- **Undo:** not found (grepped `undo`; the only hit is the "cannot be undone" wording at `SettingsView.tsx:134`). Deleting an entry asks `confirm()` once (`EntryForm.tsx:297-305`). **Same as yesterday / copy:** not found (grepped `yesterday`, `same as`, `copy`, `duplicate`; `yesterday` appears only in the unused `utils/dateUtils.ts`).

### (d) See the last two weeks
Insights tab (1). The first card is "Mood History (Last 2 Weeks)" (`InsightsView.tsx:58-61`), a line of **daily averages** with a red ring on days tagged "meds-skipped" (`:88-114`, `:481-485`). **1 tap.** To see the entries themselves: Calendar (1), plus 1 for the previous month when the range crosses months (`CalendarView.tsx:28-31`), then 1 per day. Or open the Timeline filter's date range: Home, then the filter icon and two date fields (`TimelineView.tsx:53-60`, `:119-134`). **About 4-6 taps.** The Home Timeline shows only 5 entries (`App.tsx:470`).

## 4. Insights, correlations, charts

All of it is in `components/InsightsView.tsx`. It works on entries after the "Pro Filter" (mood, tags, keyword, date range, `:25-42`). Each entry counts once; there is no weighting by day. Charts are drawn with Recharts (`:6`).

| Card | Method | Minimum data | Wording |
|---|---|---|---|
| Mood History | Last 14 days, or the filter range (`:58-76`). If the span is over 5 days, entries are averaged per calendar day; otherwise each entry is drawn (`:88-124`). | none | help text "Look for rises, dips, and patterns" (`:464`) |
| Activities & Mood | Mean mood per tag ID across the entries that carry it (`:130-153`). Top 3 are "Feels Good", bottom 3 "Feels Rough" (`:523-546`). | a tag needs **≥ 2 uses** (`:151`) | "Log more tags to see what helps!" when empty (`:518`) |
| Tags & Mood Heatmap | Per tag, counts of entries at each mood 1-5; cell opacity = share (`:156-181`, `:596-600`). Tapping a row: **> 50 %** of entries at 1-2 gives the "Rough/Meh" line, > 50 % at 4-5 gives "Good/Great", otherwise "varied" (`:647-658`). | ≥ 2 uses (`:179`) | "you feel Rough/Meh {n}% of the time" (`:656`) |
| Time of Day | Mean mood in 4 fixed buckets: 5-11, 11-17, 17-22, 22-5 (`:192-219`). Labelled Best ≥ 4, Rough ≤ 2.5 (`:715`). | none | |
| Weekly Pattern | Mean mood per weekday, Monday first (`:223-246`) | none | "Monday blues" help text (`:738`) |
| Wellness Impact | Mean mood by `symptoms.sleep` and `symptoms.tookMedication` (`:249-283`) | shown only if any exist (`:768`), which never happens from the UI (section 2) | |

- **Not computed:** a baseline comparison (a tag's mean against the overall mean), tag-with-tag co-occurrence, lagged effects (yesterday's tag against today's mood), Pearson or any other correlation coefficient, and any significance or confidence check. Grepped `pearson` and `correlat`; the only hit is the comment "Activities (Tags) Correlation" (`:129`).
- **(inference)** With 3-5 qualifying tags, the "Feels Good" and "Feels Rough" lists overlap, because both slice the same sorted array (`:525`, `:542`).
- Two tag IDs are hard-coded into the charts: `meds-skipped` (`:105`, `:122`). If the user hides that tag, the ring stops being reachable.
- The heatmap wording "Rough/Meh" does not match the picker label "Not Great" for mood 2 (`constants.ts:18`).

## 5. Customisation

- **Scales:** you cannot add, hide or rename the mood scale or the 1-5 labels (constants only, `constants.ts:4-45`). Custom moods add named feelings that map onto the same 1-5 axis (section 2). Energy, focus and the other `SymptomsData` fields have no UI.
- **Tags and categories:** add, rename, hide/unhide, recategorise; add or delete custom categories (default ones cannot be deleted) (`TagManagerModal.tsx:50-61`, `:132-160`, `:453-458`). There is also a switch for showing category colours on tags (`SettingsView.tsx:119-123`, `:549-556`).
- **Reminders** (`services/notificationService.ts`): up to 6 daily mood times and 6 medication times (`SettingsView.tsx:204-262`). Each repeats daily (`notificationService.ts:150-157`, `:209-216`). Actions are "Log Mood", "Snooze 15m" and "✓ Taken" (`:257-279`).
  - Taken only shows an `alert` and records nothing (`SettingsView.tsx:86-87`).
  - The handler branch for `mood-N` actions (`SettingsView.tsx:82-85`) has no matching registered action (`notificationService.ts:265-266`), so `logMoodFromNotification` (`:286-299`) never runs **(inference)**.
  - On app start only the mood times are rescheduled (`App.tsx:144-146`; the `medTimes` argument at `notificationService.ts:366-381` is never passed).
- **Themes:** Ocean, Midnight, Apricot and Rose, plus dark mode (`App.tsx:222-275`). Settings offers only 3 of the 4 (`SettingsView.tsx:64-69`). A "Show Nav Labels" switch is available, and it reloads the page (`:125-131`).
- **Widget:** one tap opens the entry form (`android/app/src/main/java/com/aradar/vibecheck/MoodWidgetProvider.java:60-78`, `MainActivity.java:23-34`, `App.tsx:151-157`). The streak and last-mood sync lives in the unused `AppContext` (`context/AppContext.tsx:131`), so the widget's data is never refreshed **(inference)**.
- **Extras unrelated to logging:** the Dopamine Menu of activity ideas and the ShadowBox exercise screen (`components/DopamineMenu.tsx:18-35`, `components/ShadowBox.tsx:12-13`).

## 6. Export, backup, privacy

- **Export:** one JSON file `vibecheck-backup-YYYY-MM-DD.json`, written to the cache directory and passed to the share sheet (`services/driveBackupService.ts:63-82`). The content is `{version:'1.0', exportedAt, entries, tags (hidden ones too), settings:{userName, theme, notificationTimes, tagFrequency}}` (`:28-50`).
  - It **leaves out** custom moods, custom categories and medication times (compare `:29-36` with `storageKeys.ts:1-13` and `SettingsView.tsx:252`).
  - CSV and PDF: not found. "Export PDF Report" only shows an alert (`SettingsView.tsx:199-202`). The comment at `storage.ts:288` says "Export / Import functions have been removed".
- **Import:** **replaces** all entries; nothing is merged (`storage.ts:381`). It rebuilds the tag override lists (`:384-405`) and restores some settings (`:408-415`). Then the app reloads (`SettingsView.tsx:175-196`).
- **Auto backup (Android only):** the user picks a folder once with the system folder picker (SAF tree URI with persisted permission, `android/app/src/main/java/com/aradar/vibecheck/BackupFolderPlugin.java:51-63`). After every save or delete, the app writes the same JSON (`services/autoBackup.ts:65-95`, called from `App.tsx:337`, `:361`).
  - Writes run one at a time through a queue (`autoBackup.ts:98-105`).
  - There is one file per local day; the newest 7 `vibecheck-auto-*` files are kept and only the app's own files are deleted (`services/autoBackupRules.ts:5-20`).
  - The last error is stored and can be shown (`autoBackup.ts:23-28`, `:86-90`).
- **Encryption:** not found (grepped `encrypt`). Backups are plain JSON, and so is the stored data.
- **Delete All Data** removes only 5 keys: entries, custom tags, hidden defaults, notification times, theme (`storage.ts:61-67`). Custom moods, categories, label overrides, tag usage counts, the user name, auto-backup state and medication times all stay **(inference from the key list)**.
- **Manifest** (`android/app/src/main/AndroidManifest.xml`):
  - Permissions: `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `SCHEDULE_EXACT_ALARM` (`:40-44`).
  - **No `INTERNET`** in the app manifest. Whether a Capacitor library merges one in could not be checked, because `node_modules` is absent.
  - `android:allowBackup="true"` with no `fullBackupContent` or `dataExtractionRules` (`:4`), so Android's own cloud backup may copy the SharedPreferences **(inference)**. This conflicts with "No accounts, no cloud" (`OnboardingScreen.tsx:90`).
- **Analytics and network calls:** not found (grepped `fetch(`, `XMLHttpRequest`, `analytics`, `firebase`, `sentry`). The icon font ships with the app (`public/material-symbols-outlined.woff2`). The only outbound links are Ko-fi, GitHub and the author's site, as plain `<a>` links (`SettingsView.tsx:280`, `:295-312`).

## 7. Code qualities

- **Tests:** 3 files and 20 `test()` cases, run with `node --test` (`package.json` script `test`):
  - backup file naming and retention (`services/autoBackupRules.test.ts:6-21`);
  - the emoji lookup (`services/widgetService.test.ts:9-22`);
  - two date helpers the app does not use (`utils/dateUtils.test.ts:6-87`).
  - Nothing tests storage, the entry form, insights maths or restore. The Android test files are the template examples (`android/app/src/test/.../ExampleUnitTest.java`).
- **Structure:** a flat React app with large components: `InsightsView.tsx` 814 lines, `EntryForm.tsx` 721, `SettingsView.tsx` 704, `App.tsx` 584 (`wc -l`).
  - Storage calls go straight from components; the reducer and context layer was written but never connected (see Dead code).
  - Settings are split across Preferences and `localStorage`.
  - `alert`/`confirm` serve as the dialog system (`EntryForm.tsx:302`, `SettingsView.tsx:134`, `:185-192`).
  - The filter UI is copied between Timeline and Insights (`TimelineView.tsx:16-36`, `InsightsView.tsx:16-42`).
  - The 4 themes are defined twice (`App.tsx:229-255`, `OnboardingScreen.tsx:28-33`).
- **Well done:** the auto-backup module. Pure rules are split out for testing (`autoBackupRules.ts:1-2`), there is a write queue, cleanup is best effort, and a run checks whether the folder changed meanwhile (`autoBackup.ts:76-94`).
- **Portable as ideas:** most of the UX. Save-on-first-tap, the details step, tags sorted by use, fuzzy tag dedupe, per-tag mood heatmap, day/time buckets, the daily auto backup to a folder the user picks. The code itself is not worth porting to Kotlin.

## 8. Against Flare Enough's weaknesses

| Flare Enough weakness | Vibecheck | Ref |
|---|---|---|
| One value per tracker per day, no time | **Fixed.** Many timestamped entries per day; charts average per day | `types.ts:26`, `App.tsx:318`, `CalendarView.tsx:196` |
| Past entries take labels from the current tracker | **Same flaw.** Entries keep tag IDs, and labels are looked up when drawn. Hidden tags disappear from history cards | `TimelineView.tsx:216-222`, `storage.ts:208-221` |
| Every scale "higher = worse" | **Opposite fixed direction.** One scale, higher = better. No per-scale direction, because there is only one axis | `types.ts:1-7`, `InsightsView.tsx:152` |
| "Same as yesterday" overwrites, no undo | **No such feature, and no undo.** The edit-mode mood change can still wipe tags and the note without asking | `EntryForm.tsx:77`, `:177-183` |
| No correlations | **Basic.** Mean mood per tag (≥ 2 uses), tag × mood distribution, time of day, weekday. No baseline, lag or coefficient | `InsightsView.tsx:130-246` |

## 9. What MedicAll should take vs avoid

**Take**
- One entry per event with a real timestamp, several per day (`types.ts:24-32`, `App.tsx:318`). This fits a free-text + tags log.
- Save on the first meaningful tap, then let details follow ("Logged: HH:MM", `EntryForm.tsx:177-189`, `:696-704`). Low friction for bad days. MedicAll's version would be: save when the text or the first tag is entered.
- Notes and extra fields closed by default behind toggles (`EntryForm.tsx:73`, `:674-691`). This matches "every other field optional and switchable".
- Fuzzy "use existing tag" while typing, which also revives hidden tags (`EntryForm.tsx:236-261`, `TagManagerModal.tsx:72-92`). This prevents duplicate tags, which matters on the consistency axis.
- Hide instead of delete for tags (`storage.ts:149-169`), but keep showing hidden tags on past entries (see Avoid).
- Heatmap and plain-language takeaway with a stated threshold (`InsightsView.tsx:647-658`). Easy to explain. Add the count of entries behind each statement, as the tag list already does (`:529`).
- Minimum-use gate before a tag gets a verdict (`InsightsView.tsx:151`, `:179`). Raise it above 2 and say so in the text.
- Auto backup to a folder the user picks, one file per day, keep N, delete only the app's own files, with a write queue and pure tested rules (`autoBackup.ts:65-105`, `autoBackupRules.ts:5-20`).
- Filters by tag, keyword and date range over the history (`TimelineView.tsx:26-36`).

**Avoid**
- Labels resolved at draw time from mutable tag records (`TimelineView.tsx:216-222`). Store the label shown at entry time, or keep tag-version history.
- Hidden tags vanishing from past entries (`TimelineView.tsx:217-222`) and IDs showing in charts (`InsightsView.tsx:143-146`).
- One fixed "higher = better" axis wired into colours and sorting (`InsightsView.tsx:152`, `:298-304`). Give each scale a direction.
- A data model with fields no screen writes (`types.ts:34-41` with `EntryForm.tsx:281`), and edits that rebuild the whole record and drop unknown fields (`App.tsx:320-328`).
- The edit flow that overwrites tags and notes with empties before the user confirms (`EntryForm.tsx:77`, `:177-183`). Every write should be a patch, with undo.
- Usage-sorted tag lists, which move tags around between sessions (`EntryForm.tsx:141-148`). This breaks "same place, same size". If any ordering by use is kept, it should be pinned or stable.
- Backfill timestamps at 00:00 from the calendar (`CalendarView.tsx:207`, `EntryForm.tsx:71`). Default to a chosen time, or mark the entry "time unknown".
- The whole history in one JSON string, rewritten on every save, with no schema version (`storage.ts:34-52`, `:375`). Use a database with migrations.
- Partial export and partial "Delete All Data" (`driveBackupService.ts:28-50`, `storage.ts:61-67`). Both must cover every store, and import should merge or at least warn before replacing (`storage.ts:381`).
- `allowBackup="true"` while promising "no cloud" (`AndroidManifest.xml:4`, `OnboardingScreen.tsx:90`).
- A medication "Taken" action that records nothing (`SettingsView.tsx:86-87`), and reminders not rescheduled after restart (`App.tsx:146`). For a medical hub, every reminder action must leave a record.
- Insights without a baseline or lag (`InsightsView.tsx:130-153`). Compare against the overall mean and the next day before calling a tag good or bad.

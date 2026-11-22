# Six more apps: records, profiles, calendar, logging

Research notes, as of 2026-11-12. These are not a verdict, and nothing in them is scored.

**The apps and where they are:** sources were shallow-cloned into `_ref/` (kept out of git). APKs are in `_ref/apk/`.

| App | Licence | Stack |
|---|---|---|
| HealthTrack | MIT | TypeScript web app |
| Fasten Health | GPL-3.0 | Go + FHIR; only the backend models were checked out |
| MediLog | GPL-3.0 | Flutter |
| RxDroid | GPLv3 for the code, a Creative Commons licence for the artwork | Java Android |
| Medical Calendarlog | GPL-3.0 | Kotlin Android |
| Vibecheck | GPL-3.0 | React + Capacitor |

**The code maps,** each with file:line citations:
- `models-records.md`: HealthTrack and Fasten.
- `models-profiles-calendar.md`: MediLog, RxDroid and Calendarlog.
- `flows-vibecheck.md`: Vibecheck.

**Spot-checks:** I re-checked one claim from each map myself:
- HealthTrack's lab value is `real(...).notNull()`.
- MediLog never turns on foreign keys.
- Calendarlog picks a calendar whose name contains "med" or "log".
- The Vibecheck data-loss bug happened on the phone.

**Phone walks** on the OnePlus 6T for MediLog 1.0.0 and Vibecheck 1.0.3. Each tap is logged in `_ref/walk/taps.log` (rows starting `ML-` and `VC-`), with screenshots in `_ref/walk/shots/`. No reminders were set, the OCR camera wasn't used, and both apps' data was cleared afterwards. The apps stay installed.

## The open questions, and what the six apps say

### #26: how flexible a result should be (HealthTrack, Fasten)

**What the apps do:**
- **Most flexible:** HealthTrack's newer "lab fact". A result can be:
  - a number;
  - a bound such as "<5";
  - text such as "Negative";
  - "not performed".

  It also keeps the text and unit exactly as the lab printed them, a reference range with its own unit, and the lab's H/L flag separate from the app's own.
- **HealthTrack's older table** only accepts numbers, so its parser drops "Negative".
- **Fasten** stores the full FHIR record and treats two values as the same analyte when their LOINC codes match.
- **Neither app** has a list of analytes with synonyms, or converts units. HealthTrack groups its charts by the exact test name, so "Emoglobina" and "Hb" would become two separate lines.

**Minimal model they point to, in plain words:**
- **An exam session:** a person (human or pet), the collection date, an optional report date, the lab and the doctor.
- **Attachments of any file type**, many per session.
- **Result lines,** each holding:
  - one analyte;
  - one of the four outcome kinds;
  - the printed text and unit;
  - the range;
  - both flags;
  - where it came from (typed, or OCR from a given attachment and page).
- **An analyte list the user can edit:** a name, a preferred unit, synonyms, conversion factors, and an optional LOINC code.
- **Trends** only ever plot one unit per line.
- **OCR** produces draft lines that you confirm.

### Profiles (MediLog, RxDroid)

- **MediLog's profile** holds a name, an age in years, sex and an emoji. Every record has a profile id. But foreign keys are never switched on, and no screen edits or deletes a profile.
- **On the phone:**
  - First profile: 6 taps. The keyboard covered the sex choice and Save, so it had to be closed first.
  - Switching profile: 3 taps from Home (⋮ → Switch Profile → the person).
  - The period tracker showed for an 8-year-old, because it's hidden by sex only.
- **RxDroid's support for several patients exists in its database but is switched off in the app.** Its single notification lumps every patient together as counts ("2 due, 1 missed"), never saying who.
- **What to take:**
  - a lasting id on every record, with foreign keys enforced;
  - a birth date in place of an age;
  - modules switched on per profile;
  - every notification starting with the person's name.

### OCR and attachments (MediLog)

- MediLog uses Google ML Kit, which is closed-source and contradicts its "no proprietary dependencies" claim. The scan becomes one block of text added to the notes, and the photo itself is thrown away.
- On the phone, a report is one test name, a date, one free-text result field and notes. There's no unit, range or several values per report.
- Its ~100 MB medicine database is not in the repo, and it's India-specific.
- MediLog has no encryption.

### Two-way calendar (#22, Medical Calendarlog)

**What Calendarlog does:**
- It never creates a calendar of its own. It borrows an existing writable one whose name contains "med" or "log".
- It recognises its own events by title alone, and writes only the basic columns.
- It picks up edits made in other calendar apps by watching the system calendar, but it misses repeating events and rows marked as deleted.
- If the calendar is removed, it doesn't notice.

**For MedicAll:**
- MedicAll's own local calendar, with MedicAll's data as the master copy.
- Mark each event in more than one way, so one edit in Fossify can't remove every marker.
- Detect a Fossify edit by comparing a stored fingerprint of the event.
- Treat a deletion made in Fossify as a cancellation the user can undo.
- Warn the user if the calendar disappears.

### Daily log (Vibecheck, compared with Flare Enough)

**On the phone:**

| Task | Taps | Notes |
|---|---|---|
| First run | 6 | Includes a name, a theme and a reminder offer. The Next button sat under the keyboard, so the keyboard's Enter was needed |
| Bad day: mood + 2 tags + a note | 6 | Mood, two chips, note, Done |
| Another entry later the same day | 3 | Kept as a separate entry with its own time |
| Move an entry to yesterday | 6 | The entry, the date, the date field, the day, SET, Done |
| Last two weeks | 1 | Insights, a chart of daily averages. But the tab icons have no labels, and Insights was the third icon I tried |

**Better than Flare Enough:**
- several timestamped entries a day;
- mood with a direction (higher is better);
- simple insights: average mood per tag, a tag × mood heatmap, time of day, day of week.

**Worse:**
- **Changing an entry's mood and pressing Back wiped its note and both tags.** Confirmed on the phone. There's no undo anywhere.
- **Symptoms are only tags.** The energy, focus and sleep fields exist in the code, but nothing writes them.
- **Renaming a tag rewrites history**, the same flaw as in Flare Enough.
- **The time shows as 22:18 on the entry and "10:18 PM" on the timeline.**
- **The selected chips grow and shift the others** to new rows.
- **Screen readers hear icon names** such as "sentiment_very_dissatisfied".
- **No encryption,** and `allowBackup` is on.

## What this adds to the design, as open points (not decisions)

1. The result model above, with an analyte list you can edit. This answers most of #26. Units, synonyms and LOINC are still to settle with you.
2. Calendar: MedicAll's own calendar, with MedicAll's copy as the master.
3. Profiles: a birth date, modules per profile, the person's name first in every notification.
4. Daily log: timestamped entries; a direction for every scale; labels kept with each entry, so a rename doesn't rewrite history; undo for every change.
5. OCR produces draft result lines that you confirm, and the scanned image is kept as an attachment.
6. Insights start with Vibecheck-style averages, shown "as data" as in Flare Enough. Correlation wording (#30) is still open.

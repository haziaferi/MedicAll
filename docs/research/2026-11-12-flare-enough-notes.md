# Flare Enough: notes for MedicAll's daily log

Research notes, as of 2026-11-12. These are not a verdict, and nothing in them is scored.

**Studied:** Flare Enough 0.1.5 (GPL-3.0, `Downloads/Builds/FlareEnough-main`).

**Sources:**
- The code map in `flows-flareenough.md`, with file:line citations. I re-checked three of its claims myself: rename/delete has no caller, nothing writes MISSED, and the shading formula.
- A walk on the OnePlus 6T (Android 11, en-US) using the side-by-side `preview` build (`club.hiraeth.flareenough.test`). Each tap is logged in `_ref/walk/taps.log` (rows starting `FE-`), with screenshots in `_ref/walk/shots/fe-*.png`.

**Scope:** the symptom and daily-log side only. Two medication behaviours (missed doses, clock changes) were checked in the code only. Testing them on the phone would have meant setting alarms at night.

## Measured on the 6T

| Task | Taps | Notes |
|---|---|---|
| First run to first entry | 3 | No intro screens. Today → "How are you?" → "Add these" (six starter trackers, pre-ticked) → one level. The first tap on the card did nothing right after launch and had to be repeated |
| A. Bad day: flare + 2 symptoms + a note | 6 + 3 scrolls | Every answer saves the moment you tap it, with no Save button. The note's Add button stayed above the keyboard |
| B. One more symptom in the middle of the day | 2 + 3 scrolls | The Log tab reopened where it was left (scrolled down at the notes), so getting to Mood meant scrolling back up. The new value overwrites the earlier one, and no time of day is stored |
| C. Correct yesterday | 5 | History, the day, Edit, the value, Back. Saves on tap |
| D. See the last two weeks | 1 | The month calendar. Colour is the only signal: no legend, no numbers, no screen-reader description on the cells |

## What the walk found that the code map didn't

- **The scale words are wrong for some trackers.** Mood and Sleep quality use the pain words, "1 None … 5 Severe". The calendar also counts a high score as a heavy day for every tracker (`HistoryViewModel.kt:159-163`), so a good night's sleep shades the day as bad.
- **An entry corrected later shows the time it was corrected.** After editing yesterday at 21:56, yesterday's timeline said "Pain 2: Mild · 21:56".
- **The selected day and a flare day are both shown with a border**, told apart only by colour (green for selected, orange for flare).
- **The date is written "11/11/26"**, the short US form, which is ambiguous for most days.
- **"Moderate" wraps to "Moderat / e"** at the default font size on a 1080-wide screen.

## What's worth taking

- **Tracker types:** a 5-level scale, yes/no, a duration band, or a number. Each tracker is user-defined. This is a good default set for "free text + tags, all other fields optional" (#29).
- **Answers save on tap.** That meets design rule 3 (one Save, never under the keyboard) by needing no Save at all.
- **The flare flag for the day**, and a starter list you accept with one tap.
- **The day timeline that shows doses, symptoms and notes "as data, with no interpretation".** One possible tone for #30.
- **Editing any past day from History.**
- **The body map as a list of named parts**, which screen readers can read.
- **The period tracker as an option that records only, with no predictions.**
- **Missed doses with no red styling and no guilt.** The intent is worth keeping, but the code never records a dose as missed (see below).

## What to avoid

- **One value per tracker per day, with no time stored.** A mid-day entry overwrites the morning one, which hides how a symptom changes within the day. Correlation with dose times needs timestamps.
- **Past entries take their labels from the tracker as it is now.** Renaming a tracker, or changing a duration list, would rewrite history. This is the same plan-history gap as in the medication comparison, on the logging side.
- **"Same as yesterday" overwrites today's values with no undo.** It copies only the tracker values, not the flare flag, notes or tags.
- **Every tracker is scored as "higher is worse".** Each one needs a direction: higher is worse, or higher is better.
- **The README promises rename and remove, but the app can't do either:** `updateTracker` and `deleteTracker` have no callers.
- **Missed doses are never recorded:** nothing writes `DoseStatus.MISSED`. An unanswered dose stays "Due" and then simply drops out of History.
- **The backup is a plain, unencrypted copy of the SQLite file, without the settings.** The CSV export has no guard against formula injection.
- **Tests:** 5 unit tests and 3 instrumented in the app, plus 22 in the `:schedule` module. None of them covers logging.

## Clock changes (code only)

- **UK clock changes:** handled, with 6 tests in `DstTest.kt`.
- **Time-zone changes:** alarms are rebuilt, but no test covers it.
- **Two risks the code map inferred:**
  - Doses logged before a zone change could show as "Due" again, because each dose is matched to its slot by the exact instant.
  - A screen left open past midnight keeps writing to the previous day.

## Phone state after the walk

- The test app's data was cleared (`pm clear`), and it has no alarms.
- The app stays installed, as survey apps do.
- The phone is back on the launcher.

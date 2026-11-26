# 2026-11-25 · Visual identity: Albarello (SPEC:R93, SPEC:R114 to SPEC:R122, SPEC:R53)

**Decision (owner, 2026-11-25, completed 2026-11-26):** MedicAll's look is Albarello, taken from the
Italian maiolica pharmacy jar. Its rules are R93 and R114 to R122, with R53 reworded; the agreed mock-up is `docs/design/identity.png`, the launcher
source is `docs/design/launcher.svg` (with the owner's Affinity file `launcher.af`), and the custom icons
are in `docs/design/icons`. All names and medicines in the images are invented.

## 1. How it was chosen

The visual-craft skill led the process (R93). The owner set four scoring axes before any option was
scored: calm, distinctive, legible at a glance, trustworthy. The owner asked that palettes, derived from
the brief, not feel sterile, and that light and dark both ship with an in-app switch.

| Direction | Source | Calm | Distinctive | Legible | Trustworthy | Outcome |
|---|---|---|---|---|---|---|
| Albarello | maiolica pharmacy jar | 4 | 4 | 5 | 5 | chosen as the design |
| Capsule | blister pack | 3 | 5 | 4 | 4 | kept as a palette, colours reworked |
| Record card | paper medical file | 5 | 3 | 3 | 4 | layout too angular and condensed; its colours kept as a palette |
| Drawing wall | paediatric ward drawings | 4 | 5 | 4 | 3 | rejected as infantilising for adults |

Palettes from the owner's other apps were ruled out first, so none of the directions reuses one (R93).

## 2. What changed along the way

- **The band with one profile.** Most phones hold one profile (R14), so the band carries the screen's
  title and takes the profile's name only once a second profile exists (R116).
- **Palettes per profile.** Four palettes, chosen per profile on the device, with Albarello as the
  default (R114).
- **Third colours from the palette, not the screen.** The owner rejected picking Capsule's taken colour by
  eye on one screen. Each palette's taken colour was searched with color-optimization-core against its
  own harmony, with the other roles pinned, then checked for colour-vision separation. Capsule took the
  search's rose; Murano's marigold became glass blue; Albarello's copper green stayed, because its only
  harmonious alternative duplicated Capsule's rose.
- **Dark mode.** Two dark taken colours were too dim (3.0 and 4.3:1 on the card); they were lifted to
  5:1, the most the rose allows before it nears the apricot rule for deutan vision. Dark grounds stay
  near-black; color-optimization-core's phone checks for OLED smear and halation pass on the values
  (computed, not seen on a phone).
- **Lock screen.** The owner asked for the system keyboard instead of an in-app keypad (R119).
- **Icons.** A custom batch was drawn and compared with Material Rounded, Phosphor and Lucide. The owner
  took Phosphor for generic controls and the custom set for MedicAll's own objects; Phosphor's pill
  duplicated the custom capsule, so medicine became the plain jar (R117).
- **Launcher.** Three rounds: abstract marks were rejected; the owner then supplied references (an alarm
  clock with capsules, a clipboard with a cross). The tile became the clipboard so the objects could be
  drawn larger. The owner redrew it in Affinity with outlines and details; the lines beside the cross were
  shortened so the cross survives at 24 px, and the mark was scaled into the 66 dp safe circle and centred
  by measurement. Of 2, 3 and 4.5 dp outlines the owner took 2 dp (R118).

## 3. Type

Fraunces 500 for titles, Atkinson Hyperlegible Next for everything else. The type search proposed a
1.333 ratio, whose top step reaches 70 sp on a phone; the owner took 1.25 from a 17 sp body (R115).
Atkinson Hyperlegible Next 2.001 has `tnum`, so times and doses use tabular figures.

## 4. Gaps closed before merging (2026-11-26)

A pre-merge read of the rules against the rest of the spec found four gaps; the owner settled each.

- **Status colours.** R53 let a scale's colour follow its direction, but the palettes ban red and have no
  good/bad colours. The owner asked for scale colours that show high and low by intensity instead.
  Each palette and mode got three (R121), searched against the palette's own main, accent and taken
  colours with color-optimization-core's maths: no red or green hue, at least ΔE2000 10 apart in normal,
  deutan and protan vision, one shared lightness per set so lightness can carry the value, and hues at
  least 40 degrees apart. Four colours broke the floors in three palettes, so three hold everywhere and
  charts add marker shapes past three trackers. Murano dark was searched twice more at the owner's
  request, first to separate an aqua from a cyan, then to move the aqua off green. Board:
  `docs/design/scales.png`. R53 now says colour shows only how high a step is.
- **Control outlines.** The line colour is 1.3 to 1.4:1 on the card, too faint for a text field or a
  switch's off track; those take the muted text colour (R114).
- **Today in the week strip.** The accent underline alone is 2.2:1 in Capsule light, so today's label
  is also set at weight 600 (R120).
- **Light or dark.** One setting for the whole device, not per profile (R114).

A second, independent read of the whole spec against these rules found more; the owner settled these:

- **State colours (R122).** No rule coloured focus, switch on, disabled, links, secondary buttons or
  errors, and Compose fills unset roles with Material's purple and red. The owner allowed red only for
  actual errors and asked for every other state colour to be derived. Five roles per palette and mode
  were searched against the whole palette; the focus ring is drawn 2 dp off its control so it is judged
  against the card. In Capsule dark and Murano dark a 7:1 red landed on the palette's own rose and pink,
  so the owner took a 4.5:1 brick red there. Every Material 3 role is mapped. Board: `docs/design/states.png`.
- **Screens with no profile.** Lock, first run, passphrase and the profile list show a device palette the
  person chooses, Albarello by default (R114).
- **The band.** The wordmark row above the band was dropped; the band's sizes, the screens without one and
  "shown" profiles are now in R116. The mock-up was re-rendered to match the rules: no wordmark row,
  outlines in the muted text colour, the state colours and the strip's legend.
- **Wording.** Today's label at weight 600 (R115 caps weight at 600), the scale mixing in OKLab, the hue
  floor's rounding, the OLED claim marked as computed, and R115's check named on the OnePlus 9 Pro.

## 5. Not checked, and left for the phase that builds each screen

Everything was rendered in a headless browser at 1x, not on the phones of R09: the launcher masks, the
icons at real pixel densities, and the reading screens at 200% font size are checked when phase 2b builds
them (R100). Left open, to settle before the screen that needs it:

- charts: whether a tracker keeps one scale colour everywhere or takes one by chart order, marker sizes,
  lines or dot rows, and more than twelve trackers; low scale steps (1.5 to 2.4:1) have no floor;
- Pain's 0 to 10 has eleven steps, against the glossary's "2 to 10 ordered steps" (an older conflict);
- the week strip: a "late" state (the glossary has a late window), as-needed doses, pill size, and its fit
  at 320 dp;
- colours for lab range bands, app flags, out-of-range points and PDF charts (R44, R46): any new colour
  needs an owner ruling in R93's terms;
- the Android notification icon and the themed (monochrome) launcher layer;
- licence notices for the bundled fonts (OFL) and Phosphor (MIT), and tritan vision for every colour set.

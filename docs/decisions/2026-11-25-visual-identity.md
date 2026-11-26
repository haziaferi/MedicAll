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
- **Launcher.** (Redrawn on 2026-11-26; see section 4.) Three rounds: abstract marks were rejected; the owner then supplied references (an alarm
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

A third read, of what Compose and Android draw on their own, found more; the owner settled these:

- **Role mapping (R122).** A snackbar's action (inversePrimary, the link on the text colour) measured 1.3 to
  1.9:1, and text on the accent 1.4 to 1.8:1 in dark mode. inversePrimary now takes the other mode's link
  (6.5 to 9.9:1), and onTertiary whichever of text and card reads better.
- **Selected states and the disabled fill (R122).** Containers in the card colour hid every selected chip,
  tab and navigation item. Three containers per palette and mode were searched against the whole palette
  (text on them at least 7:1, at least 10 ΔE2000 from card, ground, taken and error), taking two more runs:
  the first reached its floor through chroma (a neon mint in Murano light), the second turned some
  containers grey. The owner asked for a disabled fill that does not almost disappear; it is now the card's
  hue, 6 to 10 ΔE2000 from the card (5.4 in Capsule dark). Board: `docs/design/containers.png`.
- **Type roles (R115).** Compose draws the eight Material 3 type roles R115 did not set in Roboto; all
  fifteen now map onto R115's seven.
- **System surfaces (R114, R116, R117).** The band runs under the status bar with icons in the on-main
  colour; the profile list and device settings carry only their title, in the device palette; icons take
  the colour of the text beside them. Board: `docs/design/system-and-type.png`. A check after merging found
  R116 still edging the band above and below; the owner dropped the upper rule, as on that board, and the
  mock-up now shows the band under the status bar and the redrawn launcher.
- **Launcher and splash (R118).** The owner redrew the icon in Affinity: a lighter tile (#3C5DB9, so the ink
  outline shows at 2.34:1 against 1.69:1 on the band's cobalt), a green cross, 1.5 dp outlines, and the
  drawing centred by measurement and lifted 1 dp. Its capsule repeated the tile and the cross (0 ΔE2000), so
  four colourings were measured; the owner took red and cyan, the boldest, as the launcher's one exception
  to R114's red rule (worst pair red against saffron, 13.0 ΔE2000 in deutan vision). The splash shows the
  tile-less foreground on #3C5DB9 in light and dark, so a dark-mode user is not dazzled.
- **Launcher colours and R93.** Building the Compose theme found that R93 banned every colour literal
  outside the theme, while R118's tile, capsule and splash colours can only be literals in Android's
  resources, drawn before the app's theme exists. The owner exempted the launcher icon and splash screen
  in R93, as R114 already exempts the launcher's red.
- **What red is (R114).** R114 kept red for the error colour without defining it, and by R121's scale
  band (hue 345 to 45) Capsule's taken colour (3 to 5) and Murano's accent (352 to 354) were red. The
  owner defined red as R122's error band, OKLCh hue 15 to 40 at chroma above 0.04: only the eight error
  colours sit there, so the rose and pink stay, and R121 keeps its wider band for scale colours.

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

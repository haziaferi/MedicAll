# 2026-11-25 · Visual identity: Albarello (SPEC:R93, SPEC:R114 to SPEC:R120)

**Decision (owner, 2026-11-25):** MedicAll's look is Albarello, taken from the Italian maiolica pharmacy
jar. Its rules are R93 and R114 to R120; the agreed mock-up is `docs/design/identity.png`, the launcher
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
  near-black, which the phone checks for OLED smear and halation pass.
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

## 4. Not checked

Everything was rendered in a headless browser at 1x, not on the phones of R09: the launcher masks, the
icons at real pixel densities, and the reading screens at 200% font size are checked when phase 2b builds
them (R100).

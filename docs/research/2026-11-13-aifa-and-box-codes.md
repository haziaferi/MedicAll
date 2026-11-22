# AIFA medicine data and Italian box codes

Research notes, as of 2026-11-13. These are not a verdict.

## What AIFA publishes

All AIFA medicine data is published as CSV files on the [Liste dei farmaci](https://www.aifa.gov.it/liste-dei-farmaci) page.

**Main medicine list (Anagrafica Farmaci).** [`confezioni_fornitura.csv`](https://drive.aifa.gov.it/farmaci/confezioni_fornitura.csv):
- **Size and updates:** 82 MB, semicolon-separated, updated every day (Last-Modified 2026-11-13 00:02 GMT).
- **Columns,** as read from the file header: `CODICE_AIC; COD_FARMACO; COD_CONFEZIONE; DENOMINAZIONE` (brand) `; DESCRIZIONE` (pack: strength, form, count) `; CODICE_DITTA; RAGIONE_SOCIALE` (manufacturer) `; STATO_AMMINISTRATIVO; TIPO_PROCEDURA; FORMA; CODICE_ATC; PA_ASSOCIATI` (active ingredients) `; FORNITURA` (prescription class) `; LINK_FI` (patient leaflet) `; LINK_RCP` (summary of product characteristics).
- **One row per pack (AIC):** the data shows brand, manufacturer and pack size at the level the design needs.

**Active ingredients file.** [`PA_confezioni.csv`](https://drive.aifa.gov.it/farmaci/PA_confezioni.csv): `CODICE_AIC; PRINCIPIO_ATTIVO; QUANTITA; UNITA_MISURA`.
- **The quantity is often empty or "N.D.".** The strength will often have to be parsed from `DESCRIZIONE`, or confirmed by the user.

**ATC codes.** [`atc.csv`](https://drive.aifa.gov.it/farmaci/atc.csv).

**Interchangeable generics.** [`Lista_farmaci_equivalenti.csv`](https://www.aifa.gov.it/documents/20142/825643/Lista_farmaci_equivalenti.csv): the transparency list under Law 178/2002, with reference prices, updated 15/10/2026.

**Shortages.** [`elenco_medicinali_carenti.csv`](https://www.aifa.gov.it/documents/20142/847339/elenco_medicinali_carenti.csv), updated 13/11/2026. Not asked for, but possibly useful later.

**Excipients** aren't a column in any file. They're in the patient leaflet (section 6), which `LINK_FI` points to.

**Licence.** The [AIFA open data page](https://www.aifa.gov.it/en/open-data) says AIFA distributes its data under **CC-BY 4.0**: reuse is allowed, with attribution. That page doesn't name the medicine lists specifically, and the Liste dei farmaci page states no licence. Before bundling, confirm the licence for each dataset.

## Codes on Italian boxes

**AIC code:** 9 digits starting with 0, always printed on the pack. It identifies the exact pack: brand, strength, form and size.

**Bollino ottico** (the sticker in use now):
- It carries the AIC in plain text and as a barcode.
- [Sources](https://assets.publishing.service.gov.uk/government/uploads/system/uploads/attachment_data/file/929226/NEW_bollini_guide-16x16-25-sett-20.pdf) describe the barcode as **Code 32**: the AIC in base 32, printed with Code 39 symbology, and shown as "A" plus the AIC.
- The exact encoding wasn't found in an official spec. Verify it by scanning a real box.
- The bollino holds **no expiry or batch**.

**Data Matrix,** according to [NMVO Italia](https://www.nmvoitalia.it/en/il-sistema-di-verifica/), uses GS1 application identifiers:
- **01:** GTIN, 14 digits;
- **21:** serial number;
- **10:** batch;
- **17:** expiry date, YYMMDD;
- **716:** AIC, 9 digits.

**Transition:** from 9 Feb 2025 to 9 Feb 2027, the bollino and the Data Matrix coexist.
- After that, prescription medicines carry the Data Matrix.
- Over-the-counter medicines keep the bollino ([DottNet](https://www.dottnet.it/articolo/32543463/codice-data-matrix-in-farmacia-dal-2027-le-istruzioni-operative-per-la-transizione-dal-bollino-all-identificativo-univoco-europeo)).
- Boxes already on the market with a bollino may be sold until they expire.

## What it means for the lookup (open points, not decisions)

**Scanning a box:**
- A Data Matrix gives the AIC, the expiry date and the batch.
- A bollino gives the AIC only.

ZXing reads both Data Matrix and Code 39, and it's open source.

**Looking up the AIC** fills in the brand, the manufacturer, the pack description, the active ingredients, the ATC code and the prescription class.

**The bundled list can be much smaller than 82 MB.** Keep only the needed columns of authorised packs and drop the link columns (the links can be rebuilt from the codes). A compressed size of a few MB is likely, but it hasn't been measured.

**Updating the list:**
- **Optional refresh:** download from aifa.gov.it, sending no personal data.
- **Otherwise:** the list bundled with each app release.

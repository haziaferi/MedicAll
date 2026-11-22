# Records and exam-result models: HealthTrack and Fasten

Date: 2026-11-12. Read-only study for MedicAll open question #26, **how flexible should an exam result be?**

Sources (cloned, read only, never built or run):
- `healthtrack/`: HealthTrack (MIT), Next.js + Drizzle on SQLite. Schema in `src/db/schema/`, typed "fidelity" contracts in `src/lib/fidelity/`.
- `fasten/`: Fasten Health on-prem (GPL-3.0), Go + GORM, FHIR R4. Sparse checkout: only `backend/pkg/models` and `backend/pkg/database` (plus `backend/pkg/constants.go`). The frontend is not present, so anything about charts or screens in Fasten is "not in checkout".

Every claim cites `path:line`, with paths relative to each repo root.

---

## 0. One-paragraph answer

HealthTrack has **two lab models side by side**. The first is a legacy, number-only table: one row per analyte, `value REAL NOT NULL`, with qualitative results skipped. The second, newer one is a typed JSON "lab fact" with an outcome union (numeric, censored `<5`, text, cancelled), printed versus derived flags, collection and report dates with stated precision, specimen/report/accession ids, an optional *normalized* quantity kept next to the source unit, and per-fact provenance pointing back to the uploaded document and page. That second model is the shape MedicAll needs. Fasten stores every record as a raw FHIR resource (Observation, DiagnosticReport, DocumentReference, Binary) and extracts search columns from it. Analyte identity comes from coded `system|code` tokens (LOINC). Neither app converts units, and neither keeps an analyte catalogue with synonyms. HealthTrack trend charts group by the exact test-name string.

---

## 1. Result model

### HealthTrack: legacy tables (`lab_visits` + `lab_results`)

**Lab visit**: `healthtrack/src/db/schema/clinical.ts:192-210`
- `visit_date` text, NOT NULL (`clinical.ts:199`). The input schema demands an exact `YYYY-MM-DD` collection date (`src/lib/repos/labs.ts:53`).
- `provider_id` points to `providers` (`clinical.ts:200`). A lab is a provider whose `provider_type` is `'lab'` (`clinical.ts:28-33`).
- `source_pdf_path` text (`clinical.ts:201`), `notes`, `dependent_id`, `created_at`. There is **no `updated_at`** (`clinical.ts:204`).
- There is no separate report date at visit level. Not found: `report_date` / `reported` in `clinical.ts`.

**One analyte value**: `clinical.ts:213-239`
- `panel_name` is free text on each row (`clinical.ts:223`). There is no panel entity: grepped `panel` in `src/db/schema/*.ts` and found only this column.
- `test_name` text NOT NULL (`clinical.ts:224`).
- `value` **REAL NOT NULL** (`clinical.ts:225`), so a value has to be a number.
- `unit` text, nullable (`clinical.ts:226`).
- `reference_range_low` / `_high` real, plus `reference_range_text` (`clinical.ts:227-229`). The app checks that low <= high (`labs.ts:117-123`).
- `flag` is an enum `normal|high|low|critical` (`clinical.ts:230`).
- `loinc_code` text, optional (`clinical.ts:231`).
- No method, no specimen, no per-row date, no `updated_at`. Not found in `clinical.ts:213-239`: `method`, `specimen`, `collected`, `updated_at`.

The legacy AI parser **drops qualitative results**: its prompt says "If the result is non-numeric (e.g. "Negative", "Reactive"), skip that result" (`src/lib/claude/parse-lab.ts:62`).

### HealthTrack: typed "lab fact" (`clinical_records.payload_json`)

Records sit in the generic `clinical_records` table as one JSON payload per fact, with a `kind` (`src/db/schema/fidelity.ts:18-42`). The CHECK constraint lists the allowed kinds: `lab, medication, medication_event, encounter, sleep_study, pap_report, screening, procedure, condition, allergy_review, genetic_report` (`drizzle/0018_web_clinical_records.sql:6`).

The lab payload is `labFactSchema` (`src/lib/fidelity/contracts.ts:42-86`):
- **Outcome** is a discriminated union (`contracts.ts:18-35`):
  - `numeric {value, comparator '='}`
  - `censored {value, comparator < <= > >=}`, which is how "<5" is stored
  - `text {text}`, for qualitative results such as "Negative"
  - `canceled {reason}`
- `raw_result` keeps the exact printed text (`contracts.ts:47`).
- `source_unit` is the unit as printed (`contracts.ts:48`).
- `normalized {value, unit, basis}` is optional and only allowed for numeric or censored outcomes (`contracts.ts:49-52`, `83-86`).
- `reference {low, high, low_comparator, high_comparator, text, unit}`, checked so that low <= high (`contracts.ts:53-67`). The range has **its own unit**.
- `printed_flags`: an array of strings, up to 20, holding exactly what the lab printed (`contracts.ts:68`). `derived_flag` is a separate field, `high|low|within_range|unknown` (`contracts.ts:69`).
- `collection_date` is required and `report_date` optional (`contracts.ts:70-71`). Both are a `ClinicalDate` with `precision` set to unknown, year, month, day or instant, plus `original_text` and `approximate` (`src/lib/fidelity/base-contracts.ts:7-55`).
- `panel_name`, `specimen_id`, `specimen_type`, `report_id`, `accession_id`, `loinc_code` (`contracts.ts:72-77`).
- `fasting_status` `fasted|fed|unknown` (`contracts.ts:78`); `medication_event_ids` links up to 30 medication events (`contracts.ts:79`).
- `provenance` is required (see section 6) (`contracts.ts:80`).
- No `method` field. Not found: `method` in `labFactSchema`. The only `method` in `contracts.ts` belongs to body scans (`contracts.ts:205`).

Non-lab "reported statistic" rows in sleep studies use a simpler but still mixed shape. Each needs either a value with its unit or a qualitative result (`src/lib/fidelity/web-clinical-contracts.ts:88-118`).

### Fasten: FHIR Observation + DiagnosticReport + Specimen

Every resource table embeds `ResourceBase`: identity, `sort_date`, `sort_title`, `source_uri` and **`resource_raw`, the whole FHIR JSON** (`fasten/backend/pkg/models/resource_base.go:8-20`). The typed columns are search indexes extracted from that raw JSON with FHIRPath (`fasten/backend/pkg/database/gorm_common.go:440-445`; the generator is described in `fasten/backend/pkg/models/database/README.md:3-11`).

**Observation** (one analyte), `fasten/backend/pkg/models/database/fhir_observation.go:15-216`:
- `code` is the analyte token, `system|code|text` (`fhir_observation.go:42`; token shape in `models/database/interface.go:24-28`).
- The value is polymorphic:
  - `valueQuantity` (`:212`), filled from `Observation.valueQuantity | valueSampledData` (`:509-511`)
  - `valueString` (`:215`)
  - `valueConcept`, a coded result such as "negative" (`:206`)
  - `valueDate` (`:209`)
  - `dataAbsentReason` (`:69`)
- `component*` columns hold multi-part results, for example the two halves of a blood pressure (`:57-66`).
- `referenceRange` is indexed as a *string* search parameter (`:191`, `:466-468`). Its structure lives only in `resource_raw`.
- `interpretation` is the H/L flag (`:161`); `method` (`:179`); `specimen` reference (`:194`); `performer` (`:188`); `date` is the effective time (`:92`); `status` (`:197`); `note` (`:182`); `encounter` (`:116`); `hasMember` / `derivedFrom` (`:122`, `:95`).
- Sample data shows the range of shapes: a bare `"valueQuantity":{"value":5.1}` with no unit (`fasten/backend/pkg/database/testdata/epic_fhircamila.ndjson:47`), and narrative `valueString` results (`epic_fhircamila.ndjson:44-46`).

**DiagnosticReport** (the report or panel), `fhir_diagnostic_report.go`:
- `result` links to the Observations (`:150-152`)
- `date` is the clinically relevant time (`:68`); `issued` is when the report was issued (`:123-125`)
- `performer` (`:149`), `resultsInterpreter` (`:155`), `specimen` (`:158`), `conclusion` (`:43-45`), `media` (`:131`), `code` (`:42`), `status` (`:161`)

**Specimen**: `accession`, `collected`, `collector`, `type`, `bodysite`, `container` (`fhir_specimen.go:19-70`).

So in Fasten the visit is a DiagnosticReport (or an Encounter), the panel is the DiagnosticReport's `code` or an Observation's `hasMember`, and one analyte is one Observation. Taken (`Observation.date`) and reported (`DiagnosticReport.issued`) are separate fields.

---

## 2. Analyte identity

### HealthTrack
- **No analyte catalogue.** Not found: an analyte, test-catalogue or synonym table. Grepped `synonym|alias|canonical` in `src/db/schema/*.ts` with zero hits; the only seeded reference table is `vital_reference_ranges` for vitals (`src/db/schema/vitals.ts:82-94`, `src/db/seed-reference-ranges.ts:38-113`). Lab reference ranges come from the report itself.
- LOINC is optional and is **never inferred**. The parser prompt says "Printed LOINC only; never derived from a test name" (`src/lib/claude/parse-lab.ts:10`, rule at `:65`), and the typed prompt repeats it (`src/lib/claude/parse-typed-lab.ts:8`).
- **Trend charts group by the exact `test_name` string** and ignore the unit: `const key = result.test_name` (`src/app/(app)/labs/page.tsx:65-81`). "Glucose" and "Glicemia", or the same name reported in mg/dL and in mmol/L, therefore land on separate lines or the same line depending only on spelling.
- When reconciling imported facts against existing ones, two labs count as *related* if their names match after trim, lowercase and whitespace collapse, **or** their LOINC codes match (`src/lib/fidelity/clinical.ts:102`, `:510-513`; the SQL form is at `:577-581`). An *exact duplicate* also needs a matching accession, specimen or report id **and** identical content (`clinical.ts:499-503`, `:514-516`). A related match with different content becomes `conflict`, which the owner has to resolve (`clinical.ts:557-561`).
- **Units are not converted.** The extractor rejects a row whose printed and declared units disagree, with the message "no unit conversion was applied" (`src/lib/fidelity/clinical-extraction.ts:117-128`), and the typed prompt says "Do not normalize units" (`parse-typed-lab.ts:9`). The typed schema has a slot for a normalized value with a `basis` (`contracts.ts:49-52`), shown as "Normalized: … basis: …" (`src/lib/fidelity/presentation.ts:170-172`). No code fills that slot automatically; it is entered through the form (`src/components/clinical-intake/ClinicalFactForm.tsx:247`). Not found: a conversion table. Grepped `convertUnit|unitConver|ucum|mmol` across `src`; the `mmol` hits are only vitals registry units (`src/lib/metrics/registry.ts:180`).

### Fasten
- Identity is the coded `code` token. A search splits `system|code`, as in the example `code=http://loinc.org|29463-7` (`fasten/backend/pkg/database/gorm_repository_query.go:397-398`, SQL sketch `:45-56`). Aggregations with no modifier group by `system || '|' || code` (`gorm_repository_query.go:652-660`), so two Observations are "the same analyte" when their system and code match.
- Quantity search compares `value` and can also require the stored `code`/`system` of the *unit*. There is no conversion (`gorm_repository_query.go:546-578`). The `ap` (approximate) prefix is unsupported (`:563-564`).
- A `Glossary` table maps `(code, code_system)` to patient-friendly title and description (`fasten/backend/pkg/models/glossary.go:3-14`). It explains codes but holds no synonyms.
- Not found: unit normalization or UCUM conversion. Grepped `ucum|convert|normaliz` in `models/*.go` and `database/*.go`; the only hits are unrelated type conversions (`gorm_repository_query.go:458-466`).
- Not found: trend-chart code. The frontend is not in this sparse checkout.

---

## 3. Attachments and documents

### HealthTrack
- **Files live on disk; the database stores paths.** Files sit at `UPLOADS_DIR/<ownerId>/<uuid>.<ext>` and the database keeps the relative path (`src/lib/storage/index.ts:1-12`, `:167-168`).
- **Only PDF, PNG and JPEG are accepted, up to 10 MB** (`storage/index.ts:19-26`). The content bytes must match the declared type (`storage/index.ts:82`, `:165`), and the document MIME is sniffed from magic bytes (`src/lib/import/document-file.ts:4-10`). Anything else is rejected.
- Links to a result:
  - legacy: one `lab_visits.source_pdf_path` per visit (`clinical.ts:201`)
  - typed: an `import_batches` row per uploaded file, holding `file_hash`, `kind`, `file_name`, `source_path`, `status` and `warnings`, unique per person, hash and kind (`src/db/schema/imports.ts:8-33`). A fact's `provenance.sources[]` holds `{reference, document_id, page}` (`base-contracts.ts:68-74`), and `document_id` must be an `import_batches` id for the same person (`clinical.ts:439-448`). That gives **one fact to many source documents, down to the page**.
- An uploaded file is deduplicated by hash before it is parsed again (`src/app/api/parse-lab-pdf/route.ts:71-79`, `:110-111`).
- **OCR: none.** Not found: `tesseract|ocr|mlkit` in `src`. Parsing is AI-only and optional: the file goes to a configured model as a base64 document or image (`parse-lab.ts:71-106`, `parse-typed-lab.ts:10-30`).
- **AI output shape, legacy** (`parse-lab.ts:5-33`): `{visit_date, provider_name, multiple_collection_dates, warnings, results:[{visit_date, panel_name, test_name, loinc_code, value:number, unit, reference_range_low, reference_range_high, reference_range_text, flag, confidence:'high'|'medium'|'low', accession_id, specimen_id, collected_at, source_page, raw_text}]}`.
- **AI output shape, typed** (`parse-typed-lab.ts:6-9`): `{results:[{test_name, outcome:{kind,…}, raw_result, source_unit, collection_date:{precision,value}, report_date, panel_name, report_id, accession_id, specimen_id, specimen_type, loinc_code, source_page, printed_flags[], reference:{low,high,text,unit}}], warnings[]}`. The prompt forbids deriving flags, inventing dates, inferring LOINC and converting units (`parse-typed-lab.ts:7-9`).
- Extraction never writes clinical facts directly. Rows are staged as an intake with `add|correct|link|skip` actions (`contracts.ts:147-171`, tables `fidelity.ts:43-81`), and the owner has to approve them (`approved_at`, `approved_digest`: `fidelity.ts:54-55`). Rows that cannot be typed are kept as "held" evidence with their issues (`clinical-extraction.ts:1`, `:19-29`). Extracted facts carry the note "AI-extracted source evidence requires owner verification" and `verification:'unconfirmed'` (`clinical-extraction.ts:261-273`).

### Fasten
- **Blobs live in the database.** A `Binary` resource is a row whose `resource_raw` JSON includes `contentType` and base64 `data` (`resource_base.go:16`; sample `epic_fhircamila.ndjson:2-6`). Its indexed columns hold no file data (`fhir_binary.go:19-37`).
- A `DocumentReference` points at the file: `contenttype` (`fhir_document_reference.go:26-28`), `location` URI (`:104-106`), `relatesto` for replaces/appends (`:128-131`), `type`, `category`, `date`, `author`, `custodian`. In sample data `attachment.url` is `"Binary/<id>"`, with types including `text/html` and `text/rtf` (`epic_fhircamila.ndjson:18-19`).
- `Media` covers images and video (`fhir_media.go:19-73`). `DiagnosticReport.media` links images to a report (`fhir_diagnostic_report.go:129-131`).
- Any MIME type that FHIR carries can be stored. Not found: a MIME allow-list in `models` or `database`.
- OCR or parsing: not found in this checkout. Grepped `ocr|tesseract|parse.*pdf`.

---

## 4. People and profiles

### HealthTrack
- Every clinical table has `user_id` (the account owner) and a nullable `dependent_id`. NULL means the owner; a value means a dependent (for example `clinical.ts:24-26`, `:54`, `:203`, `:232`).
- `dependents` (`src/db/schema/users.ts:39-60`):
  - `name` and `date_of_birth`, both NOT NULL
  - `biological_sex` `male|female`
  - `relationship` `child|spouse|parent|sibling|other`
  - `transition_age` (default 18), `transitioned`, `transitioned_to`, for handing records to a grown child's own account
- `profiles` holds the owner's `date_of_birth`, `biological_sex`, height and weight. Values are **stored imperial** and the unit system is display-only (`users.ts:20-36`).
- Sharing: `health_shares`, with an access level and per-section lists that default to `medications, labs, vitals, conditions` (`src/db/schema/sharing.ts:19-41`), and `delegates` (`sharing.ts:48-67`).
- **Pets: not found.** Grepped the whole words `pet|pets|animal|species|veterinary|veterinarian` in `src`: zero hits. `biological_sex` allows only male or female (`users.ts:48`).

### Fasten
- Records belong to a `User` (`OriginBase.UserID`, `models/base.go:23-33`) and to a `SourceCredential`, which carries the source-side `Patient` id (`models/source_credential.go:22-27`). Each user also gets a built-in "fasten" source for their own entries (`database/gorm_common.go:65-73`). Manual groupings use a placeholder source with `PlatformType: "manual"` (`gorm_common.go:849`).
- Patient demographics come from FHIR `Patient` resources, one per source (`gorm_common.go:584-603`; columns at `fhir_patient.go:19-199`). `RelatedPerson` exists as a FHIR table (`models/database/fhir_related_person.go`).
- Family: one Fasten user per person; roles are only `user|admin` (`fasten/backend/pkg/constants.go:15`, `:77-78`). Not found: a dependents or delegates model. Grepped `family|delegat|dependent|caregiver` in `models/*.go` and `database/*.go`.
- Pets: not found (same grep plus `pet|animal|veterin`).

---

## 5. Other record types (fields only)

### HealthTrack (legacy tables, `src/db/schema/clinical.ts`)
- **Providers** (`:20-59`): name, `provider_type` (pcp, specialist, lab, imaging, urgent_care, hospital, pharmacy, therapist, dentist, other), specialty, organization, phone, fax, address/city/state/zip, `portal_url`, notes, `is_favorite`, `specialty_taxonomy`.
- **Conditions** (`:91-115`): name; status `active|resolved|managed|monitoring`; diagnosed_date; provider; notes; `icd10_code`.
- **Allergies** (`:118-141`): name; `rxcui`; severity `mild|moderate|severe|life_threatening`; reaction; diagnosed_date; notes.
- **Procedures** (`:144-161`): name, `cpt_code`, `procedure_date` (NOT NULL), provider, notes.
- **Vaccines** (`:164-189`): name, `cvx_code`, `vaccine_date`, `dose_number`, `series_doses`, manufacturer, `lot_number`, provider, **`next_dose_date`** (`:179`), notes.
- **Appointments** (`:242-260`): provider, `appointment_date`, reason, notes, `follow_up_date`, and an optional link to a lab visit (`:254`).
- **Notes** (`:263-283`): content, type `symptom|observation|general`, severity 1-5, tags, `recorded_at`.
- **Medications** (`:62-88`): name, dosage, frequency, category, prescriber, start and end dates, active, `rxcui`.

### HealthTrack (typed facts, `src/lib/fidelity/web-clinical-contracts.ts`)
- Every typed fact carries `provenance` and `notes` (`:12`), and `codes[]` entries are `{system, code, display}` (`:13`).
- **Condition** (`:253-267`): certainty `confirmed|suspected|ruled_out|unknown`; activity; **`temporal_scope` `current|historical|family_history|unknown`**, which is the only family-history mechanism found; onset, diagnosis, end and report dates; codes.
- **Procedure** (`:233-252`): status, procedure and report dates, `events[]` status timeline, codes, provider.
- **Encounter** (`:69-86`): name, encounter, signed and report dates, `encounter_id`, provider, location, observations.
- **Allergy review** (`:268-277`): `review_status` `unknown|reviewed_no_known_allergies|allergies_recorded`, a date, a scope, and who reviewed it. This records "no known allergies" as a fact in its own right.
- **Genetic report** (`:278-300`): report_type, which includes `family_history`; findings with gene, variant and classification.
- Not found: a dedicated family-history table. Grepped `family_history|familyHistory` in `src/db/schema`.

### Fasten (FHIR tables; column lines in each file)
- **Condition**: clinicalStatus, verificationStatus, category, severity, onsetDate/Age, abatementDate, recordedDate, bodySite, stage, evidence (`fhir_condition.go:19-147`).
- **AllergyIntolerance**: clinicalStatus, verificationStatus, type, category, criticality, severity, manifestation, route, onset, lastDate (`fhir_allergy_intolerance.go:19-161`).
- **Procedure**: code, date, status, performer, location, reasonCode, partOf, basedOn (`fhir_procedure.go:19-167`).
- **Immunization**: vaccineCode, date, doseNumber, series, lotNumber, manufacturer, reaction, reactionDate, targetDisease, status, statusReason (`fhir_immunization.go:39-138`). **Next-due date: not found.** There is no `ImmunizationRecommendation` model; grepped `ImmunizationRecommendation|nextDose|next_dose`.
- **Appointment**: date, status, appointmentType, serviceType, specialty, practitioner, location, reasonCode, slot (`fhir_appointment.go:19-85`).
- **Practitioner**: name, given, family, gender, address, phone, email, telecom (`fhir_practitioner.go:19-178`). Organization, Location and CareTeam tables also exist (file list in `models/database/`).
- **FamilyMemberHistory**: code (condition), relationship, sex, date, status (`fhir_family_member_history.go:36-131`).

---

## 6. History, edits and provenance

### HealthTrack
- Timestamps are ISO text (`src/db/schema/_shared.ts:4-25`). Most tables have `created_at` and `updated_at`; `lab_visits` and `lab_results` have only `created_at` (`clinical.ts:204`, `:233`).
- **Soft delete: none** for clinical rows. Not found: `deleted_at` in `clinical.ts`; the only `deletedAt` belongs to Health Connect ingestion (`src/db/schema/health-connect.ts:190`). Foreign keys cascade on delete (for example `clinical.ts:222`).
- **Corrections are append-only.** `clinical_corrections` stores `before_json`, `after_json`, `reason`, `batch_item_id` and `created_at` (`src/db/schema/imports.ts:59-77`). A manual lab-visit edit writes one row holding the whole visit before and after, with default reason "Manual correction" (`src/lib/repos/labs.ts:194-231`).
- Typed facts have a `revision` integer (`fidelity.ts:25`). A correction does `revision=revision+1` and writes a `clinical_corrections` row (`src/lib/fidelity/clinical.ts:944-949`). A correction or link must quote the target id and expected revision (`contracts.ts:157-160`), which is optimistic concurrency.
- Provenance on each fact (`base-contracts.ts:75-82`):
  - `basis`: `source_reported|patient_reported|derived|legacy_unknown`
  - `verification`: `unconfirmed|source_verified|conflict`
  - `sources[]` (document id and page)
  - `notes`
- An overlay links a typed fact to the legacy row it replaces (`legacy_table`, `legacy_id`, `legacy_revision`: `fidelity.ts:27-29`, unique index `:38-40`). Legacy rows that have been overlaid are hidden from the legacy list (`labs.ts:90`).

### Fasten
- `ModelBase` has `created_at`, `updated_at` and `deleted_at *time.Time` (`models/base.go:9-14`). Resource deletes call plain GORM `Delete` (`database/gorm_common.go:1554`, source delete `:1175`). GORM only soft-deletes when the field has type `gorm.DeletedAt`, and that type appears nowhere in the checkout (grepped `gorm.DeletedAt`, zero hits). So, as far as this code shows, **deletes are hard**. This is inferred from GORM's documented behaviour, not from a test.
- **Versioning: none locally.** An upsert matches on `(source_id, source_resource_type, source_resource_id)` and overwrites the row (`gorm_common.go:464-468`, unique composite index `models/base.go:29-32`). The TODO "and UpdatedAt > old UpdatedAt" shows that not even a newer-wins check exists (`gorm_common.go:467`). The source-side version is indexed only as `metaVersionId` / `metaLastUpdated` (`fhir_observation.go:167`, `:176`).
- **Provenance is structural**: every row records which source credential (provider connection) it came from and the resource id it had there (`models/base.go:23-33`), plus an optional `source_uri` (`resource_base.go:13`). References between resources go into a `related_resources` association table, created before the target exists (`gorm_common.go:313-368`; field `resource_base.go:19`). FHIR `Provenance` exists as an indexable table (agent, entity, recorded, target, when: `fhir_provenance.go:19-64`). Whether anything writes to it is not visible in this checkout.

---

## 7. What MedicAll should take, and what to avoid

### Take
- **Keep the outcome as a union, not a number.** Numeric, censored ("<5", ">=200"), text ("Negativo", "Assenti", "Tracce") and cancelled (`healthtrack/src/lib/fidelity/contracts.ts:18-35`). Italian panels routinely mix these; the legacy `value REAL NOT NULL` (`healthtrack/src/db/schema/clinical.ts:225`) forced HealthTrack to skip them (`healthtrack/src/lib/claude/parse-lab.ts:62`).
- **Always keep the printed text** next to the parsed value (`contracts.ts:47`). OCR output and manual entry can both be checked against it.
- **Give the reference range its own unit, comparators and text** (`contracts.ts:53-67`). Italian reports print ranges such as "< 200", "fino a 40" and "vedi nota".
- **Keep the lab's printed flag separate from the flag the app computes** (`contracts.ts:68-69`). An app-computed H/L must never pass as the lab's.
- **Store dates with a precision**: unknown, year, month, day or instant, plus the original text (`healthtrack/src/lib/fidelity/base-contracts.ts:11-55`). Keep collection and report dates separate (`contracts.ts:70-71`; Fasten `Observation.date` against `DiagnosticReport.issued`, `fasten/.../fhir_diagnostic_report.go:123-125`).
- **Keep the source unit and add an optional normalized value with a stated basis** (`contracts.ts:48-52`). Trends can then use converted values while the original stays on record.
- **Link facts to files through a provenance list** with document id and page (`base-contracts.ts:68-82`), deduplicate files by content hash (`healthtrack/src/db/schema/imports.ts:16`, `:26-31`), and **stage OCR or AI rows for owner approval** before they become facts (`healthtrack/src/db/schema/fidelity.ts:43-81`). Rows that cannot be parsed stay as held evidence (`healthtrack/src/lib/fidelity/clinical-extraction.ts:19-29`).
- **Keep an append-only correction log** with before, after and reason (`imports.ts:59-77`), plus a revision number for concurrent edits (`contracts.ts:157-160`). This suits Syncthing, where two devices can edit the same record.
- **Use a coded identity when one is printed, but never invent a code** (`parse-lab.ts:10`, `:65`). Grouping by `system|code` (`fasten/backend/pkg/database/gorm_repository_query.go:652-660`) is the right idea, applied to MedicAll's own analyte ids rather than requiring LOINC.
- **Use one person key on every record**, nullable for "self" in HealthTrack's form (`clinical.ts:54`). For MedicAll a plain person id is cleaner, and its "kind" can be human or pet.
- **Record "no known allergies" as a fact** (`healthtrack/src/lib/fidelity/web-clinical-contracts.ts:268-277`), and keep a next-dose date on vaccines (`clinical.ts:179`).

### Avoid
- **Grouping trends by the raw test name** (`healthtrack/src/app/(app)/labs/page.tsx:80`). "Colesterolo totale" from one lab and "Colesterolo Tot." from another would split, and two units would be plotted on one line.
- **A number-only value column** (`clinical.ts:225`) and **one PDF path per visit** (`clinical.ts:201`). The owner wants any number of files of any type.
- **A PDF/PNG/JPEG allow-list with a 10 MB cap** (`healthtrack/src/lib/storage/index.ts:19-26`). The brief asks for any file type. Keep files on disk by path, not as database blobs.
- **Base64 blobs inside the database** (`fasten/backend/pkg/models/resource_base.go:16`, sample `epic_fhircamila.ndjson:2-6`). A synced SQLite file would grow with every scan.
- **Overwrite-on-upsert with no version history** (`fasten/backend/pkg/database/gorm_common.go:464-468`) and **hard deletes** (`gorm_common.go:1554`). MedicAll needs tombstones so a deletion syncs instead of coming back.
- **Full FHIR as the storage model.** About 50 generated tables, each a JSON blob plus extracted index columns (`fasten/backend/pkg/models/database/README.md:3-11`). That is heavy for a personal offline app. Export to FHIR later if it is ever needed.
- **Imperial storage with metric display** (`healthtrack/src/db/schema/users.ts:30-33`). For an Italian user, store SI or the source unit.
- **Family history as a flag on conditions only** (`web-clinical-contracts.ts:260`). MedicAll may want the relative recorded as well (Fasten `relationship`, `fhir_family_member_history.go:122`).

### Proposed minimal result model (plain words)

**Exam session ("referto")**
- belongs to one person (a human or a pet)
- has a collection date and an optional report date, each with a precision and the original text
- names the lab and an optional requesting doctor, both from a contacts list
- has optional report and accession numbers, free notes, and a revision number

**Attachment**
- any file of any type, stored on disk under the person's folder, keyed by content hash
- records the original name, MIME type, size and when it was added
- one session can have many attachments, and one attachment can serve many sessions

**Result line**
- belongs to one session and names one analyte from the catalogue
- has an optional panel label, as printed
- has an outcome of one of four kinds: numeric (value), bounded (comparator and value), text, or not performed (reason)
- keeps the exact printed result and the printed unit
- has an optional reference range: low and high, each with a comparator, plus range text and the range's unit
- keeps the lab's printed flag as printed, and computes the app's own flag separately
- has optional specimen (blood, urine…), method and fasting status
- has a provenance: entered by hand, or OCR from a given attachment and page, with confidence; plus a status of confirmed or to be checked
- has a revision number, created and updated times, and a deleted-at tombstone

**Analyte catalogue** (owned by the user, seeded from a small built-in list)
- each entry has a canonical name, a preferred unit, and optional LOINC and specimen
- a list of synonyms, added each time the owner maps a new printed name ("Glicemia", "Glucosio", "GLU")
- a list of unit conversions to the preferred unit, each with a factor; molecular weight belongs inside that factor, as in glucose mg/dL to mmol/L

**Trends**
- plot all result lines for the same catalogue analyte, using the converted value whenever a conversion exists
- otherwise use separate series per unit, never a mixed one
- mark bounded and text outcomes on the timeline instead of plotting them as numbers

**OCR path**
- an OCR pass produces draft result lines tied to the attachment and page, in "to be checked" status, with their printed names matched to catalogue synonyms
- nothing counts as confirmed until the owner approves it
- unmatched names prompt "new analyte or synonym of…?"

**History**
- every edit writes a correction entry: before, after, reason, time and device
- a delete sets the tombstone, so Syncthing carries it to the other device

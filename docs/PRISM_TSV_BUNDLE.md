PRISM TSV Bundle v1
===================

`prism-core` now includes a first canonical TSV bundle format for loading an in-memory PRISM dataset.

Entry point
-----------

Use [`PrismTsvDatasetLoader.java`](/home/lithom/dev_chem/prism/prism-core/src/main/java/tech/molecules/structurized/prism/io/PrismTsvDatasetLoader.java):

- `PrismTsvDatasetLoader.load(directory)`

This loads a directory containing a small family of TSV files into an [`InMemoryPrismDataset.java`](/home/lithom/dev_chem/prism/prism-core/src/main/java/tech/molecules/structurized/prism/provider/inmemory/InMemoryPrismDataset.java).

Canonical file names
--------------------

Required:

- `endpoints.prism.tsv`
- `subjects.prism.tsv`
- `values.prism.tsv`

Optional:

- `subject_sets.prism.tsv`
- `subject_set_memberships.prism.tsv`

TSV row shape
-------------

Rows should normally contain the same number of tab-separated cells as the header. Exporters should preserve explicit empty cells with tabs, especially for optional columns at the end of a row.

For robustness, the loader tolerates rows that omit trailing empty cells and pads them as empty values. Rows with more cells than the header are rejected because their column alignment is ambiguous.

PRISM TSV v1 deliberately keeps parsing line-oriented and does not use CSV-style quoted multiline records. Exporters must not emit literal tabs or newlines inside cell values. Instead, use [`PrismTsvEscaper.java`](/home/lithom/dev_chem/prism/prism-core/src/main/java/tech/molecules/structurized/prism/io/PrismTsvEscaper.java) or the same escaping rules:

- `\` is written as `\\`
- tab is written as `\t`
- newline is written as `\n`
- carriage return is written as `\r`

The loader decodes these escape sequences automatically. Fields with PRISM-specific separators such as `categories`, `raw_value_ids`, `raw_values`, and `details` should still avoid unescaped use of their reserved separators unless the exporter and importer are extended together.

Why a bundle instead of one flat file
-------------------------------------

The PRISM model contains several different object types:

- endpoint definitions
- subject records
- endpoint values
- subject sets
- subject-set memberships

Flattening all of these into one TSV is possible for small convenience cases, but it mixes catalog metadata, subject metadata, result payloads, and grouping metadata. The bundle format keeps those concerns separate and maps directly onto the PRISM API.

Subjects file
-------------

`subjects.prism.tsv` contains one row per subject.

Recognized standard columns:

- `subject_id` required
- `structure_id` optional
- `batch_id` optional
- `project` optional
- `series` optional
- `smiles` optional

Any additional columns are stored in [`SubjectRecord.java`](/home/lithom/dev_chem/prism/prism-core/src/main/java/tech/molecules/structurized/prism/provider/SubjectRecord.java) metadata.

This gives you a lightweight standard subject model while still allowing project-specific extensions.

Endpoint definitions file
-------------------------

`endpoints.prism.tsv` contains one row per endpoint definition.

Required columns:

- `endpoint_id`
- `name`
- `path`
- `datatype`
- `endpoint_type`
- `evaluation_mode`

For pragmatic imports, the TSV loader accepts an empty or missing `name` and `path` and uses `endpoint_id` as the fallback value. Exporters should still populate both fields when meaningful display names and catalog paths are available.

Optional columns:

- `unit`
- `scale`
- `domain_lower_bound`
- `domain_upper_bound`
- `description`
- `categories`

`categories` is encoded as:

- `id=Name;id=Name`

For example:

- `active=Active;inactive=Inactive`

Values file
-----------

`values.prism.tsv` contains one row per `(subject_id, endpoint_id)` result.

Common columns:

- `subject_id` required
- `endpoint_id` required
- `n` optional
- `raw_value_ids` optional, `|` separated
- `first_measurement` optional
- `last_measurement` optional
- `details` optional, `key=value;key=value`

Datatype-specific columns:

- numeric:
  - `state` optional, defaults to:
    - `VALUE` if numeric fields are present
    - `NOT_MEASURED` otherwise
  - `mean` required only in `VALUE` state
  - `lower` optional
  - `upper` optional
  - `raw_values` optional, `|` separated
- optional numeric:
  - `state` optional if numeric fields imply `VALUE`
  - `mean`, `lower`, `upper`, `raw_values` as above
- boolean:
  - `value`
- categorical:
  - `value`
- text:
  - `text` preferred
  - `value` also accepted as fallback

Subject sets
------------

`subject_sets.prism.tsv` defines subject-set metadata:

- `subject_set_id`
- `name`
- `set_type`
- `subject_set_scope`
- `parent_set_id`
- `description`

`subject_set_memberships.prism.tsv` defines memberships:

- `subject_set_id`
- `subject_id`

Auto-derived project and series sets
------------------------------------

If subjects define `project` and/or `series`, the loader automatically derives useful medchem subject sets:

- project set id:
  - `project:<project>`
- series set id without project:
  - `series:<series>`
- series set id with project:
  - `series:<project>:<series>`

Derived sets use:

- project:
  - `setType = PROJECT`
  - `subjectSetScope = PROJECTS`
- series:
  - `setType = SERIES`
  - `subjectSetScope = SERIES`

This makes a simple subject file immediately useful for dashboards and exploratory tooling, even before curated subject-set files are available.

Reference provider layer
------------------------

The loaded dataset provides ready-to-use in-memory providers:

- [`InMemoryEndpointProvider.java`](/home/lithom/dev_chem/prism/prism-core/src/main/java/tech/molecules/structurized/prism/provider/inmemory/InMemoryEndpointProvider.java)
- [`InMemorySubjectSetProvider.java`](/home/lithom/dev_chem/prism/prism-core/src/main/java/tech/molecules/structurized/prism/provider/inmemory/InMemorySubjectSetProvider.java)

This is the intended first reference implementation for:

- tests
- local demos
- early dashboards
- bundle-based interchange between preprocessing jobs and UI/backend code

Current limitations
-------------------

The current TSV bundle is intentionally simple:

- no quoting/escaping layer beyond plain TSV splitting
- no JSON details parsing
- category descriptions are not represented in the TSV format yet
- no export writer yet

Those can be added later without changing the current in-memory model.

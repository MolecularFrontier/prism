PrismPack v0.2
===============

PrismPack is a lightweight package format for one analysis-ready dataframe plus
metadata that helps viewers such as DataWarrior, Prism dashboards, Python
workflows, and reports render the same prepared compound table.

Scope
-----

One PrismPack contains one rectangular dataframe. Joins, aggregation, assay
normalization, descriptor calculation, model prediction, and endpoint selection
are upstream responsibilities. PrismPack is not a workflow engine, relational
database format, or replacement for DataWarrior's `.dwar` application-state
format.

Package layout
--------------

A `.prismpack` file is a ZIP archive. The same layout may also be provided as a
directory for debugging and tests.

Required files:

- `prism-pack.json`
- `data/dataframe.tsv`
- `schema/dataframe.schema.json`

Optional files:

- `semantics/molecules.json`
- `semantics/endpoints.json`
- `semantics/scores.json`
- `semantics/property-profiles.json`
- `views/table-view.json`
- `views/visualizations.json`
- `attachments/attachments.json`
- `provenance/provenance.json`

Manifest
--------

`prism-pack.json` is the entry point:

```json
{
  "prismPackVersion": "0.1",
  "id": "example-sar-analysis",
  "title": "Example SAR Analysis",
  "description": "Prepared compound analysis package for DataWarrior inspection.",
  "createdAt": "2026-07-06T10:30:00+02:00",
  "createdBy": "Prism / analysis workflow",
  "dataframe": {
    "id": "main",
    "path": "data/dataframe.tsv",
    "schema": "schema/dataframe.schema.json",
    "rowType": "compound"
  },
  "molecules": "semantics/molecules.json",
  "endpoints": "semantics/endpoints.json",
  "scores": "semantics/scores.json",
  "propertyProfiles": "semantics/property-profiles.json",
  "tableView": "views/table-view.json",
  "visualizations": "views/visualizations.json",
  "attachments": "attachments/attachments.json",
  "provenance": "provenance/provenance.json"
}
```

Only `prismPackVersion` and `dataframe.path` are required in v0.1. If
`dataframe.schema` is absent, readers must use `schema/dataframe.schema.json`.
PrismPack v0.2 adds only optional score and property-profile metadata; v0.1
packages remain valid inputs.

Dataframe TSV
-------------

The dataframe is a line-oriented TSV file with a header row. PrismPack uses the
same cell escaping conventions as the canonical PRISM TSV bundle:

- `\` is written as `\\`
- tab is written as `\t`
- newline is written as `\n`
- carriage return is written as `\r`

Rows with omitted trailing empty cells are padded. Rows with more cells than the
header are invalid because their alignment is ambiguous.

Schema
------

`schema/dataframe.schema.json` defines column metadata:

```json
{
  "columns": [
    {
      "name": "smiles",
      "type": "string",
      "semanticType": "chemical_structure",
      "structureFormat": "smiles",
      "displayName": "Structure",
      "role": "primary_structure"
    }
  ]
}
```

Supported v0.1 column fields are:

- `name`
- `type`: `string`, `number`, `integer`, `boolean`
- `semanticType`
- `displayName`
- `role`
- `unit`
- `endpointId`
- `direction`: `higher_is_better`, `lower_is_better`, `neutral`
- `structureFormat`: `smiles`, `idcode`, `molfile`

Optional metadata
-----------------

`semantics/molecules.json` may define:

- `primaryStructureColumn`
- `structureFormat`
- `compoundIdColumn`

`semantics/endpoints.json` may define an `endpoints` array with:

- `id`
- `column`
- `displayName`
- `unit`
- `direction`
- `assay`
- `protocol`

`semantics/scores.json` may define portable endpoint desirability functions.
The initial `line_segment_v1` score contains an endpoint reference, linear or
log10 x scale, clamping policy, and ordered `(x, score)` points. Scores use the
normalized range `0` (undesirable) to `1` (desirable).

`semantics/property-profiles.json` may define ordered endpoint profiles and MPO
definitions. Profile items reference endpoints and optional score IDs. MPO v1
uses a weighted mean with missing values ignored and supports required
components, hard-fail thresholds, and a coverage warning threshold.

Evaluated scores and MPO results may also be materialized as dataframe columns.
Their schema metadata should use `endpoint_score`, `mpo_score`, `mpo_coverage`,
or `mpo_status` semantic types and include the source definition fingerprint.

`views/table-view.json` may define:

- `columns`
- `hiddenColumns`
- `frozenColumns`
- `sort`
- `filters`
- `colorRules`

`views/visualizations.json` may define scatter plots:

- `id`
- `type`: only `scatter` in v0.1
- `title`
- `x`
- `y`
- `colorBy`
- `sizeBy`


Attachments
-----------

`attachments/attachments.json` may define optional display payloads attached to
objects in the package. v0.1 readers may ignore attachments. DataWarrior uses
inline text attachments targeting dataframe cells as embedded cell details.

```json
{
  "attachments": [
    {
      "id": "att-pIC50-ACT-123-raw",
      "target": {
        "type": "cell",
        "rowKeyColumn": "subject_id",
        "rowKey": "ACT-123",
        "column": "pIC50"
      },
      "name": "Raw endpoint values",
      "mimeType": "text/plain",
      "content": {
        "type": "inline",
        "text": "Aggregate: 7.2\nn: 3\nRaw values: 7.1, 7.2, 7.3"
      }
    }
  ]
}
```

Only `target.type = cell`, `content.type = inline`, and `mimeType = text/plain`
are required for the initial DataWarrior integration. Future PrismPack consumers
may add row, column, visualization, or package-level attachment targets.

Reader behavior
---------------

Readers should fail on missing required files, malformed JSON, duplicate
dataframe column names, or ambiguous TSV rows. Readers should tolerate missing
optional files and ignore unknown JSON fields. Optional view references to
unknown columns should produce warnings rather than failing the package.

Advanced model
--------------

See [`PRISM_DATA_MODEL.md`](PRISM_DATA_MODEL.md) for the lifecycle of base,
computed, and materialized columns and how endpoint, score, MPO, chemistry, row
set, and view semantics relate to the persisted package.

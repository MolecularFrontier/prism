# Prism Markdown reports

A `.prism.md` report is ordinary CommonMark containing declarative JSON blocks. Markdown owns
the scientific narrative; each fenced `prism` block configures a reusable Prism view against the
current dataset. The report never copies chemistry values or structures.

~~~~markdown
---
prismReportVersion: 1
dataset: current
title: Series overview
---

# Series overview

~~~prism
{
  "type": "column-summary",
  "rowSet": "all",
  "columns": ["pIC50", "clogP", "series"]
}
~~~
~~~~

PrismLite validates all column and row-set references when opening the report. Embedded views use
the global Prism selection model: selection made in a report appears in the main table and other
views, and external selection is reflected in the report. Each embedded view can be opened as a
normal full-sized PrismLite view.

## Common fields

Every block requires `type`. The following conventions apply to the built-in views:

- `id`: optional stable block/view ID.
- `title`: optional embedded title.
- `rowSet`: stable Prism row-set ID.
- Column fields always contain stable runtime Prism column IDs.
- Unknown fields are rejected so agent mistakes fail early.

## `compound-table`

~~~json
{
  "type": "compound-table",
  "id": "key-compounds",
  "title": "Key compounds",
  "rowSet": "all",
  "structureColumn": "smiles",
  "columns": [
    {"column": "compound_id", "label": "Compound"},
    {"column": "pIC50", "label": "pIC50", "format": "0.00"}
  ],
  "linkSelection": true,
  "maxRows": 200
}
~~~

`columns` must be non-empty. `format` is a Java decimal pattern and is valid only for numeric
columns. `maxRows` is bounded by the compound-table safety limit.

## `structure-grid`

~~~json
{
  "type": "structure-grid",
  "rowSet": "all",
  "structureColumn": "smiles",
  "valueColumns": ["compound_id", "pIC50"],
  "sortBy": "pIC50",
  "sortDirection": "descending",
  "maxCompounds": 24,
  "gridColumns": 4
}
~~~

`sortDirection` is `ascending` or `descending`; `gridColumns` is between 1 and 8.

## `scatter`

~~~json
{
  "type": "scatter",
  "rowSet": "all",
  "xColumn": "clogP",
  "yColumn": "pIC50",
  "colorColumn": "series",
  "xMin": 1.0,
  "xMax": 5.0
}
~~~

The axes must be numeric. `colorColumn` is optional. Optional `xMin`, `xMax`, `yMin`, and `yMax`
pin plot ranges; each minimum must be smaller than its maximum.

## `column-summary`

~~~json
{
  "type": "column-summary",
  "rowSet": "all",
  "columns": ["pIC50", "clogP", "series"]
}
~~~

Numeric cards show count, missing count, range, median, mean, standard deviation, and a compact
histogram. Categorical cards show count, missing count, distinct count, and leading frequencies.
Molecule columns are intentionally rejected.

## Extending the block registry

The parser and validator dispatch through `PrismReportBlockRegistry`. A new block supplies one
`PrismReportBlockProvider` for parsing and validation, a renderer-neutral `PrismViewSpec`, and a
normal `PrismSwingViewRenderer`. Report rendering then reuses that same renderer; it does not gain
a report-specific chemistry implementation.

The built-in provider registry contains:

- `compound-table`
- `structure-grid`
- `scatter`
- `column-summary`

## Simple agent-defined scores

Structurized MCP exposes a deliberately small scoring workflow:

1. `define_prism_endpoint_score` accepts a numeric endpoint/column and at least two `{x, score}`
   points, with scores between 0 and 1. Interpolation is `linear` by default; `log10` is optional.
2. The operation persists the score definition in the session and materializes an
   `endpoint_score` numeric column. Repeating an identical definition is idempotent; reusing the
   same ID with different semantics is rejected.
3. `list_prism_endpoint_scores` returns definitions, fingerprints, source columns, and output
   columns.
4. Reports reference the returned output column exactly like any other numeric column.
5. `export_prism_snapshot` writes a new `.prismpack` containing runtime score definitions,
   materialized score columns, and row sets while preserving the source pack payload.

Export requires a PrismPack-backed snapshot, creates a distinct snapshot identity and parent
provenance, validates the written pack before publishing it, and never overwrites an existing file.
Reloading the source snapshot remains a clean restart and intentionally discards runtime analysis
objects that have not been exported.

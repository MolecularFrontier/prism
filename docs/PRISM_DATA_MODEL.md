Prism advanced data model
=========================

This document explains how the PRISM protocol, PrismPack, PrismEngine, and
PrismLite represent analysis-ready scientific data. It focuses on the advanced
parts of the model: row identity, column lifecycles, endpoint semantics,
computed and materialized values, scoring, MPOs, chemistry, operations, and
views.

The central design rule is:

> Scientific definitions, persisted values, runtime calculations, and visual
> presentation are separate objects connected by stable identifiers.

System layers
-------------

The implementation has three related layers:

```text
PRISM protocol
    immutable endpoint definitions and typed endpoint results

PrismPack
    one persisted, rectangular, analysis-ready dataframe plus semantics

PrismEngine session
    runtime columns, calculations, row sets, filters, operations, and views
```

An upstream provider or company-specific source system may aggregate assays,
choose endpoints, and construct a PrismPack. Prism consumers do not need to
understand that source system: they operate on the portable definitions and
values in the pack.

PrismPack intentionally does not model joins, remote queries, assay aggregation,
or a general workflow graph. Those operations happen before export or through
explicit PrismEngine operations.

Rows and stable identity
------------------------

One PrismPack contains one dataframe. Each row represents one subject, commonly
a compound or batch. `manifest.dataframe.rowType` describes that interpretation.

PrismEngine distinguishes three row coordinates:

* **Physical row**: immutable zero-based position in the base dataframe.
* **Stable row ID**: logical identity used by row sets, views, selections, and
  operation results.
* **Visible row**: current position after filtering and sorting.

`RowIdIndex` chooses a stable ID column using schema metadata first. A column
with `role = identifier` or `semanticType = compound_id` is preferred, followed
by conventional names such as `compound_id` and `id`. The column must contain a
unique, non-missing value for every row. Otherwise the session falls back to
IDs such as `row:17`.

Fallback IDs are deterministic for one unchanged dataframe, but they are not a
portable identity contract. Producers should therefore include a proper
identifier column.

Filtering and sorting do not move base values. They update the mapping from
visible rows to physical rows. Any long-lived result should refer to stable row
IDs rather than visible or physical positions.

Base dataframe and schema
-------------------------

Persisted values live in `data/dataframe.tsv`. Column metadata lives in
`schema/dataframe.schema.json`.

The TSV stores escaped text cells. The schema assigns interpretation:

```json
{
  "name": "pIC50",
  "type": "number",
  "semanticType": "endpoint_value",
  "displayName": "Cell potency",
  "role": "endpoint",
  "unit": null,
  "endpointId": "project.cell_potency",
  "direction": "higher_is_better"
}
```

Persisted PrismPack column types are:

```text
string
number
integer
boolean
```

PrismEngine exposes the richer runtime types:

```text
TEXT
NUMERIC
INTEGER
BOOLEAN
CATEGORICAL
MOLECULE
```

`CATEGORICAL` and `MOLECULE` are inferred from semantic metadata. A structure
column, for example, remains a portable string in the pack while PrismEngine
exposes it as a molecule column:

```json
{
  "name": "structure_idcode",
  "type": "string",
  "semanticType": "chemical_structure",
  "structureFormat": "idcode",
  "displayName": "Structure",
  "role": "primary_structure"
}
```

At runtime, `PrismColumnSchema` carries:

* column ID and display name;
* physical/runtime type;
* semantic type and role;
* unit and endpoint ID;
* preferred direction;
* structure format;
* an extensible raw metadata map.

Blank cells are missing. Numeric input is parsed strictly; invalid numeric text
fails table construction rather than silently becoming missing. All columns in
one runtime table have the same row count.

Endpoint definitions and results
--------------------------------

An endpoint is a scientific definition, not merely a column heading.
`EndpointDefinition` describes:

* stable ID, display name, and logical path;
* endpoint datatype and endpoint type;
* unit and description;
* numeric scale and optional domain bounds;
* category definitions for categorical endpoints;
* evaluation mode.

The PRISM provider API returns typed `EndpointResult` payloads. Implementations
include numeric, optional numeric, boolean, categorical, and text results.
Numeric payloads may retain:

* mean, lower bound, and upper bound;
* `VALUE`, `NOT_MEASURED`, and, where applicable, `NOT_APPLICABLE` state;
* raw values and structured datapoints;
* measurement count and raw-value IDs;
* first and last measurement metadata;
* additional details.

A PrismPack normally flattens the selected analysis-ready result into one cell.
Supporting values may be retained in attachments or provenance. This is a
deliberate boundary: the dataframe is optimized for analysis, while the
provider result can preserve richer aggregation evidence.

`semantics/endpoints.json` links endpoint IDs to concrete dataframe columns and
adds portable display, unit, direction, assay, and protocol metadata. A runtime
operation resolves an endpoint by `PrismColumnSchema.endpointId`; matching the
column ID is a compatibility fallback.

Column lifecycles
-----------------

PrismEngine combines three kinds of columns into one `RuntimePrismTable`:

```text
base columns
    persisted cells loaded from PrismPack

computed columns
    values produced by registered runtime calculations

materialized columns
    concrete values added to the current session by operations
```

They share the `PrismColumn` interface, so filtering, sorting, formatting,
operations, and views can consume them uniformly. Their ownership and
persistence behavior differ.

### Base columns

Base columns form the immutable `PrismTable` created from the PrismPack. They
retain their physical row alignment for the lifetime of the session.

### Computed values and columns

`ComputedValueDefinition<T>` defines a runtime calculation:

```java
ComputedValueDefinition<Double> definition = ComputedValueDefinition
        .builder("scaled.potency", Double.class)
        .displayName("Scaled potency")
        .columnType(PrismColumnType.NUMERIC)
        .dependencyColumnIds(List.of("pIC50"))
        .implementationVersion("1")
        .configurationFingerprint("min=5;max=9")
        .cachePolicy(CachePolicy.LAZY)
        .provider((table, row, context) -> {
            double value = table.column("pIC50").doubleValueAt(row);
            return (value - 5.0) / 4.0;
        })
        .build();

session.registerComputedValue(definition, true);
```

A definition declares:

* value ID, display name, Java type, and Prism column type;
* dependencies on base columns;
* dependencies on already registered computed values;
* implementation version and configuration fingerprint;
* cache policy and calculation provider.

Cache policies are:

* `LAZY`: calculate once when a row is first requested.
* `PRECOMPUTE`: calculate all rows during registration.
* `NO_CACHE`: calculate on every access.

The cache key includes the value ID, definition fingerprint, and physical row.
Replacing a definition invalidates its cached entries. Computed-to-computed
dependencies are read through `ComputedValueContext`.

Computed values are runtime capabilities. Their definitions and caches are not
currently serialized into PrismPack, and Prism does not provide a spreadsheet
expression language. A plugin registers Java implementations when it opens or
enriches a session.

### Materialized columns

`MaterializedColumnData` contains a schema, one concrete value per physical
row, and provenance. An operation can return it through
`PrismOperationResult.addColumn(...)`.

When an operation naturally works with subject identities, it should return
`RowIdMaterializedColumnData`. PrismEngine validates every referenced row ID and
then aligns the map to physical rows. Omitted row IDs become missing values.

Applying an operation result is atomic with respect to validation: duplicate
column IDs, existing IDs, wrong row counts, and unknown row IDs are rejected
before session state is changed.

Materialized session columns are not automatically written back to the opened
pack. Persisting them requires an exporter to create a new dataframe and schema.
Once written there, they are ordinary base columns in the next session.

Use a computed value when a capability is cheap or naturally recreated by a
plugin. Use a materialized column when users need a concrete analytical result,
the calculation is expensive, or its provenance must travel with an export.

Scores and desirability
-----------------------

An `EndpointScoreDefinition` is portable scientific configuration. It is not a
column and does not contain per-row values.

The initial `line_segment_v1` definition contains:

* score ID and source endpoint ID;
* display name and description;
* linear or log10 x scale;
* clamping policy outside the defined range;
* ordered `(endpoint value, desirability)` points;
* extensible metadata and a deterministic fingerprint.

Desirability is normalized to `[0, 1]`, where `0` is undesirable and `1` is
desirable. The curve need not be monotonic, allowing preferred windows as well
as higher-is-better or lower-is-better functions.

Definitions are stored in `semantics/scores.json`. `ScoreEvaluator` can evaluate
one endpoint value without adding a column. Missing, non-finite, or invalid
log-scale inputs produce an unavailable evaluation rather than invented data.

Property profiles
-----------------

A `PropertyProfileDefinition` is an ordered analytical and presentation context
stored in `semantics/property-profiles.json`. It contains:

* stable profile ID, title, description, and metadata;
* ordered `PropertyProfileItem` entries;
* zero or more MPO definitions.

Each profile item references an endpoint and optionally a score. It may also
define a label, group, order, visibility, and metadata. The endpoint remains the
source of the measured value; the profile determines how that value is grouped,
shown, and optionally converted to desirability.

MPO definitions
---------------

An MPO combines endpoint desirabilities rather than raw endpoint values. Each
`MpoComponentDefinition` references both an endpoint ID and score ID and defines:

* label and non-negative weight;
* whether the component is required;
* optional hard-fail threshold in score space.

MPO v1 calculates a weighted mean over available components. Missing values are
ignored in the arithmetic, while coverage is the available weight divided by
total weight. Evaluation also records component counts and returns one status:

* `INSUFFICIENT_DATA`: no score is available or a required component is missing.
* `FAIL`: an available component violates a hard-fail threshold.
* `WARNING`: coverage is below the configured warning threshold.
* `PASS`: none of the above applies.

Required-missing status takes precedence over hard fail, and hard fail takes
precedence over low-coverage warning. The complete component evaluations remain
available for explanation and audit.

Materialized score and MPO columns
----------------------------------

`score.materialize_property_profile` evaluates every row and adds concrete
session columns. A profile can produce:

```text
score__potency                 semanticType = endpoint_score
score__solubility              semanticType = endpoint_score
mpo__lead_profile              semanticType = mpo_score
mpo__lead_profile__coverage    semanticType = mpo_coverage
mpo__lead_profile__status      semanticType = mpo_status
```

Score columns retain the endpoint ID, score ID, profile ID, and score-definition
fingerprint in schema metadata. MPO columns retain the profile ID, MPO ID, and
MPO-definition fingerprint. Numeric score and MPO columns use
`higher_is_better`; status is categorical.

If a column with the expected ID and identical fingerprint already exists, the
operation reuses it. If the definition changed, it creates a deterministic
`__recomputed` variant rather than silently presenting stale values as current.

Chemistry as a runtime capability
---------------------------------

Chemical structures illustrate why the column lifecycles are separate:

```text
persisted IDCode or SMILES string
    -> computed OpenChemLib StereoMolecule
    -> computed FFP512 fingerprint
    -> substructure filter, row set, rendering, or similarity calculation
```

`prism-engine-ocl` discovers molecule columns and registers parsed-molecule and
fingerprint computed values. Parsing is normally lazy and cached. The OCL object
and fingerprint need not be serialized into the portable dataframe.

Current chemistry operations include substructure-based row-set creation and
structure-grid view creation. Both preserve stable row identity. A structure
grid references a source row set and endpoint columns instead of copying their
values into the view specification.

Future descriptors such as molecular weight or cLogP can follow either model:

* computed values for session-local, reproducible plugin capabilities;
* materialized numeric columns when values should be filtered, exported, or
  consumed without OpenChemLib.

A future query-similarity operation can reuse cached fingerprints and return a
materialized similarity column or a ranked row set. The query structure,
fingerprint implementation version, and parameters belong in provenance.

Operations, row sets, and views
-------------------------------

`PrismOperation` is the mutation boundary for headless and UI-driven work. An
operation receives an immutable `PrismSessionSnapshot` and returns a validated
`PrismOperationResult`. Results may contain:

* materialized columns;
* row sets;
* new or updated views;
* warnings, structured output, and provenance.

A `PrismRowSet` is a named set of stable row IDs. It is suitable for selections,
substructure hits, Pareto fronts, or any reproducible subset. A row-set filter
can make it the active table population.

A `PrismViewRecord` stores a typed, declarative view specification. Views
reference column and row-set IDs and resolve current session values when
prepared. They do not own copied scientific data. This is why a structure grid
or scatter plot can react to filtering and linked selection while remaining a
reproducible session artifact.

Persistence and reproducibility
-------------------------------

Portable reproducibility depends on preserving both definitions and values:

* base measurements live in the dataframe;
* endpoint semantics live in endpoint metadata;
* score curves and profiles live in semantic definition files;
* evaluated values may be materialized into dataframe columns;
* fingerprints connect materialized results to exact definitions;
* provenance identifies the producing operation and source context;
* stable row IDs connect tables, row sets, attachments, and views.

Unknown optional metadata should be retained or ignored safely. Readers fail on
ambiguous structural errors such as duplicate columns or malformed required
files, but optional view references may produce warnings. Calculations should
never substitute partial scientific results after a parse or validation error.

See also
--------

* [PRISM protocol](PRISM_PROTOCOL.md)
* [PrismPack format](PRISMPACK.md)
* [PRISM TSV bundle](PRISM_TSV_BUNDLE.md)

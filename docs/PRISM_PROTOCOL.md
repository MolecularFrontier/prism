PRISM Protocol v1
=================

`prism-core` implements PRISM:

- `PRISM = Protocol for Representing Integrated Scientific Measurements`

The module provides a compact, immutable Java API for analysis-ready medchem endpoint definitions and endpoint payloads. It is intended as the standardized boundary between heterogeneous upstream assay/project data and downstream analytics, dashboards, and automation.

Scope
-----

The current v1 implementation covers:

- endpoint definitions
- endpoint result payloads
- lightweight subject-set discovery
- subject records with optional chemistry/project metadata
- bulk endpoint fetch request/response objects
- in-memory endpoint registry
- in-memory dataset/provider implementations
- TSV bundle import
- practical validation helpers

Package layout
--------------

- `tech.molecules.structurized.prism.model`
  - endpoint definition objects and enums
- `tech.molecules.structurized.prism.result`
  - immutable endpoint payload types
- `tech.molecules.structurized.prism.provider`
  - provider interfaces, subject-set model, and subject records
- `tech.molecules.structurized.prism.query`
  - bulk fetch transport objects
- `tech.molecules.structurized.prism.registry`
  - endpoint-definition registry API and in-memory implementation
- `tech.molecules.structurized.prism.provider.inmemory`
  - reference in-memory dataset and provider implementations
- `tech.molecules.structurized.prism.io`
  - TSV bundle import
- `tech.molecules.structurized.prism.validation`
  - static validation helpers

Design notes
------------

- All protocol objects are immutable.
- Required scalar fields are validated eagerly.
- Optional collections default to empty immutable collections.
- `firstMeasurement` and `lastMeasurement` remain `String` by design.
- `rawValueIds` are part of the shared result metadata for GUI and traceability use cases.
- `SubjectSet` is intentionally lightweight and does not encode a stronger ontology than needed for discovery and paging.
- `SubjectRecord` stores a small standard medchem-oriented subject view with optional free metadata.

Endpoint model
--------------

`EndpointDefinition` describes an analysis-ready endpoint with:

- stable `id`
- display `name`
- logical `path`
- `EndpointDataType`
- `EndpointType`
- optional unit/description
- optional category definitions for categorical endpoints
- optional `NumericEndpointMeta` for numeric datatypes

`NumericEndpointMeta` currently covers:

- `scale`
- `domainLowerBound`
- `domainUpperBound`

Validation currently enforces:

- required fields must be present
- categorical endpoints must define categories
- non-categorical endpoints must not define categories
- category ids must be unique within one endpoint
- non-numeric endpoints must not define numeric metadata

Result model
------------

`EndpointResult` is the shared payload interface. Concrete result types are:

- `NumericResult`
- `OptionalNumericResult`
- `BooleanResult`
- `CategoricalResult`
- `TextResult`

`NumericResult` now distinguishes:

- `VALUE`
- `NOT_MEASURED`

This is the standard path for ordinary measured numeric endpoints where a subject may simply not have been measured yet.

`OptionalNumericResult` explicitly distinguishes:

- `VALUE`
- `NOT_MEASURED`
- `NOT_APPLICABLE`

This is useful for endpoints where a numeric value may be conceptually inapplicable for some subjects, not just absent because no measurement exists.

Provider model
--------------

The provider layer is intentionally small:

- `SubjectSetProvider` exposes discoverable subject groups and subject paging.
- `EndpointProvider` exposes endpoint definitions and bulk endpoint fetching.
- `InMemoryPrismDataset` is the reference in-memory storage object behind the initial provider implementations.

Bulk fetch uses:

- `EndpointFetchRequest`
- `EndpointValueRecord`

This keeps transport simple for GUI/backend integration while still being strongly typed.

Registry
--------

`InMemoryEndpointRegistry` is included as a deterministic, duplicate-checking registry for tests, examples, and lightweight service wiring.

In-memory reference implementation
----------------------------------

`prism-core` now also includes:

- `InMemoryPrismDataset`
- `InMemoryEndpointProvider`
- `InMemorySubjectSetProvider`

This gives PRISM a concrete reference implementation for local use, bundle-based interchange, and early integration work.

TSV bundle import
-----------------

`PrismTsvDatasetLoader` loads a canonical PRISM TSV bundle into an `InMemoryPrismDataset`.

This is the first concrete file-based interchange layer for PRISM and is intended for:

- preprocessing pipelines
- test fixtures
- demo data exchange
- lightweight dashboard backends

See [`PRISM_TSV_BUNDLE.md`](/home/lithom/dev_chem/prism/docs/PRISM_TSV_BUNDLE.md) for the bundle format.

Validation
----------

Two static validators are provided:

- `EndpointDefinitionValidator`
- `EndpointResultValidator`

These are intentionally pragmatic rather than formal. They check the invariants that matter most for downstream consistency without turning PRISM v1 into a heavyweight schema framework.

Current status
--------------

The current implementation is suitable as the first stable protocol layer for:

- dashboard backends
- endpoint catalogs
- bulk endpoint aggregation services
- analytics modules that combine structural chemistry with standardized endpoint values

It does not yet include:

- REST bindings
- persistence
- JSON library annotations
- remote provider implementations

Those can be added on top without changing the current core model.

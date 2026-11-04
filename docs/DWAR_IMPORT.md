# OpenChemLib DWAR import

`prism-engine-ocl` provides the UI-independent `tech.molecules.structurized.prism.engine.ocl.io.DwarImporter` API. It uses the pinned OpenChemLib 2026.1.1 `DWARFileParser`; no DataWarrior application source or dependency is required.

```java
var result = DwarImporter.read(path, DwarImporter.Options.defaults(), progress);
PrismPack pack = result.pack();
DwarImporter.Report report = result.report();
PrismSession session = PrismSession.from(pack);
OclPrismEngineSupport.registerCapabilities(session);
```

The reader overload accepts a `Reader` plus source name and owns/closes that reader. Records stream through `next()` into an in-memory PrismPack; declared counts are advisory and never determine allocation. Diagnostics carry codes, severity, affected column IDs/counts and interpretation decisions. Progress callbacks support cooperative cancellation. Reader failures absorbed by OCL are detected; malformed row widths are rejected because the underlying reader otherwise tolerates incomplete records.

Physical source fields become `dwar_c1`, `dwar_c2`, … with their original names as display names. IDCode structures retain their exact strings. Parent-associated 2D coordinates are retained and identified by `column.raw.coordinatesColumn`. Automatic OCL registration now honors that relationship; explicit registration remains available. Missing coordinates can be generated at depiction time without changing source cells. Technical fields, including imported descriptor caches and unsupported content, are hidden initially but retained. OCL descriptors are recomputed.

Complete-column inference supports exact integer/numeric, boolean, categorical and text columns. Leading-zero and unsafe large integral identifiers remain text. Qualified numbers, multivalues and mixed entries remain verbatim text with diagnostics, never implicit bounds-to-exact conversion or averaging. Although PRISM endpoint results support `PrismNumericDatapoint.modifier`/`unprocessedValue` and multiple contributors, the ordinary scalar table loader expects exact doubles. This importer does not invent endpoint definitions, aggregation means, units or assay semantics. Optional type overrides are validated against every row; special fields cannot be retyped.

Column properties are preserved in `column.raw.sourceMetadata.datawarrior`. OCL-exposed header/footer content is retained in `pack.provenance.sourceMetadata.datawarrior`; the structured report is in `pack.provenance.importReport`. Stored calculated values are ordinary source cells. Formulas, macros, source plots, filters and layouts are not executed or restored. Reactions, 3D and annotations remain opaque. This is not a lossless DWAR round-trip exporter. Saved-list conversion to Prism row sets remains a follow-up.

There are no fabricated scientific row identifiers and no deduplication. Prism's `RowIdIndex` generates positional `row:N` IDs for these ordinary tables; the unchanged saved source order preserves those IDs through PrismPack round trips. They establish no identity across independent imports or reordered source files.

`DwarImporterTest` contains synthetic scientific/failure fixtures and a configurable external Wikipedia acceptance test. Set `HYDRA_DWAR_FIXTURE` to the supplied `Wikipedia_Compounds_01.dwar` (24,903 rows and 22 physical columns). The external test skips explicitly when absent; synthetic coverage always runs.

```bash
mvn -pl prism-engine-ocl -am test
```

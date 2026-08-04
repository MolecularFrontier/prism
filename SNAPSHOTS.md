# Canonical PRISM TSV snapshots

`prism-core` provides a database-independent artifact boundary for reproducible dataset exchange:

```java
PrismDatasetSnapshot created = PrismTsvDatasetWriter.writeSnapshot(
        outputDirectory,
        inMemoryDataset,
        snapshotDescriptor);

PrismDatasetSnapshot loaded = PrismTsvSnapshotLoader.load(outputDirectory);
```

The writer sorts canonical TSV rows, escapes multiline cells, writes through a temporary sibling directory, computes file digests and a content/provenance identity, validates the completed bundle, and publishes it with an atomic directory move when supported. It refuses an existing destination.

The manifest is `snapshot.prism.json`. Its snapshot identity covers publisher/source semantics, subject identity, population selection and revision, endpoint revisions and metadata, subject-set revisions, arbitrary generic metadata, and the sorted file inventory. Capture timestamps are retained as provenance but excluded from identity, so identical content and definitions produce the same id on a later capture.

The loader rejects missing, modified, additional, nested, non-regular, or symbolic-link entries before loading the TSV dataset. `PrismTsvDatasetLoader` remains available for mutable or legacy manifest-free TSV directories.

PRISM does not know how endpoint data is obtained. Database connections, endpoint catalog resolution, aggregation engines, and credentials belong in publisher adapters outside this repository.

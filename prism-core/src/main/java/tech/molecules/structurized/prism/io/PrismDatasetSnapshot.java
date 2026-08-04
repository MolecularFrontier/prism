package tech.molecules.structurized.prism.io;

import tech.molecules.structurized.prism.provider.inmemory.InMemoryPrismDataset;

public record PrismDatasetSnapshot(InMemoryPrismDataset dataset, PrismSnapshotManifest manifest) {
    public PrismDatasetSnapshot {
        if (dataset == null) throw new IllegalArgumentException("dataset must not be null");
        if (manifest == null) throw new IllegalArgumentException("manifest must not be null");
    }
}

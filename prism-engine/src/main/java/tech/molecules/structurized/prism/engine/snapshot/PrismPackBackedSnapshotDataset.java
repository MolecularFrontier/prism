package tech.molecules.structurized.prism.engine.snapshot;

import tech.molecules.structurized.prism.pack.PrismPack;

public interface PrismPackBackedSnapshotDataset extends PrismSnapshotDataset {
    PrismPack sourcePack();
}

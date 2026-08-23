package tech.molecules.structurized.prism.engine.snapshot;

import java.nio.file.Path;
import java.util.List;

public record PrismSnapshotExportResult(
        Path path,
        String snapshotId,
        int rowCount,
        List<String> scoreIds,
        List<String> derivedColumnIds
) {
    public PrismSnapshotExportResult {
        scoreIds = scoreIds == null ? List.of() : List.copyOf(scoreIds);
        derivedColumnIds = derivedColumnIds == null ? List.of() : List.copyOf(derivedColumnIds);
    }
}

package tech.molecules.structurized.prism.engine;

import java.util.List;
import java.util.Optional;

public record PrismSessionSnapshot(
        PrismTable table,
        ComputedValueRegistry computedValues,
        RowIdIndex rowIdIndex,
        List<PrismRowSet> rowSets
) {
    public PrismSessionSnapshot {
        rowSets = rowSets == null ? List.of() : List.copyOf(rowSets);
    }

    public Optional<PrismRowSet> rowSet(String rowSetId) {
        if (rowSetId == null || rowSetId.isBlank()) {
            return Optional.empty();
        }
        return rowSets.stream().filter(rowSet -> rowSet.id().equals(rowSetId)).findFirst();
    }
}

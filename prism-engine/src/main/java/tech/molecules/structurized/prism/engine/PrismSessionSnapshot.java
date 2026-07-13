package tech.molecules.structurized.prism.engine;

import tech.molecules.structurized.prism.score.EndpointScoreDefinition;
import tech.molecules.structurized.prism.score.PropertyProfileDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record PrismSessionSnapshot(
        PrismTable table,
        ComputedValueRegistry computedValues,
        RowIdIndex rowIdIndex,
        List<PrismRowSet> rowSets,
        Map<String, EndpointScoreDefinition> scoreDefinitions,
        Map<String, PropertyProfileDefinition> propertyProfiles
) {
    public PrismSessionSnapshot {
        rowSets = rowSets == null ? List.of() : List.copyOf(rowSets);
        scoreDefinitions = scoreDefinitions == null ? Map.of() : Map.copyOf(scoreDefinitions);
        propertyProfiles = propertyProfiles == null ? Map.of() : Map.copyOf(propertyProfiles);
    }

    public PrismSessionSnapshot(PrismTable table,
                                ComputedValueRegistry computedValues,
                                RowIdIndex rowIdIndex,
                                List<PrismRowSet> rowSets) {
        this(table, computedValues, rowIdIndex, rowSets, Map.of(), Map.of());
    }

    public Optional<PrismRowSet> rowSet(String rowSetId) {
        if (rowSetId == null || rowSetId.isBlank()) {
            return Optional.empty();
        }
        return rowSets.stream().filter(rowSet -> rowSet.id().equals(rowSetId)).findFirst();
    }
}

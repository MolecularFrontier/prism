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
        List<PrismGrouping> groupings,
        List<PrismRowGraph> graphs,
        Map<String, EndpointScoreDefinition> scoreDefinitions,
        Map<String, PropertyProfileDefinition> propertyProfiles
) {
    public PrismSessionSnapshot {
        rowSets = rowSets == null ? List.of() : List.copyOf(rowSets);
        groupings = groupings == null ? List.of() : List.copyOf(groupings);
        graphs = graphs == null ? List.of() : List.copyOf(graphs);
        scoreDefinitions = scoreDefinitions == null ? Map.of() : Map.copyOf(scoreDefinitions);
        propertyProfiles = propertyProfiles == null ? Map.of() : Map.copyOf(propertyProfiles);
    }

    public PrismSessionSnapshot(PrismTable table,
                                ComputedValueRegistry computedValues,
                                RowIdIndex rowIdIndex,
                                List<PrismRowSet> rowSets) {
        this(table, computedValues, rowIdIndex, rowSets, List.of(), List.of(), Map.of(), Map.of());
    }

    public PrismSessionSnapshot(PrismTable table,
                                ComputedValueRegistry computedValues,
                                RowIdIndex rowIdIndex,
                                List<PrismRowSet> rowSets,
                                Map<String, EndpointScoreDefinition> scoreDefinitions,
                                Map<String, PropertyProfileDefinition> propertyProfiles) {
        this(table, computedValues, rowIdIndex, rowSets, List.of(), List.of(), scoreDefinitions, propertyProfiles);
    }

    public Optional<PrismRowSet> rowSet(String rowSetId) {
        if (rowSetId == null || rowSetId.isBlank()) {
            return Optional.empty();
        }
        return rowSets.stream().filter(rowSet -> rowSet.id().equals(rowSetId)).findFirst();
    }

    public Optional<PrismGrouping> grouping(String groupingId) {
        if (groupingId == null || groupingId.isBlank()) {
            return Optional.empty();
        }
        return groupings.stream().filter(grouping -> grouping.id().equals(groupingId)).findFirst();
    }

    public Optional<PrismRowGraph> graph(String graphId) {
        if (graphId == null || graphId.isBlank()) {
            return Optional.empty();
        }
        return graphs.stream().filter(graph -> graph.id().equals(graphId)).findFirst();
    }
}

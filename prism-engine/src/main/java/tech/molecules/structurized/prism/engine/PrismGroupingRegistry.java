package tech.molecules.structurized.prism.engine;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class PrismGroupingRegistry {
    private final RowIdIndex rowIdIndex;
    private final Map<String, PrismGrouping> groupings = new LinkedHashMap<>();
    private final Map<String, PrismGrouping> groupingsByFacetColumnId = new LinkedHashMap<>();

    PrismGroupingRegistry(RowIdIndex rowIdIndex) {
        this.rowIdIndex = rowIdIndex;
    }

    Collection<PrismGrouping> groupings() {
        return List.copyOf(groupings.values());
    }

    Optional<PrismGrouping> find(String groupingId) {
        return Optional.ofNullable(groupings.get(groupingId));
    }

    Optional<PrismGrouping> findByFacetColumnId(String columnId) {
        return Optional.ofNullable(groupingsByFacetColumnId.get(columnId));
    }

    void add(PrismGrouping grouping) {
        if (groupings.containsKey(grouping.id())) {
            throw new IllegalArgumentException("grouping already exists: " + grouping.id());
        }
        if (grouping.facetColumnId() != null && groupingsByFacetColumnId.containsKey(grouping.facetColumnId())) {
            throw new IllegalArgumentException("grouping facet column already exists: " + grouping.facetColumnId());
        }
        groupings.put(grouping.id(), grouping);
        if (grouping.facetColumnId() != null) {
            groupingsByFacetColumnId.put(grouping.facetColumnId(), grouping);
        }
    }

    PrismColumn facetColumn(String columnId) {
        PrismGrouping grouping = groupingsByFacetColumnId.get(columnId);
        if (grouping == null) {
            throw new IllegalArgumentException("unknown grouping facet column '" + columnId + "'");
        }
        return new GroupingFacetPrismColumn(grouping, rowIdIndex);
    }

    List<PrismColumn> facetColumns() {
        return groupingsByFacetColumnId.values().stream()
                .map(grouping -> (PrismColumn) new GroupingFacetPrismColumn(grouping, rowIdIndex))
                .toList();
    }
}

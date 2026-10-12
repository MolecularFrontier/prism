package tech.molecules.structurized.prism.engine;

import tech.molecules.structurized.prism.score.EndpointScoreDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PrismOperationResult {
    private final List<MaterializedColumnData> addedColumns;
    private final List<RowIdMaterializedColumnData> addedColumnsByRowId;
    private final List<PrismGrouping> addedGroupings;
    private final Set<String> visibleGroupingFacetIds;
    private final List<PrismRowSet> addedRowSets;
    private final List<PrismRowGraph> addedGraphs;
    private final List<PrismViewRecord> addedViews;
    private final List<PrismViewRecord> updatedViews;
    private final List<EndpointScoreDefinition> addedScoreDefinitions;
    private final List<String> warnings;
    private final Map<String, Object> output;
    private final Map<String, Object> provenance;

    private PrismOperationResult(Builder builder) {
        this.addedColumns = List.copyOf(builder.addedColumns);
        this.addedColumnsByRowId = List.copyOf(builder.addedColumnsByRowId);
        this.addedGroupings = List.copyOf(builder.addedGroupings);
        this.visibleGroupingFacetIds = Set.copyOf(builder.visibleGroupingFacetIds);
        this.addedRowSets = List.copyOf(builder.addedRowSets);
        this.addedGraphs = List.copyOf(builder.addedGraphs);
        this.addedViews = List.copyOf(builder.addedViews);
        this.updatedViews = List.copyOf(builder.updatedViews);
        this.addedScoreDefinitions = List.copyOf(builder.addedScoreDefinitions);
        this.warnings = List.copyOf(builder.warnings);
        this.output = builder.output.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.output));
        this.provenance = builder.provenance.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.provenance));
    }

    public List<MaterializedColumnData> addedColumns() {
        return addedColumns;
    }

    public List<RowIdMaterializedColumnData> addedColumnsByRowId() {
        return addedColumnsByRowId;
    }

    public List<PrismGrouping> addedGroupings() {
        return addedGroupings;
    }

    public Set<String> visibleGroupingFacetIds() {
        return visibleGroupingFacetIds;
    }

    public List<PrismRowSet> addedRowSets() {
        return addedRowSets;
    }

    public List<PrismRowGraph> addedGraphs() {
        return addedGraphs;
    }

    public List<PrismViewRecord> addedViews() {
        return addedViews;
    }

    public List<EndpointScoreDefinition> addedScoreDefinitions() {
        return addedScoreDefinitions;
    }

    public List<PrismViewRecord> updatedViews() {
        return updatedViews;
    }

    public List<String> warnings() {
        return warnings;
    }

    public Map<String, Object> provenance() {
        return provenance;
    }

    public Map<String, Object> output() {
        return output;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final ArrayList<MaterializedColumnData> addedColumns = new ArrayList<>();
        private final ArrayList<RowIdMaterializedColumnData> addedColumnsByRowId = new ArrayList<>();
        private final ArrayList<PrismGrouping> addedGroupings = new ArrayList<>();
        private final LinkedHashSet<String> visibleGroupingFacetIds = new LinkedHashSet<>();
        private final ArrayList<PrismRowSet> addedRowSets = new ArrayList<>();
        private final ArrayList<PrismRowGraph> addedGraphs = new ArrayList<>();
        private final ArrayList<PrismViewRecord> addedViews = new ArrayList<>();
        private final ArrayList<PrismViewRecord> updatedViews = new ArrayList<>();
        private final ArrayList<EndpointScoreDefinition> addedScoreDefinitions = new ArrayList<>();
        private final ArrayList<String> warnings = new ArrayList<>();
        private final LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        private final LinkedHashMap<String, Object> provenance = new LinkedHashMap<>();

        public Builder addColumn(MaterializedColumnData column) {
            addedColumns.add(column);
            return this;
        }

        public Builder addColumnByRowId(RowIdMaterializedColumnData column) {
            addedColumnsByRowId.add(column);
            return this;
        }

        public Builder addGrouping(PrismGrouping grouping) {
            return addGrouping(grouping, true);
        }

        public Builder addGrouping(PrismGrouping grouping, boolean facetVisible) {
            addedGroupings.add(grouping);
            if (facetVisible && grouping.facetColumnId() != null) {
                visibleGroupingFacetIds.add(grouping.facetColumnId());
            }
            return this;
        }

        public Builder addRowSet(PrismRowSet rowSet) {
            addedRowSets.add(rowSet);
            return this;
        }

        public Builder addGraph(PrismRowGraph graph) {
            addedGraphs.add(graph);
            return this;
        }

        public Builder addView(PrismViewRecord view) {
            addedViews.add(view);
            return this;
        }

        public Builder updateView(PrismViewRecord view) {
            updatedViews.add(view);
            return this;
        }

        public Builder addScoreDefinition(EndpointScoreDefinition definition) {
            addedScoreDefinitions.add(definition);
            return this;
        }

        public Builder addWarning(String warning) {
            if (warning != null && !warning.isBlank()) {
                warnings.add(warning);
            }
            return this;
        }

        public Builder provenance(String key, Object value) {
            if (key != null && !key.isBlank()) {
                provenance.put(key, value);
            }
            return this;
        }

        public Builder output(String key, Object value) {
            if (key != null && !key.isBlank()) {
                output.put(key, value);
            }
            return this;
        }

        public PrismOperationResult build() {
            return new PrismOperationResult(this);
        }
    }
}

package tech.molecules.structurized.prism.engine;

import tech.molecules.structurized.prism.pack.PrismPack;
import tech.molecules.structurized.prism.pack.PrismPackReader;
import tech.molecules.structurized.prism.prediction.InMemoryPredictionCapabilityCatalog;
import tech.molecules.structurized.prism.prediction.PredictionCapability;
import tech.molecules.structurized.prism.prediction.PredictionCapabilityCatalog;
import tech.molecules.structurized.prism.score.EndpointScoreDefinition;
import tech.molecules.structurized.prism.score.PropertyProfileDefinition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class PrismSession {
    private final PrismTable baseTable;
    private final RowIdIndex rowIdIndex;
    private final ComputedValueRegistry computedValues;
    private final MaterializedColumnRegistry materializedColumns;
    private final PrismGroupingRegistry groupingRegistry;
    private final PrismOperationRegistry operationRegistry;
    private final PrismTable table;
    private final PrismViewState viewState;
    private final Map<String, PrismRowSet> rowSets = new LinkedHashMap<>();
    private final Map<String, PrismViewRecord> views = new LinkedHashMap<>();
    private final Map<String, EndpointScoreDefinition> scoreDefinitions;
    private final Map<String, PropertyProfileDefinition> propertyProfiles;
    private final PredictionCapabilityCatalog predictionCapabilities;
    private final CopyOnWriteArrayList<Consumer<PrismSessionChange>> changeListeners = new CopyOnWriteArrayList<>();
    private BitSet activeRows;
    private int[] visibleRows;

    private PrismSession(PrismTable baseTable,
                         PrismViewState viewState,
                         Collection<EndpointScoreDefinition> scores,
                         Collection<PropertyProfileDefinition> profiles,
                         Collection<PredictionCapability> predictionCapabilities) {
        this.baseTable = Objects.requireNonNull(baseTable, "baseTable");
        this.rowIdIndex = RowIdIndex.forTable(baseTable);
        this.computedValues = new ComputedValueRegistry(baseTable);
        this.materializedColumns = new MaterializedColumnRegistry(baseTable.rowCount());
        this.groupingRegistry = new PrismGroupingRegistry(rowIdIndex);
        this.operationRegistry = new PrismOperationRegistry();
        this.table = new RuntimePrismTable(baseTable, computedValues, materializedColumns, groupingRegistry);
        this.viewState = Objects.requireNonNull(viewState, "viewState");
        this.scoreDefinitions = indexScores(scores);
        this.propertyProfiles = indexProfiles(profiles);
        this.predictionCapabilities = new InMemoryPredictionCapabilityCatalog(
                predictionCapabilities == null ? List.of() : List.copyOf(predictionCapabilities));
        this.operationRegistry.register(new ListPropertyProfilesOperation());
        this.operationRegistry.register(new DescribePropertyProfileOperation());
        this.operationRegistry.register(new EvaluateEndpointScoreOperation());
        this.operationRegistry.register(new EvaluatePropertyProfileOperation());
        this.operationRegistry.register(new EvaluateMpoOperation());
        this.operationRegistry.register(new MaterializePropertyProfileOperation());
        recompute();
    }

    public static PrismSession open(Path prismPackPath) throws IOException {
        return from(PrismPackReader.read(prismPackPath));
    }

    public static PrismSession from(PrismPack pack) {
        PrismTable table = InMemoryPrismTable.from(pack);
        Collection<EndpointScoreDefinition> scores = pack.scores() == null ? List.of() : pack.scores().scores();
        Collection<PropertyProfileDefinition> profiles = pack.propertyProfiles() == null
                ? List.of() : pack.propertyProfiles().profiles();
        Collection<PredictionCapability> predictionCapabilities = pack.predictions() == null
                ? List.of() : pack.predictions().capabilities();
        return new PrismSession(table, PrismViewState.fromPack(pack, table), scores, profiles, predictionCapabilities);
    }

    public static PrismSession from(PrismTable table) {
        return new PrismSession(table, PrismViewState.defaultFor(table), List.of(), List.of(), List.of());
    }

    public PrismTable baseTable() {
        return baseTable;
    }

    public PrismTable table() {
        return table;
    }

    public RowIdIndex rowIdIndex() {
        return rowIdIndex;
    }

    public String rowIdForPhysicalRow(int physicalRow) {
        return rowIdIndex.rowId(physicalRow);
    }

    public OptionalInt physicalRowForRowId(String rowId) {
        return rowIdIndex.physicalRow(rowId);
    }

    public ComputedValueRegistry computedValues() {
        return computedValues;
    }

    public PrismOperationRegistry operationRegistry() {
        return operationRegistry;
    }

    public PrismViewState viewState() {
        return viewState;
    }

    public List<EndpointScoreDefinition> scoreDefinitions() {
        return List.copyOf(scoreDefinitions.values());
    }

    public EndpointScoreDefinition scoreDefinition(String scoreId) {
        EndpointScoreDefinition definition = scoreDefinitions.get(scoreId);
        if (definition == null) throw new IllegalArgumentException("unknown score definition '" + scoreId + "'");
        return definition;
    }

    public List<PropertyProfileDefinition> propertyProfiles() {
        return List.copyOf(propertyProfiles.values());
    }

    public PropertyProfileDefinition propertyProfile(String profileId) {
        PropertyProfileDefinition definition = propertyProfiles.get(profileId);
        if (definition == null) throw new IllegalArgumentException("unknown property profile '" + profileId + "'");
        return definition;
    }

    public List<PredictionCapability> predictionCapabilities() {
        return predictionCapabilities.capabilities();
    }

    public List<PredictionCapability> predictionCapabilitiesFor(String endpointId) {
        return predictionCapabilities.capabilitiesForEndpoint(endpointId);
    }

    public PredictionCapability predictionCapability(String capabilityId) {
        return predictionCapabilities.findCapability(capabilityId)
                .orElseThrow(() -> new IllegalArgumentException("unknown prediction capability '" + capabilityId + "'"));
    }

    public PredictionCapabilityCatalog predictionCapabilityCatalog() {
        return predictionCapabilities;
    }

    public int totalRowCount() {
        return table.rowCount();
    }

    public int visibleRowCount() {
        return visibleRows.length;
    }

    public int visibleColumnCount() {
        return viewState.visibleColumns().size();
    }

    public String visibleColumnId(int visibleColumn) {
        return viewState.visibleColumns().get(visibleColumn);
    }

    public PrismColumn visibleColumn(int visibleColumn) {
        return table.column(visibleColumnId(visibleColumn));
    }

    public int physicalRowAtVisibleIndex(int visibleRow) {
        return visibleRows[visibleRow];
    }

    public Object valueAtVisible(int visibleRow, int visibleColumn) {
        return table.valueAt(physicalRowAtVisibleIndex(visibleRow), visibleColumnId(visibleColumn));
    }

    public String formattedValueAtVisible(int visibleRow, int visibleColumn) {
        return table.formattedValueAt(physicalRowAtVisibleIndex(visibleRow), visibleColumnId(visibleColumn));
    }

    public BitSet activeRows() {
        return (BitSet) activeRows.clone();
    }

    public int[] visiblePhysicalRows() {
        return visibleRows.clone();
    }

    public List<PrismRowSet> rowSets() {
        return List.copyOf(rowSets.values());
    }

    public PrismRowSet rowSet(String rowSetId) {
        PrismRowSet rowSet = rowSets.get(rowSetId);
        if (rowSet == null) {
            throw new IllegalArgumentException("unknown row set '" + rowSetId + "'");
        }
        return rowSet;
    }

    public List<PrismGrouping> groupings() {
        return List.copyOf(groupingRegistry.groupings());
    }

    public PrismGrouping grouping(String groupingId) {
        return groupingRegistry.find(groupingId)
                .orElseThrow(() -> new IllegalArgumentException("unknown grouping '" + groupingId + "'"));
    }

    public boolean isGroupingFacetColumn(String columnId) {
        return groupingRegistry.findByFacetColumnId(columnId).isPresent();
    }

    public List<PrismViewRecord> views() {
        return List.copyOf(views.values());
    }

    public PrismViewRecord view(String viewId) {
        PrismViewRecord view = views.get(viewId);
        if (view == null) {
            throw new IllegalArgumentException("unknown view '" + viewId + "'");
        }
        return view;
    }

    public PrismSessionSubscription subscribe(Consumer<PrismSessionChange> listener) {
        Consumer<PrismSessionChange> registered = Objects.requireNonNull(listener, "listener");
        changeListeners.add(registered);
        return () -> changeListeners.remove(registered);
    }

    public void addGrouping(PrismGrouping grouping) {
        addGrouping(grouping, true);
    }

    public void addGrouping(PrismGrouping grouping, boolean facetVisible) {
        applyOperationResult(PrismOperationResult.builder().addGrouping(grouping, facetVisible).build());
    }

    public void addRowSet(PrismRowSet rowSet) {
        applyOperationResult(PrismOperationResult.builder().addRowSet(rowSet).build());
    }

    public void addView(PrismViewRecord view) {
        applyOperationResult(PrismOperationResult.builder().addView(view).build());
    }

    public void updateView(PrismViewRecord view) {
        applyOperationResult(PrismOperationResult.builder().updateView(view).build());
    }

    public void removeView(String viewId) {
        if (views.remove(viewId) == null) {
            throw new IllegalArgumentException("unknown view '" + viewId + "'");
        }
        publishChange(PrismSessionChangeType.VIEWS);
    }

    public void addMaterializedColumn(MaterializedColumnData column, boolean visible) {
        applyOperationResult(PrismOperationResult.builder().addColumn(column).build(), visible);
    }

    public PrismOperationResult runOperation(String operationId, Map<String, Object> parameters) {
        PrismOperationResult result = operationRegistry.run(operationId, snapshot(), parameters);
        applyOperationResult(result);
        return result;
    }

    public PrismSessionSnapshot snapshot() {
        return new PrismSessionSnapshot(
                table,
                computedValues,
                rowIdIndex,
                rowSets(),
                groupings(),
                scoreDefinitions,
                propertyProfiles
        );
    }

    public void applyOperationResult(PrismOperationResult result) {
        applyOperationResult(result, true);
    }

    private void applyOperationResult(PrismOperationResult result, boolean makeColumnsVisible) {
        Objects.requireNonNull(result, "result");
        List<MaterializedColumnData> columns = materializeColumns(result);
        validateOperationResult(
                columns,
                result.addedGroupings(),
                result.addedRowSets(),
                result.addedViews(),
                result.updatedViews()
        );

        for (PrismRowSet rowSet : result.addedRowSets()) {
            rowSets.put(rowSet.id(), rowSet);
        }
        for (PrismGrouping grouping : result.addedGroupings()) {
            groupingRegistry.add(grouping);
        }
        for (MaterializedColumnData column : columns) {
            materializedColumns.add(column);
        }
        for (PrismViewRecord view : result.addedViews()) {
            views.put(view.id(), view);
        }
        for (PrismViewRecord view : result.updatedViews()) {
            views.put(view.id(), view);
        }
        if (makeColumnsVisible && (!columns.isEmpty() || !result.visibleGroupingFacetIds().isEmpty())) {
            ArrayList<String> visible = new ArrayList<>(viewState.visibleColumns());
            for (PrismGrouping grouping : result.addedGroupings()) {
                if (result.visibleGroupingFacetIds().contains(grouping.facetColumnId())
                        && !visible.contains(grouping.facetColumnId())) {
                    visible.add(grouping.facetColumnId());
                }
            }
            for (MaterializedColumnData column : columns) {
                if (!visible.contains(column.schema().id())) {
                    visible.add(column.schema().id());
                }
            }
            viewState.setVisibleColumns(visible);
        }
        recompute();
        publishChange(operationResultChangeType(columns, result));
    }

    private List<MaterializedColumnData> materializeColumns(PrismOperationResult result) {
        ArrayList<MaterializedColumnData> columns = new ArrayList<>(result.addedColumns());
        for (RowIdMaterializedColumnData column : result.addedColumnsByRowId()) {
            for (String rowId : column.valuesByRowId().keySet()) {
                if (physicalRowForRowId(rowId).isEmpty()) {
                    throw new PrismOperationException(
                            "UNKNOWN_ROW_ID",
                            "column '" + column.schema().id() + "' references unknown row ID '" + rowId + "'",
                            null,
                            Map.of("columnId", column.schema().id(), "rowId", rowId)
                    );
                }
            }
            ArrayList<Object> values = new ArrayList<>(rowIdIndex.rowCount());
            for (int row = 0; row < rowIdIndex.rowCount(); row++) {
                values.add(column.valuesByRowId().get(rowIdIndex.rowId(row)));
            }
            columns.add(new MaterializedColumnData(column.schema(), values, column.provenance()));
        }
        return List.copyOf(columns);
    }

    private void validateOperationResult(Collection<MaterializedColumnData> columns,
                                         Collection<PrismGrouping> newGroupings,
                                         Collection<PrismRowSet> newRowSets,
                                         Collection<PrismViewRecord> newViews,
                                         Collection<PrismViewRecord> updatedViews) {
        HashSet<String> newRowSetIds = new HashSet<>();
        LinkedHashMap<String, PrismRowSet> newRowSetsById = new LinkedHashMap<>();
        for (PrismRowSet rowSet : newRowSets) {
            if (!newRowSetIds.add(rowSet.id())) {
                throw new PrismOperationException("DUPLICATE_ROW_SET", "operation result contains duplicate row set '" + rowSet.id() + "'");
            }
            if (rowSets.containsKey(rowSet.id())) {
                throw new PrismOperationException("ROW_SET_EXISTS", "row set already exists: " + rowSet.id());
            }
            newRowSetsById.put(rowSet.id(), rowSet);
            for (String rowId : rowSet.rowIds()) {
                if (physicalRowForRowId(rowId).isEmpty()) {
                    throw new PrismOperationException(
                            "UNKNOWN_ROW_ID",
                            "row set '" + rowSet.id() + "' references unknown row ID '" + rowId + "'",
                            null,
                            Map.of("rowSetId", rowSet.id(), "rowId", rowId)
                    );
                }
            }
        }

        HashSet<String> newColumnIds = new HashSet<>();
        for (MaterializedColumnData column : columns) {
            validateMaterializedColumn(column);
            validateNewColumnId(column.schema().id(), newColumnIds);
        }

        HashSet<String> newGroupingIds = new HashSet<>();
        for (PrismGrouping grouping : newGroupings) {
            if (!newGroupingIds.add(grouping.id())) {
                throw new PrismOperationException(
                        "DUPLICATE_GROUPING",
                        "operation result contains duplicate grouping '" + grouping.id() + "'"
                );
            }
            if (groupingRegistry.find(grouping.id()).isPresent()) {
                throw new PrismOperationException("GROUPING_EXISTS", "grouping already exists: " + grouping.id());
            }
            if (grouping.facetColumnId() != null) {
                validateNewColumnId(grouping.facetColumnId(), newColumnIds);
            }
            validateGrouping(grouping, newRowSetsById);
        }

        HashSet<String> newViewIds = new HashSet<>();
        for (PrismViewRecord view : newViews) {
            Objects.requireNonNull(view, "view");
            if (!newViewIds.add(view.id())) {
                throw new PrismOperationException("DUPLICATE_VIEW", "operation result contains duplicate view '" + view.id() + "'");
            }
            if (views.containsKey(view.id())) {
                throw new PrismOperationException("VIEW_EXISTS", "view already exists: " + view.id());
            }
            validateViewReferences(view, newColumnIds, newRowSetIds);
        }

        HashSet<String> updatedViewIds = new HashSet<>();
        for (PrismViewRecord view : updatedViews) {
            Objects.requireNonNull(view, "view");
            if (!updatedViewIds.add(view.id()) || newViewIds.contains(view.id())) {
                throw new PrismOperationException("DUPLICATE_VIEW", "operation result contains duplicate view '" + view.id() + "'");
            }
            if (!views.containsKey(view.id())) {
                throw new PrismOperationException("VIEW_NOT_FOUND", "view does not exist: " + view.id());
            }
            validateViewReferences(view, newColumnIds, newRowSetIds);
        }
    }

    private void validateNewColumnId(String columnId, Set<String> newColumnIds) {
        if (!newColumnIds.add(columnId)) {
            throw new PrismOperationException(
                    "DUPLICATE_COLUMN",
                    "operation result contains duplicate column '" + columnId + "'"
            );
        }
        if (table.findColumn(columnId).isPresent()) {
            throw new PrismOperationException("COLUMN_EXISTS", "column already exists: " + columnId);
        }
    }

    private void validateGrouping(PrismGrouping grouping, Map<String, PrismRowSet> newRowSets) {
        PrismRowSet source = null;
        if (grouping.sourceRowSetId() != null) {
            source = newRowSets.get(grouping.sourceRowSetId());
            if (source == null) {
                source = rowSets.get(grouping.sourceRowSetId());
            }
            if (source == null) {
                throw new PrismOperationException(
                        "UNKNOWN_ROW_SET",
                        "grouping '" + grouping.id() + "' references unknown source row set '"
                                + grouping.sourceRowSetId() + "'"
                );
            }
        }
        for (PrismGroupMembership membership : grouping.memberships()) {
            if (physicalRowForRowId(membership.rowId()).isEmpty()) {
                throw new PrismOperationException(
                        "UNKNOWN_ROW_ID",
                        "grouping '" + grouping.id() + "' references unknown row ID '" + membership.rowId() + "'",
                        null,
                        Map.of("groupingId", grouping.id(), "rowId", membership.rowId())
                );
            }
            if (source != null && !source.rowIds().contains(membership.rowId())) {
                throw new PrismOperationException(
                        "GROUPING_ROW_OUTSIDE_SCOPE",
                        "grouping '" + grouping.id() + "' assigns row '" + membership.rowId()
                                + "' outside source row set '" + source.id() + "'",
                        null,
                        Map.of(
                                "groupingId", grouping.id(),
                                "rowId", membership.rowId(),
                                "sourceRowSetId", source.id()
                        )
                );
            }
        }
    }

    private void validateViewReferences(PrismViewRecord view, HashSet<String> newColumnIds, HashSet<String> newRowSetIds) {
        for (String columnId : view.specification().referencedColumnIds()) {
            if (!newColumnIds.contains(columnId) && table.findColumn(columnId).isEmpty()) {
                throw new PrismOperationException(
                        "UNKNOWN_COLUMN",
                        "view '" + view.id() + "' references unknown column '" + columnId + "'",
                        null,
                        Map.of("viewId", view.id(), "columnId", columnId)
                );
            }
        }
        for (String rowSetId : view.specification().referencedRowSetIds()) {
            if (!newRowSetIds.contains(rowSetId) && !rowSets.containsKey(rowSetId)) {
                throw new PrismOperationException(
                        "UNKNOWN_ROW_SET",
                        "view '" + view.id() + "' references unknown row set '" + rowSetId + "'",
                        null,
                        Map.of("viewId", view.id(), "rowSetId", rowSetId)
                );
            }
        }
    }

    private void validateMaterializedColumn(MaterializedColumnData column) {
        if (column.values().size() != rowIdIndex.rowCount()) {
            throw new PrismOperationException(
                    "INVALID_COLUMN_VALUES",
                    "materialized column '" + column.schema().id() + "' has " + column.values().size()
                            + " values for " + rowIdIndex.rowCount() + " rows"
            );
        }
        for (Object value : column.values()) {
            if (value != null && !isCompatibleColumnValue(column.schema().type(), value)) {
                throw new PrismOperationException(
                        "INVALID_COLUMN_VALUE",
                        "materialized column '" + column.schema().id() + "' contains value incompatible with " + column.schema().type(),
                        null,
                        Map.of("columnId", column.schema().id(), "valueType", value.getClass().getName())
                );
            }
        }
    }

    private static boolean isCompatibleColumnValue(PrismColumnType type, Object value) {
        return switch (type) {
            case NUMERIC, INTEGER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case TEXT, CATEGORICAL, MOLECULE -> true;
        };
    }

    public void filterToRowSet(String rowSetId) {
        setFilters(List.of(new RowSetFilter(rowSet(rowSetId))));
    }

    public void setVisibleColumns(List<String> columnIds) {
        for (String columnId : columnIds) {
            table.column(columnId);
        }
        viewState.setVisibleColumns(columnIds);
        publishChange(PrismSessionChangeType.STRUCTURE);
    }

    public void registerComputedValue(ComputedValueDefinition<?> definition) {
        registerComputedValue(definition, false);
    }

    public void registerComputedValue(ComputedValueDefinition<?> definition, boolean visible) {
        computedValues.register(definition);
        if (visible && !viewState.visibleColumns().contains(definition.id())) {
            ArrayList<String> columns = new ArrayList<>(viewState.visibleColumns());
            columns.add(definition.id());
            viewState.setVisibleColumns(columns);
        }
        recompute();
        publishChange(PrismSessionChangeType.STRUCTURE);
    }

    public void replaceComputedValue(ComputedValueDefinition<?> definition) {
        computedValues.replace(definition);
        recompute();
        publishChange(PrismSessionChangeType.STRUCTURE);
    }

    public void precomputeValue(String computedValueId) {
        computedValues.precompute(computedValueId);
    }

    public void addFilter(PrismFilter filter) {
        viewState.addFilter(Objects.requireNonNull(filter, "filter"));
        recompute();
        publishChange(PrismSessionChangeType.PROJECTION);
    }

    public void setFilters(List<PrismFilter> filters) {
        viewState.setActiveFilters(filters);
        recompute();
        publishChange(PrismSessionChangeType.PROJECTION);
    }

    public void clearFilters() {
        viewState.clearFilters();
        recompute();
        publishChange(PrismSessionChangeType.PROJECTION);
    }

    public void setSortKeys(List<SortKey> sortKeys) {
        if (sortKeys != null) {
            for (SortKey sortKey : sortKeys) {
                table.column(sortKey.columnId());
            }
        }
        viewState.setSortKeys(sortKeys);
        recompute();
        publishChange(PrismSessionChangeType.PROJECTION);
    }

    public void sortBy(String columnId, SortDirection direction) {
        setSortKeys(List.of(new SortKey(columnId, direction, MissingValueOrder.LAST)));
    }

    public void recompute() {
        BitSet rows = new BitSet(table.rowCount());
        rows.set(0, table.rowCount());
        PrismEvaluationContext context = new PrismEvaluationContext(viewState, computedValues, rowIdIndex);
        for (PrismFilter filter : viewState.activeFilters()) {
            BitSet filterRows = filter.evaluate(table, context);
            rows.and(filterRows);
        }
        activeRows = rows;
        visibleRows = sort(rows);
    }

    private int[] sort(BitSet rows) {
        ArrayList<Integer> ordered = new ArrayList<>(rows.cardinality());
        for (int row = rows.nextSetBit(0); row >= 0; row = rows.nextSetBit(row + 1)) {
            ordered.add(row);
        }
        if (!viewState.sortKeys().isEmpty()) {
            ordered.sort(rowComparator(viewState.sortKeys()));
        }
        int[] result = new int[ordered.size()];
        for (int i = 0; i < ordered.size(); i++) {
            result[i] = ordered.get(i);
        }
        return result;
    }

    private PrismSessionChangeType operationResultChangeType(List<MaterializedColumnData> columns,
                                                              PrismOperationResult result) {
        if (!columns.isEmpty() || !result.addedRowSets().isEmpty() || !result.addedGroupings().isEmpty()) {
            return PrismSessionChangeType.STRUCTURE;
        }
        return PrismSessionChangeType.VIEWS;
    }

    private void publishChange(PrismSessionChangeType type) {
        PrismSessionChange change = new PrismSessionChange(this, type);
        for (Consumer<PrismSessionChange> listener : changeListeners) {
            try {
                listener.accept(change);
            } catch (RuntimeException ignored) {
                // Listener failures must not turn a committed session mutation into an operation failure.
            }
        }
    }

    private Comparator<Integer> rowComparator(List<SortKey> sortKeys) {
        return (left, right) -> {
            for (SortKey sortKey : sortKeys) {
                int comparison = compareRows(left, right, sortKey);
                if (comparison != 0) {
                    return comparison;
                }
            }
            return Integer.compare(left, right);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compareRows(int left, int right, SortKey sortKey) {
        PrismColumn column = table.column(sortKey.columnId());
        boolean leftMissing = column.isMissing(left);
        boolean rightMissing = column.isMissing(right);
        if (leftMissing || rightMissing) {
            if (leftMissing && rightMissing) {
                return 0;
            }
            int missingComparison = sortKey.missingValueOrder() == MissingValueOrder.FIRST ? -1 : 1;
            return leftMissing ? missingComparison : -missingComparison;
        }
        int comparison;
        if (column.type() == PrismColumnType.NUMERIC || column.type() == PrismColumnType.INTEGER) {
            comparison = Double.compare(column.doubleValueAt(left), column.doubleValueAt(right));
        } else {
            Object leftValue = column.valueAt(left);
            Object rightValue = column.valueAt(right);
            if (leftValue instanceof Comparable comparableLeft && rightValue != null) {
                comparison = comparableLeft.compareTo(rightValue);
            } else {
                comparison = String.valueOf(leftValue).compareTo(String.valueOf(rightValue));
            }
        }
        return sortKey.direction() == SortDirection.DESCENDING ? -comparison : comparison;
    }

    private static Map<String, EndpointScoreDefinition> indexScores(Collection<EndpointScoreDefinition> definitions) {
        LinkedHashMap<String, EndpointScoreDefinition> indexed = new LinkedHashMap<>();
        if (definitions != null) {
            for (EndpointScoreDefinition definition : definitions) {
                if (indexed.putIfAbsent(definition.id(), definition) != null) {
                    throw new IllegalArgumentException("duplicate score definition '" + definition.id() + "'");
                }
            }
        }
        return Map.copyOf(indexed);
    }

    private static Map<String, PropertyProfileDefinition> indexProfiles(Collection<PropertyProfileDefinition> definitions) {
        LinkedHashMap<String, PropertyProfileDefinition> indexed = new LinkedHashMap<>();
        if (definitions != null) {
            for (PropertyProfileDefinition definition : definitions) {
                if (indexed.putIfAbsent(definition.id(), definition) != null) {
                    throw new IllegalArgumentException("duplicate property profile '" + definition.id() + "'");
                }
            }
        }
        return Map.copyOf(indexed);
    }

    @Override
    public String toString() {
        return "PrismSession{" +
                "rows=" + table.rowCount() +
                ", visibleRows=" + visibleRows.length +
                ", visibleColumns=" + Arrays.toString(viewState.visibleColumns().toArray()) +
                '}';
    }
}

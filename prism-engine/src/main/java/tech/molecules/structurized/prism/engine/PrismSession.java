package tech.molecules.structurized.prism.engine;

import tech.molecules.structurized.prism.pack.PrismPack;
import tech.molecules.structurized.prism.pack.PrismPackReader;

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

public final class PrismSession {
    private final PrismTable baseTable;
    private final RowIdIndex rowIdIndex;
    private final ComputedValueRegistry computedValues;
    private final MaterializedColumnRegistry materializedColumns;
    private final PrismOperationRegistry operationRegistry;
    private final PrismTable table;
    private final PrismViewState viewState;
    private final Map<String, PrismRowSet> rowSets = new LinkedHashMap<>();
    private BitSet activeRows;
    private int[] visibleRows;

    private PrismSession(PrismTable baseTable, PrismViewState viewState) {
        this.baseTable = Objects.requireNonNull(baseTable, "baseTable");
        this.rowIdIndex = RowIdIndex.forTable(baseTable);
        this.computedValues = new ComputedValueRegistry(baseTable);
        this.materializedColumns = new MaterializedColumnRegistry(baseTable.rowCount());
        this.operationRegistry = new PrismOperationRegistry();
        this.table = new RuntimePrismTable(baseTable, computedValues, materializedColumns);
        this.viewState = Objects.requireNonNull(viewState, "viewState");
        recompute();
    }

    public static PrismSession open(Path prismPackPath) throws IOException {
        return from(PrismPackReader.read(prismPackPath));
    }

    public static PrismSession from(PrismPack pack) {
        PrismTable table = InMemoryPrismTable.from(pack);
        return new PrismSession(table, PrismViewState.fromPack(pack, table));
    }

    public static PrismSession from(PrismTable table) {
        return new PrismSession(table, PrismViewState.defaultFor(table));
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

    public void addRowSet(PrismRowSet rowSet) {
        applyOperationResult(PrismOperationResult.builder().addRowSet(rowSet).build());
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
        return new PrismSessionSnapshot(table, computedValues, rowIdIndex);
    }

    public void applyOperationResult(PrismOperationResult result) {
        applyOperationResult(result, true);
    }

    private void applyOperationResult(PrismOperationResult result, boolean makeColumnsVisible) {
        Objects.requireNonNull(result, "result");
        List<MaterializedColumnData> columns = materializeColumns(result);
        validateOperationResult(columns, result.addedRowSets());

        for (MaterializedColumnData column : columns) {
            materializedColumns.add(column);
        }
        for (PrismRowSet rowSet : result.addedRowSets()) {
            rowSets.put(rowSet.id(), rowSet);
        }
        if (makeColumnsVisible && !columns.isEmpty()) {
            ArrayList<String> visible = new ArrayList<>(viewState.visibleColumns());
            for (MaterializedColumnData column : columns) {
                if (!visible.contains(column.schema().id())) {
                    visible.add(column.schema().id());
                }
            }
            viewState.setVisibleColumns(visible);
        }
        recompute();
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

    private void validateOperationResult(Collection<MaterializedColumnData> columns, Collection<PrismRowSet> newRowSets) {
        HashSet<String> newColumnIds = new HashSet<>();
        for (MaterializedColumnData column : columns) {
            validateMaterializedColumn(column);
            String columnId = column.schema().id();
            if (!newColumnIds.add(columnId)) {
                throw new PrismOperationException("DUPLICATE_COLUMN", "operation result contains duplicate column '" + columnId + "'");
            }
            if (table.findColumn(columnId).isPresent()) {
                throw new PrismOperationException("COLUMN_EXISTS", "column already exists: " + columnId);
            }
        }

        HashSet<String> newRowSetIds = new HashSet<>();
        for (PrismRowSet rowSet : newRowSets) {
            if (!newRowSetIds.add(rowSet.id())) {
                throw new PrismOperationException("DUPLICATE_ROW_SET", "operation result contains duplicate row set '" + rowSet.id() + "'");
            }
            if (rowSets.containsKey(rowSet.id())) {
                throw new PrismOperationException("ROW_SET_EXISTS", "row set already exists: " + rowSet.id());
            }
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
    }

    public void replaceComputedValue(ComputedValueDefinition<?> definition) {
        computedValues.replace(definition);
        recompute();
    }

    public void precomputeValue(String computedValueId) {
        computedValues.precompute(computedValueId);
    }

    public void addFilter(PrismFilter filter) {
        viewState.addFilter(Objects.requireNonNull(filter, "filter"));
        recompute();
    }

    public void setFilters(List<PrismFilter> filters) {
        viewState.setActiveFilters(filters);
        recompute();
    }

    public void clearFilters() {
        viewState.clearFilters();
        recompute();
    }

    public void setSortKeys(List<SortKey> sortKeys) {
        if (sortKeys != null) {
            for (SortKey sortKey : sortKeys) {
                table.column(sortKey.columnId());
            }
        }
        viewState.setSortKeys(sortKeys);
        recompute();
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

    @Override
    public String toString() {
        return "PrismSession{" +
                "rows=" + table.rowCount() +
                ", visibleRows=" + visibleRows.length +
                ", visibleColumns=" + Arrays.toString(viewState.visibleColumns().toArray()) +
                '}';
    }
}

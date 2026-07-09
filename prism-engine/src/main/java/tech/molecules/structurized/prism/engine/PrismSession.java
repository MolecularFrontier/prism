package tech.molecules.structurized.prism.engine;

import tech.molecules.structurized.prism.pack.PrismPack;
import tech.molecules.structurized.prism.pack.PrismPackReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class PrismSession {
    private final PrismTable baseTable;
    private final ComputedValueRegistry computedValues;
    private final PrismTable table;
    private final PrismViewState viewState;
    private BitSet activeRows;
    private int[] visibleRows;

    private PrismSession(PrismTable baseTable, PrismViewState viewState) {
        this.baseTable = Objects.requireNonNull(baseTable, "baseTable");
        this.computedValues = new ComputedValueRegistry(baseTable);
        this.table = new RuntimePrismTable(baseTable, computedValues);
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

    public ComputedValueRegistry computedValues() {
        return computedValues;
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
        PrismEvaluationContext context = new PrismEvaluationContext(viewState, computedValues);
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

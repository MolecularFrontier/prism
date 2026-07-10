package tech.molecules.structurized.prism.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class RuntimePrismTable implements PrismTable {
    private final PrismTable baseTable;
    private final ComputedValueRegistry computedValues;
    private final MaterializedColumnRegistry materializedColumns;

    RuntimePrismTable(PrismTable baseTable,
                      ComputedValueRegistry computedValues,
                      MaterializedColumnRegistry materializedColumns) {
        this.baseTable = baseTable;
        this.computedValues = computedValues;
        this.materializedColumns = materializedColumns;
    }

    @Override
    public int rowCount() {
        return baseTable.rowCount();
    }

    @Override
    public List<PrismColumn> columns() {
        ArrayList<PrismColumn> columns = new ArrayList<>(baseTable.columns());
        for (ComputedValueDefinition<?> definition : computedValues.definitions()) {
            columns.add(computedValues.asColumn(definition.id()));
        }
        for (MaterializedColumnData column : materializedColumns.columns()) {
            columns.add(materializedColumns.asColumn(column.schema().id()));
        }
        return List.copyOf(columns);
    }

    @Override
    public PrismColumn columnAt(int columnIndex) {
        return columns().get(columnIndex);
    }

    @Override
    public Optional<PrismColumn> findColumn(String columnId) {
        Optional<PrismColumn> baseColumn = baseTable.findColumn(columnId);
        if (baseColumn.isPresent()) {
            return baseColumn;
        }
        Optional<PrismColumn> computedColumn = computedValues.findDefinition(columnId)
                .map(definition -> computedValues.asColumn(definition.id()));
        if (computedColumn.isPresent()) {
            return computedColumn;
        }
        return materializedColumns.find(columnId).map(column -> materializedColumns.asColumn(column.schema().id()));
    }

    @Override
    public int columnIndex(String columnId) {
        int baseIndex = baseTable.columnIndex(columnId);
        if (baseIndex >= 0) {
            return baseIndex;
        }
        int index = baseTable.columns().size();
        for (ComputedValueDefinition<?> definition : computedValues.definitions()) {
            if (definition.id().equals(columnId)) {
                return index;
            }
            index++;
        }
        for (MaterializedColumnData column : materializedColumns.columns()) {
            if (column.schema().id().equals(columnId)) {
                return index;
            }
            index++;
        }
        return -1;
    }
}

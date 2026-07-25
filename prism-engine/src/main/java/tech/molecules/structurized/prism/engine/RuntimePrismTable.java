package tech.molecules.structurized.prism.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class RuntimePrismTable implements PrismTable {
    private final PrismTable baseTable;
    private final ComputedValueRegistry computedValues;
    private final MaterializedColumnRegistry materializedColumns;
    private final PrismGroupingRegistry groupings;

    RuntimePrismTable(PrismTable baseTable,
                      ComputedValueRegistry computedValues,
                      MaterializedColumnRegistry materializedColumns,
                      PrismGroupingRegistry groupings) {
        this.baseTable = baseTable;
        this.computedValues = computedValues;
        this.materializedColumns = materializedColumns;
        this.groupings = groupings;
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
        columns.addAll(groupings.facetColumns());
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
        Optional<PrismColumn> materializedColumn = materializedColumns.find(columnId)
                .map(column -> materializedColumns.asColumn(column.schema().id()));
        if (materializedColumn.isPresent()) {
            return materializedColumn;
        }
        return groupings.findByFacetColumnId(columnId).map(ignored -> groupings.facetColumn(columnId));
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
        for (PrismColumn column : groupings.facetColumns()) {
            if (column.id().equals(columnId)) {
                return index;
            }
            index++;
        }
        return -1;
    }
}

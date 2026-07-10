package tech.molecules.structurized.prism.engine;

import java.util.List;
import java.util.Set;

final class MaterializedPrismColumn implements PrismColumn {
    private final PrismColumnSchema schema;
    private final List<?> values;

    MaterializedPrismColumn(MaterializedColumnData data, int rowCount) {
        if (data.values().size() != rowCount) {
            throw new IllegalArgumentException("materialized column '" + data.schema().id()
                    + "' has " + data.values().size() + " values for " + rowCount + " rows");
        }
        this.schema = data.schema();
        this.values = data.values();
    }

    @Override
    public String id() {
        return schema.id();
    }

    @Override
    public PrismColumnType type() {
        return schema.type();
    }

    @Override
    public PrismColumnSchema schema() {
        return schema;
    }

    @Override
    public int rowCount() {
        return values.size();
    }

    @Override
    public boolean isMissing(int physicalRow) {
        Object value = valueAt(physicalRow);
        return value == null || (value instanceof Double doubleValue && Double.isNaN(doubleValue));
    }

    @Override
    public Object valueAt(int physicalRow) {
        return values.get(physicalRow);
    }

    @Override
    public String formattedValueAt(int physicalRow) {
        Object value = valueAt(physicalRow);
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public double doubleValueAt(int physicalRow) {
        Object value = valueAt(physicalRow);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new UnsupportedOperationException("materialized column is not numeric: " + id());
    }

    @Override
    public Set<FilterCapability> filterCapabilities() {
        return switch (type()) {
            case NUMERIC, INTEGER -> Set.of(FilterCapability.NUMERIC_RANGE, FilterCapability.MISSING_VALUE);
            case CATEGORICAL -> Set.of(FilterCapability.CATEGORY_INCLUDE, FilterCapability.TEXT_CONTAINS, FilterCapability.MISSING_VALUE);
            case TEXT, MOLECULE -> Set.of(FilterCapability.TEXT_CONTAINS, FilterCapability.MISSING_VALUE);
            case BOOLEAN -> Set.of(FilterCapability.MISSING_VALUE);
        };
    }
}

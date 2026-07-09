package tech.molecules.structurized.prism.engine;

import java.util.Set;

final class ComputedPrismColumn implements PrismColumn {
    private final ComputedValueRegistry registry;
    private final ComputedValueDefinition<?> definition;
    private final PrismColumnSchema schema;

    ComputedPrismColumn(ComputedValueRegistry registry, ComputedValueDefinition<?> definition) {
        this.registry = registry;
        this.definition = definition;
        this.schema = definition.columnSchema();
    }

    @Override
    public String id() {
        return definition.id();
    }

    @Override
    public PrismColumnType type() {
        return definition.columnType();
    }

    @Override
    public PrismColumnSchema schema() {
        return schema;
    }

    @Override
    public int rowCount() {
        return registry.baseTable().rowCount();
    }

    @Override
    public boolean isMissing(int physicalRow) {
        Object value = valueAt(physicalRow);
        return value == null || (value instanceof Double doubleValue && Double.isNaN(doubleValue));
    }

    @Override
    public Object valueAt(int physicalRow) {
        return registry.value(definition.id(), physicalRow);
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
        throw new UnsupportedOperationException("computed column is not numeric: " + id());
    }

    @Override
    public Set<FilterCapability> filterCapabilities() {
        return switch (definition.columnType()) {
            case NUMERIC, INTEGER -> Set.of(FilterCapability.NUMERIC_RANGE, FilterCapability.MISSING_VALUE);
            case CATEGORICAL -> Set.of(FilterCapability.CATEGORY_INCLUDE, FilterCapability.TEXT_CONTAINS, FilterCapability.MISSING_VALUE);
            case TEXT, MOLECULE -> Set.of(FilterCapability.TEXT_CONTAINS, FilterCapability.MISSING_VALUE);
            case BOOLEAN -> Set.of(FilterCapability.MISSING_VALUE);
        };
    }
}

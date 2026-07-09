package tech.molecules.structurized.prism.engine;

import java.util.Objects;

public final class ComputedValueContext {
    private final PrismTable table;
    private final ComputedValueRegistry registry;

    ComputedValueContext(PrismTable table, ComputedValueRegistry registry) {
        this.table = Objects.requireNonNull(table, "table");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public PrismTable table() {
        return table;
    }

    public <T> T value(String computedValueId, int physicalRow, Class<T> valueType) {
        return registry.value(computedValueId, physicalRow, valueType);
    }
}

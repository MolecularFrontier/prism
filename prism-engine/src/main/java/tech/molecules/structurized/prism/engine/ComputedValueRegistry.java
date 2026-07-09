package tech.molecules.structurized.prism.engine;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ComputedValueRegistry {
    private final PrismTable baseTable;
    private final Map<String, ComputedValueDefinition<?>> definitions = new LinkedHashMap<>();
    private final ComputedValueCache cache = new ComputedValueCache();

    public ComputedValueRegistry(PrismTable baseTable) {
        this.baseTable = Objects.requireNonNull(baseTable, "baseTable");
    }

    public PrismTable baseTable() {
        return baseTable;
    }

    public ComputedValueCache cache() {
        return cache;
    }

    public Collection<ComputedValueDefinition<?>> definitions() {
        return List.copyOf(definitions.values());
    }

    public Optional<ComputedValueDefinition<?>> findDefinition(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public ComputedValueDefinition<?> definition(String id) {
        ComputedValueDefinition<?> definition = definitions.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("unknown computed value '" + id + "'");
        }
        return definition;
    }

    public void register(ComputedValueDefinition<?> definition) {
        Objects.requireNonNull(definition, "definition");
        if (definitions.containsKey(definition.id())) {
            throw new IllegalArgumentException("computed value already registered: " + definition.id());
        }
        validateDependencies(definition);
        definitions.put(definition.id(), definition);
        if (definition.cachePolicy() == CachePolicy.PRECOMPUTE) {
            precompute(definition.id());
        }
    }

    public void replace(ComputedValueDefinition<?> definition) {
        Objects.requireNonNull(definition, "definition");
        validateDependencies(definition);
        definitions.put(definition.id(), definition);
        cache.invalidate(definition.id());
        if (definition.cachePolicy() == CachePolicy.PRECOMPUTE) {
            precompute(definition.id());
        }
    }

    public void unregister(String id) {
        definitions.remove(id);
        cache.invalidate(id);
    }

    public void precompute(String id) {
        for (int row = 0; row < baseTable.rowCount(); row++) {
            value(id, row, Object.class);
        }
    }

    public Object value(String id, int physicalRow) {
        return value(id, physicalRow, Object.class);
    }

    public <T> T value(String id, int physicalRow, Class<T> valueType) {
        ComputedValueDefinition<?> definition = definition(id);
        Object value = computeOrRead(definition, physicalRow);
        if (value == null) {
            return null;
        }
        if (valueType == Object.class || valueType.isInstance(value)) {
            return valueType.cast(value);
        }
        throw new ClassCastException("computed value '" + id + "' produced " + value.getClass().getName()
                + " but " + valueType.getName() + " was requested");
    }

    public PrismColumn asColumn(String id) {
        return new ComputedPrismColumn(this, definition(id));
    }

    private Object computeOrRead(ComputedValueDefinition<?> definition, int physicalRow) {
        if (physicalRow < 0 || physicalRow >= baseTable.rowCount()) {
            throw new IndexOutOfBoundsException("physicalRow " + physicalRow + " outside 0.." + (baseTable.rowCount() - 1));
        }
        if (definition.cachePolicy() == CachePolicy.NO_CACHE) {
            return compute(definition, physicalRow);
        }
        ComputedValueKey key = new ComputedValueKey(definition.id(), definition.definitionFingerprint(), physicalRow);
        Optional<Object> cached = cache.get(key);
        if (cached.isPresent()) {
            Object cachedValue = cached.get();
            return cachedValue == ComputedValueCache.NullCachedValue.INSTANCE ? null : cachedValue;
        }
        Object value = compute(definition, physicalRow);
        cache.put(key, value);
        return value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object compute(ComputedValueDefinition definition, int physicalRow) {
        return definition.provider().compute(baseTable, physicalRow, new ComputedValueContext(baseTable, this));
    }

    private void validateDependencies(ComputedValueDefinition<?> definition) {
        for (String columnId : definition.dependencyColumnIds()) {
            baseTable.column(columnId);
        }
        for (String computedValueId : definition.dependencyComputedValueIds()) {
            if (!definitions.containsKey(computedValueId)) {
                throw new IllegalArgumentException("computed value '" + definition.id()
                        + "' depends on unknown computed value '" + computedValueId + "'");
            }
        }
    }
}

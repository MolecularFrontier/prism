package tech.molecules.structurized.prism.engine;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

public final class ComputedValueCache {
    private static final Object NULL_VALUE = new Object();
    private final Map<ComputedValueKey, Object> values = new HashMap<>();

    Optional<Object> get(ComputedValueKey key) {
        if (!values.containsKey(key)) {
            return Optional.empty();
        }
        Object value = values.get(key);
        return Optional.of(value == NULL_VALUE ? NullCachedValue.INSTANCE : value);
    }

    void put(ComputedValueKey key, Object value) {
        values.put(key, value == null ? NULL_VALUE : value);
    }

    public void invalidate(String computedValueId) {
        Iterator<ComputedValueKey> iterator = values.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().computedValueId().equals(computedValueId)) {
                iterator.remove();
            }
        }
    }

    public void clear() {
        values.clear();
    }

    public int size() {
        return values.size();
    }

    enum NullCachedValue {
        INSTANCE
    }
}

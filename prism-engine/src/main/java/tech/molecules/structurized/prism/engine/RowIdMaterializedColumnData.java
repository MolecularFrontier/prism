package tech.molecules.structurized.prism.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RowIdMaterializedColumnData(
        PrismColumnSchema schema,
        Map<String, ?> valuesByRowId,
        Map<String, Object> provenance
) {
    public RowIdMaterializedColumnData {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }
        valuesByRowId = valuesByRowId == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(valuesByRowId));
        provenance = provenance == null || provenance.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(provenance));
    }
}

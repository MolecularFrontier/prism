package tech.molecules.structurized.prism.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MaterializedColumnData(
        PrismColumnSchema schema,
        List<?> values,
        Map<String, Object> provenance
) {
    public MaterializedColumnData {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }
        values = values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
        provenance = provenance == null || provenance.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(provenance));
    }
}

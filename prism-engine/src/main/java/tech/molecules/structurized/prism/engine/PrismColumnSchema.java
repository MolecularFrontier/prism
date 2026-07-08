package tech.molecules.structurized.prism.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record PrismColumnSchema(
        String id,
        PrismColumnType type,
        String displayName,
        String semanticType,
        String role,
        String unit,
        String endpointId,
        String direction,
        String structureFormat,
        Map<String, Object> raw
) {
    public PrismColumnSchema {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("column id must not be blank");
        }
        type = type == null ? PrismColumnType.TEXT : type;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        raw = copyMapAllowingNulls(raw);
    }

    private static Map<String, Object> copyMapAllowingNulls(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}

package tech.molecules.structurized.prism.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record PrismRowGraphEdge(
        String id,
        String sourceRowId,
        String targetRowId,
        String label,
        Map<String, Object> properties
) {
    public PrismRowGraphEdge {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("edge id must not be blank");
        }
        if (sourceRowId == null || sourceRowId.isBlank()) {
            throw new IllegalArgumentException("source row id must not be blank");
        }
        if (targetRowId == null || targetRowId.isBlank()) {
            throw new IllegalArgumentException("target row id must not be blank");
        }
        id = id.trim();
        sourceRowId = sourceRowId.trim();
        targetRowId = targetRowId.trim();
        label = label == null ? "" : label.trim();
        properties = properties == null || properties.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}

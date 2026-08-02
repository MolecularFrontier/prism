package tech.molecules.structurized.prism.engine.live;

import java.util.List;
import java.util.Map;

public record PrismLiveResult(
        String schemaId,
        Map<String, Object> values,
        List<String> warnings,
        Map<String, Object> metadata
) {
    public PrismLiveResult {
        if (schemaId == null || schemaId.isBlank()) {
            throw new IllegalArgumentException("result schema ID must not be blank");
        }
        schemaId = schemaId.trim();
        values = values == null ? Map.of() : Map.copyOf(values);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}

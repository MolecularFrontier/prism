package tech.molecules.structurized.prism.prediction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PredictionMetadata(
        List<PredictionCapability> capabilities,
        Map<String, Object> raw
) {
    public PredictionMetadata {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        raw = raw == null || raw.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(raw));
    }
}

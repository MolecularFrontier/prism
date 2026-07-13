package tech.molecules.structurized.prism.score;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record PropertyProfileItem(
        String endpointId,
        String scoreId,
        String label,
        String group,
        int order,
        boolean visible,
        Map<String, Object> metadata
) {
    public PropertyProfileItem {
        if (endpointId == null || endpointId.isBlank()) {
            throw new IllegalArgumentException("profile item endpointId must not be blank");
        }
        endpointId = endpointId.trim();
        scoreId = normalize(scoreId);
        label = normalize(label);
        group = normalize(group);
        metadata = metadata == null || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public String displayLabel() {
        return label == null ? endpointId : label;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

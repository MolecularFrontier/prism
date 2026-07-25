package tech.molecules.structurized.prism.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record PrismGroupMembership(
        String rowId,
        String groupId,
        Double weight,
        String role,
        Map<String, Object> metadata
) {
    public PrismGroupMembership {
        rowId = requireText(rowId, "membership row id");
        groupId = requireText(groupId, "membership group id");
        if (weight != null && (!Double.isFinite(weight) || weight < 0.0 || weight > 1.0)) {
            throw new IllegalArgumentException("membership weight must be finite and within [0, 1]");
        }
        role = role == null || role.isBlank() ? null : role.trim();
        metadata = metadata == null || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

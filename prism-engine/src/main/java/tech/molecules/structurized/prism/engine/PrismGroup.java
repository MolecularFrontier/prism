package tech.molecules.structurized.prism.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record PrismGroup(
        String id,
        String label,
        String description,
        String parentGroupId,
        String representativeRowId,
        Map<String, Object> metadata
) {
    public PrismGroup {
        id = requireText(id, "group id");
        label = label == null || label.isBlank() ? id : label.trim();
        description = description == null ? "" : description.trim();
        parentGroupId = optionalText(parentGroupId);
        representativeRowId = optionalText(representativeRowId);
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

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

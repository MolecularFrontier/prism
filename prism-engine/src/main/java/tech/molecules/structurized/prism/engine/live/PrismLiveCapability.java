package tech.molecules.structurized.prism.engine.live;

import java.util.Objects;

public record PrismLiveCapability<T>(
        String id,
        String displayName,
        String description,
        Class<T> valueType,
        boolean publishable
) {
    public PrismLiveCapability {
        id = requireText(id, "capability id");
        displayName = requireText(displayName, "capability display name");
        description = description == null ? "" : description.trim();
        Objects.requireNonNull(valueType, "valueType");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

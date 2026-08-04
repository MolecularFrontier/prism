package tech.molecules.structurized.prism.io;

import java.util.LinkedHashMap;
import java.util.Map;

public record PrismSnapshotEndpoint(
        String endpointId,
        String revision,
        Map<String, Object> metadata
) {
    public PrismSnapshotEndpoint {
        endpointId = requireText(endpointId, "endpointId");
        revision = requireText(revision, "revision");
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

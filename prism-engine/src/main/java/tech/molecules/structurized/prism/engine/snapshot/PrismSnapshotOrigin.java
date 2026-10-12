package tech.molecules.structurized.prism.engine.snapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Serializable source identity. Reloadability is supplied separately by the host runtime. */
public record PrismSnapshotOrigin(
        String providerId,
        String repositoryId,
        String repositoryRevision,
        String viewId,
        String createdAt,
        String createdBy,
        Map<String, Object> metadata
) {
    public PrismSnapshotOrigin {
        metadata = metadata == null || metadata.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}

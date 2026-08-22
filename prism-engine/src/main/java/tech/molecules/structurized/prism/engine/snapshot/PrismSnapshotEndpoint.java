package tech.molecules.structurized.prism.engine.snapshot;

import tech.molecules.structurized.prism.model.EndpointDefinition;

import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.LinkedHashMap;

public record PrismSnapshotEndpoint(String id, String columnId, String displayName,
                                    Optional<EndpointDefinition> definition, Map<String, Object> metadata) {
    public PrismSnapshotEndpoint {
        definition = definition == null ? Optional.empty() : definition;
        metadata = metadata == null || metadata.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}

package tech.molecules.structurized.prism.engine;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record PrismViewRecord(
        String id,
        String type,
        String title,
        PrismViewSpec specification,
        Instant createdAt,
        Map<String, Object> provenance
) {
    public PrismViewRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("view id must not be blank");
        }
        id = id.trim();
        specification = Objects.requireNonNull(specification, "specification");
        if (!id.equals(specification.viewId())) {
            throw new IllegalArgumentException("view record id must match specification view id");
        }
        type = type == null || type.isBlank() ? specification.viewType() : type.trim();
        title = title == null || title.isBlank() ? specification.title() : title.trim();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        provenance = provenance == null || provenance.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(provenance));
    }

    public static PrismViewRecord of(PrismViewSpec specification) {
        return new PrismViewRecord(
                specification.viewId(),
                specification.viewType(),
                specification.title(),
                specification,
                Instant.now(),
                Map.of()
        );
    }
}

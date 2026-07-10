package tech.molecules.structurized.prism.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record PrismRowSet(
        String id,
        String name,
        String description,
        Set<String> rowIds,
        Map<String, Object> provenance
) {
    public PrismRowSet {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("row set id must not be blank");
        }
        id = id.trim();
        name = name == null || name.isBlank() ? id : name.trim();
        description = description == null ? "" : description.trim();
        rowIds = rowIds == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(rowIds));
        provenance = provenance == null || provenance.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(provenance));
    }
}

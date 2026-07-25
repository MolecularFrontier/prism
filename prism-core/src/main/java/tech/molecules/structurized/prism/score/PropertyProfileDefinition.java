package tech.molecules.structurized.prism.score;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record PropertyProfileDefinition(
        String id,
        String title,
        String description,
        List<PropertyProfileItem> items,
        List<MpoDefinition> mpos,
        Map<String, Object> metadata
) {
    public PropertyProfileDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("property profile id must not be blank");
        }
        id = id.trim();
        title = title == null || title.isBlank() ? id : title.trim();
        description = description == null || description.isBlank() ? null : description.trim();
        items = (items == null ? List.<PropertyProfileItem>of() : items).stream()
                .sorted(Comparator.comparingInt(PropertyProfileItem::order).thenComparing(PropertyProfileItem::endpointId))
                .toList();
        Set<String> endpointIds = new HashSet<>();
        for (PropertyProfileItem item : items) {
            if (!endpointIds.add(item.endpointId().toLowerCase())) {
                throw new IllegalArgumentException("profile contains duplicate endpointId: " + item.endpointId());
            }
        }
        mpos = mpos == null ? List.of() : List.copyOf(mpos);
        Set<String> mpoIds = new HashSet<>();
        for (MpoDefinition mpo : mpos) {
            if (!mpoIds.add(mpo.id().toLowerCase())) {
                throw new IllegalArgumentException("profile contains duplicate MPO id: " + mpo.id());
            }
        }
        metadata = metadata == null || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}

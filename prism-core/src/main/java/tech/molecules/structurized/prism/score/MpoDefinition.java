package tech.molecules.structurized.prism.score;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record MpoDefinition(
        String id,
        String displayName,
        List<MpoComponentDefinition> components,
        MpoAggregationDefinition aggregation
) {
    public MpoDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("MPO id must not be blank");
        }
        id = id.trim();
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        components = components == null ? List.of() : List.copyOf(components);
        if (components.isEmpty()) {
            throw new IllegalArgumentException("MPO requires at least one component");
        }
        Set<String> endpointIds = new HashSet<>();
        double totalWeight = 0.0;
        for (MpoComponentDefinition component : components) {
            if (!endpointIds.add(component.endpointId().toLowerCase())) {
                throw new IllegalArgumentException("MPO contains duplicate endpointId: " + component.endpointId());
            }
            totalWeight += component.weight();
        }
        if (totalWeight <= 0.0) {
            throw new IllegalArgumentException("MPO requires at least one component with weight > 0");
        }
        aggregation = aggregation == null ? MpoAggregationDefinition.defaults() : aggregation;
    }
}

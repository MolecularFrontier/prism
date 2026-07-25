package tech.molecules.structurized.prism.prediction;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryPredictionCapabilityCatalog implements PredictionCapabilityCatalog {
    private final Map<String, PredictionCapability> byId;

    public InMemoryPredictionCapabilityCatalog(List<PredictionCapability> capabilities) {
        LinkedHashMap<String, PredictionCapability> indexed = new LinkedHashMap<>();
        if (capabilities != null) {
            for (PredictionCapability capability : capabilities) {
                if (indexed.putIfAbsent(capability.capabilityId(), capability) != null) {
                    throw new IllegalArgumentException("duplicate prediction capability '" + capability.capabilityId() + "'");
                }
            }
        }
        byId = Map.copyOf(indexed);
    }

    public static InMemoryPredictionCapabilityCatalog empty() {
        return new InMemoryPredictionCapabilityCatalog(List.of());
    }

    @Override
    public List<PredictionCapability> capabilities() {
        return byId.values().stream()
                .sorted(order())
                .toList();
    }

    @Override
    public List<PredictionCapability> capabilitiesForEndpoint(String endpointId) {
        if (endpointId == null || endpointId.isBlank()) {
            return List.of();
        }
        String normalized = endpointId.trim();
        return byId.values().stream()
                .filter(capability -> capability.endpointId().equals(normalized))
                .sorted(order())
                .toList();
    }

    @Override
    public Optional<PredictionCapability> findCapability(String capabilityId) {
        if (capabilityId == null || capabilityId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(capabilityId.trim()));
    }

    private static Comparator<PredictionCapability> order() {
        return Comparator.comparingInt(PredictionCapability::priority).reversed()
                .thenComparing(PredictionCapability::capabilityId);
    }
}

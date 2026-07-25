package tech.molecules.structurized.prism.prediction;

import java.util.List;
import java.util.Optional;

public interface PredictionCapabilityCatalog {
    List<PredictionCapability> capabilities();

    List<PredictionCapability> capabilitiesForEndpoint(String endpointId);

    Optional<PredictionCapability> findCapability(String capabilityId);
}

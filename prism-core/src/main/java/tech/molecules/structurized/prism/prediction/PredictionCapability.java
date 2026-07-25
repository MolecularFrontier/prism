package tech.molecules.structurized.prism.prediction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record PredictionCapability(
        String capabilityId,
        String endpointId,
        String predictedEndpointId,
        String displayName,
        String providerId,
        String workflowId,
        String workflowVersion,
        String status,
        int priority,
        String structureColumn,
        String structureFormat,
        Map<String, Object> metadata
) {
    public PredictionCapability {
        if (capabilityId == null || capabilityId.isBlank()) {
            throw new IllegalArgumentException("capabilityId must not be blank");
        }
        if (endpointId == null || endpointId.isBlank()) {
            throw new IllegalArgumentException("endpointId must not be blank");
        }
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflowId must not be blank");
        }
        capabilityId = capabilityId.trim();
        endpointId = endpointId.trim();
        predictedEndpointId = predictedEndpointId == null || predictedEndpointId.isBlank()
                ? endpointId + ".predicted"
                : predictedEndpointId.trim();
        displayName = displayName == null || displayName.isBlank() ? capabilityId : displayName.trim();
        providerId = providerId.trim();
        workflowId = workflowId.trim();
        workflowVersion = workflowVersion == null || workflowVersion.isBlank() ? null : workflowVersion.trim();
        status = status == null || status.isBlank() ? "available" : status.trim();
        structureColumn = structureColumn == null || structureColumn.isBlank() ? null : structureColumn.trim();
        structureFormat = structureFormat == null || structureFormat.isBlank() ? null : structureFormat.trim();
        metadata = metadata == null || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}

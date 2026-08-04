package tech.molecules.structurized.prism.io;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PrismSnapshotDescriptor(
        String captureStartedAt,
        String captureCompletedAt,
        String publisherId,
        String publisherVersion,
        String sourceRef,
        String subjectAggregationLevel,
        String structureColumn,
        String structureFormat,
        String structureStandardization,
        PrismSnapshotSelection selection,
        List<PrismSnapshotEndpoint> endpoints,
        Map<String, String> subjectSetRevisions,
        Map<String, Object> metadata
) {
    public PrismSnapshotDescriptor {
        captureStartedAt = requireText(captureStartedAt, "captureStartedAt");
        captureCompletedAt = requireText(captureCompletedAt, "captureCompletedAt");
        publisherId = requireText(publisherId, "publisherId");
        publisherVersion = requireText(publisherVersion, "publisherVersion");
        sourceRef = requireText(sourceRef, "sourceRef");
        subjectAggregationLevel = requireText(subjectAggregationLevel, "subjectAggregationLevel");
        structureColumn = requireText(structureColumn, "structureColumn");
        structureFormat = requireText(structureFormat, "structureFormat");
        structureStandardization = requireText(structureStandardization, "structureStandardization");
        if (selection == null) throw new IllegalArgumentException("selection must not be null");
        ArrayList<PrismSnapshotEndpoint> orderedEndpoints = new ArrayList<>(endpoints == null ? List.of() : endpoints);
        orderedEndpoints.sort(Comparator.comparing(PrismSnapshotEndpoint::endpointId));
        endpoints = List.copyOf(orderedEndpoints);
        LinkedHashMap<String, String> orderedRevisions = new LinkedHashMap<>();
        (subjectSetRevisions == null ? Map.<String, String>of() : subjectSetRevisions).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> orderedRevisions.put(requireText(entry.getKey(), "subjectSetId"), requireText(entry.getValue(), "subjectSetRevision")));
        subjectSetRevisions = Map.copyOf(orderedRevisions);
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

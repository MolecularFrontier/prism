package tech.molecules.structurized.prism.provider.inmemory;

import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.provider.SubjectRecord;
import tech.molecules.structurized.prism.provider.SubjectSet;
import tech.molecules.structurized.prism.query.EndpointValueRecord;
import tech.molecules.structurized.prism.validation.EndpointResultValidator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable in-memory PRISM dataset used by the reference provider implementations.
 */
public final class InMemoryPrismDataset {
    private final List<EndpointDefinition> endpointDefinitions;
    private final Map<String, EndpointDefinition> endpointDefinitionsById;
    private final List<SubjectRecord> subjectRecords;
    private final Map<String, SubjectRecord> subjectRecordsById;
    private final List<SubjectSet> subjectSets;
    private final Map<String, SubjectSet> subjectSetsById;
    private final Map<String, List<String>> subjectsBySetId;
    private final List<EndpointValueRecord> endpointValues;
    private final Map<String, Map<String, EndpointValueRecord>> endpointValuesBySubjectId;

    private InMemoryPrismDataset(Builder builder) {
        this.endpointDefinitionsById = toEndpointDefinitionMap(builder.endpointDefinitions);
        this.endpointDefinitions = List.copyOf(endpointDefinitionsById.values());

        this.subjectRecordsById = toSubjectRecordMap(builder.subjectRecords);
        this.subjectRecords = List.copyOf(subjectRecordsById.values());

        this.subjectSetsById = toSubjectSetMap(builder.subjectSets);
        this.subjectSets = List.copyOf(subjectSetsById.values());

        this.subjectsBySetId = buildMembershipIndex(subjectSetsById.keySet(), subjectRecordsById.keySet(), builder.subjectIdsBySetId);

        EndpointValueIndex valueIndex = buildEndpointValues(builder.endpointValues, endpointDefinitionsById, subjectRecordsById);
        this.endpointValues = valueIndex.endpointValues();
        this.endpointValuesBySubjectId = valueIndex.valuesBySubjectId();
    }

    public List<EndpointDefinition> getEndpointDefinitions() {
        return endpointDefinitions;
    }

    public Optional<EndpointDefinition> findEndpointDefinition(String endpointId) {
        return Optional.ofNullable(endpointDefinitionsById.get(endpointId));
    }

    public List<SubjectRecord> getSubjectRecords() {
        return subjectRecords;
    }

    public Optional<SubjectRecord> findSubjectRecord(String subjectId) {
        return Optional.ofNullable(subjectRecordsById.get(subjectId));
    }

    public List<SubjectSet> getSubjectSets() {
        return subjectSets;
    }

    public Optional<SubjectSet> findSubjectSet(String subjectSetId) {
        return Optional.ofNullable(subjectSetsById.get(subjectSetId));
    }

    public List<String> getSubjectsForSet(String subjectSetId) {
        return subjectsBySetId.getOrDefault(subjectSetId, List.of());
    }

    public List<EndpointValueRecord> getEndpointValues() {
        return endpointValues;
    }

    public Optional<EndpointValueRecord> findEndpointValue(String subjectId, String endpointId) {
        Map<String, EndpointValueRecord> byEndpoint = endpointValuesBySubjectId.get(subjectId);
        if (byEndpoint == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byEndpoint.get(endpointId));
    }

    public InMemoryEndpointProvider endpointProvider() {
        return new InMemoryEndpointProvider(this);
    }

    public InMemorySubjectSetProvider subjectSetProvider() {
        return new InMemorySubjectSetProvider(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<EndpointDefinition> endpointDefinitions = List.of();
        private List<SubjectRecord> subjectRecords = List.of();
        private List<SubjectSet> subjectSets = List.of();
        private Map<String, List<String>> subjectIdsBySetId = Map.of();
        private List<EndpointValueRecord> endpointValues = List.of();

        private Builder() {}

        public Builder endpointDefinitions(Collection<EndpointDefinition> endpointDefinitions) {
            this.endpointDefinitions = endpointDefinitions == null ? List.of() : List.copyOf(endpointDefinitions);
            return this;
        }

        public Builder addEndpointDefinition(EndpointDefinition endpointDefinition) {
            Objects.requireNonNull(endpointDefinition, "endpointDefinition must not be null");
            List<EndpointDefinition> next = new ArrayList<>(this.endpointDefinitions);
            next.add(endpointDefinition);
            this.endpointDefinitions = List.copyOf(next);
            return this;
        }

        public Builder subjectRecords(Collection<SubjectRecord> subjectRecords) {
            this.subjectRecords = subjectRecords == null ? List.of() : List.copyOf(subjectRecords);
            return this;
        }

        public Builder addSubjectRecord(SubjectRecord subjectRecord) {
            Objects.requireNonNull(subjectRecord, "subjectRecord must not be null");
            List<SubjectRecord> next = new ArrayList<>(this.subjectRecords);
            next.add(subjectRecord);
            this.subjectRecords = List.copyOf(next);
            return this;
        }

        public Builder subjectSets(Collection<SubjectSet> subjectSets) {
            this.subjectSets = subjectSets == null ? List.of() : List.copyOf(subjectSets);
            return this;
        }

        public Builder addSubjectSet(SubjectSet subjectSet) {
            Objects.requireNonNull(subjectSet, "subjectSet must not be null");
            List<SubjectSet> next = new ArrayList<>(this.subjectSets);
            next.add(subjectSet);
            this.subjectSets = List.copyOf(next);
            return this;
        }

        public Builder addSubjectMembership(String subjectSetId, String subjectId) {
            String normalizedSetId = requireText(subjectSetId, "subjectSetId");
            String normalizedSubjectId = requireText(subjectId, "subjectId");
            LinkedHashMap<String, List<String>> next = new LinkedHashMap<>(this.subjectIdsBySetId);
            List<String> current = next.getOrDefault(normalizedSetId, List.of());
            List<String> updated = new ArrayList<>(current);
            updated.add(normalizedSubjectId);
            next.put(normalizedSetId, List.copyOf(updated));
            this.subjectIdsBySetId = Map.copyOf(next);
            return this;
        }

        public Builder endpointValues(Collection<EndpointValueRecord> endpointValues) {
            this.endpointValues = endpointValues == null ? List.of() : List.copyOf(endpointValues);
            return this;
        }

        public Builder addEndpointValue(EndpointValueRecord endpointValue) {
            Objects.requireNonNull(endpointValue, "endpointValue must not be null");
            List<EndpointValueRecord> next = new ArrayList<>(this.endpointValues);
            next.add(endpointValue);
            this.endpointValues = List.copyOf(next);
            return this;
        }

        public InMemoryPrismDataset build() {
            return new InMemoryPrismDataset(this);
        }
    }

    private static Map<String, EndpointDefinition> toEndpointDefinitionMap(List<EndpointDefinition> endpointDefinitions) {
        LinkedHashMap<String, EndpointDefinition> byId = new LinkedHashMap<>();
        for (EndpointDefinition endpointDefinition : endpointDefinitions) {
            Objects.requireNonNull(endpointDefinition, "endpointDefinition must not be null");
            EndpointDefinition previous = byId.putIfAbsent(endpointDefinition.getId(), endpointDefinition);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate endpoint id '" + endpointDefinition.getId() + "'");
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    private static Map<String, SubjectRecord> toSubjectRecordMap(List<SubjectRecord> subjectRecords) {
        LinkedHashMap<String, SubjectRecord> byId = new LinkedHashMap<>();
        for (SubjectRecord subjectRecord : subjectRecords) {
            Objects.requireNonNull(subjectRecord, "subjectRecord must not be null");
            SubjectRecord previous = byId.putIfAbsent(subjectRecord.getSubjectId(), subjectRecord);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate subject id '" + subjectRecord.getSubjectId() + "'");
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    private static Map<String, SubjectSet> toSubjectSetMap(List<SubjectSet> subjectSets) {
        LinkedHashMap<String, SubjectSet> byId = new LinkedHashMap<>();
        for (SubjectSet subjectSet : subjectSets) {
            Objects.requireNonNull(subjectSet, "subjectSet must not be null");
            SubjectSet previous = byId.putIfAbsent(subjectSet.getId(), subjectSet);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate subject set id '" + subjectSet.getId() + "'");
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    private static Map<String, List<String>> buildMembershipIndex(Set<String> subjectSetIds,
                                                                  Set<String> subjectIds,
                                                                  Map<String, List<String>> sourceMemberships) {
        LinkedHashMap<String, List<String>> bySet = new LinkedHashMap<>();
        for (String subjectSetId : subjectSetIds) {
            List<String> input = sourceMemberships.getOrDefault(subjectSetId, List.of());
            LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
            for (String subjectId : input) {
                String normalizedSubjectId = requireText(subjectId, "subjectId");
                if (!subjectIds.contains(normalizedSubjectId)) {
                    throw new IllegalArgumentException("unknown subject id '" + normalizedSubjectId
                            + "' in subject set '" + subjectSetId + "'");
                }
                deduplicated.add(normalizedSubjectId);
            }
            bySet.put(subjectSetId, List.copyOf(deduplicated));
        }

        for (String subjectSetId : sourceMemberships.keySet()) {
            if (!subjectSetIds.contains(subjectSetId)) {
                throw new IllegalArgumentException("unknown subject set id '" + subjectSetId + "' in membership data");
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(bySet));
    }

    private static EndpointValueIndex buildEndpointValues(List<EndpointValueRecord> endpointValues,
                                                          Map<String, EndpointDefinition> endpointDefinitionsById,
                                                          Map<String, SubjectRecord> subjectRecordsById) {
        LinkedHashMap<String, Map<String, EndpointValueRecord>> bySubjectId = new LinkedHashMap<>();
        ArrayList<EndpointValueRecord> orderedValues = new ArrayList<>();

        for (EndpointValueRecord endpointValue : endpointValues) {
            Objects.requireNonNull(endpointValue, "endpointValue must not be null");
            EndpointDefinition definition = endpointDefinitionsById.get(endpointValue.getEndpointId());
            if (definition == null) {
                throw new IllegalArgumentException("unknown endpoint id '" + endpointValue.getEndpointId()
                        + "' for subject '" + endpointValue.getSubjectId() + "'");
            }
            if (!subjectRecordsById.containsKey(endpointValue.getSubjectId())) {
                throw new IllegalArgumentException("unknown subject id '" + endpointValue.getSubjectId()
                        + "' for endpoint '" + endpointValue.getEndpointId() + "'");
            }
            EndpointResultValidator.validate(definition, endpointValue.getResult());

            LinkedHashMap<String, EndpointValueRecord> byEndpoint =
                    new LinkedHashMap<>(bySubjectId.getOrDefault(endpointValue.getSubjectId(), Map.of()));
            EndpointValueRecord previous = byEndpoint.putIfAbsent(endpointValue.getEndpointId(), endpointValue);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate endpoint value for subject '" + endpointValue.getSubjectId()
                        + "' and endpoint '" + endpointValue.getEndpointId() + "'");
            }
            bySubjectId.put(endpointValue.getSubjectId(), Collections.unmodifiableMap(new LinkedHashMap<>(byEndpoint)));
            orderedValues.add(endpointValue);
        }

        return new EndpointValueIndex(List.copyOf(orderedValues), Collections.unmodifiableMap(new LinkedHashMap<>(bySubjectId)));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private record EndpointValueIndex(List<EndpointValueRecord> endpointValues,
                                      Map<String, Map<String, EndpointValueRecord>> valuesBySubjectId) {
    }
}

package tech.molecules.structurized.prism.provider.inmemory;

import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.provider.EndpointProvider;
import tech.molecules.structurized.prism.query.EndpointFetchRequest;
import tech.molecules.structurized.prism.query.EndpointValueRecord;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory {@link EndpointProvider} backed by an {@link InMemoryPrismDataset}.
 */
public final class InMemoryEndpointProvider implements EndpointProvider {
    private final InMemoryPrismDataset dataset;

    public InMemoryEndpointProvider(InMemoryPrismDataset dataset) {
        this.dataset = Objects.requireNonNull(dataset, "dataset must not be null");
    }

    @Override
    public List<EndpointDefinition> listEndpointDefinitions() {
        return dataset.getEndpointDefinitions();
    }

    @Override
    public Optional<EndpointDefinition> findEndpointDefinition(String endpointId) {
        return dataset.findEndpointDefinition(endpointId);
    }

    @Override
    public List<EndpointValueRecord> fetchEndpointValues(EndpointFetchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LinkedHashSet<String> subjectIds = new LinkedHashSet<>(request.getSubjectIds());
        LinkedHashSet<String> endpointIds = new LinkedHashSet<>(request.getEndpointIds());
        if (subjectIds.isEmpty() || endpointIds.isEmpty()) {
            return List.of();
        }

        ArrayList<EndpointValueRecord> values = new ArrayList<>();
        for (String subjectId : subjectIds) {
            for (String endpointId : endpointIds) {
                dataset.findEndpointValue(subjectId, endpointId).ifPresent(values::add);
            }
        }
        return List.copyOf(values);
    }
}

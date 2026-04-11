package tech.molecules.structurized.prism.provider;

import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.query.EndpointFetchRequest;
import tech.molecules.structurized.prism.query.EndpointValueRecord;

import java.util.List;
import java.util.Optional;

/**
 * Bulk retrieval API for PRISM endpoint values.
 */
public interface EndpointProvider {
    List<EndpointDefinition> listEndpointDefinitions();

    Optional<EndpointDefinition> findEndpointDefinition(String endpointId);

    List<EndpointValueRecord> fetchEndpointValues(EndpointFetchRequest request);
}

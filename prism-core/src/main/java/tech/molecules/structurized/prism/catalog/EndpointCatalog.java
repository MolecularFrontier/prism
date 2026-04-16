package tech.molecules.structurized.prism.catalog;

import tech.molecules.structurized.prism.model.EndpointDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Read-only catalog of known endpoint definitions.
 */
public interface EndpointCatalog {
    List<EndpointDefinition> listEndpointDefinitions();

    Optional<EndpointDefinition> findEndpointDefinition(String endpointId);
}

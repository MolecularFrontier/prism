package tech.molecules.structurized.prism.registry;

import tech.molecules.structurized.prism.model.EndpointDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Read-only registry of known endpoint definitions.
 */
public interface EndpointRegistry {
    List<EndpointDefinition> listEndpointDefinitions();

    Optional<EndpointDefinition> findEndpointDefinition(String endpointId);
}

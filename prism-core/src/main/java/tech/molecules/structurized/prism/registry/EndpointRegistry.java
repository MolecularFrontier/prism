package tech.molecules.structurized.prism.registry;

import tech.molecules.structurized.prism.catalog.EndpointCatalog;
import tech.molecules.structurized.prism.model.EndpointDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Read-only registry of known endpoint definitions.
 */
@Deprecated(forRemoval = false)
public interface EndpointRegistry extends EndpointCatalog {
    @Override
    List<EndpointDefinition> listEndpointDefinitions();

    @Override
    Optional<EndpointDefinition> findEndpointDefinition(String endpointId);
}

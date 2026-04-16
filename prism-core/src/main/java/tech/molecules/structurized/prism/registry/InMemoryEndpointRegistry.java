package tech.molecules.structurized.prism.registry;

import tech.molecules.structurized.prism.catalog.InMemoryEndpointCatalog;
import tech.molecules.structurized.prism.model.EndpointDefinition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Simple deterministic in-memory endpoint registry.
 */
@Deprecated(forRemoval = false)
public final class InMemoryEndpointRegistry extends InMemoryEndpointCatalog implements EndpointRegistry {

    public InMemoryEndpointRegistry(Collection<EndpointDefinition> definitions) {
        super(definitions);
    }

    @Override
    public List<EndpointDefinition> listEndpointDefinitions() {
        return super.listEndpointDefinitions();
    }

    @Override
    public Optional<EndpointDefinition> findEndpointDefinition(String endpointId) {
        return super.findEndpointDefinition(endpointId);
    }
}

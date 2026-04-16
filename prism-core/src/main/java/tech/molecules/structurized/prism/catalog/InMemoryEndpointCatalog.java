package tech.molecules.structurized.prism.catalog;

import tech.molecules.structurized.prism.model.EndpointDefinition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Simple deterministic in-memory endpoint catalog.
 */
public class InMemoryEndpointCatalog implements EndpointCatalog {
    private final Map<String, EndpointDefinition> definitionsById;
    private final List<EndpointDefinition> definitions;

    public InMemoryEndpointCatalog(Collection<EndpointDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions must not be null");
        LinkedHashMap<String, EndpointDefinition> byId = new LinkedHashMap<>();
        for (EndpointDefinition definition : definitions) {
            Objects.requireNonNull(definition, "definition must not be null");
            EndpointDefinition previous = byId.putIfAbsent(definition.getId(), definition);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate endpoint id '" + definition.getId() + "'");
            }
        }
        this.definitionsById = Map.copyOf(byId);
        this.definitions = List.copyOf(byId.values());
    }

    @Override
    public List<EndpointDefinition> listEndpointDefinitions() {
        return definitions;
    }

    @Override
    public Optional<EndpointDefinition> findEndpointDefinition(String endpointId) {
        if (endpointId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitionsById.get(endpointId));
    }
}

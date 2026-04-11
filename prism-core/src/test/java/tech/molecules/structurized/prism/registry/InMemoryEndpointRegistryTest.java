package tech.molecules.structurized.prism.registry;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.model.EndpointDataType;
import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.model.EndpointType;
import tech.molecules.structurized.prism.model.EvaluationMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryEndpointRegistryTest {

    @Test
    void duplicateEndpointIdsAreRejected() {
        EndpointDefinition first = endpoint("ic50", "IC50");
        EndpointDefinition duplicate = endpoint("ic50", "IC50 Duplicate");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new InMemoryEndpointRegistry(List.of(first, duplicate)));

        assertEquals("duplicate endpoint id 'ic50'", ex.getMessage());
    }

    @Test
    void registryPreservesDefinitionOrderAndSupportsLookup() {
        EndpointDefinition first = endpoint("ic50", "IC50");
        EndpointDefinition second = endpoint("active", "Active");

        InMemoryEndpointRegistry registry = new InMemoryEndpointRegistry(List.of(first, second));

        assertEquals(List.of(first, second), registry.listEndpointDefinitions());
        assertTrue(registry.findEndpointDefinition("active").isPresent());
        assertEquals(second, registry.findEndpointDefinition("active").orElseThrow());
    }

    private static EndpointDefinition endpoint(String id, String name) {
        return EndpointDefinition.builder()
                .id(id)
                .name(name)
                .path("assay/" + id)
                .datatype(EndpointDataType.NUMERIC)
                .endpointType(EndpointType.MEASURED)
                .evaluationMode(EvaluationMode.IMMEDIATE)
                .build();
    }
}

package tech.molecules.structurized.prism.catalog;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.model.EndpointDataType;
import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.model.EndpointType;
import tech.molecules.structurized.prism.model.EvaluationMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryEndpointCatalogTest {

    @Test
    void duplicateEndpointIdsAreRejected() {
        EndpointDefinition first = endpoint("ic50", "IC50");
        EndpointDefinition duplicate = endpoint("ic50", "IC50 Duplicate");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new InMemoryEndpointCatalog(List.of(first, duplicate)));

        assertEquals("duplicate endpoint id 'ic50'", ex.getMessage());
    }

    @Test
    void catalogPreservesDefinitionOrderAndSupportsLookup() {
        EndpointDefinition first = endpoint("ic50", "IC50");
        EndpointDefinition second = endpoint("active", "Active");

        InMemoryEndpointCatalog catalog = new InMemoryEndpointCatalog(List.of(first, second));

        assertEquals(List.of(first, second), catalog.listEndpointDefinitions());
        assertTrue(catalog.findEndpointDefinition("active").isPresent());
        assertEquals(second, catalog.findEndpointDefinition("active").orElseThrow());
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

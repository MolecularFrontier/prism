package tech.molecules.structurized.prism.validation;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.model.CategoryDefinition;
import tech.molecules.structurized.prism.model.EndpointDataType;
import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.model.EndpointType;
import tech.molecules.structurized.prism.model.EvaluationMode;
import tech.molecules.structurized.prism.model.NumericEndpointMeta;
import tech.molecules.structurized.prism.model.NumericScale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EndpointDefinitionValidatorTest {

    @Test
    void categoricalEndpointRequiresCategories() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> baseDefinition()
                .datatype(EndpointDataType.CATEGORICAL)
                .build());

        assertEquals("categorical endpoint 'potency_class' must define categories", ex.getMessage());
    }

    @Test
    void nonCategoricalEndpointMustNotDefineCategories() {
        CategoryDefinition active = CategoryDefinition.builder()
                .id("active")
                .name("Active")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> baseDefinition()
                .datatype(EndpointDataType.NUMERIC)
                .addCategory(active)
                .build());

        assertEquals("non-categorical endpoint 'potency_class' must not define categories", ex.getMessage());
    }

    @Test
    void duplicateCategoryIdsAreRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> baseDefinition()
                .datatype(EndpointDataType.CATEGORICAL)
                .addCategory(CategoryDefinition.builder().id("active").name("Active").build())
                .addCategory(CategoryDefinition.builder().id("active").name("Inactive").build())
                .build());

        assertEquals("duplicate category id 'active' in endpoint 'potency_class'", ex.getMessage());
    }

    @Test
    void validCategoricalEndpointBuildsCleanly() {
        EndpointDefinition definition = assertDoesNotThrow(() -> baseDefinition()
                .datatype(EndpointDataType.CATEGORICAL)
                .addCategory(CategoryDefinition.builder().id("active").name("Active").build())
                .addCategory(CategoryDefinition.builder().id("inactive").name("Inactive").build())
                .build());

        assertEquals(2, definition.getCategories().size());
    }

    @Test
    void numericEndpointCanDefineNumericMeta() {
        EndpointDefinition definition = assertDoesNotThrow(() -> baseDefinition()
                .datatype(EndpointDataType.NUMERIC)
                .numericMeta(NumericEndpointMeta.builder()
                        .scale(NumericScale.LOG)
                        .domainLowerBound(0.001)
                        .domainUpperBound(10000.0)
                        .build())
                .build());

        assertEquals(NumericScale.LOG, definition.getNumericMeta().getScale());
        assertEquals(0.001, definition.getNumericMeta().getDomainLowerBound());
    }

    @Test
    void nonNumericEndpointMustNotDefineNumericMeta() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> baseDefinition()
                .datatype(EndpointDataType.TEXT)
                .numericMeta(NumericEndpointMeta.builder()
                        .scale(NumericScale.ABSOLUTE)
                        .build())
                .build());

        assertEquals("non-numeric endpoint 'potency_class' must not define numericMeta", ex.getMessage());
    }

    private static EndpointDefinition.Builder baseDefinition() {
        return EndpointDefinition.builder()
                .id("potency_class")
                .name("Potency Class")
                .path("project/potency_class")
                .endpointType(EndpointType.MEASURED)
                .evaluationMode(EvaluationMode.IMMEDIATE);
    }
}

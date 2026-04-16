package tech.molecules.structurized.prism.validation;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.model.CategoryDefinition;
import tech.molecules.structurized.prism.model.EndpointDataType;
import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.model.EndpointType;
import tech.molecules.structurized.prism.model.EvaluationMode;
import tech.molecules.structurized.prism.result.CategoricalResult;
import tech.molecules.structurized.prism.result.NumericResult;
import tech.molecules.structurized.prism.result.NumericState;
import tech.molecules.structurized.prism.result.OptionalNumericResult;
import tech.molecules.structurized.prism.result.OptionalNumericState;
import tech.molecules.structurized.prism.result.PrismNumericDatapoint;
import tech.molecules.structurized.prism.result.TextResult;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EndpointResultValidatorTest {

    @Test
    void numericResultMatchesNumericEndpoint() {
        EndpointDefinition definition = EndpointDefinition.builder()
                .id("ic50")
                .name("IC50")
                .path("assay/ic50")
                .datatype(EndpointDataType.NUMERIC)
                .endpointType(EndpointType.MEASURED)
                .evaluationMode(EvaluationMode.IMMEDIATE)
                .build();

        NumericResult result = NumericResult.builder()
                .mean(7.2)
                .addRawValue(7.1)
                .addRawValue(7.3)
                .addRawValueId("raw-1")
                .build();

        assertDoesNotThrow(() -> EndpointResultValidator.validate(definition, result));
    }

    @Test
    void numericResultCanCarryStructuredDatapoints() {
        EndpointDefinition definition = EndpointDefinition.builder()
                .id("ic50")
                .name("IC50")
                .path("assay/ic50")
                .datatype(EndpointDataType.NUMERIC)
                .endpointType(EndpointType.MEASURED)
                .evaluationMode(EvaluationMode.IMMEDIATE)
                .build();

        PrismNumericDatapoint datapoint = PrismNumericDatapoint.builder()
                .date("2026-04-16T09:15:00Z")
                .batch("batch-7")
                .sourceId("raw-1")
                .value(7.1)
                .unprocessedValue(">7.1")
                .putMetadata("sourceSystem", "osiris")
                .build();

        NumericResult result = NumericResult.builder()
                .mean(7.2)
                .addRawValue(7.1)
                .addRawValueId("raw-1")
                .addDatapoint(datapoint)
                .build();

        assertDoesNotThrow(() -> EndpointResultValidator.validate(definition, result));
    }

    @Test
    void numericResultCanRepresentNotMeasured() {
        EndpointDefinition definition = EndpointDefinition.builder()
                .id("ic50")
                .name("IC50")
                .path("assay/ic50")
                .datatype(EndpointDataType.NUMERIC)
                .endpointType(EndpointType.MEASURED)
                .evaluationMode(EvaluationMode.IMMEDIATE)
                .build();

        NumericResult result = NumericResult.builder()
                .state(NumericState.NOT_MEASURED)
                .build();

        assertDoesNotThrow(() -> EndpointResultValidator.validate(definition, result));
    }

    @Test
    void mismatchedResultTypeIsRejected() {
        EndpointDefinition definition = EndpointDefinition.builder()
                .id("active")
                .name("Active")
                .path("assay/active")
                .datatype(EndpointDataType.BOOLEAN)
                .endpointType(EndpointType.MEASURED)
                .evaluationMode(EvaluationMode.IMMEDIATE)
                .build();

        TextResult result = TextResult.builder()
                .text("yes")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> EndpointResultValidator.validate(definition, result));

        assertEquals("result type TEXT does not match endpoint datatype BOOLEAN for endpoint 'active'", ex.getMessage());
    }

    @Test
    void categoricalResultMustUseDefinedCategoryId() {
        EndpointDefinition definition = EndpointDefinition.builder()
                .id("potency_class")
                .name("Potency Class")
                .path("assay/potency_class")
                .datatype(EndpointDataType.CATEGORICAL)
                .endpointType(EndpointType.DERIVED)
                .evaluationMode(EvaluationMode.ON_DEMAND)
                .addCategory(CategoryDefinition.builder().id("active").name("Active").build())
                .addCategory(CategoryDefinition.builder().id("inactive").name("Inactive").build())
                .build();

        CategoricalResult result = CategoricalResult.builder()
                .value("unknown")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> EndpointResultValidator.validate(definition, result));

        assertEquals("categorical value 'unknown' is not defined for endpoint 'potency_class'", ex.getMessage());
    }

    @Test
    void optionalNumericBuilderRejectsMissingMeanInValueState() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> OptionalNumericResult.builder()
                .state(OptionalNumericState.VALUE)
                .build());

        assertEquals("optional numeric result in VALUE state must provide mean", ex.getMessage());
    }

    @Test
    void optionalNumericBuilderRejectsMeanOutsideValueState() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> OptionalNumericResult.builder()
                .state(OptionalNumericState.NOT_MEASURED)
                .mean(4.5)
                .build());

        assertEquals("optional numeric result without VALUE state must not provide mean/lower/upper", ex.getMessage());
    }

    @Test
    void numericBuilderRejectsMeanOutsideMeasuredState() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> NumericResult.builder()
                .state(NumericState.NOT_MEASURED)
                .mean(4.5)
                .build());

        assertEquals("numeric result in NOT_MEASURED state must not provide mean/lower/upper", ex.getMessage());
    }

    @Test
    void optionalNumericResultCanCarryStructuredDatapoints() {
        EndpointDefinition definition = EndpointDefinition.builder()
                .id("ic50_optional")
                .name("IC50 Optional")
                .path("assay/ic50_optional")
                .datatype(EndpointDataType.OPTIONAL_NUMERIC)
                .endpointType(EndpointType.MEASURED)
                .evaluationMode(EvaluationMode.IMMEDIATE)
                .build();

        PrismNumericDatapoint datapoint = PrismNumericDatapoint.builder()
                .date("2026-04-16")
                .sourceId("raw-optional-1")
                .value(4.5)
                .build();

        OptionalNumericResult result = OptionalNumericResult.builder()
                .state(OptionalNumericState.VALUE)
                .mean(4.5)
                .addDatapoint(datapoint)
                .build();

        assertDoesNotThrow(() -> EndpointResultValidator.validate(definition, result));
    }
}

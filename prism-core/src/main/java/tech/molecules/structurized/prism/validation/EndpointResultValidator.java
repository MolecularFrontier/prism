package tech.molecules.structurized.prism.validation;

import tech.molecules.structurized.prism.model.EndpointDataType;
import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.result.CategoricalResult;
import tech.molecules.structurized.prism.result.EndpointResult;
import tech.molecules.structurized.prism.result.NumericResult;
import tech.molecules.structurized.prism.result.PrismNumericDatapoint;
import tech.molecules.structurized.prism.result.NumericState;
import tech.molecules.structurized.prism.result.OptionalNumericResult;
import tech.molecules.structurized.prism.result.OptionalNumericState;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Static validation helpers for PRISM endpoint payloads.
 */
public final class EndpointResultValidator {
    private EndpointResultValidator() {}

    public static void validate(EndpointDefinition definition, EndpointResult result) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(result, "result must not be null");
        EndpointDefinitionValidator.validate(definition);

        if (result.getType() != definition.getDatatype()) {
            throw new IllegalArgumentException(
                    "result type " + result.getType() + " does not match endpoint datatype " + definition.getDatatype()
                            + " for endpoint '" + definition.getId() + "'"
            );
        }

        Objects.requireNonNull(result.getRawValueIds(), "rawValueIds must not be null");
        Objects.requireNonNull(result.getDetails(), "details must not be null");

        if (definition.getDatatype() == EndpointDataType.CATEGORICAL) {
            validateCategorical(definition, (CategoricalResult) result);
        } else if (definition.getDatatype() == EndpointDataType.NUMERIC) {
            validateNumeric((NumericResult) result);
        } else if (definition.getDatatype() == EndpointDataType.OPTIONAL_NUMERIC) {
            validateOptionalNumeric((OptionalNumericResult) result);
        }
    }

    private static void validateCategorical(EndpointDefinition definition, CategoricalResult result) {
        Set<String> categoryIds = definition.getCategories().stream()
                .map(category -> category.getId())
                .collect(Collectors.toSet());
        if (!categoryIds.contains(result.getValue())) {
            throw new IllegalArgumentException(
                    "categorical value '" + result.getValue() + "' is not defined for endpoint '" + definition.getId() + "'"
            );
        }
    }

    private static void validateNumeric(NumericResult result) {
        Objects.requireNonNull(result.getRawValues(), "rawValues must not be null");
        Objects.requireNonNull(result.getDatapoints(), "datapoints must not be null");
        validateDatapoints(result.getDatapoints());
        if (result.getState() == NumericState.VALUE && result.getMean() == null) {
            throw new IllegalArgumentException("numeric result in VALUE state must provide mean");
        }
        if (result.getState() == NumericState.NOT_MEASURED
                && (result.getMean() != null || result.getLower() != null || result.getUpper() != null)) {
            throw new IllegalArgumentException("numeric result in NOT_MEASURED state must not provide mean/lower/upper");
        }
    }

    private static void validateOptionalNumeric(OptionalNumericResult result) {
        Objects.requireNonNull(result.getRawValues(), "rawValues must not be null");
        Objects.requireNonNull(result.getDatapoints(), "datapoints must not be null");
        validateDatapoints(result.getDatapoints());
        if (result.getState() == OptionalNumericState.VALUE && result.getMean() == null) {
            throw new IllegalArgumentException("optional numeric result in VALUE state must provide mean");
        }
        if (result.getState() != OptionalNumericState.VALUE
                && (result.getMean() != null || result.getLower() != null || result.getUpper() != null)) {
            throw new IllegalArgumentException("optional numeric result without VALUE state must not provide mean/lower/upper");
        }
    }

    private static void validateDatapoints(Iterable<PrismNumericDatapoint> datapoints) {
        for (PrismNumericDatapoint datapoint : datapoints) {
            Objects.requireNonNull(datapoint, "datapoints must not contain null elements");
            Objects.requireNonNull(datapoint.getMetadata(), "datapoint metadata must not be null");
        }
    }
}

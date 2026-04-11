package tech.molecules.structurized.prism.validation;

import tech.molecules.structurized.prism.model.CategoryDefinition;
import tech.molecules.structurized.prism.model.EndpointDataType;
import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.model.NumericEndpointMeta;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Static validation helpers for PRISM endpoint definitions.
 */
public final class EndpointDefinitionValidator {
    private EndpointDefinitionValidator() {}

    public static void validate(EndpointDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        requireText(definition.getId(), "endpoint id");
        requireText(definition.getName(), "endpoint name");
        requireText(definition.getPath(), "endpoint path");
        Objects.requireNonNull(definition.getDatatype(), "endpoint datatype must not be null");
        Objects.requireNonNull(definition.getEndpointType(), "endpointType must not be null");
        Objects.requireNonNull(definition.getEvaluationMode(), "evaluationMode must not be null");
        Objects.requireNonNull(definition.getCategories(), "categories must not be null");
        validateNumericMeta(definition);

        if (definition.getDatatype() == EndpointDataType.CATEGORICAL) {
            if (definition.getCategories().isEmpty()) {
                throw new IllegalArgumentException("categorical endpoint '" + definition.getId() + "' must define categories");
            }
        } else if (!definition.getCategories().isEmpty()) {
            throw new IllegalArgumentException("non-categorical endpoint '" + definition.getId() + "' must not define categories");
        }

        Set<String> categoryIds = new HashSet<>();
        for (CategoryDefinition category : definition.getCategories()) {
            Objects.requireNonNull(category, "category must not be null");
            requireText(category.getId(), "category id");
            requireText(category.getName(), "category name");
            if (!categoryIds.add(category.getId())) {
                throw new IllegalArgumentException("duplicate category id '" + category.getId() + "' in endpoint '" + definition.getId() + "'");
            }
        }
    }

    public static void validateAll(Collection<EndpointDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions must not be null");
        for (EndpointDefinition definition : definitions) {
            validate(definition);
        }
    }

    private static void validateNumericMeta(EndpointDefinition definition) {
        NumericEndpointMeta numericMeta = definition.getNumericMeta();
        if (numericMeta == null) {
            return;
        }
        if (definition.getDatatype() != EndpointDataType.NUMERIC
                && definition.getDatatype() != EndpointDataType.OPTIONAL_NUMERIC) {
            throw new IllegalArgumentException("non-numeric endpoint '" + definition.getId() + "' must not define numericMeta");
        }
        if (numericMeta.getDomainLowerBound() != null
                && numericMeta.getDomainUpperBound() != null
                && numericMeta.getDomainLowerBound() > numericMeta.getDomainUpperBound()) {
            throw new IllegalArgumentException("numericMeta domain bounds must satisfy lower <= upper for endpoint '" + definition.getId() + "'");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}

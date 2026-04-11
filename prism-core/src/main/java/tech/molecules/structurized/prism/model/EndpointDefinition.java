package tech.molecules.structurized.prism.model;

import tech.molecules.structurized.prism.validation.EndpointDefinitionValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable definition of one analysis-ready endpoint.
 */
public final class EndpointDefinition {
    private final String id;
    private final String name;
    private final String path;
    private final EndpointDataType datatype;
    private final EndpointType endpointType;
    private final String unit;
    private final NumericEndpointMeta numericMeta;
    private final EvaluationMode evaluationMode;
    private final String description;
    private final List<CategoryDefinition> categories;

    private EndpointDefinition(Builder builder) {
        this.id = requireText(builder.id, "id");
        this.name = requireText(builder.name, "name");
        this.path = requireText(builder.path, "path");
        this.datatype = Objects.requireNonNull(builder.datatype, "datatype must not be null");
        this.endpointType = Objects.requireNonNull(builder.endpointType, "endpointType must not be null");
        this.unit = normalize(builder.unit);
        this.numericMeta = builder.numericMeta;
        this.evaluationMode = Objects.requireNonNull(builder.evaluationMode, "evaluationMode must not be null");
        this.description = normalize(builder.description);
        this.categories = List.copyOf(new ArrayList<>(builder.categories));
        EndpointDefinitionValidator.validate(this);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public EndpointDataType getDatatype() {
        return datatype;
    }

    public EndpointType getEndpointType() {
        return endpointType;
    }

    public String getUnit() {
        return unit;
    }

    public NumericEndpointMeta getNumericMeta() {
        return numericMeta;
    }

    public EvaluationMode getEvaluationMode() {
        return evaluationMode;
    }

    public String getDescription() {
        return description;
    }

    public List<CategoryDefinition> getCategories() {
        return categories;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EndpointDefinition that)) return false;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(path, that.path)
                && datatype == that.datatype
                && endpointType == that.endpointType
                && Objects.equals(unit, that.unit)
                && Objects.equals(numericMeta, that.numericMeta)
                && evaluationMode == that.evaluationMode
                && Objects.equals(description, that.description)
                && Objects.equals(categories, that.categories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, path, datatype, endpointType, unit, numericMeta, evaluationMode, description, categories);
    }

    @Override
    public String toString() {
        return "EndpointDefinition{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", path='" + path + '\'' +
                ", datatype=" + datatype +
                ", endpointType=" + endpointType +
                ", unit='" + unit + '\'' +
                ", numericMeta=" + numericMeta +
                ", evaluationMode=" + evaluationMode +
                ", description='" + description + '\'' +
                ", categories=" + categories +
                '}';
    }

    public static final class Builder {
        private String id;
        private String name;
        private String path;
        private EndpointDataType datatype;
        private EndpointType endpointType;
        private String unit;
        private NumericEndpointMeta numericMeta;
        private EvaluationMode evaluationMode;
        private String description;
        private List<CategoryDefinition> categories = List.of();

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder datatype(EndpointDataType datatype) {
            this.datatype = datatype;
            return this;
        }

        public Builder endpointType(EndpointType endpointType) {
            this.endpointType = endpointType;
            return this;
        }

        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        public Builder numericMeta(NumericEndpointMeta numericMeta) {
            this.numericMeta = numericMeta;
            return this;
        }

        public Builder evaluationMode(EvaluationMode evaluationMode) {
            this.evaluationMode = evaluationMode;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder categories(List<CategoryDefinition> categories) {
            this.categories = categories == null ? List.of() : List.copyOf(categories);
            return this;
        }

        public Builder addCategory(CategoryDefinition category) {
            Objects.requireNonNull(category, "category must not be null");
            List<CategoryDefinition> next = new ArrayList<>(this.categories);
            next.add(category);
            this.categories = List.copyOf(next);
            return this;
        }

        public EndpointDefinition build() {
            return new EndpointDefinition(this);
        }
    }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

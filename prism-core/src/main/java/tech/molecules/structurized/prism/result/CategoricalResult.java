package tech.molecules.structurized.prism.result;

import tech.molecules.structurized.prism.model.EndpointDataType;

import java.util.Objects;

/**
 * Categorical endpoint result.
 */
public final class CategoricalResult extends AbstractEndpointResult {
    private final String value;

    private CategoricalResult(Builder builder) {
        super(builder);
        this.value = requireText(builder.value, "value");
    }

    @Override
    public EndpointDataType getType() {
        return EndpointDataType.CATEGORICAL;
    }

    public String getValue() {
        return value;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CategoricalResult that)) return false;
        return Objects.equals(getN(), that.getN())
                && Objects.equals(getRawValueIds(), that.getRawValueIds())
                && Objects.equals(getFirstMeasurement(), that.getFirstMeasurement())
                && Objects.equals(getLastMeasurement(), that.getLastMeasurement())
                && Objects.equals(getDetails(), that.getDetails())
                && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getN(), getRawValueIds(), getFirstMeasurement(), getLastMeasurement(), getDetails(), value);
    }

    @Override
    public String toString() {
        return "CategoricalResult{" +
                "n=" + getN() +
                ", value='" + value + '\'' +
                ", rawValueIds=" + getRawValueIds() +
                ", firstMeasurement='" + getFirstMeasurement() + '\'' +
                ", lastMeasurement='" + getLastMeasurement() + '\'' +
                ", details=" + getDetails() +
                '}';
    }

    public static final class Builder extends AbstractEndpointResult.Builder<Builder> {
        private String value;

        private Builder() {}

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public CategoricalResult build() {
            return new CategoricalResult(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

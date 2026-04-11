package tech.molecules.structurized.prism.result;

import tech.molecules.structurized.prism.model.EndpointDataType;

import java.util.Objects;

/**
 * Boolean endpoint result.
 */
public final class BooleanResult extends AbstractEndpointResult {
    private final boolean value;

    private BooleanResult(Builder builder) {
        super(builder);
        this.value = builder.value;
    }

    @Override
    public EndpointDataType getType() {
        return EndpointDataType.BOOLEAN;
    }

    public boolean isValue() {
        return value;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BooleanResult that)) return false;
        return value == that.value
                && Objects.equals(getN(), that.getN())
                && Objects.equals(getRawValueIds(), that.getRawValueIds())
                && Objects.equals(getFirstMeasurement(), that.getFirstMeasurement())
                && Objects.equals(getLastMeasurement(), that.getLastMeasurement())
                && Objects.equals(getDetails(), that.getDetails());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getN(), getRawValueIds(), getFirstMeasurement(), getLastMeasurement(), getDetails(), value);
    }

    @Override
    public String toString() {
        return "BooleanResult{" +
                "n=" + getN() +
                ", value=" + value +
                ", rawValueIds=" + getRawValueIds() +
                ", firstMeasurement='" + getFirstMeasurement() + '\'' +
                ", lastMeasurement='" + getLastMeasurement() + '\'' +
                ", details=" + getDetails() +
                '}';
    }

    public static final class Builder extends AbstractEndpointResult.Builder<Builder> {
        private boolean value;

        private Builder() {}

        public Builder value(boolean value) {
            this.value = value;
            return this;
        }

        public BooleanResult build() {
            return new BooleanResult(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}

package tech.molecules.structurized.prism.result;

import tech.molecules.structurized.prism.model.EndpointDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Numeric endpoint result with summary and optional raw values.
 */
public final class NumericResult extends AbstractEndpointResult {
    private final NumericState state;
    private final Double mean;
    private final Double lower;
    private final Double upper;
    private final List<Double> rawValues;

    private NumericResult(Builder builder) {
        super(builder);
        this.state = builder.state != null
                ? builder.state
                : inferState(builder.mean, builder.lower, builder.upper, builder.rawValues);
        if (state == NumericState.VALUE && builder.mean == null) {
            throw new IllegalArgumentException("numeric result in VALUE state must provide mean");
        }
        if (state == NumericState.NOT_MEASURED && (builder.mean != null || builder.lower != null || builder.upper != null)) {
            throw new IllegalArgumentException("numeric result in NOT_MEASURED state must not provide mean/lower/upper");
        }
        this.mean = builder.mean;
        this.lower = builder.lower;
        this.upper = builder.upper;
        this.rawValues = List.copyOf(new ArrayList<>(builder.rawValues));
    }

    @Override
    public EndpointDataType getType() {
        return EndpointDataType.NUMERIC;
    }

    public NumericState getState() {
        return state;
    }

    public Double getMean() {
        return mean;
    }

    public Double getLower() {
        return lower;
    }

    public Double getUpper() {
        return upper;
    }

    public List<Double> getRawValues() {
        return rawValues;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NumericResult that)) return false;
        return Objects.equals(getN(), that.getN())
                && Objects.equals(getRawValueIds(), that.getRawValueIds())
                && Objects.equals(getFirstMeasurement(), that.getFirstMeasurement())
                && Objects.equals(getLastMeasurement(), that.getLastMeasurement())
                && Objects.equals(getDetails(), that.getDetails())
                && state == that.state
                && Objects.equals(mean, that.mean)
                && Objects.equals(lower, that.lower)
                && Objects.equals(upper, that.upper)
                && Objects.equals(rawValues, that.rawValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getN(), getRawValueIds(), getFirstMeasurement(), getLastMeasurement(), getDetails(), state, mean, lower, upper, rawValues);
    }

    @Override
    public String toString() {
        return "NumericResult{" +
                "n=" + getN() +
                ", state=" + state +
                ", mean=" + mean +
                ", lower=" + lower +
                ", upper=" + upper +
                ", rawValues=" + rawValues +
                ", rawValueIds=" + getRawValueIds() +
                ", firstMeasurement='" + getFirstMeasurement() + '\'' +
                ", lastMeasurement='" + getLastMeasurement() + '\'' +
                ", details=" + getDetails() +
                '}';
    }

    public static final class Builder extends AbstractEndpointResult.Builder<Builder> {
        private NumericState state;
        private Double mean;
        private Double lower;
        private Double upper;
        private List<Double> rawValues = List.of();

        private Builder() {}

        public Builder state(NumericState state) {
            this.state = state;
            return this;
        }

        public Builder mean(double mean) {
            this.mean = mean;
            return this;
        }

        public Builder lower(Double lower) {
            this.lower = lower;
            return this;
        }

        public Builder upper(Double upper) {
            this.upper = upper;
            return this;
        }

        public Builder rawValues(List<Double> rawValues) {
            this.rawValues = rawValues == null ? List.of() : List.copyOf(rawValues);
            return this;
        }

        public Builder addRawValue(Double rawValue) {
            Objects.requireNonNull(rawValue, "rawValue must not be null");
            List<Double> next = new ArrayList<>(this.rawValues);
            next.add(rawValue);
            this.rawValues = List.copyOf(next);
            return this;
        }

        public NumericResult build() {
            return new NumericResult(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }

    private static NumericState inferState(Double mean, Double lower, Double upper, List<Double> rawValues) {
        if (mean != null || lower != null || upper != null || (rawValues != null && !rawValues.isEmpty())) {
            return NumericState.VALUE;
        }
        return NumericState.NOT_MEASURED;
    }
}

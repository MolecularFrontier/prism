package tech.molecules.structurized.prism.result;

import tech.molecules.structurized.prism.model.EndpointDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Numeric endpoint result that can explicitly represent missing or inapplicable states.
 */
public final class OptionalNumericResult extends AbstractEndpointResult {
    private final OptionalNumericState state;
    private final Double mean;
    private final Double lower;
    private final Double upper;
    private final List<Double> rawValues;
    private final List<PrismNumericDatapoint> datapoints;

    private OptionalNumericResult(Builder builder) {
        super(builder);
        this.state = Objects.requireNonNull(builder.state, "state must not be null");
        this.mean = builder.mean;
        this.lower = builder.lower;
        this.upper = builder.upper;
        this.rawValues = List.copyOf(new ArrayList<>(builder.rawValues));
        this.datapoints = List.copyOf(new ArrayList<>(builder.datapoints));
        if (state == OptionalNumericState.VALUE && mean == null) {
            throw new IllegalArgumentException("optional numeric result in VALUE state must provide mean");
        }
        if (state != OptionalNumericState.VALUE && (mean != null || lower != null || upper != null)) {
            throw new IllegalArgumentException("optional numeric result without VALUE state must not provide mean/lower/upper");
        }
    }

    @Override
    public EndpointDataType getType() {
        return EndpointDataType.OPTIONAL_NUMERIC;
    }

    public OptionalNumericState getState() {
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

    public List<PrismNumericDatapoint> getDatapoints() {
        return datapoints;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OptionalNumericResult that)) return false;
        return Objects.equals(getN(), that.getN())
                && Objects.equals(getRawValueIds(), that.getRawValueIds())
                && Objects.equals(getFirstMeasurement(), that.getFirstMeasurement())
                && Objects.equals(getLastMeasurement(), that.getLastMeasurement())
                && Objects.equals(getDetails(), that.getDetails())
                && state == that.state
                && Objects.equals(mean, that.mean)
                && Objects.equals(lower, that.lower)
                && Objects.equals(upper, that.upper)
                && Objects.equals(rawValues, that.rawValues)
                && Objects.equals(datapoints, that.datapoints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getN(), getRawValueIds(), getFirstMeasurement(), getLastMeasurement(), getDetails(), state, mean, lower, upper, rawValues, datapoints);
    }

    @Override
    public String toString() {
        return "OptionalNumericResult{" +
                "n=" + getN() +
                ", state=" + state +
                ", mean=" + mean +
                ", lower=" + lower +
                ", upper=" + upper +
                ", rawValues=" + rawValues +
                ", datapoints=" + datapoints +
                ", rawValueIds=" + getRawValueIds() +
                ", firstMeasurement='" + getFirstMeasurement() + '\'' +
                ", lastMeasurement='" + getLastMeasurement() + '\'' +
                ", details=" + getDetails() +
                '}';
    }

    public static final class Builder extends AbstractEndpointResult.Builder<Builder> {
        private OptionalNumericState state;
        private Double mean;
        private Double lower;
        private Double upper;
        private List<Double> rawValues = List.of();
        private List<PrismNumericDatapoint> datapoints = List.of();

        private Builder() {}

        public Builder state(OptionalNumericState state) {
            this.state = state;
            return this;
        }

        public Builder mean(Double mean) {
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

        public Builder datapoints(List<PrismNumericDatapoint> datapoints) {
            this.datapoints = datapoints == null ? List.of() : List.copyOf(datapoints);
            return this;
        }

        public Builder addDatapoint(PrismNumericDatapoint datapoint) {
            Objects.requireNonNull(datapoint, "datapoint must not be null");
            List<PrismNumericDatapoint> next = new ArrayList<>(this.datapoints);
            next.add(datapoint);
            this.datapoints = List.copyOf(next);
            return this;
        }

        public OptionalNumericResult build() {
            return new OptionalNumericResult(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}

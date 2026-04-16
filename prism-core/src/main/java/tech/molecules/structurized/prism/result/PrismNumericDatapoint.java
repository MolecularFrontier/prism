package tech.molecules.structurized.prism.result;

import java.util.Objects;

/**
 * Lightweight numeric contributing datapoint for PRISM numeric endpoint results.
 */
public final class PrismNumericDatapoint extends PrismDatapoint {
    private final Double value;
    private final String unprocessedValue;

    private PrismNumericDatapoint(Builder builder) {
        super(builder);
        this.value = builder.value;
        this.unprocessedValue = normalize(builder.unprocessedValue);
    }

    public Double getValue() {
        return value;
    }

    public String getUnprocessedValue() {
        return unprocessedValue;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrismNumericDatapoint that)) return false;
        return super.equals(o)
                && Objects.equals(value, that.value)
                && Objects.equals(unprocessedValue, that.unprocessedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), value, unprocessedValue);
    }

    @Override
    public String toString() {
        return "PrismNumericDatapoint{" +
                "date='" + getDate() + '\'' +
                ", batch='" + getBatch() + '\'' +
                ", sourceId='" + getSourceId() + '\'' +
                ", value=" + value +
                ", unprocessedValue='" + unprocessedValue + '\'' +
                ", metadata=" + getMetadata() +
                '}';
    }

    public static final class Builder extends PrismDatapoint.Builder<Builder> {
        private Double value;
        private String unprocessedValue;

        private Builder() {}

        public Builder value(Double value) {
            this.value = value;
            return this;
        }

        public Builder unprocessedValue(String unprocessedValue) {
            this.unprocessedValue = unprocessedValue;
            return this;
        }

        public PrismNumericDatapoint build() {
            return new PrismNumericDatapoint(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}

package tech.molecules.structurized.prism.result;

import java.util.Objects;

/**
 * Lightweight numeric contributing datapoint for PRISM numeric endpoint results.
 */
public final class PrismNumericDatapoint extends PrismDatapoint {
    private final Double value;
    private final String modifier;
    private final String unprocessedValue;

    private PrismNumericDatapoint(Builder builder) {
        super(builder);
        this.value = builder.value;
        this.modifier = normalize(builder.modifier);
        this.unprocessedValue = normalize(builder.unprocessedValue);
    }

    public Double getValue() {
        return value;
    }

    /**
     * Returns the qualifier attached to the numeric value, for example {@code <}, {@code <=},
     * {@code >}, or {@code >=}. The value is intentionally not restricted to a fixed vocabulary
     * so source-system qualifiers can be preserved losslessly.
     */
    public String getModifier() {
        return modifier;
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
                && Objects.equals(modifier, that.modifier)
                && Objects.equals(unprocessedValue, that.unprocessedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), value, modifier, unprocessedValue);
    }

    @Override
    public String toString() {
        return "PrismNumericDatapoint{" +
                "date='" + getDate() + '\'' +
                ", batch='" + getBatch() + '\'' +
                ", sourceId='" + getSourceId() + '\'' +
                ", value=" + value +
                ", modifier='" + modifier + '\'' +
                ", unprocessedValue='" + unprocessedValue + '\'' +
                ", metadata=" + getMetadata() +
                '}';
    }

    public static final class Builder extends PrismDatapoint.Builder<Builder> {
        private Double value;
        private String modifier;
        private String unprocessedValue;

        private Builder() {}

        public Builder value(Double value) {
            this.value = value;
            return this;
        }

        public Builder modifier(String modifier) {
            this.modifier = modifier;
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

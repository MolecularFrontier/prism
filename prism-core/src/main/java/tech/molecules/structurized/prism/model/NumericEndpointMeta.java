package tech.molecules.structurized.prism.model;

import java.util.Objects;

/**
 * Numeric-specific endpoint metadata such as scale and meaningful value bounds.
 */
public final class NumericEndpointMeta {
    private final NumericScale scale;
    private final Double domainLowerBound;
    private final Double domainUpperBound;

    private NumericEndpointMeta(Builder builder) {
        this.scale = builder.scale;
        this.domainLowerBound = builder.domainLowerBound;
        this.domainUpperBound = builder.domainUpperBound;
        if (domainLowerBound != null && domainUpperBound != null && domainLowerBound > domainUpperBound) {
            throw new IllegalArgumentException("domainLowerBound must be <= domainUpperBound");
        }
    }

    public NumericScale getScale() {
        return scale;
    }

    public Double getDomainLowerBound() {
        return domainLowerBound;
    }

    public Double getDomainUpperBound() {
        return domainUpperBound;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NumericEndpointMeta that)) return false;
        return scale == that.scale
                && Objects.equals(domainLowerBound, that.domainLowerBound)
                && Objects.equals(domainUpperBound, that.domainUpperBound);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scale, domainLowerBound, domainUpperBound);
    }

    @Override
    public String toString() {
        return "NumericEndpointMeta{" +
                "scale=" + scale +
                ", domainLowerBound=" + domainLowerBound +
                ", domainUpperBound=" + domainUpperBound +
                '}';
    }

    public static final class Builder {
        private NumericScale scale;
        private Double domainLowerBound;
        private Double domainUpperBound;

        private Builder() {}

        public Builder scale(NumericScale scale) {
            this.scale = scale;
            return this;
        }

        public Builder domainLowerBound(Double domainLowerBound) {
            this.domainLowerBound = domainLowerBound;
            return this;
        }

        public Builder domainUpperBound(Double domainUpperBound) {
            this.domainUpperBound = domainUpperBound;
            return this;
        }

        public NumericEndpointMeta build() {
            return new NumericEndpointMeta(this);
        }
    }
}

package tech.molecules.structurized.prism.result;

import tech.molecules.structurized.prism.model.EndpointDataType;

import java.util.Objects;

/**
 * Free-text endpoint result.
 */
public final class TextResult extends AbstractEndpointResult {
    private final String text;

    private TextResult(Builder builder) {
        super(builder);
        this.text = Objects.requireNonNull(builder.text, "text must not be null");
    }

    @Override
    public EndpointDataType getType() {
        return EndpointDataType.TEXT;
    }

    public String getText() {
        return text;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextResult that)) return false;
        return Objects.equals(getN(), that.getN())
                && Objects.equals(getRawValueIds(), that.getRawValueIds())
                && Objects.equals(getFirstMeasurement(), that.getFirstMeasurement())
                && Objects.equals(getLastMeasurement(), that.getLastMeasurement())
                && Objects.equals(getDetails(), that.getDetails())
                && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getN(), getRawValueIds(), getFirstMeasurement(), getLastMeasurement(), getDetails(), text);
    }

    @Override
    public String toString() {
        return "TextResult{" +
                "n=" + getN() +
                ", text='" + text + '\'' +
                ", rawValueIds=" + getRawValueIds() +
                ", firstMeasurement='" + getFirstMeasurement() + '\'' +
                ", lastMeasurement='" + getLastMeasurement() + '\'' +
                ", details=" + getDetails() +
                '}';
    }

    public static final class Builder extends AbstractEndpointResult.Builder<Builder> {
        private String text;

        private Builder() {}

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public TextResult build() {
            return new TextResult(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}

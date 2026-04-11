package tech.molecules.structurized.prism.query;

import tech.molecules.structurized.prism.result.EndpointResult;

import java.util.Objects;

/**
 * One endpoint value for one subject.
 */
public final class EndpointValueRecord {
    private final String subjectId;
    private final String endpointId;
    private final EndpointResult result;

    private EndpointValueRecord(Builder builder) {
        this.subjectId = requireText(builder.subjectId, "subjectId");
        this.endpointId = requireText(builder.endpointId, "endpointId");
        this.result = Objects.requireNonNull(builder.result, "result must not be null");
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public EndpointResult getResult() {
        return result;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EndpointValueRecord that)) return false;
        return Objects.equals(subjectId, that.subjectId)
                && Objects.equals(endpointId, that.endpointId)
                && Objects.equals(result, that.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, endpointId, result);
    }

    @Override
    public String toString() {
        return "EndpointValueRecord{" +
                "subjectId='" + subjectId + '\'' +
                ", endpointId='" + endpointId + '\'' +
                ", result=" + result +
                '}';
    }

    public static final class Builder {
        private String subjectId;
        private String endpointId;
        private EndpointResult result;

        private Builder() {}

        public Builder subjectId(String subjectId) {
            this.subjectId = subjectId;
            return this;
        }

        public Builder endpointId(String endpointId) {
            this.endpointId = endpointId;
            return this;
        }

        public Builder result(EndpointResult result) {
            this.result = result;
            return this;
        }

        public EndpointValueRecord build() {
            return new EndpointValueRecord(this);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

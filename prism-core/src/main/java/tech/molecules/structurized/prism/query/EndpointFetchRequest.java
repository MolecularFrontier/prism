package tech.molecules.structurized.prism.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bulk endpoint retrieval request.
 */
public final class EndpointFetchRequest {
    private final List<String> subjectIds;
    private final List<String> endpointIds;

    private EndpointFetchRequest(Builder builder) {
        this.subjectIds = List.copyOf(new ArrayList<>(builder.subjectIds));
        this.endpointIds = List.copyOf(new ArrayList<>(builder.endpointIds));
    }

    public List<String> getSubjectIds() {
        return subjectIds;
    }

    public List<String> getEndpointIds() {
        return endpointIds;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EndpointFetchRequest that)) return false;
        return Objects.equals(subjectIds, that.subjectIds)
                && Objects.equals(endpointIds, that.endpointIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectIds, endpointIds);
    }

    @Override
    public String toString() {
        return "EndpointFetchRequest{" +
                "subjectIds=" + subjectIds +
                ", endpointIds=" + endpointIds +
                '}';
    }

    public static final class Builder {
        private List<String> subjectIds = List.of();
        private List<String> endpointIds = List.of();

        private Builder() {}

        public Builder subjectIds(List<String> subjectIds) {
            this.subjectIds = subjectIds == null ? List.of() : List.copyOf(subjectIds);
            return this;
        }

        public Builder endpointIds(List<String> endpointIds) {
            this.endpointIds = endpointIds == null ? List.of() : List.copyOf(endpointIds);
            return this;
        }

        public Builder addSubjectId(String subjectId) {
            Objects.requireNonNull(subjectId, "subjectId must not be null");
            List<String> next = new ArrayList<>(this.subjectIds);
            next.add(subjectId);
            this.subjectIds = List.copyOf(next);
            return this;
        }

        public Builder addEndpointId(String endpointId) {
            Objects.requireNonNull(endpointId, "endpointId must not be null");
            List<String> next = new ArrayList<>(this.endpointIds);
            next.add(endpointId);
            this.endpointIds = List.copyOf(next);
            return this;
        }

        public EndpointFetchRequest build() {
            return new EndpointFetchRequest(this);
        }
    }
}

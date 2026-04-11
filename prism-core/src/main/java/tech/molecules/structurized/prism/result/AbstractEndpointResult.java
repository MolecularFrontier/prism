package tech.molecules.structurized.prism.result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared immutable metadata fields for PRISM endpoint results.
 */
public abstract class AbstractEndpointResult implements EndpointResult {
    private final Integer n;
    private final List<String> rawValueIds;
    private final String firstMeasurement;
    private final String lastMeasurement;
    private final Map<String, Object> details;

    protected AbstractEndpointResult(Builder<?> builder) {
        this.n = builder.n;
        this.rawValueIds = List.copyOf(new ArrayList<>(builder.rawValueIds));
        this.firstMeasurement = builder.firstMeasurement;
        this.lastMeasurement = builder.lastMeasurement;
        this.details = Map.copyOf(new LinkedHashMap<>(builder.details));
    }

    @Override
    public Integer getN() {
        return n;
    }

    @Override
    public List<String> getRawValueIds() {
        return rawValueIds;
    }

    @Override
    public String getFirstMeasurement() {
        return firstMeasurement;
    }

    @Override
    public String getLastMeasurement() {
        return lastMeasurement;
    }

    @Override
    public Map<String, Object> getDetails() {
        return details;
    }

    public abstract static class Builder<B extends Builder<B>> {
        private Integer n;
        private List<String> rawValueIds = List.of();
        private String firstMeasurement;
        private String lastMeasurement;
        private Map<String, Object> details = Map.of();

        protected abstract B self();

        public B n(Integer n) {
            this.n = n;
            return self();
        }

        public B rawValueIds(List<String> rawValueIds) {
            this.rawValueIds = rawValueIds == null ? List.of() : List.copyOf(rawValueIds);
            return self();
        }

        public B firstMeasurement(String firstMeasurement) {
            this.firstMeasurement = firstMeasurement;
            return self();
        }

        public B lastMeasurement(String lastMeasurement) {
            this.lastMeasurement = lastMeasurement;
            return self();
        }

        public B details(Map<String, Object> details) {
            this.details = details == null ? Map.of() : Map.copyOf(details);
            return self();
        }

        public B addRawValueId(String rawValueId) {
            Objects.requireNonNull(rawValueId, "rawValueId must not be null");
            List<String> next = new ArrayList<>(this.rawValueIds);
            next.add(rawValueId);
            this.rawValueIds = List.copyOf(next);
            return self();
        }

        public B putDetail(String key, Object value) {
            Objects.requireNonNull(key, "detail key must not be null");
            Objects.requireNonNull(value, "detail value must not be null");
            LinkedHashMap<String, Object> next = new LinkedHashMap<>(this.details);
            next.put(key, value);
            this.details = Map.copyOf(next);
            return self();
        }
    }
}

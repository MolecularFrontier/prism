package tech.molecules.structurized.prism.result;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Lightweight contributing datapoint abstraction for PRISM endpoint results.
 *
 * This is intentionally not a full source-system assay result model. It only captures
 * a small set of generic, practically useful evidence fields that can accompany an
 * aggregated endpoint result.
 */
public class PrismDatapoint {
    private final String date;
    private final String batch;
    private final String sourceId;
    private final Map<String, Object> metadata;

    protected PrismDatapoint(Builder<?> builder) {
        this.date = builder.date;
        this.batch = builder.batch;
        this.sourceId = builder.sourceId;
        this.metadata = Map.copyOf(new LinkedHashMap<>(builder.metadata));
    }

    public String getDate() {
        return date;
    }

    public String getBatch() {
        return batch;
    }

    public String getSourceId() {
        return sourceId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrismDatapoint that)) return false;
        return Objects.equals(date, that.date)
                && Objects.equals(batch, that.batch)
                && Objects.equals(sourceId, that.sourceId)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, batch, sourceId, metadata);
    }

    @Override
    public String toString() {
        return "PrismDatapoint{" +
                "date='" + date + '\'' +
                ", batch='" + batch + '\'' +
                ", sourceId='" + sourceId + '\'' +
                ", metadata=" + metadata +
                '}';
    }

    public abstract static class Builder<B extends Builder<B>> {
        private String date;
        private String batch;
        private String sourceId;
        private Map<String, Object> metadata = Map.of();

        protected abstract B self();

        public B date(String date) {
            this.date = normalize(date);
            return self();
        }

        public B batch(String batch) {
            this.batch = normalize(batch);
            return self();
        }

        public B sourceId(String sourceId) {
            this.sourceId = normalize(sourceId);
            return self();
        }

        public B metadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            return self();
        }

        public B putMetadata(String key, Object value) {
            Objects.requireNonNull(key, "metadata key must not be null");
            Objects.requireNonNull(value, "metadata value must not be null");
            LinkedHashMap<String, Object> next = new LinkedHashMap<>(this.metadata);
            next.put(key, value);
            this.metadata = Map.copyOf(next);
            return self();
        }
    }

    protected static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

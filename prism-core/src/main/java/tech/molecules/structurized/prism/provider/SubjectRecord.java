package tech.molecules.structurized.prism.provider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable subject record with optional chemistry and project metadata.
 */
public final class SubjectRecord {
    private final String subjectId;
    private final String structureId;
    private final String batchId;
    private final String project;
    private final String series;
    private final String smiles;
    private final Map<String, String> metadata;

    private SubjectRecord(Builder builder) {
        this.subjectId = requireText(builder.subjectId, "subjectId");
        this.structureId = normalize(builder.structureId);
        this.batchId = normalize(builder.batchId);
        this.project = normalize(builder.project);
        this.series = normalize(builder.series);
        this.smiles = normalize(builder.smiles);
        this.metadata = Map.copyOf(new LinkedHashMap<>(builder.metadata));
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getStructureId() {
        return structureId;
    }

    public String getBatchId() {
        return batchId;
    }

    public String getProject() {
        return project;
    }

    public String getSeries() {
        return series;
    }

    public String getSmiles() {
        return smiles;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SubjectRecord that)) return false;
        return Objects.equals(subjectId, that.subjectId)
                && Objects.equals(structureId, that.structureId)
                && Objects.equals(batchId, that.batchId)
                && Objects.equals(project, that.project)
                && Objects.equals(series, that.series)
                && Objects.equals(smiles, that.smiles)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, structureId, batchId, project, series, smiles, metadata);
    }

    @Override
    public String toString() {
        return "SubjectRecord{" +
                "subjectId='" + subjectId + '\'' +
                ", structureId='" + structureId + '\'' +
                ", batchId='" + batchId + '\'' +
                ", project='" + project + '\'' +
                ", series='" + series + '\'' +
                ", smiles='" + smiles + '\'' +
                ", metadata=" + metadata +
                '}';
    }

    public static final class Builder {
        private String subjectId;
        private String structureId;
        private String batchId;
        private String project;
        private String series;
        private String smiles;
        private Map<String, String> metadata = Map.of();

        private Builder() {}

        public Builder subjectId(String subjectId) {
            this.subjectId = subjectId;
            return this;
        }

        public Builder structureId(String structureId) {
            this.structureId = structureId;
            return this;
        }

        public Builder batchId(String batchId) {
            this.batchId = batchId;
            return this;
        }

        public Builder project(String project) {
            this.project = project;
            return this;
        }

        public Builder series(String series) {
            this.series = series;
            return this;
        }

        public Builder smiles(String smiles) {
            this.smiles = smiles;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            return this;
        }

        public Builder putMetadata(String key, String value) {
            String normalizedKey = requireText(key, "metadata key");
            String normalizedValue = requireText(value, "metadata value");
            LinkedHashMap<String, String> next = new LinkedHashMap<>(this.metadata);
            next.put(normalizedKey, normalizedValue);
            this.metadata = Map.copyOf(next);
            return this;
        }

        public SubjectRecord build() {
            return new SubjectRecord(this);
        }
    }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

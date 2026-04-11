package tech.molecules.structurized.prism.provider;

import java.util.Objects;

/**
 * Lightweight discoverable group of subject IDs.
 */
public final class SubjectSet {
    private final String id;
    private final String name;
    private final String setType;
    private final String subjectSetScope;
    private final String parentSetId;
    private final String description;

    private SubjectSet(Builder builder) {
        this.id = requireText(builder.id, "id");
        this.name = requireText(builder.name, "name");
        this.setType = requireText(builder.setType, "setType");
        this.subjectSetScope = normalize(builder.subjectSetScope);
        this.parentSetId = normalize(builder.parentSetId);
        this.description = normalize(builder.description);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSetType() {
        return setType;
    }

    public String getSubjectSetScope() {
        return subjectSetScope;
    }

    public String getParentSetId() {
        return parentSetId;
    }

    public String getDescription() {
        return description;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SubjectSet that)) return false;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(setType, that.setType)
                && Objects.equals(subjectSetScope, that.subjectSetScope)
                && Objects.equals(parentSetId, that.parentSetId)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, setType, subjectSetScope, parentSetId, description);
    }

    @Override
    public String toString() {
        return "SubjectSet{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", setType='" + setType + '\'' +
                ", subjectSetScope='" + subjectSetScope + '\'' +
                ", parentSetId='" + parentSetId + '\'' +
                ", description='" + description + '\'' +
                '}';
    }

    public static final class Builder {
        private String id;
        private String name;
        private String setType;
        private String subjectSetScope;
        private String parentSetId;
        private String description;

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder setType(String setType) {
            this.setType = setType;
            return this;
        }

        public Builder subjectSetScope(String subjectSetScope) {
            this.subjectSetScope = subjectSetScope;
            return this;
        }

        public Builder parentSetId(String parentSetId) {
            this.parentSetId = parentSetId;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public SubjectSet build() {
            return new SubjectSet(this);
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

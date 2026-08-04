package tech.molecules.structurized.prism.io;

public record PrismSnapshotSelection(String kind, String ref, String revision) {
    public PrismSnapshotSelection {
        kind = requireText(kind, "kind");
        ref = normalize(ref);
        revision = requireText(revision, "revision");
        if ("subject_set".equals(kind) && ref == null) {
            throw new IllegalArgumentException("subject_set selections require ref");
        }
        if ("global".equals(kind) && ref != null) {
            throw new IllegalArgumentException("global selections must not define ref");
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
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

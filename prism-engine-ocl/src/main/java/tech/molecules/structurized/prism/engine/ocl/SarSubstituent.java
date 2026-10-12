package tech.molecules.structurized.prism.engine.ocl;

public record SarSubstituent(Type type, String identity, String label, String idcode) {
    public enum Type {
        SUBSTITUENT,
        UNSUBSTITUTED,
        MULTI_ATTACHMENT,
        AMBIGUOUS,
        UNMATCHED,
        LABEL
    }

    public SarSubstituent {
        if (type == null) throw new IllegalArgumentException("SAR substituent type must not be null");
        if (identity == null || identity.isBlank()) throw new IllegalArgumentException("SAR substituent identity must not be blank");
        label = label == null || label.isBlank() ? identity : label;
        idcode = idcode == null || idcode.isBlank() ? null : idcode;
    }

    public boolean isProjectable() {
        return type != Type.MULTI_ATTACHMENT && type != Type.AMBIGUOUS && type != Type.UNMATCHED;
    }
}

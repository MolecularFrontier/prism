package tech.molecules.structurized.prism.engine;

import java.util.Locale;

public enum PrismColumnType {
    NUMERIC,
    INTEGER,
    BOOLEAN,
    TEXT,
    CATEGORICAL,
    MOLECULE;

    static PrismColumnType fromSchema(String type, String semanticType, String structureFormat) {
        if (structureFormat != null && !structureFormat.isBlank()) {
            return MOLECULE;
        }
        String semantic = normalize(semanticType);
        if ("chemical_structure".equals(semantic)) {
            return MOLECULE;
        }
        if ("category".equals(semantic)) {
            return CATEGORICAL;
        }
        return switch (normalize(type)) {
            case "number" -> NUMERIC;
            case "integer" -> INTEGER;
            case "boolean" -> BOOLEAN;
            default -> TEXT;
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

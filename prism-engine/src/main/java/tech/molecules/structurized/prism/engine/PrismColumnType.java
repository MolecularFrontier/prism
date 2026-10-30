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
        if ("category".equals(semantic) || isCategoricalAnalysisSemanticType(semantic)) {
            return CATEGORICAL;
        }
        return switch (normalize(type)) {
            case "number" -> NUMERIC;
            case "integer" -> INTEGER;
            case "boolean" -> BOOLEAN;
            default -> TEXT;
        };
    }

    /**
     * Semantic types produced by analysis operations whose values are discrete group keys rather
     * than free text. These must resolve to {@link #CATEGORICAL} so that round-tripped columns stay
     * usable by consumers requiring a categorical column - notably the {@code sar-1d} and
     * {@code sar-2d} report blocks, which reject a plain text substituent column.
     */
    private static boolean isCategoricalAnalysisSemanticType(String semantic) {
        return switch (semantic) {
            case "sar_substituent", "cluster_id", "clustering_facet", "prediction_status" -> true;
            default -> false;
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

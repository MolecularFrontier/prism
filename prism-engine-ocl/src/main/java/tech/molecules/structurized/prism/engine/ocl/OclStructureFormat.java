package tech.molecules.structurized.prism.engine.ocl;

import java.util.Locale;

public enum OclStructureFormat {
    IDCODE,
    SMILES,
    MOLFILE;

    public static OclStructureFormat fromMetadata(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "idcode" -> IDCODE;
            case "molfile", "mol" -> MOLFILE;
            default -> SMILES;
        };
    }
}

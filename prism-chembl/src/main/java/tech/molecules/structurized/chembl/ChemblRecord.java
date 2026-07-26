package tech.molecules.structurized.chembl;

import java.util.Objects;

public record ChemblRecord(
        String chemblId,
        String smiles,
        String inchiKey,
        String parentChemblId,
        String moleculeType,
        String structureType,
        boolean polymer,
        boolean inorganic,
        String release,
        int sourceCount,
        int documentCount,
        String documentIds,
        String sourceIds
) {
    public ChemblRecord {
        chemblId = requireText(chemblId, "chemblId");
        smiles = blankToNull(smiles);
        inchiKey = blankToNull(inchiKey);
        parentChemblId = blankToNull(parentChemblId);
        moleculeType = blankToNull(moleculeType);
        structureType = blankToNull(structureType);
        release = blankToNull(release);
        documentIds = blankToEmpty(documentIds);
        sourceIds = blankToEmpty(sourceIds);
    }

    public static ChemblRecord minimal(String chemblId, String smiles) {
        return new ChemblRecord(chemblId, smiles, null, null, "Small molecule", "MOL", false, false, null, 0, 0, "", "");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String blankToEmpty(String value) { return Objects.requireNonNullElse(blankToNull(value), ""); }
}

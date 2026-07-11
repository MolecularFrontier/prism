package tech.molecules.structurized.prismlite.swing.chembl;

public record ChemblPublicationImportOptions(
        String documentChemblId,
        int minCompoundsPerEndpoint,
        int maxEndpoints
) {
    public ChemblPublicationImportOptions {
        documentChemblId = normalizeDocumentId(documentChemblId);
        if (minCompoundsPerEndpoint < 1) {
            throw new IllegalArgumentException("minCompoundsPerEndpoint must be at least 1");
        }
        if (maxEndpoints < 1) {
            throw new IllegalArgumentException("maxEndpoints must be at least 1");
        }
    }

    public static ChemblPublicationImportOptions defaults(String documentChemblId) {
        return new ChemblPublicationImportOptions(documentChemblId, 10, 12);
    }

    private static String normalizeDocumentId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ChEMBL document ID must not be blank");
        }
        return value.trim().toUpperCase();
    }
}

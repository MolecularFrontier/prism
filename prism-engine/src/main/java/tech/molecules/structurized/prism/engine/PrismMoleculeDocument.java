package tech.molecules.structurized.prism.engine;

public record PrismMoleculeDocument(
        String id,
        String title,
        PrismMoleculeDocumentMode mode,
        String idcode,
        String coordinates,
        long revision
) {
    public PrismMoleculeDocument {
        id = requireText(id, "document id");
        title = title == null || title.isBlank() ? id : title.trim();
        mode = mode == null ? PrismMoleculeDocumentMode.MOLECULE : mode;
        idcode = idcode == null ? "" : idcode.trim();
        coordinates = coordinates == null ? "" : coordinates.trim();
        if (revision < 1) {
            throw new IllegalArgumentException("document revision must be positive");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

package tech.molecules.structurized.prism.engine;

import java.util.HashSet;
import java.util.List;

public record PrismMoleculeList(
        String id,
        String title,
        List<PrismMoleculeDocument> documents
) {
    public PrismMoleculeList {
        id = requireText(id, "molecule list id");
        title = title == null || title.isBlank() ? id : title.trim();
        documents = documents == null ? List.of() : List.copyOf(documents);
        HashSet<String> ids = new HashSet<>();
        for (PrismMoleculeDocument document : documents) {
            if (!ids.add(document.id())) {
                throw new IllegalArgumentException("duplicate molecule document id '" + document.id() + "'");
            }
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

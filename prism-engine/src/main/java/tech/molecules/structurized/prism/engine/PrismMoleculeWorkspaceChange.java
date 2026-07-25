package tech.molecules.structurized.prism.engine;

public record PrismMoleculeWorkspaceChange(
        long revision,
        String listId,
        String documentId
) {
}

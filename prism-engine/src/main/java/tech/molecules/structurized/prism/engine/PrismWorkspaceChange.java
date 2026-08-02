package tech.molecules.structurized.prism.engine;

public record PrismWorkspaceChange(
        PrismWorkspace workspace,
        long revision,
        PrismWorkspaceChangeType type,
        PrismWorkspaceChangeOrigin origin
) {
}

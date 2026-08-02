package tech.molecules.structurized.prism.engine;

public enum PrismWorkspaceChangeType {
    PROJECTION,
    VIEWS,
    MOLECULES,
    LIVE_CONFIGURATION,
    STRUCTURE;

    static PrismWorkspaceChangeType merge(PrismWorkspaceChangeType left, PrismWorkspaceChangeType right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}

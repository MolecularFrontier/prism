package tech.molecules.structurized.prism.engine;

public final class PrismWorkspaceRevisionConflictException extends RuntimeException {
    private final long expectedRevision;
    private final long actualRevision;

    public PrismWorkspaceRevisionConflictException(long expectedRevision, long actualRevision) {
        super("workspace is at revision " + actualRevision + ", expected " + expectedRevision);
        this.expectedRevision = expectedRevision;
        this.actualRevision = actualRevision;
    }

    public long expectedRevision() {
        return expectedRevision;
    }

    public long actualRevision() {
        return actualRevision;
    }
}

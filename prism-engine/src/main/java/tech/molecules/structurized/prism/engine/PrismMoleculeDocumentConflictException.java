package tech.molecules.structurized.prism.engine;

public final class PrismMoleculeDocumentConflictException extends RuntimeException {
    private final String documentId;
    private final long expectedRevision;
    private final long actualRevision;

    public PrismMoleculeDocumentConflictException(String documentId, long expectedRevision, long actualRevision) {
        super("molecule document '" + documentId + "' is at revision " + actualRevision
                + ", expected " + expectedRevision);
        this.documentId = documentId;
        this.expectedRevision = expectedRevision;
        this.actualRevision = actualRevision;
    }

    public String documentId() {
        return documentId;
    }

    public long expectedRevision() {
        return expectedRevision;
    }

    public long actualRevision() {
        return actualRevision;
    }
}

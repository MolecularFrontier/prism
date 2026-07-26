package tech.molecules.structurized.chembl;

public final class ChemblNormalizationException extends RuntimeException {
    private final ChemblRejection reason;

    public ChemblNormalizationException(ChemblRejection reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ChemblNormalizationException(ChemblRejection reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public ChemblRejection reason() { return reason; }
}

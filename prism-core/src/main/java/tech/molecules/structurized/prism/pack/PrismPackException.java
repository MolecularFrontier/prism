package tech.molecules.structurized.prism.pack;

/**
 * Indicates that a PrismPack cannot be read because required content is missing
 * or malformed.
 */
public class PrismPackException extends RuntimeException {
    public PrismPackException(String message) {
        super(message);
    }

    public PrismPackException(String message, Throwable cause) {
        super(message, cause);
    }
}

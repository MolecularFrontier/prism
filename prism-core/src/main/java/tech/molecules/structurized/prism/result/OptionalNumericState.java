package tech.molecules.structurized.prism.result;

/**
 * State model for numeric endpoints whose absence is semantically meaningful.
 */
public enum OptionalNumericState {
    VALUE,
    NOT_MEASURED,
    NOT_APPLICABLE
}

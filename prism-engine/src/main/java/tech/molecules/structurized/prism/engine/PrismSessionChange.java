package tech.molecules.structurized.prism.engine;

import java.util.Objects;

public record PrismSessionChange(PrismSession session, PrismSessionChangeType type) {
    public PrismSessionChange {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(type, "type");
    }
}

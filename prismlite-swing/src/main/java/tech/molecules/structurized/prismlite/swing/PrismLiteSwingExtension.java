package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.PrismSession;

public interface PrismLiteSwingExtension {
    default void configureSession(PrismSession session) {
    }

    default void configureSwing(PrismLiteSwingContext context) {
    }
}

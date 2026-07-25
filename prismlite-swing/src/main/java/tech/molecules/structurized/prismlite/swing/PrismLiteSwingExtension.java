package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.PrismSession;

import java.nio.file.Path;

public interface PrismLiteSwingExtension {
    default void configureSession(PrismSession session) {
    }

    default void configureSession(PrismSession session, Path sourcePath) {
        configureSession(session);
    }

    default void configureSwing(PrismLiteSwingContext context) {
    }
}

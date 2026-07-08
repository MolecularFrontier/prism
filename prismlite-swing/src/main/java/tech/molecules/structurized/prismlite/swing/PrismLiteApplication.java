package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.PrismSession;

import java.nio.file.Path;

public final class PrismLiteApplication {
    private PrismLiteApplication() {
    }

    public static void main(String[] args) throws Exception {
        Path path = args.length == 0 ? defaultExamplePath() : Path.of(args[0]);
        PrismLiteFrame.show(PrismSession.open(path), path);
    }

    private static Path defaultExamplePath() {
        Path fromModuleDirectory = Path.of("..", "examples", "example.prismpack");
        if (fromModuleDirectory.toFile().exists()) {
            return fromModuleDirectory;
        }
        return Path.of("examples", "example.prismpack");
    }
}

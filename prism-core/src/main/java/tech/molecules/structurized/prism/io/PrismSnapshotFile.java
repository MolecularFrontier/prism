package tech.molecules.structurized.prism.io;

import java.nio.file.Path;

public record PrismSnapshotFile(String path, String sha256, long rowCount) {
    public PrismSnapshotFile {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("path must not be blank");
        Path relative = Path.of(path);
        if (relative.isAbsolute() || relative.getNameCount() != 1 || !path.equals(relative.getFileName().toString())
                || ".".equals(path) || "..".equals(path)) {
            throw new IllegalArgumentException("path must be one safe snapshot file name");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be a lowercase SHA-256 digest");
        }
        if (rowCount < 0) throw new IllegalArgumentException("rowCount must not be negative");
    }
}

package tech.molecules.structurized.prism.io;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record PrismSnapshotManifest(
        int schemaVersion,
        String format,
        String snapshotId,
        PrismSnapshotDescriptor descriptor,
        List<PrismSnapshotFile> files
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;
    public static final String FORMAT = "prism-tsv-snapshot";

    public PrismSnapshotManifest {
        if (schemaVersion < MIN_SUPPORTED_SCHEMA_VERSION || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported snapshot schemaVersion " + schemaVersion);
        }
        if (!FORMAT.equals(format)) throw new IllegalArgumentException("unsupported snapshot format " + format);
        if (snapshotId == null || !snapshotId.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("snapshotId must be a sha256 identity");
        }
        if (descriptor == null) throw new IllegalArgumentException("descriptor must not be null");
        ArrayList<PrismSnapshotFile> ordered = new ArrayList<>(files == null ? List.of() : files);
        ordered.sort(Comparator.comparing(PrismSnapshotFile::path));
        files = List.copyOf(ordered);
    }
}

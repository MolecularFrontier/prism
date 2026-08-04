package tech.molecules.structurized.prism.io;

import tech.molecules.structurized.prism.provider.inmemory.InMemoryPrismDataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Validates and loads an immutable canonical PRISM TSV snapshot. */
public final class PrismTsvSnapshotLoader {
    private PrismTsvSnapshotLoader() {}

    public static boolean isSnapshot(Path directory) {
        return directory != null && Files.isRegularFile(directory.resolve(PrismTsvDatasetWriter.SNAPSHOT_MANIFEST_FILE_NAME));
    }

    public static PrismDatasetSnapshot load(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory must not be null");
        Path normalized = directory.toAbsolutePath().normalize();
        PrismSnapshotManifest manifest = PrismTsvDatasetWriter.readManifest(normalized);
        PrismTsvDatasetWriter.validateSnapshotDirectory(normalized, manifest);
        InMemoryPrismDataset dataset = PrismTsvDatasetLoader.load(normalized);
        return new PrismDatasetSnapshot(dataset, manifest);
    }
}

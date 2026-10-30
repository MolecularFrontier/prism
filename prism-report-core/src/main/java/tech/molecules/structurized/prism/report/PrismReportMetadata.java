package tech.molecules.structurized.prism.report;

/**
 * Front-matter metadata of a {@code .prism.md} report.
 *
 * <p>{@code datasetId} and {@code datasetSnapshotId} are optional and purely informational: they
 * record which dataset the report was authored against so that opening it against a newer snapshot
 * can say so, rather than silently rendering different numbers. They are deliberately not a
 * compatibility gate - snapshot identity is content-derived, so any data refresh produces a new
 * identity while the report usually remains perfectly valid. Reference resolution stays the hard
 * gate; a mismatch here is a warning.
 */
public record PrismReportMetadata(
        int prismReportVersion,
        String dataset,
        String title,
        String id,
        String createdAt,
        String datasetId,
        String datasetSnapshotId
) {
    /** Retains the pre-identity shape so existing callers and tests keep compiling. */
    public PrismReportMetadata(int prismReportVersion, String dataset, String title,
                               String id, String createdAt) {
        this(prismReportVersion, dataset, title, id, createdAt, null, null);
    }
}

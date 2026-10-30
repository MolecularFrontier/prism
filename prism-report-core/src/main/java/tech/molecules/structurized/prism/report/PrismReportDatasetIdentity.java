package tech.molecules.structurized.prism.report;

/**
 * Identity of the dataset a report is being validated against, supplied by whoever opened it.
 *
 * <p>The session itself does not carry this - {@code PrismSession} retains no backing dataset - so
 * the caller that read the pack passes it in. Both fields are optional; a null field simply skips
 * the corresponding comparison.
 *
 * @param datasetId   logical dataset identity, e.g. a managed session or dataset ID
 * @param snapshotId  content-derived snapshot identity, e.g. {@code sha256:...}
 */
public record PrismReportDatasetIdentity(String datasetId, String snapshotId) {
    public PrismReportDatasetIdentity {
        datasetId = datasetId == null || datasetId.isBlank() ? null : datasetId.trim();
        snapshotId = snapshotId == null || snapshotId.isBlank() ? null : snapshotId.trim();
    }

    public boolean isEmpty() {
        return datasetId == null && snapshotId == null;
    }
}

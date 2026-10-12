package tech.molecules.structurized.prism.report;

public record PrismReportMetadata(
        int prismReportVersion,
        String dataset,
        String title,
        String id,
        String createdAt
) {
}

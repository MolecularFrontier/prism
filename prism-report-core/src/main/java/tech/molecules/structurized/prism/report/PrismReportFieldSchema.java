package tech.molecules.structurized.prism.report;

public record PrismReportFieldSchema(
        String name,
        String type,
        boolean required,
        String description
) {
}

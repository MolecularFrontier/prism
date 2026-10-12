package tech.molecules.structurized.prism.report;

import java.util.List;

public record PrismReportDocument(
        PrismReportMetadata metadata,
        List<PrismReportBlock> blocks,
        String source,
        List<PrismReportDiagnostic> diagnostics
) {
    public PrismReportDocument {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        source = source == null ? "" : source;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(item -> item.severity() == PrismReportSeverity.ERROR);
    }
}

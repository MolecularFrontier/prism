package tech.molecules.structurized.prism.report;

import java.util.List;

/** One embedded report that could not be registered, with the diagnostics that explain why. */
public record PrismReportAttachmentFailure(
        String attachmentId,
        String message,
        List<PrismReportDiagnostic> diagnostics
) {
    public PrismReportAttachmentFailure {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}

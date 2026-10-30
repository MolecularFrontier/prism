package tech.molecules.structurized.prism.report;

import java.util.List;

/**
 * Thrown when a report cannot be registered because validation produced errors.
 *
 * <p>Lives in {@code prism-report-core} rather than in a UI module so that every consumer - the
 * Swing app, the MCP server, embedded-report loading - shares one failure type.
 */
public final class PrismReportValidationException extends Exception {
    private final List<PrismReportDiagnostic> diagnostics;

    public PrismReportValidationException(List<PrismReportDiagnostic> diagnostics) {
        super(diagnostics.stream().map(PrismReportDiagnostic::displayMessage).reduce((a, b) -> a + "\n" + b)
                .orElse("The Prism report is invalid."));
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<PrismReportDiagnostic> diagnostics() {
        return diagnostics;
    }
}

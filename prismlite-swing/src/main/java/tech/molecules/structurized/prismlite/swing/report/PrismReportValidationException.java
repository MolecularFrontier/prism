package tech.molecules.structurized.prismlite.swing.report;

import tech.molecules.structurized.prism.report.PrismReportDiagnostic;

import java.util.List;

public final class PrismReportValidationException extends Exception {
    private final List<PrismReportDiagnostic> diagnostics;

    public PrismReportValidationException(List<PrismReportDiagnostic> diagnostics) {
        super(diagnostics.stream().map(PrismReportDiagnostic::displayMessage).reduce((a, b) -> a + "\n" + b).orElse(
                "The Prism report is invalid."));
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<PrismReportDiagnostic> diagnostics() {
        return diagnostics;
    }
}

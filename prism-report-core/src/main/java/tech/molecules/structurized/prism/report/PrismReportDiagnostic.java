package tech.molecules.structurized.prism.report;

public record PrismReportDiagnostic(
        PrismReportSeverity severity,
        String code,
        String message,
        String blockId,
        int line,
        int column
) {
    public String displayMessage() {
        String location = line > 0 ? "line " + line + (column > 0 ? ":" + column : "") + ": " : "";
        return location + message;
    }
}

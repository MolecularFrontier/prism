package tech.molecules.structurized.prism.report;

public record MarkdownReportBlock(String markdown, int sourceLine) implements PrismReportBlock {
}

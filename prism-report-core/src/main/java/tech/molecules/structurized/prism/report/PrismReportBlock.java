package tech.molecules.structurized.prism.report;

public sealed interface PrismReportBlock permits MarkdownReportBlock, EmbeddedPrismViewReportBlock {
    int sourceLine();
}

package tech.molecules.structurized.prism.report;

public sealed interface PrismReportBlock permits MarkdownReportBlock, CompoundTableReportBlock {
    int sourceLine();
}

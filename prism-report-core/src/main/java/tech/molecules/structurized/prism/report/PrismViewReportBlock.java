package tech.molecules.structurized.prism.report;

import tech.molecules.structurized.prism.engine.PrismViewSpec;

public record PrismViewReportBlock(
        String blockId,
        String blockType,
        PrismViewSpec specification,
        int sourceLine
) implements EmbeddedPrismViewReportBlock {
    public PrismViewReportBlock {
        if (blockId == null || blockId.isBlank()) throw new IllegalArgumentException("block id must not be blank");
        if (blockType == null || blockType.isBlank()) throw new IllegalArgumentException("block type must not be blank");
        if (specification == null) throw new IllegalArgumentException("view specification must not be null");
    }
}

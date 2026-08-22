package tech.molecules.structurized.prism.report;

import tech.molecules.structurized.prism.engine.ocl.CompoundTableViewSpec;

public record CompoundTableReportBlock(
        String blockId,
        CompoundTableViewSpec specification,
        int sourceLine
) implements PrismReportBlock {
}

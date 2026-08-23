package tech.molecules.structurized.prism.report;

import tech.molecules.structurized.prism.engine.PrismViewSpec;

public non-sealed interface EmbeddedPrismViewReportBlock extends PrismReportBlock {
    String blockId();
    String blockType();
    PrismViewSpec specification();
}

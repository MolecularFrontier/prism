package tech.molecules.structurized.prismlite.swing.workspace.filters;

import tech.molecules.structurized.prism.engine.PrismFilter;
import tech.molecules.structurized.prism.engine.PrismTable;

public interface PrismFilterLabelProvider {
    boolean supports(PrismFilter filter);

    String label(PrismFilter filter, PrismTable table);
}

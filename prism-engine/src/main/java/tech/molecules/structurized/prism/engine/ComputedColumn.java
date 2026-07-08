package tech.molecules.structurized.prism.engine;

import java.util.Set;

public interface ComputedColumn {
    PrismColumnSchema schema();

    Set<String> referencedColumnIds();

    Object valueAt(PrismTable table, int physicalRow);
}

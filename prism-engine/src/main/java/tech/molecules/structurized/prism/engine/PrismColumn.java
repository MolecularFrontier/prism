package tech.molecules.structurized.prism.engine;

import java.util.Set;

public interface PrismColumn {
    String id();

    PrismColumnType type();

    PrismColumnSchema schema();

    int rowCount();

    boolean isMissing(int physicalRow);

    Object valueAt(int physicalRow);

    String formattedValueAt(int physicalRow);

    default double doubleValueAt(int physicalRow) {
        throw new UnsupportedOperationException("column is not numeric: " + id());
    }

    Set<FilterCapability> filterCapabilities();
}

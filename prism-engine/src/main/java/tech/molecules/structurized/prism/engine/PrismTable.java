package tech.molecules.structurized.prism.engine;

import java.util.List;
import java.util.Optional;

public interface PrismTable {
    int rowCount();

    List<PrismColumn> columns();

    PrismColumn columnAt(int columnIndex);

    Optional<PrismColumn> findColumn(String columnId);

    default PrismColumn column(String columnId) {
        return findColumn(columnId).orElseThrow(() -> new IllegalArgumentException("unknown column '" + columnId + "'"));
    }

    int columnIndex(String columnId);

    default Object valueAt(int physicalRow, String columnId) {
        return column(columnId).valueAt(physicalRow);
    }

    default String formattedValueAt(int physicalRow, String columnId) {
        return column(columnId).formattedValueAt(physicalRow);
    }
}

package tech.molecules.structurized.prism.engine;

import java.util.BitSet;

public final class RowSelectionModel {
    private final BitSet selectedRows = new BitSet();

    public void clear() {
        selectedRows.clear();
    }

    public void setSelected(int physicalRow, boolean selected) {
        selectedRows.set(physicalRow, selected);
    }

    public boolean isSelected(int physicalRow) {
        return selectedRows.get(physicalRow);
    }

    public void replace(BitSet rows) {
        selectedRows.clear();
        if (rows != null) {
            selectedRows.or(rows);
        }
    }

    public BitSet selectedRows() {
        return (BitSet) selectedRows.clone();
    }
}

package tech.molecules.structurized.prismlite.swing.workspace.table;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeRenderCache;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeViewPanel;

import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

public final class PhysicalRowMoleculeCellRenderer extends MoleculeViewPanel implements TableCellRenderer {
    private final PrismColumn column;
    private final MoleculeRenderCache cache;
    private final IntUnaryOperator physicalRowForModelRow;

    public PhysicalRowMoleculeCellRenderer(
            PrismColumn column,
            MoleculeRenderCache cache,
            IntUnaryOperator physicalRowForModelRow
    ) {
        this.column = Objects.requireNonNull(column, "column");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.physicalRowForModelRow = Objects.requireNonNull(physicalRowForModelRow, "physicalRowForModelRow");
        setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int columnIndex
    ) {
        setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        int modelRow = row < 0 ? -1 : table.convertRowIndexToModel(row);
        int physicalRow = modelRow < 0 ? -1 : physicalRowForModelRow.applyAsInt(modelRow);
        setMolecule(physicalRow < 0 ? null : cache.molecule(column, physicalRow));
        return this;
    }
}

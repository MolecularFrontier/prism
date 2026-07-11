package tech.molecules.structurized.prismlite.swing.workspace.table;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeRenderCache;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeViewPanel;

import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.util.Objects;

public final class MoleculeCellRenderer extends MoleculeViewPanel implements TableCellRenderer {
    private final PrismLiteWorkspaceModel workspace;
    private final PrismColumn column;
    private final MoleculeRenderCache renderCache;

    public MoleculeCellRenderer(PrismLiteWorkspaceModel workspace, PrismColumn column, MoleculeRenderCache renderCache) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.column = Objects.requireNonNull(column, "column");
        this.renderCache = Objects.requireNonNull(renderCache, "renderCache");
        setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int columnIndex) {
        setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        if (row >= 0 && row < workspace.session().visibleRowCount()) {
            setMolecule(renderCache.molecule(column, workspace.session().physicalRowAtVisibleIndex(row)));
        } else {
            setMolecule(null);
        }
        return this;
    }
}

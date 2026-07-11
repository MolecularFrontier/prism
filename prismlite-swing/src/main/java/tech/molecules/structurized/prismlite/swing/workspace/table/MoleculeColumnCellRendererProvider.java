package tech.molecules.structurized.prismlite.swing.workspace.table;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeRenderCache;

import javax.swing.table.TableCellRenderer;

public final class MoleculeColumnCellRendererProvider implements PrismColumnCellRendererProvider {
    private final MoleculeRenderCache renderCache;

    public MoleculeColumnCellRendererProvider() {
        this(null);
    }

    public MoleculeColumnCellRendererProvider(MoleculeRenderCache renderCache) {
        this.renderCache = renderCache;
    }

    @Override
    public boolean supports(PrismColumn column) {
        return column.type() == PrismColumnType.MOLECULE;
    }

    @Override
    public TableCellRenderer createRenderer(PrismLiteWorkspaceModel workspace, PrismColumn column) {
        MoleculeRenderCache cache = renderCache == null ? new MoleculeRenderCache(workspace.table()) : renderCache;
        return new MoleculeCellRenderer(workspace, column, cache);
    }
}

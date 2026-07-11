package tech.molecules.structurized.prismlite.swing.workspace.table;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.table.TableCellRenderer;

public interface PrismColumnCellRendererProvider {
    boolean supports(PrismColumn column);

    TableCellRenderer createRenderer(PrismLiteWorkspaceModel workspace, PrismColumn column);
}

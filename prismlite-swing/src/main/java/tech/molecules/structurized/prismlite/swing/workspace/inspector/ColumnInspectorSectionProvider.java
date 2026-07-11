package tech.molecules.structurized.prismlite.swing.workspace.inspector;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.JComponent;

public interface ColumnInspectorSectionProvider {
    boolean supports(PrismColumn column);

    JComponent create(PrismLiteWorkspaceModel workspace, PrismColumn column);
}

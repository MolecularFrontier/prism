package tech.molecules.structurized.prismlite.swing.workspace.inspector;

import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.JComponent;

public interface RowInspectorSectionProvider {
    boolean supports(PrismLiteWorkspaceModel workspace, int physicalRow);

    String title();

    JComponent create(PrismLiteWorkspaceModel workspace, int physicalRow);
}

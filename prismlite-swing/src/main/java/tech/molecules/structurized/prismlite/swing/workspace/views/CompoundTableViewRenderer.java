package tech.molecules.structurized.prismlite.swing.workspace.views;

import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prism.engine.ocl.CompoundTableViewSpec;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceController;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class CompoundTableViewRenderer implements PrismSwingViewRenderer {
    @Override
    public String viewType() {
        return CompoundTableViewSpec.VIEW_TYPE;
    }

    @Override
    public JComponent createComponent(
            PrismViewRecord view,
            PrismLiteWorkspaceModel model,
            PrismLiteWorkspaceController controller,
            Runnable refresh
    ) {
        if (!(view.specification() instanceof CompoundTableViewSpec specification)) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(new JLabel("Unsupported compound-table specification."), BorderLayout.CENTER);
            return panel;
        }
        return new CompoundTablePanel(specification, model);
    }
}

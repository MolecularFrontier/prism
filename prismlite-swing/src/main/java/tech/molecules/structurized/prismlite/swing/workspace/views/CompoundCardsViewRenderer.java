package tech.molecules.structurized.prismlite.swing.workspace.views;

import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prism.engine.ocl.CompoundCardsViewSpec;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceController;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class CompoundCardsViewRenderer implements PrismSwingViewRenderer {
    @Override
    public String viewType() {
        return CompoundCardsViewSpec.VIEW_TYPE;
    }

    @Override
    public JComponent createComponent(PrismViewRecord view, PrismLiteWorkspaceModel model,
                                      PrismLiteWorkspaceController controller, Runnable refresh) {
        if (!(view.specification() instanceof CompoundCardsViewSpec specification)) {
            JPanel message = new JPanel(new BorderLayout());
            message.add(new JLabel("Unsupported compound-cards specification."), BorderLayout.CENTER);
            return message;
        }
        return new CompoundCardsPanel(specification, model);
    }
}

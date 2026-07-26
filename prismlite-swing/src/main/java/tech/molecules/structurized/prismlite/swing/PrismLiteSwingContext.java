package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspacePanel;
import tech.molecules.structurized.prismlite.swing.workspace.views.PrismSwingViewRenderer;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTable;

public record PrismLiteSwingContext(
        PrismSession session,
        JFrame frame,
        JTable table,
        PrismLiteTableModel tableModel,
        JPanel sidePanel,
        Runnable refresh,
        PrismLiteWorkspacePanel workspace
) {
    public PrismLiteSwingContext(PrismSession session,
                                 JFrame frame,
                                 JTable table,
                                 PrismLiteTableModel tableModel,
                                 JPanel sidePanel,
                                 Runnable refresh) {
        this(session, frame, table, tableModel, sidePanel, refresh, null);
    }

    public void registerViewRenderer(PrismSwingViewRenderer renderer) {
        if (workspace == null) {
            throw new IllegalStateException("PrismLite workspace is not available");
        }
        workspace.registerViewRenderer(renderer);
    }
}

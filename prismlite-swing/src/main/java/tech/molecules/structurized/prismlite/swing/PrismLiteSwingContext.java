package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.PrismSession;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTable;

public record PrismLiteSwingContext(
        PrismSession session,
        JFrame frame,
        JTable table,
        PrismLiteTableModel tableModel,
        JPanel sidePanel,
        Runnable refresh
) {
}

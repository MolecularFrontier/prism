package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.SortDirection;
import tech.molecules.structurized.prism.engine.SortKey;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;

public final class PrismLiteFrame extends JFrame {
    private final PrismLiteTableModel tableModel;
    private final JLabel statusLabel;

    public PrismLiteFrame(PrismSession session, Path sourcePath) {
        super(titleFor(sourcePath));
        this.tableModel = new PrismLiteTableModel(session);
        this.statusLabel = new JLabel();
        JTable table = new JTable(tableModel);
        table.setAutoCreateRowSorter(false);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int column = table.columnAtPoint(event.getPoint());
                if (column < 0) {
                    return;
                }
                toggleSort(column);
            }
        });
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                syncSelection(table);
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        root.add(statusLabel, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(960, 640);
        setLocationByPlatform(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        updateStatus();
    }

    private void toggleSort(int visibleColumn) {
        PrismSession session = tableModel.session();
        String columnId = session.visibleColumnId(visibleColumn);
        SortDirection nextDirection = SortDirection.ASCENDING;
        List<SortKey> current = session.viewState().sortKeys();
        if (!current.isEmpty()
                && current.get(0).columnId().equals(columnId)
                && current.get(0).direction() == SortDirection.ASCENDING) {
            nextDirection = SortDirection.DESCENDING;
        }
        session.sortBy(columnId, nextDirection);
        tableModel.refresh();
        updateStatus();
    }

    private void syncSelection(JTable table) {
        PrismSession session = tableModel.session();
        session.viewState().selectionModel().clear();
        for (int selectedRow : table.getSelectedRows()) {
            if (selectedRow >= 0 && selectedRow < session.visibleRowCount()) {
                session.viewState().selectionModel().setSelected(session.physicalRowAtVisibleIndex(selectedRow), true);
            }
        }
    }

    private void updateStatus() {
        PrismSession session = tableModel.session();
        statusLabel.setText("Visible rows: " + session.visibleRowCount() + " / " + session.totalRowCount());
    }

    private static String titleFor(Path sourcePath) {
        if (sourcePath == null) {
            return "PrismLite";
        }
        Path fileName = sourcePath.getFileName();
        return "PrismLite - " + (fileName == null ? sourcePath : fileName);
    }

    public static void show(PrismSession session, Path sourcePath) {
        SwingUtilities.invokeLater(() -> new PrismLiteFrame(session, sourcePath).setVisible(true));
    }
}

package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.SortDirection;
import tech.molecules.structurized.prism.engine.SortKey;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class PrismLiteFrame extends JFrame {
    private final PrismLiteTableModel tableModel;
    private final JLabel statusLabel;

    public PrismLiteFrame(PrismSession session, Path sourcePath) {
        this(session, sourcePath, PrismLiteSwingExtensions.load());
    }

    PrismLiteFrame(PrismSession session, Path sourcePath, List<PrismLiteSwingExtension> extensions) {
        super(titleFor(sourcePath));
        List<PrismLiteSwingExtension> loadedExtensions = List.copyOf(extensions);
        loadedExtensions.forEach(extension -> extension.configureSession(session));
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

        JPanel sidePanel = new JPanel(new BorderLayout());
        PrismLiteRowSetPanel rowSetPanel = new PrismLiteRowSetPanel(session, this::refresh);
        JTabbedPane sideTabs = new JTabbedPane();
        sideTabs.addTab("Filters", new PrismLiteFilterPanel(session, this::refresh));
        sideTabs.addTab("Row Sets", rowSetPanel);
        sideTabs.addTab("Operations", new PrismLiteOperationPanel(session, rowSetPanel, this::refresh));
        sidePanel.add(sideTabs, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout());
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        root.add(sidePanel, BorderLayout.WEST);
        root.add(statusLabel, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(1120, 680);
        setLocationByPlatform(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loadedExtensions.forEach(extension -> extension.configureSwing(new PrismLiteSwingContext(
                session,
                this,
                table,
                tableModel,
                sidePanel,
                this::refresh
        )));
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
        refresh();
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

    private void refresh() {
        tableModel.refresh();
        updateStatus();
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
        open(session, sourcePath);
    }

    public static PrismLiteFrame open(PrismSession session, Path sourcePath) {
        if (SwingUtilities.isEventDispatchThread()) {
            PrismLiteFrame frame = new PrismLiteFrame(session, sourcePath);
            frame.setVisible(true);
            return frame;
        }
        AtomicReference<PrismLiteFrame> frame = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                PrismLiteFrame created = new PrismLiteFrame(session, sourcePath);
                created.setVisible(true);
                frame.set(created);
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while opening PrismLite frame", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Failed to open PrismLite frame", cause);
        }
        return frame.get();
    }
}

package tech.molecules.structurized.prismlite.swing.workspace;

import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prismlite.swing.PrismLiteOperationPanel;
import tech.molecules.structurized.prismlite.swing.PrismLiteRowSetPanel;
import tech.molecules.structurized.prismlite.swing.PrismLiteTableModel;
import tech.molecules.structurized.prismlite.swing.workspace.analysis.ColumnSummaryService;
import tech.molecules.structurized.prismlite.swing.workspace.filters.FilterShelfPanel;
import tech.molecules.structurized.prismlite.swing.workspace.inspector.ColumnInspectorPanel;
import tech.molecules.structurized.prismlite.swing.workspace.navigator.ColumnNavigatorPanel;
import tech.molecules.structurized.prismlite.swing.workspace.table.PrismColumnHeaderRenderer;
import tech.molecules.structurized.prismlite.swing.workspace.table.PrismTableHeader;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PrismLiteWorkspacePanel extends JPanel {
    private final PrismLiteWorkspaceModel model;
    private final PrismLiteTableModel tableModel;
    private final PrismLiteWorkspaceController controller;
    private final JTable table;
    private final JPanel sidePanel = new JPanel(new BorderLayout());
    private final ColumnNavigatorPanel navigator;
    private final ColumnInspectorPanel inspector;
    private final FilterShelfPanel filterShelf;
    private final JLabel status = new JLabel();
    private final JPanel inspectorContainer = new JPanel(new BorderLayout());
    private final ExecutorService summaryExecutor;

    public PrismLiteWorkspacePanel(PrismSession session) {
        super(new BorderLayout(4, 4));
        Objects.requireNonNull(session, "session");
        this.model = new PrismLiteWorkspaceModel(session);
        this.tableModel = new PrismLiteTableModel(session);
        this.controller = new PrismLiteWorkspaceController(model, tableModel);
        this.table = new JTable(tableModel);
        this.summaryExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "prismlite-column-summary");
            thread.setDaemon(true);
            return thread;
        });
        ColumnSummaryService summaries = new ColumnSummaryService(session.table(), summaryExecutor);

        table.setAutoCreateRowSorter(false);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                syncSelection();
            }
        });
        table.getTableHeader().setDefaultRenderer(new PrismColumnHeaderRenderer(model));
        table.setTableHeader(new PrismTableHeader(table.getColumnModel(), table, model, controller, this::refreshWorkspace));
        controller.attachTable(table);

        navigator = new ColumnNavigatorPanel(model, controller);
        inspector = new ColumnInspectorPanel(model, controller, summaries, this::refreshWorkspace);
        filterShelf = new FilterShelfPanel(model, controller, this::refreshWorkspace);

        PrismLiteRowSetPanel rowSetPanel = new PrismLiteRowSetPanel(session, this::refreshWorkspace);
        JTabbedPane tools = new JTabbedPane();
        tools.addTab("Columns", navigator);
        tools.addTab("Row Sets", rowSetPanel);
        tools.addTab("Operations", new PrismLiteOperationPanel(session, rowSetPanel, this::refreshWorkspace));
        sidePanel.add(tools, BorderLayout.CENTER);

        inspectorContainer.add(inspector, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.add(filterShelf, BorderLayout.CENTER);
        footer.add(status, BorderLayout.EAST);

        add(toolbar(), BorderLayout.NORTH);
        add(sidePanel, BorderLayout.WEST);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(inspectorContainer, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        model.addListener(this::refreshWorkspace);
        updateStatus();
    }

    public PrismLiteWorkspaceModel model() {
        return model;
    }

    public PrismLiteTableModel tableModel() {
        return tableModel;
    }

    public JTable table() {
        return table;
    }

    public JPanel sidePanel() {
        return sidePanel;
    }

    public void refreshWorkspace() {
        tableModel.refreshStructure();
        navigator.refresh();
        inspector.refresh();
        filterShelf.refresh();
        updateStatus();
        String focused = model.focusedColumnId();
        if (focused != null) {
            controller.scrollTableToColumn(focused);
        }
    }

    private void syncSelection() {
        PrismSession session = model.session();
        session.viewState().selectionModel().clear();
        for (int selectedRow : table.getSelectedRows()) {
            if (selectedRow >= 0 && selectedRow < session.visibleRowCount()) {
                session.viewState().selectionModel().setSelected(session.physicalRowAtVisibleIndex(selectedRow), true);
            }
        }
    }

    private JToolBar toolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton columns = new JButton("Columns");
        columns.addActionListener(event -> sidePanel.setVisible(!sidePanel.isVisible()));
        JButton inspectorButton = new JButton("Inspector");
        inspectorButton.addActionListener(event -> inspectorContainer.setVisible(!inspectorContainer.isVisible()));
        JButton applyAll = new JButton("Apply all");
        applyAll.addActionListener(event -> {
            model.applyAllDrafts();
            refreshWorkspace();
        });
        JButton discardAll = new JButton("Discard all");
        discardAll.addActionListener(event -> {
            model.discardAllDrafts();
            refreshWorkspace();
        });
        toolbar.add(columns);
        toolbar.add(inspectorButton);
        toolbar.addSeparator();
        toolbar.add(discardAll);
        toolbar.add(applyAll);
        return toolbar;
    }

    private void updateStatus() {
        PrismSession session = model.session();
        int dirty = model.dirtyFilterColumns().size();
        String prefix = dirty == 0 ? "" : dirty + " unapplied filter change" + (dirty == 1 ? "   " : "s   ");
        status.setText(prefix + session.visibleRowCount() + " / " + session.totalRowCount() + " rows");
    }
}

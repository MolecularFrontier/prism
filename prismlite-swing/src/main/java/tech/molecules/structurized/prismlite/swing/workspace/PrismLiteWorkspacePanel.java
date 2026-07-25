package tech.molecules.structurized.prismlite.swing.workspace;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prismlite.swing.PrismLiteOperationPanel;
import tech.molecules.structurized.prismlite.swing.PrismLiteRowSetPanel;
import tech.molecules.structurized.prismlite.swing.PrismLiteTableModel;
import tech.molecules.structurized.prismlite.swing.workspace.analysis.ColumnSummaryService;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeRenderCache;
import tech.molecules.structurized.prismlite.swing.workspace.filters.FilterShelfPanel;
import tech.molecules.structurized.prismlite.swing.workspace.inspector.ColumnInspectorPanel;
import tech.molecules.structurized.prismlite.swing.workspace.inspector.RowInspectorPanel;
import tech.molecules.structurized.prismlite.swing.workspace.navigator.ColumnNavigatorPanel;
import tech.molecules.structurized.prismlite.swing.workspace.profile.PropertyProfilePanel;
import tech.molecules.structurized.prismlite.swing.workspace.table.MoleculeColumnCellRendererProvider;
import tech.molecules.structurized.prismlite.swing.workspace.table.PrismColumnCellRendererProvider;
import tech.molecules.structurized.prismlite.swing.workspace.table.PrismColumnHeaderRenderer;
import tech.molecules.structurized.prismlite.swing.workspace.table.PrismTableHeader;
import tech.molecules.structurized.prismlite.swing.workspace.table.ScoreColumnCellRendererProvider;
import tech.molecules.structurized.prismlite.swing.workspace.views.PrismSwingViewRendererRegistry;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PrismLiteWorkspacePanel extends JPanel {
    private static final int AUTO_WIDTH_SAMPLE_ROWS = 200;
    private static final int MIN_COLUMN_WIDTH = 64;
    private static final int MAX_COLUMN_WIDTH = 340;
    private static final int MAX_MOLECULE_COLUMN_WIDTH = 260;

    private final PrismLiteWorkspaceModel model;
    private final PrismLiteTableModel tableModel;
    private final PrismLiteWorkspaceController controller;
    private final JTable table;
    private final JScrollPane tableScrollPane;
    private final JTabbedPane mainTabs = new JTabbedPane();
    private final JPanel sidePanel = new JPanel(new BorderLayout());
    private final ColumnNavigatorPanel navigator;
    private final ColumnInspectorPanel columnInspector;
    private final RowInspectorPanel rowInspector;
    private final FilterShelfPanel filterShelf;
    private final PropertyProfilePanel propertyProfiles;
    private final JLabel status = new JLabel();
    private final JTabbedPane inspectorTabs = new JTabbedPane();
    private final JPanel inspectorContainer = new JPanel(new BorderLayout());
    private final ExecutorService summaryExecutor;
    private final PrismSwingViewRendererRegistry viewRenderers = PrismSwingViewRendererRegistry.defaults();
    private final List<PrismColumnCellRendererProvider> rendererProviders = new ArrayList<>();
    private int renderedViewCount;
    private boolean restoringSelection;
    private boolean restoringColumnWidths;

    public PrismLiteWorkspacePanel(PrismSession session) {
        super(new BorderLayout(4, 4));
        Objects.requireNonNull(session, "session");
        this.model = new PrismLiteWorkspaceModel(session);
        this.tableModel = new PrismLiteTableModel(session);
        this.controller = new PrismLiteWorkspaceController(model, tableModel);
        this.table = new JTable(tableModel);
        this.tableScrollPane = new JScrollPane(table);
        this.summaryExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "prismlite-column-summary");
            thread.setDaemon(true);
            return thread;
        });
        ColumnSummaryService summaries = new ColumnSummaryService(session.table(), summaryExecutor);
        rendererProviders.add(new MoleculeColumnCellRendererProvider(new MoleculeRenderCache(session.table())));
        rendererProviders.add(new ScoreColumnCellRendererProvider());
        initializeDefaultRowHeight();

        table.setAutoCreateRowSorter(false);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !restoringSelection) {
                syncSelection();
            }
        });
        table.getColumnModel().addColumnModelListener(new WidthRecorder());
        table.getTableHeader().setDefaultRenderer(new PrismColumnHeaderRenderer(model));
        table.setTableHeader(new PrismTableHeader(
                table.getColumnModel(),
                table,
                model,
                controller,
                this::refreshDataWorkspace,
                this::refreshStructureWorkspace,
                this::refreshChrome));
        controller.attachTable(table);

        navigator = new ColumnNavigatorPanel(model, controller);
        columnInspector = new ColumnInspectorPanel(model, controller, summaries, this::refreshDataWorkspace, this::refreshStructureWorkspace);
        rowInspector = new RowInspectorPanel(model);
        filterShelf = new FilterShelfPanel(model, controller, this::refreshDataWorkspace);
        propertyProfiles = new PropertyProfilePanel(model, this::refreshStructureWorkspace);

        PrismLiteRowSetPanel rowSetPanel = new PrismLiteRowSetPanel(session, this::refreshDataWorkspace);
        JTabbedPane tools = new JTabbedPane();
        tools.addTab("Columns", navigator);
        tools.addTab("Row Sets", rowSetPanel);
        tools.addTab("Profiles", propertyProfiles);
        tools.addTab("Operations", new PrismLiteOperationPanel(session, rowSetPanel, this::refreshWorkspace, this::selectedPhysicalRowsForOperations));
        sidePanel.add(tools, BorderLayout.CENTER);

        inspectorTabs.addTab("Column", columnInspector);
        inspectorTabs.addTab("Row", rowInspector);
        inspectorContainer.add(inspectorTabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.add(filterShelf, BorderLayout.CENTER);
        footer.add(status, BorderLayout.EAST);

        mainTabs.addTab("Table", tableScrollPane);

        add(toolbar(), BorderLayout.NORTH);
        add(sidePanel, BorderLayout.WEST);
        add(mainTabs, BorderLayout.CENTER);
        add(inspectorContainer, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        installColumnRenderers();
        restoreColumnWidths();
        applyRowHeight();
        refreshViewTabs();
        model.addChangeListener(this::handleWorkspaceChange);
        refreshChrome();
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

    public int workspaceTabCount() {
        return mainTabs.getTabCount();
    }

    public String selectedWorkspaceTabTitle() {
        int selected = mainTabs.getSelectedIndex();
        return selected < 0 ? null : mainTabs.getTitleAt(selected);
    }

    public void addApplicationTab(String id, String title, javax.swing.JComponent component) {
        String tabId = requireApplicationTabId(id);
        Objects.requireNonNull(component, "component");
        for (int index = 1; index < mainTabs.getTabCount(); index++) {
            if (Objects.equals(tabId, applicationTabIdForTab(index))) {
                mainTabs.setTitleAt(index, title == null || title.isBlank() ? tabId : title.trim());
                mainTabs.setComponentAt(index, component);
                component.putClientProperty("prismApplicationTabId", tabId);
                return;
            }
        }
        component.putClientProperty("prismApplicationTabId", tabId);
        mainTabs.addTab(title == null || title.isBlank() ? tabId : title.trim(), component);
    }

    public void focusApplicationTab(String id) {
        String tabId = requireApplicationTabId(id);
        for (int index = 1; index < mainTabs.getTabCount(); index++) {
            if (Objects.equals(tabId, applicationTabIdForTab(index))) {
                mainTabs.setSelectedIndex(index);
                return;
            }
        }
    }

    public JPanel sidePanel() {
        return sidePanel;
    }

    public void toggleNavigator() {
        sidePanel.setVisible(!sidePanel.isVisible());
    }

    public void toggleInspector() {
        inspectorContainer.setVisible(!inspectorContainer.isVisible());
    }

    public void autoSizeVisibleColumns() {
        autoSizeColumns();
    }

    public void resetRowHeight() {
        initializeDefaultRowHeight();
        applyRowHeight();
    }

    public void applyAllDraftFilters() {
        model.applyAllDrafts();
        refreshDataWorkspace();
    }

    public void discardAllDraftFilters() {
        model.discardAllDrafts();
        refreshChrome();
    }

    public void clearAppliedFilters() {
        model.clearAppliedFilters();
        refreshDataWorkspace();
    }

    public void registerRendererProvider(PrismColumnCellRendererProvider provider) {
        rendererProviders.add(Objects.requireNonNull(provider, "provider"));
        installColumnRenderers();
    }

    public void refreshWorkspace() {
        refreshStructureWorkspace();
    }

    public void refreshDataWorkspace() {
        Set<Integer> selectedPhysicalRows = selectedPhysicalRows();
        tableModel.refresh();
        restoreSelection(selectedPhysicalRows);
        refreshChrome();
    }

    public void refreshStructureWorkspace() {
        Set<Integer> selectedPhysicalRows = selectedPhysicalRows();
        tableModel.refreshStructure();
        installColumnRenderers();
        restoreColumnWidths();
        applyRowHeight();
        restoreSelection(selectedPhysicalRows);
        refreshViewTabs();
        refreshChrome();
        String focused = model.focusedColumnId();
        if (focused != null) {
            controller.scrollTableToColumn(focused);
        }
    }


    public void focusView(String viewId) {
        for (int index = 1; index < mainTabs.getTabCount(); index++) {
            if (Objects.equals(viewId, viewIdForTab(index))) {
                mainTabs.setSelectedIndex(index);
                return;
            }
        }
    }

    private void refreshViewTabs() {
        String selectedViewId = selectedViewId();
        java.awt.Component selectedComponent = mainTabs.getSelectedComponent();
        boolean selectedApplicationTab = selectedComponent instanceof javax.swing.JComponent jComponent
                && jComponent.getClientProperty("prismApplicationTabId") != null;
        int previousViewCount = renderedViewCount;
        for (int index = mainTabs.getTabCount() - 1; index >= 1; index--) {
            if (viewIdForTab(index) != null) mainTabs.removeTabAt(index);
        }
        int insertIndex = firstApplicationTabIndex();
        for (PrismViewRecord view : model.session().views()) {
            java.awt.Component component = createViewTabComponent(view);
            if (component instanceof javax.swing.JComponent jComponent) {
                jComponent.putClientProperty("prismViewId", view.id());
            }
            mainTabs.insertTab(view.title(), null, component, null, insertIndex++);
        }
        renderedViewCount = model.session().views().size();
        if (selectedApplicationTab && selectedComponent.getParent() == mainTabs) {
            mainTabs.setSelectedComponent(selectedComponent);
        } else if (selectedViewId != null) {
            focusView(selectedViewId);
        } else if (renderedViewCount > previousViewCount && !model.session().views().isEmpty()) {
            focusView(model.session().views().getLast().id());
        }
    }

    private java.awt.Component createViewTabComponent(PrismViewRecord view) {
        java.util.Optional<tech.molecules.structurized.prismlite.swing.workspace.views.PrismSwingViewRenderer> renderer = viewRenderers.find(view.type());
        if (renderer.isEmpty()) {
            return unsupportedView(view);
        }
        tech.molecules.structurized.prismlite.swing.workspace.views.PrismSwingViewRenderer viewRenderer = renderer.get();
        javax.swing.JComponent content = viewRenderer.createComponent(view, model, controller, this::refreshStructureWorkspace);
        javax.swing.JComponent configuration = viewRenderer.createConfigurationComponent(view, model, controller, this::refreshStructureWorkspace);
        if (configuration == null) {
            return content;
        }
        JPanel wrapper = new JPanel(new BorderLayout());
        JPanel header = new JPanel(new BorderLayout());
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton configure = new JButton("Configure");
        configuration.setVisible(false);
        configure.addActionListener(event -> {
            configuration.setVisible(!configuration.isVisible());
            wrapper.revalidate();
        });
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(configure);
        header.add(toolbar, BorderLayout.NORTH);
        header.add(configuration, BorderLayout.CENTER);
        wrapper.add(header, BorderLayout.NORTH);
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    private String selectedViewId() {
        int selected = mainTabs.getSelectedIndex();
        return selected <= 0 ? null : viewIdForTab(selected);
    }

    private String viewIdForTab(int index) {
        java.awt.Component component = mainTabs.getComponentAt(index);
        if (component instanceof javax.swing.JComponent jComponent) {
            Object value = jComponent.getClientProperty("prismViewId");
            return value instanceof String text ? text : null;
        }
        return null;
    }

    private int firstApplicationTabIndex() {
        for (int index = 1; index < mainTabs.getTabCount(); index++) {
            if (applicationTabIdForTab(index) != null) return index;
        }
        return mainTabs.getTabCount();
    }

    private String applicationTabIdForTab(int index) {
        java.awt.Component component = mainTabs.getComponentAt(index);
        if (component instanceof javax.swing.JComponent jComponent) {
            Object value = jComponent.getClientProperty("prismApplicationTabId");
            return value instanceof String text ? text : null;
        }
        return null;
    }

    private static String requireApplicationTabId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("application tab id must not be blank");
        return id.trim();
    }

    private static JPanel unsupportedView(PrismViewRecord view) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("No renderer registered for view type: " + view.type()), BorderLayout.CENTER);
        return panel;
    }

    private void handleWorkspaceChange(PrismLiteWorkspaceModel.WorkspaceChange change) {
        if (change == PrismLiteWorkspaceModel.WorkspaceChange.ROW_FOCUS) {
            refreshRowFocus();
        } else {
            refreshChrome();
        }
    }

    private void refreshChrome() {
        Point viewPosition = tableScrollPane.getViewport().getViewPosition();
        applyRowHeight();
        navigator.refresh();
        columnInspector.refresh();
        rowInspector.refresh();
        filterShelf.refresh();
        propertyProfiles.refresh();
        updateStatus();
        if (table.getTableHeader() != null) {
            table.getTableHeader().repaint();
        }
        restoreTableViewPosition(viewPosition);
    }

    private void refreshRowFocus() {
        rowInspector.refresh();
    }

    private void restoreTableViewPosition(Point viewPosition) {
        if (viewPosition == null) {
            return;
        }
        JViewport viewport = tableScrollPane.getViewport();
        Dimension viewSize = viewport.getViewSize();
        Dimension extent = viewport.getExtentSize();
        int x = Math.max(0, Math.min(viewPosition.x, Math.max(0, viewSize.width - extent.width)));
        int y = Math.max(0, Math.min(viewPosition.y, Math.max(0, viewSize.height - extent.height)));
        viewport.setViewPosition(new Point(x, y));
    }

    private void syncSelection() {
        Point viewPosition = tableScrollPane.getViewport().getViewPosition();
        PrismSession session = model.session();
        session.viewState().selectionModel().clear();
        for (int selectedRow : table.getSelectedRows()) {
            if (selectedRow >= 0 && selectedRow < session.visibleRowCount()) {
                session.viewState().selectionModel().setSelected(session.physicalRowAtVisibleIndex(selectedRow), true);
            }
        }
        Integer oldFocusedRow = model.focusedPhysicalRow();
        int lead = table.getSelectionModel().getLeadSelectionIndex();
        if (lead >= 0 && lead < session.visibleRowCount()) {
            model.setFocusedVisibleRow(lead);
        } else if (table.getSelectedRowCount() == 0) {
            model.setFocusedVisibleRow(null);
        }
        if (Objects.equals(oldFocusedRow, model.focusedPhysicalRow())) {
            refreshRowFocus();
        }
        restoreTableViewPosition(viewPosition);
    }

    private Set<Integer> selectedPhysicalRowsForOperations() {
        LinkedHashSet<Integer> rows = new LinkedHashSet<>(selectedPhysicalRows());
        java.util.BitSet selected = model.session().viewState().selectionModel().selectedRows();
        for (int row = selected.nextSetBit(0); row >= 0; row = selected.nextSetBit(row + 1)) {
            rows.add(row);
        }
        return rows;
    }

    private Set<Integer> selectedPhysicalRows() {
        LinkedHashSet<Integer> rows = new LinkedHashSet<>();
        PrismSession session = model.session();
        for (int selectedRow : table.getSelectedRows()) {
            if (selectedRow >= 0 && selectedRow < session.visibleRowCount()) {
                rows.add(session.physicalRowAtVisibleIndex(selectedRow));
            }
        }
        java.util.BitSet selected = session.viewState().selectionModel().selectedRows();
        for (int row = selected.nextSetBit(0); row >= 0; row = selected.nextSetBit(row + 1)) {
            rows.add(row);
        }
        return rows;
    }

    private void restoreSelection(Set<Integer> selectedPhysicalRows) {
        restoringSelection = true;
        try {
            table.clearSelection();
            if (selectedPhysicalRows.isEmpty()) {
                return;
            }
            PrismSession session = model.session();
            for (int visible = 0; visible < session.visibleRowCount(); visible++) {
                if (selectedPhysicalRows.contains(session.physicalRowAtVisibleIndex(visible))) {
                    table.addRowSelectionInterval(visible, visible);
                }
            }
        } finally {
            restoringSelection = false;
        }
    }

    private void installColumnRenderers() {
        for (int visible = 0; visible < model.session().visibleColumnCount() && visible < table.getColumnModel().getColumnCount(); visible++) {
            PrismColumn column = model.session().visibleColumn(visible);
            for (PrismColumnCellRendererProvider provider : rendererProviders) {
                if (provider.supports(column)) {
                    TableCellRenderer renderer = provider.createRenderer(model, column);
                    table.getColumnModel().getColumn(visible).setCellRenderer(renderer);
                    if (column.type() == PrismColumnType.MOLECULE) {
                        TableColumn tableColumn = table.getColumnModel().getColumn(visible);
                        tableColumn.setPreferredWidth(Math.max(140, tableColumn.getPreferredWidth()));
                    }
                    break;
                }
            }
        }
    }

    private void restoreColumnWidths() {
        restoringColumnWidths = true;
        try {
            for (int visible = 0; visible < model.session().visibleColumnCount() && visible < table.getColumnModel().getColumnCount(); visible++) {
                String columnId = model.session().visibleColumnId(visible);
                Integer width = model.preferredWidths().get(columnId);
                if (width != null) {
                    table.getColumnModel().getColumn(visible).setPreferredWidth(width);
                    table.getColumnModel().getColumn(visible).setWidth(width);
                }
            }
        } finally {
            restoringColumnWidths = false;
        }
    }

    private void recordVisibleColumnWidths() {
        if (restoringColumnWidths) {
            return;
        }
        for (int visible = 0; visible < model.session().visibleColumnCount() && visible < table.getColumnModel().getColumnCount(); visible++) {
            String columnId = model.session().visibleColumnId(visible);
            model.setPreferredWidth(columnId, table.getColumnModel().getColumn(visible).getWidth());
        }
    }

    private void autoSizeColumns() {
        restoringColumnWidths = true;
        try {
            for (int visible = 0; visible < model.session().visibleColumnCount() && visible < table.getColumnModel().getColumnCount(); visible++) {
                PrismColumn prismColumn = model.session().visibleColumn(visible);
                int width = preferredWidthForColumn(visible, prismColumn);
                TableColumn tableColumn = table.getColumnModel().getColumn(visible);
                tableColumn.setPreferredWidth(width);
                tableColumn.setWidth(width);
                model.setPreferredWidth(prismColumn.id(), width);
            }
        } finally {
            restoringColumnWidths = false;
        }
    }

    private int preferredWidthForColumn(int visibleColumn, PrismColumn prismColumn) {
        TableColumn tableColumn = table.getColumnModel().getColumn(visibleColumn);
        TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
        Component header = headerRenderer.getTableCellRendererComponent(table, tableColumn.getHeaderValue(), false, false, -1, visibleColumn);
        int width = header.getPreferredSize().width + 18;
        int rows = Math.min(table.getRowCount(), AUTO_WIDTH_SAMPLE_ROWS);
        for (int row = 0; row < rows; row++) {
            TableCellRenderer renderer = table.getCellRenderer(row, visibleColumn);
            Component component = table.prepareRenderer(renderer, row, visibleColumn);
            width = Math.max(width, component.getPreferredSize().width + 18);
        }
        int max = prismColumn.type() == PrismColumnType.MOLECULE ? MAX_MOLECULE_COLUMN_WIDTH : MAX_COLUMN_WIDTH;
        return Math.max(MIN_COLUMN_WIDTH, Math.min(width, max));
    }

    private void initializeDefaultRowHeight() {
        model.setRowHeight(defaultRowHeight());
    }

    private int defaultRowHeight() {
        for (PrismColumn column : model.session().table().columns()) {
            if (column.type() == PrismColumnType.MOLECULE) {
                return 88;
            }
        }
        return 24;
    }

    private void applyRowHeight() {
        int rowHeight = model.rowHeight();
        if (table.getRowHeight() != rowHeight) {
            table.setRowHeight(rowHeight);
        }
    }

    private JToolBar toolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton columns = new JButton("Columns");
        columns.addActionListener(event -> toggleNavigator());
        JButton inspectorButton = new JButton("Inspector");
        inspectorButton.addActionListener(event -> toggleInspector());
        JButton autoWidth = new JButton("Auto width");
        autoWidth.addActionListener(event -> autoSizeVisibleColumns());
        JLabel rowHeightLabel = new JLabel("Row height");
        JSlider rowHeight = new JSlider(18, 160, model.rowHeight());
        rowHeight.setFocusable(false);
        Dimension rowHeightSize = new Dimension(108, rowHeight.getPreferredSize().height);
        rowHeight.setPreferredSize(rowHeightSize);
        rowHeight.setMaximumSize(rowHeightSize);
        ChangeListener rowHeightListener = event -> model.setRowHeight(rowHeight.getValue());
        rowHeight.addChangeListener(rowHeightListener);
        JButton applyAll = new JButton("Apply all");
        applyAll.addActionListener(event -> applyAllDraftFilters());
        JButton discardAll = new JButton("Discard all");
        discardAll.addActionListener(event -> discardAllDraftFilters());
        toolbar.add(columns);
        toolbar.add(inspectorButton);
        toolbar.addSeparator();
        toolbar.add(autoWidth);
        toolbar.add(Box.createHorizontalStrut(8));
        toolbar.add(rowHeightLabel);
        toolbar.add(rowHeight);
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

    private final class WidthRecorder implements TableColumnModelListener {
        @Override
        public void columnAdded(TableColumnModelEvent event) {
        }

        @Override
        public void columnRemoved(TableColumnModelEvent event) {
        }

        @Override
        public void columnMoved(TableColumnModelEvent event) {
            recordVisibleColumnWidths();
        }

        @Override
        public void columnMarginChanged(javax.swing.event.ChangeEvent event) {
            recordVisibleColumnWidths();
        }

        @Override
        public void columnSelectionChanged(javax.swing.event.ListSelectionEvent event) {
        }
    }
}

package tech.molecules.structurized.prismlite.swing.workspace.views;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.RowSelectionSubscription;
import tech.molecules.structurized.prism.engine.ocl.CompoundTableColumnSpec;
import tech.molecules.structurized.prism.engine.ocl.CompoundTableViewSpec;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeRenderCache;
import tech.molecules.structurized.prismlite.swing.workspace.table.PhysicalRowMoleculeCellRenderer;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;

public final class CompoundTablePanel extends JPanel {
    private final PrismLiteWorkspaceModel workspace;
    private final CompoundTableViewSpec specification;
    private final List<Integer> physicalRows;
    private final CompoundTableModel tableModel;
    private final JTable table;
    private RowSelectionSubscription selectionSubscription;
    private boolean restoringSelection;

    public CompoundTablePanel(CompoundTableViewSpec specification, PrismLiteWorkspaceModel workspace) {
        super(new BorderLayout(0, 4));
        this.specification = specification;
        this.workspace = workspace;
        PrismSession session = workspace.session();
        this.physicalRows = resolveRows(session, specification);
        this.tableModel = new CompoundTableModel(session, specification, physicalRows);
        this.table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setRowHeight(96);
        table.getColumnModel().getColumn(0).setPreferredWidth(190);
        table.getColumnModel().getColumn(0).setCellRenderer(new PhysicalRowMoleculeCellRenderer(
                session.table().column(specification.structureColumnId()),
                new MoleculeRenderCache(session.table()),
                tableModel::physicalRowAt
        ));
        for (int column = 0; column < specification.columns().size(); column++) {
            table.getColumnModel().getColumn(column + 1).setCellRenderer(
                    new ValueRenderer(session, specification.columns().get(column), tableModel));
        }
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !restoringSelection) publishSelection();
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(640, Math.min(430, Math.max(150, 35 + physicalRows.size() * 96))));
        add(scroll, BorderLayout.CENTER);

        int total = session.rowSet(specification.rowSetId()).rowIds().size();
        JLabel count = new JLabel("Showing " + physicalRows.size() + " of " + total + " compounds");
        count.setHorizontalAlignment(SwingConstants.RIGHT);
        count.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 4));
        add(count, BorderLayout.SOUTH);
        subscribeToSelection();
        restoreSelection(session.viewState().selectionModel().selectedRows());
    }

    public JTable table() {
        return table;
    }

    public List<Integer> physicalRows() {
        return physicalRows;
    }

    private void publishSelection() {
        if (!specification.linkSelection()) return;
        BitSet selected = new BitSet(workspace.session().totalRowCount());
        for (int viewRow : table.getSelectedRows()) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            selected.set(tableModel.physicalRowAt(modelRow));
        }
        workspace.session().viewState().selectionModel().replace(selected);
        int leadViewRow = table.getSelectionModel().getLeadSelectionIndex();
        if (leadViewRow >= 0 && leadViewRow < table.getRowCount()) {
            workspace.setFocusedPhysicalRow(tableModel.physicalRowAt(table.convertRowIndexToModel(leadViewRow)));
        }
    }

    private void restoreSelection(BitSet selected) {
        if (!specification.linkSelection()) return;
        Runnable update = () -> {
            restoringSelection = true;
            try {
                table.clearSelection();
                for (int modelRow = 0; modelRow < physicalRows.size(); modelRow++) {
                    if (selected.get(tableModel.physicalRowAt(modelRow))) {
                        int viewRow = table.convertRowIndexToView(modelRow);
                        if (viewRow >= 0) table.addRowSelectionInterval(viewRow, viewRow);
                    }
                }
            } finally {
                restoringSelection = false;
            }
        };
        if (SwingUtilities.isEventDispatchThread()) update.run(); else SwingUtilities.invokeLater(update);
    }

    @Override
    public void removeNotify() {
        if (selectionSubscription != null) {
            selectionSubscription.close();
            selectionSubscription = null;
        }
        super.removeNotify();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        subscribeToSelection();
        restoreSelection(workspace.session().viewState().selectionModel().selectedRows());
    }

    private void subscribeToSelection() {
        if (selectionSubscription == null) {
            selectionSubscription = workspace.session().viewState().selectionModel().subscribe(this::restoreSelection);
        }
    }

    private static List<Integer> resolveRows(PrismSession session, CompoundTableViewSpec specification) {
        ArrayList<Integer> result = new ArrayList<>();
        for (String rowId : session.rowSet(specification.rowSetId()).rowIds()) {
            session.physicalRowForRowId(rowId).ifPresent(result::add);
            if (result.size() >= specification.maxRows()) break;
        }
        return List.copyOf(result);
    }

    private static final class CompoundTableModel extends AbstractTableModel {
        private final PrismSession session;
        private final CompoundTableViewSpec specification;
        private final List<Integer> physicalRows;

        private CompoundTableModel(
                PrismSession session,
                CompoundTableViewSpec specification,
                List<Integer> physicalRows
        ) {
            this.session = session;
            this.specification = specification;
            this.physicalRows = physicalRows;
        }

        @Override
        public int getRowCount() {
            return physicalRows.size();
        }

        @Override
        public int getColumnCount() {
            return specification.columns().size() + 1;
        }

        @Override
        public String getColumnName(int column) {
            if (column == 0) return session.table().column(specification.structureColumnId()).schema().displayName();
            CompoundTableColumnSpec item = specification.columns().get(column - 1);
            return item.label() == null ? session.table().column(item.columnId()).schema().displayName() : item.label();
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) return Object.class;
            PrismColumnType type = session.table().column(
                    specification.columns().get(columnIndex - 1).columnId()).type();
            return switch (type) {
                case NUMERIC -> Double.class;
                case INTEGER -> Number.class;
                case BOOLEAN -> Boolean.class;
                default -> Object.class;
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            int physicalRow = physicalRowAt(rowIndex);
            if (columnIndex == 0) return physicalRow;
            PrismColumn column = session.table().column(specification.columns().get(columnIndex - 1).columnId());
            return column.isMissing(physicalRow) ? null : column.valueAt(physicalRow);
        }

        private int physicalRowAt(int modelRow) {
            return physicalRows.get(modelRow);
        }
    }

    private static final class ValueRenderer extends DefaultTableCellRenderer {
        private final PrismColumn column;
        private final DecimalFormat format;
        private final CompoundTableModel model;

        private ValueRenderer(
                PrismSession session,
                CompoundTableColumnSpec specification,
                CompoundTableModel model
        ) {
            this.column = session.table().column(specification.columnId());
            this.format = specification.format() == null ? null : new DecimalFormat(
                    specification.format(), java.text.DecimalFormatSymbols.getInstance(Locale.ROOT));
            this.model = model;
        }

        @Override
        protected void setValue(Object value) {
            if (value == null) {
                setText("");
            } else if (format != null && value instanceof Number number) {
                setText(format.format(number));
            } else {
                setText(value.toString());
            }
        }

        @Override
        public java.awt.Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int columnIndex) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, columnIndex);
            if (value != null && format == null) {
                int modelRow = table.convertRowIndexToModel(row);
                setText(column.formattedValueAt(model.physicalRowAt(modelRow)));
            }
            return this;
        }
    }
}

package tech.molecules.structurized.prismlite.swing.workspace.views;

import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prism.engine.ocl.Sar2DViewSpec;
import tech.molecules.structurized.prism.engine.ocl.SarProjectionBuilder;
import tech.molecules.structurized.prism.engine.ocl.SarProjectionModels.AggregatedValue;
import tech.molecules.structurized.prism.engine.ocl.SarProjectionModels.CellKey;
import tech.molecules.structurized.prism.engine.ocl.SarProjectionModels.Sar2DCell;
import tech.molecules.structurized.prism.engine.ocl.SarProjectionModels.Sar2DModel;
import tech.molecules.structurized.prism.engine.ocl.SarSubstituent;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceController;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Sar2DViewRenderer implements PrismSwingViewRenderer {
    @Override public String viewType() { return Sar2DViewSpec.VIEW_TYPE; }

    @Override
    public JComponent createComponent(PrismViewRecord view, PrismLiteWorkspaceModel workspace,
                                      PrismLiteWorkspaceController controller, Runnable refresh) {
        if (!(view.specification() instanceof Sar2DViewSpec spec)) return message("Unsupported 2D SAR specification.");
        PrismSession session = workspace.session();
        Sar2DModel model;
        try {
            model = SarProjectionBuilder.build2D(session.snapshot(), spec);
        } catch (RuntimeException exception) {
            return message("Could not build 2D SAR: " + exception.getMessage());
        }
        SarSwingSupport.SelectionAwareTable table = new SarSwingSupport.SelectionAwareTable(session) {
            @Override public String getToolTipText(MouseEvent event) {
                int row = rowAtPoint(event.getPoint());
                int column = columnAtPoint(event.getPoint());
                if (row < 0 || column <= 0) return null;
                Sar2DCell cell = cell(model, row, column);
                if (cell == null) return "Empty SAR cell";
                int observations = cell.values().stream().mapToInt(AggregatedValue::valueCount).max().orElse(0);
                return SarSwingSupport.tooltip(cell.contributingRowIds(), observations,
                        cell.contextVariantCount(), session);
            }
        };
        table.setModel(new TwoDimTableModel(model));
        table.setRowHeight(Math.max(88, 24 + spec.values().size() * 22));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(0).setCellRenderer(new AxisRenderer(session, model));
        for (int column = 1; column < table.getColumnCount(); column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(145);
            table.getColumnModel().getColumn(column).setCellRenderer(new CellRenderer(session));
            table.getColumnModel().getColumn(column).setHeaderValue(model.columnSubstituents().get(column - 1));
        }
        table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
        table.getTableHeader().setPreferredSize(new Dimension(100, 84));
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (!spec.linkSelection()) return;
                int row = table.rowAtPoint(event.getPoint());
                int column = table.columnAtPoint(event.getPoint());
                if (row < 0) return;
                Set<String> rowIds = column == 0 ? rowIdsForRow(model, row)
                        : cellRowIds(model, row, column);
                if (!rowIds.isEmpty()) SarSwingSupport.publishSelection(session, workspace, rowIds, event);
            }
        });
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (!spec.linkSelection()) return;
                int column = table.columnAtPoint(event.getPoint());
                if (column <= 0) return;
                Set<String> rowIds = rowIdsForColumn(model, column);
                if (!rowIds.isEmpty()) SarSwingSupport.publishSelection(session, workspace, rowIds, event);
            }
        });
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JLabel(summary(model)), BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private static String summary(Sar2DModel model) {
        String value = model.totalRowGroupCount() + " × " + model.totalColumnGroupCount() + " substituent groups";
        if (!model.contextColumnIds().isEmpty()) value += "; projected across " + model.contextColumnIds().size() + " other dimensions";
        if (model.excludedRowCount() > 0) value += "; " + model.excludedRowCount() + " special rows excluded";
        if (model.truncated()) value += "; truncated";
        return value;
    }

    private static Sar2DCell cell(Sar2DModel model, int row, int column) {
        if (column <= 0 || row < 0 || row >= model.rowSubstituents().size()
                || column > model.columnSubstituents().size()) return null;
        return model.cells().get(new CellKey(model.rowSubstituents().get(row).identity(),
                model.columnSubstituents().get(column - 1).identity()));
    }

    private static Set<String> cellRowIds(Sar2DModel model, int row, int column) {
        Sar2DCell cell = cell(model, row, column);
        return cell == null ? Set.of() : cell.contributingRowIds();
    }

    private static Set<String> rowIdsForRow(Sar2DModel model, int row) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (int column = 1; column <= model.columnSubstituents().size(); column++)
            result.addAll(cellRowIds(model, row, column));
        return Set.copyOf(result);
    }

    private static Set<String> rowIdsForColumn(Sar2DModel model, int column) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (int row = 0; row < model.rowSubstituents().size(); row++)
            result.addAll(cellRowIds(model, row, column));
        return Set.copyOf(result);
    }

    private static final class TwoDimTableModel extends AbstractTableModel {
        private final Sar2DModel model;
        private TwoDimTableModel(Sar2DModel model) { this.model = model; }
        @Override public int getRowCount() { return model.rowSubstituents().size(); }
        @Override public int getColumnCount() { return model.columnSubstituents().size() + 1; }
        @Override public String getColumnName(int column) { return column == 0 ? "R\\C" : ""; }
        @Override public Object getValueAt(int row, int column) {
            return column == 0 ? model.rowSubstituents().get(row) : cell(model, row, column);
        }
    }

    private static final class CellRenderer implements TableCellRenderer {
        private final PrismSession session;
        private CellRenderer(PrismSession session) { this.session = session; }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            if (!(value instanceof Sar2DCell cell)) {
                JPanel empty = new JPanel();
                empty.setBackground(new Color(248, 248, 248));
                empty.setBorder(BorderFactory.createLineBorder(SarSwingSupport.GRID));
                return empty;
            }
            return SarSwingSupport.metricBands(cell.values(),
                    SarSwingSupport.intersects(session, cell.contributingRowIds()));
        }
    }

    private static final class AxisRenderer implements TableCellRenderer {
        private final PrismSession session;
        private final Sar2DModel model;
        private AxisRenderer(PrismSession session, Sar2DModel model) {
            this.session = session;
            this.model = model;
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.add(SarSwingSupport.substituent((SarSubstituent) value, 140, 78));
            boolean linked = SarSwingSupport.intersects(session, rowIdsForRow(model, row));
            wrapper.setBorder(BorderFactory.createLineBorder(linked ? SarSwingSupport.SELECTED : SarSwingSupport.GRID,
                    linked ? 2 : 1));
            return wrapper;
        }
    }

    private static final class HeaderRenderer implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            if (value instanceof SarSubstituent substituent) {
                JPanel wrapper = new JPanel(new BorderLayout());
                wrapper.add(SarSwingSupport.substituent(substituent, 135, 76));
                wrapper.setBorder(BorderFactory.createLineBorder(SarSwingSupport.GRID));
                return wrapper;
            }
            JLabel label = new JLabel(String.valueOf(value), JLabel.CENTER);
            label.setBorder(BorderFactory.createLineBorder(SarSwingSupport.GRID));
            return label;
        }
    }

    private static JComponent message(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(text), BorderLayout.CENTER);
        return panel;
    }
}

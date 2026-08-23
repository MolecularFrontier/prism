package tech.molecules.structurized.prismlite.swing.workspace.views;

import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prism.engine.ocl.Sar1DViewSpec;
import tech.molecules.structurized.prism.engine.ocl.SarProjectionBuilder;
import tech.molecules.structurized.prism.engine.ocl.SarProjectionModels.AggregatedValue;
import tech.molecules.structurized.prism.engine.ocl.SarProjectionModels.Sar1DModel;
import tech.molecules.structurized.prism.engine.ocl.SarProjectionModels.Sar1DRow;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceController;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class Sar1DViewRenderer implements PrismSwingViewRenderer {
    @Override public String viewType() { return Sar1DViewSpec.VIEW_TYPE; }

    @Override
    public JComponent createComponent(PrismViewRecord view, PrismLiteWorkspaceModel workspace,
                                      PrismLiteWorkspaceController controller, Runnable refresh) {
        if (!(view.specification() instanceof Sar1DViewSpec spec)) return message("Unsupported 1D SAR specification.");
        PrismSession session = workspace.session();
        Sar1DModel model;
        try {
            model = SarProjectionBuilder.build1D(session.snapshot(), spec);
        } catch (RuntimeException exception) {
            return message("Could not build 1D SAR: " + exception.getMessage());
        }
        SarSwingSupport.SelectionAwareTable table = new SarSwingSupport.SelectionAwareTable(session) {
            @Override public String getToolTipText(MouseEvent event) {
                int row = rowAtPoint(event.getPoint());
                if (row < 0) return null;
                Sar1DRow item = model.rows().get(convertRowIndexToModel(row));
                int observations = item.values().stream().mapToInt(AggregatedValue::valueCount).max().orElse(0);
                return SarSwingSupport.tooltip(item.contributingRowIds(), observations,
                        item.contextVariantCount(), session);
            }
        };
        table.setModel(new OneDimTableModel(model));
        table.setRowHeight(92);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(180);
        table.getColumnModel().getColumn(0).setCellRenderer(new SarSwingSupport.SubstituentRenderer(170, 84));
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        for (int column = 2; column < table.getColumnCount(); column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(130);
            table.getColumnModel().getColumn(column).setCellRenderer(new MetricRenderer(session, model));
        }
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int displayed = table.rowAtPoint(event.getPoint());
                if (displayed < 0 || !spec.linkSelection()) return;
                Sar1DRow row = model.rows().get(table.convertRowIndexToModel(displayed));
                SarSwingSupport.publishSelection(session, workspace, row.contributingRowIds(), event);
            }
        });
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JLabel(summary(model)), BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private static String summary(Sar1DModel model) {
        String value = model.totalGroupCount() + " substituents";
        if (!model.contextColumnIds().isEmpty()) value += "; context checked across " + model.contextColumnIds().size() + " dimensions";
        if (model.excludedRowCount() > 0) value += "; " + model.excludedRowCount() + " special rows excluded";
        if (model.truncated()) value += "; truncated";
        return value;
    }

    private static final class OneDimTableModel extends AbstractTableModel {
        private final Sar1DModel model;

        private OneDimTableModel(Sar1DModel model) { this.model = model; }
        @Override public int getRowCount() { return model.rows().size(); }
        @Override public int getColumnCount() { return model.rows().isEmpty() ? 2 : 2 + model.rows().getFirst().values().size(); }
        @Override public String getColumnName(int column) {
            if (column == 0) return "Substituent";
            if (column == 1) return "n";
            var spec = model.rows().getFirst().values().get(column - 2).specification();
            return spec.label() == null ? spec.columnId() : spec.label();
        }
        @Override public Object getValueAt(int row, int column) {
            Sar1DRow value = model.rows().get(row);
            if (column == 0) return value.substituent();
            if (column == 1) return value.contributingRowIds().size();
            return value.values().get(column - 2);
        }
    }

    private static final class MetricRenderer extends DefaultTableCellRenderer {
        private final PrismSession session;
        private final Sar1DModel model;

        private MetricRenderer(PrismSession session, Sar1DModel model) {
            this.session = session;
            this.model = model;
            setHorizontalAlignment(CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            AggregatedValue metric = (AggregatedValue) value;
            Sar1DRow item = model.rows().get(table.convertRowIndexToModel(row));
            boolean globallySelected = SarSwingSupport.intersects(session, item.contributingRowIds());
            super.getTableCellRendererComponent(table,
                    metric.value() == null ? "—" : SarSwingSupport.format(metric.value(), metric.specification().format()),
                    globallySelected, focus, row, column);
            setOpaque(true);
            setBackground(SarSwingSupport.scoreColor(metric.score()));
            setBorder(BorderFactory.createLineBorder(globallySelected ? SarSwingSupport.SELECTED : SarSwingSupport.GRID,
                    globallySelected ? 2 : 1));
            return this;
        }
    }

    private static JComponent message(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(text), BorderLayout.CENTER);
        return panel;
    }
}

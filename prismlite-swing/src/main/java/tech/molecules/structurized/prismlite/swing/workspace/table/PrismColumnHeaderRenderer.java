package tech.molecules.structurized.prismlite.swing.workspace.table;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.SortDirection;
import tech.molecules.structurized.prism.engine.SortKey;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.util.List;
import java.util.Objects;

public final class PrismColumnHeaderRenderer extends DefaultTableCellRenderer {
    private final PrismLiteWorkspaceModel model;

    public PrismColumnHeaderRenderer(PrismLiteWorkspaceModel model) {
        this.model = Objects.requireNonNull(model, "model");
        setHorizontalAlignment(JLabel.LEFT);
        setVerticalAlignment(JLabel.CENTER);
    }
    @Override
    public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (column < 0 || column >= model.session().visibleColumnCount()) {
            return this;
        }
        PrismColumn prismColumn = model.session().visibleColumn(column);
        setText(headerText(prismColumn));
        setToolTipText(prismColumn.id());
        if (Objects.equals(model.focusedColumnId(), prismColumn.id())) {
            setBackground(new Color(220, 235, 252));
        } else {
            setBackground(table.getTableHeader().getBackground());
        }
        return this;
    }
    private String headerText(PrismColumn column) {
        StringBuilder indicators = new StringBuilder();
        if (model.hasAppliedColumnFilter(column.id())) {
            indicators.append(" *");
        }
        if (model.isDirty(column.id())) {
            indicators.append(" draft");
        }
        if (model.isComputedColumn(column.id())) {
            indicators.append(" fx");
        }
        SortDirection direction = sortDirection(column.id());
        if (direction == SortDirection.ASCENDING) {
            indicators.append(" asc");
        } else if (direction == SortDirection.DESCENDING) {
            indicators.append(" desc");
        }
        String unit = column.schema().unit();
        String line2 = unit == null || unit.isBlank() ? column.type().name().toLowerCase() : unit;
        return "<html>" + escape(column.schema().displayName()) + escape(indicators.toString())
                + "<br><span style='font-size:9px;color:#666666'>" + escape(line2) + "</span></html>";
    }
    private SortDirection sortDirection(String columnId) {
        List<SortKey> sortKeys = model.session().viewState().sortKeys();
        if (sortKeys.isEmpty() || !sortKeys.getFirst().columnId().equals(columnId)) {
            return null;
        }
        return sortKeys.getFirst().direction();
    }
    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

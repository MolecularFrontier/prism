package tech.molecules.structurized.prismlite.swing.workspace.table;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;
import tech.molecules.structurized.prismlite.swing.workspace.profile.ScoreDisplayService;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;

public final class ScoreColumnCellRendererProvider implements PrismColumnCellRendererProvider {
    @Override
    public boolean supports(PrismColumn column) {
        String semanticType = column.schema().semanticType();
        return "endpoint_score".equals(semanticType) || "mpo_score".equals(semanticType);
    }

    @Override
    public TableCellRenderer createRenderer(PrismLiteWorkspaceModel model, PrismColumn column) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table,
                                                           Object value,
                                                           boolean selected,
                                                           boolean focused,
                                                           int row,
                                                           int columnIndex) {
                super.getTableCellRendererComponent(table, value, selected, focused, row, columnIndex);
                if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
                    setText(ScoreDisplayService.format(number.doubleValue()));
                    setToolTipText("Desirability score: " + ScoreDisplayService.format(number.doubleValue()));
                    if (!selected) setBackground(ScoreDisplayService.softScoreColor(number.doubleValue()));
                } else {
                    setText("");
                    setToolTipText("Score unavailable");
                    if (!selected) setBackground(Color.WHITE);
                }
                return this;
            }
        };
    }
}

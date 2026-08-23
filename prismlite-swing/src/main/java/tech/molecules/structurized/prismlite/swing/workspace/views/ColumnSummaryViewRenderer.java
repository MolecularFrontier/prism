package tech.molecules.structurized.prismlite.swing.workspace.views;

import tech.molecules.structurized.prism.engine.CategoricalColumnSummary;
import tech.molecules.structurized.prism.engine.ColumnSummaries;
import tech.molecules.structurized.prism.engine.ColumnSummary;
import tech.molecules.structurized.prism.engine.ColumnSummaryViewSpec;
import tech.molecules.structurized.prism.engine.NumericColumnSummary;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceController;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GridLayout;
import java.text.DecimalFormat;
import java.util.BitSet;

public final class ColumnSummaryViewRenderer implements PrismSwingViewRenderer {
    private static final DecimalFormat NUMBER = new DecimalFormat("0.###");

    @Override public String viewType() {
        return ColumnSummaryViewSpec.VIEW_TYPE;
    }

    @Override
    public JComponent createComponent(PrismViewRecord view, PrismLiteWorkspaceModel model,
                                      PrismLiteWorkspaceController controller, Runnable refresh) {
        if (!(view.specification() instanceof ColumnSummaryViewSpec spec)) {
            return new JLabel("Unsupported column-summary specification.");
        }
        var session = model.session();
        BitSet rows = new BitSet(session.totalRowCount());
        for (String rowId : session.rowSet(spec.rowSetId()).rowIds()) {
            session.physicalRowForRowId(rowId).ifPresent(rows::set);
        }
        int columns = Math.max(1, Math.min(3, spec.columnIds().size()));
        JPanel cards = new JPanel(new GridLayout(0, columns, 8, 8));
        cards.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (String columnId : spec.columnIds()) {
            PrismColumn column = session.table().column(columnId);
            cards.add(card(column, ColumnSummaries.compute(column, rows)));
        }
        return cards;
    }

    private static JComponent card(PrismColumn column, ColumnSummary summary) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(215, 218, 223)),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        card.add(new JLabel("<html><b>" + escape(column.schema().displayName()) + "</b></html>"));
        card.add(Box.createVerticalStrut(5));
        card.add(new JLabel("Values: " + summary.validCount()));
        card.add(new JLabel("Missing: " + summary.missingCount()));
        if (summary instanceof NumericColumnSummary numeric) {
            card.add(new JLabel("Minimum: " + number(numeric.minimum())));
            card.add(new JLabel("Median: " + number(numeric.median())));
            card.add(new JLabel("Mean: " + number(numeric.mean())));
            card.add(new JLabel("Maximum: " + number(numeric.maximum())));
        } else if (summary instanceof CategoricalColumnSummary categorical) {
            card.add(new JLabel("Distinct: " + categorical.distinctCount()));
            card.add(Box.createVerticalStrut(4));
            categorical.topValues().stream().limit(5).forEach(value ->
                    card.add(new JLabel(escape(value.value()) + ": " + value.count())));
        }
        return card;
    }

    private static String number(double value) {
        return Double.isFinite(value) ? NUMBER.format(value) : "—";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

package tech.molecules.structurized.prismlite.swing.workspace.views;

import com.actelion.research.chem.IDCodeParser;
import com.actelion.research.chem.StereoMolecule;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.RowSelectionSubscription;
import tech.molecules.structurized.prism.engine.ocl.SarProjectionModels.AggregatedValue;
import tech.molecules.structurized.prism.engine.ocl.SarSubstituent;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeViewPanel;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

final class SarSwingSupport {
    static final Color SELECTED = new Color(51, 102, 204);
    static final Color GRID = new Color(210, 214, 220);

    private SarSwingSupport() {
    }

    static JComponent substituent(SarSubstituent substituent, int width, int height) {
        if (substituent.idcode() == null) {
            JLabel label = new JLabel(substituent.label(), SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(Color.WHITE);
            label.setPreferredSize(new Dimension(width, height));
            return label;
        }
        try {
            StereoMolecule molecule = new StereoMolecule();
            new IDCodeParser().parse(molecule, substituent.idcode());
            MoleculeViewPanel panel = new MoleculeViewPanel();
            panel.setMolecule(molecule);
            panel.setPreferredSize(new Dimension(width, height));
            return panel;
        } catch (RuntimeException exception) {
            JLabel label = new JLabel(substituent.label(), SwingConstants.CENTER);
            label.setPreferredSize(new Dimension(width, height));
            return label;
        }
    }

    static JPanel metricBands(List<AggregatedValue> values, boolean selected) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(true);
        for (AggregatedValue value : values) {
            String label = value.specification().label() == null
                    ? value.specification().columnId() : value.specification().label();
            String text = value.value() == null ? "—" : format(value.value(), value.specification().format());
            JLabel band = new JLabel(label + ": " + text, SwingConstants.CENTER);
            band.setOpaque(true);
            band.setBackground(scoreColor(value.score()));
            band.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(band);
        }
        panel.setBorder(BorderFactory.createLineBorder(selected ? SELECTED : GRID, selected ? 2 : 1));
        return panel;
    }

    static String format(double value, String pattern) {
        if (pattern == null) return Double.toString(value);
        try {
            return new DecimalFormat(pattern).format(value);
        } catch (IllegalArgumentException exception) {
            return Double.toString(value);
        }
    }

    static Color scoreColor(Double score) {
        if (score == null || !Double.isFinite(score)) return Color.WHITE;
        double bounded = Math.max(0.0, Math.min(1.0, score));
        Color low = new Color(246, 190, 190);
        Color middle = new Color(250, 236, 174);
        Color high = new Color(190, 230, 194);
        return bounded <= 0.5 ? blend(low, middle, bounded * 2.0)
                : blend(middle, high, (bounded - 0.5) * 2.0);
    }

    private static Color blend(Color left, Color right, double fraction) {
        return new Color(
                (int) Math.round(left.getRed() + (right.getRed() - left.getRed()) * fraction),
                (int) Math.round(left.getGreen() + (right.getGreen() - left.getGreen()) * fraction),
                (int) Math.round(left.getBlue() + (right.getBlue() - left.getBlue()) * fraction));
    }

    static boolean intersects(PrismSession session, Set<String> rowIds) {
        BitSet selected = session.viewState().selectionModel().selectedRows();
        for (String rowId : rowIds) {
            var row = session.physicalRowForRowId(rowId);
            if (row.isPresent() && selected.get(row.getAsInt())) return true;
        }
        return false;
    }

    static void publishSelection(PrismSession session, PrismLiteWorkspaceModel model,
                                 Set<String> rowIds, MouseEvent event) {
        boolean additive = (event.getModifiersEx()
                & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
        BitSet selected = additive ? session.viewState().selectionModel().selectedRows()
                : new BitSet(session.totalRowCount());
        Integer first = null;
        for (String rowId : rowIds) {
            var row = session.physicalRowForRowId(rowId);
            if (row.isEmpty()) continue;
            int physical = row.getAsInt();
            if (first == null) first = physical;
            selected.set(physical, !additive || !selected.get(physical));
        }
        session.viewState().selectionModel().replace(selected);
        if (first != null) model.setFocusedPhysicalRow(first);
    }

    static String tooltip(Set<String> rowIds, int valueCount, int contextVariants, PrismSession session) {
        int selected = 0;
        BitSet selection = session.viewState().selectionModel().selectedRows();
        for (String rowId : rowIds) {
            var row = session.physicalRowForRowId(rowId);
            if (row.isPresent() && selection.get(row.getAsInt())) selected++;
        }
        return "Compounds: " + rowIds.size() + " | selected: " + selected
                + " | observations: " + valueCount
                + (contextVariants > 1 ? " | mixed contexts: " + contextVariants : " | clean context");
    }

    static class SelectionAwareTable extends JTable {
        private final PrismSession session;
        private RowSelectionSubscription subscription;

        SelectionAwareTable(PrismSession session) {
            this.session = session;
        }

        @Override public void addNotify() {
            super.addNotify();
            if (subscription == null) subscription = session.viewState().selectionModel().subscribe(this::selectionChanged);
        }

        @Override public void removeNotify() {
            if (subscription != null) {
                subscription.close();
                subscription = null;
            }
            super.removeNotify();
        }

        private void selectionChanged(BitSet ignored) {
            if (SwingUtilities.isEventDispatchThread()) repaint();
            else SwingUtilities.invokeLater(this::repaint);
        }
    }

    static final class SubstituentRenderer implements TableCellRenderer {
        private final int width;
        private final int height;

        SubstituentRenderer(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            JPanel wrapper = new JPanel(new BorderLayout());
            if (value instanceof SarSubstituent substituent) wrapper.add(substituent(substituent, width, height));
            wrapper.setBorder(BorderFactory.createLineBorder(selected ? SELECTED : GRID, selected ? 2 : 1));
            return wrapper;
        }
    }
}

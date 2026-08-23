package tech.molecules.structurized.prismlite.swing.workspace.views;

import com.actelion.research.chem.StereoMolecule;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.RowSelectionSubscription;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prism.engine.SortDirection;
import tech.molecules.structurized.prism.engine.ocl.StructureGridValueSpec;
import tech.molecules.structurized.prism.engine.ocl.StructureGridViewSpec;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceController;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeRenderCache;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeViewPanel;
import tech.molecules.structurized.prismlite.swing.workspace.profile.ScoreDisplayService;

import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.JList;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.DefaultListModel;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class StructureGridViewRenderer implements PrismSwingViewRenderer {
    @Override
    public String viewType() {
        return StructureGridViewSpec.VIEW_TYPE;
    }

    @Override
    public JComponent createComponent(
            PrismViewRecord view,
            PrismLiteWorkspaceModel model,
            PrismLiteWorkspaceController controller,
            Runnable refresh
    ) {
        if (!(view.specification() instanceof StructureGridViewSpec spec)) {
            return message("Unsupported structure-grid specification.");
        }
        PrismSession session = model.session();
        PrismColumn structureColumn = session.table().column(spec.structureColumnId());
        if (structureColumn.type() != PrismColumnType.MOLECULE) {
            return message("Structure column is not a molecule column: " + spec.structureColumnId());
        }

        List<Integer> physicalRows = resolvedRows(session, spec);
        sortRows(session, spec, physicalRows);
        if (physicalRows.size() > spec.maxCompounds()) {
            physicalRows = physicalRows.subList(0, spec.maxCompounds());
        }
        if (physicalRows.isEmpty()) {
            return message("No structures to display.");
        }

        MoleculeRenderCache cache = new MoleculeRenderCache(session.table());
        SelectionAwareGridPanel grid = new SelectionAwareGridPanel(session, spec.columns());
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (Integer physicalRow : physicalRows) {
            grid.addCard(physicalRow, card(session, model, structureColumn, spec, cache, physicalRow));
        }
        JScrollPane scroll = new JScrollPane(grid);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    @Override
    public JComponent createConfigurationComponent(
            PrismViewRecord view,
            PrismLiteWorkspaceModel model,
            PrismLiteWorkspaceController controller,
            Runnable refresh
    ) {
        if (!(view.specification() instanceof StructureGridViewSpec spec)) {
            return null;
        }
        PrismSession session = model.session();
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        JTextField title = new JTextField(spec.title(), 18);
        JComboBox<String> rowSet = new JComboBox<>();
        rowSet.addItem("");
        for (PrismRowSet existing : session.rowSets()) {
            rowSet.addItem(existing.id());
        }
        rowSet.setSelectedItem(spec.rowSetId() == null ? "" : spec.rowSetId());
        JComboBox<String> sortColumn = new JComboBox<>();
        sortColumn.addItem("");
        DefaultListModel<String> endpointModel = new DefaultListModel<>();
        for (PrismColumn column : session.table().columns()) {
            sortColumn.addItem(column.id());
            if (!column.id().equals(spec.structureColumnId())) {
                endpointModel.addElement(column.id());
            }
        }
        sortColumn.setSelectedItem(spec.sortColumnId() == null ? "" : spec.sortColumnId());
        JComboBox<SortDirection> sortDirection = new JComboBox<>(SortDirection.values());
        sortDirection.setSelectedItem(spec.sortDirection());
        JList<String> endpoints = new JList<>(endpointModel);
        endpoints.setVisibleRowCount(Math.min(6, Math.max(4, endpointModel.size())));
        for (int index = 0; index < endpointModel.size(); index++) {
            if (spec.endpointColumnIds().contains(endpointModel.get(index))) {
                endpoints.addSelectionInterval(index, index);
            }
        }
        JTextField maxCompounds = new JTextField(String.valueOf(spec.maxCompounds()), 6);
        JTextField columns = new JTextField(String.valueOf(spec.columns()), 6);
        JButton apply = new JButton("Apply");
        apply.addActionListener(event -> {
            String nextRowSetId = stringValue(rowSet.getSelectedItem());
            String nextSortColumnId = stringValue(sortColumn.getSelectedItem());
            List<String> nextEndpointIds = endpoints.getSelectedValuesList();
            Map<String, StructureGridValueSpec> currentValues = new LinkedHashMap<>();
            spec.valueSpecifications().forEach(value -> currentValues.put(value.columnId(), value));
            List<StructureGridValueSpec> nextValues = nextEndpointIds.stream()
                    .map(column -> currentValues.getOrDefault(column, new StructureGridValueSpec(column)))
                    .toList();
            StructureGridViewSpec updatedSpec = new StructureGridViewSpec(
                    spec.viewId(),
                    title.getText(),
                    nextRowSetId,
                    spec.structureColumnId(),
                    nextEndpointIds,
                    nextSortColumnId,
                    (SortDirection) sortDirection.getSelectedItem(),
                    parseInt(maxCompounds.getText(), spec.maxCompounds()),
                    parseInt(columns.getText(), spec.columns()),
                    nextValues
            );
            Map<String, Object> provenance = new LinkedHashMap<>(view.provenance());
            provenance.put("updatedAt", Instant.now().toString());
            session.updateView(new PrismViewRecord(
                    updatedSpec.viewId(),
                    updatedSpec.viewType(),
                    updatedSpec.title(),
                    updatedSpec,
                    view.createdAt(),
                    provenance
            ));
            refresh.run();
        });

        addConfigRow(panel, 0, "Title", title);
        addConfigRow(panel, 1, "Row set", rowSet);
        addConfigRow(panel, 2, "Endpoints", new JScrollPane(endpoints));
        addConfigRow(panel, 3, "Sort by", sortColumn);
        addConfigRow(panel, 4, "Direction", sortDirection);
        addConfigRow(panel, 5, "Max", maxCompounds);
        addConfigRow(panel, 6, "Columns", columns);
        addConfigRow(panel, 7, "", apply);
        return panel;
    }

    private static List<Integer> resolvedRows(PrismSession session, StructureGridViewSpec spec) {
        ArrayList<Integer> rows = new ArrayList<>();
        int[] visiblePhysicalRows = session.visiblePhysicalRows();
        if (spec.rowSetId() == null) {
            for (int physicalRow : visiblePhysicalRows) {
                rows.add(physicalRow);
            }
            return rows;
        }

        BitSet visibleRows = new BitSet(session.totalRowCount());
        for (int physicalRow : visiblePhysicalRows) {
            visibleRows.set(physicalRow);
        }
        PrismRowSet rowSet = session.rowSet(spec.rowSetId());
        for (String rowId : rowSet.rowIds()) {
            java.util.OptionalInt physicalRow = session.physicalRowForRowId(rowId);
            if (physicalRow.isPresent() && visibleRows.get(physicalRow.getAsInt())) {
                rows.add(physicalRow.getAsInt());
            }
        }
        return rows;
    }

    private static void sortRows(PrismSession session, StructureGridViewSpec spec, List<Integer> physicalRows) {
        if (spec.sortColumnId() == null) {
            return;
        }
        PrismColumn sortColumn = session.table().column(spec.sortColumnId());
        Comparator<Integer> comparator = (left, right) -> compareRows(sortColumn, left, right, spec.sortDirection());
        physicalRows.sort(comparator.thenComparingInt(Integer::intValue));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compareRows(PrismColumn column, int left, int right, SortDirection direction) {
        boolean leftMissing = column.isMissing(left);
        boolean rightMissing = column.isMissing(right);
        if (leftMissing || rightMissing) {
            if (leftMissing && rightMissing) {
                return 0;
            }
            return leftMissing ? 1 : -1;
        }
        Object leftValue = column.valueAt(left);
        Object rightValue = column.valueAt(right);
        int comparison;
        if (column.type() == PrismColumnType.NUMERIC || column.type() == PrismColumnType.INTEGER) {
            comparison = Double.compare(column.doubleValueAt(left), column.doubleValueAt(right));
        } else if (leftValue instanceof Comparable comparable && rightValue != null) {
            comparison = comparable.compareTo(rightValue);
        } else {
            comparison = Objects.toString(leftValue, "").compareTo(Objects.toString(rightValue, ""));
        }
        return direction == SortDirection.DESCENDING ? -comparison : comparison;
    }

    private static JPanel card(
            PrismSession session,
            PrismLiteWorkspaceModel model,
            PrismColumn structureColumn,
            StructureGridViewSpec spec,
            MoleculeRenderCache cache,
            int physicalRow
    ) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(Color.WHITE);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        boolean selected = session.viewState().selectionModel().isSelected(physicalRow);
        updateSelectionBorder(card, selected);

        JLabel title = new JLabel(session.rowIdForPhysicalRow(physicalRow));
        card.add(title, BorderLayout.NORTH);

        MoleculeViewPanel moleculeView = new MoleculeViewPanel();
        moleculeView.setPreferredSize(new Dimension(220, 150));
        StereoMolecule molecule = cache.molecule(structureColumn, physicalRow);
        moleculeView.setMolecule(molecule);
        card.add(moleculeView, BorderLayout.CENTER);

        JPanel values = new JPanel();
        values.setOpaque(false);
        values.setLayout(new BoxLayout(values, BoxLayout.Y_AXIS));
        for (StructureGridValueSpec valueSpec : spec.valueSpecifications()) {
            values.add(valueLabel(session, valueSpec, physicalRow));
        }
        card.add(values, BorderLayout.SOUTH);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                boolean additive = (event.getModifiersEx()
                        & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
                BitSet selectedRows = additive
                        ? session.viewState().selectionModel().selectedRows()
                        : new BitSet(session.totalRowCount());
                selectedRows.set(physicalRow, !additive || !selectedRows.get(physicalRow));
                session.viewState().selectionModel().replace(selectedRows);
                model.setFocusedPhysicalRow(physicalRow);
            }
        });
        return card;
    }

    private static JLabel valueLabel(PrismSession session, StructureGridValueSpec specification, int physicalRow) {
        PrismColumn valueColumn = session.table().column(specification.columnId());
        String labelText = specification.label() == null
                ? valueColumn.schema().displayName() : specification.label();
        String valueText = formattedValue(valueColumn, specification.format(), physicalRow);
        JLabel label = new JLabel(labelText + ": " + valueText);
        PrismColumn colorColumn = colorColumn(session, valueColumn, specification.colorColumnId());
        Double score = scoreAt(colorColumn, physicalRow);
        if (score != null) {
            label.setOpaque(true);
            label.setBackground(ScoreDisplayService.softScoreColor(score));
            label.setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));
            label.setToolTipText("Desirability score: " + ScoreDisplayService.format(score));
        }
        return label;
    }

    private static String formattedValue(PrismColumn column, String format, int physicalRow) {
        if (column.isMissing(physicalRow)) return "";
        Object value = column.valueAt(physicalRow);
        if (format != null && value instanceof Number number) {
            return new java.text.DecimalFormat(format,
                    java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT)).format(number);
        }
        return column.formattedValueAt(physicalRow);
    }

    private static PrismColumn colorColumn(PrismSession session, PrismColumn valueColumn, String colorColumnId) {
        if (colorColumnId != null) return session.table().column(colorColumnId);
        String semanticType = valueColumn.schema().semanticType();
        return isScoreSemanticType(semanticType) ? valueColumn : null;
    }

    private static boolean isScoreSemanticType(String semanticType) {
        return "endpoint_score".equals(semanticType) || "mpo_score".equals(semanticType);
    }

    private static Double scoreAt(PrismColumn colorColumn, int physicalRow) {
        if (colorColumn == null || colorColumn.isMissing(physicalRow)) return null;
        Object value = colorColumn.valueAt(physicalRow);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) return null;
        return number.doubleValue();
    }

    private static void updateSelectionBorder(JPanel card, boolean selected) {
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        selected ? new Color(51, 102, 204) : new Color(210, 214, 220),
                        selected ? 2 : 1),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
    }

    private static final class SelectionAwareGridPanel extends JPanel {
        private final PrismSession session;
        private final Map<Integer, JPanel> cards = new LinkedHashMap<>();
        private RowSelectionSubscription subscription;

        private SelectionAwareGridPanel(PrismSession session, int columns) {
            super(new GridLayout(0, columns, 8, 8));
            this.session = session;
        }

        private void addCard(int physicalRow, JPanel card) {
            cards.put(physicalRow, card);
            add(card);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (subscription == null) {
                subscription = session.viewState().selectionModel().subscribe(this::selectionChanged);
            }
            selectionChanged(session.viewState().selectionModel().selectedRows());
        }

        @Override
        public void removeNotify() {
            if (subscription != null) {
                subscription.close();
                subscription = null;
            }
            super.removeNotify();
        }

        private void selectionChanged(BitSet selected) {
            Runnable update = () -> cards.forEach((row, card) ->
                    updateSelectionBorder(card, selected.get(row)));
            if (javax.swing.SwingUtilities.isEventDispatchThread()) update.run();
            else javax.swing.SwingUtilities.invokeLater(update);
        }
    }

    private static void addConfigRow(JPanel panel, int row, String label, JComponent component) {
        GridBagConstraints labelConstraints = configConstraints(0, row);
        panel.add(new JLabel(label), labelConstraints);
        GridBagConstraints componentConstraints = configConstraints(1, row);
        componentConstraints.fill = GridBagConstraints.HORIZONTAL;
        componentConstraints.weightx = 1.0;
        panel.add(component, componentConstraints);
    }

    private static GridBagConstraints configConstraints(int x, int y) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.insets = new Insets(2, 2, 2, 2);
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private static String stringValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static JComponent message(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(text), BorderLayout.CENTER);
        return panel;
    }
}

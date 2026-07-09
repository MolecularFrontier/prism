package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.CategoryIncludeFilter;
import tech.molecules.structurized.prism.engine.FilterCapability;
import tech.molecules.structurized.prism.engine.MissingValueFilter;
import tech.molecules.structurized.prism.engine.MissingValueMode;
import tech.molecules.structurized.prism.engine.NumericRangeFilter;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismFilter;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.TextPatternFilter;
import tech.molecules.structurized.prism.engine.TextPatternMode;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PrismLiteFilterPanel extends JPanel {
    public static final int DEFAULT_CATEGORY_THRESHOLD = 200;

    private final PrismSession session;
    private final Runnable refresh;
    private final int categoryThreshold;
    private final Map<String, PrismFilter> filtersByKey = new LinkedHashMap<>();

    public PrismLiteFilterPanel(PrismSession session, Runnable refresh) {
        this(session, refresh, DEFAULT_CATEGORY_THRESHOLD);
    }

    public PrismLiteFilterPanel(PrismSession session, Runnable refresh, int categoryThreshold) {
        super(new BorderLayout());
        this.session = Objects.requireNonNull(session, "session");
        this.refresh = refresh == null ? () -> { } : refresh;
        this.categoryThreshold = Math.max(1, categoryThreshold);
        buildUi();
    }

    public void setNumericRangeFilter(String columnId, Double min, Double max, boolean includeMissing) {
        String key = key("numeric", columnId);
        if (min == null && max == null && includeMissing) {
            filtersByKey.remove(key);
        } else if (min == null && max == null) {
            filtersByKey.put(key, new MissingValueFilter(columnId, MissingValueMode.HAS_VALUE));
        } else {
            filtersByKey.put(key, new NumericRangeFilter(columnId, min, max, includeMissing));
        }
        applyFilters();
    }

    public void setTextFilter(String columnId,
                              String pattern,
                              TextPatternMode mode,
                              boolean caseInsensitive,
                              boolean includeMissing) {
        String key = key("text", columnId);
        if (pattern == null || pattern.isBlank()) {
            filtersByKey.remove(key);
        } else {
            filtersByKey.put(key, new TextPatternFilter(columnId, pattern, mode, caseInsensitive, includeMissing));
        }
        applyFilters();
    }

    public void setCategoryFilter(String columnId, Set<String> includedValues, boolean includeMissing) {
        String key = key("category", columnId);
        if ((includedValues == null || includedValues.isEmpty()) && includeMissing) {
            filtersByKey.remove(key);
        } else if (includedValues == null || includedValues.isEmpty()) {
            filtersByKey.put(key, new MissingValueFilter(columnId, MissingValueMode.MISSING));
        } else {
            filtersByKey.put(key, new CategoryIncludeFilter(columnId, includedValues, includeMissing));
        }
        applyFilters();
    }

    public void clearAllFilters() {
        filtersByKey.clear();
        applyFilters();
    }

    public List<PrismFilter> activeFilters() {
        return List.copyOf(filtersByKey.values());
    }

    private void buildUi() {
        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel("Filters"), BorderLayout.WEST);
        JButton reset = new JButton("Reset");
        reset.addActionListener(event -> clearAllFilters());
        header.add(reset, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (PrismColumn column : session.table().columns()) {
            JPanel editor = editorFor(column);
            if (editor != null) {
                list.add(editor);
                list.add(Box.createVerticalStrut(6));
            }
        }
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    private JPanel editorFor(PrismColumn column) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(column.schema().displayName()));
        if (column.filterCapabilities().contains(FilterCapability.NUMERIC_RANGE)) {
            panel.add(numericEditor(column), BorderLayout.CENTER);
            return panel;
        }
        if (shouldShowCategoryEditor(column)) {
            panel.add(categoryEditor(column), BorderLayout.CENTER);
            return panel;
        }
        if (column.filterCapabilities().contains(FilterCapability.TEXT_CONTAINS)) {
            panel.add(textEditor(column), BorderLayout.CENTER);
            return panel;
        }
        return null;
    }

    private JPanel numericEditor(PrismColumn column) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JTextField min = new JTextField(6);
        JTextField max = new JTextField(6);
        JCheckBox includeMissing = new JCheckBox("missing", true);
        JButton apply = new JButton("Apply");
        apply.addActionListener(event -> setNumericRangeFilter(
                column.id(),
                parseNullableDouble(min.getText()),
                parseNullableDouble(max.getText()),
                includeMissing.isSelected()
        ));
        panel.add(new JLabel("min"));
        panel.add(min);
        panel.add(new JLabel("max"));
        panel.add(max);
        panel.add(includeMissing);
        panel.add(apply);
        return panel;
    }

    private JPanel textEditor(PrismColumn column) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JTextField pattern = new JTextField(12);
        JComboBox<TextPatternMode> mode = new JComboBox<>(TextPatternMode.values());
        JCheckBox caseInsensitive = new JCheckBox("Aa", true);
        JCheckBox includeMissing = new JCheckBox("missing", false);
        JButton apply = new JButton("Apply");
        apply.addActionListener(event -> setTextFilter(
                column.id(),
                pattern.getText(),
                (TextPatternMode) mode.getSelectedItem(),
                caseInsensitive.isSelected(),
                includeMissing.isSelected()
        ));
        panel.add(pattern);
        panel.add(mode);
        panel.add(caseInsensitive);
        panel.add(includeMissing);
        panel.add(apply);
        return panel;
    }

    private JPanel categoryEditor(PrismColumn column) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        List<String> values = categoryValues(column);
        JList<String> list = new JList<>(values.toArray(String[]::new));
        list.setVisibleRowCount(Math.min(6, Math.max(3, values.size())));
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JCheckBox includeMissing = new JCheckBox("include missing", false);
        JButton apply = new JButton("Apply");
        apply.addActionListener(event -> setCategoryFilter(column.id(), new LinkedHashSet<>(list.getSelectedValuesList()), includeMissing.isSelected()));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        controls.add(includeMissing);
        controls.add(apply);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    private boolean shouldShowCategoryEditor(PrismColumn column) {
        if (!column.filterCapabilities().contains(FilterCapability.CATEGORY_INCLUDE)) {
            return false;
        }
        return categoryValues(column).size() <= categoryThreshold;
    }

    private List<String> categoryValues(PrismColumn column) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (int row = 0; row < column.rowCount(); row++) {
            if (!column.isMissing(row)) {
                values.add(column.formattedValueAt(row));
                if (values.size() > categoryThreshold) {
                    break;
                }
            }
        }
        return new ArrayList<>(values);
    }

    private void applyFilters() {
        session.setFilters(List.copyOf(filtersByKey.values()));
        refresh.run();
    }

    private static String key(String type, String columnId) {
        return type + ":" + columnId;
    }

    private static Double parseNullableDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Double.valueOf(value.trim());
    }
}

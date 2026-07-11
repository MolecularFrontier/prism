package tech.molecules.structurized.prismlite.swing.workspace.inspector;

import tech.molecules.structurized.prism.engine.CategoryIncludeFilter;
import tech.molecules.structurized.prism.engine.MissingValueFilter;
import tech.molecules.structurized.prism.engine.MissingValueMode;
import tech.molecules.structurized.prism.engine.NumericRangeFilter;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnSchema;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismFilter;
import tech.molecules.structurized.prism.engine.TextPatternFilter;
import tech.molecules.structurized.prism.engine.TextPatternMode;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceController;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;
import tech.molecules.structurized.prismlite.swing.workspace.analysis.CategoricalColumnSummary;
import tech.molecules.structurized.prismlite.swing.workspace.analysis.CategoryFrequency;
import tech.molecules.structurized.prismlite.swing.workspace.analysis.ColumnSummary;
import tech.molecules.structurized.prismlite.swing.workspace.analysis.ColumnSummaryService;
import tech.molecules.structurized.prismlite.swing.workspace.analysis.HistogramStrip;
import tech.molecules.structurized.prismlite.swing.workspace.analysis.NumericColumnSummary;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ColumnInspectorPanel extends JPanel {
    private final PrismLiteWorkspaceModel model;
    private final PrismLiteWorkspaceController controller;
    private final ColumnSummaryService summaries;
    private final Runnable refreshWorkspace;

    public ColumnInspectorPanel(PrismLiteWorkspaceModel model,
                                PrismLiteWorkspaceController controller,
                                ColumnSummaryService summaries,
                                Runnable refreshWorkspace) {
        super(new BorderLayout());
        this.model = Objects.requireNonNull(model, "model");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.summaries = Objects.requireNonNull(summaries, "summaries");
        this.refreshWorkspace = refreshWorkspace == null ? () -> { } : refreshWorkspace;
        refresh();
    }

    public void refresh() {
        removeAll();
        String columnId = model.focusedColumnId();
        if (columnId == null) {
            add(new JLabel("No column selected"), BorderLayout.NORTH);
            return;
        }
        PrismColumn column = model.table().column(columnId);
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(header(column));
        content.add(filterSection(column));
        content.add(summarySection(column));
        content.add(displaySection(column));
        content.add(metadataSection(column));
        add(new JScrollPane(content), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private JComponent header(PrismColumn column) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        String unit = column.schema().unit() == null ? "" : " - " + column.schema().unit();
        JLabel title = new JLabel("<html><b>" + escape(column.schema().displayName()) + "</b><br>"
                + column.type() + unit + "</html>");
        panel.add(title, BorderLayout.CENTER);
        return panel;
    }

    private JComponent filterSection(PrismColumn column) {
        JPanel panel = section("Filter");
        if (column.type() == PrismColumnType.NUMERIC || column.type() == PrismColumnType.INTEGER) {
            numericFilter(column, panel);
        } else if (column.type() == PrismColumnType.CATEGORICAL || column.type() == PrismColumnType.BOOLEAN) {
            categoricalFilter(column, panel);
        } else {
            textFilter(column, panel);
        }
        return panel;
    }

    private void numericFilter(PrismColumn column, JPanel panel) {
        PrismFilter current = model.draftFilter(column.id());
        Double currentMin = current instanceof NumericRangeFilter numeric ? numeric.min() : null;
        Double currentMax = current instanceof NumericRangeFilter numeric ? numeric.max() : null;
        boolean includeMissing = !(current instanceof NumericRangeFilter numeric) || numeric.includeMissing();
        JTextField min = new JTextField(currentMin == null ? "" : currentMin.toString(), 7);
        JTextField max = new JTextField(currentMax == null ? "" : currentMax.toString(), 7);
        JCheckBox missing = new JCheckBox("missing", includeMissing);
        JLabel state = dirtyLabel(column.id());
        HistogramStrip histogram = new HistogramStrip();
        histogram.setLoading(true);
        histogram.setDraftRange(currentMin, currentMax);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.add(new JLabel("min"));
        row.add(min);
        row.add(new JLabel("max"));
        row.add(max);
        row.add(missing);
        row.add(state);
        panel.add(row);
        panel.add(histogram);
        JPanel buttons = buttons(column.id());
        panel.add(buttons);
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                update();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                update();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                update();
            }

            private void update() {
                try {
                    Double minValue = parseDouble(min.getText());
                    Double maxValue = parseDouble(max.getText());
                    PrismFilter filter = numericDraft(column.id(), minValue, maxValue, missing.isSelected());
                    model.setDraftFilter(column.id(), filter);
                    histogram.setDraftRange(minValue, maxValue);
                } catch (NumberFormatException ignored) {
                    state.setText("Invalid number");
                }
            }
        };
        min.getDocument().addDocumentListener(listener);
        max.getDocument().addDocumentListener(listener);
        missing.addActionListener(event -> {
            try {
                model.setDraftFilter(column.id(), numericDraft(column.id(), parseDouble(min.getText()), parseDouble(max.getText()), missing.isSelected()));
            } catch (NumberFormatException ignored) {
                state.setText("Invalid number");
            }
        });
        summaries.summary(column.id()).whenComplete((summary, failure) -> SwingUtilities.invokeLater(() -> {
            if (!Objects.equals(model.focusedColumnId(), column.id())) {
                return;
            }
            histogram.setLoading(false);
            if (summary instanceof NumericColumnSummary numeric) {
                histogram.setHistogram(numeric.histogram());
            }
        }));
    }

    private void categoricalFilter(PrismColumn column, JPanel panel) {
        JLabel loading = new JLabel("Loading values...");
        panel.add(loading);
        JCheckBox missing = new JCheckBox("include missing", false);
        panel.add(missing);
        panel.add(buttons(column.id()));
        summaries.summary(column.id()).whenComplete((summary, failure) -> SwingUtilities.invokeLater(() -> {
            if (!Objects.equals(model.focusedColumnId(), column.id())) {
                return;
            }
            panel.remove(loading);
            if (failure != null || !(summary instanceof CategoricalColumnSummary categorical)) {
                panel.add(new JLabel("Summary failed"), 0);
            } else {
                JList<String> values = new JList<>(categorical.topValues().stream().map(this::formatCategory).toArray(String[]::new));
                values.setVisibleRowCount(Math.min(10, Math.max(3, categorical.topValues().size())));
                values.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                values.addListSelectionListener(event -> {
                    if (!event.getValueIsAdjusting()) {
                        Set<String> selected = new LinkedHashSet<>();
                        for (String value : values.getSelectedValuesList()) {
                            selected.add(stripCount(value));
                        }
                        model.setDraftFilter(column.id(), categoryDraft(column.id(), selected, missing.isSelected()));
                    }
                });
                panel.add(new JScrollPane(values), 0);
            }
            panel.revalidate();
            panel.repaint();
        }));
        missing.addActionListener(event -> model.setDraftFilter(column.id(), categoryDraft(column.id(), Set.of(), missing.isSelected())));
    }

    private void textFilter(PrismColumn column, JPanel panel) {
        PrismFilter current = model.draftFilter(column.id());
        TextPatternFilter active = current instanceof TextPatternFilter text ? text : null;
        JTextField pattern = new JTextField(active == null ? "" : active.patternText(), 14);
        JComboBox<TextPatternMode> mode = new JComboBox<>(TextPatternMode.values());
        if (active != null) {
            mode.setSelectedItem(active.mode());
        }
        JCheckBox caseInsensitive = new JCheckBox("case insensitive", active == null || active.caseInsensitive());
        JCheckBox missing = new JCheckBox("include missing", active != null && active.includeMissing());
        JLabel state = dirtyLabel(column.id());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.add(pattern);
        row.add(mode);
        row.add(caseInsensitive);
        row.add(missing);
        panel.add(row);
        panel.add(state);
        panel.add(buttons(column.id()));
        Runnable update = () -> {
            try {
                String value = pattern.getText();
                PrismFilter filter = value == null || value.isBlank()
                        ? null
                        : new TextPatternFilter(column.id(), value, (TextPatternMode) mode.getSelectedItem(), caseInsensitive.isSelected(), missing.isSelected());
                model.setDraftFilter(column.id(), filter);
                state.setText(model.isDirty(column.id()) ? "Unapplied changes" : "Applied");
            } catch (RuntimeException exception) {
                state.setText(exception.getMessage());
            }
        };
        pattern.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                update.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                update.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                update.run();
            }
        });
        mode.addActionListener(event -> update.run());
        caseInsensitive.addActionListener(event -> update.run());
        missing.addActionListener(event -> update.run());
    }

    private JComponent summarySection(PrismColumn column) {
        JPanel panel = section("Summary");
        JLabel label = new JLabel("Loading...");
        panel.add(label);
        summaries.summary(column.id()).whenComplete((summary, failure) -> SwingUtilities.invokeLater(() -> {
            if (!Objects.equals(model.focusedColumnId(), column.id())) {
                return;
            }
            if (failure != null) {
                label.setText("Failed: " + failure.getMessage());
            } else {
                label.setText(summaryText(summary));
            }
        }));
        return panel;
    }

    private JComponent displaySection(PrismColumn column) {
        JPanel panel = section("Display");
        JButton visible = new JButton(model.isVisible(column.id()) ? "Hide" : "Show");
        visible.addActionListener(event -> {
            model.setColumnVisible(column.id(), !model.isVisible(column.id()));
            refreshWorkspace.run();
        });
        JButton pin = new JButton(model.isPinned(column.id()) ? "Unpin" : "Pin");
        pin.addActionListener(event -> {
            model.setPinned(column.id(), !model.isPinned(column.id()));
            refreshWorkspace.run();
        });
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.add(visible);
        row.add(pin);
        panel.add(row);
        return panel;
    }

    private JComponent metadataSection(PrismColumn column) {
        JPanel panel = section("Metadata");
        PrismColumnSchema schema = column.schema();
        JTextArea text = new JTextArea(7, 24);
        text.setEditable(false);
        text.setText("id: " + schema.id()
                + "\nsemanticType: " + schema.semanticType()
                + "\nrole: " + schema.role()
                + "\nendpointId: " + schema.endpointId()
                + "\ndirection: " + schema.direction()
                + "\nstructureFormat: " + schema.structureFormat());
        panel.add(text);
        return panel;
    }

    private JPanel buttons(String columnId) {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton revert = new JButton("Revert");
        revert.addActionListener(event -> {
            model.discardDraft(columnId);
            refreshWorkspace.run();
        });
        JButton apply = new JButton("Apply");
        apply.addActionListener(event -> {
            model.applyDraft(columnId);
            refreshWorkspace.run();
        });
        buttons.add(revert);
        buttons.add(apply);
        return buttons;
    }

    private JLabel dirtyLabel(String columnId) {
        return new JLabel(model.isDirty(columnId) ? "Unapplied changes" : "Applied");
    }

    private JPanel section(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(Box.createVerticalStrut(2));
        return panel;
    }

    private static PrismFilter numericDraft(String columnId, Double min, Double max, boolean includeMissing) {
        if (min == null && max == null && includeMissing) {
            return null;
        }
        if (min == null && max == null) {
            return new MissingValueFilter(columnId, MissingValueMode.HAS_VALUE);
        }
        return new NumericRangeFilter(columnId, min, max, includeMissing);
    }

    private static PrismFilter categoryDraft(String columnId, Set<String> values, boolean includeMissing) {
        if ((values == null || values.isEmpty()) && includeMissing) {
            return null;
        }
        if (values == null || values.isEmpty()) {
            return new MissingValueFilter(columnId, MissingValueMode.MISSING);
        }
        return new CategoryIncludeFilter(columnId, values, includeMissing);
    }

    private static Double parseDouble(String value) {
        return value == null || value.isBlank() ? null : Double.valueOf(value.trim());
    }

    private static String summaryText(ColumnSummary summary) {
        if (summary instanceof NumericColumnSummary numeric) {
            return "valid " + numeric.validCount()
                    + ", missing " + numeric.missingCount()
                    + ", min " + trim(numeric.minimum())
                    + ", max " + trim(numeric.maximum())
                    + ", mean " + trim(numeric.mean())
                    + ", median " + trim(numeric.median());
        }
        if (summary instanceof CategoricalColumnSummary categorical) {
            return "valid " + categorical.validCount()
                    + ", missing " + categorical.missingCount()
                    + ", distinct " + categorical.distinctCount();
        }
        return "No summary";
    }

    private String formatCategory(CategoryFrequency frequency) {
        return frequency.value() + " (" + frequency.count() + ")";
    }

    private static String stripCount(String value) {
        int marker = value.lastIndexOf(" (");
        return marker < 0 ? value : value.substring(0, marker);
    }

    private static String trim(double value) {
        if (Double.isNaN(value)) {
            return "n/a";
        }
        if (Math.rint(value) == value) {
            return Long.toString((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.4g", value);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

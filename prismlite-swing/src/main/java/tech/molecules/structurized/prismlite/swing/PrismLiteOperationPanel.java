package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismOperationDescriptor;
import tech.molecules.structurized.prism.engine.PrismOperationParameter;
import tech.molecules.structurized.prism.engine.PrismOperationParameterType;
import tech.molecules.structurized.prism.engine.PrismSession;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class PrismLiteOperationPanel extends JPanel {
    private final PrismSession session;
    private final Runnable refresh;
    private final PrismLiteRowSetPanel rowSetPanel;
    private final JComboBox<PrismOperationDescriptor> operationSelector;
    private final JPanel parameterPanel = new JPanel(new GridBagLayout());
    private final JTextArea status = new JTextArea(3, 24);
    private final Map<String, Object> editors = new LinkedHashMap<>();

    public PrismLiteOperationPanel(PrismSession session, PrismLiteRowSetPanel rowSetPanel, Runnable refresh) {
        super(new BorderLayout(4, 4));
        this.session = Objects.requireNonNull(session, "session");
        this.rowSetPanel = Objects.requireNonNull(rowSetPanel, "rowSetPanel");
        this.refresh = refresh == null ? () -> { } : refresh;
        this.operationSelector = new JComboBox<>(session.operationRegistry().descriptors().toArray(PrismOperationDescriptor[]::new));
        operationSelector.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.name());
            if (isSelected) {
                label.setOpaque(true);
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        });
        operationSelector.addActionListener(event -> rebuildParameters());
        JButton run = new JButton("Run");
        run.addActionListener(event -> runSelectedOperation());

        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.add(operationSelector, BorderLayout.CENTER);
        top.add(run, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(parameterPanel), BorderLayout.CENTER);
        status.setEditable(false);
        add(status, BorderLayout.SOUTH);
        rebuildParameters();
    }

    public void reloadOperations() {
        operationSelector.removeAllItems();
        for (PrismOperationDescriptor descriptor : session.operationRegistry().descriptors()) {
            operationSelector.addItem(descriptor);
        }
        rebuildParameters();
    }

    private void rebuildParameters() {
        editors.clear();
        parameterPanel.removeAll();
        PrismOperationDescriptor descriptor = (PrismOperationDescriptor) operationSelector.getSelectedItem();
        if (descriptor == null) {
            parameterPanel.revalidate();
            parameterPanel.repaint();
            return;
        }
        int row = 0;
        for (PrismOperationParameter parameter : descriptor.parameters()) {
            GridBagConstraints labelConstraints = constraints(0, row);
            labelConstraints.weightx = 0.0;
            parameterPanel.add(new JLabel(parameter.name()), labelConstraints);
            Object editor = editorFor(parameter);
            editors.put(parameter.id(), editor);
            GridBagConstraints editorConstraints = constraints(1, row);
            editorConstraints.weightx = 1.0;
            editorConstraints.fill = GridBagConstraints.HORIZONTAL;
            parameterPanel.add((java.awt.Component) editor, editorConstraints);
            row++;
        }
        parameterPanel.revalidate();
        parameterPanel.repaint();
    }

    private Object editorFor(PrismOperationParameter parameter) {
        if (parameter.type() == PrismOperationParameterType.COLUMN) {
            JComboBox<String> columns = new JComboBox<>();
            String semanticType = String.valueOf(parameter.hints().getOrDefault("semanticType", ""));
            for (PrismColumn column : session.table().columns()) {
                if (semanticType.isBlank() || semanticType.equals(column.schema().semanticType())
                        || ("chemical_structure".equals(semanticType) && column.type() == PrismColumnType.MOLECULE)) {
                    columns.addItem(column.id());
                }
            }
            return columns;
        }
        if (parameter.type() == PrismOperationParameterType.ENUM) {
            return new JComboBox<>(parameter.allowedValues().toArray(String[]::new));
        }
        return new JTextField(16);
    }

    private void runSelectedOperation() {
        PrismOperationDescriptor descriptor = (PrismOperationDescriptor) operationSelector.getSelectedItem();
        if (descriptor == null) {
            return;
        }
        try {
            Map<String, Object> parameters = collectParameters();
            var result = session.runOperation(descriptor.id(), parameters);
            rowSetPanel.refreshRowSets();
            refresh.run();
            status.setText("Added " + result.addedRowSets().size() + " row set(s), "
                    + result.addedColumns().size() + " column(s).");
        } catch (RuntimeException exception) {
            status.setText(exception.getMessage());
        }
    }

    private Map<String, Object> collectParameters() {
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : editors.entrySet()) {
            Object editor = entry.getValue();
            if (editor instanceof JComboBox<?> comboBox) {
                parameters.put(entry.getKey(), comboBox.getSelectedItem());
            } else if (editor instanceof JTextField textField) {
                parameters.put(entry.getKey(), textField.getText());
            }
        }
        return parameters;
    }

    private static GridBagConstraints constraints(int x, int y) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.insets = new Insets(2, 2, 2, 2);
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }
}

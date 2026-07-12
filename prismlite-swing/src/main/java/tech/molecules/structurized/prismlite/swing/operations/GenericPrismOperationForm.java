package tech.molecules.structurized.prismlite.swing.operations;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismOperationDescriptor;
import tech.molecules.structurized.prism.engine.PrismOperationParameter;
import tech.molecules.structurized.prism.engine.PrismOperationParameterType;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;

import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class GenericPrismOperationForm implements PrismOperationForm {
    private final PrismSession session;
    private final PrismOperationDescriptor descriptor;
    private final JPanel panel = new JPanel(new GridBagLayout());
    private final Map<String, Object> editors = new LinkedHashMap<>();

    GenericPrismOperationForm(PrismSession session, PrismOperationDescriptor descriptor) {
        this.session = Objects.requireNonNull(session, "session");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        build();
    }

    @Override
    public JComponent component() {
        return panel;
    }

    @Override
    public Map<String, Object> collectParameters() {
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : editors.entrySet()) {
            Object editor = entry.getValue();
            if (editor instanceof JComboBox<?> comboBox) {
                parameters.put(entry.getKey(), comboBox.getSelectedItem());
            } else if (editor instanceof JList<?> list) {
                parameters.put(entry.getKey(), list.getSelectedValuesList());
            } else if (editor instanceof JTextField textField) {
                parameters.put(entry.getKey(), textField.getText());
            }
        }
        return parameters;
    }

    private void build() {
        int row = 0;
        for (PrismOperationParameter parameter : descriptor.parameters()) {
            GridBagConstraints labelConstraints = constraints(0, row);
            labelConstraints.weightx = 0.0;
            panel.add(new JLabel(parameter.name()), labelConstraints);
            JComponent editor = editorFor(parameter);
            editors.put(parameter.id(), editor instanceof JScrollPane scroll && scroll.getViewport().getView() instanceof JList<?> list ? list : editor);
            GridBagConstraints editorConstraints = constraints(1, row);
            editorConstraints.weightx = 1.0;
            editorConstraints.fill = GridBagConstraints.HORIZONTAL;
            panel.add(editor, editorConstraints);
            row++;
        }
    }

    private JComponent editorFor(PrismOperationParameter parameter) {
        if (parameter.type() == PrismOperationParameterType.COLUMN) {
            JComboBox<String> columns = new JComboBox<>();
            if (!parameter.required()) {
                columns.addItem("");
            }
            addColumns(columns, parameter);
            return columns;
        }
        if (parameter.type() == PrismOperationParameterType.COLUMN_LIST) {
            DefaultListModel<String> model = new DefaultListModel<>();
            for (PrismColumn column : session.table().columns()) {
                if (isCompatibleColumn(parameter, column)) {
                    model.addElement(column.id());
                }
            }
            JList<String> list = new JList<>(model);
            list.setVisibleRowCount(Math.min(6, Math.max(3, model.size())));
            return new JScrollPane(list);
        }
        if (parameter.type() == PrismOperationParameterType.ROW_SET) {
            JComboBox<String> rowSets = new JComboBox<>();
            if (!parameter.required()) {
                rowSets.addItem("");
            }
            for (PrismRowSet rowSet : session.rowSets()) {
                rowSets.addItem(rowSet.id());
            }
            return rowSets;
        }
        if (parameter.type() == PrismOperationParameterType.ENUM) {
            JComboBox<String> values = new JComboBox<>();
            if (!parameter.required()) {
                values.addItem("");
            }
            for (String allowed : parameter.allowedValues()) {
                values.addItem(allowed);
            }
            return values;
        }
        return new JTextField(16);
    }

    private void addColumns(JComboBox<String> columns, PrismOperationParameter parameter) {
        for (PrismColumn column : session.table().columns()) {
            if (isCompatibleColumn(parameter, column)) {
                columns.addItem(column.id());
            }
        }
    }

    private static boolean isCompatibleColumn(PrismOperationParameter parameter, PrismColumn column) {
        String semanticType = String.valueOf(parameter.hints().getOrDefault("semanticType", ""));
        return semanticType.isBlank()
                || semanticType.equals(column.schema().semanticType())
                || ("chemical_structure".equals(semanticType) && column.type() == PrismColumnType.MOLECULE);
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

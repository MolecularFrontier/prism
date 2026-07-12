package tech.molecules.structurized.prismlite.swing.operations;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismOperationDescriptor;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.ocl.OclCreateStructureGridViewOperation;

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

final class StructureGridOperationFormProvider implements PrismOperationFormProvider {
    @Override
    public boolean supports(PrismOperationDescriptor descriptor) {
        return OclCreateStructureGridViewOperation.ID.equals(descriptor.id());
    }

    @Override
    public PrismOperationForm createForm(PrismSession session, PrismOperationDescriptor descriptor, PrismOperationLaunchContext context) {
        return new StructureGridForm(session, context);
    }

    private static final class StructureGridForm implements PrismOperationForm {
        private enum RowSource {
            VISIBLE_ROWS("Visible rows"),
            ROW_SET("Existing row set"),
            SELECTION("Current selection");

            private final String label;

            RowSource(String label) {
                this.label = label;
            }

            @Override
            public String toString() {
                return label;
            }
        }

        private final PrismSession session;
        private final PrismOperationLaunchContext context;
        private final JPanel panel = new JPanel(new GridBagLayout());
        private final JComboBox<RowSource> rowSource = new JComboBox<>(RowSource.values());
        private final JComboBox<String> rowSet = new JComboBox<>();
        private final JComboBox<String> structureColumn = new JComboBox<>();
        private final JList<String> endpointColumns;
        private final JComboBox<String> sortColumn = new JComboBox<>();
        private final JComboBox<String> sortDirection = new JComboBox<>(new String[] {"ASCENDING", "DESCENDING"});
        private final JTextField maxCompounds = new JTextField("24", 6);
        private final JTextField columns = new JTextField("4", 6);
        private final JTextField title = new JTextField("Structure Grid", 18);

        StructureGridForm(PrismSession session, PrismOperationLaunchContext context) {
            this.session = session;
            this.context = context;
            DefaultListModel<String> endpointModel = new DefaultListModel<>();
            for (PrismColumn column : session.table().columns()) {
                if (column.type() == PrismColumnType.MOLECULE) {
                    structureColumn.addItem(column.id());
                } else {
                    endpointModel.addElement(column.id());
                }
                sortColumn.addItem(column.id());
            }
            sortColumn.insertItemAt("", 0);
            sortColumn.setSelectedIndex(0);
            rowSet.addItem("");
            for (PrismRowSet existing : session.rowSets()) {
                rowSet.addItem(existing.id());
            }
            endpointColumns = new JList<>(endpointModel);
            endpointColumns.setVisibleRowCount(Math.min(7, Math.max(4, endpointModel.size())));
            rowSource.addActionListener(event -> rowSet.setEnabled(rowSource.getSelectedItem() == RowSource.ROW_SET));
            rowSet.setEnabled(false);
            build();
        }

        @Override
        public JComponent component() {
            return panel;
        }

        @Override
        public Map<String, Object> collectParameters() {
            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("title", title.getText());
            parameters.put("structureColumn", structureColumn.getSelectedItem());
            parameters.put("endpointColumns", endpointColumns.getSelectedValuesList());
            parameters.put("sortColumn", sortColumn.getSelectedItem());
            parameters.put("sortDirection", sortDirection.getSelectedItem());
            parameters.put("maxCompounds", maxCompounds.getText());
            parameters.put("columns", columns.getText());

            RowSource source = (RowSource) rowSource.getSelectedItem();
            if (source == RowSource.ROW_SET) {
                parameters.put("rowSetId", rowSet.getSelectedItem());
            } else if (source == RowSource.SELECTION) {
                PrismRowSet selected = context.materializeSelectedRows(title.getText().isBlank() ? "Selected structures" : title.getText());
                parameters.put("rowSetId", selected.id());
            }
            return parameters;
        }

        private void build() {
            addRow(0, "Rows", rowSource);
            addRow(1, "Row set", rowSet);
            addRow(2, "Structure", structureColumn);
            addRow(3, "Endpoints", new JScrollPane(endpointColumns));
            addRow(4, "Sort by", sortColumn);
            addRow(5, "Direction", sortDirection);
            addRow(6, "Max", maxCompounds);
            addRow(7, "Columns", columns);
            addRow(8, "Title", title);
        }

        private void addRow(int row, String label, JComponent component) {
            GridBagConstraints labelConstraints = constraints(0, row);
            panel.add(new JLabel(label), labelConstraints);
            GridBagConstraints editorConstraints = constraints(1, row);
            editorConstraints.fill = GridBagConstraints.HORIZONTAL;
            editorConstraints.weightx = 1.0;
            panel.add(component, editorConstraints);
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
}

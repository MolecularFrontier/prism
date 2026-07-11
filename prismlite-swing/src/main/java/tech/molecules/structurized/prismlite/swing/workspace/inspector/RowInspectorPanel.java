package tech.molecules.structurized.prismlite.swing.workspace.inspector;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeRenderUtil;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeViewPanel;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RowInspectorPanel extends JPanel {
    private final PrismLiteWorkspaceModel model;
    private final List<RowInspectorSectionProvider> providers = new ArrayList<>();

    public RowInspectorPanel(PrismLiteWorkspaceModel model) {
        super(new BorderLayout());
        this.model = Objects.requireNonNull(model, "model");
        providers.add(new RowHeaderSectionProvider());
        providers.add(new RowMoleculeSectionProvider());
        providers.add(new RowValuesSectionProvider());
        providers.add(new RowSetsSectionProvider());
        providers.add(new RowMetadataSectionProvider());
        refresh();
    }

    public void registerProvider(RowInspectorSectionProvider provider) {
        providers.add(Objects.requireNonNull(provider, "provider"));
        refresh();
    }

    public void refresh() {
        removeAll();
        Integer physicalRow = model.focusedPhysicalRow();
        if (physicalRow == null) {
            add(new JLabel("No row selected"), BorderLayout.NORTH);
            revalidate();
            repaint();
            return;
        }
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        for (RowInspectorSectionProvider provider : providers) {
            if (!provider.supports(model, physicalRow)) {
                continue;
            }
            JPanel section = new JPanel(new BorderLayout(4, 4));
            section.setBorder(BorderFactory.createTitledBorder(provider.title()));
            section.add(provider.create(model, physicalRow), BorderLayout.CENTER);
            content.add(section);
        }
        add(new JScrollPane(content), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private static final class RowHeaderSectionProvider implements RowInspectorSectionProvider {
        @Override
        public boolean supports(PrismLiteWorkspaceModel workspace, int physicalRow) {
            return true;
        }

        @Override
        public String title() {
            return "Row";
        }

        @Override
        public JComponent create(PrismLiteWorkspaceModel workspace, int physicalRow) {
            String rowId = workspace.session().rowIdForPhysicalRow(physicalRow);
            int visible = workspace.focusedVisibleRow().orElse(-1);
            return new JLabel("<html><b>" + escape(rowId) + "</b><br>physical " + physicalRow
                    + (visible >= 0 ? ", visible " + visible : "") + "</html>");
        }
    }

    private static final class RowMoleculeSectionProvider implements RowInspectorSectionProvider {
        @Override
        public boolean supports(PrismLiteWorkspaceModel workspace, int physicalRow) {
            return moleculeColumn(workspace) != null;
        }

        @Override
        public String title() {
            return "Structure";
        }

        @Override
        public JComponent create(PrismLiteWorkspaceModel workspace, int physicalRow) {
            PrismColumn column = moleculeColumn(workspace);
            MoleculeViewPanel panel = new MoleculeViewPanel();
            panel.setMolecule(MoleculeRenderUtil.parse(column, physicalRow));
            return panel;
        }

        private static PrismColumn moleculeColumn(PrismLiteWorkspaceModel workspace) {
            for (PrismColumn column : workspace.table().columns()) {
                if (column.type() == PrismColumnType.MOLECULE) {
                    return column;
                }
            }
            return null;
        }
    }

    private static final class RowValuesSectionProvider implements RowInspectorSectionProvider {
        @Override
        public boolean supports(PrismLiteWorkspaceModel workspace, int physicalRow) {
            return true;
        }

        @Override
        public String title() {
            return "Values";
        }

        @Override
        public JComponent create(PrismLiteWorkspaceModel workspace, int physicalRow) {
            DefaultTableModel model = new DefaultTableModel(new Object[] {"Column", "Value"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            for (String columnId : workspace.session().viewState().visibleColumns()) {
                PrismColumn column = workspace.table().column(columnId);
                model.addRow(new Object[] {column.schema().displayName(), column.formattedValueAt(physicalRow)});
            }
            JTable table = new JTable(model);
            table.setAutoCreateRowSorter(false);
            return new JScrollPane(table);
        }
    }

    private static final class RowSetsSectionProvider implements RowInspectorSectionProvider {
        @Override
        public boolean supports(PrismLiteWorkspaceModel workspace, int physicalRow) {
            return !workspace.session().rowSets().isEmpty();
        }

        @Override
        public String title() {
            return "Row Sets";
        }

        @Override
        public JComponent create(PrismLiteWorkspaceModel workspace, int physicalRow) {
            String rowId = workspace.session().rowIdForPhysicalRow(physicalRow);
            StringBuilder text = new StringBuilder();
            for (PrismRowSet rowSet : workspace.session().rowSets()) {
                if (rowSet.rowIds().contains(rowId)) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(rowSet.name()).append(" (").append(rowSet.id()).append(')');
                }
            }
            return new JLabel(text.isEmpty() ? "No row-set membership" : "<html>" + escape(text.toString()).replace("\n", "<br>") + "</html>");
        }
    }

    private static final class RowMetadataSectionProvider implements RowInspectorSectionProvider {
        @Override
        public boolean supports(PrismLiteWorkspaceModel workspace, int physicalRow) {
            return true;
        }

        @Override
        public String title() {
            return "Metadata";
        }

        @Override
        public JComponent create(PrismLiteWorkspaceModel workspace, int physicalRow) {
            JTextArea text = new JTextArea(5, 24);
            text.setEditable(false);
            String flags = workspace.session().viewState().flagModel().flagNames().stream()
                    .filter(flag -> workspace.session().viewState().flagModel().isFlagged(flag, physicalRow))
                    .sorted()
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("none");
            text.setText("rowId: " + workspace.session().rowIdForPhysicalRow(physicalRow)
                    + "\nphysicalRow: " + physicalRow
                    + "\nselected: " + workspace.session().viewState().selectionModel().isSelected(physicalRow)
                    + "\nflags: " + flags);
            return text;
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

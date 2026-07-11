package tech.molecules.structurized.prismlite.swing.workspace.navigator;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceController;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Locale;
import java.util.Objects;

public final class ColumnNavigatorPanel extends JPanel {
    private final PrismLiteWorkspaceModel model;
    private final PrismLiteWorkspaceController controller;
    private final JTextField search = new JTextField();
    private final JComboBox<Mode> mode = new JComboBox<>(Mode.values());
    private final DefaultListModel<ColumnItem> listModel = new DefaultListModel<>();
    private final JList<ColumnItem> list = new JList<>(listModel);

    public ColumnNavigatorPanel(PrismLiteWorkspaceModel model, PrismLiteWorkspaceController controller) {
        super(new BorderLayout(4, 4));
        this.model = Objects.requireNonNull(model, "model");
        this.controller = Objects.requireNonNull(controller, "controller");
        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.add(search, BorderLayout.CENTER);
        top.add(mode, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                ColumnItem selected = list.getSelectedValue();
                if (selected != null) {
                    controller.focusColumn(selected.columnId());
                }
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refresh();
            }
        });
        mode.addActionListener(event -> refresh());
        refresh();
    }

    public void refresh() {
        String selected = list.getSelectedValue() == null ? model.focusedColumnId() : list.getSelectedValue().columnId();
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        Mode selectedMode = (Mode) mode.getSelectedItem();
        listModel.clear();
        for (PrismColumn column : model.table().columns()) {
            if (!matchesMode(column, selectedMode) || !matchesSearch(column, query)) {
                continue;
            }
            listModel.addElement(new ColumnItem(column.id(), ColumnGroupResolver.groupFor(model, column), column.schema().displayName()));
        }
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i).columnId().equals(selected)) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                break;
            }
        }
        revalidate();
        repaint();
    }

    private boolean matchesMode(PrismColumn column, Mode selectedMode) {
        return switch (selectedMode == null ? Mode.ALL : selectedMode) {
            case ALL -> true;
            case VISIBLE -> model.isVisible(column.id());
            case FILTERED -> model.hasAppliedColumnFilter(column.id());
            case COMPUTED -> model.isComputedColumn(column.id());
        };
    }

    private static boolean matchesSearch(PrismColumn column, String query) {
        if (query.isBlank()) {
            return true;
        }
        return column.id().toLowerCase(Locale.ROOT).contains(query)
                || column.schema().displayName().toLowerCase(Locale.ROOT).contains(query)
                || String.valueOf(column.schema().semanticType()).toLowerCase(Locale.ROOT).contains(query);
    }

    private enum Mode {
        ALL,
        VISIBLE,
        FILTERED,
        COMPUTED
    }

    private record ColumnItem(String columnId, String group, String name) {
    }

    private final class Renderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list,
                                                      Object value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ColumnItem item) {
                String markers = "";
                if (model.hasAppliedColumnFilter(item.columnId())) {
                    markers += " *";
                }
                if (model.isDirty(item.columnId())) {
                    markers += " draft";
                }
                if (model.isComputedColumn(item.columnId())) {
                    markers += " fx";
                }
                setText(item.group() + " / " + item.name() + markers);
            }
            return this;
        }
    }
}

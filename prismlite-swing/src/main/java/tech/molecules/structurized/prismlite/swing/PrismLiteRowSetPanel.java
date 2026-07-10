package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.Objects;

public final class PrismLiteRowSetPanel extends JPanel {
    private final PrismSession session;
    private final Runnable refresh;
    private final DefaultListModel<PrismRowSet> model = new DefaultListModel<>();
    private final JList<PrismRowSet> rowSetList = new JList<>(model);

    public PrismLiteRowSetPanel(PrismSession session, Runnable refresh) {
        super(new BorderLayout(4, 4));
        this.session = Objects.requireNonNull(session, "session");
        this.refresh = refresh == null ? () -> { } : refresh;
        rowSetList.setCellRenderer(new RowSetRenderer());
        add(new JScrollPane(rowSetList), BorderLayout.CENTER);

        JButton filter = new JButton("Filter");
        filter.addActionListener(event -> filterSelectedRowSet());
        JButton clear = new JButton("Clear");
        clear.addActionListener(event -> {
            session.clearFilters();
            refresh.run();
        });
        JButton reload = new JButton("Refresh");
        reload.addActionListener(event -> refreshRowSets());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.add(filter);
        buttons.add(clear);
        buttons.add(reload);
        add(buttons, BorderLayout.SOUTH);
        refreshRowSets();
    }

    public void refreshRowSets() {
        model.clear();
        for (PrismRowSet rowSet : session.rowSets()) {
            model.addElement(rowSet);
        }
    }

    public void selectRowSet(String rowSetId) {
        for (int index = 0; index < model.size(); index++) {
            if (model.get(index).id().equals(rowSetId)) {
                rowSetList.setSelectedIndex(index);
                return;
            }
        }
    }

    public void filterSelectedRowSet() {
        PrismRowSet rowSet = rowSetList.getSelectedValue();
        if (rowSet == null) {
            return;
        }
        session.filterToRowSet(rowSet.id());
        refresh.run();
    }

    private static final class RowSetRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list,
                                                      Object value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof PrismRowSet rowSet) {
                setText(rowSet.name() + " (" + rowSet.rowIds().size() + ")");
            }
            return this;
        }
    }
}

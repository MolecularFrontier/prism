package tech.molecules.structurized.prismlite.swing.workspace.table;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.SortDirection;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceController;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;

public final class PrismTableHeader extends JTableHeader {
    private final JTable table;
    private final PrismLiteWorkspaceModel model;
    private final PrismLiteWorkspaceController controller;
    private final Runnable refresh;

    public PrismTableHeader(TableColumnModel columnModel,
                            JTable table,
                            PrismLiteWorkspaceModel model,
                            PrismLiteWorkspaceController controller,
                            Runnable refresh) {
        super(columnModel);
        this.table = Objects.requireNonNull(table, "table");
        this.model = Objects.requireNonNull(model, "model");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.refresh = refresh == null ? () -> { } : refresh;
        setDefaultRenderer(new PrismColumnHeaderRenderer(model));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                handle(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                handle(event);
            }
        });
    }

    private void handle(MouseEvent event) {
        int viewColumn = table.columnAtPoint(event.getPoint());
        if (viewColumn < 0 || viewColumn >= model.session().visibleColumnCount()) {
            return;
        }
        String columnId = model.session().visibleColumnId(viewColumn);
        controller.focusColumn(columnId);
        if (event.isPopupTrigger() || SwingUtilities.isRightMouseButton(event)) {
            menuFor(model.session().visibleColumn(viewColumn)).show(this, event.getX(), event.getY());
        }
    }
    private JPopupMenu menuFor(PrismColumn column) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem details = new JMenuItem("Column details");
        details.addActionListener(event -> controller.focusColumn(column.id()));
        JMenuItem sortAsc = new JMenuItem("Sort ascending");
        sortAsc.addActionListener(event -> {
            controller.sortBy(column.id(), SortDirection.ASCENDING);
            refresh.run();
        });
        JMenuItem sortDesc = new JMenuItem("Sort descending");
        sortDesc.addActionListener(event -> {
            controller.sortBy(column.id(), SortDirection.DESCENDING);
            refresh.run();
        });
        JMenuItem hide = new JMenuItem("Hide");
        hide.addActionListener(event -> {
            model.setColumnVisible(column.id(), false);
            refresh.run();
        });
        JMenuItem pin = new JMenuItem(model.isPinned(column.id()) ? "Unpin" : "Pin");
        pin.addActionListener(event -> {
            model.setPinned(column.id(), !model.isPinned(column.id()));
            refresh.run();
        });
        JMenuItem restore = new JMenuItem("Restore default order");
        restore.addActionListener(event -> {
            model.restoreDefaultColumnOrder();
            refresh.run();
        });
        menu.add(details);
        menu.addSeparator();
        menu.add(sortAsc);
        menu.add(sortDesc);
        menu.addSeparator();
        menu.add(hide);
        menu.add(pin);
        menu.add(restore);
        return menu;
    }
}

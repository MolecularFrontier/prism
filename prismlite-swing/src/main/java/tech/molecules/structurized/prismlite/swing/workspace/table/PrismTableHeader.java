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
    private final Runnable refreshData;
    private final Runnable refreshStructure;
    private final Runnable refreshChrome;
    private boolean dragged;

    public PrismTableHeader(TableColumnModel columnModel,
                            JTable table,
                            PrismLiteWorkspaceModel model,
                            PrismLiteWorkspaceController controller,
                            Runnable refreshData,
                            Runnable refreshStructure,
                            Runnable refreshChrome) {
        super(columnModel);
        this.table = Objects.requireNonNull(table, "table");
        this.model = Objects.requireNonNull(model, "model");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.refreshData = refreshData == null ? () -> { } : refreshData;
        this.refreshStructure = refreshStructure == null ? () -> { } : refreshStructure;
        this.refreshChrome = refreshChrome == null ? () -> { } : refreshChrome;
        setDefaultRenderer(new PrismColumnHeaderRenderer(model));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                dragged = false;
                handlePopup(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                handlePopup(event);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (!dragged && !isResizing()) {
                    handleFocus(event);
                }
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                dragged = true;
            }
        });
    }

    private void handleFocus(MouseEvent event) {
        int viewColumn = table.columnAtPoint(event.getPoint());
        if (viewColumn < 0 || viewColumn >= model.session().visibleColumnCount()) {
            return;
        }
        controller.focusColumn(model.session().visibleColumnId(viewColumn));
    }

    private void handlePopup(MouseEvent event) {
        if (isResizing() || !(event.isPopupTrigger() || SwingUtilities.isRightMouseButton(event))) {
            return;
        }
        int viewColumn = table.columnAtPoint(event.getPoint());
        if (viewColumn < 0 || viewColumn >= model.session().visibleColumnCount()) {
            return;
        }
        String columnId = model.session().visibleColumnId(viewColumn);
        controller.focusColumn(columnId);
        menuFor(model.session().visibleColumn(viewColumn)).show(this, event.getX(), event.getY());
    }

    private boolean isResizing() {
        return getResizingColumn() != null;
    }
    private JPopupMenu menuFor(PrismColumn column) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem details = new JMenuItem("Column details");
        details.addActionListener(event -> controller.focusColumn(column.id()));
        JMenuItem sortAsc = new JMenuItem("Sort ascending");
        sortAsc.addActionListener(event -> {
            controller.sortBy(column.id(), SortDirection.ASCENDING);
            refreshData.run();
        });
        JMenuItem sortDesc = new JMenuItem("Sort descending");
        sortDesc.addActionListener(event -> {
            controller.sortBy(column.id(), SortDirection.DESCENDING);
            refreshData.run();
        });
        JMenuItem hide = new JMenuItem("Hide");
        hide.addActionListener(event -> {
            model.setColumnVisible(column.id(), false);
            refreshStructure.run();
        });
        JMenuItem pin = new JMenuItem(model.isPinned(column.id()) ? "Unpin" : "Pin");
        pin.addActionListener(event -> {
            model.setPinned(column.id(), !model.isPinned(column.id()));
            refreshChrome.run();
        });
        JMenuItem restore = new JMenuItem("Restore default order");
        restore.addActionListener(event -> {
            model.restoreDefaultColumnOrder();
            refreshStructure.run();
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

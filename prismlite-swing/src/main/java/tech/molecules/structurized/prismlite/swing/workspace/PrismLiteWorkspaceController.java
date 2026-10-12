package tech.molecules.structurized.prismlite.swing.workspace;

import tech.molecules.structurized.prism.engine.SortDirection;
import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prism.engine.SortKey;
import tech.molecules.structurized.prismlite.swing.PrismLiteTableModel;

import javax.swing.JTable;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class PrismLiteWorkspaceController {
    private final PrismLiteWorkspaceModel model;
    private final PrismLiteTableModel tableModel;
    private JTable table;
    private Consumer<PrismViewRecord> viewOpener;

    public PrismLiteWorkspaceController(PrismLiteWorkspaceModel model, PrismLiteTableModel tableModel) {
        this.model = Objects.requireNonNull(model, "model");
        this.tableModel = Objects.requireNonNull(tableModel, "tableModel");
    }

    public void attachTable(JTable table) {
        this.table = table;
    }

    public void attachViewOpener(Consumer<PrismViewRecord> viewOpener) {
        this.viewOpener = viewOpener;
    }

    public void addAndFocusView(PrismViewRecord view) {
        if (viewOpener == null) {
            model.session().addView(view);
        } else {
            viewOpener.accept(view);
        }
    }

    public void focusColumn(String columnId) {
        model.setFocusedColumn(columnId);
        scrollTableToColumn(columnId);
    }

    public void sortBy(String columnId, SortDirection direction) {
        model.session().setSortKeys(List.of(new SortKey(columnId, direction, null)));
        refreshData();
    }

    public void refreshData() {
        tableModel.refresh();
    }

    public void refreshStructure() {
        tableModel.refreshStructure();
    }

    public void scrollTableToColumn(String columnId) {
        if (table == null) {
            return;
        }
        int visibleIndex = model.session().viewState().visibleColumns().indexOf(columnId);
        if (visibleIndex < 0 || visibleIndex >= table.getColumnCount()) {
            return;
        }
        Rectangle rectangle = table.getCellRect(0, visibleIndex, true);
        table.scrollRectToVisible(rectangle);
    }
}

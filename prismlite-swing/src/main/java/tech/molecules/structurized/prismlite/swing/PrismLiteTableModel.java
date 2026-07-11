package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismSession;

import javax.swing.table.AbstractTableModel;
import java.util.Objects;

public final class PrismLiteTableModel extends AbstractTableModel {
    private final PrismSession session;

    public PrismLiteTableModel(PrismSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public PrismSession session() {
        return session;
    }

    @Override
    public int getRowCount() {
        return session.visibleRowCount();
    }

    @Override
    public int getColumnCount() {
        return session.visibleColumnCount();
    }

    @Override
    public String getColumnName(int column) {
        return session.visibleColumn(column).schema().displayName();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        PrismColumn column = session.visibleColumn(columnIndex);
        if (column.type() == PrismColumnType.NUMERIC || column.type() == PrismColumnType.INTEGER) {
            return Double.class;
        }
        if (column.type() == PrismColumnType.BOOLEAN) {
            return Boolean.class;
        }
        return String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return session.valueAtVisible(rowIndex, columnIndex);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    public void refresh() {
        fireTableDataChanged();
    }

    public void refreshStructure() {
        fireTableStructureChanged();
    }
}

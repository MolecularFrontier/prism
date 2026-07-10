package tech.molecules.structurized.prism.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

public final class RowIdIndex {
    private final String[] rowIds;
    private final Map<String, Integer> physicalRowsById;

    private RowIdIndex(String[] rowIds) {
        this.rowIds = rowIds.clone();
        LinkedHashMap<String, Integer> byId = new LinkedHashMap<>();
        for (int row = 0; row < rowIds.length; row++) {
            byId.put(rowIds[row], row);
        }
        this.physicalRowsById = Map.copyOf(byId);
    }

    public static RowIdIndex forTable(PrismTable table) {
        String candidate = findStableIdColumn(table);
        if (candidate != null) {
            String[] ids = idsFromColumn(table.column(candidate));
            if (ids != null) {
                return new RowIdIndex(ids);
            }
        }
        String[] fallback = new String[table.rowCount()];
        for (int row = 0; row < fallback.length; row++) {
            fallback[row] = "row:" + row;
        }
        return new RowIdIndex(fallback);
    }

    public int rowCount() {
        return rowIds.length;
    }

    public String rowId(int physicalRow) {
        return rowIds[physicalRow];
    }

    public OptionalInt physicalRow(String rowId) {
        Integer row = physicalRowsById.get(rowId);
        return row == null ? OptionalInt.empty() : OptionalInt.of(row);
    }

    private static String findStableIdColumn(PrismTable table) {
        for (PrismColumn column : table.columns()) {
            PrismColumnSchema schema = column.schema();
            if ("identifier".equals(schema.role()) || "compound_id".equals(schema.semanticType())) {
                return column.id();
            }
        }
        for (String id : new String[]{"compound_id", "CompoundId", "id"}) {
            if (table.findColumn(id).isPresent()) {
                return id;
            }
        }
        return null;
    }

    private static String[] idsFromColumn(PrismColumn column) {
        LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
        String[] ids = new String[column.rowCount()];
        for (int row = 0; row < column.rowCount(); row++) {
            if (column.isMissing(row)) {
                return null;
            }
            String id = column.formattedValueAt(row);
            if (id == null || id.isBlank() || seen.put(id, Boolean.TRUE) != null) {
                return null;
            }
            ids[row] = id;
        }
        return ids;
    }
}

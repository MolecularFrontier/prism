package tech.molecules.structurized.prismlite.swing.workspace.chem;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismTable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StructureCoordinateResolver {
    private static final List<String> EXPLICIT_KEYS = List.of(
            "coordinatesColumn",
            "coordinateColumn",
            "coordinatesColumnId",
            "coordinateColumnId",
            "idcodeCoordinatesColumn",
            "idcodeCoordinatesColumnId",
            "structureCoordinatesColumn",
            "structureCoordinatesColumnId"
    );
    private static final List<String> SUFFIXES = List.of(
            "_coords",
            "_coordinates",
            "_2d",
            "_2d_coordinates",
            "_2dcoords",
            "_idcoordinates",
            "_idcode_coordinates"
    );

    private StructureCoordinateResolver() {
    }

    public static String coordinateColumnId(PrismTable table, PrismColumn structureColumn) {
        if (table == null || structureColumn == null) {
            return null;
        }
        String explicit = explicitCoordinateColumnId(table, structureColumn.schema().raw());
        if (explicit != null) {
            return explicit;
        }
        String bySemantic = semanticCoordinateColumnId(table, structureColumn.id());
        if (bySemantic != null) {
            return bySemantic;
        }
        return heuristicCoordinateColumnId(table, structureColumn.id());
    }

    public static String coordinateValue(PrismTable table, PrismColumn structureColumn, int physicalRow) {
        String columnId = coordinateColumnId(table, structureColumn);
        if (columnId == null) {
            return null;
        }
        PrismColumn coordinates = table.column(columnId);
        if (physicalRow < 0 || physicalRow >= coordinates.rowCount() || coordinates.isMissing(physicalRow)) {
            return null;
        }
        String value = coordinates.formattedValueAt(physicalRow);
        return value == null || value.isBlank() ? null : value;
    }

    private static String explicitCoordinateColumnId(PrismTable table, Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        for (String key : EXPLICIT_KEYS) {
            String value = stringValue(raw.get(key));
            if (value != null && hasColumn(table, value)) {
                return value;
            }
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = normalize(entry.getKey());
            if (key.contains("coordinate") || key.contains("coords")) {
                String value = stringValue(entry.getValue());
                if (value != null && hasColumn(table, value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String semanticCoordinateColumnId(PrismTable table, String structureColumnId) {
        for (PrismColumn column : table.columns()) {
            if (column.id().equals(structureColumnId) || column.type() == PrismColumnType.MOLECULE) {
                continue;
            }
            String semantic = normalize(column.schema().semanticType());
            String role = normalize(column.schema().role());
            if ((semantic.contains("chemical") || semantic.contains("structure"))
                    && (semantic.contains("coordinate") || semantic.contains("coords") || role.contains("coordinate") || role.contains("coords"))) {
                return column.id();
            }
        }
        return null;
    }

    private static String heuristicCoordinateColumnId(PrismTable table, String structureColumnId) {
        String normalizedStructure = normalize(structureColumnId);
        for (String suffix : SUFFIXES) {
            String candidate = structureColumnId + suffix;
            if (hasColumn(table, candidate)) {
                return candidate;
            }
            candidate = structureColumnId + suffix.toUpperCase(Locale.ROOT);
            if (hasColumn(table, candidate)) {
                return candidate;
            }
        }
        for (PrismColumn column : table.columns()) {
            String normalized = normalize(column.id());
            if (normalized.equals(normalizedStructure + "coords")
                    || normalized.equals(normalizedStructure + "coordinates")
                    || normalized.equals(normalizedStructure + "2d")
                    || normalized.equals(normalizedStructure + "2dcoordinates")
                    || normalized.equals(normalizedStructure + "idcoordinates")) {
                return column.id();
            }
        }
        return null;
    }

    private static boolean hasColumn(PrismTable table, String columnId) {
        if (columnId == null || columnId.isBlank()) {
            return false;
        }
        try {
            table.column(columnId);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}

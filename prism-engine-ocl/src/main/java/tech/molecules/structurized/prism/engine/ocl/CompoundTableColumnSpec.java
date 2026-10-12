package tech.molecules.structurized.prism.engine.ocl;

public record CompoundTableColumnSpec(String columnId, String label, String format, String colorColumnId) {
    public CompoundTableColumnSpec(String columnId, String label, String format) {
        this(columnId, label, format, null);
    }

    public CompoundTableColumnSpec {
        if (columnId == null || columnId.isBlank()) {
            throw new IllegalArgumentException("column id must not be blank");
        }
        columnId = columnId.trim();
        label = label == null || label.isBlank() ? null : label.trim();
        format = format == null || format.isBlank() ? null : format.trim();
        colorColumnId = colorColumnId == null || colorColumnId.isBlank() ? null : colorColumnId.trim();
    }
}

package tech.molecules.structurized.prism.engine.ocl;

public record StructureGridValueSpec(
        String columnId,
        String label,
        String format,
        String colorColumnId
) {
    public StructureGridValueSpec(String columnId) {
        this(columnId, null, null, null);
    }

    public StructureGridValueSpec {
        if (columnId == null || columnId.isBlank()) {
            throw new IllegalArgumentException("structure-grid value column id must not be blank");
        }
        columnId = columnId.trim();
        label = label == null || label.isBlank() ? null : label.trim();
        format = format == null || format.isBlank() ? null : format.trim();
        colorColumnId = colorColumnId == null || colorColumnId.isBlank() ? null : colorColumnId.trim();
    }
}

package tech.molecules.structurized.prism.engine.ocl;

public record CompoundCardPropertySpec(
        String columnId,
        String label,
        String format,
        boolean showDelta,
        String colorColumnId
) {
    public CompoundCardPropertySpec {
        if (columnId == null || columnId.isBlank()) {
            throw new IllegalArgumentException("compound-card property column id must not be blank");
        }
        columnId = columnId.trim();
        label = label == null || label.isBlank() ? null : label.trim();
        format = format == null || format.isBlank() ? null : format.trim();
        colorColumnId = colorColumnId == null || colorColumnId.isBlank() ? null : colorColumnId.trim();
    }
}

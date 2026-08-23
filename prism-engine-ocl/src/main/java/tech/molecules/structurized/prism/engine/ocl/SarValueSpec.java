package tech.molecules.structurized.prism.engine.ocl;

public record SarValueSpec(
        String columnId,
        String label,
        String format,
        SarAggregation aggregation,
        String colorColumnId
) {
    public SarValueSpec {
        if (columnId == null || columnId.isBlank()) {
            throw new IllegalArgumentException("SAR value column id must not be blank");
        }
        columnId = columnId.trim();
        label = label == null || label.isBlank() ? null : label.trim();
        format = format == null || format.isBlank() ? null : format.trim();
        aggregation = aggregation == null ? SarAggregation.BEST : aggregation;
        colorColumnId = colorColumnId == null || colorColumnId.isBlank() ? null : colorColumnId.trim();
    }
}

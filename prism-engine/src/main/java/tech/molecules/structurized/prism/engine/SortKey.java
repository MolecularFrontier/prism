package tech.molecules.structurized.prism.engine;

public record SortKey(String columnId, SortDirection direction, MissingValueOrder missingValueOrder) {
    public SortKey {
        if (columnId == null || columnId.isBlank()) {
            throw new IllegalArgumentException("columnId must not be blank");
        }
        direction = direction == null ? SortDirection.ASCENDING : direction;
        missingValueOrder = missingValueOrder == null ? MissingValueOrder.LAST : missingValueOrder;
    }

    public static SortKey asc(String columnId) {
        return new SortKey(columnId, SortDirection.ASCENDING, MissingValueOrder.LAST);
    }

    public static SortKey desc(String columnId) {
        return new SortKey(columnId, SortDirection.DESCENDING, MissingValueOrder.LAST);
    }
}

package tech.molecules.structurized.prism.engine;

import java.util.Set;

public abstract class ColumnFilter implements PrismFilter {
    private final String columnId;

    protected ColumnFilter(String columnId) {
        if (columnId == null || columnId.isBlank()) {
            throw new IllegalArgumentException("columnId must not be blank");
        }
        this.columnId = columnId;
    }

    public final String columnId() {
        return columnId;
    }

    @Override
    public final Set<String> referencedColumnIds() {
        return Set.of(columnId);
    }
}

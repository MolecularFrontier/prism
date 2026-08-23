package tech.molecules.structurized.prism.engine.ocl;

import tech.molecules.structurized.prism.engine.PrismViewSpec;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record CompoundTableViewSpec(
        String viewId,
        String title,
        String rowSetId,
        String structureColumnId,
        List<CompoundTableColumnSpec> columns,
        boolean linkSelection,
        int maxRows
) implements PrismViewSpec {
    public static final String VIEW_TYPE = "chemistry.compound-table";
    public static final int DEFAULT_MAX_ROWS = 200;
    public static final int HARD_MAX_ROWS = 2_000;

    public CompoundTableViewSpec {
        if (viewId == null || viewId.isBlank()) throw new IllegalArgumentException("view id must not be blank");
        if (rowSetId == null || rowSetId.isBlank()) throw new IllegalArgumentException("row set id must not be blank");
        if (structureColumnId == null || structureColumnId.isBlank()) {
            throw new IllegalArgumentException("structure column id must not be blank");
        }
        viewId = viewId.trim();
        title = title == null || title.isBlank() ? "Compound Table" : title.trim();
        rowSetId = rowSetId.trim();
        structureColumnId = structureColumnId.trim();
        columns = columns == null ? List.of() : List.copyOf(columns);
        maxRows = maxRows < 1 ? DEFAULT_MAX_ROWS : Math.min(maxRows, HARD_MAX_ROWS);
    }

    @Override
    public String viewType() {
        return VIEW_TYPE;
    }

    @Override
    public Set<String> referencedRowSetIds() {
        return Set.of(rowSetId);
    }

    @Override
    public Set<String> referencedColumnIds() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(structureColumnId);
        columns.forEach(column -> result.add(column.columnId()));
        return Set.copyOf(result);
    }

    @Override
    public PrismViewSpec copyWithIdentity(String id, String newTitle) {
        return new CompoundTableViewSpec(id, newTitle, rowSetId, structureColumnId, columns,
                linkSelection, maxRows);
    }
}

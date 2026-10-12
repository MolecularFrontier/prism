package tech.molecules.structurized.prism.engine;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record ColumnSummaryViewSpec(
        String viewId,
        String title,
        String rowSetId,
        List<String> columnIds
) implements PrismViewSpec {
    public static final String VIEW_TYPE = "analysis.column-summary";

    public ColumnSummaryViewSpec {
        if (viewId == null || viewId.isBlank()) throw new IllegalArgumentException("view id must not be blank");
        if (rowSetId == null || rowSetId.isBlank()) throw new IllegalArgumentException("row set id must not be blank");
        viewId = viewId.trim();
        title = title == null || title.isBlank() ? "Column Summary" : title.trim();
        rowSetId = rowSetId.trim();
        columnIds = columnIds == null ? List.of() : columnIds.stream()
                .filter(id -> id != null && !id.isBlank()).map(String::trim).distinct().toList();
        if (columnIds.isEmpty()) throw new IllegalArgumentException("column summary requires at least one column");
    }

    @Override public String viewType() { return VIEW_TYPE; }
    @Override public Set<String> referencedRowSetIds() { return Set.of(rowSetId); }
    @Override public Set<String> referencedColumnIds() { return Set.copyOf(new LinkedHashSet<>(columnIds)); }
    @Override public PrismViewSpec copyWithIdentity(String id, String newTitle) {
        return new ColumnSummaryViewSpec(id, newTitle, rowSetId, columnIds);
    }
}

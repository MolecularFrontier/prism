package tech.molecules.structurized.prism.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record RowGraphNeighborhoodViewSpec(
        String viewId,
        String title,
        String graphId,
        String centerRowId,
        String structureColumnId,
        List<String> labelColumnIds,
        int maxNeighbors,
        boolean showEdgeLabels,
        RowGraphNeighborhoodEdgeMode edgeMode,
        RowGraphNeighborhoodLabelMode labelMode,
        RowGraphNeighborhoodLayoutMode layoutMode
) implements PrismViewSpec {
    public static final String VIEW_TYPE = "row_graph.neighborhood";

    public RowGraphNeighborhoodViewSpec(
            String viewId,
            String title,
            String graphId,
            String centerRowId,
            String structureColumnId,
            List<String> labelColumnIds,
            int maxNeighbors,
            boolean showEdgeLabels
    ) {
        this(
                viewId,
                title,
                graphId,
                centerRowId,
                structureColumnId,
                labelColumnIds,
                maxNeighbors,
                showEdgeLabels,
                RowGraphNeighborhoodEdgeMode.CENTER_ONLY,
                RowGraphNeighborhoodLabelMode.SELECTED_ONLY,
                RowGraphNeighborhoodLayoutMode.RADIAL_BY_DEGREE
        );
    }

    public RowGraphNeighborhoodViewSpec {
        if (viewId == null || viewId.isBlank()) {
            throw new IllegalArgumentException("view id must not be blank");
        }
        if (graphId == null || graphId.isBlank()) {
            throw new IllegalArgumentException("graph id must not be blank");
        }
        if (centerRowId == null || centerRowId.isBlank()) {
            throw new IllegalArgumentException("center row id must not be blank");
        }
        if (structureColumnId == null || structureColumnId.isBlank()) {
            throw new IllegalArgumentException("structure column id must not be blank");
        }
        viewId = viewId.trim();
        title = title == null || title.isBlank() ? "Graph Neighborhood" : title.trim();
        graphId = graphId.trim();
        centerRowId = centerRowId.trim();
        structureColumnId = structureColumnId.trim();
        labelColumnIds = labelColumnIds == null ? List.of() : labelColumnIds.stream()
                .filter(column -> column != null && !column.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        maxNeighbors = Math.max(1, Math.min(maxNeighbors < 1 ? 18 : maxNeighbors, 120));
        edgeMode = edgeMode == null ? RowGraphNeighborhoodEdgeMode.CENTER_ONLY : edgeMode;
        labelMode = labelMode == null ? RowGraphNeighborhoodLabelMode.SELECTED_ONLY : labelMode;
        layoutMode = layoutMode == null ? RowGraphNeighborhoodLayoutMode.RADIAL_BY_DEGREE : layoutMode;
    }

    @Override
    public String viewType() {
        return VIEW_TYPE;
    }

    @Override
    public Set<String> referencedRowSetIds() {
        return Set.of();
    }

    @Override
    public Set<String> referencedColumnIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(structureColumnId);
        ids.addAll(labelColumnIds);
        return Set.copyOf(new ArrayList<>(ids));
    }
}

package tech.molecules.structurized.prism.engine.ocl;

import tech.molecules.structurized.prism.engine.PrismViewSpec;
import tech.molecules.structurized.prism.engine.SortDirection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record StructureGridViewSpec(
        String viewId,
        String title,
        String rowSetId,
        String structureColumnId,
        List<String> endpointColumnIds,
        String sortColumnId,
        SortDirection sortDirection,
        int maxCompounds,
        int columns,
        List<StructureGridValueSpec> valueSpecifications
) implements PrismViewSpec {
    public static final String VIEW_TYPE = "chemistry.structure-grid";

    public StructureGridViewSpec {
        if (viewId == null || viewId.isBlank()) {
            throw new IllegalArgumentException("view id must not be blank");
        }
        if (structureColumnId == null || structureColumnId.isBlank()) {
            throw new IllegalArgumentException("structure column id must not be blank");
        }
        viewId = viewId.trim();
        title = title == null || title.isBlank() ? "Structure Grid" : title.trim();
        rowSetId = rowSetId == null || rowSetId.isBlank() ? null : rowSetId.trim();
        structureColumnId = structureColumnId.trim();
        List<String> normalizedEndpointIds = endpointColumnIds == null ? List.of() : endpointColumnIds.stream()
                .filter(column -> column != null && !column.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        endpointColumnIds = normalizedEndpointIds;
        valueSpecifications = valueSpecifications == null ? List.of() : valueSpecifications.stream()
                .filter(java.util.Objects::nonNull)
                .filter(value -> normalizedEndpointIds.contains(value.columnId()))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(StructureGridValueSpec::columnId, value -> value,
                                (first, ignored) -> first, java.util.LinkedHashMap::new),
                        values -> normalizedEndpointIds.stream()
                                .map(column -> values.getOrDefault(column, new StructureGridValueSpec(column)))
                                .toList()));
        if (valueSpecifications.isEmpty() && !normalizedEndpointIds.isEmpty()) {
            valueSpecifications = normalizedEndpointIds.stream().map(StructureGridValueSpec::new).toList();
        }
        sortColumnId = sortColumnId == null || sortColumnId.isBlank() ? null : sortColumnId.trim();
        sortDirection = sortDirection == null ? SortDirection.ASCENDING : sortDirection;
        maxCompounds = maxCompounds < 1 ? 24 : maxCompounds;
        columns = Math.max(1, Math.min(columns < 1 ? 4 : columns, 8));
    }

    public StructureGridViewSpec(
            String viewId,
            String title,
            String rowSetId,
            String structureColumnId,
            List<String> endpointColumnIds,
            String sortColumnId,
            SortDirection sortDirection,
            int maxCompounds,
            int columns
    ) {
        this(viewId, title, rowSetId, structureColumnId, endpointColumnIds, sortColumnId,
                sortDirection, maxCompounds, columns, List.of());
    }

    @Override
    public String viewType() {
        return VIEW_TYPE;
    }

    @Override
    public Set<String> referencedRowSetIds() {
        return rowSetId == null ? Set.of() : Set.of(rowSetId);
    }

    @Override
    public Set<String> referencedColumnIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(structureColumnId);
        for (StructureGridValueSpec value : valueSpecifications) {
            ids.add(value.columnId());
            if (value.colorColumnId() != null) ids.add(value.colorColumnId());
        }
        if (sortColumnId != null) {
            ids.add(sortColumnId);
        }
        return Set.copyOf(new ArrayList<>(ids));
    }

    @Override
    public PrismViewSpec copyWithIdentity(String id, String newTitle) {
        return new StructureGridViewSpec(id, newTitle, rowSetId, structureColumnId, endpointColumnIds,
                sortColumnId, sortDirection, maxCompounds, columns, valueSpecifications);
    }
}

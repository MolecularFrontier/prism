package tech.molecules.structurized.prism.engine.ocl;

import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismExecutionProfile;
import tech.molecules.structurized.prism.engine.PrismOperation;
import tech.molecules.structurized.prism.engine.PrismOperationDescriptor;
import tech.molecules.structurized.prism.engine.PrismOperationEffect;
import tech.molecules.structurized.prism.engine.PrismOperationException;
import tech.molecules.structurized.prism.engine.PrismOperationParameter;
import tech.molecules.structurized.prism.engine.PrismOperationParameterType;
import tech.molecules.structurized.prism.engine.PrismOperationResult;
import tech.molecules.structurized.prism.engine.PrismSessionSnapshot;
import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prism.engine.SortDirection;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class OclCreateStructureGridViewOperation implements PrismOperation {
    public static final String ID = "chemistry.create_structure_grid_view";

    private static final PrismOperationDescriptor DESCRIPTOR = new PrismOperationDescriptor(
            ID,
            "1",
            "Create structure grid view",
            "Create a row-linked structure grid view with selected endpoint columns.",
            List.of(
                    new PrismOperationParameter("viewId", PrismOperationParameterType.STRING, "View ID", "", false, List.of(), Map.of()),
                    new PrismOperationParameter("title", PrismOperationParameterType.STRING, "Title", "", false, List.of(), Map.of()),
                    PrismOperationParameter.optionalRowSet("rowSetId", "Row set"),
                    PrismOperationParameter.requiredColumn("structureColumn", "Structure column", "chemical_structure"),
                    PrismOperationParameter.optionalColumnList("endpointColumns", "Endpoint columns", null),
                    new PrismOperationParameter("sortColumn", PrismOperationParameterType.COLUMN, "Sort column", "", false, List.of(), Map.of()),
                    new PrismOperationParameter("sortDirection", PrismOperationParameterType.ENUM, "Sort direction", "", false, List.of("ASCENDING", "DESCENDING"), Map.of()),
                    new PrismOperationParameter("maxCompounds", PrismOperationParameterType.NUMBER, "Max compounds", "", false, List.of(), Map.of()),
                    new PrismOperationParameter("columns", PrismOperationParameterType.NUMBER, "Columns", "", false, List.of(), Map.of())
            ),
            Set.of(PrismOperationEffect.ADD_VIEWS),
            PrismExecutionProfile.INTERACTIVE
    );

    @Override
    public PrismOperationDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public PrismOperationResult execute(PrismSessionSnapshot snapshot, Map<String, Object> parameters) {
        String structureColumn = required(parameters, "structureColumn");
        if (snapshot.table().column(structureColumn).type() != PrismColumnType.MOLECULE) {
            throw new PrismOperationException("INCOMPATIBLE_COLUMN", "structure column must have MOLECULE type", "structureColumn");
        }

        List<String> endpointColumns = endpointColumns(parameters.get("endpointColumns"));
        for (String endpointColumn : endpointColumns) {
            snapshot.table().column(endpointColumn);
        }

        String sortColumn = optionalString(parameters, "sortColumn");
        if (sortColumn != null) {
            snapshot.table().column(sortColumn);
        }
        SortDirection sortDirection = enumValue(parameters, "sortDirection", SortDirection.ASCENDING, SortDirection.class);
        String title = optionalString(parameters, "title");
        String rowSetId = optionalString(parameters, "rowSetId");
        int maxCompounds = intValue(parameters, "maxCompounds", 24);
        int columns = intValue(parameters, "columns", 4);
        String viewId = optionalString(parameters, "viewId");
        if (viewId == null) {
            viewId = "structure-grid:" + slug(title == null ? (rowSetId == null ? structureColumn : rowSetId) : title);
        }

        StructureGridViewSpec spec = new StructureGridViewSpec(
                viewId,
                title == null ? "Structure Grid" : title,
                rowSetId,
                structureColumn,
                endpointColumns,
                sortColumn,
                sortDirection,
                maxCompounds,
                columns
        );

        return PrismOperationResult.builder()
                .addView(new PrismViewRecord(
                        spec.viewId(),
                        spec.viewType(),
                        spec.title(),
                        spec,
                        Instant.now(),
                        Map.of("operationId", ID)
                ))
                .provenance("operationId", ID)
                .build();
    }

    private static String required(Map<String, Object> parameters, String id) {
        Object value = parameters.get(id);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("missing required parameter '" + id + "'");
        }
        return String.valueOf(value).trim();
    }

    private static String optionalString(Map<String, Object> parameters, String id) {
        Object value = parameters.get(id);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private static List<String> endpointColumns(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(text -> !text.isBlank())
                    .distinct()
                    .toList();
        }
        return Arrays.stream(String.valueOf(value).split(","))
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .distinct()
                .toList();
    }

    private static int intValue(Map<String, Object> parameters, String id, int defaultValue) {
        Object value = parameters.get(id);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value).trim());
    }

    private static <E extends Enum<E>> E enumValue(Map<String, Object> parameters, String id, E defaultValue, Class<E> type) {
        Object value = parameters.get(id);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return Enum.valueOf(type, String.valueOf(value).trim().toUpperCase(Locale.ROOT));
    }

    private static String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isBlank() ? "structures" : slug;
    }
}

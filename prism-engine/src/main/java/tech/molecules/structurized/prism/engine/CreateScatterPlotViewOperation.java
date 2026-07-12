package tech.molecules.structurized.prism.engine;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CreateScatterPlotViewOperation implements PrismOperation {
    public static final String ID = "chart.create_scatter_plot_view";

    private static final PrismOperationDescriptor DESCRIPTOR = new PrismOperationDescriptor(
            ID,
            "1",
            "Create scatter plot view",
            "Create a filter-aware 2D scatter plot view from two numeric columns.",
            List.of(
                    new PrismOperationParameter("viewId", PrismOperationParameterType.STRING, "View ID", "", false, List.of(), Map.of()),
                    new PrismOperationParameter("title", PrismOperationParameterType.STRING, "Title", "", false, List.of(), Map.of()),
                    PrismOperationParameter.optionalRowSet("rowSetId", "Row set"),
                    PrismOperationParameter.requiredColumn("xColumnId", "X column", null),
                    PrismOperationParameter.requiredColumn("yColumnId", "Y column", null),
                    new PrismOperationParameter("colorColumnId", PrismOperationParameterType.COLUMN, "Color column", "", false, List.of(), Map.of()),
                    new PrismOperationParameter("xMin", PrismOperationParameterType.NUMBER, "X min", "", false, List.of(), Map.of()),
                    new PrismOperationParameter("xMax", PrismOperationParameterType.NUMBER, "X max", "", false, List.of(), Map.of()),
                    new PrismOperationParameter("yMin", PrismOperationParameterType.NUMBER, "Y min", "", false, List.of(), Map.of()),
                    new PrismOperationParameter("yMax", PrismOperationParameterType.NUMBER, "Y max", "", false, List.of(), Map.of())
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
        String xColumnId = required(parameters, "xColumnId");
        String yColumnId = required(parameters, "yColumnId");
        requireNumeric(snapshot.table().column(xColumnId), "xColumnId");
        requireNumeric(snapshot.table().column(yColumnId), "yColumnId");

        String colorColumnId = optionalString(parameters, "colorColumnId");
        if (colorColumnId != null) {
            snapshot.table().column(colorColumnId);
        }
        Double xMin = optionalNumber(parameters, "xMin");
        Double xMax = optionalNumber(parameters, "xMax");
        Double yMin = optionalNumber(parameters, "yMin");
        Double yMax = optionalNumber(parameters, "yMax");
        validateBounds("x", xMin, xMax);
        validateBounds("y", yMin, yMax);

        String title = optionalString(parameters, "title");
        String rowSetId = optionalString(parameters, "rowSetId");
        String viewId = optionalString(parameters, "viewId");
        if (viewId == null) {
            viewId = "scatter:" + slug(title == null ? xColumnId + "-vs-" + yColumnId : title);
        }

        ScatterPlotViewSpec spec = new ScatterPlotViewSpec(
                viewId,
                title == null ? "Scatter Plot" : title,
                rowSetId,
                xColumnId,
                yColumnId,
                colorColumnId,
                xMin,
                xMax,
                yMin,
                yMax
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

    private static void requireNumeric(PrismColumn column, String parameterName) {
        if (column.type() != PrismColumnType.NUMERIC && column.type() != PrismColumnType.INTEGER) {
            throw new PrismOperationException(
                    "INCOMPATIBLE_COLUMN",
                    "column '" + column.id() + "' must be numeric",
                    parameterName,
                    Map.of("columnId", column.id())
            );
        }
    }

    private static void validateBounds(String axis, Double min, Double max) {
        if (min != null && max != null && min >= max) {
            throw new PrismOperationException(
                    "INVALID_PARAMETER",
                    axis + " axis minimum must be smaller than maximum",
                    axis + "Min",
                    Map.of(axis + "Min", min, axis + "Max", max)
            );
        }
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

    private static Double optionalNumber(Map<String, Object> parameters, String id) {
        Object value = parameters.get(id);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.valueOf(String.valueOf(value).trim());
    }

    private static String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isBlank() ? "scatter" : slug;
    }
}

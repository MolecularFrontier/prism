package tech.molecules.structurized.prism.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public record ScatterPlotViewSpec(
        String viewId,
        String title,
        String rowSetId,
        String xColumnId,
        String yColumnId,
        String colorColumnId,
        Double xMin,
        Double xMax,
        Double yMin,
        Double yMax
) implements PrismViewSpec {
    public static final String VIEW_TYPE = "chart.scatter";

    public ScatterPlotViewSpec {
        if (viewId == null || viewId.isBlank()) {
            throw new IllegalArgumentException("view id must not be blank");
        }
        if (xColumnId == null || xColumnId.isBlank()) {
            throw new IllegalArgumentException("x column id must not be blank");
        }
        if (yColumnId == null || yColumnId.isBlank()) {
            throw new IllegalArgumentException("y column id must not be blank");
        }
        viewId = viewId.trim();
        title = title == null || title.isBlank() ? "Scatter Plot" : title.trim();
        rowSetId = rowSetId == null || rowSetId.isBlank() ? null : rowSetId.trim();
        xColumnId = xColumnId.trim();
        yColumnId = yColumnId.trim();
        colorColumnId = colorColumnId == null || colorColumnId.isBlank() ? null : colorColumnId.trim();
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
        ids.add(xColumnId);
        ids.add(yColumnId);
        if (colorColumnId != null) {
            ids.add(colorColumnId);
        }
        return Set.copyOf(new ArrayList<>(ids));
    }

    @Override
    public PrismViewSpec copyWithIdentity(String id, String newTitle) {
        return new ScatterPlotViewSpec(id, newTitle, rowSetId, xColumnId, yColumnId, colorColumnId,
                xMin, xMax, yMin, yMax);
    }
}

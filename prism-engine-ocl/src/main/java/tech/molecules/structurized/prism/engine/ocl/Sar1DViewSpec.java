package tech.molecules.structurized.prism.engine.ocl;

import tech.molecules.structurized.prism.engine.PrismViewSpec;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record Sar1DViewSpec(
        String viewId,
        String title,
        String rowSetId,
        String substituentColumnId,
        List<String> contextColumnIds,
        List<SarValueSpec> values,
        int maxGroups,
        boolean linkSelection
) implements PrismViewSpec {
    public static final String VIEW_TYPE = "chemistry.sar-1d";
    public static final int DEFAULT_MAX_GROUPS = 50;
    public static final int HARD_MAX_GROUPS = 200;

    public Sar1DViewSpec {
        viewId = required(viewId, "view id");
        title = title == null || title.isBlank() ? "1D SAR" : title.trim();
        rowSetId = required(rowSetId, "row set id");
        substituentColumnId = required(substituentColumnId, "substituent column id");
        contextColumnIds = normalized(contextColumnIds);
        values = values == null ? List.of() : List.copyOf(values);
        if (values.isEmpty() || values.size() > 4) throw new IllegalArgumentException("SAR view requires between 1 and 4 values");
        maxGroups = maxGroups < 1 ? DEFAULT_MAX_GROUPS : Math.min(maxGroups, HARD_MAX_GROUPS);
    }

    @Override public String viewType() { return VIEW_TYPE; }

    @Override public Set<String> referencedRowSetIds() { return Set.of(rowSetId); }

    @Override
    public Set<String> referencedColumnIds() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(substituentColumnId);
        result.addAll(contextColumnIds);
        for (SarValueSpec value : values) {
            result.add(value.columnId());
            if (value.colorColumnId() != null) result.add(value.colorColumnId());
        }
        return Set.copyOf(result);
    }

    @Override
    public PrismViewSpec copyWithIdentity(String id, String newTitle) {
        return new Sar1DViewSpec(id, newTitle, rowSetId, substituentColumnId, contextColumnIds, values,
                maxGroups, linkSelection);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static List<String> normalized(List<String> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).distinct().toList();
    }
}

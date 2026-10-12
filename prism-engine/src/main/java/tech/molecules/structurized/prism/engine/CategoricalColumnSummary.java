package tech.molecules.structurized.prism.engine;

import java.util.List;

public record CategoricalColumnSummary(
        long validCount,
        long missingCount,
        long distinctCount,
        List<CategoryFrequency> topValues
) implements ColumnSummary {
    public CategoricalColumnSummary {
        topValues = topValues == null ? List.of() : List.copyOf(topValues);
    }
}

package tech.molecules.structurized.prismlite.swing.workspace.analysis;

import java.util.List;

public record CategoricalColumnSummary(
        long validCount,
        long missingCount,
        long distinctCount,
        List<CategoryFrequency> topValues
) implements ColumnSummary {
}

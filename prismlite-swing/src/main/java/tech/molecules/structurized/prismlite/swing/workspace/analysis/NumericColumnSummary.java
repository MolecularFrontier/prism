package tech.molecules.structurized.prismlite.swing.workspace.analysis;

import java.util.List;

public record NumericColumnSummary(
        long validCount,
        long missingCount,
        double minimum,
        double maximum,
        double mean,
        double median,
        double standardDeviation,
        List<HistogramBin> histogram
) implements ColumnSummary {
}

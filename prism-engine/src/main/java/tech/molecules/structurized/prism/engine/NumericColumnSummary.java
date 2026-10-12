package tech.molecules.structurized.prism.engine;

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
    public NumericColumnSummary {
        histogram = histogram == null ? List.of() : List.copyOf(histogram);
    }
}

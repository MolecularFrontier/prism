package tech.molecules.structurized.prismlite.swing.workspace.analysis;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ColumnSummaries {
    public static final int DEFAULT_HISTOGRAM_BINS = 32;
    public static final int DEFAULT_TOP_CATEGORIES = 200;

    private ColumnSummaries() {
    }

    public static ColumnSummary compute(PrismColumn column) {
        if (column.type() == PrismColumnType.NUMERIC || column.type() == PrismColumnType.INTEGER) {
            return numeric(column);
        }
        return categorical(column);
    }

    private static NumericColumnSummary numeric(PrismColumn column) {
        ArrayList<Double> values = new ArrayList<>();
        long missing = 0;
        double sum = 0.0;
        for (int row = 0; row < column.rowCount(); row++) {
            if (column.isMissing(row)) {
                missing++;
            } else {
                double value = column.doubleValueAt(row);
                if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                    values.add(value);
                    sum += value;
                } else {
                    missing++;
                }
            }
        }
        if (values.isEmpty()) {
            return new NumericColumnSummary(0, missing, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, List.of());
        }
        values.sort(Comparator.naturalOrder());
        double min = values.getFirst();
        double max = values.getLast();
        double mean = sum / values.size();
        double median = median(values);
        double varianceSum = 0.0;
        for (double value : values) {
            double delta = value - mean;
            varianceSum += delta * delta;
        }
        double std = values.size() < 2 ? 0.0 : Math.sqrt(varianceSum / (values.size() - 1));
        return new NumericColumnSummary(values.size(), missing, min, max, mean, median, std, histogram(values, min, max));
    }

    private static CategoricalColumnSummary categorical(PrismColumn column) {
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        long missing = 0;
        long valid = 0;
        for (int row = 0; row < column.rowCount(); row++) {
            if (column.isMissing(row)) {
                missing++;
            } else {
                valid++;
                counts.merge(column.formattedValueAt(row), 1L, Long::sum);
            }
        }
        List<CategoryFrequency> top = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(DEFAULT_TOP_CATEGORIES)
                .map(entry -> new CategoryFrequency(entry.getKey(), entry.getValue()))
                .toList();
        return new CategoricalColumnSummary(valid, missing, counts.size(), top);
    }

    private static double median(List<Double> values) {
        int size = values.size();
        if (size % 2 == 1) {
            return values.get(size / 2);
        }
        return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
    }

    private static List<HistogramBin> histogram(List<Double> values, double min, double max) {
        if (values.isEmpty()) {
            return List.of();
        }
        if (Double.compare(min, max) == 0) {
            return List.of(new HistogramBin(min, max, values.size()));
        }
        long[] counts = new long[DEFAULT_HISTOGRAM_BINS];
        double width = (max - min) / DEFAULT_HISTOGRAM_BINS;
        for (double value : values) {
            int index = (int) ((value - min) / width);
            if (index >= DEFAULT_HISTOGRAM_BINS) {
                index = DEFAULT_HISTOGRAM_BINS - 1;
            }
            if (index < 0) {
                index = 0;
            }
            counts[index]++;
        }
        ArrayList<HistogramBin> bins = new ArrayList<>(DEFAULT_HISTOGRAM_BINS);
        for (int i = 0; i < counts.length; i++) {
            double left = min + i * width;
            double right = i == counts.length - 1 ? max : left + width;
            bins.add(new HistogramBin(left, right, counts[i]));
        }
        return List.copyOf(bins);
    }
}

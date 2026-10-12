package tech.molecules.structurized.prism.engine;

import java.util.ArrayList;
import java.util.BitSet;
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
        return compute(column, allRows(column.rowCount()));
    }

    public static ColumnSummary compute(PrismColumn column, BitSet physicalRows) {
        BitSet rows = physicalRows == null ? allRows(column.rowCount()) : bounded(physicalRows, column.rowCount());
        return column.type() == PrismColumnType.NUMERIC || column.type() == PrismColumnType.INTEGER
                ? numeric(column, rows)
                : categorical(column, rows);
    }

    private static NumericColumnSummary numeric(PrismColumn column, BitSet rows) {
        ArrayList<Double> values = new ArrayList<>();
        long missing = 0;
        double sum = 0.0;
        for (int row = rows.nextSetBit(0); row >= 0; row = rows.nextSetBit(row + 1)) {
            if (column.isMissing(row)) {
                missing++;
            } else {
                double value = column.doubleValueAt(row);
                if (Double.isFinite(value)) {
                    values.add(value);
                    sum += value;
                } else {
                    missing++;
                }
            }
        }
        if (values.isEmpty()) {
            return new NumericColumnSummary(0, missing, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, List.of());
        }
        values.sort(Comparator.naturalOrder());
        double min = values.getFirst();
        double max = values.getLast();
        double mean = sum / values.size();
        double varianceSum = 0.0;
        for (double value : values) {
            double delta = value - mean;
            varianceSum += delta * delta;
        }
        double standardDeviation = values.size() < 2 ? 0.0 : Math.sqrt(varianceSum / (values.size() - 1));
        return new NumericColumnSummary(values.size(), missing, min, max, mean, median(values),
                standardDeviation, histogram(values, min, max));
    }

    private static CategoricalColumnSummary categorical(PrismColumn column, BitSet rows) {
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        long missing = 0;
        long valid = 0;
        for (int row = rows.nextSetBit(0); row >= 0; row = rows.nextSetBit(row + 1)) {
            if (column.isMissing(row)) {
                missing++;
            } else {
                valid++;
                counts.merge(column.formattedValueAt(row), 1L, Long::sum);
            }
        }
        List<CategoryFrequency> top = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(entry -> String.valueOf(entry.getKey())))
                .limit(DEFAULT_TOP_CATEGORIES)
                .map(entry -> new CategoryFrequency(entry.getKey(), entry.getValue()))
                .toList();
        return new CategoricalColumnSummary(valid, missing, counts.size(), top);
    }

    private static BitSet allRows(int rowCount) {
        BitSet rows = new BitSet(rowCount);
        rows.set(0, rowCount);
        return rows;
    }

    private static BitSet bounded(BitSet source, int rowCount) {
        BitSet rows = (BitSet) source.clone();
        if (rows.length() > rowCount) rows.clear(rowCount, rows.length());
        return rows;
    }

    private static double median(List<Double> values) {
        int size = values.size();
        return size % 2 == 1 ? values.get(size / 2) : (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
    }

    private static List<HistogramBin> histogram(List<Double> values, double min, double max) {
        if (Double.compare(min, max) == 0) return List.of(new HistogramBin(min, max, values.size()));
        long[] counts = new long[DEFAULT_HISTOGRAM_BINS];
        double width = (max - min) / DEFAULT_HISTOGRAM_BINS;
        for (double value : values) {
            int index = Math.max(0, Math.min(DEFAULT_HISTOGRAM_BINS - 1, (int) ((value - min) / width)));
            counts[index]++;
        }
        ArrayList<HistogramBin> bins = new ArrayList<>(DEFAULT_HISTOGRAM_BINS);
        for (int index = 0; index < counts.length; index++) {
            double left = min + index * width;
            bins.add(new HistogramBin(left, index == counts.length - 1 ? max : left + width, counts[index]));
        }
        return List.copyOf(bins);
    }
}

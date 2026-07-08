package tech.molecules.structurized.prism.engine;

import java.util.BitSet;

public final class NumericRangeFilter extends ColumnFilter {
    private final Double min;
    private final Double max;
    private final boolean includeMissing;

    public NumericRangeFilter(String columnId, Double min, Double max, boolean includeMissing) {
        super(columnId);
        this.min = min;
        this.max = max;
        this.includeMissing = includeMissing;
    }

    public Double min() {
        return min;
    }

    public Double max() {
        return max;
    }

    public boolean includeMissing() {
        return includeMissing;
    }

    @Override
    public BitSet evaluate(PrismTable table, PrismEvaluationContext context) {
        PrismColumn column = table.column(columnId());
        BitSet result = new BitSet(table.rowCount());
        for (int row = 0; row < table.rowCount(); row++) {
            if (column.isMissing(row)) {
                if (includeMissing) {
                    result.set(row);
                }
                continue;
            }
            double value = column.doubleValueAt(row);
            if ((min == null || value >= min) && (max == null || value <= max)) {
                result.set(row);
            }
        }
        return result;
    }
}

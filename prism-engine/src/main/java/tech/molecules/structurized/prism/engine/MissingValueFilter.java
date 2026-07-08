package tech.molecules.structurized.prism.engine;

import java.util.BitSet;

public final class MissingValueFilter extends ColumnFilter {
    private final MissingValueMode mode;

    public MissingValueFilter(String columnId, MissingValueMode mode) {
        super(columnId);
        this.mode = mode == null ? MissingValueMode.HAS_VALUE : mode;
    }

    @Override
    public BitSet evaluate(PrismTable table, PrismEvaluationContext context) {
        PrismColumn column = table.column(columnId());
        BitSet result = new BitSet(table.rowCount());
        for (int row = 0; row < table.rowCount(); row++) {
            boolean missing = column.isMissing(row);
            if ((mode == MissingValueMode.MISSING && missing) || (mode == MissingValueMode.HAS_VALUE && !missing)) {
                result.set(row);
            }
        }
        return result;
    }
}

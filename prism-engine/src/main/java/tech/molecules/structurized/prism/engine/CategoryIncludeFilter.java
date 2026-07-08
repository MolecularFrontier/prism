package tech.molecules.structurized.prism.engine;

import java.util.BitSet;
import java.util.Set;

public final class CategoryIncludeFilter extends ColumnFilter {
    private final Set<String> includedValues;
    private final boolean includeMissing;

    public CategoryIncludeFilter(String columnId, Set<String> includedValues, boolean includeMissing) {
        super(columnId);
        this.includedValues = includedValues == null ? Set.of() : Set.copyOf(includedValues);
        this.includeMissing = includeMissing;
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
            if (includedValues.contains(column.formattedValueAt(row))) {
                result.set(row);
            }
        }
        return result;
    }
}

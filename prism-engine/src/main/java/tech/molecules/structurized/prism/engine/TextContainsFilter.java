package tech.molecules.structurized.prism.engine;

import java.util.BitSet;
import java.util.Locale;

public final class TextContainsFilter extends ColumnFilter {
    private final String needle;
    private final boolean caseInsensitive;
    private final boolean includeMissing;

    public TextContainsFilter(String columnId, String needle, boolean caseInsensitive, boolean includeMissing) {
        super(columnId);
        this.needle = needle == null ? "" : needle;
        this.caseInsensitive = caseInsensitive;
        this.includeMissing = includeMissing;
    }

    @Override
    public BitSet evaluate(PrismTable table, PrismEvaluationContext context) {
        PrismColumn column = table.column(columnId());
        String effectiveNeedle = normalize(needle);
        BitSet result = new BitSet(table.rowCount());
        for (int row = 0; row < table.rowCount(); row++) {
            if (column.isMissing(row)) {
                if (includeMissing) {
                    result.set(row);
                }
                continue;
            }
            String value = normalize(column.formattedValueAt(row));
            if (value.contains(effectiveNeedle)) {
                result.set(row);
            }
        }
        return result;
    }

    private String normalize(String value) {
        return caseInsensitive ? value.toLowerCase(Locale.ROOT) : value;
    }
}

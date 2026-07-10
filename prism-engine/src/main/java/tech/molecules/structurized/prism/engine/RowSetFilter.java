package tech.molecules.structurized.prism.engine;

import java.util.BitSet;
import java.util.Set;

public final class RowSetFilter implements PrismFilter {
    private final String rowSetId;
    private final Set<String> rowIds;

    public RowSetFilter(PrismRowSet rowSet) {
        this.rowSetId = rowSet.id();
        this.rowIds = rowSet.rowIds();
    }

    public String rowSetId() {
        return rowSetId;
    }

    @Override
    public BitSet evaluate(PrismTable table, PrismEvaluationContext context) {
        if (context.rowIdIndex() == null) {
            throw new IllegalStateException("row set filtering requires row IDs in the evaluation context");
        }
        BitSet result = new BitSet(table.rowCount());
        for (String rowId : rowIds) {
            context.rowIdIndex().physicalRow(rowId).ifPresent(result::set);
        }
        return result;
    }

    @Override
    public Set<String> referencedColumnIds() {
        return Set.of();
    }
}

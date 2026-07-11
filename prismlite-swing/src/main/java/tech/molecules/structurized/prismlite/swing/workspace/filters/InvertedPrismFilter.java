package tech.molecules.structurized.prismlite.swing.workspace.filters;

import tech.molecules.structurized.prism.engine.PrismEvaluationContext;
import tech.molecules.structurized.prism.engine.PrismFilter;
import tech.molecules.structurized.prism.engine.PrismTable;

import java.util.BitSet;
import java.util.Objects;
import java.util.Set;

public final class InvertedPrismFilter implements PrismFilter {
    private final PrismFilter delegate;

    public InvertedPrismFilter(PrismFilter delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public PrismFilter delegate() {
        return delegate;
    }

    @Override
    public BitSet evaluate(PrismTable table, PrismEvaluationContext context) {
        BitSet result = delegate.evaluate(table, context);
        result.flip(0, table.rowCount());
        return result;
    }

    @Override
    public Set<String> referencedColumnIds() {
        return delegate.referencedColumnIds();
    }
}

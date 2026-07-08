package tech.molecules.structurized.prism.engine;

import java.util.BitSet;
import java.util.Set;

public interface PrismFilter {
    BitSet evaluate(PrismTable table, PrismEvaluationContext context);

    Set<String> referencedColumnIds();
}

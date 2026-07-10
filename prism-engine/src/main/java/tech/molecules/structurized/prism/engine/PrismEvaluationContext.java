package tech.molecules.structurized.prism.engine;

public record PrismEvaluationContext(
        PrismViewState viewState,
        ComputedValueRegistry computedValues,
        RowIdIndex rowIdIndex
) {
    public PrismEvaluationContext(PrismViewState viewState) {
        this(viewState, null, null);
    }

    public PrismEvaluationContext(PrismViewState viewState, ComputedValueRegistry computedValues) {
        this(viewState, computedValues, null);
    }
}

package tech.molecules.structurized.prism.engine;

public record PrismEvaluationContext(PrismViewState viewState, ComputedValueRegistry computedValues) {
    public PrismEvaluationContext(PrismViewState viewState) {
        this(viewState, null);
    }
}

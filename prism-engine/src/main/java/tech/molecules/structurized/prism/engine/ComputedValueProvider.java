package tech.molecules.structurized.prism.engine;

public interface ComputedValueProvider<T> {
    T compute(PrismTable table, int physicalRow, ComputedValueContext context);
}

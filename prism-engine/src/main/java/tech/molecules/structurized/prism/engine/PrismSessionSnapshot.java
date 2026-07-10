package tech.molecules.structurized.prism.engine;

public record PrismSessionSnapshot(
        PrismTable table,
        ComputedValueRegistry computedValues,
        RowIdIndex rowIdIndex
) {
}

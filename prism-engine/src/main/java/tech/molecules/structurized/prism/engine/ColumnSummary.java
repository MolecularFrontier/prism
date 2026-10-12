package tech.molecules.structurized.prism.engine;

public sealed interface ColumnSummary permits NumericColumnSummary, CategoricalColumnSummary {
    long validCount();
    long missingCount();
}

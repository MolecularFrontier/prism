package tech.molecules.structurized.prism.engine.ocl;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SarProjectionModels {
    private SarProjectionModels() {
    }

    public record AggregatedValue(SarValueSpec specification, Double value, Double score, int valueCount) {}

    public record Sar1DRow(SarSubstituent substituent, List<AggregatedValue> values,
                           Set<String> contributingRowIds, int contextVariantCount) {
        public boolean mixedContext() { return contextVariantCount > 1; }
    }

    public record Sar1DModel(List<Sar1DRow> rows, int totalGroupCount, int excludedRowCount,
                             List<String> contextColumnIds) {
        public boolean truncated() { return rows.size() < totalGroupCount; }
    }

    public record CellKey(String rowIdentity, String columnIdentity) {}

    public record Sar2DCell(SarSubstituent rowSubstituent, SarSubstituent columnSubstituent,
                            List<AggregatedValue> values, Set<String> contributingRowIds,
                            int contextVariantCount) {
        public boolean mixedContext() { return contextVariantCount > 1; }
    }

    public record Sar2DModel(List<SarSubstituent> rowSubstituents,
                             List<SarSubstituent> columnSubstituents,
                             Map<CellKey, Sar2DCell> cells,
                             int totalRowGroupCount,
                             int totalColumnGroupCount,
                             int excludedRowCount,
                             List<String> contextColumnIds) {
        public boolean truncated() {
            return rowSubstituents.size() < totalRowGroupCount
                    || columnSubstituents.size() < totalColumnGroupCount;
        }
    }
}

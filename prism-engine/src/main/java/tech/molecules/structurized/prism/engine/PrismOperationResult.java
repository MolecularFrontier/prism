package tech.molecules.structurized.prism.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PrismOperationResult {
    private final List<MaterializedColumnData> addedColumns;
    private final List<RowIdMaterializedColumnData> addedColumnsByRowId;
    private final List<PrismRowSet> addedRowSets;
    private final List<String> warnings;
    private final Map<String, Object> provenance;

    private PrismOperationResult(Builder builder) {
        this.addedColumns = List.copyOf(builder.addedColumns);
        this.addedColumnsByRowId = List.copyOf(builder.addedColumnsByRowId);
        this.addedRowSets = List.copyOf(builder.addedRowSets);
        this.warnings = List.copyOf(builder.warnings);
        this.provenance = builder.provenance.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.provenance));
    }

    public List<MaterializedColumnData> addedColumns() {
        return addedColumns;
    }

    public List<RowIdMaterializedColumnData> addedColumnsByRowId() {
        return addedColumnsByRowId;
    }

    public List<PrismRowSet> addedRowSets() {
        return addedRowSets;
    }

    public List<String> warnings() {
        return warnings;
    }

    public Map<String, Object> provenance() {
        return provenance;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final ArrayList<MaterializedColumnData> addedColumns = new ArrayList<>();
        private final ArrayList<RowIdMaterializedColumnData> addedColumnsByRowId = new ArrayList<>();
        private final ArrayList<PrismRowSet> addedRowSets = new ArrayList<>();
        private final ArrayList<String> warnings = new ArrayList<>();
        private final LinkedHashMap<String, Object> provenance = new LinkedHashMap<>();

        public Builder addColumn(MaterializedColumnData column) {
            addedColumns.add(column);
            return this;
        }

        public Builder addColumnByRowId(RowIdMaterializedColumnData column) {
            addedColumnsByRowId.add(column);
            return this;
        }

        public Builder addRowSet(PrismRowSet rowSet) {
            addedRowSets.add(rowSet);
            return this;
        }

        public Builder addWarning(String warning) {
            if (warning != null && !warning.isBlank()) {
                warnings.add(warning);
            }
            return this;
        }

        public Builder provenance(String key, Object value) {
            if (key != null && !key.isBlank()) {
                provenance.put(key, value);
            }
            return this;
        }

        public PrismOperationResult build() {
            return new PrismOperationResult(this);
        }
    }
}

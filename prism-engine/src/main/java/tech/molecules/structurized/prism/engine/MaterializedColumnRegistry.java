package tech.molecules.structurized.prism.engine;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class MaterializedColumnRegistry {
    private final int rowCount;
    private final Map<String, MaterializedColumnData> columns = new LinkedHashMap<>();

    MaterializedColumnRegistry(int rowCount) {
        this.rowCount = rowCount;
    }

    Collection<MaterializedColumnData> columns() {
        return List.copyOf(columns.values());
    }

    Optional<MaterializedColumnData> find(String id) {
        return Optional.ofNullable(columns.get(id));
    }

    void add(MaterializedColumnData data) {
        Objects.requireNonNull(data, "data");
        if (columns.containsKey(data.schema().id())) {
            throw new IllegalArgumentException("materialized column already exists: " + data.schema().id());
        }
        if (data.values().size() != rowCount) {
            throw new IllegalArgumentException("materialized column '" + data.schema().id()
                    + "' has " + data.values().size() + " values for " + rowCount + " rows");
        }
        columns.put(data.schema().id(), data);
    }

    PrismColumn asColumn(String id) {
        MaterializedColumnData data = columns.get(id);
        if (data == null) {
            throw new IllegalArgumentException("unknown materialized column '" + id + "'");
        }
        return new MaterializedPrismColumn(data, rowCount);
    }
}

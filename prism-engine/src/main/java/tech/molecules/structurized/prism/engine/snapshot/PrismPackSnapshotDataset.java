package tech.molecules.structurized.prism.engine.snapshot;

import tech.molecules.structurized.prism.engine.InMemoryPrismTable;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismTable;
import tech.molecules.structurized.prism.engine.RowIdIndex;
import tech.molecules.structurized.prism.model.EndpointDataType;
import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.pack.PrismPack;
import tech.molecules.structurized.prism.result.BooleanResult;
import tech.molecules.structurized.prism.result.CategoricalResult;
import tech.molecules.structurized.prism.result.EndpointResult;
import tech.molecules.structurized.prism.result.NumericResult;
import tech.molecules.structurized.prism.result.NumericState;
import tech.molecules.structurized.prism.result.OptionalNumericResult;
import tech.molecules.structurized.prism.result.OptionalNumericState;
import tech.molecules.structurized.prism.result.TextResult;
import tech.molecules.structurized.prism.score.EndpointScoreDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PrismPackSnapshotDataset implements PrismSnapshotDataset {
    private final PrismPack pack;
    private final PrismTable table;
    private final RowIdIndex rowIdIndex;
    private final List<PrismSnapshotEndpoint> endpoints;
    private final Map<String, PrismSnapshotEndpoint> endpointsById;
    private final Map<String, EndpointResult> results;
    private final List<PrismRowSet> rowSets;
    private final Optional<PrismSnapshotOrigin> origin;
    private final boolean runtimeReloadable;
    private final PrismSnapshotCapabilities capabilities;

    public static PrismPackSnapshotDataset from(PrismPack pack) { return new PrismPackSnapshotDataset(pack, null, false); }

    public PrismPackSnapshotDataset(PrismPack pack, PrismSnapshotOrigin origin, boolean runtimeReloadable) {
        this.pack = pack;
        this.table = InMemoryPrismTable.from(pack);
        this.rowIdIndex = RowIdIndex.forTable(table);
        this.endpoints = endpointDescriptors(pack);
        LinkedHashMap<String, PrismSnapshotEndpoint> endpointIndex = new LinkedHashMap<>();
        endpoints.forEach(endpoint -> endpointIndex.put(endpoint.id(), endpoint));
        this.endpointsById = Map.copyOf(endpointIndex);
        this.results = resultIndex(pack);
        this.rowSets = rowSets(pack);
        this.origin = Optional.ofNullable(origin == null ? originFrom(pack) : origin);
        this.runtimeReloadable = runtimeReloadable;
        this.capabilities = computeCapabilities();
    }

    @Override public PrismTable table() { return table; }
    @Override public List<PrismSnapshotEndpoint> endpoints() { return endpoints; }
    @Override public List<PrismRowSet> rowSets() { return rowSets; }
    @Override public List<EndpointScoreDefinition> scoreDefinitions() { return pack.scores() == null ? List.of() : pack.scores().scores(); }
    @Override public PrismSnapshotCapabilities capabilities() { return capabilities; }
    @Override public Optional<PrismSnapshotOrigin> origin() { return origin; }

    @Override
    public Optional<PrismEndpointCell> endpointCell(String rowId, String endpointId) {
        PrismSnapshotEndpoint endpoint = endpointsById.get(endpointId);
        if (endpoint == null) return Optional.empty();
        var physical = rowIdIndex.physicalRow(rowId);
        if (physical.isEmpty()) return Optional.empty();
        String display = table.formattedValueAt(physical.getAsInt(), endpoint.columnId());
        String sidecarRowKey = sidecarRowKey(physical.getAsInt());
        EndpointResult full = results.get(key(sidecarRowKey, endpointId));
        if (full != null) return Optional.of(new PrismEndpointCell(rowId, endpointId, display, Optional.of(full), EndpointResultFidelity.FULL));
        EndpointResult synthesized = endpoint.definition().map(definition -> synthesize(definition, display)).orElse(null);
        return Optional.of(new PrismEndpointCell(rowId, endpointId, display, Optional.ofNullable(synthesized),
                synthesized == null ? EndpointResultFidelity.DISPLAY_ONLY : EndpointResultFidelity.SYNTHESIZED));
    }

    private PrismSnapshotCapabilities computeCapabilities() {
        int total = table.rowCount() * endpoints.size();
        int full = results.size();
        boolean definitions = !endpoints.isEmpty() && endpoints.stream().allMatch(endpoint -> endpoint.definition().isPresent());
        EndpointResultFidelity fidelity;
        if (endpoints.isEmpty()) fidelity = EndpointResultFidelity.NONE;
        else if (total > 0 && full == total) fidelity = EndpointResultFidelity.FULL;
        else if (full > 0) fidelity = EndpointResultFidelity.PARTIAL;
        else if (definitions) fidelity = EndpointResultFidelity.SYNTHESIZED;
        else fidelity = EndpointResultFidelity.DISPLAY_ONLY;
        boolean measurements = results.values().stream().anyMatch(result -> !result.getRawValueIds().isEmpty()
                || result instanceof NumericResult numeric && !numeric.getDatapoints().isEmpty()
                || result instanceof OptionalNumericResult numeric && !numeric.getDatapoints().isEmpty());
        return new PrismSnapshotCapabilities(fidelity, definitions, !rowSets.isEmpty(), !scoreDefinitions().isEmpty(), measurements, runtimeReloadable);
    }

    private String sidecarRowKey(int physicalRow) {
        if (pack.endpointResults() == null || pack.endpointResults().rowKeyColumn() == null) return rowIdIndex.rowId(physicalRow);
        return pack.dataFrame().valueAt(physicalRow, pack.endpointResults().rowKeyColumn());
    }

    private static List<PrismSnapshotEndpoint> endpointDescriptors(PrismPack pack) {
        if (pack.endpoints() == null) return List.of();
        return pack.endpoints().endpoints().stream().map(endpoint -> new PrismSnapshotEndpoint(endpoint.id(), endpoint.column(),
                endpoint.displayName(), Optional.ofNullable(endpoint.definition()), endpoint.raw())).toList();
    }

    private static Map<String, EndpointResult> resultIndex(PrismPack pack) {
        if (pack.endpointResults() == null) return Map.of();
        LinkedHashMap<String, EndpointResult> map = new LinkedHashMap<>();
        pack.endpointResults().results().forEach(result -> map.put(key(result.rowKey(), result.endpointId()), result.result()));
        return Map.copyOf(map);
    }

    private static List<PrismRowSet> rowSets(PrismPack pack) {
        if (pack.rowSets() == null) return List.of();
        return pack.rowSets().rowSets().stream().map(rowSet -> new PrismRowSet(rowSet.id(), rowSet.name(), rowSet.description(),
                new LinkedHashSet<>(rowSet.rowIds()), rowSet.provenance())).toList();
    }

    private static PrismSnapshotOrigin originFrom(PrismPack pack) {
        Object nested = pack.provenance().get("snapshotOrigin");
        Map<?, ?> map = nested instanceof Map<?, ?> candidate ? candidate : pack.provenance();
        if (map.isEmpty()) return null;
        return new PrismSnapshotOrigin(text(map.get("providerId")), text(map.get("repositoryId")), text(map.get("repositoryRevision")),
                text(map.get("viewId")), pack.manifest().createdAt(), pack.manifest().createdBy(), stringMap(map));
    }

    private static EndpointResult synthesize(EndpointDefinition definition, String display) {
        if (display == null || display.isBlank()) return null;
        try {
            return switch (definition.getDatatype()) {
                case NUMERIC -> NumericResult.builder().state(NumericState.VALUE).mean(Double.parseDouble(display)).build();
                case OPTIONAL_NUMERIC -> OptionalNumericResult.builder().state(OptionalNumericState.VALUE).mean(Double.parseDouble(display)).build();
                case BOOLEAN -> BooleanResult.builder().value(Boolean.parseBoolean(display)).build();
                case CATEGORICAL -> CategoricalResult.builder().value(display).build();
                case TEXT -> TextResult.builder().text(display).build();
            };
        } catch (RuntimeException ignored) { return null; }
    }

    private static String key(String rowId, String endpointId) { return rowId + "\u0000" + endpointId; }
    private static String text(Object value) { return value == null ? null : String.valueOf(value); }
    private static Map<String, Object> stringMap(Map<?, ?> raw) { LinkedHashMap<String, Object> map = new LinkedHashMap<>(); raw.forEach((key, value) -> map.put(String.valueOf(key), value)); return map; }
}

package tech.molecules.structurized.prism.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PrismRowGraph {
    private final String id;
    private final String title;
    private final String description;
    private final String graphType;
    private final String pluginId;
    private final int schemaVersion;
    private final boolean directed;
    private final String sourceRowSetId;
    private final List<PrismRowGraphEdge> edges;
    private final Map<String, Object> metadata;
    private final Map<String, Object> provenance;
    private final Set<String> rowIds;
    private final Map<String, List<PrismRowGraphEdge>> outgoingEdgesByRowId;
    private final Map<String, List<PrismRowGraphEdge>> incomingEdgesByRowId;
    private final Map<String, PrismRowGraphEdge> edgesById;

    public PrismRowGraph(String id,
                         String title,
                         String description,
                         String graphType,
                         String pluginId,
                         int schemaVersion,
                         boolean directed,
                         String sourceRowSetId,
                         List<PrismRowGraphEdge> edges,
                         Map<String, Object> metadata,
                         Map<String, Object> provenance) {
        this.id = requireText(id, "graph id");
        this.title = title == null || title.isBlank() ? this.id : title.trim();
        this.description = description == null ? "" : description.trim();
        this.graphType = graphType == null || graphType.isBlank() ? "generic.row_graph" : graphType.trim();
        this.pluginId = optionalText(pluginId);
        this.schemaVersion = schemaVersion <= 0 ? 1 : schemaVersion;
        this.directed = directed;
        this.sourceRowSetId = optionalText(sourceRowSetId);
        this.edges = edges == null ? List.of() : List.copyOf(edges);
        this.metadata = metadata == null || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.provenance = provenance == null || provenance.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(provenance));
        GraphIndex index = indexEdges(this.edges, this.directed);
        this.rowIds = index.rowIds();
        this.outgoingEdgesByRowId = index.outgoingEdgesByRowId();
        this.incomingEdgesByRowId = index.incomingEdgesByRowId();
        this.edgesById = index.edgesById();
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String graphType() {
        return graphType;
    }

    public String pluginId() {
        return pluginId;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public boolean directed() {
        return directed;
    }

    public String sourceRowSetId() {
        return sourceRowSetId;
    }

    public List<PrismRowGraphEdge> edges() {
        return edges;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Map<String, Object> provenance() {
        return provenance;
    }

    public Set<String> rowIds() {
        return rowIds;
    }

    public PrismRowGraphEdge edge(String edgeId) {
        PrismRowGraphEdge edge = edgesById.get(edgeId);
        if (edge == null) {
            throw new IllegalArgumentException("unknown edge '" + edgeId + "' in graph '" + id + "'");
        }
        return edge;
    }

    public List<PrismRowGraphEdge> outgoingEdges(String rowId) {
        return outgoingEdgesByRowId.getOrDefault(rowId, List.of());
    }

    public List<PrismRowGraphEdge> incomingEdges(String rowId) {
        return incomingEdgesByRowId.getOrDefault(rowId, List.of());
    }

    public List<PrismRowGraphEdge> incidentEdges(String rowId) {
        LinkedHashMap<String, PrismRowGraphEdge> incident = new LinkedHashMap<>();
        for (PrismRowGraphEdge edge : outgoingEdges(rowId)) {
            incident.put(edge.id(), edge);
        }
        for (PrismRowGraphEdge edge : incomingEdges(rowId)) {
            incident.put(edge.id(), edge);
        }
        return List.copyOf(incident.values());
    }

    public Set<String> neighborRowIds(String rowId) {
        LinkedHashSet<String> neighbors = new LinkedHashSet<>();
        for (PrismRowGraphEdge edge : outgoingEdges(rowId)) {
            neighbors.add(edge.targetRowId());
        }
        for (PrismRowGraphEdge edge : incomingEdges(rowId)) {
            neighbors.add(edge.sourceRowId());
        }
        neighbors.remove(rowId);
        return Collections.unmodifiableSet(neighbors);
    }

    public int degree(String rowId) {
        return incidentEdges(rowId).size();
    }

    private static GraphIndex indexEdges(List<PrismRowGraphEdge> edges, boolean directed) {
        LinkedHashSet<String> rowIds = new LinkedHashSet<>();
        LinkedHashMap<String, ArrayList<PrismRowGraphEdge>> outgoing = new LinkedHashMap<>();
        LinkedHashMap<String, ArrayList<PrismRowGraphEdge>> incoming = new LinkedHashMap<>();
        LinkedHashMap<String, PrismRowGraphEdge> edgesById = new LinkedHashMap<>();
        for (PrismRowGraphEdge edge : edges) {
            if (edgesById.putIfAbsent(edge.id(), edge) != null) {
                throw new IllegalArgumentException("duplicate graph edge id '" + edge.id() + "'");
            }
            rowIds.add(edge.sourceRowId());
            rowIds.add(edge.targetRowId());
            outgoing.computeIfAbsent(edge.sourceRowId(), ignored -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.targetRowId(), ignored -> new ArrayList<>()).add(edge);
            if (!directed) {
                outgoing.computeIfAbsent(edge.targetRowId(), ignored -> new ArrayList<>()).add(edge);
                incoming.computeIfAbsent(edge.sourceRowId(), ignored -> new ArrayList<>()).add(edge);
            }
        }
        LinkedHashMap<String, List<PrismRowGraphEdge>> immutableOutgoing = new LinkedHashMap<>();
        outgoing.forEach((rowId, rowEdges) -> immutableOutgoing.put(rowId, List.copyOf(rowEdges)));
        LinkedHashMap<String, List<PrismRowGraphEdge>> immutableIncoming = new LinkedHashMap<>();
        incoming.forEach((rowId, rowEdges) -> immutableIncoming.put(rowId, List.copyOf(rowEdges)));
        return new GraphIndex(
                Collections.unmodifiableSet(rowIds),
                Collections.unmodifiableMap(immutableOutgoing),
                Collections.unmodifiableMap(immutableIncoming),
                Collections.unmodifiableMap(edgesById)
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record GraphIndex(
            Set<String> rowIds,
            Map<String, List<PrismRowGraphEdge>> outgoingEdgesByRowId,
            Map<String, List<PrismRowGraphEdge>> incomingEdgesByRowId,
            Map<String, PrismRowGraphEdge> edgesById
    ) {
    }
}

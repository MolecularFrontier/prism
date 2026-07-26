package tech.molecules.structurized.prism.engine;

public record PrismRowGraphSummary(
        String id,
        String title,
        String graphType,
        String pluginId,
        int schemaVersion,
        boolean directed,
        String sourceRowSetId,
        int nodeCount,
        int edgeCount
) {
    public static PrismRowGraphSummary from(PrismRowGraph graph) {
        return new PrismRowGraphSummary(
                graph.id(),
                graph.title(),
                graph.graphType(),
                graph.pluginId(),
                graph.schemaVersion(),
                graph.directed(),
                graph.sourceRowSetId(),
                graph.rowIds().size(),
                graph.edges().size()
        );
    }
}

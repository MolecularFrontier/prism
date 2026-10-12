package tech.molecules.structurized.prism.engine;

import tech.molecules.structurized.prism.score.EndpointScoreDefinition;
import tech.molecules.structurized.prism.score.ScoreEvaluator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EndpointScoreMaterializer {
    private EndpointScoreMaterializer() {
    }

    public static PrismOperationResult define(PrismSessionSnapshot snapshot,
                                              EndpointScoreDefinition definition,
                                              String requestedColumnId) {
        if (snapshot == null) throw new IllegalArgumentException("session snapshot must not be null");
        if (definition == null) throw new IllegalArgumentException("score definition must not be null");
        EndpointScoreDefinition existingDefinition = snapshot.scoreDefinitions().get(definition.id());
        if (existingDefinition != null && !existingDefinition.fingerprint().equals(definition.fingerprint())) {
            throw new PrismOperationException("SCORE_EXISTS",
                    "score definition already exists with different semantics: " + definition.id());
        }
        PrismColumn source = resolveSourceColumn(snapshot.table(), definition.endpointId());
        if (source.type() != PrismColumnType.NUMERIC && source.type() != PrismColumnType.INTEGER) {
            throw new PrismOperationException("INVALID_SCORE_ENDPOINT",
                    "endpoint score source column must be numeric: " + source.id());
        }
        String columnId = requestedColumnId == null || requestedColumnId.isBlank()
                ? "score__" + slug(definition.id()) : requestedColumnId.trim();
        PrismColumn existingColumn = snapshot.table().findColumn(columnId).orElse(null);
        if (existingColumn != null) {
            Object fingerprint = existingColumn.schema().raw().get("scoreDefinitionFingerprint");
            if (definition.fingerprint().equals(fingerprint)) {
                return PrismOperationResult.builder()
                        .addScoreDefinition(definition)
                        .output("scoreId", definition.id())
                        .output("sourceColumnId", source.id())
                        .output("outputColumnId", columnId)
                        .output("reused", true)
                        .provenance("operation", "define_endpoint_score")
                        .build();
            }
            throw new PrismOperationException("COLUMN_EXISTS",
                    "output score column already exists with different provenance: " + columnId);
        }

        ArrayList<Object> values = new ArrayList<>(snapshot.table().rowCount());
        for (int row = 0; row < snapshot.table().rowCount(); row++) {
            Double input = source.isMissing(row) ? null : source.doubleValueAt(row);
            values.add(ScoreEvaluator.evaluate(definition, input).score());
        }
        LinkedHashMap<String, Object> raw = new LinkedHashMap<>();
        raw.put("scoreId", definition.id());
        raw.put("scoreDefinitionFingerprint", definition.fingerprint());
        raw.put("sourceColumnId", source.id());
        raw.put("createdAt", Instant.now().toString());
        Map<String, Object> metadata = Map.copyOf(raw);
        MaterializedColumnData column = new MaterializedColumnData(
                new PrismColumnSchema(columnId, PrismColumnType.NUMERIC, definition.displayName(),
                        "endpoint_score", "score", null, definition.endpointId(),
                        "higher_is_better", null, metadata),
                values,
                metadata);
        return PrismOperationResult.builder()
                .addScoreDefinition(definition)
                .addColumn(column)
                .output("scoreId", definition.id())
                .output("sourceColumnId", source.id())
                .output("outputColumnId", columnId)
                .output("reused", false)
                .provenance("operation", "define_endpoint_score")
                .build();
    }

    private static PrismColumn resolveSourceColumn(PrismTable table, String endpointId) {
        PrismColumn exact = table.findColumn(endpointId).orElse(null);
        if (exact != null) return exact;
        List<PrismColumn> matches = table.columns().stream()
                .filter(column -> endpointId.equals(column.schema().endpointId()))
                .toList();
        if (matches.isEmpty()) {
            throw new PrismOperationException("SCORE_ENDPOINT_NOT_FOUND",
                    "no column is linked to endpoint '" + endpointId + "'");
        }
        if (matches.size() > 1) {
            throw new PrismOperationException("AMBIGUOUS_SCORE_ENDPOINT",
                    "multiple columns are linked to endpoint '" + endpointId + "': "
                            + matches.stream().map(PrismColumn::id).toList());
        }
        return matches.getFirst();
    }

    private static String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        return slug.isBlank() ? "score" : slug;
    }
}

package tech.molecules.structurized.prism.report;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Derives what a report depends on, so that a later failure to resolve a reference can explain
 * itself instead of only naming the missing column.
 *
 * <p>Materialised analysis columns already record their origin in their schema {@code raw} map -
 * for example a scaffold-SAR substituent column carries {@code sarAnalysisId} and
 * {@code sarMaterializationFingerprint}. Walking a report's referenced columns and collecting that
 * provenance turns "Unknown column: sar.macro.R_cap" into "requires scaffold analysis macro29,
 * which is not present in this dataset". The report author writes nothing; it is all derived.
 *
 * <p>The fingerprint is as useful as the ID: if the analysis exists but its fingerprint differs,
 * the scaffold or its materialisation changed and the SAR blocks will render against a different
 * decomposition. That is otherwise invisible.
 */
public final class PrismReportRequirements {
    private PrismReportRequirements() {
    }

    /** Provenance keys that identify the analysis behind a materialised column. */
    private static final List<String> ANALYSIS_ID_KEYS = List.of("sarAnalysisId", "analysisId", "clusteringId");
    private static final List<String> FINGERPRINT_KEYS =
            List.of("sarMaterializationFingerprint", "materializationFingerprint", "fingerprint");

    /**
     * Builds the {@code requiresAnalyses} / {@code requiresRowSets} metadata for a report.
     *
     * @return a map suitable for an attachment {@code raw} block; empty when nothing is derivable
     */
    public static Map<String, Object> describe(PrismReportDocument document, PrismSession session) {
        TreeSet<String> columnIds = new TreeSet<>();
        TreeSet<String> rowSetIds = new TreeSet<>();
        for (PrismReportBlock block : document.blocks()) {
            if (!(block instanceof EmbeddedPrismViewReportBlock embedded)) continue;
            columnIds.addAll(embedded.specification().referencedColumnIds());
            rowSetIds.addAll(embedded.specification().referencedRowSetIds());
        }

        // analysisId -> { kind, analysisId, fingerprint, columns[] }
        LinkedHashMap<String, LinkedHashMap<String, Object>> analyses = new LinkedHashMap<>();
        for (String columnId : columnIds) {
            PrismColumn column = session.table().findColumn(columnId).orElse(null);
            if (column == null || column.schema() == null) continue;
            Map<String, Object> raw = column.schema().raw();
            if (raw == null || raw.isEmpty()) continue;
            String analysisId = firstPresent(raw, ANALYSIS_ID_KEYS);
            if (analysisId == null) continue;
            LinkedHashMap<String, Object> entry = analyses.computeIfAbsent(analysisId, key -> {
                LinkedHashMap<String, Object> created = new LinkedHashMap<>();
                created.put("kind", kindOf(column.schema().semanticType(), raw));
                created.put("analysisId", key);
                String fingerprint = firstPresent(raw, FINGERPRINT_KEYS);
                if (fingerprint != null) created.put("fingerprint", fingerprint);
                created.put("columns", new ArrayList<String>());
                return created;
            });
            @SuppressWarnings("unchecked")
            List<String> columns = (List<String>) entry.get("columns");
            columns.add(columnId);
        }

        LinkedHashMap<String, Object> described = new LinkedHashMap<>();
        if (!analyses.isEmpty()) described.put("requiresAnalyses", List.copyOf(analyses.values()));
        if (!rowSetIds.isEmpty()) described.put("requiresRowSets", List.copyOf(rowSetIds));
        if (!columnIds.isEmpty()) described.put("requiresColumns", List.copyOf(columnIds));
        return Map.copyOf(described);
    }

    private static String kindOf(String semanticType, Map<String, Object> raw) {
        Object source = raw.get("source");
        if (source != null) return String.valueOf(source);
        return semanticType == null ? "analysis" : semanticType;
    }

    private static String firstPresent(Map<String, Object> raw, List<String> keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return null;
    }
}

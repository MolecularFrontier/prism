package tech.molecules.structurized.prism.engine;

import tech.molecules.structurized.prism.score.EndpointScoreDefinition;
import tech.molecules.structurized.prism.score.EndpointScoreEvaluation;
import tech.molecules.structurized.prism.score.MpoDefinition;
import tech.molecules.structurized.prism.score.MpoEvaluation;
import tech.molecules.structurized.prism.score.PropertyProfileDefinition;
import tech.molecules.structurized.prism.score.PropertyProfileItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MaterializePropertyProfileOperation implements PrismOperation {
    public static final String ID = "score.materialize_property_profile";

    private static final PrismOperationDescriptor DESCRIPTOR = new PrismOperationDescriptor(
            ID, "1", "Materialize property profile",
            "Evaluate endpoint desirability scores and MPO results for every row.",
            List.of(PrismOperationParameter.requiredString("profileId", "Property profile")),
            Set.of(PrismOperationEffect.ADD_COLUMNS), PrismExecutionProfile.SHORT);

    @Override
    public PrismOperationDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public PrismOperationResult execute(PrismSessionSnapshot snapshot, Map<String, Object> parameters) {
        String profileId = required(parameters, "profileId");
        PropertyProfileDefinition profile = snapshot.propertyProfiles().get(profileId);
        if (profile == null) {
            throw new PrismOperationException("PROFILE_NOT_FOUND", "unknown property profile '" + profileId + "'",
                    "profileId", Map.of("profileId", profileId));
        }
        ArrayList<PropertyProfileRowEvaluation> rows = new ArrayList<>(snapshot.table().rowCount());
        for (int row = 0; row < snapshot.table().rowCount(); row++) {
            rows.add(PropertyProfileEvaluator.evaluate(snapshot, profile, row));
        }
        PrismOperationResult.Builder result = PrismOperationResult.builder()
                .provenance("operationId", ID)
                .provenance("propertyProfileId", profile.id());
        for (PropertyProfileItem item : profile.items()) {
            if (item.scoreId() == null) continue;
            EndpointScoreDefinition score = snapshot.scoreDefinitions().get(item.scoreId());
            if (score == null) {
                result.addWarning("Score definition is unavailable: " + item.scoreId());
                continue;
            }
            String columnId = availableColumnId(snapshot.table(), "score__" + slug(score.id()), score.fingerprint());
            if (columnId == null) {
                result.addWarning("Reused cached score column for " + score.id());
                continue;
            }
            ArrayList<Object> values = new ArrayList<>(rows.size());
            for (PropertyProfileRowEvaluation row : rows) {
                EndpointScoreEvaluation evaluation = row.scores().get(score.id());
                values.add(evaluation == null ? null : evaluation.score());
            }
            Map<String, Object> raw = Map.of(
                    "scoreId", score.id(), "scoreDefinitionFingerprint", score.fingerprint(),
                    "propertyProfileId", profile.id());
            result.addColumn(new MaterializedColumnData(
                    new PrismColumnSchema(columnId, PrismColumnType.NUMERIC, item.displayLabel() + " Score",
                            "endpoint_score", "score", null, item.endpointId(), "higher_is_better", null, raw),
                    values, raw));
        }
        for (MpoDefinition mpo : profile.mpos()) {
            addMpoColumns(snapshot, profile, mpo, rows, result);
        }
        return result.build();
    }

    private static void addMpoColumns(PrismSessionSnapshot snapshot,
                                      PropertyProfileDefinition profile,
                                      MpoDefinition mpo,
                                      List<PropertyProfileRowEvaluation> rows,
                                      PrismOperationResult.Builder result) {
        String fingerprint = mpoFingerprint(snapshot, mpo);
        Map<String, Object> raw = Map.of("mpoId", mpo.id(), "mpoDefinitionFingerprint", fingerprint,
                "propertyProfileId", profile.id());
        addColumn(snapshot, rows, result, "mpo__" + slug(mpo.id()), mpo.displayName(), "mpo_score",
                PrismColumnType.NUMERIC, fingerprint, raw, evaluation -> evaluation.score());
        addColumn(snapshot, rows, result, "mpo__" + slug(mpo.id()) + "__coverage", mpo.displayName() + " Coverage",
                "mpo_coverage", PrismColumnType.NUMERIC, fingerprint, raw, evaluation -> evaluation.coverage());
        addColumn(snapshot, rows, result, "mpo__" + slug(mpo.id()) + "__status", mpo.displayName() + " Status",
                "mpo_status", PrismColumnType.CATEGORICAL, fingerprint, raw, evaluation -> evaluation.status().name());
    }

    private static void addColumn(PrismSessionSnapshot snapshot,
                                  List<PropertyProfileRowEvaluation> rows,
                                  PrismOperationResult.Builder result,
                                  String baseId,
                                  String displayName,
                                  String semanticType,
                                  PrismColumnType type,
                                  String fingerprint,
                                  Map<String, Object> raw,
                                  MpoValue value) {
        String columnId = availableColumnId(snapshot.table(), baseId, fingerprint);
        if (columnId == null) {
            result.addWarning("Reused cached column " + baseId);
            return;
        }
        ArrayList<Object> values = new ArrayList<>(rows.size());
        String mpoId = String.valueOf(raw.get("mpoId"));
        for (PropertyProfileRowEvaluation row : rows) {
            MpoEvaluation evaluation = row.mpos().get(mpoId);
            values.add(evaluation == null ? null : value.get(evaluation));
        }
        result.addColumn(new MaterializedColumnData(
                new PrismColumnSchema(columnId, type, displayName, semanticType, "score", null, null,
                        "mpo_status".equals(semanticType) ? null : "higher_is_better", null, raw), values, raw));
    }

    private static String availableColumnId(PrismTable table, String baseId, String fingerprint) {
        PrismColumn existing = table.findColumn(baseId).orElse(null);
        if (existing == null) return baseId;
        Object existingFingerprint = existing.schema().raw().get("scoreDefinitionFingerprint");
        if (existingFingerprint == null) existingFingerprint = existing.schema().raw().get("mpoDefinitionFingerprint");
        if (fingerprint.equals(existingFingerprint)) return null;
        String candidate = baseId + "__recomputed";
        int suffix = 2;
        while (table.findColumn(candidate).isPresent()) candidate = baseId + "__recomputed_" + suffix++;
        return candidate;
    }

    private static String mpoFingerprint(PrismSessionSnapshot snapshot, MpoDefinition mpo) {
        StringBuilder fingerprint = new StringBuilder(mpo.id()).append('|')
                .append(mpo.aggregation().warningCoverageBelow());
        mpo.components().forEach(component -> {
            EndpointScoreDefinition score = snapshot.scoreDefinitions().get(component.scoreId());
            fingerprint.append('|').append(component.endpointId()).append(':').append(component.scoreId())
                    .append(':').append(component.weight()).append(':').append(component.required())
                    .append(':').append(component.hardFailBelow())
                    .append(':').append(score == null ? "missing" : score.fingerprint());
        });
        return Integer.toHexString(fingerprint.toString().hashCode());
    }

    private static String required(Map<String, Object> parameters, String id) {
        Object value = parameters.get(id);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new PrismOperationException("INVALID_PARAMETER", "missing required parameter '" + id + "'", id, Map.of());
        }
        return String.valueOf(value).trim();
    }

    private static String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return slug.isBlank() ? "score" : slug;
    }

    @FunctionalInterface
    private interface MpoValue {
        Object get(MpoEvaluation evaluation);
    }
}

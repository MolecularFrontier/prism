package tech.molecules.structurized.prism.engine;

import tech.molecules.structurized.prism.score.EndpointScoreDefinition;
import tech.molecules.structurized.prism.score.EndpointScoreEvaluation;
import tech.molecules.structurized.prism.score.MpoDefinition;
import tech.molecules.structurized.prism.score.MpoEvaluation;
import tech.molecules.structurized.prism.score.PropertyProfileDefinition;
import tech.molecules.structurized.prism.score.PropertyProfileItem;
import tech.molecules.structurized.prism.score.ScoreEvaluator;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PropertyProfileEvaluator {
    private PropertyProfileEvaluator() {
    }

    public static PropertyProfileRowEvaluation evaluate(PrismSessionSnapshot snapshot,
                                                        PropertyProfileDefinition profile,
                                                        int physicalRow) {
        LinkedHashMap<String, Double> endpointValues = new LinkedHashMap<>();
        LinkedHashMap<String, EndpointScoreEvaluation> scores = new LinkedHashMap<>();
        for (PropertyProfileItem item : profile.items()) {
            Double value = numericEndpointValue(snapshot.table(), item.endpointId(), physicalRow);
            endpointValues.put(item.endpointId(), value);
            if (item.scoreId() == null) continue;
            EndpointScoreDefinition definition = snapshot.scoreDefinitions().get(item.scoreId());
            EndpointScoreEvaluation evaluation = definition == null
                    ? new EndpointScoreEvaluation(item.scoreId(), item.endpointId(), value, null,
                    "SCORE_NOT_FOUND", "Score definition is unavailable")
                    : ScoreEvaluator.evaluate(definition, value);
            scores.put(item.scoreId(), evaluation);
        }
        LinkedHashMap<String, MpoEvaluation> mpos = new LinkedHashMap<>();
        for (MpoDefinition mpo : profile.mpos()) {
            mpos.put(mpo.id(), ScoreEvaluator.evaluate(mpo, endpointValues, snapshot.scoreDefinitions()));
        }
        return new PropertyProfileRowEvaluation(snapshot.rowIdIndex().rowId(physicalRow), scores, mpos);
    }

    private static Double numericEndpointValue(PrismTable table, String endpointId, int physicalRow) {
        for (PrismColumn column : table.columns()) {
            if (!endpointId.equals(column.schema().endpointId()) && !endpointId.equals(column.id())) continue;
            if (column.type() != PrismColumnType.NUMERIC && column.type() != PrismColumnType.INTEGER) return null;
            if (column.isMissing(physicalRow)) return null;
            double value = column.doubleValueAt(physicalRow);
            return Double.isFinite(value) ? value : null;
        }
        return null;
    }
}

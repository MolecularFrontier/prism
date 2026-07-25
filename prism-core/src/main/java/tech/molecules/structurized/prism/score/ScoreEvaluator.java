package tech.molecules.structurized.prism.score;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ScoreEvaluator {
    private ScoreEvaluator() {
    }

    public static EndpointScoreEvaluation evaluate(EndpointScoreDefinition definition, Double endpointValue) {
        if (endpointValue == null || !Double.isFinite(endpointValue)) {
            return new EndpointScoreEvaluation(definition.id(), definition.endpointId(), endpointValue, null,
                    "MISSING", "No finite numeric endpoint value");
        }
        if (EndpointScoreDefinition.LOG10.equals(definition.xScale()) && endpointValue <= 0.0) {
            return new EndpointScoreEvaluation(definition.id(), definition.endpointId(), endpointValue, null,
                    "INVALID_INPUT", "Endpoint value must be > 0 for log10 scoring");
        }
        List<ScorePoint> points = definition.points();
        double x = transform(endpointValue, definition.xScale());
        double value;
        if (x <= transform(points.getFirst().x(), definition.xScale())) {
            value = definition.clampOutsideRange()
                    ? points.getFirst().score()
                    : interpolate(x, points.get(0), points.get(1), definition.xScale());
        } else if (x >= transform(points.getLast().x(), definition.xScale())) {
            value = definition.clampOutsideRange()
                    ? points.getLast().score()
                    : interpolate(x, points.get(points.size() - 2), points.getLast(), definition.xScale());
        } else {
            value = Double.NaN;
            for (int i = 1; i < points.size(); i++) {
                if (x <= transform(points.get(i).x(), definition.xScale())) {
                    value = interpolate(x, points.get(i - 1), points.get(i), definition.xScale());
                    break;
                }
            }
        }
        return new EndpointScoreEvaluation(definition.id(), definition.endpointId(), endpointValue, value,
                "VALUE", "");
    }

    public static MpoEvaluation evaluate(MpoDefinition definition,
                                         Map<String, Double> endpointValues,
                                         Map<String, EndpointScoreDefinition> scores) {
        ArrayList<MpoComponentEvaluation> components = new ArrayList<>();
        double totalWeight = 0.0;
        double availableWeight = 0.0;
        double weightedSum = 0.0;
        int availableCount = 0;
        int missingCount = 0;
        int hardFailCount = 0;
        int requiredMissingCount = 0;
        for (MpoComponentDefinition component : definition.components()) {
            totalWeight += component.weight();
            EndpointScoreDefinition scoreDefinition = scores.get(component.scoreId());
            EndpointScoreEvaluation score = scoreDefinition == null
                    ? new EndpointScoreEvaluation(component.scoreId(), component.endpointId(), null, null,
                    "SCORE_NOT_FOUND", "Score definition is unavailable")
                    : evaluate(scoreDefinition, endpointValues.get(component.endpointId()));
            boolean hardFail = score.available() && component.hardFailBelow() != null
                    && score.score() < component.hardFailBelow();
            components.add(new MpoComponentEvaluation(component, score, hardFail));
            if (score.available()) {
                availableCount++;
                availableWeight += component.weight();
                weightedSum += score.score() * component.weight();
                if (hardFail) hardFailCount++;
            } else {
                missingCount++;
                if (component.required()) requiredMissingCount++;
            }
        }
        Double mpoScore = availableWeight > 0.0 ? weightedSum / availableWeight : null;
        double coverage = totalWeight > 0.0 ? availableWeight / totalWeight : 0.0;
        MpoStatus status;
        if (requiredMissingCount > 0 || mpoScore == null) {
            status = MpoStatus.INSUFFICIENT_DATA;
        } else if (hardFailCount > 0) {
            status = MpoStatus.FAIL;
        } else if (coverage < definition.aggregation().warningCoverageBelow()) {
            status = MpoStatus.WARNING;
        } else {
            status = MpoStatus.PASS;
        }
        return new MpoEvaluation(definition.id(), mpoScore, coverage, availableCount, missingCount,
                hardFailCount, requiredMissingCount, status, components);
    }

    private static double interpolate(double x, ScorePoint left, ScorePoint right, String scale) {
        double leftX = transform(left.x(), scale);
        double rightX = transform(right.x(), scale);
        double fraction = (x - leftX) / (rightX - leftX);
        return left.score() + fraction * (right.score() - left.score());
    }

    private static double transform(double value, String scale) {
        return EndpointScoreDefinition.LOG10.equals(scale) ? Math.log10(value) : value;
    }
}

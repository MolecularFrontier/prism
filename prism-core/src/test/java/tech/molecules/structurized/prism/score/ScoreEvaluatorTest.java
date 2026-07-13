package tech.molecules.structurized.prism.score;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoreEvaluatorTest {
    @Test
    void evaluatesLinearAndLog10LineSegments() {
        EndpointScoreDefinition linear = score("potency", "pIC50", EndpointScoreDefinition.LINEAR,
                List.of(new ScorePoint(5.0, 0.0), new ScorePoint(9.0, 1.0)));
        EndpointScoreDefinition log = score("clearance", "clint", EndpointScoreDefinition.LOG10,
                List.of(new ScorePoint(1.0, 1.0), new ScorePoint(100.0, 0.0)));

        assertEquals(0.5, ScoreEvaluator.evaluate(linear, 7.0).score(), 1.0e-12);
        assertEquals(0.5, ScoreEvaluator.evaluate(log, 10.0).score(), 1.0e-12);
        assertEquals(0.0, ScoreEvaluator.evaluate(linear, 3.0).score(), 1.0e-12);
        assertFalse(ScoreEvaluator.evaluate(log, 0.0).available());
    }

    @Test
    void rejectsInvalidScoreDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> new EndpointScoreDefinition(
                "bad", "pIC50", "Bad", null, "line_segment_v1", "linear", true,
                List.of(new ScorePoint(1.0, 0.0), new ScorePoint(1.0, 1.0)), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ScorePoint(1.0, 1.1));
    }

    @Test
    void evaluatesMpoCoverageRequiredAndHardFailSemantics() {
        EndpointScoreDefinition potency = score("potency", "pIC50", EndpointScoreDefinition.LINEAR,
                List.of(new ScorePoint(5.0, 0.0), new ScorePoint(9.0, 1.0)));
        EndpointScoreDefinition clearance = score("clearance", "clint", EndpointScoreDefinition.LINEAR,
                List.of(new ScorePoint(0.0, 1.0), new ScorePoint(100.0, 0.0)));
        MpoDefinition mpo = new MpoDefinition("lead_mpo", "Lead MPO", List.of(
                new MpoComponentDefinition("pIC50", "potency", "Potency", 2.0, true, 0.4),
                new MpoComponentDefinition("clint", "clearance", "Clearance", 1.0, false, null)
        ), new MpoAggregationDefinition("weighted_mean", "ignore", 0.8));
        Map<String, EndpointScoreDefinition> scores = Map.of("potency", potency, "clearance", clearance);

        MpoEvaluation pass = ScoreEvaluator.evaluate(mpo, Map.of("pIC50", 8.0, "clint", 20.0), scores);
        assertEquals(0.7666666666666666, pass.score(), 1.0e-12);
        assertEquals(MpoStatus.PASS, pass.status());

        MpoEvaluation warning = ScoreEvaluator.evaluate(mpo, Map.of("pIC50", 8.0), scores);
        assertEquals(2.0 / 3.0, warning.coverage(), 1.0e-12);
        assertEquals(MpoStatus.WARNING, warning.status());

        MpoEvaluation requiredMissing = ScoreEvaluator.evaluate(mpo, Map.of("clint", 20.0), scores);
        assertEquals(MpoStatus.INSUFFICIENT_DATA, requiredMissing.status());

        MpoEvaluation hardFail = ScoreEvaluator.evaluate(mpo, Map.of("pIC50", 5.5, "clint", 20.0), scores);
        assertEquals(MpoStatus.FAIL, hardFail.status());

        MpoEvaluation empty = ScoreEvaluator.evaluate(mpo, Map.of(), scores);
        assertNull(empty.score());
        assertEquals(MpoStatus.INSUFFICIENT_DATA, empty.status());
    }

    private static EndpointScoreDefinition score(String id, String endpointId, String scale, List<ScorePoint> points) {
        return new EndpointScoreDefinition(id, endpointId, id, null, "line_segment_v1", scale, true, points, Map.of());
    }
}

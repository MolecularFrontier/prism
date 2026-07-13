package tech.molecules.structurized.prism.engine;

import tech.molecules.structurized.prism.score.EndpointScoreEvaluation;
import tech.molecules.structurized.prism.score.MpoEvaluation;

import java.util.Map;

public record PropertyProfileRowEvaluation(
        String rowId,
        Map<String, EndpointScoreEvaluation> scores,
        Map<String, MpoEvaluation> mpos
) {
    public PropertyProfileRowEvaluation {
        scores = scores == null ? Map.of() : Map.copyOf(scores);
        mpos = mpos == null ? Map.of() : Map.copyOf(mpos);
    }
}

package tech.molecules.structurized.prism.engine;

import tech.molecules.structurized.prism.score.ScoreEvaluator;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EvaluateEndpointScoreOperation implements PrismOperation {
    public static final String ID = "score.evaluate_endpoint_score";
    private static final PrismOperationDescriptor DESCRIPTOR = new PrismOperationDescriptor(
            ID, "1", "Evaluate endpoint score", "Apply one portable score definition to a numeric endpoint value.",
            List.of(PrismOperationParameter.requiredString("scoreId", "Score"),
                    new PrismOperationParameter("value", PrismOperationParameterType.NUMBER, "Value", "", true, List.of(), Map.of())),
            Set.of(), PrismExecutionProfile.INTERACTIVE);

    @Override public PrismOperationDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public PrismOperationResult execute(PrismSessionSnapshot snapshot, Map<String, Object> parameters) {
        String scoreId = String.valueOf(parameters.get("scoreId"));
        var definition = snapshot.scoreDefinitions().get(scoreId);
        if (definition == null) throw new PrismOperationException("SCORE_NOT_FOUND", "unknown score definition '" + scoreId + "'");
        double value = ((Number) parameters.get("value")).doubleValue();
        return PrismOperationResult.builder()
                .output("evaluation", ScoreOperationOutput.score(ScoreEvaluator.evaluate(definition, value))).build();
    }
}

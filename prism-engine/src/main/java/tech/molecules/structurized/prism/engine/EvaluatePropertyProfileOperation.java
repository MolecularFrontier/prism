package tech.molecules.structurized.prism.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EvaluatePropertyProfileOperation implements PrismOperation {
    public static final String ID = "score.evaluate_property_profile";
    private static final PrismOperationDescriptor DESCRIPTOR = new PrismOperationDescriptor(
            ID, "1", "Evaluate property profile", "Evaluate all endpoint scores and MPOs for one stable Prism row ID.",
            List.of(PrismOperationParameter.requiredString("profileId", "Property profile"),
                    PrismOperationParameter.requiredString("rowId", "Row ID")),
            Set.of(), PrismExecutionProfile.INTERACTIVE);

    @Override public PrismOperationDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public PrismOperationResult execute(PrismSessionSnapshot snapshot, Map<String, Object> parameters) {
        String profileId = String.valueOf(parameters.get("profileId"));
        String rowId = String.valueOf(parameters.get("rowId"));
        var profile = snapshot.propertyProfiles().get(profileId);
        if (profile == null) throw new PrismOperationException("PROFILE_NOT_FOUND", "unknown property profile '" + profileId + "'");
        int physicalRow = snapshot.rowIdIndex().physicalRow(rowId).orElseThrow(() ->
                new PrismOperationException("ROW_NOT_FOUND", "unknown row ID '" + rowId + "'"));
        PropertyProfileRowEvaluation evaluation = PropertyProfileEvaluator.evaluate(snapshot, profile, physicalRow);
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("rowId", rowId);
        output.put("profileId", profileId);
        output.put("scores", evaluation.scores().values().stream().map(ScoreOperationOutput::score).toList());
        output.put("mpos", evaluation.mpos().values().stream().map(ScoreOperationOutput::mpo).toList());
        return PrismOperationResult.builder().output("evaluation", output).build();
    }
}

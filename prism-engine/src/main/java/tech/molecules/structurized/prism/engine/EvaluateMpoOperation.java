package tech.molecules.structurized.prism.engine;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EvaluateMpoOperation implements PrismOperation {
    public static final String ID = "score.evaluate_mpo";
    private static final PrismOperationDescriptor DESCRIPTOR = new PrismOperationDescriptor(
            ID, "1", "Evaluate MPO", "Evaluate one MPO for one stable Prism row ID, including component contributions.",
            List.of(PrismOperationParameter.requiredString("profileId", "Property profile"),
                    PrismOperationParameter.requiredString("mpoId", "MPO"),
                    PrismOperationParameter.requiredString("rowId", "Row ID")),
            Set.of(), PrismExecutionProfile.INTERACTIVE);

    @Override public PrismOperationDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public PrismOperationResult execute(PrismSessionSnapshot snapshot, Map<String, Object> parameters) {
        String profileId = String.valueOf(parameters.get("profileId"));
        String mpoId = String.valueOf(parameters.get("mpoId"));
        String rowId = String.valueOf(parameters.get("rowId"));
        var profile = snapshot.propertyProfiles().get(profileId);
        if (profile == null) throw new PrismOperationException("PROFILE_NOT_FOUND", "unknown property profile '" + profileId + "'");
        int physicalRow = snapshot.rowIdIndex().physicalRow(rowId).orElseThrow(() ->
                new PrismOperationException("ROW_NOT_FOUND", "unknown row ID '" + rowId + "'"));
        var evaluation = PropertyProfileEvaluator.evaluate(snapshot, profile, physicalRow).mpos().get(mpoId);
        if (evaluation == null) throw new PrismOperationException("MPO_NOT_FOUND", "unknown MPO '" + mpoId + "' in profile '" + profileId + "'");
        return PrismOperationResult.builder().output("evaluation", ScoreOperationOutput.mpo(evaluation)).build();
    }
}

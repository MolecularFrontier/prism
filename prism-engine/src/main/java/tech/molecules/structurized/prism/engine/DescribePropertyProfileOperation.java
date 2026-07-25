package tech.molecules.structurized.prism.engine;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DescribePropertyProfileOperation implements PrismOperation {
    public static final String ID = "score.describe_property_profile";
    private static final PrismOperationDescriptor DESCRIPTOR = new PrismOperationDescriptor(
            ID, "1", "Describe property profile", "Return endpoint, score, grouping, and MPO definitions for one profile.",
            List.of(PrismOperationParameter.requiredString("profileId", "Property profile")), Set.of(), PrismExecutionProfile.INTERACTIVE);

    @Override public PrismOperationDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public PrismOperationResult execute(PrismSessionSnapshot snapshot, Map<String, Object> parameters) {
        String profileId = String.valueOf(parameters.get("profileId"));
        var profile = snapshot.propertyProfiles().get(profileId);
        if (profile == null) throw new PrismOperationException("PROFILE_NOT_FOUND", "unknown property profile '" + profileId + "'");
        return PrismOperationResult.builder().output("profile", ScoreOperationOutput.profile(profile)).build();
    }
}

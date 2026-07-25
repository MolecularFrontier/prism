package tech.molecules.structurized.prism.engine;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ListPropertyProfilesOperation implements PrismOperation {
    public static final String ID = "score.list_property_profiles";
    private static final PrismOperationDescriptor DESCRIPTOR = new PrismOperationDescriptor(
            ID, "1", "List property profiles", "List portable endpoint score and MPO profiles.",
            List.of(), Set.of(), PrismExecutionProfile.INTERACTIVE);

    @Override public PrismOperationDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public PrismOperationResult execute(PrismSessionSnapshot snapshot, Map<String, Object> parameters) {
        return PrismOperationResult.builder().output("profiles", snapshot.propertyProfiles().values().stream()
                .map(profile -> Map.of("id", profile.id(), "title", profile.title(),
                        "endpointCount", profile.items().size(), "mpoCount", profile.mpos().size()))
                .toList()).build();
    }
}

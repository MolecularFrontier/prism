package tech.molecules.structurized.prism.engine;

import java.util.List;
import java.util.Set;

public record PrismOperationDescriptor(
        String id,
        String version,
        String name,
        String description,
        List<PrismOperationParameter> parameters,
        Set<PrismOperationEffect> effects,
        PrismExecutionProfile executionProfile
) {
    public PrismOperationDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("operation id must not be blank");
        }
        id = id.trim();
        version = version == null || version.isBlank() ? "1" : version.trim();
        name = name == null || name.isBlank() ? id : name.trim();
        description = description == null ? "" : description.trim();
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        effects = effects == null ? Set.of() : Set.copyOf(effects);
        executionProfile = executionProfile == null ? PrismExecutionProfile.INTERACTIVE : executionProfile;
    }
}

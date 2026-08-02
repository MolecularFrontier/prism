package tech.molecules.structurized.prism.engine.live;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface PrismLiveComputationProvider<T> {
    PrismLiveCapability<T> capability();

    String version();

    default boolean supports(PrismLiveInput input) {
        return true;
    }

    default String fingerprint(PrismLiveInput input, Map<String, Object> configuration) {
        return input.resourceType() + ":" + input.resourceId() + ":" + input.revision();
    }

    CompletionStage<T> compute(
            PrismLiveInput input,
            Map<String, Object> configuration,
            PrismLiveComputationContext context
    );
}

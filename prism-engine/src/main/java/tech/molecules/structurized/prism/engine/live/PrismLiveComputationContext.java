package tech.molecules.structurized.prism.engine.live;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface PrismLiveComputationContext {
    PrismLiveInput input();

    Map<String, Object> configuration();

    default <T> CompletionStage<T> require(PrismLiveCapability<T> capability) {
        return require(capability, Map.of());
    }

    <T> CompletionStage<T> require(
            PrismLiveCapability<T> capability,
            Map<String, Object> configuration
    );
}

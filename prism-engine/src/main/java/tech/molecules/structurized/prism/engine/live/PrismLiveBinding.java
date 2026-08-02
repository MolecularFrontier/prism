package tech.molecules.structurized.prism.engine.live;

import java.time.Duration;
import java.util.Map;

public record PrismLiveBinding(
        String id,
        String capabilityId,
        PrismLiveExecutionMode mode,
        Duration quietPeriod,
        Map<String, Object> configuration
) {
    public PrismLiveBinding {
        id = requireText(id, "binding id");
        capabilityId = requireText(capabilityId, "capability id");
        mode = mode == null ? PrismLiveExecutionMode.MANUAL : mode;
        quietPeriod = quietPeriod == null ? Duration.ZERO : quietPeriod;
        if (quietPeriod.isNegative()) {
            throw new IllegalArgumentException("quiet period must not be negative");
        }
        configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

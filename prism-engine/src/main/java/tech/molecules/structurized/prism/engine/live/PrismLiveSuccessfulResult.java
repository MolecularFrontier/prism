package tech.molecules.structurized.prism.engine.live;

import java.time.Instant;
import java.util.Objects;

public record PrismLiveSuccessfulResult(
        long inputRevision,
        Instant completedAt,
        PrismLiveResult result
) {
    public PrismLiveSuccessfulResult {
        Objects.requireNonNull(completedAt, "completedAt");
        Objects.requireNonNull(result, "result");
    }
}

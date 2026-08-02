package tech.molecules.structurized.prism.engine.live;

import java.time.Instant;
import java.util.Objects;

public record PrismLiveEvaluation(
        String bindingId,
        String resourceId,
        long targetRevision,
        PrismLiveEvaluationStatus status,
        Instant updatedAt,
        PrismLiveSuccessfulResult lastSuccessful,
        String error
) {
    public PrismLiveEvaluation {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(updatedAt, "updatedAt");
        error = error == null ? "" : error;
    }

    public boolean showingStaleResult() {
        return lastSuccessful != null && lastSuccessful.inputRevision() != targetRevision;
    }
}

package tech.molecules.structurized.prism.engine.live;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

public record PrismLiveExecutionEnvironment(
        ScheduledExecutorService scheduler,
        Executor computationExecutor,
        int completedCacheEntries
) {
    public PrismLiveExecutionEnvironment {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(computationExecutor, "computationExecutor");
        if (completedCacheEntries < 1) {
            throw new IllegalArgumentException("completed cache size must be positive");
        }
    }
}

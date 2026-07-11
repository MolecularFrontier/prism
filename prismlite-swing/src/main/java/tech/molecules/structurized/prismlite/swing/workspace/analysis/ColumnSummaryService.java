package tech.molecules.structurized.prismlite.swing.workspace.analysis;

import tech.molecules.structurized.prism.engine.PrismTable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

public final class ColumnSummaryService {
    private final PrismTable table;
    private final Executor executor;
    private final ConcurrentMap<String, CompletableFuture<ColumnSummary>> summaries = new ConcurrentHashMap<>();

    public ColumnSummaryService(PrismTable table, Executor executor) {
        this.table = Objects.requireNonNull(table, "table");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public CompletionStage<ColumnSummary> summary(String columnId) {
        return summaries.computeIfAbsent(columnId, id -> CompletableFuture.supplyAsync(
                () -> ColumnSummaries.compute(table.column(id)),
                executor
        ));
    }

    public void invalidate(String columnId) {
        summaries.remove(columnId);
    }

    public void clear() {
        summaries.clear();
    }
}

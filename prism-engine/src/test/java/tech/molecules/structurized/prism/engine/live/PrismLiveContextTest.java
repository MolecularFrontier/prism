package tech.molecules.structurized.prism.engine.live;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismMoleculeDocumentMode;
import tech.molecules.structurized.prism.engine.PrismMoleculeWorkspace;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrismLiveContextTest {
    private static final PrismLiveCapability<PrismLiveResult> ROOT =
            resultCapability("test.root");
    private static final PrismLiveCapability<PrismLiveResult> ROOT_A =
            resultCapability("test.root.a");
    private static final PrismLiveCapability<PrismLiveResult> ROOT_B =
            resultCapability("test.root.b");
    private static final PrismLiveCapability<String> SHARED =
            new PrismLiveCapability<>("test.shared", "Shared", "", String.class, false);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService computations = Executors.newFixedThreadPool(4);
    private final List<PrismLiveContext> contexts = new ArrayList<>();

    @AfterEach
    void shutdown() {
        for (PrismLiveContext context : contexts) context.close();
        scheduler.shutdownNow();
        computations.shutdownNow();
    }

    @Test
    void debouncesRapidDocumentChangesAndPublishesOnlyLatestRevision() throws Exception {
        PrismMoleculeWorkspace molecules = new PrismMoleculeWorkspace();
        PrismLiveContext context = context(molecules);
        AtomicInteger computations = new AtomicInteger();
        context.registerProvider(resultProvider(ROOT, input -> {
            computations.incrementAndGet();
            return result("revision", input.revision());
        }));
        context.configureBinding(new PrismLiveBinding(
                "root", ROOT.id(), PrismLiveExecutionMode.AUTO, Duration.ofMillis(80), Map.of()));

        var document = molecules.addDocument(PrismMoleculeWorkspace.SCRATCHPAD_ID, "idea",
                "Idea", PrismMoleculeDocumentMode.MOLECULE, "a", "");
        document = molecules.updateDocument(document.id(), document.revision(), document.title(),
                document.mode(), "b", "");
        document = molecules.updateDocument(document.id(), document.revision(), document.title(),
                document.mode(), "c", "");
        long expectedRevision = document.revision();

        await(() -> context.findEvaluation("root", "idea")
                .map(evaluation -> evaluation.status() == PrismLiveEvaluationStatus.SUCCEEDED)
                .orElse(false));

        PrismLiveEvaluation evaluation = context.findEvaluation("root", "idea").orElseThrow();
        assertEquals(expectedRevision, evaluation.targetRevision());
        assertEquals(expectedRevision, evaluation.lastSuccessful().result().values().get("revision"));
        assertEquals(1, computations.get());
    }

    @Test
    void ignoresCompletionFromSupersededRunningRevision() throws Exception {
        PrismMoleculeWorkspace molecules = new PrismMoleculeWorkspace();
        PrismLiveContext context = context(molecules);
        ConcurrentHashMap<Long, CompletableFuture<PrismLiveResult>> futures = new ConcurrentHashMap<>();
        context.registerProvider(new PrismLiveComputationProvider<PrismLiveResult>() {
            public PrismLiveCapability<PrismLiveResult> capability() { return ROOT; }
            public String version() { return "1"; }
            public CompletionStage<PrismLiveResult> compute(
                    PrismLiveInput input,
                    Map<String, Object> configuration,
                    PrismLiveComputationContext computationContext
            ) {
                CompletableFuture<PrismLiveResult> future = new CompletableFuture<>();
                futures.put(input.revision(), future);
                return future;
            }
        });
        context.configureBinding(new PrismLiveBinding(
                "root", ROOT.id(), PrismLiveExecutionMode.AUTO, Duration.ZERO, Map.of()));

        var document = molecules.addDocument(PrismMoleculeWorkspace.SCRATCHPAD_ID, "idea",
                "Idea", PrismMoleculeDocumentMode.MOLECULE, "a", "");
        await(() -> futures.containsKey(1L));
        document = molecules.updateDocument(document.id(), document.revision(), document.title(),
                document.mode(), "b", "");
        await(() -> futures.containsKey(2L));

        futures.get(1L).complete(result("revision", 1L));
        Thread.sleep(40);
        PrismLiveEvaluation whileSecondRuns = context.findEvaluation("root", "idea").orElseThrow();
        assertEquals(2, whileSecondRuns.targetRevision());
        assertEquals(PrismLiveEvaluationStatus.RUNNING, whileSecondRuns.status());

        futures.get(2L).complete(result("revision", 2L));
        await(() -> context.findEvaluation("root", "idea")
                .map(evaluation -> evaluation.status() == PrismLiveEvaluationStatus.SUCCEEDED)
                .orElse(false));
        assertEquals(2L, context.findEvaluation("root", "idea").orElseThrow()
                .lastSuccessful().result().values().get("revision"));
    }

    @Test
    void sharesOnePrerequisiteAcrossIndependentRootEvaluations() throws Exception {
        PrismMoleculeWorkspace molecules = new PrismMoleculeWorkspace();
        PrismLiveContext context = context(molecules);
        AtomicInteger prerequisiteCalls = new AtomicInteger();
        context.registerProvider(new PrismLiveComputationProvider<String>() {
            public PrismLiveCapability<String> capability() { return SHARED; }
            public String version() { return "1"; }
            public CompletionStage<String> compute(
                    PrismLiveInput input,
                    Map<String, Object> configuration,
                    PrismLiveComputationContext computationContext
            ) {
                prerequisiteCalls.incrementAndGet();
                return CompletableFuture.completedFuture("shared-" + input.revision());
            }
        });
        context.registerProvider(dependentProvider(ROOT_A));
        context.registerProvider(dependentProvider(ROOT_B));
        context.configureBinding(manual("root-a", ROOT_A));
        context.configureBinding(manual("root-b", ROOT_B));
        molecules.addDocument(PrismMoleculeWorkspace.SCRATCHPAD_ID, "idea",
                "Idea", PrismMoleculeDocumentMode.MOLECULE, "a", "");

        context.runNow("root-a", "idea", 1L);
        context.runNow("root-b", "idea", 1L);

        await(() -> context.evaluationsFor("idea").stream()
                .filter(evaluation -> evaluation.status() == PrismLiveEvaluationStatus.SUCCEEDED)
                .count() == 2);
        assertEquals(1, prerequisiteCalls.get());
    }

    @Test
    void rejectsDependencyCyclesWithoutPoisoningRuntime() throws Exception {
        PrismMoleculeWorkspace molecules = new PrismMoleculeWorkspace();
        PrismLiveContext context = context(molecules);
        context.registerProvider(new PrismLiveComputationProvider<PrismLiveResult>() {
            public PrismLiveCapability<PrismLiveResult> capability() { return ROOT; }
            public String version() { return "1"; }
            public CompletionStage<PrismLiveResult> compute(
                    PrismLiveInput input,
                    Map<String, Object> configuration,
                    PrismLiveComputationContext computationContext
            ) {
                return computationContext.require(ROOT);
            }
        });
        context.configureBinding(manual("root", ROOT));
        molecules.addDocument(PrismMoleculeWorkspace.SCRATCHPAD_ID, "idea",
                "Idea", PrismMoleculeDocumentMode.MOLECULE, "a", "");

        context.runNow("root", "idea", 1L);

        await(() -> context.findEvaluation("root", "idea")
                .map(evaluation -> evaluation.status() == PrismLiveEvaluationStatus.FAILED)
                .orElse(false));
        assertTrue(context.findEvaluation("root", "idea").orElseThrow().error().contains("cycle"));
    }

    private PrismLiveContext context(PrismMoleculeWorkspace molecules) {
        PrismLiveContext context = new PrismLiveContext(
                molecules,
                new PrismLiveExecutionEnvironment(scheduler, computations, 32)
        );
        contexts.add(context);
        return context;
    }

    private static PrismLiveComputationProvider<PrismLiveResult> resultProvider(
            PrismLiveCapability<PrismLiveResult> capability,
            java.util.function.Function<PrismLiveInput, PrismLiveResult> computation
    ) {
        return new PrismLiveComputationProvider<>() {
            public PrismLiveCapability<PrismLiveResult> capability() { return capability; }
            public String version() { return "1"; }
            public CompletionStage<PrismLiveResult> compute(
                    PrismLiveInput input,
                    Map<String, Object> configuration,
                    PrismLiveComputationContext computationContext
            ) {
                return CompletableFuture.completedFuture(computation.apply(input));
            }
        };
    }

    private static PrismLiveComputationProvider<PrismLiveResult> dependentProvider(
            PrismLiveCapability<PrismLiveResult> capability
    ) {
        return new PrismLiveComputationProvider<>() {
            public PrismLiveCapability<PrismLiveResult> capability() { return capability; }
            public String version() { return "1"; }
            public CompletionStage<PrismLiveResult> compute(
                    PrismLiveInput input,
                    Map<String, Object> configuration,
                    PrismLiveComputationContext computationContext
            ) {
                return computationContext.require(SHARED)
                        .thenApply(value -> result("value", value));
            }
        };
    }

    private static PrismLiveBinding manual(
            String id,
            PrismLiveCapability<PrismLiveResult> capability
    ) {
        return new PrismLiveBinding(id, capability.id(), PrismLiveExecutionMode.MANUAL, Duration.ZERO, Map.of());
    }

    private static PrismLiveCapability<PrismLiveResult> resultCapability(String id) {
        return new PrismLiveCapability<>(id, id, "", PrismLiveResult.class, true);
    }

    private static PrismLiveResult result(String key, Object value) {
        return new PrismLiveResult("test.result", Map.of(key, value), List.of(), Map.of());
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("condition was not satisfied before timeout");
            }
            Thread.sleep(10);
        }
    }
}

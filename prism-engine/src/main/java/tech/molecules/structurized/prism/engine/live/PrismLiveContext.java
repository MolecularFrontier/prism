package tech.molecules.structurized.prism.engine.live;

import tech.molecules.structurized.prism.engine.PrismMoleculeDocument;
import tech.molecules.structurized.prism.engine.PrismMoleculeList;
import tech.molecules.structurized.prism.engine.PrismMoleculeWorkspace;
import tech.molecules.structurized.prism.engine.PrismMoleculeWorkspaceChange;
import tech.molecules.structurized.prism.engine.PrismMoleculeWorkspaceSubscription;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class PrismLiveContext implements AutoCloseable {
    private final PrismMoleculeWorkspace molecules;
    private final PrismLiveExecutionEnvironment environment;
    private final LinkedHashMap<String, PrismLiveComputationProvider<?>> providers = new LinkedHashMap<>();
    private final LinkedHashMap<String, PrismLiveBinding> bindings = new LinkedHashMap<>();
    private final LinkedHashMap<RootKey, PrismLiveEvaluation> evaluations = new LinkedHashMap<>();
    private final Map<RootKey, Long> generations = new LinkedHashMap<>();
    private final Map<RootKey, ScheduledFuture<?>> pending = new LinkedHashMap<>();
    private final ConcurrentMap<ComputationKey, CompletableFuture<?>> inFlight = new ConcurrentHashMap<>();
    private final LinkedHashMap<ComputationKey, Object> completed;
    private final CopyOnWriteArrayList<Consumer<PrismLiveContextChange>> listeners = new CopyOnWriteArrayList<>();
    private final PrismMoleculeWorkspaceSubscription moleculeSubscription;
    private long sequence = 1;
    private boolean closed;

    public PrismLiveContext(
            PrismMoleculeWorkspace molecules,
            PrismLiveExecutionEnvironment environment
    ) {
        this.molecules = Objects.requireNonNull(molecules, "molecules");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.completed = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ComputationKey, Object> eldest) {
                return size() > PrismLiveContext.this.environment.completedCacheEntries();
            }
        };
        this.moleculeSubscription = molecules.subscribe(this::moleculeChanged);
    }

    public synchronized long sequence() {
        return sequence;
    }

    public synchronized List<PrismLiveCapability<?>> capabilities() {
        ArrayList<PrismLiveCapability<?>> result = new ArrayList<>(providers.size());
        for (PrismLiveComputationProvider<?> provider : providers.values()) {
            result.add(provider.capability());
        }
        return List.copyOf(result);
    }

    public synchronized Optional<PrismLiveCapability<?>> findCapability(String capabilityId) {
        PrismLiveComputationProvider<?> provider = providers.get(normalize(capabilityId, "capability id"));
        return provider == null ? Optional.empty() : Optional.of(provider.capability());
    }

    public synchronized List<PrismLiveBinding> bindings() {
        return List.copyOf(bindings.values());
    }

    public synchronized Optional<PrismLiveBinding> findBinding(String bindingId) {
        return Optional.ofNullable(bindings.get(normalize(bindingId, "binding id")));
    }

    public synchronized List<PrismLiveEvaluation> evaluationsFor(String resourceId) {
        String normalized = normalize(resourceId, "resource id");
        return evaluations.entrySet().stream()
                .filter(entry -> entry.getKey().resourceId().equals(normalized))
                .map(Map.Entry::getValue)
                .toList();
    }

    public synchronized Optional<PrismLiveEvaluation> findEvaluation(String bindingId, String resourceId) {
        return Optional.ofNullable(evaluations.get(new RootKey(
                normalize(bindingId, "binding id"),
                normalize(resourceId, "resource id")
        )));
    }

    public synchronized void registerProvider(PrismLiveComputationProvider<?> provider) {
        ensureOpen();
        PrismLiveComputationProvider<?> registered = Objects.requireNonNull(provider, "provider");
        String capabilityId = registered.capability().id();
        if (providers.putIfAbsent(capabilityId, registered) != null) {
            throw new IllegalArgumentException("live capability '" + capabilityId + "' is already registered");
        }
        if (registered.version() == null || registered.version().isBlank()) {
            providers.remove(capabilityId);
            throw new IllegalArgumentException("live provider version must not be blank");
        }
    }

    public synchronized PrismLiveBinding configureBinding(PrismLiveBinding binding) {
        ensureOpen();
        PrismLiveBinding next = Objects.requireNonNull(binding, "binding");
        PrismLiveComputationProvider<?> provider = requireProvider(next.capabilityId());
        if (!provider.capability().publishable()
                || !PrismLiveResult.class.isAssignableFrom(provider.capability().valueType())) {
            throw new IllegalArgumentException("live capability '" + next.capabilityId() + "' is not publishable");
        }
        PrismLiveBinding previous = bindings.put(next.id(), next);
        if (!next.equals(previous)) {
            clearBindingEvaluations(next.id());
            publish(PrismLiveContextChangeType.BINDINGS, next.id(), null);
            for (PrismMoleculeDocument document : documents()) {
                if (next.mode() == PrismLiveExecutionMode.AUTO) {
                    queue(next, new PrismMoleculeLiveInput(document), next.quietPeriod().toMillis());
                } else if (next.mode() == PrismLiveExecutionMode.DISABLED) {
                    markDisabled(next, document);
                }
            }
        }
        return next;
    }

    public synchronized void removeBinding(String bindingId) {
        ensureOpen();
        String id = normalize(bindingId, "binding id");
        if (bindings.remove(id) == null) return;
        clearBindingEvaluations(id);
        publish(PrismLiveContextChangeType.BINDINGS, id, null);
    }

    public synchronized PrismLiveEvaluation runNow(
            String bindingId,
            String documentId,
            Long expectedDocumentRevision
    ) {
        ensureOpen();
        PrismLiveBinding binding = requireBinding(bindingId);
        if (binding.mode() == PrismLiveExecutionMode.DISABLED) {
            throw new IllegalStateException("live evaluator '" + binding.id() + "' is disabled");
        }
        PrismMoleculeDocument document = molecules.findDocument(normalize(documentId, "document id"))
                .orElseThrow(() -> new IllegalArgumentException("unknown molecule document '" + documentId + "'"));
        if (expectedDocumentRevision != null && document.revision() != expectedDocumentRevision) {
            throw new IllegalStateException("molecule document '" + document.id() + "' is at revision "
                    + document.revision() + ", expected " + expectedDocumentRevision);
        }
        queue(binding, new PrismMoleculeLiveInput(document), 0);
        return evaluations.get(new RootKey(binding.id(), document.id()));
    }

    public PrismLiveContextSubscription subscribe(Consumer<PrismLiveContextChange> listener) {
        Consumer<PrismLiveContextChange> registered = Objects.requireNonNull(listener, "listener");
        listeners.add(registered);
        return () -> listeners.remove(registered);
    }

    private synchronized void moleculeChanged(PrismMoleculeWorkspaceChange change) {
        if (closed || change.documentId() == null) return;
        Optional<PrismMoleculeDocument> document = molecules.findDocument(change.documentId());
        if (document.isEmpty()) {
            removeResource(change.documentId());
            return;
        }
        PrismMoleculeLiveInput input = new PrismMoleculeLiveInput(document.orElseThrow());
        for (PrismLiveBinding binding : bindings.values()) {
            if (binding.mode() == PrismLiveExecutionMode.AUTO) {
                queue(binding, input, binding.quietPeriod().toMillis());
            } else if (binding.mode() == PrismLiveExecutionMode.DISABLED) {
                markDisabled(binding, document.orElseThrow());
            }
        }
    }

    private void queue(PrismLiveBinding binding, PrismMoleculeLiveInput input, long delayMillis) {
        RootKey root = new RootKey(binding.id(), input.resourceId());
        PrismLiveComputationProvider<?> provider = requireProvider(binding.capabilityId());
        long generation = generations.merge(root, 1L, Long::sum);
        cancelPending(root);
        PrismLiveEvaluation previous = evaluations.get(root);
        PrismLiveSuccessfulResult lastSuccessful = previous == null ? null : previous.lastSuccessful();
        if (!provider.supports(input)) {
            evaluations.put(root, new PrismLiveEvaluation(
                    binding.id(), input.resourceId(), input.revision(),
                    PrismLiveEvaluationStatus.UNSUPPORTED, Instant.now(), lastSuccessful,
                    "The evaluator does not support this molecule document."
            ));
            publish(PrismLiveContextChangeType.EVALUATION, binding.id(), input.resourceId());
            return;
        }
        evaluations.put(root, new PrismLiveEvaluation(
                binding.id(), input.resourceId(), input.revision(),
                PrismLiveEvaluationStatus.QUEUED, Instant.now(), lastSuccessful, ""
        ));
        publish(PrismLiveContextChangeType.EVALUATION, binding.id(), input.resourceId());
        ScheduledFuture<?> scheduled = environment.scheduler().schedule(
                () -> start(root, input, generation),
                Math.max(0, delayMillis),
                TimeUnit.MILLISECONDS
        );
        pending.put(root, scheduled);
    }

    private synchronized void start(RootKey root, PrismMoleculeLiveInput input, long generation) {
        if (closed || generations.getOrDefault(root, 0L) != generation) return;
        PrismLiveBinding binding = bindings.get(root.bindingId());
        if (binding == null || binding.mode() == PrismLiveExecutionMode.DISABLED) return;
        pending.remove(root);
        PrismLiveEvaluation previous = evaluations.get(root);
        PrismLiveSuccessfulResult lastSuccessful = previous == null ? null : previous.lastSuccessful();
        evaluations.put(root, new PrismLiveEvaluation(
                binding.id(), input.resourceId(), input.revision(),
                PrismLiveEvaluationStatus.RUNNING, Instant.now(), lastSuccessful, ""
        ));
        publish(PrismLiveContextChangeType.EVALUATION, binding.id(), input.resourceId());

        PrismLiveComputationProvider<?> provider = requireProvider(binding.capabilityId());
        @SuppressWarnings("unchecked")
        PrismLiveCapability<PrismLiveResult> capability =
                (PrismLiveCapability<PrismLiveResult>) provider.capability();
        resolve(capability, input, binding.configuration(), List.of())
                .whenComplete((result, failure) -> complete(root, input, generation, result, failure));
    }

    private synchronized void complete(
            RootKey root,
            PrismMoleculeLiveInput input,
            long generation,
            PrismLiveResult result,
            Throwable failure
    ) {
        if (closed || generations.getOrDefault(root, 0L) != generation) return;
        PrismLiveBinding binding = bindings.get(root.bindingId());
        if (binding == null) return;
        Optional<PrismMoleculeDocument> current = molecules.findDocument(input.resourceId());
        if (current.isEmpty() || current.orElseThrow().revision() != input.revision()) return;

        PrismLiveEvaluation previous = evaluations.get(root);
        PrismLiveSuccessfulResult lastSuccessful = previous == null ? null : previous.lastSuccessful();
        if (failure != null) {
            Throwable cause = unwrap(failure);
            evaluations.put(root, new PrismLiveEvaluation(
                    binding.id(), input.resourceId(), input.revision(),
                    PrismLiveEvaluationStatus.FAILED, Instant.now(), lastSuccessful,
                    cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()
            ));
        } else {
            PrismLiveSuccessfulResult success =
                    new PrismLiveSuccessfulResult(input.revision(), Instant.now(), result);
            evaluations.put(root, new PrismLiveEvaluation(
                    binding.id(), input.resourceId(), input.revision(),
                    PrismLiveEvaluationStatus.SUCCEEDED, Instant.now(), success, ""
            ));
        }
        publish(PrismLiveContextChangeType.EVALUATION, binding.id(), input.resourceId());
    }

    private void markDisabled(PrismLiveBinding binding, PrismMoleculeDocument document) {
        RootKey root = new RootKey(binding.id(), document.id());
        generations.merge(root, 1L, Long::sum);
        cancelPending(root);
        PrismLiveEvaluation previous = evaluations.get(root);
        evaluations.put(root, new PrismLiveEvaluation(
                binding.id(), document.id(), document.revision(),
                PrismLiveEvaluationStatus.DISABLED, Instant.now(),
                previous == null ? null : previous.lastSuccessful(), ""
        ));
        publish(PrismLiveContextChangeType.EVALUATION, binding.id(), document.id());
    }

    private synchronized <T> CompletionStage<T> resolve(
            PrismLiveCapability<T> capability,
            PrismLiveInput input,
            Map<String, Object> configuration,
            List<ComputationKey> path
    ) {
        @SuppressWarnings("unchecked")
        PrismLiveComputationProvider<T> provider =
                (PrismLiveComputationProvider<T>) requireProvider(capability.id());
        Map<String, Object> effectiveConfiguration =
                configuration == null ? Map.of() : Map.copyOf(configuration);
        if (!provider.supports(input)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("capability '" + capability.id() + "' does not support this input"));
        }
        ComputationKey key = new ComputationKey(
                capability.id(),
                provider.version().trim(),
                provider.fingerprint(input, effectiveConfiguration),
                PrismLiveFingerprints.configuration(effectiveConfiguration)
        );
        if (path.contains(key)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("live computation dependency cycle at '" + capability.id() + "'"));
        }
        if (completed.containsKey(key)) {
            return CompletableFuture.completedFuture(capability.valueType().cast(completed.get(key)));
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<T> existing = (CompletableFuture<T>) inFlight.get(key);
        if (existing != null) return existing;

        CompletableFuture<T> created = new CompletableFuture<>();
        @SuppressWarnings("unchecked")
        CompletableFuture<T> raced = (CompletableFuture<T>) inFlight.putIfAbsent(key, created);
        if (raced != null) return raced;

        ArrayList<ComputationKey> nextPath = new ArrayList<>(path);
        nextPath.add(key);
        environment.computationExecutor().execute(() -> {
            try {
                PrismLiveComputationContext context = new ComputationContext(
                        input, effectiveConfiguration, List.copyOf(nextPath));
                CompletionStage<T> stage = Objects.requireNonNull(
                        provider.compute(input, effectiveConfiguration, context),
                        "live provider returned null CompletionStage"
                );
                stage.whenComplete((value, failure) -> finishComputation(key, capability, created, value, failure));
            } catch (Throwable failure) {
                finishComputation(key, capability, created, null, failure);
            }
        });
        return created;
    }

    private <T> void finishComputation(
            ComputationKey key,
            PrismLiveCapability<T> capability,
            CompletableFuture<T> future,
            T value,
            Throwable failure
    ) {
        inFlight.remove(key, future);
        if (failure != null) {
            future.completeExceptionally(unwrap(failure));
            return;
        }
        if (value == null || !capability.valueType().isInstance(value)) {
            future.completeExceptionally(new IllegalStateException(
                    "live capability '" + capability.id() + "' returned an incompatible value"));
            return;
        }
        synchronized (this) {
            if (!closed) completed.put(key, value);
        }
        future.complete(value);
    }

    private synchronized void clearBindingEvaluations(String bindingId) {
        List<RootKey> roots = evaluations.keySet().stream()
                .filter(root -> root.bindingId().equals(bindingId))
                .toList();
        for (RootKey root : roots) {
            generations.merge(root, 1L, Long::sum);
            cancelPending(root);
            evaluations.remove(root);
        }
    }

    private synchronized void removeResource(String resourceId) {
        List<RootKey> roots = evaluations.keySet().stream()
                .filter(root -> root.resourceId().equals(resourceId))
                .toList();
        for (RootKey root : roots) {
            generations.merge(root, 1L, Long::sum);
            cancelPending(root);
            evaluations.remove(root);
            publish(PrismLiveContextChangeType.EVALUATION, root.bindingId(), resourceId);
        }
    }

    private void cancelPending(RootKey root) {
        ScheduledFuture<?> scheduled = pending.remove(root);
        if (scheduled != null) scheduled.cancel(false);
    }

    private List<PrismMoleculeDocument> documents() {
        return molecules.lists().stream()
                .map(PrismMoleculeList::documents)
                .flatMap(List::stream)
                .toList();
    }

    private PrismLiveBinding requireBinding(String bindingId) {
        String id = normalize(bindingId, "binding id");
        PrismLiveBinding binding = bindings.get(id);
        if (binding == null) throw new IllegalArgumentException("unknown live binding '" + id + "'");
        return binding;
    }

    private PrismLiveComputationProvider<?> requireProvider(String capabilityId) {
        String id = normalize(capabilityId, "capability id");
        PrismLiveComputationProvider<?> provider = providers.get(id);
        if (provider == null) throw new IllegalArgumentException("unknown live capability '" + id + "'");
        return provider;
    }

    private void publish(PrismLiveContextChangeType type, String bindingId, String resourceId) {
        PrismLiveContextChange change = new PrismLiveContextChange(++sequence, type, bindingId, resourceId);
        for (Consumer<PrismLiveContextChange> listener : listeners) {
            try {
                listener.accept(change);
            } catch (RuntimeException ignored) {
                // Observers cannot roll back an already completed live-state transition.
            }
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("live context is closed");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        moleculeSubscription.close();
        for (ScheduledFuture<?> scheduled : pending.values()) scheduled.cancel(false);
        pending.clear();
        for (CompletableFuture<?> future : inFlight.values()) future.cancel(false);
        inFlight.clear();
        completed.clear();
        listeners.clear();
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String normalize(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private record RootKey(String bindingId, String resourceId) {
    }

    private record ComputationKey(
            String capabilityId,
            String providerVersion,
            String inputFingerprint,
            String configurationFingerprint
    ) {
    }

    private final class ComputationContext implements PrismLiveComputationContext {
        private final PrismLiveInput input;
        private final Map<String, Object> configuration;
        private final List<ComputationKey> path;

        private ComputationContext(
                PrismLiveInput input,
                Map<String, Object> configuration,
                List<ComputationKey> path
        ) {
            this.input = input;
            this.configuration = configuration;
            this.path = path;
        }

        @Override
        public PrismLiveInput input() {
            return input;
        }

        @Override
        public Map<String, Object> configuration() {
            return configuration;
        }

        @Override
        public <T> CompletionStage<T> require(
                PrismLiveCapability<T> capability,
                Map<String, Object> configuration
        ) {
            return PrismLiveContext.this.resolve(capability, input, configuration, path);
        }
    }
}

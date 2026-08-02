package tech.molecules.structurized.prism.engine;

import tech.molecules.structurized.prism.engine.live.PrismLiveContext;
import tech.molecules.structurized.prism.engine.live.PrismLiveContextChangeType;
import tech.molecules.structurized.prism.engine.live.PrismLiveContextSubscription;
import tech.molecules.structurized.prism.engine.live.PrismLiveExecutionEnvironment;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PrismWorkspace implements AutoCloseable {
    private final String workspaceId;
    private final PrismSession session;
    private final PrismMoleculeWorkspace molecules;
    private final PrismLiveContext liveContext;
    private final PrismWorkspaceExecutor executor;
    private final CopyOnWriteArrayList<Consumer<PrismWorkspaceChange>> listeners = new CopyOnWriteArrayList<>();
    private final ThreadLocal<MutationScope> mutationScope = new ThreadLocal<>();
    private final PrismSessionSubscription sessionSubscription;
    private final PrismMoleculeWorkspaceSubscription moleculeSubscription;
    private final PrismLiveContextSubscription liveSubscription;
    private volatile long revision = 1;
    private volatile boolean closed;

    public PrismWorkspace(
            String workspaceId,
            PrismSession session,
            PrismWorkspaceExecutor executor,
            PrismLiveExecutionEnvironment liveEnvironment
    ) {
        this(workspaceId, session, new PrismMoleculeWorkspace(), executor, liveEnvironment, context -> {});
    }

    public PrismWorkspace(
            String workspaceId,
            PrismSession session,
            PrismMoleculeWorkspace molecules,
            PrismWorkspaceExecutor executor,
            PrismLiveExecutionEnvironment liveEnvironment
    ) {
        this(workspaceId, session, molecules, executor, liveEnvironment, context -> {});
    }

    public PrismWorkspace(
            String workspaceId,
            PrismSession session,
            PrismMoleculeWorkspace molecules,
            PrismWorkspaceExecutor executor,
            PrismLiveExecutionEnvironment liveEnvironment,
            Consumer<PrismLiveContext> liveInitializer
    ) {
        this.workspaceId = requireText(workspaceId, "workspace id");
        this.session = Objects.requireNonNull(session, "session");
        this.molecules = Objects.requireNonNull(molecules, "molecules");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.liveContext = new PrismLiveContext(molecules, Objects.requireNonNull(liveEnvironment, "liveEnvironment"));
        Objects.requireNonNull(liveInitializer, "liveInitializer").accept(liveContext);
        this.sessionSubscription = session.subscribe(this::sessionChanged);
        this.moleculeSubscription = molecules.subscribe(change -> recordChange(PrismWorkspaceChangeType.MOLECULES));
        this.liveSubscription = liveContext.subscribe(change -> {
            if (change.type() == PrismLiveContextChangeType.BINDINGS) {
                recordChange(PrismWorkspaceChangeType.LIVE_CONFIGURATION);
            }
        });
    }

    public String workspaceId() {
        return workspaceId;
    }

    public long revision() {
        return revision;
    }

    public PrismSession session() {
        return session;
    }

    public PrismMoleculeWorkspace molecules() {
        return molecules;
    }

    public PrismLiveContext liveContext() {
        return liveContext;
    }

    public PrismWorkspaceSubscription subscribe(Consumer<PrismWorkspaceChange> listener) {
        Consumer<PrismWorkspaceChange> registered = Objects.requireNonNull(listener, "listener");
        listeners.add(registered);
        return () -> listeners.remove(registered);
    }

    public void runAs(PrismWorkspaceChangeOrigin origin, Runnable action) {
        callAs(origin, null, () -> {
            action.run();
            return null;
        });
    }

    public void runAs(PrismWorkspaceChangeOrigin origin, Long expectedRevision, Runnable action) {
        callAs(origin, expectedRevision, () -> {
            action.run();
            return null;
        });
    }

    public <T> T callAs(PrismWorkspaceChangeOrigin origin, Supplier<T> action) {
        return callAs(origin, null, action);
    }

    public <T> T callAs(
            PrismWorkspaceChangeOrigin origin,
            Long expectedRevision,
            Supplier<T> action
    ) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(action, "action");
        ensureOpen();
        return executor.execute(() -> callDirect(origin, expectedRevision, action));
    }

    private <T> T callDirect(
            PrismWorkspaceChangeOrigin origin,
            Long expectedRevision,
            Supplier<T> action
    ) {
        MutationScope previous = mutationScope.get();
        if (previous != null) return action.get();
        if (expectedRevision != null && revision != expectedRevision) {
            throw new PrismWorkspaceRevisionConflictException(expectedRevision, revision);
        }
        MutationScope scope = new MutationScope();
        mutationScope.set(scope);
        try {
            return action.get();
        } finally {
            mutationScope.remove();
            if (scope.type != null) publish(scope.type, origin);
        }
    }

    private void sessionChanged(PrismSessionChange change) {
        PrismWorkspaceChangeType type = switch (change.type()) {
            case PROJECTION -> PrismWorkspaceChangeType.PROJECTION;
            case STRUCTURE -> PrismWorkspaceChangeType.STRUCTURE;
            case VIEWS -> PrismWorkspaceChangeType.VIEWS;
        };
        recordChange(type);
    }

    private void recordChange(PrismWorkspaceChangeType type) {
        MutationScope scope = mutationScope.get();
        if (scope != null) {
            scope.type = PrismWorkspaceChangeType.merge(scope.type, type);
            return;
        }
        publish(type, PrismWorkspaceChangeOrigin.LOCAL_UI);
    }

    private synchronized void publish(PrismWorkspaceChangeType type, PrismWorkspaceChangeOrigin origin) {
        if (closed) return;
        PrismWorkspaceChange change = new PrismWorkspaceChange(this, ++revision, type, origin);
        for (Consumer<PrismWorkspaceChange> listener : listeners) {
            try {
                listener.accept(change);
            } catch (RuntimeException ignored) {
                // Observers cannot roll back an already committed workspace mutation.
            }
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("workspace is closed");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        liveSubscription.close();
        moleculeSubscription.close();
        sessionSubscription.close();
        liveContext.close();
        listeners.clear();
        closed = true;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private static final class MutationScope {
        private PrismWorkspaceChangeType type;
    }
}

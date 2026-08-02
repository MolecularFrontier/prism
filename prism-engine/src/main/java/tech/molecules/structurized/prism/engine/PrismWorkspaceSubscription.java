package tech.molecules.structurized.prism.engine;

@FunctionalInterface
public interface PrismWorkspaceSubscription extends AutoCloseable {
    @Override
    void close();
}

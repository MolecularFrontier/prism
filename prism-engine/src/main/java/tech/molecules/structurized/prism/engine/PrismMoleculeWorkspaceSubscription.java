package tech.molecules.structurized.prism.engine;

@FunctionalInterface
public interface PrismMoleculeWorkspaceSubscription extends AutoCloseable {
    @Override
    void close();
}

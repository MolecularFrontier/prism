package tech.molecules.structurized.prism.engine;

@FunctionalInterface
public interface RowSelectionSubscription extends AutoCloseable {
    @Override
    void close();
}

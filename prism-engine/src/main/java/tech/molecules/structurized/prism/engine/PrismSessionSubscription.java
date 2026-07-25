package tech.molecules.structurized.prism.engine;

@FunctionalInterface
public interface PrismSessionSubscription extends AutoCloseable {
    @Override
    void close();
}

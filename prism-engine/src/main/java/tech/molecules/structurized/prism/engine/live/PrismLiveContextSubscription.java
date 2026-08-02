package tech.molecules.structurized.prism.engine.live;

@FunctionalInterface
public interface PrismLiveContextSubscription extends AutoCloseable {
    @Override
    void close();
}

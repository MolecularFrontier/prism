package tech.molecules.structurized.prism.engine.live;

public interface PrismLiveInput {
    String resourceType();

    String resourceId();

    long revision();

    Object snapshot();
}

package tech.molecules.structurized.prism.engine;

public interface PrismViewSerializer {
    String serialize(PrismViewState viewState);

    PrismViewState deserialize(String serialized);
}

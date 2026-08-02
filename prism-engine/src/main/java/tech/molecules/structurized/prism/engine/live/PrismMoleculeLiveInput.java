package tech.molecules.structurized.prism.engine.live;

import tech.molecules.structurized.prism.engine.PrismMoleculeDocument;

import java.util.Objects;

public record PrismMoleculeLiveInput(PrismMoleculeDocument document) implements PrismLiveInput {
    public static final String RESOURCE_TYPE = "molecule_document";

    public PrismMoleculeLiveInput {
        Objects.requireNonNull(document, "document");
    }

    @Override
    public String resourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public String resourceId() {
        return document.id();
    }

    @Override
    public long revision() {
        return document.revision();
    }

    @Override
    public Object snapshot() {
        return document;
    }
}

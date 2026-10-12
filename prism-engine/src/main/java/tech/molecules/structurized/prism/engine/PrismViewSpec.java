package tech.molecules.structurized.prism.engine;

import java.util.Set;

public interface PrismViewSpec {
    String viewId();

    String viewType();

    String title();

    Set<String> referencedRowSetIds();

    Set<String> referencedColumnIds();

    default PrismViewSpec copyWithIdentity(String viewId, String title) {
        throw new UnsupportedOperationException("view type does not support identity copying: " + viewType());
    }
}

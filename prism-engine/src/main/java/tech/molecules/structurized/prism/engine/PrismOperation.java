package tech.molecules.structurized.prism.engine;

import java.util.Map;

public interface PrismOperation {
    PrismOperationDescriptor descriptor();

    PrismOperationResult execute(PrismSessionSnapshot snapshot, Map<String, Object> parameters);
}

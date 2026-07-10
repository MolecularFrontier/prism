package tech.molecules.structurized.prism.engine;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PrismOperationRegistry {
    private final Map<String, PrismOperation> operations = new LinkedHashMap<>();

    public void register(PrismOperation operation) {
        Objects.requireNonNull(operation, "operation");
        String id = operation.descriptor().id();
        if (operations.containsKey(id)) {
            throw new IllegalArgumentException("operation already registered: " + id);
        }
        operations.put(id, operation);
    }

    public Collection<PrismOperationDescriptor> descriptors() {
        return operations.values().stream().map(PrismOperation::descriptor).toList();
    }

    public List<String> operationIds() {
        return List.copyOf(operations.keySet());
    }

    public PrismOperation operation(String id) {
        PrismOperation operation = operations.get(id);
        if (operation == null) {
            throw new PrismOperationException("UNKNOWN_OPERATION", "unknown operation '" + id + "'");
        }
        return operation;
    }

    public PrismOperationResult run(String id, PrismSessionSnapshot snapshot, Map<String, Object> parameters) {
        PrismOperation operation = operation(id);
        Map<String, Object> boundParameters = PrismOperationParameterBinder.bind(
                operation.descriptor(),
                snapshot,
                parameters == null ? Map.of() : parameters
        );
        try {
            return operation.execute(snapshot, boundParameters);
        } catch (PrismOperationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new PrismOperationException("OPERATION_FAILED", exception.getMessage(), null, Map.of(), exception);
        }
    }
}

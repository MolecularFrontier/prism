package tech.molecules.structurized.prism.engine;

import java.util.function.Supplier;

@FunctionalInterface
public interface PrismWorkspaceExecutor {
    <T> T execute(Supplier<T> action);

    static PrismWorkspaceExecutor direct() {
        return new PrismWorkspaceExecutor() {
            @Override
            public <T> T execute(Supplier<T> action) {
                return action.get();
            }
        };
    }
}

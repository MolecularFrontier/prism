package tech.molecules.structurized.prismlite.swing.operations;

import tech.molecules.structurized.prism.engine.PrismOperationDescriptor;
import tech.molecules.structurized.prism.engine.PrismSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PrismOperationFormRegistry {
    private final List<PrismOperationFormProvider> providers = new ArrayList<>();

    public static PrismOperationFormRegistry defaults() {
        PrismOperationFormRegistry registry = new PrismOperationFormRegistry();
        registry.register(new StructureGridOperationFormProvider());
        return registry;
    }

    public void register(PrismOperationFormProvider provider) {
        providers.add(provider);
    }

    public PrismOperationForm createForm(PrismSession session, PrismOperationDescriptor descriptor, PrismOperationLaunchContext context) {
        Optional<PrismOperationFormProvider> provider = providers.stream()
                .filter(candidate -> candidate.supports(descriptor))
                .findFirst();
        return provider
                .map(value -> value.createForm(session, descriptor, context))
                .orElseGet(() -> new GenericPrismOperationForm(session, descriptor));
    }
}

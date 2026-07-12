package tech.molecules.structurized.prismlite.swing.operations;

import tech.molecules.structurized.prism.engine.PrismOperationDescriptor;
import tech.molecules.structurized.prism.engine.PrismSession;

public interface PrismOperationFormProvider {
    boolean supports(PrismOperationDescriptor descriptor);

    PrismOperationForm createForm(
            PrismSession session,
            PrismOperationDescriptor descriptor,
            PrismOperationLaunchContext context
    );
}

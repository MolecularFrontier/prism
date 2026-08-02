package tech.molecules.structurized.prism.engine.live;

public record PrismLiveContextChange(
        long sequence,
        PrismLiveContextChangeType type,
        String bindingId,
        String resourceId
) {
}

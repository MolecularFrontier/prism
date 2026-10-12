package tech.molecules.structurized.prism.engine.snapshot;

public record PrismSnapshotCapabilities(
        EndpointResultFidelity endpointResultFidelity,
        boolean endpointDefinitions,
        boolean rowSets,
        boolean scoreDefinitions,
        boolean supportingMeasurements,
        boolean reloadFromSource
) {}

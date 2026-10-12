package tech.molecules.structurized.prism.engine.snapshot;

import tech.molecules.structurized.prism.result.EndpointResult;

import java.util.Optional;

public record PrismEndpointCell(String rowId, String endpointId, String displayValue,
                                Optional<EndpointResult> result, EndpointResultFidelity fidelity) {
    public PrismEndpointCell { result = result == null ? Optional.empty() : result; }
}

package tech.molecules.structurized.prism.score;

import java.util.List;

public record MpoEvaluation(
        String mpoId,
        Double score,
        double coverage,
        int availableCount,
        int missingCount,
        int hardFailCount,
        int requiredMissingCount,
        MpoStatus status,
        List<MpoComponentEvaluation> components
) {
    public MpoEvaluation {
        components = components == null ? List.of() : List.copyOf(components);
    }
}

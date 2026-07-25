package tech.molecules.structurized.prism.score;

public record MpoComponentEvaluation(
        MpoComponentDefinition definition,
        EndpointScoreEvaluation score,
        boolean hardFail
) {
    public boolean available() {
        return score != null && score.available();
    }
}

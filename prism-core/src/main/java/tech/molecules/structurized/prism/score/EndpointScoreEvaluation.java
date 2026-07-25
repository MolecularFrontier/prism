package tech.molecules.structurized.prism.score;

public record EndpointScoreEvaluation(
        String scoreId,
        String endpointId,
        Double inputValue,
        Double score,
        String state,
        String message
) {
    public boolean available() {
        return score != null && Double.isFinite(score);
    }
}

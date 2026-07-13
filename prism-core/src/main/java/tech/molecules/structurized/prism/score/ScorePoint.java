package tech.molecules.structurized.prism.score;

public record ScorePoint(double x, double score) {
    public ScorePoint {
        if (!Double.isFinite(x)) {
            throw new IllegalArgumentException("score point x must be finite");
        }
        if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score point score must be between 0 and 1");
        }
    }
}

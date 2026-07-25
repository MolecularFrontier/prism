package tech.molecules.structurized.prism.score;

public record MpoAggregationDefinition(String type, String missing, double warningCoverageBelow) {
    public static final String WEIGHTED_MEAN = "weighted_mean";
    public static final String IGNORE = "ignore";

    public MpoAggregationDefinition {
        type = type == null || type.isBlank() ? WEIGHTED_MEAN : type.trim().toLowerCase();
        missing = missing == null || missing.isBlank() ? IGNORE : missing.trim().toLowerCase();
        if (!WEIGHTED_MEAN.equals(type)) {
            throw new IllegalArgumentException("only weighted_mean MPO aggregation is supported");
        }
        if (!IGNORE.equals(missing)) {
            throw new IllegalArgumentException("only missing=ignore MPO aggregation is supported");
        }
        if (!Double.isFinite(warningCoverageBelow) || warningCoverageBelow < 0.0 || warningCoverageBelow > 1.0) {
            throw new IllegalArgumentException("warningCoverageBelow must be between 0 and 1");
        }
    }

    public static MpoAggregationDefinition defaults() {
        return new MpoAggregationDefinition(WEIGHTED_MEAN, IGNORE, 0.5);
    }
}

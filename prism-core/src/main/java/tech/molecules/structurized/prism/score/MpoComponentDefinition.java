package tech.molecules.structurized.prism.score;

public record MpoComponentDefinition(
        String endpointId,
        String scoreId,
        String label,
        double weight,
        boolean required,
        Double hardFailBelow,
        Double hardFailValueAtOrBelow,
        Double hardFailValueAtOrAbove
) {
    public MpoComponentDefinition(String endpointId,
                                  String scoreId,
                                  String label,
                                  double weight,
                                  boolean required,
                                  Double hardFailBelow) {
        this(endpointId, scoreId, label, weight, required, hardFailBelow, null, null);
    }

    public MpoComponentDefinition {
        if (endpointId == null || endpointId.isBlank()) {
            throw new IllegalArgumentException("MPO component endpointId must not be blank");
        }
        if (scoreId == null || scoreId.isBlank()) {
            throw new IllegalArgumentException("MPO component scoreId must not be blank");
        }
        endpointId = endpointId.trim();
        scoreId = scoreId.trim();
        label = label == null || label.isBlank() ? endpointId : label.trim();
        if (!Double.isFinite(weight) || weight < 0.0) {
            throw new IllegalArgumentException("MPO component weight must be finite and >= 0");
        }
        if (hardFailBelow != null && (!Double.isFinite(hardFailBelow) || hardFailBelow < 0.0 || hardFailBelow > 1.0)) {
            throw new IllegalArgumentException("MPO hardFailBelow must be between 0 and 1");
        }
        if (hardFailValueAtOrBelow != null && !Double.isFinite(hardFailValueAtOrBelow)) {
            throw new IllegalArgumentException("MPO hardFailValueAtOrBelow must be finite");
        }
        if (hardFailValueAtOrAbove != null && !Double.isFinite(hardFailValueAtOrAbove)) {
            throw new IllegalArgumentException("MPO hardFailValueAtOrAbove must be finite");
        }
        if (hardFailValueAtOrBelow != null && hardFailValueAtOrAbove != null
                && hardFailValueAtOrBelow >= hardFailValueAtOrAbove) {
            throw new IllegalArgumentException("MPO raw hard-fail lower bound must be below the upper bound");
        }
    }
}

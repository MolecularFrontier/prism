package tech.molecules.structurized.prism.score;

public record MpoComponentDefinition(
        String endpointId,
        String scoreId,
        String label,
        double weight,
        boolean required,
        Double hardFailBelow
) {
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
    }
}

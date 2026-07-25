package tech.molecules.structurized.prism.score;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record EndpointScoreDefinition(
        String id,
        String endpointId,
        String displayName,
        String description,
        String scoreType,
        String xScale,
        boolean clampOutsideRange,
        List<ScorePoint> points,
        Map<String, Object> metadata
) {
    public static final String LINE_SEGMENT_V1 = "line_segment_v1";
    public static final String LINEAR = "linear";
    public static final String LOG10 = "log10";

    public EndpointScoreDefinition {
        id = requireText(id, "score id");
        endpointId = requireText(endpointId, "score endpointId");
        displayName = normalize(displayName) == null ? id : displayName.trim();
        description = normalize(description);
        scoreType = normalize(scoreType) == null ? LINE_SEGMENT_V1 : scoreType.trim().toLowerCase(Locale.ROOT);
        xScale = normalize(xScale) == null ? LINEAR : xScale.trim().toLowerCase(Locale.ROOT);
        if (!LINE_SEGMENT_V1.equals(scoreType)) {
            throw new IllegalArgumentException("unsupported score type: " + scoreType);
        }
        if (!LINEAR.equals(xScale) && !LOG10.equals(xScale)) {
            throw new IllegalArgumentException("unsupported score xScale: " + xScale);
        }
        ArrayList<ScorePoint> sorted = new ArrayList<>(points == null ? List.of() : points);
        sorted.sort(Comparator.comparingDouble(ScorePoint::x));
        if (sorted.size() < 2) {
            throw new IllegalArgumentException("line segment score requires at least two points");
        }
        for (int i = 0; i < sorted.size(); i++) {
            ScorePoint point = sorted.get(i);
            if (LOG10.equals(xScale) && point.x() <= 0.0) {
                throw new IllegalArgumentException("score point x must be > 0 for log10 interpolation");
            }
            if (i > 0 && Double.compare(sorted.get(i - 1).x(), point.x()) == 0) {
                throw new IllegalArgumentException("score point x values must be unique");
            }
        }
        points = List.copyOf(sorted);
        metadata = metadata == null || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public String fingerprint() {
        StringBuilder canonical = new StringBuilder(id).append('\n')
                .append(endpointId).append('\n')
                .append(scoreType).append('\n')
                .append(xScale).append('\n')
                .append(clampOutsideRange).append('\n');
        for (ScorePoint point : points) {
            canonical.append(Double.toHexString(point.x())).append(':')
                    .append(Double.toHexString(point.score())).append('\n');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

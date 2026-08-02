package tech.molecules.structurized.prism.engine.live;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

final class PrismLiveFingerprints {
    private PrismLiveFingerprints() {
    }

    static String configuration(Map<String, Object> configuration) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, configuration);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void append(StringBuilder target, Object value) {
        if (value == null) {
            target.append("null");
        } else if (value instanceof Map<?, ?> map) {
            target.append('{');
            ArrayList<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
            entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
            for (Map.Entry<?, ?> entry : entries) {
                append(target, String.valueOf(entry.getKey()));
                target.append(':');
                append(target, entry.getValue());
                target.append(';');
            }
            target.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            target.append('[');
            for (Object item : iterable) {
                append(target, item);
                target.append(';');
            }
            target.append(']');
        } else if (value.getClass().isArray()) {
            target.append('[');
            for (int index = 0; index < Array.getLength(value); index++) {
                append(target, Array.get(value, index));
                target.append(';');
            }
            target.append(']');
        } else if (value instanceof Enum<?> enumeration) {
            target.append(enumeration.getDeclaringClass().getName())
                    .append(':')
                    .append(enumeration.name());
        } else {
            target.append(value.getClass().getName())
                    .append(':')
                    .append(value);
        }
    }
}

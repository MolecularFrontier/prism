package tech.molecules.structurized.prism.engine.ocl;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Stable text encoding used by sparse materialized SAR dimension columns. */
public final class SarSubstituentCodec {
    public static final String SEMANTIC_TYPE = "sar_substituent";
    public static final String ENCODING = "sar_substituent_v1";

    private static final String SUBSTITUENT_PREFIX = "sub:";

    private SarSubstituentCodec() {
    }

    public static String substituent(String idcode) {
        if (idcode == null || idcode.isBlank()) throw new IllegalArgumentException("fragment idcode must not be blank");
        return SUBSTITUENT_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(idcode.getBytes(StandardCharsets.UTF_8));
    }

    public static String unsubstituted() { return "none"; }

    public static String multiAttachment() { return "multi"; }

    public static String ambiguous() { return "ambiguous"; }

    public static String unmatched() { return "unmatched"; }

    public static SarSubstituent decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return new SarSubstituent(SarSubstituent.Type.UNSUBSTITUTED, "none", "H", null);
        }
        String value = encoded.trim();
        return switch (value) {
            case "none" -> new SarSubstituent(SarSubstituent.Type.UNSUBSTITUTED, value, "H", null);
            case "multi" -> new SarSubstituent(SarSubstituent.Type.MULTI_ATTACHMENT, value, "[multi-attachment]", null);
            case "ambiguous" -> new SarSubstituent(SarSubstituent.Type.AMBIGUOUS, value, "[ambiguous]", null);
            case "unmatched" -> new SarSubstituent(SarSubstituent.Type.UNMATCHED, value, "[unmatched]", null);
            default -> decodeValue(value);
        };
    }

    private static SarSubstituent decodeValue(String value) {
        if (!value.startsWith(SUBSTITUENT_PREFIX)) {
            return new SarSubstituent(SarSubstituent.Type.LABEL, "label:" + value, value, null);
        }
        try {
            String idcode = new String(Base64.getUrlDecoder().decode(value.substring(SUBSTITUENT_PREFIX.length())),
                    StandardCharsets.UTF_8);
            return new SarSubstituent(SarSubstituent.Type.SUBSTITUENT, value, "[substituent]", idcode);
        } catch (IllegalArgumentException exception) {
            return new SarSubstituent(SarSubstituent.Type.LABEL, "label:" + value, value, null);
        }
    }
}

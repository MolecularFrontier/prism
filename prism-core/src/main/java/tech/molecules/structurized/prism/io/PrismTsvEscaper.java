package tech.molecules.structurized.prism.io;

/**
 * Escapes and unescapes PRISM TSV cell values while keeping every record on one physical line.
 */
public final class PrismTsvEscaper {
    private PrismTsvEscaper() {}

    /**
     * Escapes characters that would otherwise break line-oriented TSV parsing.
     *
     * @param value raw cell value, may be null
     * @return escaped value, or an empty string for null
     */
    public static String escapeCell(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '\t' -> escaped.append("\\t");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                default -> escaped.append(ch);
            }
        }
        return escaped.toString();
    }

    /**
     * Reverses {@link #escapeCell(String)}. Unknown escape sequences are preserved literally.
     *
     * @param value escaped cell value, may be null
     * @return unescaped value, or null for null
     */
    public static String unescapeCell(String value) {
        if (value == null || value.indexOf('\\') < 0) {
            return value;
        }
        StringBuilder unescaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch != '\\' || i + 1 >= value.length()) {
                unescaped.append(ch);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case '\\' -> unescaped.append('\\');
                case 't' -> unescaped.append('\t');
                case 'n' -> unescaped.append('\n');
                case 'r' -> unescaped.append('\r');
                default -> unescaped.append('\\').append(next);
            }
        }
        return unescaped.toString();
    }
}

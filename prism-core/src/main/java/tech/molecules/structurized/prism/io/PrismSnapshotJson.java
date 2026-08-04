package tech.molecules.structurized.prism.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PrismSnapshotJson {
    private PrismSnapshotJson() {}

    static Object parse(String json) {
        return new Parser(json).parse();
    }

    static String stringify(Object value) {
        StringBuilder builder = new StringBuilder();
        writeValue(builder, value, 0);
        builder.append('\n');
        return builder.toString();
    }

    private static void writeValue(StringBuilder builder, Object value, int indent) {
        if (value == null) builder.append("null");
        else if (value instanceof String string) writeString(builder, string);
        else if (value instanceof Number || value instanceof Boolean) builder.append(value);
        else if (value instanceof Map<?, ?> map) writeObject(builder, map, indent);
        else if (value instanceof Iterable<?> iterable) writeArray(builder, iterable, indent);
        else writeString(builder, String.valueOf(value));
    }

    private static void writeObject(StringBuilder builder, Map<?, ?> map, int indent) {
        builder.append('{');
        int next = indent + 2;
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) continue;
            if (!first) builder.append(',');
            first = false;
            builder.append('\n').append(" ".repeat(next));
            writeString(builder, String.valueOf(entry.getKey()));
            builder.append(": ");
            writeValue(builder, entry.getValue(), next);
        }
        if (!first) builder.append('\n').append(" ".repeat(indent));
        builder.append('}');
    }

    private static void writeArray(StringBuilder builder, Iterable<?> values, int indent) {
        builder.append('[');
        int next = indent + 2;
        boolean first = true;
        for (Object value : values) {
            if (!first) builder.append(',');
            first = false;
            builder.append('\n').append(" ".repeat(next));
            writeValue(builder, value, next);
        }
        if (!first) builder.append('\n').append(" ".repeat(indent));
        builder.append(']');
    }

    private static void writeString(StringBuilder builder, String value) {
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) builder.append(String.format("\\u%04x", (int) ch));
                    else builder.append(ch);
                }
            }
        }
        builder.append('"');
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text == null ? "" : text;
        }

        private Object parse() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (index != text.length()) throw error("unexpected trailing content");
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= text.length()) throw error("unexpected end of JSON");
            char ch = text.charAt(index);
            return switch (ch) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> {
                    if (ch == '-' || Character.isDigit(ch)) yield parseNumber();
                    throw error("unexpected character '" + ch + "'");
                }
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) { index++; return result; }
            while (true) {
                String key = parseString();
                expect(':');
                result.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) { index++; return result; }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            ArrayList<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) { index++; return result; }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                if (peek(']')) { index++; return result; }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < text.length()) {
                char ch = text.charAt(index++);
                if (ch == '"') return result.toString();
                if (ch != '\\') { result.append(ch); continue; }
                if (index >= text.length()) throw error("unterminated escape sequence");
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    case '/' -> result.append('/');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> result.append(parseUnicode());
                    default -> throw error("unsupported escape sequence");
                }
            }
            throw error("unterminated string");
        }

        private char parseUnicode() {
            if (index + 4 > text.length()) throw error("incomplete unicode escape");
            String value = text.substring(index, index + 4);
            index += 4;
            try { return (char) Integer.parseInt(value, 16); }
            catch (NumberFormatException exception) { throw error("invalid unicode escape"); }
        }

        private Object parseNumber() {
            int start = index;
            if (peek('-')) index++;
            consumeDigits();
            boolean decimal = false;
            if (peek('.')) { decimal = true; index++; consumeDigits(); }
            if (peek('e') || peek('E')) {
                decimal = true; index++;
                if (peek('+') || peek('-')) index++;
                consumeDigits();
            }
            String value = text.substring(start, index);
            try {
                if (decimal) return Double.parseDouble(value);
                return Long.parseLong(value);
            }
            catch (NumberFormatException exception) { throw error("invalid number '" + value + "'"); }
        }

        private Object parseLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index)) throw error("expected '" + literal + "'");
            index += literal.length();
            return value;
        }

        private void consumeDigits() {
            int start = index;
            while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
            if (start == index) throw error("expected digit");
        }

        private void expect(char expected) {
            skipWhitespace();
            if (!peek(expected)) throw error("expected '" + expected + "'");
            index++;
        }

        private boolean peek(char value) { return index < text.length() && text.charAt(index) == value; }
        private void skipWhitespace() { while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++; }
        private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at JSON offset " + index); }
    }
}

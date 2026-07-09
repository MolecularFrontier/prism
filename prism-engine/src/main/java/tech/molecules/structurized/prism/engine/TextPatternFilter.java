package tech.molecules.structurized.prism.engine;

import java.util.BitSet;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class TextPatternFilter extends ColumnFilter {
    private final String patternText;
    private final TextPatternMode mode;
    private final boolean caseInsensitive;
    private final boolean includeMissing;
    private final Pattern regex;

    public TextPatternFilter(String columnId,
                             String patternText,
                             TextPatternMode mode,
                             boolean caseInsensitive,
                             boolean includeMissing) {
        super(columnId);
        this.patternText = patternText == null ? "" : patternText;
        this.mode = mode == null ? TextPatternMode.SUBSTRING : mode;
        this.caseInsensitive = caseInsensitive;
        this.includeMissing = includeMissing;
        this.regex = compileRegex(this.patternText, this.mode, caseInsensitive);
    }

    public String patternText() {
        return patternText;
    }

    public TextPatternMode mode() {
        return mode;
    }

    public boolean caseInsensitive() {
        return caseInsensitive;
    }

    public boolean includeMissing() {
        return includeMissing;
    }

    @Override
    public BitSet evaluate(PrismTable table, PrismEvaluationContext context) {
        PrismColumn column = table.column(columnId());
        BitSet result = new BitSet(table.rowCount());
        String needle = normalize(patternText);
        for (int row = 0; row < table.rowCount(); row++) {
            if (column.isMissing(row)) {
                if (includeMissing) {
                    result.set(row);
                }
                continue;
            }
            String value = column.formattedValueAt(row);
            boolean matches = mode == TextPatternMode.REGEX
                    ? regex.matcher(value).find()
                    : normalize(value).contains(needle);
            if (matches) {
                result.set(row);
            }
        }
        return result;
    }

    private String normalize(String value) {
        return caseInsensitive ? value.toLowerCase(Locale.ROOT) : value;
    }

    private static Pattern compileRegex(String patternText, TextPatternMode mode, boolean caseInsensitive) {
        if (mode != TextPatternMode.REGEX) {
            return null;
        }
        try {
            return Pattern.compile(patternText, caseInsensitive ? Pattern.CASE_INSENSITIVE : 0);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException("invalid regular expression: " + patternText, exception);
        }
    }
}

package tech.molecules.structurized.prism.report;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds actionable text for the diagnostics a report author actually hits.
 *
 * <p>The failures worth explaining are not syntax errors - those are already obvious - but the two
 * cases where a report is well-formed and still wrong:
 *
 * <ul>
 *   <li>a column name that does not resolve because the <em>analysis that materializes it</em> was
 *       never run in this session, which looks identical to a typo unless the message says so;</li>
 *   <li>a SAR column referenced over a row set whose compounds the scaffold never matched, which
 *       renders as a table of {@code unmatched} with no error at all.</li>
 * </ul>
 */
public final class PrismReportHints {
    private PrismReportHints() {
    }

    /** Row-set suffix created by scaffold-SAR materialization, used to check coverage. */
    private static final String MATCHED_ROW_SET_SUFFIX = ".matched";

    /**
     * Describes an unresolved identifier, adding a nearest-name suggestion and - for namespaced
     * analysis columns - the likely real cause.
     */
    public static String unknown(String kind, String value, List<String> candidates) {
        StringBuilder message = new StringBuilder("Unknown " + kind + ": " + value + ".");
        String closest = closest(value, candidates);
        if (closest != null) message.append(" Did you mean: ").append(closest).append("?");

        // A dotted id such as sar.macro.R_cap is materialized by an analysis. If nothing at all
        // exists under that namespace, the analysis is missing rather than the name misspelled -
        // a distinction that otherwise costs a long debugging detour.
        String namespace = namespaceOf(value);
        if (namespace != null && candidates.stream().noneMatch(item -> item.startsWith(namespace + "."))) {
            message.append(" No ").append(kind).append("s exist under '").append(namespace)
                    .append("', so the analysis that materializes them has probably not been run in"
                            + " this session (for scaffold SAR, materialize_prism_scaffold_analysis"
                            + " with output_prefix '").append(namespace).append("').");
        }
        return message.toString();
    }

    /**
     * Warns when a SAR column is shown over rows the scaffold never matched.
     *
     * <p>Compared by row ID, so no physical-row mapping is needed: the materialized
     * {@code <prefix>.matched} row set records exactly which rows carry substituents.
     *
     * @return the warning text, or empty when coverage is complete or not checkable
     */
    public static Optional<String> sarCoverage(PrismSession session, String columnId, String rowSetId) {
        if (rowSetId == null || columnId == null) return Optional.empty();
        PrismColumn column = session.table().findColumn(columnId).orElse(null);
        if (column == null || !isSarSubstituent(column)) return Optional.empty();

        String namespace = namespaceOf(columnId);
        if (namespace == null) return Optional.empty();

        Set<String> matched = rowIds(session, namespace + MATCHED_ROW_SET_SUFFIX);
        Set<String> shown = rowIds(session, rowSetId);
        if (matched.isEmpty() || shown.isEmpty()) return Optional.empty();

        TreeSet<String> outside = new TreeSet<>(shown);
        outside.removeAll(matched);
        if (outside.isEmpty()) return Optional.empty();

        return Optional.of(outside.size() + " of " + shown.size() + " rows in row set '" + rowSetId
                + "' are outside '" + namespace + MATCHED_ROW_SET_SUFFIX + "', so column '" + columnId
                + "' will render as unmatched for them. Restrict the block to '"
                + namespace + MATCHED_ROW_SET_SUFFIX + "', or drop the column from this block.");
    }

    /**
     * Suggests a concrete aggregation when {@code best} cannot be resolved.
     *
     * <p>Deliberately a <em>suggestion in the message</em> rather than an inference applied for the
     * author. Direction is guessed from the column name, and guessing wrong silently inverts which
     * substituent a SAR table calls best - a wrong answer that looks like a right one. Naming the
     * likely choice costs the author one word; applying it could cost them a conclusion.
     */
    public static String directionAdvice(String columnId) {
        String lower = columnId == null ? "" : columnId.toLowerCase(Locale.ROOT);
        boolean lowerIsBetter = lower.contains("ic50") && !lower.startsWith("pic50") && !lower.contains(".pic50")
                || lower.contains("ec50") && !lower.contains("pec50")
                || lower.contains("clint") || lower.contains("clearance")
                || lower.contains("ki") && !lower.contains("pki")
                || lower.contains("herg") || lower.contains("cyp");
        boolean higherIsBetter = lower.startsWith("p") && (lower.contains("ic50") || lower.contains("ki"))
                || lower.contains("lipe") || lower.contains("ligand_efficiency")
                || lower.contains("solubility") || lower.contains("permeab")
                || lower.contains("inhibition") || lower.contains("score");

        if (lowerIsBetter && !higherIsBetter) {
            return " Use aggregation 'min' if lower values are better for this endpoint.";
        }
        if (higherIsBetter && !lowerIsBetter) {
            return " Use aggregation 'max' if higher values are better for this endpoint.";
        }
        return " Use aggregation 'min' or 'max' to state the direction explicitly.";
    }

    /** True when the column was materialized as a scaffold-SAR substituent. */
    public static boolean isSarSubstituent(PrismColumn column) {
        if (column == null || column.schema() == null) return false;
        if ("sar_substituent".equals(column.schema().semanticType())) return true;
        return column.schema().raw() != null && column.schema().raw().containsKey("sarAnalysisId");
    }

    private static Set<String> rowIds(PrismSession session, String rowSetId) {
        try {
            PrismRowSet rowSet = session.rowSet(rowSetId);
            return rowSet == null || rowSet.rowIds() == null ? Set.of() : rowSet.rowIds();
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Set.of();
        }
    }

    /** Everything before the last dot, or null when the id is not namespaced. */
    private static String namespaceOf(String value) {
        int lastDot = value == null ? -1 : value.lastIndexOf('.');
        return lastDot <= 0 ? null : value.substring(0, lastDot);
    }

    private static String closest(String value, List<String> candidates) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = distance(value.toLowerCase(Locale.ROOT), candidate.toLowerCase(Locale.ROOT));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        // Only suggest a genuinely near name; an unrelated nearest match is worse than silence.
        int tolerance = Math.max(2, value.length() / 3);
        return bestDistance <= tolerance ? best : null;
    }

    private static int distance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitution = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}

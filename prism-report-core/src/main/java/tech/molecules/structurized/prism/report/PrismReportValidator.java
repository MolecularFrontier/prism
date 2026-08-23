package tech.molecules.structurized.prism.report;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.ocl.CompoundTableColumnSpec;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PrismReportValidator {
    private final PrismReportBlockRegistry blockRegistry;

    public PrismReportValidator() {
        this(PrismReportBlockRegistry.defaults());
    }

    public PrismReportValidator(PrismReportBlockRegistry blockRegistry) {
        this.blockRegistry = blockRegistry;
    }

    public List<PrismReportDiagnostic> validate(PrismReportDocument document, PrismSession session) {
        ArrayList<PrismReportDiagnostic> result = new ArrayList<>(document.diagnostics());
        validateMetadata(document.metadata(), result);
        HashSet<String> blockIds = new HashSet<>();
        for (PrismReportBlock block : document.blocks()) {
            if (block instanceof EmbeddedPrismViewReportBlock embedded) {
                if (!blockIds.add(embedded.blockId())) {
                    result.add(error("DUPLICATE_BLOCK_ID", "Duplicate block id '" + embedded.blockId() + "'.",
                            embedded.blockId(), embedded.sourceLine()));
                }
                blockRegistry.validate(block, session, result);
            }
        }
        return List.copyOf(result);
    }

    public boolean isValid(PrismReportDocument document, PrismSession session) {
        return validate(document, session).stream().noneMatch(item -> item.severity() == PrismReportSeverity.ERROR);
    }

    private static void validateMetadata(PrismReportMetadata metadata, List<PrismReportDiagnostic> diagnostics) {
        if (metadata.prismReportVersion() != 1) {
            diagnostics.add(error("UNSUPPORTED_REPORT_VERSION",
                    metadata.prismReportVersion() < 0
                            ? "Missing required front-matter field 'prismReportVersion'."
                            : "Unsupported prismReportVersion " + metadata.prismReportVersion() + "; expected 1.",
                    null, 1));
        }
        if (!"current".equals(metadata.dataset())) {
            diagnostics.add(error("UNSUPPORTED_DATASET",
                    metadata.dataset() == null
                            ? "Missing required front-matter field 'dataset'."
                            : "Slice one supports only dataset: current.",
                    null, 1));
        }
        if (metadata.title() == null || metadata.title().isBlank()) {
            diagnostics.add(error("MISSING_REPORT_TITLE", "Missing required front-matter field 'title'.", null, 1));
        }
        if (metadata.createdAt() != null) {
            try {
                Instant.parse(metadata.createdAt());
            } catch (DateTimeParseException exception) {
                diagnostics.add(error("INVALID_CREATED_AT", "createdAt must be an ISO-8601 instant.", null, 1));
            }
        }
    }

    private static void validateTable(
            CompoundTableReportBlock block,
            PrismSession session,
            Set<String> blockIds,
            List<PrismReportDiagnostic> diagnostics
    ) {
        var spec = block.specification();
        if (!blockIds.add(block.blockId())) {
            diagnostics.add(error("DUPLICATE_BLOCK_ID", "Duplicate block id '" + block.blockId() + "'.",
                    block.blockId(), block.sourceLine()));
        }
        try {
            session.rowSet(spec.rowSetId());
        } catch (IllegalArgumentException exception) {
            diagnostics.add(error("UNKNOWN_ROW_SET",
                    unknownWithSuggestion("row set", spec.rowSetId(),
                            session.rowSets().stream().map(item -> item.id()).toList()),
                    block.blockId(), block.sourceLine()));
        }
        PrismColumn structure = findColumn(session, spec.structureColumnId(), block, diagnostics);
        if (structure != null && structure.type() != PrismColumnType.MOLECULE) {
            diagnostics.add(error("INVALID_STRUCTURE_COLUMN",
                    "Column '" + structure.id() + "' is not a molecule column.",
                    block.blockId(), block.sourceLine()));
        }
        HashSet<String> seenColumns = new HashSet<>();
        for (CompoundTableColumnSpec columnSpec : spec.columns()) {
            if (!seenColumns.add(columnSpec.columnId())) {
                diagnostics.add(error("DUPLICATE_COLUMN",
                        "Column '" + columnSpec.columnId() + "' is listed more than once.",
                        block.blockId(), block.sourceLine()));
            }
            PrismColumn column = findColumn(session, columnSpec.columnId(), block, diagnostics);
            if (columnSpec.format() != null) {
                if (column != null && column.type() != PrismColumnType.NUMERIC && column.type() != PrismColumnType.INTEGER) {
                    diagnostics.add(error("FORMAT_ON_NON_NUMERIC_COLUMN",
                            "A numeric format cannot be applied to column '" + column.id() + "'.",
                            block.blockId(), block.sourceLine()));
                } else {
                    try {
                        new DecimalFormat(columnSpec.format());
                    } catch (IllegalArgumentException exception) {
                        diagnostics.add(error("INVALID_NUMBER_FORMAT",
                                "Invalid numeric format '" + columnSpec.format() + "'.",
                                block.blockId(), block.sourceLine()));
                    }
                }
            }
        }
        try {
            int size = session.rowSet(spec.rowSetId()).rowIds().size();
            if (size > spec.maxRows()) {
                diagnostics.add(new PrismReportDiagnostic(
                        PrismReportSeverity.WARNING,
                        "ROW_LIMIT_APPLIED",
                        "Row set '" + spec.rowSetId() + "' has " + size + " rows; the block will show "
                                + spec.maxRows() + ".",
                        block.blockId(), block.sourceLine(), 1));
            }
        } catch (IllegalArgumentException ignored) {
            // Unknown row set is already reported above.
        }
    }

    private static PrismColumn findColumn(
            PrismSession session,
            String columnId,
            CompoundTableReportBlock block,
            List<PrismReportDiagnostic> diagnostics
    ) {
        return session.table().findColumn(columnId).orElseGet(() -> {
            diagnostics.add(error("UNKNOWN_COLUMN",
                    unknownWithSuggestion("column", columnId,
                            session.table().columns().stream().map(PrismColumn::id).toList()),
                    block.blockId(), block.sourceLine()));
            return null;
        });
    }

    private static String unknownWithSuggestion(String kind, String value, List<String> candidates) {
        String closest = null;
        int distance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int next = editDistance(value, candidate);
            if (next < distance) {
                distance = next;
                closest = candidate;
            }
        }
        String base = "Unknown " + kind + ": " + value + ".";
        int threshold = Math.max(2, value.length() / 3);
        return closest != null && distance <= threshold ? base + " Did you mean: " + closest + "?" : base;
    }

    private static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) previous[index] = index;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private static PrismReportDiagnostic error(String code, String message, String blockId, int line) {
        return new PrismReportDiagnostic(PrismReportSeverity.ERROR, code, message, blockId, line, 1);
    }
}

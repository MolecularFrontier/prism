package tech.molecules.structurized.prism.report;

import com.fasterxml.jackson.databind.JsonNode;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.ocl.Sar1DViewSpec;
import tech.molecules.structurized.prism.engine.ocl.Sar2DViewSpec;
import tech.molecules.structurized.prism.engine.ocl.SarAggregation;
import tech.molecules.structurized.prism.engine.ocl.SarProjectionBuilder;
import tech.molecules.structurized.prism.engine.ocl.SarValueSpec;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SarPrismReportBlockProviders {
    private SarPrismReportBlockProviders() {
    }

    static void register(PrismReportBlockRegistry registry) {
        registry.register(new Sar1DProvider());
        registry.register(new Sar2DProvider());
    }

    private abstract static class SarProvider implements PrismReportBlockProvider {
        private static final Set<String> VALUE_FIELDS = Set.of(
                "column", "label", "format", "aggregation", "colorColumn");

        @Override
        public void validate(PrismReportBlock block, PrismSession session,
                             List<PrismReportDiagnostic> diagnostics) {
            EmbeddedPrismViewReportBlock embedded = (EmbeddedPrismViewReportBlock) block;
            for (String rowSetId : embedded.specification().referencedRowSetIds()) {
                try {
                    session.rowSet(rowSetId);
                } catch (IllegalArgumentException exception) {
                    diagnostics.add(error("UNKNOWN_ROW_SET", "Unknown row set: " + rowSetId + ".", embedded));
                }
            }
            for (String columnId : embedded.specification().referencedColumnIds()) {
                if (session.table().findColumn(columnId).isEmpty()) {
                    diagnostics.add(error("UNKNOWN_COLUMN", "Unknown column: " + columnId + ".", embedded));
                }
            }
            validateSpecific(embedded, session, diagnostics);
        }

        protected abstract void validateSpecific(EmbeddedPrismViewReportBlock block, PrismSession session,
                                                 List<PrismReportDiagnostic> diagnostics);

        protected static List<SarValueSpec> values(JsonNode json, PrismReportParseContext context) {
            JsonNode array = json.get("values");
            if (array == null || !array.isArray() || array.isEmpty()) {
                context.error("MISSING_VALUES", "SAR blocks require a non-empty 'values' array.");
                return List.of();
            }
            if (array.size() > 4) context.error("TOO_MANY_VALUES", "SAR blocks support at most 4 displayed values.");
            ArrayList<SarValueSpec> result = new ArrayList<>();
            for (int index = 0; index < Math.min(4, array.size()); index++) {
                JsonNode item = array.get(index);
                if (!item.isObject()) {
                    context.error("INVALID_VALUE", "values[" + index + "] must be an object.");
                    continue;
                }
                context.rejectUnknownFields(item, VALUE_FIELDS);
                String column = context.requiredText(item, "column");
                SarAggregation aggregation = aggregation(item, context);
                if (column != null) result.add(new SarValueSpec(column,
                        context.optionalText(item, "label"), context.optionalText(item, "format"),
                        aggregation, context.optionalText(item, "colorColumn")));
            }
            return List.copyOf(result);
        }

        private static SarAggregation aggregation(JsonNode item, PrismReportParseContext context) {
            String text = context.optionalText(item, "aggregation");
            if (text == null) return SarAggregation.BEST;
            try {
                return SarAggregation.valueOf(text.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                context.error("INVALID_AGGREGATION", "aggregation must be best, mean, median, min, or max.");
                return SarAggregation.BEST;
            }
        }

        protected static List<String> contexts(JsonNode json, PrismReportParseContext context) {
            return json.has("contextColumns") ? context.requiredStringArray(json, "contextColumns") : List.of();
        }

        protected static void validateValues(List<SarValueSpec> values, EmbeddedPrismViewReportBlock block,
                                             PrismSession session, List<PrismReportDiagnostic> diagnostics) {
            for (SarValueSpec value : values) {
                PrismColumn column = session.table().findColumn(value.columnId()).orElse(null);
                if (column != null && !numeric(column)) diagnostics.add(error("INVALID_SAR_VALUE_COLUMN",
                        "SAR value column '" + column.id() + "' must be numeric.", block));
                if (column != null && value.aggregation() == SarAggregation.BEST && !hasDirection(column)) {
                    diagnostics.add(error("MISSING_ENDPOINT_DIRECTION",
                            "BEST aggregation requires higher/lower direction metadata on column '"
                                    + column.id() + "'.", block));
                }
                if (value.format() != null) {
                    try {
                        new DecimalFormat(value.format());
                    } catch (IllegalArgumentException exception) {
                        diagnostics.add(error("INVALID_NUMBER_FORMAT", "Invalid numeric format '"
                                + value.format() + "'.", block));
                    }
                }
                if (value.colorColumnId() != null) {
                    PrismColumn color = session.table().findColumn(value.colorColumnId()).orElse(null);
                    if (color != null && !numeric(color)) diagnostics.add(error("INVALID_SAR_COLOR_COLUMN",
                            "SAR color column '" + color.id() + "' must be numeric.", block));
                }
            }
        }

        protected static void validateContexts(List<String> contexts, Set<String> projected,
                                               EmbeddedPrismViewReportBlock block,
                                               List<PrismReportDiagnostic> diagnostics) {
            HashSet<String> seen = new HashSet<>();
            for (String context : contexts) {
                if (!seen.add(context)) diagnostics.add(error("DUPLICATE_CONTEXT_COLUMN",
                        "Context column '" + context + "' is listed more than once.", block));
                if (projected.contains(context)) diagnostics.add(error("PROJECTED_CONTEXT_COLUMN",
                        "Projected SAR column '" + context + "' cannot also be a context column.", block));
            }
        }

        protected static boolean validForModel(EmbeddedPrismViewReportBlock block,
                                               List<PrismReportDiagnostic> diagnostics) {
            return diagnostics.stream().noneMatch(item -> item.severity() == PrismReportSeverity.ERROR
                    && block.blockId().equals(item.blockId()));
        }

        private static boolean numeric(PrismColumn column) {
            return column.type() == PrismColumnType.NUMERIC || column.type() == PrismColumnType.INTEGER;
        }

        private static boolean hasDirection(PrismColumn column) {
            if (column.schema().direction() == null) return false;
            String direction = column.schema().direction().trim().toLowerCase(Locale.ROOT);
            return Set.of("higher_is_better", "higher", "maximize", "max",
                    "lower_is_better", "lower", "minimize", "min").contains(direction);
        }
    }

    private static final class Sar1DProvider extends SarProvider {
        private static final Set<String> FIELDS = Set.of("type", "id", "title", "rowSet",
                "substituentColumn", "contextColumns", "values", "maxGroups", "linkSelection");

        @Override public String type() { return "sar-1d"; }

        @Override
        public PrismReportBlock parse(JsonNode json, PrismReportParseContext context) {
            context.rejectUnknownFields(json, FIELDS);
            String rowSet = context.requiredText(json, "rowSet");
            String substituent = context.requiredText(json, "substituentColumn");
            List<SarValueSpec> values = values(json, context);
            int maxGroups = bounded(context, json, "maxGroups", Sar1DViewSpec.DEFAULT_MAX_GROUPS,
                    Sar1DViewSpec.HARD_MAX_GROUPS);
            if (rowSet == null || substituent == null || values.isEmpty()) return null;
            Sar1DViewSpec spec = new Sar1DViewSpec(context.blockId(), context.optionalText(json, "title"),
                    rowSet, substituent, contexts(json, context), values, maxGroups,
                    context.optionalBoolean(json, "linkSelection", true));
            return new PrismViewReportBlock(context.blockId(), type(), spec, context.sourceLine());
        }

        @Override
        protected void validateSpecific(EmbeddedPrismViewReportBlock block, PrismSession session,
                                        List<PrismReportDiagnostic> diagnostics) {
            Sar1DViewSpec spec = (Sar1DViewSpec) block.specification();
            PrismColumn substituent = session.table().findColumn(spec.substituentColumnId()).orElse(null);
            if (substituent != null && (substituent.type() == PrismColumnType.NUMERIC
                    || substituent.type() == PrismColumnType.INTEGER)) diagnostics.add(error(
                    "INVALID_SAR_SUBSTITUENT_COLUMN", "SAR substituent column '" + substituent.id()
                            + "' must be molecular or categorical.", block));
            validateContexts(spec.contextColumnIds(), Set.of(spec.substituentColumnId()), block, diagnostics);
            validateValues(spec.values(), block, session, diagnostics);
            if (!validForModel(block, diagnostics)) return;
            var model = SarProjectionBuilder.build1D(session.snapshot(), spec);
            if (model.truncated()) diagnostics.add(warning("SAR_GROUP_LIMIT_APPLIED",
                    "1D SAR has " + model.totalGroupCount() + " groups; the block will show "
                            + spec.maxGroups() + ".", block));
            specialWarning(model.excludedRowCount(), block, diagnostics);
        }
    }

    private static final class Sar2DProvider extends SarProvider {
        private static final Set<String> FIELDS = Set.of("type", "id", "title", "rowSet",
                "rowSubstituent", "columnSubstituent", "contextColumns", "values",
                "maxRowGroups", "maxColumnGroups", "linkSelection");

        @Override public String type() { return "sar-2d"; }

        @Override
        public PrismReportBlock parse(JsonNode json, PrismReportParseContext context) {
            context.rejectUnknownFields(json, FIELDS);
            String rowSet = context.requiredText(json, "rowSet");
            String row = context.requiredText(json, "rowSubstituent");
            String column = context.requiredText(json, "columnSubstituent");
            List<SarValueSpec> values = values(json, context);
            int maxRows = bounded(context, json, "maxRowGroups", Sar2DViewSpec.DEFAULT_MAX_GROUPS,
                    Sar2DViewSpec.HARD_MAX_GROUPS);
            int maxColumns = bounded(context, json, "maxColumnGroups", Sar2DViewSpec.DEFAULT_MAX_GROUPS,
                    Sar2DViewSpec.HARD_MAX_GROUPS);
            if (row != null && row.equals(column)) context.error("DUPLICATE_SAR_DIMENSION",
                    "rowSubstituent and columnSubstituent must differ.");
            if (rowSet == null || row == null || column == null || row.equals(column) || values.isEmpty()) return null;
            Sar2DViewSpec spec = new Sar2DViewSpec(context.blockId(), context.optionalText(json, "title"),
                    rowSet, row, column, contexts(json, context), values, maxRows, maxColumns,
                    context.optionalBoolean(json, "linkSelection", true));
            return new PrismViewReportBlock(context.blockId(), type(), spec, context.sourceLine());
        }

        @Override
        protected void validateSpecific(EmbeddedPrismViewReportBlock block, PrismSession session,
                                        List<PrismReportDiagnostic> diagnostics) {
            Sar2DViewSpec spec = (Sar2DViewSpec) block.specification();
            validateContexts(spec.contextColumnIds(), Set.of(spec.rowSubstituentColumnId(),
                    spec.columnSubstituentColumnId()), block, diagnostics);
            validateValues(spec.values(), block, session, diagnostics);
            if (!validForModel(block, diagnostics)) return;
            var model = SarProjectionBuilder.build2D(session.snapshot(), spec);
            if (model.truncated()) diagnostics.add(warning("SAR_GROUP_LIMIT_APPLIED",
                    "2D SAR has " + model.totalRowGroupCount() + " × " + model.totalColumnGroupCount()
                            + " groups; limits are " + spec.maxRowGroups() + " × "
                            + spec.maxColumnGroups() + ".", block));
            specialWarning(model.excludedRowCount(), block, diagnostics);
        }
    }

    private static int bounded(PrismReportParseContext context, JsonNode json, String field,
                               int fallback, int maximum) {
        int value = context.optionalInt(json, field, fallback);
        if (value >= 1 && value <= maximum) return value;
        context.error("INVALID_MAX_GROUPS", field + " must be between 1 and " + maximum + ".");
        return Math.max(1, Math.min(value, maximum));
    }

    private static void specialWarning(int count, EmbeddedPrismViewReportBlock block,
                                       List<PrismReportDiagnostic> diagnostics) {
        if (count > 0) diagnostics.add(warning("SAR_SPECIAL_ROWS_EXCLUDED",
                count + " rows have unmatched, ambiguous, or multi-attachment buckets and are excluded.", block));
    }

    private static PrismReportDiagnostic error(String code, String message,
                                               EmbeddedPrismViewReportBlock block) {
        return new PrismReportDiagnostic(PrismReportSeverity.ERROR, code, message,
                block.blockId(), block.sourceLine(), 1);
    }

    private static PrismReportDiagnostic warning(String code, String message,
                                                 EmbeddedPrismViewReportBlock block) {
        return new PrismReportDiagnostic(PrismReportSeverity.WARNING, code, message,
                block.blockId(), block.sourceLine(), 1);
    }
}

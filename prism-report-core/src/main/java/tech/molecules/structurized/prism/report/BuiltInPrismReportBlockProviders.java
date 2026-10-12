package tech.molecules.structurized.prism.report;

import com.fasterxml.jackson.databind.JsonNode;
import tech.molecules.structurized.prism.engine.ColumnSummaryViewSpec;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.PrismViewSpec;
import tech.molecules.structurized.prism.engine.ScatterPlotViewSpec;
import tech.molecules.structurized.prism.engine.SortDirection;
import tech.molecules.structurized.prism.engine.ocl.CompoundCardPropertySpec;
import tech.molecules.structurized.prism.engine.ocl.CompoundCardsViewSpec;
import tech.molecules.structurized.prism.engine.ocl.CompoundTableColumnSpec;
import tech.molecules.structurized.prism.engine.ocl.CompoundTableViewSpec;
import tech.molecules.structurized.prism.engine.ocl.StructureGridValueSpec;
import tech.molecules.structurized.prism.engine.ocl.StructureGridViewSpec;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class BuiltInPrismReportBlockProviders {
    private BuiltInPrismReportBlockProviders() {
    }

    static void registerDefaults(PrismReportBlockRegistry registry) {
        registry.register(new CompoundTableProvider());
        registry.register(new CompoundCardsProvider());
        registry.register(new StructureGridProvider());
        registry.register(new ScatterProvider());
        registry.register(new ColumnSummaryProvider());
        SarPrismReportBlockProviders.register(registry);
    }

    private abstract static class ViewProvider implements PrismReportBlockProvider {
        @Override
        public void validate(PrismReportBlock block, PrismSession session,
                             List<PrismReportDiagnostic> diagnostics) {
            EmbeddedPrismViewReportBlock embedded = (EmbeddedPrismViewReportBlock) block;
            PrismViewSpec specification = embedded.specification();
            for (String rowSetId : specification.referencedRowSetIds()) {
                try {
                    session.rowSet(rowSetId);
                } catch (IllegalArgumentException exception) {
                    diagnostics.add(error("UNKNOWN_ROW_SET",
                            unknownWithSuggestion("row set", rowSetId,
                                    session.rowSets().stream().map(item -> item.id()).toList()), embedded));
                }
            }
            for (String columnId : specification.referencedColumnIds()) {
                if (session.table().findColumn(columnId).isEmpty()) {
                    diagnostics.add(error("UNKNOWN_COLUMN",
                            unknownWithSuggestion("column", columnId,
                                    session.table().columns().stream().map(PrismColumn::id).toList()), embedded));
                }
            }
            validateSpecific(embedded, session, diagnostics);
        }

        protected abstract void validateSpecific(EmbeddedPrismViewReportBlock block, PrismSession session,
                                                 List<PrismReportDiagnostic> diagnostics);

        protected static PrismColumn column(PrismSession session, String id) {
            return session.table().findColumn(id).orElse(null);
        }
    }

    private static final class CompoundTableProvider extends ViewProvider {
        private static final Set<String> FIELDS = Set.of(
                "type", "id", "title", "rowSet", "structureColumn", "columns", "linkSelection", "maxRows");
        private static final Set<String> COLUMN_FIELDS = Set.of("column", "label", "format", "colorColumn");

        @Override public String type() { return "compound-table"; }

        @Override
        public PrismReportBlock parse(JsonNode json, PrismReportParseContext context) {
            context.rejectUnknownFields(json, FIELDS);
            String rowSet = context.requiredText(json, "rowSet");
            String structure = context.requiredText(json, "structureColumn");
            ArrayList<CompoundTableColumnSpec> columns = new ArrayList<>();
            JsonNode array = json.get("columns");
            if (array == null || !array.isArray() || array.isEmpty()) {
                context.error("MISSING_COLUMNS", "compound-table requires a non-empty 'columns' array.");
            } else {
                for (int index = 0; index < array.size(); index++) {
                    JsonNode item = array.get(index);
                    if (!item.isObject()) {
                        context.error("INVALID_COLUMN", "columns[" + index + "] must be an object.");
                        continue;
                    }
                    context.rejectUnknownFields(item, COLUMN_FIELDS);
                    String column = context.requiredText(item, "column");
                    if (column != null) columns.add(new CompoundTableColumnSpec(
                            column, context.optionalText(item, "label"), context.optionalText(item, "format"),
                            context.optionalText(item, "colorColumn")));
                }
            }
            int maxRows = context.optionalInt(json, "maxRows", CompoundTableViewSpec.DEFAULT_MAX_ROWS);
            if (maxRows < 1 || maxRows > CompoundTableViewSpec.HARD_MAX_ROWS) {
                context.error("INVALID_MAX_ROWS", "maxRows must be between 1 and "
                        + CompoundTableViewSpec.HARD_MAX_ROWS + ".");
                maxRows = Math.max(1, Math.min(maxRows, CompoundTableViewSpec.HARD_MAX_ROWS));
            }
            if (rowSet == null || structure == null || columns.isEmpty()) return null;
            String title = context.optionalText(json, "title");
            CompoundTableViewSpec spec = new CompoundTableViewSpec(context.blockId(), title, rowSet, structure,
                    columns, context.optionalBoolean(json, "linkSelection", true), maxRows);
            return new CompoundTableReportBlock(context.blockId(), spec, context.sourceLine());
        }

        @Override
        protected void validateSpecific(EmbeddedPrismViewReportBlock block, PrismSession session,
                                        List<PrismReportDiagnostic> diagnostics) {
            CompoundTableViewSpec spec = (CompoundTableViewSpec) block.specification();
            PrismColumn structure = column(session, spec.structureColumnId());
            if (structure != null && structure.type() != PrismColumnType.MOLECULE) {
                diagnostics.add(error("INVALID_STRUCTURE_COLUMN",
                        "Column '" + structure.id() + "' is not a molecule column.", block));
            }
            HashSet<String> seen = new HashSet<>();
            for (CompoundTableColumnSpec item : spec.columns()) {
                if (!seen.add(item.columnId())) diagnostics.add(error("DUPLICATE_COLUMN",
                        "Column '" + item.columnId() + "' is listed more than once.", block));
                PrismColumn value = column(session, item.columnId());
                if (item.format() != null) {
                    if (value != null && !numeric(value)) {
                        diagnostics.add(error("FORMAT_ON_NON_NUMERIC_COLUMN",
                                "A numeric format cannot be applied to column '" + value.id() + "'.", block));
                    } else {
                        try {
                            new DecimalFormat(item.format());
                        } catch (IllegalArgumentException exception) {
                            diagnostics.add(error("INVALID_NUMBER_FORMAT",
                                    "Invalid numeric format '" + item.format() + "'.", block));
                        }
                    }
                }
                if (item.colorColumnId() != null) {
                    PrismColumn color = column(session, item.colorColumnId());
                    if (color != null && !numeric(color)) diagnostics.add(error("INVALID_COLOR_COLUMN",
                            "Color column '" + color.id() + "' must be numeric.", block));
                }
            }
            try {
                int size = session.rowSet(spec.rowSetId()).rowIds().size();
                if (size > spec.maxRows()) diagnostics.add(warning("ROW_LIMIT_APPLIED",
                        "Row set '" + spec.rowSetId() + "' has " + size + " rows; the block will show "
                                + spec.maxRows() + ".", block));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static final class CompoundCardsProvider extends ViewProvider {
        private static final Set<String> FIELDS = Set.of(
                "type", "id", "title", "rowSet", "structureColumn", "titleColumn", "referenceRow",
                "properties", "linkSelection", "maxCards");
        private static final Set<String> PROPERTY_FIELDS = Set.of(
                "column", "label", "format", "showDelta", "colorColumn");

        @Override public String type() { return "compound-cards"; }

        @Override
        public PrismReportBlock parse(JsonNode json, PrismReportParseContext context) {
            context.rejectUnknownFields(json, FIELDS);
            String rowSet = context.requiredText(json, "rowSet");
            String structure = context.requiredText(json, "structureColumn");
            String titleColumn = context.requiredText(json, "titleColumn");
            ArrayList<CompoundCardPropertySpec> properties = new ArrayList<>();
            JsonNode array = json.get("properties");
            if (array == null || !array.isArray() || array.isEmpty()) {
                context.error("MISSING_PROPERTIES", "compound-cards requires a non-empty 'properties' array.");
            } else if (array.size() > CompoundCardsViewSpec.HARD_MAX_PROPERTIES) {
                context.error("TOO_MANY_PROPERTIES", "compound-cards supports at most "
                        + CompoundCardsViewSpec.HARD_MAX_PROPERTIES + " properties.");
            } else {
                for (int index = 0; index < array.size(); index++) {
                    JsonNode item = array.get(index);
                    if (!item.isObject()) {
                        context.error("INVALID_PROPERTY", "properties[" + index + "] must be an object.");
                        continue;
                    }
                    context.rejectUnknownFields(item, PROPERTY_FIELDS);
                    String column = context.requiredText(item, "column");
                    if (column != null) properties.add(new CompoundCardPropertySpec(column,
                            context.optionalText(item, "label"), context.optionalText(item, "format"),
                            context.optionalBoolean(item, "showDelta", false),
                            context.optionalText(item, "colorColumn")));
                }
            }
            int maxCards = context.optionalInt(json, "maxCards", CompoundCardsViewSpec.DEFAULT_MAX_CARDS);
            if (maxCards < 1 || maxCards > CompoundCardsViewSpec.HARD_MAX_CARDS) {
                context.error("INVALID_MAX_CARDS", "maxCards must be between 1 and "
                        + CompoundCardsViewSpec.HARD_MAX_CARDS + ".");
                maxCards = Math.max(1, Math.min(maxCards, CompoundCardsViewSpec.HARD_MAX_CARDS));
            }
            if (rowSet == null || structure == null || titleColumn == null || properties.isEmpty()
                    || properties.size() > CompoundCardsViewSpec.HARD_MAX_PROPERTIES) return null;
            CompoundCardsViewSpec spec = new CompoundCardsViewSpec(context.blockId(),
                    context.optionalText(json, "title"), rowSet, structure, titleColumn,
                    context.optionalText(json, "referenceRow"), properties,
                    context.optionalBoolean(json, "linkSelection", true), maxCards);
            return new PrismViewReportBlock(context.blockId(), type(), spec, context.sourceLine());
        }

        @Override
        protected void validateSpecific(EmbeddedPrismViewReportBlock block, PrismSession session,
                                        List<PrismReportDiagnostic> diagnostics) {
            CompoundCardsViewSpec spec = (CompoundCardsViewSpec) block.specification();
            PrismColumn structure = column(session, spec.structureColumnId());
            if (structure != null && structure.type() != PrismColumnType.MOLECULE) {
                diagnostics.add(error("INVALID_STRUCTURE_COLUMN",
                        "Column '" + structure.id() + "' is not a molecule column.", block));
            }
            PrismColumn title = column(session, spec.titleColumnId());
            if (title != null && title.type() == PrismColumnType.MOLECULE) {
                diagnostics.add(error("INVALID_TITLE_COLUMN",
                        "Title column '" + title.id() + "' must not be a molecule column.", block));
            }
            HashSet<String> seen = new HashSet<>();
            for (CompoundCardPropertySpec property : spec.properties()) {
                if (!seen.add(property.columnId())) diagnostics.add(error("DUPLICATE_PROPERTY",
                        "Property column '" + property.columnId() + "' is listed more than once.", block));
                PrismColumn value = column(session, property.columnId());
                if (property.format() != null) validateFormat(value, property.format(), block, diagnostics);
                if (property.showDelta() && value != null && !numeric(value)) {
                    diagnostics.add(error("DELTA_ON_NON_NUMERIC_COLUMN",
                            "Deltas require a numeric property column; '" + value.id() + "' is not numeric.", block));
                }
                if (property.colorColumnId() != null) {
                    PrismColumn color = column(session, property.colorColumnId());
                    if (color != null && !numeric(color)) diagnostics.add(error("INVALID_COLOR_COLUMN",
                            "Color column '" + color.id() + "' must be numeric.", block));
                }
            }
            try {
                var rowSet = session.rowSet(spec.rowSetId());
                int size = rowSet.rowIds().size();
                if (size > spec.maxCards()) diagnostics.add(warning("CARD_LIMIT_APPLIED",
                        "Row set '" + spec.rowSetId() + "' has " + size + " rows; the block will show "
                                + spec.maxCards() + ".", block));
                if (spec.referenceRowId() != null) {
                    if (session.physicalRowForRowId(spec.referenceRowId()).isEmpty()) {
                        diagnostics.add(error("UNKNOWN_REFERENCE_ROW",
                                "Unknown reference row: " + spec.referenceRowId() + ".", block));
                    } else if (!rowSet.rowIds().contains(spec.referenceRowId())) {
                        diagnostics.add(error("REFERENCE_OUTSIDE_ROW_SET", "Reference row '"
                                + spec.referenceRowId() + "' is not contained in row set '"
                                + spec.rowSetId() + "'.", block));
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        private static void validateFormat(PrismColumn column, String pattern,
                                           EmbeddedPrismViewReportBlock block,
                                           List<PrismReportDiagnostic> diagnostics) {
            if (column != null && !numeric(column)) {
                diagnostics.add(error("FORMAT_ON_NON_NUMERIC_COLUMN",
                        "A numeric format cannot be applied to column '" + column.id() + "'.", block));
                return;
            }
            try {
                new DecimalFormat(pattern);
            } catch (IllegalArgumentException exception) {
                diagnostics.add(error("INVALID_NUMBER_FORMAT",
                        "Invalid numeric format '" + pattern + "'.", block));
            }
        }
    }

    private static final class StructureGridProvider extends ViewProvider {
        private static final Set<String> FIELDS = Set.of("type", "id", "title", "rowSet", "structureColumn",
                "valueColumns", "sortBy", "sortDirection", "maxCompounds", "gridColumns");
        private static final Set<String> VALUE_FIELDS = Set.of(
                "column", "label", "format", "colorColumn");

        @Override public String type() { return "structure-grid"; }

        @Override
        public PrismReportBlock parse(JsonNode json, PrismReportParseContext context) {
            context.rejectUnknownFields(json, FIELDS);
            String rowSet = context.requiredText(json, "rowSet");
            String structure = context.requiredText(json, "structureColumn");
            ArrayList<StructureGridValueSpec> values = new ArrayList<>();
            JsonNode valueColumns = json.get("valueColumns");
            if (valueColumns != null && !valueColumns.isArray()) {
                context.error("INVALID_VALUE_COLUMNS", "valueColumns must be an array.");
            } else if (valueColumns != null) {
                for (int index = 0; index < valueColumns.size(); index++) {
                    JsonNode item = valueColumns.get(index);
                    if (item.isTextual() && !item.textValue().isBlank()) {
                        values.add(new StructureGridValueSpec(item.textValue()));
                    } else if (item.isObject()) {
                        context.rejectUnknownFields(item, VALUE_FIELDS);
                        String column = context.requiredText(item, "column");
                        if (column != null) values.add(new StructureGridValueSpec(
                                column, context.optionalText(item, "label"),
                                context.optionalText(item, "format"),
                                context.optionalText(item, "colorColumn")));
                    } else {
                        context.error("INVALID_VALUE_COLUMN",
                                "valueColumns[" + index + "] must be a column ID or an object.");
                    }
                }
            }
            List<String> valueIds = values.stream().map(StructureGridValueSpec::columnId).toList();
            String directionText = context.optionalText(json, "sortDirection");
            SortDirection direction = SortDirection.ASCENDING;
            if (directionText != null) {
                try {
                    direction = SortDirection.valueOf(directionText.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    context.error("INVALID_SORT_DIRECTION", "sortDirection must be ascending or descending.");
                }
            }
            int maxCompounds = context.optionalInt(json, "maxCompounds", 24);
            int gridColumns = context.optionalInt(json, "gridColumns", 4);
            if (maxCompounds < 1) {
                context.error("INVALID_MAX_COMPOUNDS", "maxCompounds must be at least 1.");
                maxCompounds = 24;
            }
            if (gridColumns < 1 || gridColumns > 8) {
                context.error("INVALID_GRID_COLUMNS", "gridColumns must be between 1 and 8.");
                gridColumns = Math.max(1, Math.min(8, gridColumns));
            }
            if (rowSet == null || structure == null) return null;
            StructureGridViewSpec spec = new StructureGridViewSpec(context.blockId(),
                    context.optionalText(json, "title"), rowSet, structure, valueIds,
                    context.optionalText(json, "sortBy"), direction, maxCompounds, gridColumns, values);
            return new PrismViewReportBlock(context.blockId(), type(), spec, context.sourceLine());
        }

        @Override
        protected void validateSpecific(EmbeddedPrismViewReportBlock block, PrismSession session,
                                        List<PrismReportDiagnostic> diagnostics) {
            StructureGridViewSpec spec = (StructureGridViewSpec) block.specification();
            PrismColumn structure = column(session, spec.structureColumnId());
            if (structure != null && structure.type() != PrismColumnType.MOLECULE) {
                diagnostics.add(error("INVALID_STRUCTURE_COLUMN",
                        "Column '" + structure.id() + "' is not a molecule column.", block));
            }
            for (StructureGridValueSpec valueSpec : spec.valueSpecifications()) {
                PrismColumn value = column(session, valueSpec.columnId());
                if (valueSpec.format() != null) {
                    if (value != null && !numeric(value)) {
                        diagnostics.add(error("FORMAT_ON_NON_NUMERIC_COLUMN",
                                "A numeric format cannot be applied to column '" + value.id() + "'.", block));
                    } else {
                        try {
                            new DecimalFormat(valueSpec.format());
                        } catch (IllegalArgumentException exception) {
                            diagnostics.add(error("INVALID_NUMBER_FORMAT",
                                    "Invalid numeric format '" + valueSpec.format() + "'.", block));
                        }
                    }
                }
                PrismColumn color = valueSpec.colorColumnId() == null ? null : column(session, valueSpec.colorColumnId());
                if (color != null && !numeric(color)) diagnostics.add(error("INVALID_COLOR_COLUMN",
                        "Color column '" + color.id() + "' must be numeric.", block));
            }
        }
    }

    private static final class ScatterProvider extends ViewProvider {
        private static final Set<String> FIELDS = Set.of("type", "id", "title", "rowSet", "xColumn", "yColumn",
                "colorColumn", "xMin", "xMax", "yMin", "yMax");

        @Override public String type() { return "scatter"; }

        @Override
        public PrismReportBlock parse(JsonNode json, PrismReportParseContext context) {
            context.rejectUnknownFields(json, FIELDS);
            String rowSet = context.requiredText(json, "rowSet");
            String x = context.requiredText(json, "xColumn");
            String y = context.requiredText(json, "yColumn");
            if (rowSet == null || x == null || y == null) return null;
            ScatterPlotViewSpec spec = new ScatterPlotViewSpec(context.blockId(),
                    context.optionalText(json, "title"), rowSet, x, y,
                    context.optionalText(json, "colorColumn"),
                    context.optionalDouble(json, "xMin"), context.optionalDouble(json, "xMax"),
                    context.optionalDouble(json, "yMin"), context.optionalDouble(json, "yMax"));
            return new PrismViewReportBlock(context.blockId(), type(), spec, context.sourceLine());
        }

        @Override
        protected void validateSpecific(EmbeddedPrismViewReportBlock block, PrismSession session,
                                        List<PrismReportDiagnostic> diagnostics) {
            ScatterPlotViewSpec spec = (ScatterPlotViewSpec) block.specification();
            PrismColumn x = column(session, spec.xColumnId());
            PrismColumn y = column(session, spec.yColumnId());
            if (x != null && !numeric(x)) diagnostics.add(error("INVALID_X_COLUMN",
                    "Scatter x column '" + x.id() + "' must be numeric.", block));
            if (y != null && !numeric(y)) diagnostics.add(error("INVALID_Y_COLUMN",
                    "Scatter y column '" + y.id() + "' must be numeric.", block));
            if (spec.xMin() != null && spec.xMax() != null && spec.xMin() >= spec.xMax()) {
                diagnostics.add(error("INVALID_X_RANGE", "xMin must be less than xMax.", block));
            }
            if (spec.yMin() != null && spec.yMax() != null && spec.yMin() >= spec.yMax()) {
                diagnostics.add(error("INVALID_Y_RANGE", "yMin must be less than yMax.", block));
            }
        }
    }

    private static final class ColumnSummaryProvider extends ViewProvider {
        private static final Set<String> FIELDS = Set.of("type", "id", "title", "rowSet", "columns");

        @Override public String type() { return "column-summary"; }

        @Override
        public PrismReportBlock parse(JsonNode json, PrismReportParseContext context) {
            context.rejectUnknownFields(json, FIELDS);
            String rowSet = context.requiredText(json, "rowSet");
            List<String> columns = context.requiredStringArray(json, "columns");
            if (rowSet == null || columns.isEmpty()) return null;
            ColumnSummaryViewSpec spec = new ColumnSummaryViewSpec(context.blockId(),
                    context.optionalText(json, "title"), rowSet, columns);
            return new PrismViewReportBlock(context.blockId(), type(), spec, context.sourceLine());
        }

        @Override
        protected void validateSpecific(EmbeddedPrismViewReportBlock block, PrismSession session,
                                        List<PrismReportDiagnostic> diagnostics) {
            ColumnSummaryViewSpec spec = (ColumnSummaryViewSpec) block.specification();
            for (String id : spec.columnIds()) {
                PrismColumn column = column(session, id);
                if (column != null && column.type() == PrismColumnType.MOLECULE) {
                    diagnostics.add(error("INVALID_SUMMARY_COLUMN",
                            "Molecule column '" + id + "' cannot be summarized.", block));
                }
            }
        }
    }

    private static boolean numeric(PrismColumn column) {
        return column.type() == PrismColumnType.NUMERIC || column.type() == PrismColumnType.INTEGER;
    }

    private static PrismReportDiagnostic error(String code, String message, EmbeddedPrismViewReportBlock block) {
        return new PrismReportDiagnostic(PrismReportSeverity.ERROR, code, message,
                block.blockId(), block.sourceLine(), 1);
    }

    private static PrismReportDiagnostic warning(String code, String message, EmbeddedPrismViewReportBlock block) {
        return new PrismReportDiagnostic(PrismReportSeverity.WARNING, code, message,
                block.blockId(), block.sourceLine(), 1);
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
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            previous = current;
        }
        return previous[right.length()];
    }
}

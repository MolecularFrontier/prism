package tech.molecules.structurized.prism.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record PrismReportSchema(
        int prismReportVersion,
        String fileExtension,
        List<PrismReportFieldSchema> frontMatter,
        List<PrismReportBlockSchema> blockTypes,
        String template
) {
    public PrismReportSchema {
        frontMatter = frontMatter == null ? List.of() : List.copyOf(frontMatter);
        blockTypes = blockTypes == null ? List.of() : List.copyOf(blockTypes);
    }

    public static PrismReportSchema current() {
        return new PrismReportSchema(1, ".prism.md", List.of(
                field("prismReportVersion", "integer", true, "Must be 1."),
                field("dataset", "string", true, "Must be current."),
                field("title", "string", true, "Human-readable report title."),
                field("id", "string", false, "Optional stable report identity."),
                field("createdAt", "string", false, "Optional ISO-8601 instant.")),
                List.of(compoundTable(), compoundCards(), structureGrid(), scatter(), columnSummary(), sar1d(), sar2d()),
                """
                        ---
                        prismReportVersion: 1
                        dataset: current
                        title: Report title
                        ---

                        # Report title

                        Scientific narrative in ordinary Markdown.

                        ```prism
                        {
                          "type": "compound-table",
                          "id": "key-compounds",
                          "rowSet": "all",
                          "structureColumn": "structure",
                          "columns": [
                            {"column": "compound_id", "label": "Compound"}
                          ],
                          "linkSelection": true
                        }
                        ```
                        """);
    }

    private static PrismReportBlockSchema compoundTable() {
        return block("compound-table", "Sortable structure and property table.", List.of(
                common("id"), common("title"),
                field("rowSet", "string", true, "Prism row-set ID."),
                field("structureColumn", "string", true, "Molecule column ID."),
                field("columns", "array<object>", true, "One or more displayed column descriptors."),
                field("columns[].column", "string", true, "Displayed Prism column ID."),
                field("columns[].label", "string", false, "Optional display label."),
                field("columns[].format", "string", false, "Optional DecimalFormat pattern for numeric values."),
                field("linkSelection", "boolean", false, "Defaults to true."),
                field("maxRows", "integer", false, "Defaults to 200; range 1..2000.")),
                Map.of("type", "compound-table", "id", "key-compounds", "rowSet", "all",
                        "structureColumn", "structure", "columns", List.of(Map.of(
                                "column", "compound_id", "label", "Compound")), "linkSelection", true));
    }

    private static PrismReportBlockSchema compoundCards() {
        return block("compound-cards", "Focused comparison of up to eight compounds with reference deltas and score coloring.", List.of(
                common("id"), common("title"),
                field("rowSet", "string", true, "Prism row-set ID containing the compounds to compare."),
                field("structureColumn", "string", true, "Molecule column ID."),
                field("titleColumn", "string", true, "Non-molecule column used as each card title."),
                field("referenceRow", "string", false, "Optional stable Prism row ID used as the delta reference."),
                field("properties", "array<object>", true, "Between 1 and 8 displayed property descriptors."),
                field("properties[].column", "string", true, "Displayed Prism column ID."),
                field("properties[].label", "string", false, "Optional display label."),
                field("properties[].format", "string", false, "Optional DecimalFormat pattern for numeric values."),
                field("properties[].showDelta", "boolean", false, "Show numeric difference from referenceRow; defaults to false."),
                field("properties[].colorColumn", "string", false, "Optional numeric score column, conventionally scaled 0..1."),
                field("linkSelection", "boolean", false, "Defaults to true."),
                field("maxCards", "integer", false, "Defaults to 6; range 1..8.")),
                Map.ofEntries(Map.entry("type", "compound-cards"), Map.entry("id", "lead-comparison"),
                        Map.entry("rowSet", "report.lead-comparison"), Map.entry("structureColumn", "structure"),
                        Map.entry("titleColumn", "compound_id"), Map.entry("referenceRow", "cmpd-14"),
                        Map.entry("properties", List.of(
                                Map.of("column", "pIC50", "label", "Activity", "format", "0.00",
                                        "showDelta", true, "colorColumn", "score__activity"),
                                Map.of("column", "logD", "format", "0.0", "showDelta", true))),
                        Map.entry("linkSelection", true), Map.entry("maxCards", 6)));
    }

    private static PrismReportBlockSchema structureGrid() {
        return block("structure-grid", "Compact structure-card grid with selected values.", List.of(
                common("id"), common("title"),
                field("rowSet", "string", true, "Prism row-set ID."),
                field("structureColumn", "string", true, "Molecule column ID."),
                field("valueColumns", "array<string>", false, "Displayed Prism column IDs."),
                field("sortBy", "string", false, "Optional Prism column ID."),
                field("sortDirection", "string", false, "ascending or descending; defaults to ascending."),
                field("maxCompounds", "integer", false, "Defaults to 24; must be at least 1."),
                field("gridColumns", "integer", false, "Defaults to 4; range 1..8.")),
                Map.of("type", "structure-grid", "id", "lead-grid", "rowSet", "all",
                        "structureColumn", "structure", "valueColumns", List.of("compound_id"),
                        "maxCompounds", 12, "gridColumns", 4));
    }

    private static PrismReportBlockSchema scatter() {
        return block("scatter", "Interactive numeric scatter plot.", List.of(
                common("id"), common("title"),
                field("rowSet", "string", true, "Prism row-set ID."),
                field("xColumn", "string", true, "Numeric Prism column ID."),
                field("yColumn", "string", true, "Numeric Prism column ID."),
                field("colorColumn", "string", false, "Optional Prism column used for coloring."),
                field("xMin", "number", false, "Optional finite lower x bound."),
                field("xMax", "number", false, "Optional finite upper x bound."),
                field("yMin", "number", false, "Optional finite lower y bound."),
                field("yMax", "number", false, "Optional finite upper y bound.")),
                Map.of("type", "scatter", "id", "activity-property", "rowSet", "all",
                        "xColumn", "pIC50", "yColumn", "logD", "colorColumn", "score__activity"));
    }

    private static PrismReportBlockSchema columnSummary() {
        return block("column-summary", "Compact distributions and category summaries.", List.of(
                common("id"), common("title"),
                field("rowSet", "string", true, "Prism row-set ID."),
                field("columns", "array<string>", true, "One or more non-molecule Prism column IDs.")),
                Map.of("type", "column-summary", "id", "property-summary", "rowSet", "all",
                        "columns", List.of("pIC50", "logD")));
    }

    private static PrismReportBlockSchema sar1d() {
        return block("sar-1d", "One-dimensional substituent SAR table.", List.of(
                common("id"), common("title"),
                field("rowSet", "string", true, "Prism row-set ID."),
                field("substituentColumn", "string", true, "Molecular or categorical substituent column ID."),
                field("contextColumns", "array<string>", false, "Optional columns defining matched context."),
                valuesField(), valueColumn(), valueOptional("label", "Optional display label."),
                valueOptional("format", "Optional DecimalFormat pattern."),
                valueOptional("aggregation", "best, mean, median, min, or max; defaults to best."),
                valueOptional("colorColumn", "Optional numeric score/color column ID."),
                field("maxGroups", "integer", false, "Defaults to 50; range 1..200."),
                field("linkSelection", "boolean", false, "Defaults to true.")),
                Map.of("type", "sar-1d", "id", "r1-sar", "rowSet", "series-a",
                        "substituentColumn", "series_a.R1", "values", List.of(Map.of(
                                "column", "pIC50", "aggregation", "best", "format", "0.00")),
                        "linkSelection", true));
    }

    private static PrismReportBlockSchema sar2d() {
        return block("sar-2d", "Two-dimensional substituent SAR matrix.", List.of(
                common("id"), common("title"),
                field("rowSet", "string", true, "Prism row-set ID."),
                field("rowSubstituent", "string", true, "Row substituent column ID."),
                field("columnSubstituent", "string", true, "Different column substituent column ID."),
                field("contextColumns", "array<string>", false, "Optional columns defining matched context."),
                valuesField(), valueColumn(), valueOptional("label", "Optional display label."),
                valueOptional("format", "Optional DecimalFormat pattern."),
                valueOptional("aggregation", "best, mean, median, min, or max; defaults to best."),
                valueOptional("colorColumn", "Optional numeric score/color column ID."),
                field("maxRowGroups", "integer", false, "Defaults to 24; range 1..50."),
                field("maxColumnGroups", "integer", false, "Defaults to 24; range 1..50."),
                field("linkSelection", "boolean", false, "Defaults to true.")),
                Map.ofEntries(Map.entry("type", "sar-2d"), Map.entry("id", "r1-r2-sar"),
                        Map.entry("rowSet", "series-a"), Map.entry("rowSubstituent", "series_a.R1"),
                        Map.entry("columnSubstituent", "series_a.R2"), Map.entry("values", List.of(Map.of(
                                "column", "pIC50", "aggregation", "best", "format", "0.00"))),
                        Map.entry("linkSelection", true)));
    }

    private static PrismReportBlockSchema block(String type, String description,
                                                 List<PrismReportFieldSchema> fields,
                                                 Map<String, Object> example) {
        ArrayList<PrismReportFieldSchema> allFields = new ArrayList<>();
        allFields.add(field("type", "string", true, "Must be " + type + "."));
        allFields.addAll(fields);
        return new PrismReportBlockSchema(type, description, allFields, example);
    }

    private static PrismReportFieldSchema common(String name) {
        return field(name, "string", false, name.equals("id")
                ? "Optional block ID; generated when omitted. IDs must be unique."
                : "Optional block title.");
    }

    private static PrismReportFieldSchema valuesField() {
        return field("values", "array<object>", true, "Between 1 and 4 displayed numeric values.");
    }

    private static PrismReportFieldSchema valueColumn() {
        return field("values[].column", "string", true, "Numeric Prism column ID.");
    }

    private static PrismReportFieldSchema valueOptional(String name, String description) {
        return field("values[]." + name, "string", false, description);
    }

    private static PrismReportFieldSchema field(String name, String type, boolean required, String description) {
        return new PrismReportFieldSchema(name, type, required, description);
    }
}

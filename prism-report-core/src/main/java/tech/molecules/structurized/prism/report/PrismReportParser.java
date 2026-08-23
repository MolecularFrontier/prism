package tech.molecules.structurized.prism.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.commonmark.Extension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Document;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import tech.molecules.structurized.prism.engine.ocl.CompoundTableColumnSpec;
import tech.molecules.structurized.prism.engine.ocl.CompoundTableViewSpec;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PrismReportParser {
    private static final Set<String> BLOCK_FIELDS = Set.of(
            "type", "id", "rowSet", "structureColumn", "columns", "linkSelection", "maxRows");
    private static final Set<String> COLUMN_FIELDS = Set.of("column", "label", "format");

    private final ObjectMapper objectMapper;
    private final PrismReportBlockRegistry blockRegistry;
    private final Parser parser;
    private final MarkdownRenderer markdownRenderer;

    public PrismReportParser() {
        this(new ObjectMapper());
    }

    public PrismReportParser(ObjectMapper objectMapper) {
        this(objectMapper, PrismReportBlockRegistry.defaults());
    }

    public PrismReportParser(ObjectMapper objectMapper, PrismReportBlockRegistry blockRegistry) {
        this.objectMapper = objectMapper;
        this.blockRegistry = blockRegistry;
        List<Extension> extensions = List.of(YamlFrontMatterExtension.create());
        this.parser = Parser.builder()
                .extensions(extensions)
                .includeSourceSpans(IncludeSourceSpans.BLOCKS)
                .build();
        this.markdownRenderer = MarkdownRenderer.builder().extensions(extensions).build();
    }

    public PrismReportDocument parse(String source) {
        String normalizedSource = source == null ? "" : source;
        Node root = parser.parse(normalizedSource);
        ArrayList<PrismReportDiagnostic> diagnostics = new ArrayList<>();
        PrismReportMetadata metadata = metadata(root, diagnostics);
        ArrayList<PrismReportBlock> blocks = new ArrayList<>();
        Document prose = new Document();
        int proseLine = 1;
        int prismOrdinal = 0;

        for (Node child = root.getFirstChild(); child != null; ) {
            Node next = child.getNext();
            int line = sourceLine(child);
            if (child instanceof org.commonmark.ext.front.matter.YamlFrontMatterBlock) {
                child.unlink();
            } else if (child instanceof FencedCodeBlock fence && "prism".equals(normalizedInfo(fence.getInfo()))) {
                flushProse(prose, proseLine, blocks);
                prose = new Document();
                prismOrdinal++;
                PrismReportBlock block = blockRegistry.parse(
                        objectMapper, fence.getLiteral(), prismOrdinal, line, diagnostics);
                if (block != null) blocks.add(block);
                proseLine = line + (int) Math.max(1L, fence.getLiteral().lines().count() + 2L);
                child.unlink();
            } else {
                if (prose.getFirstChild() == null) proseLine = line;
                child.unlink();
                prose.appendChild(child);
            }
            child = next;
        }
        flushProse(prose, proseLine, blocks);
        return new PrismReportDocument(metadata, blocks, normalizedSource, diagnostics);
    }

    private PrismReportMetadata metadata(Node root, List<PrismReportDiagnostic> diagnostics) {
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        root.accept(visitor);
        Map<String, List<String>> data = visitor.getData();
        int version = parseVersion(first(data, "prismReportVersion"), diagnostics);
        return new PrismReportMetadata(
                version,
                first(data, "dataset"),
                first(data, "title"),
                first(data, "id"),
                first(data, "createdAt")
        );
    }

    private static int parseVersion(String value, List<PrismReportDiagnostic> diagnostics) {
        if (value == null) return -1;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            diagnostics.add(error("INVALID_REPORT_VERSION", "prismReportVersion must be an integer.", null, 2, 1));
            return -1;
        }
    }

    private CompoundTableReportBlock parsePrismBlock(
            FencedCodeBlock fence,
            int ordinal,
            int sourceLine,
            List<PrismReportDiagnostic> diagnostics
    ) {
        JsonNode json;
        try {
            json = objectMapper.readTree(fence.getLiteral());
        } catch (JsonProcessingException exception) {
            int relativeLine = exception.getLocation() == null ? 1 : Math.max(1, exception.getLocation().getLineNr());
            int column = exception.getLocation() == null ? 1 : Math.max(1, exception.getLocation().getColumnNr());
            diagnostics.add(error("INVALID_BLOCK_JSON", exception.getOriginalMessage(), null,
                    sourceLine + relativeLine, column));
            return null;
        }
        if (json == null || !json.isObject()) {
            diagnostics.add(error("INVALID_BLOCK_JSON", "A prism block must contain one JSON object.", null,
                    sourceLine + 1, 1));
            return null;
        }
        String type = text(json, "type");
        String blockId = normalized(text(json, "id"));
        if (blockId == null) blockId = "block-" + ordinal;
        rejectUnknownFields(json, BLOCK_FIELDS, blockId, sourceLine, diagnostics);
        if (!"compound-table".equals(type)) {
            diagnostics.add(error("UNKNOWN_BLOCK_TYPE",
                    type == null ? "Prism block is missing required field 'type'."
                            : "Unknown prism block type '" + type + "'.",
                    blockId, sourceLine + 1, 1));
            return null;
        }

        String rowSet = requiredText(json, "rowSet", blockId, sourceLine, diagnostics);
        String structureColumn = requiredText(json, "structureColumn", blockId, sourceLine, diagnostics);
        List<CompoundTableColumnSpec> columns = columns(json.get("columns"), blockId, sourceLine, diagnostics);
        boolean linkSelection = true;
        if (json.has("linkSelection")) {
            if (json.get("linkSelection").isBoolean()) {
                linkSelection = json.get("linkSelection").booleanValue();
            } else {
                diagnostics.add(error("INVALID_FIELD_TYPE", "linkSelection must be a boolean.",
                        blockId, sourceLine + 1, 1));
            }
        }
        int maxRows = CompoundTableViewSpec.DEFAULT_MAX_ROWS;
        if (json.has("maxRows")) {
            if (json.get("maxRows").isIntegralNumber()) {
                maxRows = json.get("maxRows").intValue();
            } else {
                diagnostics.add(error("INVALID_FIELD_TYPE", "maxRows must be an integer.",
                        blockId, sourceLine + 1, 1));
            }
        }
        if (maxRows < 1 || maxRows > CompoundTableViewSpec.HARD_MAX_ROWS) {
            diagnostics.add(error("INVALID_MAX_ROWS",
                    "maxRows must be between 1 and " + CompoundTableViewSpec.HARD_MAX_ROWS + ".",
                    blockId, sourceLine + 1, 1));
            maxRows = Math.max(1, Math.min(maxRows, CompoundTableViewSpec.HARD_MAX_ROWS));
        }
        if (rowSet == null || structureColumn == null || columns.isEmpty()) return null;
        CompoundTableViewSpec spec = new CompoundTableViewSpec(
                blockId, "Compound Table", rowSet, structureColumn, columns, linkSelection, maxRows);
        return new CompoundTableReportBlock(blockId, spec, sourceLine);
    }

    private static List<CompoundTableColumnSpec> columns(
            JsonNode node,
            String blockId,
            int sourceLine,
            List<PrismReportDiagnostic> diagnostics
    ) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            diagnostics.add(error("MISSING_COLUMNS", "compound-table requires a non-empty 'columns' array.",
                    blockId, sourceLine + 1, 1));
            return List.of();
        }
        ArrayList<CompoundTableColumnSpec> result = new ArrayList<>();
        int index = 0;
        for (JsonNode item : node) {
            index++;
            if (!item.isObject()) {
                diagnostics.add(error("INVALID_COLUMN", "columns[" + (index - 1) + "] must be an object.",
                        blockId, sourceLine + 1, 1));
                continue;
            }
            rejectUnknownFields(item, COLUMN_FIELDS, blockId, sourceLine, diagnostics);
            String column = normalized(text(item, "column"));
            if (column == null) {
                diagnostics.add(error("MISSING_COLUMN_ID", "columns[" + (index - 1) + "] requires 'column'.",
                        blockId, sourceLine + 1, 1));
                continue;
            }
            result.add(new CompoundTableColumnSpec(column, text(item, "label"), text(item, "format")));
        }
        return List.copyOf(result);
    }

    private static void rejectUnknownFields(
            JsonNode object,
            Set<String> allowed,
            String blockId,
            int sourceLine,
            List<PrismReportDiagnostic> diagnostics
    ) {
        Iterator<String> fields = object.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                diagnostics.add(error("UNKNOWN_FIELD", "Unknown field '" + field + "'.",
                        blockId, sourceLine + 1, 1));
            }
        }
    }

    private static String requiredText(JsonNode json, String field, String blockId, int sourceLine,
                                       List<PrismReportDiagnostic> diagnostics) {
        String value = normalized(text(json, field));
        if (value == null) {
            diagnostics.add(error("MISSING_FIELD", "compound-table requires '" + field + "'.",
                    blockId, sourceLine + 1, 1));
        }
        return value;
    }

    private void flushProse(Document prose, int sourceLine, List<PrismReportBlock> blocks) {
        if (prose.getFirstChild() == null) return;
        String markdown = markdownRenderer.render(prose);
        if (!markdown.isBlank()) blocks.add(new MarkdownReportBlock(markdown, sourceLine));
    }

    private static int sourceLine(Node node) {
        return node.getSourceSpans().isEmpty() ? 1 : node.getSourceSpans().getFirst().getLineIndex() + 1;
    }

    private static String normalizedInfo(String info) {
        return info == null ? "" : info.trim().split("\\s+", 2)[0];
    }

    private static String first(Map<String, List<String>> data, String key) {
        List<String> values = data.get(key);
        return values == null || values.isEmpty() ? null : normalized(values.getFirst());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static PrismReportDiagnostic error(String code, String message, String blockId, int line, int column) {
        return new PrismReportDiagnostic(PrismReportSeverity.ERROR, code, message, blockId, line, column);
    }
}

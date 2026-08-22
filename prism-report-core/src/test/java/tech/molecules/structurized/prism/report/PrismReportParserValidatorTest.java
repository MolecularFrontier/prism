package tech.molecules.structurized.prism.report;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrismReportParserValidatorTest {
    @Test
    void parsesOrderedMarkdownAndCompoundTableBlocks() {
        PrismReportDocument report = new PrismReportParser().parse(validReport("pIC50"));

        assertFalse(report.hasErrors());
        assertEquals("Series A overview", report.metadata().title());
        assertEquals(3, report.blocks().size());
        assertInstanceOf(MarkdownReportBlock.class, report.blocks().get(0));
        CompoundTableReportBlock table = assertInstanceOf(CompoundTableReportBlock.class, report.blocks().get(1));
        assertEquals("key-compounds", table.blockId());
        assertEquals("all", table.specification().rowSetId());
        assertEquals("pIC50", table.specification().columns().get(1).columnId());
        assertInstanceOf(MarkdownReportBlock.class, report.blocks().get(2));
    }

    @Test
    void preservesOrdinaryFencedCodeAsMarkdown() {
        PrismReportDocument report = new PrismReportParser().parse("""
                ---
                prismReportVersion: 1
                dataset: current
                title: Code
                ---
                ~~~java
                int answer = 42;
                ~~~
                """);

        MarkdownReportBlock markdown = assertInstanceOf(MarkdownReportBlock.class, report.blocks().getFirst());
        assertTrue(markdown.markdown().contains("java"));
        assertTrue(markdown.markdown().contains("int answer"));
    }

    @Test
    void validatesReferencesFormatsAndSuggestions() throws Exception {
        PrismSession session = exampleSession();
        PrismReportDocument report = new PrismReportParser().parse(validReport("pIC5O"));
        var diagnostics = new PrismReportValidator().validate(report, session);

        assertTrue(diagnostics.stream().anyMatch(item -> item.code().equals("UNKNOWN_COLUMN")
                && item.message().contains("Did you mean: pIC50?")));
    }

    @Test
    void reportsMalformedJsonWithSourceLine() {
        PrismReportDocument report = new PrismReportParser().parse("""
                ---
                prismReportVersion: 1
                dataset: current
                title: Broken
                ---

                # Heading

                ~~~prism
                { "type": "compound-table", }
                ~~~
                """);

        PrismReportDiagnostic diagnostic = report.diagnostics().getFirst();
        assertEquals("INVALID_BLOCK_JSON", diagnostic.code());
        assertTrue(diagnostic.line() >= 9);
    }

    static PrismSession exampleSession() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        LinkedHashSet<String> rowIds = new LinkedHashSet<>();
        for (int row = 0; row < session.totalRowCount(); row++) rowIds.add(session.rowIdForPhysicalRow(row));
        session.addRowSet(new PrismRowSet("all", "All", "", rowIds, Map.of()));
        return session;
    }

    static String validReport(String endpointColumn) {
        return """
                ---
                prismReportVersion: 1
                dataset: current
                title: Series A overview
                createdAt: 2026-08-22T10:00:00Z
                ---

                # Key compounds

                Narrative before the table.

                ~~~prism
                {
                  "type": "compound-table",
                  "id": "key-compounds",
                  "rowSet": "all",
                  "structureColumn": "smiles",
                  "columns": [
                    {"column": "compound_id", "label": "Compound"},
                    {"column": "%s", "format": "0.00"}
                  ],
                  "linkSelection": true,
                  "maxRows": 200
                }
                ~~~

                Narrative after the table.
                """.formatted(endpointColumn);
    }
}

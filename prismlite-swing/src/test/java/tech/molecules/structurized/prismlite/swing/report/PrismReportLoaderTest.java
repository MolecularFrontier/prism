package tech.molecules.structurized.prismlite.swing.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.report.PrismReportViewSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrismReportLoaderTest {
    @Test
    void loadsValidatedExternalReportAsRuntimeView(@TempDir Path tempDir) throws Exception {
        PrismSession session = exampleSession();
        Path report = tempDir.resolve("series-a.prism.md");
        Files.writeString(report, report("pIC50"));

        var view = PrismReportLoader.load(report, Path.of("example.prismpack"), session);

        assertEquals("report:series-a", view.id());
        assertEquals("Series A", view.title());
        assertInstanceOf(PrismReportViewSpec.class, view.specification());
    }

    @Test
    void loadsTheRunnableExampleIncludingCompoundCards() throws Exception {
        PrismSession session = exampleSession();

        var view = PrismReportLoader.load(Path.of("..", "examples", "example-report.prism.md"),
                Path.of("example.prismpack"), session);
        PrismReportViewSpec report = assertInstanceOf(PrismReportViewSpec.class, view.specification());

        assertEquals(5, report.document().blocks().stream()
                .filter(tech.molecules.structurized.prism.report.EmbeddedPrismViewReportBlock.class::isInstance)
                .count());
    }

    @Test
    void rejectsInvalidReportWithoutChangingSession(@TempDir Path tempDir) throws Exception {
        PrismSession session = exampleSession();
        Path report = tempDir.resolve("invalid.prism.md");
        Files.writeString(report, report("missing_column"));

        assertThrows(PrismReportValidationException.class,
                () -> PrismReportLoader.load(report, null, session));
        assertEquals(0, session.views().size());
    }

    private static PrismSession exampleSession() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        LinkedHashSet<String> rowIds = new LinkedHashSet<>();
        for (int row = 0; row < session.totalRowCount(); row++) rowIds.add(session.rowIdForPhysicalRow(row));
        session.addRowSet(new PrismRowSet("all", "All", "", rowIds, Map.of()));
        return session;
    }

    private static String report(String endpoint) {
        return """
                ---
                prismReportVersion: 1
                dataset: current
                title: Series A
                ---
                ~~~prism
                {
                  "type": "compound-table",
                  "rowSet": "all",
                  "structureColumn": "smiles",
                  "columns": [{"column": "compound_id"}, {"column": "%s"}]
                }
                ~~~
                """.formatted(endpoint);
    }
}

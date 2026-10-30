package tech.molecules.structurized.prism.report;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prism.pack.PrismPack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportDataLinkingTest {

    // ---------- B1: optional identity, warning not error ----------

    @Test
    void existingReportsWithoutIdentityStayValid() throws Exception {
        PrismReportDocument report = new PrismReportParser().parse(report(null, null));

        // no identity declared, none supplied -> unchanged behaviour
        assertTrue(new PrismReportValidator()
                .validate(report, PrismReportParserValidatorTest.exampleSession())
                .isEmpty());
        assertEquals(null, report.metadata().datasetId());
        assertEquals(null, report.metadata().datasetSnapshotId());
    }

    @Test
    void parsesOptionalIdentityFromFrontMatter() {
        PrismReportDocument report = new PrismReportParser()
                .parse(report("mc2r29", "sha256:aaa"));

        assertEquals("mc2r29", report.metadata().datasetId());
        assertEquals("sha256:aaa", report.metadata().datasetSnapshotId());
    }

    @Test
    void snapshotMismatchIsAWarningSoReportsReloadAgainstNewerData() throws Exception {
        PrismReportDocument report = new PrismReportParser()
                .parse(report("mc2r29", "sha256:authored"));

        List<PrismReportDiagnostic> diagnostics = new PrismReportValidator().validate(
                report,
                PrismReportParserValidatorTest.exampleSession(),
                new PrismReportDatasetIdentity("mc2r29", "sha256:newer"));

        // exactly one diagnostic, and it must not block loading
        assertEquals(1, diagnostics.size());
        assertEquals("DATASET_SNAPSHOT_MISMATCH", diagnostics.getFirst().code());
        assertEquals(PrismReportSeverity.WARNING, diagnostics.getFirst().severity());
        assertFalse(diagnostics.stream().anyMatch(d -> d.severity() == PrismReportSeverity.ERROR));
        assertTrue(diagnostics.getFirst().message().contains("sha256:authored"));
        assertTrue(diagnostics.getFirst().message().contains("sha256:newer"));
    }

    @Test
    void matchingIdentityProducesNoDiagnostics() throws Exception {
        PrismReportDocument report = new PrismReportParser()
                .parse(report("mc2r29", "sha256:same"));

        assertTrue(new PrismReportValidator().validate(
                report,
                PrismReportParserValidatorTest.exampleSession(),
                new PrismReportDatasetIdentity("mc2r29", "sha256:same")).isEmpty());
    }

    // ---------- B3: embed and auto-attach ----------

    @Test
    void embeddedReportRoundTripsAndRegistersAsAView() throws Exception {
        PrismSession session = PrismReportParserValidatorTest.exampleSession();
        PrismPack.Attachment attachment = PrismPackReports.attachmentFor(
                "linking-demo", "Linking demo", report(null, null), Map.of());

        assertTrue(PrismPackReports.isReport(attachment));
        assertEquals("report:linking-demo", attachment.id());

        PrismPack pack = packWith(attachment);
        assertEquals(1, PrismPackReports.reports(pack).size());

        List<PrismReportAttachmentFailure> failures = new ArrayList<>();
        List<String> registered = PrismPackReports.attach(pack, session, failures);

        assertTrue(failures.isEmpty(), () -> "unexpected failures: " + failures);
        assertEquals(List.of("report:linking-demo"), registered);
        PrismViewRecord view = session.views().stream()
                .filter(item -> item.id().equals("report:linking-demo"))
                .findFirst().orElseThrow();
        assertEquals("prismpack:report:linking-demo", view.provenance().get("reportSource"));
    }

    @Test
    void nonReportAttachmentsAreIgnored() throws Exception {
        PrismPack pack = packWith(new PrismPack.Attachment(
                "notes", new PrismPack.AttachmentTarget("dataset", null, null, null, Map.of()),
                "Notes", "text/plain",
                new PrismPack.AttachmentContent("inline", "just a note", null, Map.of()), Map.of()));

        assertTrue(PrismPackReports.reports(pack).isEmpty());
        assertTrue(PrismPackReports.attach(pack, PrismReportParserValidatorTest.exampleSession()).isEmpty());
    }

    @Test
    void oneBrokenEmbeddedReportDoesNotPreventTheOthers() throws Exception {
        PrismSession session = PrismReportParserValidatorTest.exampleSession();
        PrismPack pack = packWith(
                PrismPackReports.attachmentFor("broken", "Broken", brokenReport(), Map.of()),
                PrismPackReports.attachmentFor("good", "Good", report(null, null), Map.of()));

        List<PrismReportAttachmentFailure> failures = new ArrayList<>();
        List<String> registered = PrismPackReports.attach(pack, session, failures);

        assertEquals(List.of("report:good"), registered);
        assertEquals(1, failures.size());
        assertEquals("report:broken", failures.getFirst().attachmentId());
        assertFalse(failures.getFirst().diagnostics().isEmpty());
    }

    // ---------- B2: one implementation for file and embedded reports ----------

    @Test
    void registrarRejectsInvalidReportsRegardlessOfOrigin() throws Exception {
        PrismSession session = PrismReportParserValidatorTest.exampleSession();
        assertThrows(PrismReportValidationException.class, () -> PrismReportRegistrar.register(
                brokenReport(), "broken", "inline-test", null, session, null));
    }

    // ---------- B4: derived requirements ----------

    @Test
    void derivesReferencedColumnsAndRowSets() throws Exception {
        PrismSession session = PrismReportParserValidatorTest.exampleSession();
        PrismReportDocument report = new PrismReportParser().parse(report(null, null));

        Map<String, Object> described = PrismReportRequirements.describe(report, session);

        @SuppressWarnings("unchecked")
        List<String> rowSets = (List<String>) described.get("requiresRowSets");
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) described.get("requiresColumns");
        assertTrue(rowSets.contains("all"), () -> "row sets: " + rowSets);
        assertTrue(columns.contains("pIC50"), () -> "columns: " + columns);
        // the example session has no materialized analysis columns, so nothing to declare
        assertFalse(described.containsKey("requiresAnalyses"));
    }

    // ---------- helpers ----------

    private static PrismPack packWith(PrismPack.Attachment... attachments) {
        return new PrismPack(
                new PrismPack.Manifest("0.3", "test-pack", "Test", null, null, "test",
                        null, null, null, null, null, null, null, null, null, null, null, null, Map.of()),
                new PrismPack.DataFrame(List.of(), List.of()),
                new PrismPack.DataFrameSchema(List.of(), Map.of()),
                null, null, null, null, null, null,
                new PrismPack.AttachmentSet(List.of(attachments), Map.of()),
                null, null, null, Map.of(), List.of());
    }

    private static String report(String datasetId, String snapshotId) {
        String identity = (datasetId == null ? "" : "datasetId: " + datasetId + "\n")
                + (snapshotId == null ? "" : "datasetSnapshotId: \"" + snapshotId + "\"\n");
        return """
                ---
                prismReportVersion: 1
                dataset: current
                title: Linking demo
                %s---

                # Linking demo

                ```prism
                {
                  "type": "column-summary",
                  "id": "summary",
                  "rowSet": "all",
                  "columns": ["pIC50"]
                }
                ```
                """.formatted(identity);
    }

    private static String brokenReport() {
        return """
                ---
                prismReportVersion: 1
                dataset: current
                title: Broken
                ---

                ```prism
                {
                  "type": "column-summary",
                  "id": "summary",
                  "rowSet": "no-such-row-set",
                  "columns": ["pIC50"]
                }
                ```
                """;
    }
}

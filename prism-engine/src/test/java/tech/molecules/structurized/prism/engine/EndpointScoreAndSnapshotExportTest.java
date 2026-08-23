package tech.molecules.structurized.prism.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.molecules.structurized.prism.engine.snapshot.PrismPackSnapshotDataset;
import tech.molecules.structurized.prism.engine.snapshot.PrismSnapshotExportResult;
import tech.molecules.structurized.prism.engine.snapshot.PrismSnapshotExportService;
import tech.molecules.structurized.prism.pack.PrismPack;
import tech.molecules.structurized.prism.pack.PrismPackReader;
import tech.molecules.structurized.prism.score.EndpointScoreDefinition;
import tech.molecules.structurized.prism.score.ScorePoint;

import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointScoreAndSnapshotExportTest {
    @Test
    void computesReusableSummariesForAllRowsAndScopes() throws Exception {
        PrismSession session = PrismSession.open(examplePack());

        NumericColumnSummary numeric = (NumericColumnSummary) ColumnSummaries.compute(session.table().column("pIC50"));
        assertEquals(3, numeric.validCount());
        assertEquals(5.9, numeric.minimum(), 1.0e-12);
        assertEquals(7.2, numeric.maximum(), 1.0e-12);

        BitSet firstTwo = new BitSet();
        firstTwo.set(0, 2);
        CategoricalColumnSummary series = (CategoricalColumnSummary) ColumnSummaries.compute(
                session.table().column("series"), firstTwo);
        assertEquals(2, series.validCount());
        assertEquals(1, series.distinctCount());
        assertEquals("A", series.topValues().getFirst().value());
    }

    @Test
    void definesEndpointScoreIdempotentlyAndRejectsSemanticConflict() throws Exception {
        PrismSession session = PrismSession.open(examplePack());
        EndpointScoreDefinition score = score(List.of(new ScorePoint(5.0, 0.0), new ScorePoint(8.0, 1.0)));

        PrismOperationResult first = session.defineEndpointScore(score, null);
        PrismOperationResult repeated = session.defineEndpointScore(score, null);

        assertFalse((Boolean) first.output().get("reused"));
        assertTrue((Boolean) repeated.output().get("reused"));
        assertEquals(1, session.scoreDefinitions().size());
        assertEquals(0.7333333333333334, session.table().column("score__potency").doubleValueAt(0), 1.0e-12);
        assertThrows(PrismOperationException.class, () -> session.defineEndpointScore(
                score(List.of(new ScorePoint(5.0, 1.0), new ScorePoint(8.0, 0.0))), null));
    }

    @Test
    void exportsNewFullFidelityPackWithRuntimeScoreAndNeverOverwrites(@TempDir Path tempDir) throws Exception {
        PrismPack source = PrismPackReader.read(examplePack());
        PrismPackSnapshotDataset snapshot = PrismPackSnapshotDataset.from(source);
        PrismSession session = PrismSession.from(snapshot);
        session.defineEndpointScore(score(List.of(new ScorePoint(5.0, 0.0), new ScorePoint(8.0, 1.0))), null);
        Path output = tempDir.resolve("analysis.prismpack");

        PrismSnapshotExportResult result = PrismSnapshotExportService.export(
                snapshot, session, output, "Agent analysis", "test");
        PrismPack exported = PrismPackReader.read(output);

        assertEquals(output, result.path());
        assertNotEquals(source.manifest().id(), exported.manifest().id());
        assertEquals(source.endpointResults(), exported.endpointResults());
        assertTrue(exported.dataFrame().headers().contains("score__potency"));
        assertEquals("potency", exported.scores().scores().getFirst().id());
        assertTrue(((Map<?, ?>) exported.provenance().get("analysisExport")).containsKey("parentSnapshotId"));
        assertThrows(IllegalArgumentException.class, () -> PrismSnapshotExportService.export(
                snapshot, session, output, "Again", "test"));
    }

    private static EndpointScoreDefinition score(List<ScorePoint> points) {
        return new EndpointScoreDefinition("potency", "pIC50", "Potency score", null,
                EndpointScoreDefinition.LINE_SEGMENT_V1, EndpointScoreDefinition.LINEAR, true, points, Map.of());
    }

    private static Path examplePack() {
        return Path.of("..", "examples", "example.prismpack");
    }
}

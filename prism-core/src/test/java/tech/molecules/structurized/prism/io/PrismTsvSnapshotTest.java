package tech.molecules.structurized.prism.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.molecules.structurized.prism.model.EndpointDataType;
import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.model.EndpointType;
import tech.molecules.structurized.prism.model.EvaluationMode;
import tech.molecules.structurized.prism.provider.SubjectRecord;
import tech.molecules.structurized.prism.provider.SubjectSet;
import tech.molecules.structurized.prism.provider.inmemory.InMemoryPrismDataset;
import tech.molecules.structurized.prism.query.EndpointValueRecord;
import tech.molecules.structurized.prism.result.NumericResult;
import tech.molecules.structurized.prism.result.NumericState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrismTsvSnapshotTest {
    @Test
    void snapshotRoundTripsAndIdentityIgnoresCaptureWindow(@TempDir Path tempDir) throws IOException {
        InMemoryPrismDataset dataset = dataset();
        PrismDatasetSnapshot first = PrismTsvDatasetWriter.writeSnapshot(tempDir.resolve("first"), dataset,
                descriptor("2026-08-04T09:00:00Z", "2026-08-04T09:01:00Z"));
        PrismDatasetSnapshot second = PrismTsvDatasetWriter.writeSnapshot(tempDir.resolve("second"), dataset,
                descriptor("2026-08-05T09:00:00Z", "2026-08-05T09:01:00Z"));

        assertEquals(first.manifest().snapshotId(), second.manifest().snapshotId());
        PrismDatasetSnapshot loaded = PrismTsvSnapshotLoader.load(tempDir.resolve("first"));
        assertEquals(first.manifest(), loaded.manifest());
        assertEquals(List.of("cmp-1"), loaded.dataset().getSubjectsForSet("series:alpha"));
        NumericResult value = (NumericResult) loaded.dataset().findEndpointValue("cmp-1", "clearance").orElseThrow().getResult();
        assertEquals(7.5, value.getMean());
        assertEquals(List.of(7.0, 8.0), value.getRawValues());
        assertEquals("2026-01-01", value.getFirstMeasurement());
        assertTrue(Files.readString(tempDir.resolve("first/subjects.prism.tsv")).contains("owner"));
    }

    @Test
    void snapshotRejectsTamperingAndExistingTargets(@TempDir Path tempDir) throws IOException {
        Path snapshot = tempDir.resolve("snapshot");
        PrismTsvDatasetWriter.writeSnapshot(snapshot, dataset(), descriptor("2026-08-04T09:00:00Z", "2026-08-04T09:01:00Z"));
        Files.writeString(snapshot.resolve("values.prism.tsv"), "tampered", java.nio.file.StandardOpenOption.APPEND);

        assertThrows(IllegalArgumentException.class, () -> PrismTsvSnapshotLoader.load(snapshot));
        assertThrows(IllegalArgumentException.class,
                () -> PrismTsvDatasetWriter.writeSnapshot(snapshot, dataset(), descriptor("a", "b")));
    }

    private static InMemoryPrismDataset dataset() {
        EndpointDefinition endpoint = EndpointDefinition.builder()
                .id("clearance").name("Clearance").path("shared/dmpk/clearance")
                .datatype(EndpointDataType.NUMERIC).endpointType(EndpointType.MEASURED)
                .evaluationMode(EvaluationMode.IMMEDIATE).unit("mL/min/kg").build();
        SubjectRecord subject = SubjectRecord.builder().subjectId("cmp-1").structureId("mol-1")
                .project("P1").series("Alpha").smiles("CCO").putMetadata("owner", "Ada").build();
        SubjectSet set = SubjectSet.builder().id("series:alpha").name("Alpha").setType("SERIES")
                .subjectSetScope("SERIES").build();
        NumericResult result = NumericResult.builder().state(NumericState.VALUE).mean(7.5).lower(6.5).upper(8.5)
                .rawValues(List.of(7.0, 8.0)).rawValueIds(List.of("r1", "r2"))
                .n(2).firstMeasurement("2026-01-01").lastMeasurement("2026-01-03")
                .details(Map.of("source", "assay"))
                .build();
        return InMemoryPrismDataset.builder().addEndpointDefinition(endpoint).addSubjectRecord(subject)
                .addSubjectSet(set).addSubjectMembership(set.getId(), subject.getSubjectId())
                .addEndpointValue(EndpointValueRecord.builder().subjectId(subject.getSubjectId())
                        .endpointId(endpoint.getId()).result(result).build())
                .build();
    }

    private static PrismSnapshotDescriptor descriptor(String started, String completed) {
        return new PrismSnapshotDescriptor(started, completed, "test-publisher", "1", "test:source",
                "STRUCTURE", "smiles", "smiles", "as-supplied",
                new PrismSnapshotSelection("subject_set", "series:alpha", "membership-1"),
                List.of(new PrismSnapshotEndpoint("clearance", "endpoint-1", Map.of("workflow", "test"))),
                Map.of("series:alpha", "membership-1"), Map.of("purpose", "test"));
    }
}

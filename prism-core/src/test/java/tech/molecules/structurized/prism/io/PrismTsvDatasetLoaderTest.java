package tech.molecules.structurized.prism.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.molecules.structurized.prism.provider.SubjectRecord;
import tech.molecules.structurized.prism.provider.inmemory.InMemoryPrismDataset;
import tech.molecules.structurized.prism.query.EndpointFetchRequest;
import tech.molecules.structurized.prism.query.EndpointValueRecord;
import tech.molecules.structurized.prism.result.CategoricalResult;
import tech.molecules.structurized.prism.result.NumericResult;
import tech.molecules.structurized.prism.result.NumericState;
import tech.molecules.structurized.prism.result.TextResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrismTsvDatasetLoaderTest {

    @Test
    void loaderBuildsDatasetAndDerivesProjectAndSeriesSets(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.ENDPOINTS_FILE_NAME), """
                endpoint_id\tname\tpath\tdatatype\tendpoint_type\tevaluation_mode\tunit\tscale\tdomain_lower_bound\tdomain_upper_bound\tcategories
                ic50\tIC50\tassay/ic50\tNUMERIC\tMEASURED\tIMMEDIATE\tnM\tLOG\t0.001\t10000\t
                potency_class\tPotency Class\tassay/potency_class\tCATEGORICAL\tDERIVED\tON_DEMAND\t\t\t\t\tactive=Active;inactive=Inactive
                """);

        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.SUBJECTS_FILE_NAME), """
                subject_id\tstructure_id\tbatch_id\tproject\tseries\tsmiles\towner
                cmp-1\tSTR-1\tB-1\tProject A\tSeries 1\tCCO\tAlice
                cmp-2\tSTR-2\tB-2\tProject A\tSeries 2\tCCN\tBob
                """);

        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.VALUES_FILE_NAME), """
                subject_id\tendpoint_id\tmean\tvalue\traw_value_ids\tdetails
                cmp-1\tic50\t7.1\t\tr1|r2\tsource=assay1;plate=P1
                cmp-1\tpotency_class\t\tactive\t\t
                cmp-2\tic50\t\t\tr3\tsource=assay1
                cmp-2\tpotency_class\t\tinactive\t\t
                """);

        InMemoryPrismDataset dataset = PrismTsvDatasetLoader.load(tempDir);

        assertEquals(2, dataset.getEndpointDefinitions().size());
        assertEquals(2, dataset.getSubjectRecords().size());
        assertEquals(3, dataset.getSubjectSets().size());
        assertEquals(List.of("PROJECTS", "SERIES"), dataset.subjectSetProvider().listSubjectSetScopes());
        assertEquals(0.001, dataset.findEndpointDefinition("ic50").orElseThrow().getNumericMeta().getDomainLowerBound());

        SubjectRecord cmp1 = dataset.findSubjectRecord("cmp-1").orElseThrow();
        assertEquals("CCO", cmp1.getSmiles());
        assertEquals("Alice", cmp1.getMetadata().get("owner"));

        List<String> projectSubjects = dataset.subjectSetProvider().listSubjects("project:Project A", 0, 10);
        assertEquals(List.of("cmp-1", "cmp-2"), projectSubjects);
        assertEquals(1, dataset.subjectSetProvider().countSubjects("series:Project A:Series 1"));

        List<EndpointValueRecord> values = dataset.endpointProvider().fetchEndpointValues(EndpointFetchRequest.builder()
                .addSubjectId("cmp-1")
                .addEndpointId("ic50")
                .addEndpointId("potency_class")
                .build());

        assertEquals(2, values.size());
        assertInstanceOf(NumericResult.class, values.get(0).getResult());
        assertInstanceOf(CategoricalResult.class, values.get(1).getResult());
        NumericResult numeric = (NumericResult) values.get(0).getResult();
        assertEquals(List.of("r1", "r2"), numeric.getRawValueIds());
        assertEquals("assay1", numeric.getDetails().get("source"));
        assertEquals("P1", numeric.getDetails().get("plate"));
        NumericResult notMeasured = (NumericResult) dataset.endpointProvider().fetchEndpointValues(EndpointFetchRequest.builder()
                .addSubjectId("cmp-2")
                .addEndpointId("ic50")
                .build()).getFirst().getResult();
        assertEquals(NumericState.NOT_MEASURED, notMeasured.getState());
    }

    @Test
    void loaderPadsMissingTrailingEmptyCells(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.ENDPOINTS_FILE_NAME), """
                endpoint_id\tname\tpath\tdatatype\tendpoint_type\tevaluation_mode\tunit\tscale\tdomain_lower_bound\tdomain_upper_bound\tcategories\tdescription
                ic50\tIC50\tassay/ic50\tNUMERIC\tMEASURED\tIMMEDIATE\tnM\tLOG\t0.001\t10000\t
                """);

        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.SUBJECTS_FILE_NAME), """
                subject_id\tsmiles
                cmp-1\tCCO
                """);

        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.VALUES_FILE_NAME), """
                subject_id\tendpoint_id\tmean\traw_value_ids\tdetails
                cmp-1\tic50\t7.1\tr1
                """);

        InMemoryPrismDataset dataset = PrismTsvDatasetLoader.load(tempDir);

        assertNull(dataset.findEndpointDefinition("ic50").orElseThrow().getDescription());
        NumericResult numeric = (NumericResult) dataset.endpointProvider().fetchEndpointValues(EndpointFetchRequest.builder()
                .addSubjectId("cmp-1")
                .addEndpointId("ic50")
                .build()).getFirst().getResult();
        assertEquals(7.1, numeric.getMean());
        assertEquals(List.of("r1"), numeric.getRawValueIds());
        assertTrue(numeric.getDetails().isEmpty());
    }

    @Test
    void loaderUsesEndpointIdAsFallbackNameAndPath(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.ENDPOINTS_FILE_NAME), """
                endpoint_id\tname\tpath\tdatatype\tendpoint_type\tevaluation_mode
                ic50\t\t\tNUMERIC\tMEASURED\tIMMEDIATE
                """);

        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.SUBJECTS_FILE_NAME), """
                subject_id
                cmp-1
                """);

        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.VALUES_FILE_NAME), """
                subject_id\tendpoint_id\tmean
                cmp-1\tic50\t7.1
                """);

        InMemoryPrismDataset dataset = PrismTsvDatasetLoader.load(tempDir);

        assertEquals("ic50", dataset.findEndpointDefinition("ic50").orElseThrow().getName());
        assertEquals("ic50", dataset.findEndpointDefinition("ic50").orElseThrow().getPath());
    }

    @Test
    void loaderUnescapesLineOrientedTsvCells(@TempDir Path tempDir) throws IOException {
        String description = "Line one\nLine two\tTabbed\\Backslash";
        String textValue = "First line\nSecond line";

        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.ENDPOINTS_FILE_NAME), """
                endpoint_id\tname\tpath\tdatatype\tendpoint_type\tevaluation_mode\tdescription
                note\tNote\ttext/note\tTEXT\tMEASURED\tIMMEDIATE\t%s
                """.formatted(PrismTsvEscaper.escapeCell(description)));

        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.SUBJECTS_FILE_NAME), """
                subject_id\tcomment
                cmp-1\t%s
                """.formatted(PrismTsvEscaper.escapeCell("Subject\tcomment")));

        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.VALUES_FILE_NAME), """
                subject_id\tendpoint_id\ttext\tdetails
                cmp-1\tnote\t%s\tsource=notebook
                """.formatted(PrismTsvEscaper.escapeCell(textValue)));

        InMemoryPrismDataset dataset = PrismTsvDatasetLoader.load(tempDir);

        assertEquals(description, dataset.findEndpointDefinition("note").orElseThrow().getDescription());
        assertEquals("Subject\tcomment", dataset.findSubjectRecord("cmp-1").orElseThrow().getMetadata().get("comment"));
        TextResult result = (TextResult) dataset.endpointProvider().fetchEndpointValues(EndpointFetchRequest.builder()
                .addSubjectId("cmp-1")
                .addEndpointId("note")
                .build()).getFirst().getResult();
        assertEquals(textValue, result.getText());
    }

    @Test
    void loaderSupportsExplicitSubjectSetsAndMemberships(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.ENDPOINTS_FILE_NAME), """
                endpoint_id\tname\tpath\tdatatype\tendpoint_type\tevaluation_mode
                active\tActive\tassay/active\tBOOLEAN\tMEASURED\tIMMEDIATE
                """);

        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.SUBJECTS_FILE_NAME), """
                subject_id\tproject\tsmiles
                cmp-1\tProject A\tCCO
                """);

        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.VALUES_FILE_NAME), """
                subject_id\tendpoint_id\tvalue
                cmp-1\tactive\ttrue
                """);

        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.SUBJECT_SETS_FILE_NAME), """
                subject_set_id\tname\tset_type\tsubject_set_scope\tparent_set_id\tdescription
                custom:focus\tFocus List\tCUSTOM\tCUSTOM\t\tHand-curated focus compounds
                """);

        Files.writeString(tempDir.resolve(PrismTsvDatasetLoader.SUBJECT_SET_MEMBERSHIPS_FILE_NAME), """
                subject_set_id\tsubject_id
                custom:focus\tcmp-1
                """);

        InMemoryPrismDataset dataset = PrismTsvDatasetLoader.load(tempDir);

        assertTrue(dataset.findSubjectSet("custom:focus").isPresent());
        assertEquals(List.of("cmp-1"), dataset.subjectSetProvider().listSubjects("custom:focus", 0, 10));
    }
}

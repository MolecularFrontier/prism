package tech.molecules.structurized.prism.provider.inmemory;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.model.EndpointDataType;
import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.model.EndpointType;
import tech.molecules.structurized.prism.model.EvaluationMode;
import tech.molecules.structurized.prism.provider.SubjectRecord;
import tech.molecules.structurized.prism.provider.SubjectSet;
import tech.molecules.structurized.prism.query.EndpointFetchRequest;
import tech.molecules.structurized.prism.query.EndpointValueRecord;
import tech.molecules.structurized.prism.result.NumericResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPrismDatasetTest {

    @Test
    void datasetExposesEndpointAndSubjectSetProviders() {
        EndpointDefinition ic50 = EndpointDefinition.builder()
                .id("ic50")
                .name("IC50")
                .path("assay/ic50")
                .datatype(EndpointDataType.NUMERIC)
                .endpointType(EndpointType.MEASURED)
                .evaluationMode(EvaluationMode.IMMEDIATE)
                .build();

        SubjectRecord cmp1 = SubjectRecord.builder()
                .subjectId("cmp-1")
                .project("Project A")
                .series("Series 1")
                .smiles("CCO")
                .build();

        SubjectRecord cmp2 = SubjectRecord.builder()
                .subjectId("cmp-2")
                .project("Project A")
                .series("Series 2")
                .smiles("CCN")
                .build();

        SubjectSet project = SubjectSet.builder()
                .id("project:a")
                .name("Project A")
                .setType("PROJECT")
                .subjectSetScope("PROJECTS")
                .build();

        InMemoryPrismDataset dataset = InMemoryPrismDataset.builder()
                .addEndpointDefinition(ic50)
                .addSubjectRecord(cmp1)
                .addSubjectRecord(cmp2)
                .addSubjectSet(project)
                .addSubjectMembership(project.getId(), cmp1.getSubjectId())
                .addSubjectMembership(project.getId(), cmp2.getSubjectId())
                .addEndpointValue(EndpointValueRecord.builder()
                        .subjectId("cmp-1")
                        .endpointId("ic50")
                        .result(NumericResult.builder().mean(7.1).build())
                        .build())
                .addEndpointValue(EndpointValueRecord.builder()
                        .subjectId("cmp-2")
                        .endpointId("ic50")
                        .result(NumericResult.builder().mean(6.8).build())
                        .build())
                .build();

        assertTrue(dataset.findSubjectRecord("cmp-1").isPresent());
        assertEquals(List.of("PROJECTS"), dataset.subjectSetProvider().listSubjectSetScopes());
        assertEquals(2, dataset.subjectSetProvider().countSubjects(project.getId()));
        assertEquals(List.of("cmp-2"), dataset.subjectSetProvider().listSubjects(project.getId(), 1, 1));

        List<EndpointValueRecord> values = dataset.endpointProvider().fetchEndpointValues(EndpointFetchRequest.builder()
                .addSubjectId("cmp-2")
                .addSubjectId("cmp-1")
                .addEndpointId("ic50")
                .build());

        assertEquals(2, values.size());
        assertEquals("cmp-2", values.get(0).getSubjectId());
        assertEquals("cmp-1", values.get(1).getSubjectId());
    }
}

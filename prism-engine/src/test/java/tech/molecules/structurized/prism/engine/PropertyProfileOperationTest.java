package tech.molecules.structurized.prism.engine;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.pack.PrismPack;
import tech.molecules.structurized.prism.score.EndpointScoreDefinition;
import tech.molecules.structurized.prism.score.MpoAggregationDefinition;
import tech.molecules.structurized.prism.score.MpoComponentDefinition;
import tech.molecules.structurized.prism.score.MpoDefinition;
import tech.molecules.structurized.prism.score.PropertyProfileDefinition;
import tech.molecules.structurized.prism.score.PropertyProfileItem;
import tech.molecules.structurized.prism.score.ScorePoint;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyProfileOperationTest {
    @Test
    void sessionLoadsDefinitionsAndMaterializesScoresAndMpo() {
        PrismSession session = PrismSession.from(pack());

        assertEquals(1, session.scoreDefinitions().size());
        assertEquals(1, session.propertyProfiles().size());
        assertNotNull(session.operationRegistry().operation(MaterializePropertyProfileOperation.ID));

        PrismOperationResult result = session.runOperation(MaterializePropertyProfileOperation.ID,
                Map.of("profileId", "lead_profile"));

        assertEquals(4, result.addedColumns().size());
        assertEquals(0.75, session.table().column("score__potency_score").doubleValueAt(0), 1.0e-12);
        assertEquals(0.75, session.table().column("mpo__lead_mpo").doubleValueAt(0), 1.0e-12);
        assertEquals("PASS", session.table().column("mpo__lead_mpo__status").valueAt(0));
        assertEquals("FAIL", session.table().column("mpo__lead_mpo__status").valueAt(1));
        assertTrue(session.viewState().visibleColumns().contains("score__potency_score"));
    }

    @Test
    void readOnlyOperationsExposeStructuredAiResults() {
        PrismSession session = PrismSession.from(pack());

        PrismOperationResult listed = session.runOperation(ListPropertyProfilesOperation.ID, Map.of());
        assertEquals(1, ((List<?>) listed.output().get("profiles")).size());

        PrismOperationResult described = session.runOperation(DescribePropertyProfileOperation.ID,
                Map.of("profileId", "lead_profile"));
        assertEquals("lead_profile", ((Map<?, ?>) described.output().get("profile")).get("id"));

        PrismOperationResult scored = session.runOperation(EvaluateEndpointScoreOperation.ID,
                Map.of("scoreId", "potency_score", "value", 8.0));
        assertEquals(0.75, (Double) ((Map<?, ?>) scored.output().get("evaluation")).get("score"), 1.0e-12);

        PrismOperationResult profile = session.runOperation(EvaluatePropertyProfileOperation.ID,
                Map.of("profileId", "lead_profile", "rowId", "CMP-1"));
        assertEquals("CMP-1", ((Map<?, ?>) profile.output().get("evaluation")).get("rowId"));

        PrismOperationResult mpo = session.runOperation(EvaluateMpoOperation.ID,
                Map.of("profileId", "lead_profile", "mpoId", "lead_mpo", "rowId", "CMP-1"));
        assertEquals("PASS", ((Map<?, ?>) mpo.output().get("evaluation")).get("status"));
    }

    @Test
    void repeatedMaterializationReusesMatchingCache() {
        PrismSession session = PrismSession.from(pack());
        session.runOperation(MaterializePropertyProfileOperation.ID, Map.of("profileId", "lead_profile"));

        PrismOperationResult repeated = session.runOperation(MaterializePropertyProfileOperation.ID,
                Map.of("profileId", "lead_profile"));

        assertEquals(0, repeated.addedColumns().size());
        assertEquals(4, repeated.warnings().size());
    }

    private static PrismPack pack() {
        EndpointScoreDefinition score = new EndpointScoreDefinition(
                "potency_score", "pIC50", "Potency", null, "line_segment_v1", "linear", true,
                List.of(new ScorePoint(5.0, 0.0), new ScorePoint(9.0, 1.0)), Map.of());
        MpoDefinition mpo = new MpoDefinition("lead_mpo", "Lead MPO", List.of(
                new MpoComponentDefinition("pIC50", "potency_score", "Potency", 1.0, true, 0.25)),
                MpoAggregationDefinition.defaults());
        PropertyProfileDefinition profile = new PropertyProfileDefinition("lead_profile", "Lead profile", null,
                List.of(new PropertyProfileItem("pIC50", "potency_score", "Potency", "Activity", 0, true, Map.of())),
                List.of(mpo), Map.of());
        PrismPack.Manifest manifest = new PrismPack.Manifest("0.2", "scored", "Scored", null, null, null,
                new PrismPack.DataframeRef("main", "data/dataframe.tsv", "schema/dataframe.schema.json", "compound", Map.of()),
                null, "semantics/endpoints.json", null, null, null,
                "semantics/scores.json", "semantics/property-profiles.json", null, Map.of());
        PrismPack.DataFrame data = new PrismPack.DataFrame(List.of("subject_id", "pIC50"),
                List.of(List.of("CMP-1", "8.0"), List.of("CMP-2", "5.5")));
        PrismPack.DataFrameSchema schema = new PrismPack.DataFrameSchema(List.of(
                new PrismPack.Column("subject_id", "string", "compound_id", "Subject", "identifier", null, null, null, null, Map.of()),
                new PrismPack.Column("pIC50", "number", "endpoint_value", "pIC50", null, null, "pIC50", "higher_is_better", null, Map.of())), Map.of());
        PrismPack.EndpointMetadata endpoints = new PrismPack.EndpointMetadata(List.of(
                new PrismPack.Endpoint("pIC50", "pIC50", "pIC50", null, "higher_is_better", null, null, Map.of())), Map.of());
        return new PrismPack(manifest, data, schema, null, endpoints, null, null, null,
                new PrismPack.ScoreMetadata(List.of(score), Map.of()),
                new PrismPack.PropertyProfileMetadata(List.of(profile), Map.of()), Map.of(), List.of());
    }
}

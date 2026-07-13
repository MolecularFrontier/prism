package tech.molecules.structurized.prism.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.molecules.structurized.prism.score.EndpointScoreDefinition;
import tech.molecules.structurized.prism.score.MpoAggregationDefinition;
import tech.molecules.structurized.prism.score.MpoComponentDefinition;
import tech.molecules.structurized.prism.score.MpoDefinition;
import tech.molecules.structurized.prism.score.PropertyProfileDefinition;
import tech.molecules.structurized.prism.score.PropertyProfileItem;
import tech.molecules.structurized.prism.score.ScorePoint;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrismPackScoreMetadataTest {
    @TempDir
    Path tempDir;

    @Test
    void v02DefinitionsRoundTripThroughZip() throws Exception {
        PrismPack source = pack();
        Path zip = tempDir.resolve("scored.prismpack");

        PrismPackWriter.writeZip(zip, source);
        PrismPack loaded = PrismPackReader.read(zip);

        assertEquals("0.2", loaded.manifest().prismPackVersion());
        assertEquals("potency_score", loaded.scores().scores().getFirst().id());
        assertEquals("lead_profile", loaded.propertyProfiles().profiles().getFirst().id());
        assertEquals("lead_mpo", loaded.propertyProfiles().profiles().getFirst().mpos().getFirst().id());
        assertTrue(loaded.warnings().isEmpty());
    }

    static PrismPack pack() {
        EndpointScoreDefinition score = new EndpointScoreDefinition(
                "potency_score", "pIC50", "Potency", null, "line_segment_v1", "linear", true,
                List.of(new ScorePoint(5.0, 0.0), new ScorePoint(9.0, 1.0)), Map.of("source", "external_system"));
        MpoDefinition mpo = new MpoDefinition("lead_mpo", "Lead MPO", List.of(
                new MpoComponentDefinition("pIC50", "potency_score", "Potency", 1.0, true, 0.25)),
                MpoAggregationDefinition.defaults());
        PropertyProfileDefinition profile = new PropertyProfileDefinition(
                "lead_profile", "Lead profile", "Portable property profile",
                List.of(new PropertyProfileItem("pIC50", "potency_score", "Potency", "Activity", 0, true, Map.of())),
                List.of(mpo), Map.of("sourceViewPath", "project/lead"));
        PrismPack.Manifest manifest = new PrismPack.Manifest(
                "0.2", "scored", "Scored", null, null, null,
                new PrismPack.DataframeRef("main", PrismPackWriter.DATAFRAME_PATH, PrismPackWriter.SCHEMA_PATH, "compound", Map.of()),
                null, PrismPackWriter.ENDPOINTS_PATH, null, null, null,
                PrismPackWriter.SCORES_PATH, PrismPackWriter.PROPERTY_PROFILES_PATH, null, Map.of());
        PrismPack.DataFrame data = new PrismPack.DataFrame(List.of("subject_id", "pIC50"),
                List.of(List.of("CMP-1", "8.0"), List.of("CMP-2", "5.5")));
        PrismPack.DataFrameSchema schema = new PrismPack.DataFrameSchema(List.of(
                new PrismPack.Column("subject_id", "string", "compound_id", "Subject", "identifier", null, null, null, null, Map.of()),
                new PrismPack.Column("pIC50", "number", "endpoint_value", "pIC50", null, null, "pIC50", "higher_is_better", null, Map.of())
        ), Map.of());
        PrismPack.EndpointMetadata endpoints = new PrismPack.EndpointMetadata(List.of(
                new PrismPack.Endpoint("pIC50", "pIC50", "pIC50", null, "higher_is_better", null, null, Map.of())), Map.of());
        return new PrismPack(manifest, data, schema, null, endpoints, null, null, null,
                new PrismPack.ScoreMetadata(List.of(score), Map.of()),
                new PrismPack.PropertyProfileMetadata(List.of(profile), Map.of()), Map.of(), List.of());
    }
}

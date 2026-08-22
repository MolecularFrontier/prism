package tech.molecules.structurized.prism.pack;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.result.BooleanResult;
import tech.molecules.structurized.prism.result.CategoricalResult;
import tech.molecules.structurized.prism.result.EndpointResult;
import tech.molecules.structurized.prism.result.NumericResult;
import tech.molecules.structurized.prism.result.NumericState;
import tech.molecules.structurized.prism.result.OptionalNumericResult;
import tech.molecules.structurized.prism.result.OptionalNumericState;
import tech.molecules.structurized.prism.result.PrismNumericDatapoint;
import tech.molecules.structurized.prism.result.TextResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EndpointResultCodecTest {
    @Test
    void roundTripsCompleteNumericEvidence() {
        NumericResult source = NumericResult.builder().state(NumericState.VALUE).mean(23.0).lower(20.0).upper(26.0)
                .rawValues(List.of(22.0, 24.0)).rawValueIds(List.of("r1", "r2")).n(2)
                .firstMeasurement("2026-01-01").lastMeasurement("2026-01-03")
                .details(Map.of("workflow", Map.of("type", "assay"), "quality", 0.9))
                .datapoints(List.of(PrismNumericDatapoint.builder().value(22.0).unprocessedValue("22")
                        .date("2026-01-01").batch("B1").sourceId("r1").metadata(Map.of("plate", 7.0)).build()))
                .build();

        EndpointResult decoded = EndpointResultCodec.decodeJson(EndpointResultCodec.encodeJson(source));

        assertEquals(source, decoded);
    }

    @Test
    void roundTripsExplicitOptionalMissingState() {
        OptionalNumericResult source = OptionalNumericResult.builder().state(OptionalNumericState.NOT_APPLICABLE)
                .details(Map.of("reason", "outside domain")).build();

        assertEquals(source, EndpointResultCodec.decode(EndpointResultCodec.encode(source)));
    }

    @Test
    void roundTripsBooleanCategoricalAndTextResults() {
        List<EndpointResult> results = List.of(
                BooleanResult.builder().value(true).n(1).rawValueIds(List.of("b1")).build(),
                CategoricalResult.builder().value("active").details(Map.of("authority", "curated")).build(),
                TextResult.builder().text("reviewed observation").lastMeasurement("2026-08-22").build());

        for (EndpointResult result : results) {
            assertEquals(result, EndpointResultCodec.decodeJson(EndpointResultCodec.encodeJson(result)));
        }
    }
}

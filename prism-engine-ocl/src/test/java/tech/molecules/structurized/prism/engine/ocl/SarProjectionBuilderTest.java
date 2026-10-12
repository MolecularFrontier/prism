package tech.molecules.structurized.prism.engine.ocl;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismColumnSchema;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismOperationResult;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.RowIdMaterializedColumnData;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SarProjectionBuilderTest {
    @Test
    void buildsOneDimensionalProjectionWithValuesScoresContextAndSpecialRows() throws Exception {
        PrismSession session = sarSession();

        var model = SarProjectionBuilder.build1D(session.snapshot(), new Sar1DViewSpec(
                "sar-r1", "R1 SAR", "sar.test.matched", "sar.test.R1", List.of(),
                List.of(
                        new SarValueSpec("activity", "Activity", "0.0", SarAggregation.MEAN, "activity.score"),
                        new SarValueSpec("activity.score", "Score", "0.00", SarAggregation.MEAN, null)
                ),
                50, true
        ));

        assertEquals(1, model.rows().size());
        assertEquals(1, model.excludedRowCount());
        assertEquals(List.of("sar.test.R2", "sar.test.R3"), model.contextColumnIds());
        var row = model.rows().getFirst();
        assertEquals(2, row.contributingRowIds().size());
        assertEquals(2, row.contextVariantCount());
        assertTrue(row.mixedContext());
        assertEquals(7.0, row.values().get(0).value());
        assertEquals(0.5, row.values().get(0).score());
        assertEquals(2, row.values().get(0).valueCount());
        assertEquals(0.5, row.values().get(1).value());
        assertFalse(model.truncated());
    }

    @Test
    void buildsSparseTwoDimensionalProjectionAndRetainsContributingRows() throws Exception {
        PrismSession session = sarSession();

        var model = SarProjectionBuilder.build2D(session.snapshot(), new Sar2DViewSpec(
                "sar-r1-r2", "R1 x R2", "sar.test.matched", "sar.test.R1", "sar.test.R2",
                List.of(), List.of(new SarValueSpec(
                        "activity", "Activity", "0.0", SarAggregation.BEST, "activity.score")),
                24, 24, true
        ));

        assertEquals(1, model.rowSubstituents().size());
        assertEquals(2, model.columnSubstituents().size());
        assertEquals(2, model.cells().size());
        assertEquals(1, model.excludedRowCount());
        assertEquals(List.of("sar.test.R3"), model.contextColumnIds());
        assertTrue(model.cells().values().stream().allMatch(cell -> cell.contributingRowIds().size() == 1));
        assertTrue(model.cells().values().stream().allMatch(cell -> cell.values().getFirst().valueCount() == 1));
    }

    @Test
    void missingSparseSarValuesAreExcludedRatherThanTreatedAsHydrogen() throws Exception {
        PrismSession session = sarSession();
        String firstRowId = session.rowIdForPhysicalRow(0);
        session.applyOperationResult(PrismOperationResult.builder()
                .addColumnByRowId(column(
                        "sar.partial",
                        PrismColumnType.CATEGORICAL,
                        SarSubstituentCodec.SEMANTIC_TYPE,
                        null,
                        Map.of(firstRowId, SarSubstituentCodec.unsubstituted()),
                        Map.of("sarAnalysisId", "partial")
                ))
                .build());

        SarSubstituent missing = SarProjectionBuilder.substituent(session.table().column("sar.partial"), 1);

        assertEquals(SarSubstituent.Type.UNMATCHED, missing.type());
        assertFalse(missing.isProjectable());
    }

    private static PrismSession sarSession() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        List<String> rowIds = java.util.stream.IntStream.range(0, session.totalRowCount())
                .mapToObj(session::rowIdForPhysicalRow)
                .toList();
        LinkedHashSet<String> rows = new LinkedHashSet<>(rowIds);
        Map<String, Object> metadata = Map.of(
                "sarAnalysisId", "test-analysis",
                "sarEncoding", SarSubstituentCodec.ENCODING
        );
        PrismOperationResult.Builder operation = PrismOperationResult.builder()
                .addRowSet(new PrismRowSet("sar.test.matched", "Matched", "", rows, metadata))
                .addColumnByRowId(column("activity", PrismColumnType.NUMERIC, "activity_value",
                        "higher_is_better", values(rowIds, 6.0, 8.0, 10.0), Map.of()))
                .addColumnByRowId(column("activity.score", PrismColumnType.NUMERIC, "endpoint_score",
                        "higher_is_better", values(rowIds, 0.2, 0.8, 1.0), Map.of()))
                .addColumnByRowId(column("sar.test.R1", PrismColumnType.CATEGORICAL,
                        SarSubstituentCodec.SEMANTIC_TYPE, null, values(rowIds,
                                SarSubstituentCodec.substituent("fragment-a"),
                                SarSubstituentCodec.substituent("fragment-a"),
                                SarSubstituentCodec.multiAttachment()), metadata))
                .addColumnByRowId(column("sar.test.R2", PrismColumnType.CATEGORICAL,
                        SarSubstituentCodec.SEMANTIC_TYPE, null, values(rowIds,
                                SarSubstituentCodec.substituent("fragment-x"),
                                SarSubstituentCodec.substituent("fragment-y"),
                                SarSubstituentCodec.substituent("fragment-x")), metadata))
                .addColumnByRowId(column("sar.test.R3", PrismColumnType.CATEGORICAL,
                        SarSubstituentCodec.SEMANTIC_TYPE, null, values(rowIds,
                                SarSubstituentCodec.unsubstituted(),
                                SarSubstituentCodec.substituent("fragment-z"),
                                SarSubstituentCodec.substituent("fragment-x")), metadata));
        session.applyOperationResult(operation.build());
        return session;
    }

    private static RowIdMaterializedColumnData column(
            String id,
            PrismColumnType type,
            String semanticType,
            String direction,
            Map<String, ?> values,
            Map<String, Object> metadata
    ) {
        return new RowIdMaterializedColumnData(
                new PrismColumnSchema(id, type, id, semanticType, "analysis_result",
                        null, null, direction, null, metadata),
                values,
                metadata
        );
    }

    private static Map<String, Object> values(List<String> rowIds, Object... values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < rowIds.size(); index++) {
            result.put(rowIds.get(index), values[index]);
        }
        return result;
    }
}

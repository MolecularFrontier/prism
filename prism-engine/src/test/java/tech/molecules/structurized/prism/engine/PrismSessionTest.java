package tech.molecules.structurized.prism.engine;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrismSessionTest {
    @Test
    void loadsExamplePackWithSchemaAndDefaultView() throws Exception {
        PrismSession session = exampleSession();

        assertEquals(3, session.totalRowCount());
        assertEquals(3, session.visibleRowCount());
        assertEquals("compound_id", session.visibleColumnId(0));
        assertEquals(PrismColumnType.MOLECULE, session.table().column("smiles").type());
        assertEquals(PrismColumnType.NUMERIC, session.table().column("pIC50").type());
    }

    @Test
    void numericRangeFilterComputesVisibleRows() throws Exception {
        PrismSession session = exampleSession();

        session.addFilter(new NumericRangeFilter("pIC50", 6.5, null, false));

        assertEquals(2, session.visibleRowCount());
        assertEquals("CMPD-001", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(0), "compound_id"));
        assertEquals("CMPD-002", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(1), "compound_id"));
    }

    @Test
    void textAndCategoryFiltersAreAndCombined() throws Exception {
        PrismSession session = exampleSession();

        session.setFilters(List.of(
                new CategoryIncludeFilter("series", Set.of("A"), false),
                new TextContainsFilter("comment", "interest", true, false)
        ));

        assertEquals(1, session.visibleRowCount());
        assertEquals("CMPD-001", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(0), "compound_id"));
    }

    @Test
    void textPatternFilterSupportsSubstringAndRegexModes() throws Exception {
        PrismSession session = exampleSession();

        session.addFilter(new TextPatternFilter("compound_id", "cmpd-00[12]", TextPatternMode.REGEX, true, false));

        assertEquals(2, session.visibleRowCount());
        assertEquals("CMPD-001", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(0), "compound_id"));
        assertEquals("CMPD-002", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(1), "compound_id"));

        session.setFilters(List.of(new TextPatternFilter("comment", "interesting", TextPatternMode.SUBSTRING, true, false)));

        assertEquals(1, session.visibleRowCount());
        assertEquals("CMPD-001", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(0), "compound_id"));
    }

    @Test
    void missingFilterFindsEmptyCells() throws Exception {
        PrismSession session = exampleSession();

        session.addFilter(new MissingValueFilter("comment", MissingValueMode.MISSING));

        assertEquals(1, session.visibleRowCount());
        assertEquals("CMPD-002", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(0), "compound_id"));
    }

    @Test
    void sortingUsesEngineVisibleOrderAndKeepsStablePhysicalRows() throws Exception {
        PrismSession session = exampleSession();

        session.setSortKeys(List.of(SortKey.desc("HLM_CLint")));

        assertEquals("CMPD-003", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(0), "compound_id"));
        assertEquals("CMPD-002", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(1), "compound_id"));
        assertEquals("CMPD-001", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(2), "compound_id"));
    }

    @Test
    void selectionAndFlagsReferencePhysicalRows() throws Exception {
        PrismSession session = exampleSession();
        session.viewState().selectionModel().setSelected(2, true);
        session.viewState().flagModel().setFlagged("Interesting", 0, true);

        session.setSortKeys(List.of(SortKey.asc("pIC50")));

        assertTrue(session.viewState().selectionModel().isSelected(2));
        assertTrue(session.viewState().flagModel().isFlagged("Interesting", 0));
        assertFalse(session.viewState().flagModel().isFlagged("Interesting", 1));
    }

    @Test
    void stableRowIdsSurviveSortingAndFiltering() throws Exception {
        PrismSession session = exampleSession();

        assertEquals("CMPD-001", session.rowIdForPhysicalRow(0));
        assertEquals(2, session.physicalRowForRowId("CMPD-003").orElseThrow());

        session.setSortKeys(List.of(SortKey.asc("pIC50")));
        session.addFilter(new NumericRangeFilter("pIC50", 5.0, null, false));

        assertEquals("CMPD-003", session.rowIdForPhysicalRow(session.physicalRowAtVisibleIndex(0)));
        assertEquals(2, session.physicalRowForRowId("CMPD-003").orElseThrow());
    }

    @Test
    void rowSetFiltersUseStableRowIds() throws Exception {
        PrismSession session = exampleSession();
        session.addRowSet(new PrismRowSet("preferred", "Preferred", "", Set.of("CMPD-001", "CMPD-003"), Map.of()));

        session.filterToRowSet("preferred");

        assertEquals(2, session.visibleRowCount());
        assertEquals("CMPD-001", session.rowIdForPhysicalRow(session.physicalRowAtVisibleIndex(0)));
        assertEquals("CMPD-003", session.rowIdForPhysicalRow(session.physicalRowAtVisibleIndex(1)));
    }

    @Test
    void materializedColumnsAreVisibleThroughRuntimeTable() throws Exception {
        PrismSession session = exampleSession();
        MaterializedColumnData score = new MaterializedColumnData(
                new PrismColumnSchema("ai_score", PrismColumnType.NUMERIC, "AI Score", "ai_score", "augmentation", null, null, null, null, Map.of()),
                List.of(0.8, 0.4, 0.9),
                Map.of("origin", "test")
        );

        session.addMaterializedColumn(score, true);

        assertEquals(PrismColumnType.NUMERIC, session.table().column("ai_score").type());
        assertEquals(0.9, session.table().valueAt(2, "ai_score"));
        assertEquals("ai_score", session.visibleColumnId(session.visibleColumnCount() - 1));
    }

    @Test
    void operationRegistryRunsAndAppliesStructuredResults() throws Exception {
        PrismSession session = exampleSession();
        session.operationRegistry().register(new PrismOperation() {
            private final PrismOperationDescriptor descriptor = new PrismOperationDescriptor(
                    "test.add_results",
                    "1",
                    "Add Results",
                    "",
                    List.of(),
                    Set.of(PrismOperationEffect.ADD_COLUMNS, PrismOperationEffect.ADD_ROW_SETS),
                    PrismExecutionProfile.INTERACTIVE
            );

            @Override
            public PrismOperationDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public PrismOperationResult execute(PrismSessionSnapshot snapshot, Map<String, Object> parameters) {
                return PrismOperationResult.builder()
                        .addColumn(new MaterializedColumnData(
                                new PrismColumnSchema("operation_score", PrismColumnType.NUMERIC, "Operation Score", "operation_score", "augmentation", null, null, null, null, Map.of()),
                                List.of(1.0, 2.0, 3.0),
                                Map.of("operationId", descriptor.id())
                        ))
                        .addRowSet(new PrismRowSet("operation_rows", "Operation Rows", "", Set.of(snapshot.rowIdIndex().rowId(1)), Map.of()))
                        .build();
            }
        });

        PrismOperationResult result = session.runOperation("test.add_results", Map.of());

        assertEquals(1, result.addedColumns().size());
        assertEquals(1, result.addedRowSets().size());
        assertEquals(3.0, session.table().valueAt(2, "operation_score"));
        assertEquals(Set.of("CMPD-002"), session.rowSet("operation_rows").rowIds());
    }

    @Test
    void operationParametersAreValidatedAndConvertedCentrally() throws Exception {
        PrismSession session = exampleSession();
        session.operationRegistry().register(new PrismOperation() {
            private final PrismOperationDescriptor descriptor = new PrismOperationDescriptor(
                    "test.validated",
                    "1",
                    "Validated",
                    "",
                    List.of(
                            PrismOperationParameter.requiredColumn("column", "Column", null),
                            PrismOperationParameter.requiredEnum("mode", "Mode", List.of("A", "B")),
                            new PrismOperationParameter("threshold", PrismOperationParameterType.NUMBER, "Threshold", "", true, List.of(), Map.of())
                    ),
                    Set.of(),
                    PrismExecutionProfile.INTERACTIVE
            );

            @Override
            public PrismOperationDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public PrismOperationResult execute(PrismSessionSnapshot snapshot, Map<String, Object> parameters) {
                assertEquals("pIC50", parameters.get("column"));
                assertEquals("B", parameters.get("mode"));
                assertEquals(0.7, parameters.get("threshold"));
                return PrismOperationResult.builder().build();
            }
        });

        session.runOperation("test.validated", Map.of("column", "pIC50", "mode", "b", "threshold", "0.7"));

        PrismOperationException missing = assertThrows(PrismOperationException.class,
                () -> session.runOperation("test.validated", Map.of("column", "pIC50", "threshold", 1.0)));
        assertEquals("MISSING_PARAMETER", missing.errorCode());
        assertEquals("mode", missing.parameterName());

        PrismOperationException unknown = assertThrows(PrismOperationException.class,
                () -> session.runOperation("test.validated", Map.of("column", "pIC50", "mode", "A", "threshold", 1.0, "extra", true)));
        assertEquals("UNKNOWN_PARAMETER", unknown.errorCode());
    }

    @Test
    void rowIdKeyedMaterializedColumnsAreMaterializedByStableRowId() throws Exception {
        PrismSession session = exampleSession();
        PrismOperationResult result = PrismOperationResult.builder()
                .addColumnByRowId(new RowIdMaterializedColumnData(
                        new PrismColumnSchema("row_id_score", PrismColumnType.NUMERIC, "Row ID Score", "ai_score", "augmentation", null, null, null, null, Map.of()),
                        Map.of("CMPD-003", 0.9, "CMPD-001", 0.4),
                        Map.of("origin", "test")
                ))
                .build();

        session.applyOperationResult(result);

        assertEquals(0.4, session.table().valueAt(0, "row_id_score"));
        assertTrue(session.table().column("row_id_score").isMissing(1));
        assertEquals(0.9, session.table().valueAt(2, "row_id_score"));
    }

    @Test
    void operationResultsAreAppliedAtomically() throws Exception {
        PrismSession session = exampleSession();
        session.addRowSet(new PrismRowSet("existing", "Existing", "", Set.of("CMPD-001"), Map.of()));
        PrismOperationResult invalid = PrismOperationResult.builder()
                .addColumn(new MaterializedColumnData(
                        new PrismColumnSchema("should_not_apply", PrismColumnType.NUMERIC, "Should Not Apply", "ai_score", "augmentation", null, null, null, null, Map.of()),
                        List.of(1.0, 2.0, 3.0),
                        Map.of()
                ))
                .addRowSet(new PrismRowSet("existing", "Duplicate", "", Set.of("CMPD-002"), Map.of()))
                .build();

        PrismOperationException exception = assertThrows(PrismOperationException.class,
                () -> session.applyOperationResult(invalid));

        assertEquals("ROW_SET_EXISTS", exception.errorCode());
        assertTrue(session.table().findColumn("should_not_apply").isEmpty());
        assertEquals(Set.of("CMPD-001"), session.rowSet("existing").rowIds());
    }

    @Test
    void rowIdKeyedMaterializedColumnsRejectUnknownRowIdsAtomically() throws Exception {
        PrismSession session = exampleSession();
        PrismOperationResult invalid = PrismOperationResult.builder()
                .addColumnByRowId(new RowIdMaterializedColumnData(
                        new PrismColumnSchema("unknown_row_score", PrismColumnType.NUMERIC, "Unknown Row Score", "ai_score", "augmentation", null, null, null, null, Map.of()),
                        Map.of("NOT-A-ROW", 0.1),
                        Map.of()
                ))
                .build();

        PrismOperationException exception = assertThrows(PrismOperationException.class,
                () -> session.applyOperationResult(invalid));

        assertEquals("UNKNOWN_ROW_ID", exception.errorCode());
        assertTrue(session.table().findColumn("unknown_row_score").isEmpty());
    }

    @Test
    void activeRowsReturnsDefensiveCopy() throws Exception {
        PrismSession session = exampleSession();
        BitSet activeRows = session.activeRows();
        activeRows.clear();

        assertEquals(3, session.activeRows().cardinality());
    }

    private static PrismSession exampleSession() throws Exception {
        return PrismSession.open(Path.of("..", "examples", "example.prismpack"));
    }
}

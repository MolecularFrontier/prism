package tech.molecules.structurized.prism.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
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
    void indexesPredictionCapabilitiesFromPack(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("data"));
        Files.createDirectories(tempDir.resolve("schema"));
        Files.createDirectories(tempDir.resolve("semantics"));
        Files.writeString(tempDir.resolve("prism-pack.json"), """
                {"prismPackVersion":"0.2","dataframe":{"path":"data/dataframe.tsv","schema":"schema/dataframe.schema.json"},"endpoints":"semantics/endpoints.json","predictions":"semantics/predictions.json"}
                """);
        Files.writeString(tempDir.resolve("schema/dataframe.schema.json"), """
                {"columns":[{"name":"compound_id","type":"string"},{"name":"smiles","type":"string","semanticType":"chemical_structure"},{"name":"hlm_clint","type":"number","endpointId":"hlm_clint"}]}
                """);
        Files.writeString(tempDir.resolve("data/dataframe.tsv"), """
                compound_id	smiles	hlm_clint
                CMP-1	CCN	12.0
                """);
        Files.writeString(tempDir.resolve("semantics/endpoints.json"), """
                {"endpoints":[{"id":"hlm_clint","column":"hlm_clint","displayName":"HLM CLint"}]}
                """);
        Files.writeString(tempDir.resolve("semantics/predictions.json"), """
                {"capabilities":[{"capabilityId":"apy.hlm.production","endpointId":"hlm_clint","predictedEndpointId":"hlm_clint.predicted","displayName":"APY HLM production","providerId":"apy","workflowId":"apy://hlm-production","priority":100}]}
                """);

        PrismSession session = PrismSession.open(tempDir);

        assertEquals(1, session.predictionCapabilities().size());
        assertEquals("apy.hlm.production", session.predictionCapabilitiesFor("hlm_clint").getFirst().capabilityId());
        assertEquals("hlm_clint.predicted", session.predictionCapability("apy.hlm.production").predictedEndpointId());
        assertTrue(session.predictionCapabilitiesFor("unknown").isEmpty());
    }

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
    void scatterPlotOperationCreatesViewRecord() throws Exception {
        PrismSession session = exampleSession();
        session.operationRegistry().register(new CreateScatterPlotViewOperation());
        session.addRowSet(new PrismRowSet("preferred", "Preferred", "", Set.of("CMPD-001", "CMPD-002"), Map.of()));

        PrismOperationResult result = session.runOperation(CreateScatterPlotViewOperation.ID, Map.of(
                "viewId", "scatter:potency-clearance",
                "title", "Potency versus Clearance",
                "rowSetId", "preferred",
                "xColumnId", "pIC50",
                "yColumnId", "HLM_CLint",
                "colorColumnId", "series",
                "xMin", "5.0",
                "xMax", "9.0"
        ));

        assertEquals(1, result.addedViews().size());
        ScatterPlotViewSpec spec = (ScatterPlotViewSpec) session.view("scatter:potency-clearance").specification();
        assertEquals("Potency versus Clearance", spec.title());
        assertEquals("preferred", spec.rowSetId());
        assertEquals("pIC50", spec.xColumnId());
        assertEquals("HLM_CLint", spec.yColumnId());
        assertEquals("series", spec.colorColumnId());
        assertEquals(5.0, spec.xMin());
        assertEquals(9.0, spec.xMax());
    }

    @Test
    void scatterPlotOperationRejectsNonNumericAxes() throws Exception {
        PrismSession session = exampleSession();
        session.operationRegistry().register(new CreateScatterPlotViewOperation());

        PrismOperationException exception = assertThrows(PrismOperationException.class, () ->
                session.runOperation(CreateScatterPlotViewOperation.ID, Map.of(
                        "title", "Bad Scatter",
                        "xColumnId", "compound_id",
                        "yColumnId", "pIC50"
                )));

        assertEquals("INCOMPATIBLE_COLUMN", exception.errorCode());
        assertEquals("xColumnId", exception.parameterName());
        assertEquals(0, session.views().size());
    }

    @Test
    void operationResultsCanAddViews() throws Exception {
        PrismSession session = exampleSession();
        session.addRowSet(new PrismRowSet("preferred", "Preferred", "", Set.of("CMPD-001", "CMPD-003"), Map.of()));
        PrismViewRecord view = PrismViewRecord.of(new TestViewSpec(
                "view-1",
                "test.view",
                "Test View",
                Set.of("preferred"),
                Set.of("smiles", "pIC50")
        ));

        session.applyOperationResult(PrismOperationResult.builder().addView(view).build());

        assertEquals(1, session.views().size());
        assertEquals("Test View", session.view("view-1").title());
    }

    @Test
    void viewResultsRejectDuplicateIdsAndUnknownReferencesAtomically() throws Exception {
        PrismSession session = exampleSession();
        PrismViewRecord valid = PrismViewRecord.of(new TestViewSpec(
                "view-1",
                "test.view",
                "Test View",
                Set.of(),
                Set.of("smiles")
        ));
        session.addView(valid);

        PrismOperationException duplicate = assertThrows(PrismOperationException.class,
                () -> session.addView(valid));
        assertEquals("VIEW_EXISTS", duplicate.errorCode());

        PrismOperationException unknownColumn = assertThrows(PrismOperationException.class,
                () -> session.addView(PrismViewRecord.of(new TestViewSpec(
                        "view-2",
                        "test.view",
                        "Bad Column",
                        Set.of(),
                        Set.of("missing_column")
                ))));
        assertEquals("UNKNOWN_COLUMN", unknownColumn.errorCode());
        assertEquals(1, session.views().size());

        PrismOperationException unknownRowSet = assertThrows(PrismOperationException.class,
                () -> session.addView(PrismViewRecord.of(new TestViewSpec(
                        "view-3",
                        "test.view",
                        "Bad Row Set",
                        Set.of("missing_row_set"),
                        Set.of("smiles")
                ))));
        assertEquals("UNKNOWN_ROW_SET", unknownRowSet.errorCode());
        assertEquals(1, session.views().size());
    }


    @Test
    void operationResultsCanAddRowGraphs() throws Exception {
        PrismSession session = exampleSession();
        session.addRowSet(new PrismRowSet("preferred", "Preferred", "", Set.of("CMPD-001", "CMPD-002"), Map.of()));
        PrismRowGraph graph = new PrismRowGraph(
                "mmp:test",
                "Test MMP Graph",
                "",
                "chemistry.mmp",
                "test",
                1,
                true,
                "preferred",
                List.of(new PrismRowGraphEdge(
                        "edge-1",
                        "CMPD-001",
                        "CMPD-002",
                        "A to B",
                        Map.of("cutCount", 1, "delta", 1.5)
                )),
                Map.of("edgeCount", 1),
                Map.of("source", "test")
        );

        session.applyOperationResult(PrismOperationResult.builder().addGraph(graph).build());

        assertEquals(1, session.graphs().size());
        assertEquals("chemistry.mmp", session.graph("mmp:test").graphType());
        assertEquals(Set.of("CMPD-002"), session.graph("mmp:test").neighborRowIds("CMPD-001"));
        assertEquals(1, session.graphSummaries().getFirst().edgeCount());
        assertEquals(1, session.snapshot().graph("mmp:test").orElseThrow().degree("CMPD-001"));
    }

    @Test
    void rowGraphsRejectUnknownRowsAtomically() throws Exception {
        PrismSession session = exampleSession();
        PrismRowGraph invalid = new PrismRowGraph(
                "bad-graph",
                "Bad Graph",
                "",
                "generic.row_graph",
                null,
                1,
                true,
                null,
                List.of(new PrismRowGraphEdge("edge-1", "CMPD-001", "NOT-A-ROW", "", Map.of())),
                Map.of(),
                Map.of()
        );

        PrismOperationException exception = assertThrows(PrismOperationException.class,
                () -> session.applyOperationResult(PrismOperationResult.builder().addGraph(invalid).build()));

        assertEquals("UNKNOWN_ROW_ID", exception.errorCode());
        assertTrue(session.graphs().isEmpty());
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
    void operationResultsCanUpdateViewsAtomically() throws Exception {
        PrismSession session = exampleSession();
        session.addRowSet(new PrismRowSet("preferred", "Preferred", "", Set.of("CMPD-001"), Map.of()));
        session.addView(PrismViewRecord.of(new TestViewSpec(
                "view:test",
                "test.view",
                "Initial",
                Set.of("preferred"),
                Set.of("smiles")
        )));

        PrismViewRecord updated = PrismViewRecord.of(new TestViewSpec(
                "view:test",
                "test.view",
                "Updated",
                Set.of("preferred"),
                Set.of("pIC50")
        ));
        session.applyOperationResult(PrismOperationResult.builder().updateView(updated).build());

        assertEquals("Updated", session.view("view:test").title());
        assertEquals(Set.of("pIC50"), session.view("view:test").specification().referencedColumnIds());

        PrismOperationException exception = assertThrows(PrismOperationException.class,
                () -> session.applyOperationResult(PrismOperationResult.builder()
                        .updateView(PrismViewRecord.of(new TestViewSpec(
                                "view:test",
                                "test.view",
                                "Invalid",
                                Set.of("missing"),
                                Set.of("smiles")
                        )))
                        .build()));

        assertEquals("UNKNOWN_ROW_SET", exception.errorCode());
        assertEquals("Updated", session.view("view:test").title());
    }

    @Test
    void activeRowsReturnsDefensiveCopy() throws Exception {
        PrismSession session = exampleSession();
        BitSet activeRows = session.activeRows();
        activeRows.clear();

        assertEquals(3, session.activeRows().cardinality());
    }


    @Test
    void opensMoonshotPackWithConfiguredFullView() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "moonshot-medchem.prismpack"));

        assertEquals(2062, session.totalRowCount());
        assertEquals(2062, session.visibleRowCount());
        assertEquals(13, session.visibleColumnCount());
        assertEquals("compound_id", session.visibleColumnId(0));
        assertEquals("mpro_fluorescence_pIC50", session.visibleColumnId(3));
        assertEquals(PrismColumnType.MOLECULE, session.table().column("smiles").type());
        assertEquals(PrismColumnType.NUMERIC, session.table().column("mpro_fluorescence_pIC50").type());
        double firstPotency = (double) session.valueAtVisible(0, 3);
        double secondPotency = (double) session.valueAtVisible(1, 3);
        assertTrue(firstPotency >= secondPotency);
    }

    @Test
    void opensCoaddPackWithConfiguredFullView() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "coadd-antimicrobial.prismpack"));

        assertEquals(4803, session.totalRowCount());
        assertEquals(4803, session.visibleRowCount());
        assertEquals(15, session.visibleColumnCount());
        assertEquals("compound_id", session.visibleColumnId(0));
        assertEquals("coadd_cc50_ma_007_ug_ml", session.visibleColumnId(3));
        assertEquals(PrismColumnType.MOLECULE, session.table().column("smiles").type());
        assertEquals(PrismColumnType.NUMERIC, session.table().column("coadd_mic_gn_001_ug_ml").type());
        double firstMic = (double) session.table().valueAt(session.physicalRowAtVisibleIndex(0), "coadd_mic_gn_001_ug_ml");
        double secondMic = (double) session.table().valueAt(session.physicalRowAtVisibleIndex(1), "coadd_mic_gn_001_ug_ml");
        assertTrue(firstMic <= secondMic);
    }

    @Test
    void opensSparkAchaogenPackWithConfiguredFullView() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "spark-achaogen.prismpack"));

        assertEquals(1873, session.totalRowCount());
        assertEquals(1873, session.visibleRowCount());
        assertEquals(22, session.visibleColumnCount());
        assertEquals("compound_id", session.visibleColumnId(0));
        assertEquals("spark_achaogen_lpxc_pic50", session.visibleColumnId(3));
        assertEquals(PrismColumnType.MOLECULE, session.table().column("smiles").type());
        assertEquals(PrismColumnType.NUMERIC, session.table().column("spark_achaogen_lpxc_pic50").type());
        assertEquals("SPK-0125656", session.rowIdForPhysicalRow(session.physicalRowAtVisibleIndex(0)));
        double firstPotency = (double) session.valueAtVisible(0, 3);
        double secondPotency = (double) session.valueAtVisible(1, 3);
        assertTrue(firstPotency >= secondPotency);
    }

    @Test
    void opensMoleculeAcePackWithConfiguredFullView() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "moleculeace-chembl2034-ki.prismpack"));

        assertEquals(750, session.totalRowCount());
        assertEquals(750, session.visibleRowCount());
        assertEquals(11, session.visibleColumnCount());
        assertEquals("compound_id", session.visibleColumnId(0));
        assertEquals("p_activity", session.visibleColumnId(6));
        assertEquals(PrismColumnType.MOLECULE, session.table().column("smiles").type());
        assertEquals(PrismColumnType.NUMERIC, session.table().column("p_activity").type());
        assertEquals(10.0, (double) session.valueAtVisible(0, 6));
        double firstPotency = (double) session.valueAtVisible(0, 6);
        double secondPotency = (double) session.valueAtVisible(1, 6);
        assertTrue(firstPotency >= secondPotency);
    }

    @Test
    void opensChemblPublicationPackWithConfiguredFullView() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "chembl-publication-chembl5360622.prismpack"));

        assertEquals(224, session.totalRowCount());
        assertEquals(224, session.visibleRowCount());
        assertEquals(18, session.visibleColumnCount());
        assertEquals("compound_id", session.visibleColumnId(0));
        assertEquals("chembl_chembl5360622_chembl5363957_ic50_chembl4203", session.visibleColumnId(5));
        assertEquals(PrismColumnType.MOLECULE, session.table().column("smiles").type());
        assertEquals(PrismColumnType.NUMERIC, session.table().column("chembl_chembl5360622_chembl5363957_ic50_chembl4203").type());
        assertEquals(9.0, (double) session.valueAtVisible(0, 5));
        double firstPotency = (double) session.valueAtVisible(0, 5);
        double secondPotency = (double) session.valueAtVisible(1, 5);
        assertTrue(firstPotency >= secondPotency);
    }

    private record TestViewSpec(
            String viewId,
            String viewType,
            String title,
            Set<String> referencedRowSetIds,
            Set<String> referencedColumnIds
    ) implements PrismViewSpec {
    }


    private static PrismSession exampleSession() throws Exception {
        return PrismSession.open(Path.of("..", "examples", "example.prismpack"));
    }
}

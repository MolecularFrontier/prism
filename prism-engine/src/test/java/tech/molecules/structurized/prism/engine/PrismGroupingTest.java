package tech.molecules.structurized.prism.engine;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrismGroupingTest {
    @Test
    void exclusiveGroupingPublishesAFilterableScalarFacet() throws Exception {
        PrismSession session = exampleSession();
        PrismGrouping grouping = exclusiveGrouping(null);

        session.addGrouping(grouping);

        assertEquals(grouping, session.grouping("series-clusters"));
        assertEquals("cluster_id", session.visibleColumnId(session.visibleColumnCount() - 1));
        assertEquals("cluster-a", session.table().valueAt(0, "cluster_id"));
        assertEquals("Series A", session.table().formattedValueAt(0, "cluster_id"));
        assertEquals("cluster-b", session.table().valueAt(2, "cluster_id"));
        assertTrue(session.table().column("cluster_id").isMissing(1));

        session.setFilters(List.of(new CategoryIncludeFilter("cluster_id", Set.of("Series A"), false)));

        assertEquals(1, session.visibleRowCount());
        assertEquals("CMPD-001", session.rowIdForPhysicalRow(session.physicalRowAtVisibleIndex(0)));
    }

    @Test
    void groupingFacetCanRemainHiddenWithoutLosingItsTableRepresentation() throws Exception {
        PrismSession session = exampleSession();

        session.addGrouping(exclusiveGrouping(null), false);

        assertFalse(session.viewState().visibleColumns().contains("cluster_id"));
        assertTrue(session.table().findColumn("cluster_id").isPresent());
        assertTrue(session.isGroupingFacetColumn("cluster_id"));
    }

    @Test
    void overlappingGroupingSupportsHierarchyAndMultipleMembershipsWithoutScalarFacet() {
        PrismGrouping grouping = new PrismGrouping(
                "motifs",
                "Structural motifs",
                "",
                null,
                PrismGroupingMode.OVERLAPPING,
                List.of(
                        group("heterocycles", "Heterocycles", null, null),
                        group("pyridines", "Pyridines", "heterocycles", "CMPD-001"),
                        group("basic", "Basic centers", null, null)
                ),
                List.of(
                        membership("CMPD-001", "pyridines", 1.0),
                        membership("CMPD-001", "basic", 0.8)
                ),
                null,
                Map.of("operationId", "test.motifs")
        );

        assertEquals(2, grouping.membershipsForRow("CMPD-001").size());
        assertEquals(Set.of("CMPD-001"), grouping.rowsInGroup("pyridines"));
        assertEquals("heterocycles", grouping.group("pyridines").parentGroupId());
        assertNull(grouping.facetColumnId());
        assertThrows(IllegalStateException.class, grouping::facetSchema);
        assertThrows(IllegalStateException.class, () -> grouping.exclusiveMembership("CMPD-001"));
    }

    @Test
    void groupingModelRejectsInvalidMembershipsHierarchyAndRepresentatives() {
        assertThrows(IllegalArgumentException.class, () -> new PrismGrouping(
                "exclusive",
                "Exclusive",
                "",
                null,
                PrismGroupingMode.EXCLUSIVE,
                List.of(group("a", "A", null, null), group("b", "B", null, null)),
                List.of(membership("CMPD-001", "a", null), membership("CMPD-001", "b", null)),
                "exclusive.group",
                Map.of()
        ));

        assertThrows(IllegalArgumentException.class, () -> new PrismGrouping(
                "cycle",
                "Cycle",
                "",
                null,
                PrismGroupingMode.OVERLAPPING,
                List.of(group("a", "A", "b", null), group("b", "B", "a", null)),
                List.of(),
                null,
                Map.of()
        ));

        assertThrows(IllegalArgumentException.class, () -> new PrismGrouping(
                "representative",
                "Representative",
                "",
                null,
                PrismGroupingMode.EXCLUSIVE,
                List.of(group("a", "A", null, "CMPD-002")),
                List.of(membership("CMPD-001", "a", null)),
                "representative.group",
                Map.of()
        ));

        assertThrows(IllegalArgumentException.class,
                () -> membership("CMPD-001", "a", 1.1));
    }

    @Test
    void operationPublicationValidatesGroupingScopeAndColumnCollisionsAtomically() throws Exception {
        PrismSession session = exampleSession();
        PrismRowSet scope = new PrismRowSet(
                "scope",
                "Scope",
                "",
                Set.of("CMPD-001"),
                Map.of()
        );
        PrismGrouping outsideScope = new PrismGrouping(
                "series-clusters",
                "Series clusters",
                "",
                "scope",
                PrismGroupingMode.EXCLUSIVE,
                List.of(group("cluster-a", "Series A", null, null)),
                List.of(membership("CMPD-002", "cluster-a", 0.7)),
                "cluster_id",
                Map.of()
        );
        PrismOperationResult invalid = PrismOperationResult.builder()
                .addRowSet(scope)
                .addGrouping(outsideScope)
                .addColumn(new MaterializedColumnData(
                        new PrismColumnSchema(
                                "should_not_apply",
                                PrismColumnType.NUMERIC,
                                "Should not apply",
                                "test",
                                "augmentation",
                                null,
                                null,
                                null,
                                null,
                                Map.of()
                        ),
                        List.of(1.0, 2.0, 3.0),
                        Map.of()
                ))
                .build();

        PrismOperationException exception = assertThrows(
                PrismOperationException.class,
                () -> session.applyOperationResult(invalid)
        );

        assertEquals("GROUPING_ROW_OUTSIDE_SCOPE", exception.errorCode());
        assertTrue(session.rowSets().isEmpty());
        assertTrue(session.groupings().isEmpty());
        assertTrue(session.table().findColumn("cluster_id").isEmpty());
        assertTrue(session.table().findColumn("should_not_apply").isEmpty());

        PrismGrouping collidingFacet = new PrismGrouping(
                "bad-facet",
                "Bad facet",
                "",
                null,
                PrismGroupingMode.EXCLUSIVE,
                List.of(),
                List.of(),
                "series",
                Map.of()
        );
        PrismOperationException collision = assertThrows(
                PrismOperationException.class,
                () -> session.addGrouping(collidingFacet)
        );
        assertEquals("COLUMN_EXISTS", collision.errorCode());
        assertTrue(session.groupings().isEmpty());
    }

    private static PrismGrouping exclusiveGrouping(String sourceRowSetId) {
        return new PrismGrouping(
                "series-clusters",
                "Series clusters",
                "A reusable row grouping",
                sourceRowSetId,
                PrismGroupingMode.EXCLUSIVE,
                List.of(
                        group("cluster-a", "Series A", null, "CMPD-001"),
                        group("cluster-b", "Series B", null, "CMPD-003")
                ),
                List.of(
                        membership("CMPD-001", "cluster-a", 1.0),
                        membership("CMPD-003", "cluster-b", 0.9)
                ),
                "cluster_id",
                Map.of("operationId", "test.cluster")
        );
    }

    private static PrismGroup group(String id, String label, String parentId, String representativeRowId) {
        return new PrismGroup(id, label, "", parentId, representativeRowId, Map.of());
    }

    private static PrismGroupMembership membership(String rowId, String groupId, Double weight) {
        return new PrismGroupMembership(rowId, groupId, weight, null, Map.of());
    }

    private static PrismSession exampleSession() throws Exception {
        return PrismSession.open(Path.of("..", "examples", "example.prismpack"));
    }
}

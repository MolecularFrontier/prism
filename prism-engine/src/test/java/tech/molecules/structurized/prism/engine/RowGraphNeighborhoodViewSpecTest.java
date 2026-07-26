package tech.molecules.structurized.prism.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RowGraphNeighborhoodViewSpecTest {
    @Test
    void normalizesReferencesAndLimits() {
        RowGraphNeighborhoodViewSpec spec = new RowGraphNeighborhoodViewSpec(
                " view ", "", " graph ", " row-1 ", " structure ", List.of(" pIC50 ", "pIC50", "logD"), 200, true);

        assertEquals(RowGraphNeighborhoodViewSpec.VIEW_TYPE, spec.viewType());
        assertEquals("view", spec.viewId());
        assertEquals("Graph Neighborhood", spec.title());
        assertEquals("graph", spec.graphId());
        assertEquals("row-1", spec.centerRowId());
        assertEquals(120, spec.maxNeighbors());
        assertEquals(RowGraphNeighborhoodEdgeMode.CENTER_ONLY, spec.edgeMode());
        assertEquals(RowGraphNeighborhoodLabelMode.SELECTED_ONLY, spec.labelMode());
        assertEquals(RowGraphNeighborhoodLayoutMode.RADIAL_BY_DEGREE, spec.layoutMode());
        assertEquals(Set.of("structure", "pIC50", "logD"), spec.referencedColumnIds());
        assertEquals(Set.of(), spec.referencedRowSetIds());
    }

    @Test
    void acceptsExplicitDisplayModes() {
        RowGraphNeighborhoodViewSpec spec = new RowGraphNeighborhoodViewSpec(
                "view", "View", "graph", "row", "structure", List.of(), 12, true,
                RowGraphNeighborhoodEdgeMode.INDUCED_NEIGHBORHOOD,
                RowGraphNeighborhoodLabelMode.ALL,
                RowGraphNeighborhoodLayoutMode.RADIAL_BY_DEGREE);

        assertEquals(RowGraphNeighborhoodEdgeMode.INDUCED_NEIGHBORHOOD, spec.edgeMode());
        assertEquals(RowGraphNeighborhoodLabelMode.ALL, spec.labelMode());
        assertEquals(RowGraphNeighborhoodLayoutMode.RADIAL_BY_DEGREE, spec.layoutMode());
    }

    @Test
    void requiresGraphCenterAndStructureColumn() {
        assertThrows(IllegalArgumentException.class, () -> new RowGraphNeighborhoodViewSpec(
                "view", "View", "", "row", "structure", List.of(), 24, false));
        assertThrows(IllegalArgumentException.class, () -> new RowGraphNeighborhoodViewSpec(
                "view", "View", "graph", "", "structure", List.of(), 24, false));
        assertThrows(IllegalArgumentException.class, () -> new RowGraphNeighborhoodViewSpec(
                "view", "View", "graph", "row", "", List.of(), 24, false));
    }
}

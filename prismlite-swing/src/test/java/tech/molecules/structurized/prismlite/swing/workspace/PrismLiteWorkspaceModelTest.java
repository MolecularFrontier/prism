package tech.molecules.structurized.prismlite.swing.workspace;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.NumericRangeFilter;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.RowSetFilter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrismLiteWorkspaceModelTest {
    @Test
    void draftFilterDoesNotAffectSessionUntilApplied() throws Exception {
        PrismSession session = exampleSession();
        PrismLiteWorkspaceModel model = new PrismLiteWorkspaceModel(session);

        model.setDraftFilter("pIC50", new NumericRangeFilter("pIC50", 6.5, null, false));

        assertTrue(model.isDirty("pIC50"));
        assertEquals(3, session.visibleRowCount());
        assertEquals(0, session.viewState().activeFilters().size());

        model.applyDraft("pIC50");

        assertFalse(model.isDirty("pIC50"));
        assertEquals(2, session.visibleRowCount());
        assertEquals(1, session.viewState().activeFilters().size());
    }

    @Test
    void applyDraftPreservesRowSetFilters() throws Exception {
        PrismSession session = exampleSession();
        session.addRowSet(new PrismRowSet("preferred", "Preferred", "", Set.of("CMPD-001", "CMPD-003"), Map.of()));
        session.setFilters(java.util.List.of(new RowSetFilter(session.rowSet("preferred"))));
        PrismLiteWorkspaceModel model = new PrismLiteWorkspaceModel(session);

        model.setDraftFilter("pIC50", new NumericRangeFilter("pIC50", 6.5, null, false));
        model.applyDraft("pIC50");

        assertEquals(2, session.viewState().activeFilters().size());
        assertTrue(session.viewState().activeFilters().stream().anyMatch(RowSetFilter.class::isInstance));
        assertEquals(1, session.visibleRowCount());
        assertEquals("CMPD-001", session.valueAtVisible(0, 0));
    }

    @Test
    void focusedVisibleRowMapsToPhysicalRowAndRowId() throws Exception {
        PrismSession session = exampleSession();
        PrismLiteWorkspaceModel model = new PrismLiteWorkspaceModel(session);

        model.setFocusedVisibleRow(1);

        assertEquals(1, model.focusedPhysicalRow());
        assertEquals("CMPD-002", model.focusedRowId());
        assertTrue(model.focusedVisibleRow().isPresent());
        assertEquals(1, model.focusedVisibleRow().getAsInt());
    }

    @Test
    void focusedRowEmitsRowFocusChangeOnly() throws Exception {
        PrismSession session = exampleSession();
        PrismLiteWorkspaceModel model = new PrismLiteWorkspaceModel(session);
        ArrayList<PrismLiteWorkspaceModel.WorkspaceChange> changes = new ArrayList<>();
        model.addChangeListener(changes::add);

        model.setFocusedVisibleRow(1);

        assertEquals(List.of(PrismLiteWorkspaceModel.WorkspaceChange.ROW_FOCUS), changes);
    }

    @Test
    void storesSwingPresentationState() throws Exception {
        PrismSession session = exampleSession();
        PrismLiteWorkspaceModel model = new PrismLiteWorkspaceModel(session);

        model.setPreferredWidth("pIC50", 144);
        model.setRowHeight(72);

        assertEquals(144, model.preferredWidths().get("pIC50"));
        assertEquals(72, model.rowHeight());
    }

    @Test
    void disabledAndInvertedFiltersRemainGuiManaged() throws Exception {
        PrismSession session = exampleSession();
        PrismLiteWorkspaceModel model = new PrismLiteWorkspaceModel(session);

        model.setDraftFilter("pIC50", new NumericRangeFilter("pIC50", 6.5, null, false));
        model.applyDraft("pIC50");

        assertEquals(2, session.visibleRowCount());

        model.setAppliedFilterInverted("pIC50", true);
        assertEquals(1, session.visibleRowCount());
        assertEquals("CMPD-003", session.valueAtVisible(0, 0));

        model.setAppliedFilterEnabled("pIC50", false);
        assertEquals(3, session.visibleRowCount());
        assertEquals(1, model.appliedColumnFilterStates().size());
        assertFalse(model.appliedColumnFilterState("pIC50").enabled());

        model.setAppliedFilterEnabled("pIC50", true);
        assertEquals(1, session.visibleRowCount());
        assertTrue(model.appliedColumnFilterState("pIC50").inverted());
    }

    private static PrismSession exampleSession() throws Exception {
        return PrismSession.open(Path.of("..", "examples", "example.prismpack"));
    }
}

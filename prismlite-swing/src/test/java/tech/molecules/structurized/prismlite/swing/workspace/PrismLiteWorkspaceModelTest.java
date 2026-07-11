package tech.molecules.structurized.prismlite.swing.workspace;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.NumericRangeFilter;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.RowSetFilter;

import java.nio.file.Path;
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

    private static PrismSession exampleSession() throws Exception {
        return PrismSession.open(Path.of("..", "examples", "example.prismpack"));
    }
}

package tech.molecules.structurized.prism.engine.ocl;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompoundCardProjectionBuilderTest {
    @Test
    void putsReferenceFirstAndProjectsFormattedDeltasAndScores() throws Exception {
        PrismSession session = exampleSession();
        CompoundCardsViewSpec specification = new CompoundCardsViewSpec(
                "cards", "Comparison", "all", "smiles", "compound_id", "CMPD-002",
                List.of(new CompoundCardPropertySpec("pIC50", "Activity", "0.00", true, "pIC50")),
                true, 2);

        var model = CompoundCardProjectionBuilder.build(session.snapshot(), specification);

        assertEquals(3, model.totalCompoundCount());
        assertEquals(2, model.cards().size());
        assertTrue(model.truncated());
        assertEquals("CMPD-002", model.cards().getFirst().rowId());
        assertTrue(model.cards().getFirst().reference());
        assertEquals("6.80", model.cards().getFirst().values().getFirst().formattedValue());
        assertNull(model.cards().getFirst().values().getFirst().formattedDelta());
        assertEquals("CMPD-001", model.cards().get(1).rowId());
        assertEquals("+0.40", model.cards().get(1).values().getFirst().formattedDelta());
        assertEquals(7.2, model.cards().get(1).values().getFirst().score());
    }

    private static PrismSession exampleSession() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        LinkedHashSet<String> rowIds = new LinkedHashSet<>();
        for (int row = 0; row < session.totalRowCount(); row++) rowIds.add(session.rowIdForPhysicalRow(row));
        session.addRowSet(new PrismRowSet("all", "All", "", rowIds, Map.of()));
        return session;
    }
}

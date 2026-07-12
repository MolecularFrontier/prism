package tech.molecules.structurized.prismlite.swing.operations;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrismOperationLaunchContextTest {
    @Test
    void materializesSelectedPhysicalRowsAsStableRowSet() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        PrismOperationLaunchContext context = new PrismOperationLaunchContext(session, () -> Set.of(0, 2));

        PrismRowSet rowSet = context.materializeSelectedRows("Selected Structures");

        assertEquals("selection:selected-structures", rowSet.id());
        assertEquals(Set.of("CMPD-001", "CMPD-003"), rowSet.rowIds());
        assertEquals(rowSet, session.rowSet("selection:selected-structures"));
    }

    @Test
    void materializedSelectionIdsAreUnique() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        PrismOperationLaunchContext context = new PrismOperationLaunchContext(session, () -> Set.of(1));

        PrismRowSet first = context.materializeSelectedRows("Selected Structures");
        PrismRowSet second = context.materializeSelectedRows("Selected Structures");

        assertEquals("selection:selected-structures", first.id());
        assertEquals("selection:selected-structures-2", second.id());
    }
}

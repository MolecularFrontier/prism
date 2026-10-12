package tech.molecules.structurized.prismlite.swing.workspace.views;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.ocl.CompoundCardPropertySpec;
import tech.molecules.structurized.prism.engine.ocl.CompoundCardsViewSpec;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompoundCardsPanelTest {
    @Test
    void synchronizesCardSelectionBothWays() throws Exception {
        PrismSession session = exampleSession();
        AtomicReference<CompoundCardsPanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(new CompoundCardsPanel(spec(),
                new PrismLiteWorkspaceModel(session))));
        CompoundCardsPanel panel = panelRef.get();
        assertEquals(List.of("CMPD-002", "CMPD-001", "CMPD-003"), panel.displayedRowIds());

        int externalRow = session.physicalRowForRowId("CMPD-003").orElseThrow();
        JComponent externalCard = panel.cardForPhysicalRow(externalRow);
        Border before = externalCard.getBorder();
        SwingUtilities.invokeAndWait(() -> {
            BitSet selected = new BitSet();
            selected.set(externalRow);
            session.viewState().selectionModel().replace(selected);
        });
        assertNotEquals(before, externalCard.getBorder());

        int clickedRow = session.physicalRowForRowId("CMPD-001").orElseThrow();
        JComponent clickedCard = panel.cardForPhysicalRow(clickedRow);
        SwingUtilities.invokeAndWait(() -> clickedCard.dispatchEvent(new MouseEvent(clickedCard,
                MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 8, 8, 1, false)));
        assertTrue(session.viewState().selectionModel().isSelected(clickedRow));
        assertEquals(1, session.viewState().selectionModel().selectedRows().cardinality());
    }

    private static CompoundCardsViewSpec spec() {
        return new CompoundCardsViewSpec("cards", "Comparison", "all", "smiles", "compound_id",
                "CMPD-002", List.of(
                new CompoundCardPropertySpec("pIC50", "Activity", "0.00", true, null),
                new CompoundCardPropertySpec("clogP", "cLogP", "0.0", true, null)), true, 3);
    }

    private static PrismSession exampleSession() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        LinkedHashSet<String> rowIds = new LinkedHashSet<>();
        for (int row = 0; row < session.totalRowCount(); row++) rowIds.add(session.rowIdForPhysicalRow(row));
        session.addRowSet(new PrismRowSet("all", "All", "", rowIds, Map.of()));
        return session;
    }
}

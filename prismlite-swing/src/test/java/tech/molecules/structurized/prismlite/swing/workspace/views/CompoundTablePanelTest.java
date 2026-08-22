package tech.molecules.structurized.prismlite.swing.workspace.views;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.NumericRangeFilter;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.ocl.CompoundTableColumnSpec;
import tech.molecules.structurized.prism.engine.ocl.CompoundTableViewSpec;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompoundTablePanelTest {
    @Test
    void usesStableRowSetAndSynchronizesSelectionBothWays() throws Exception {
        PrismSession session = exampleSession();
        session.setFilters(List.of(new NumericRangeFilter("pIC50", 7.0, null, false)));
        AtomicReference<CompoundTablePanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(new CompoundTablePanel(spec(), new PrismLiteWorkspaceModel(session))));
        CompoundTablePanel panel = panelRef.get();

        assertEquals(3, panel.physicalRows().size());
        assertEquals(3, panel.table().getRowCount());

        SwingUtilities.invokeAndWait(() -> panel.table().addRowSelectionInterval(1, 1));
        int selectedPhysicalRow = panel.physicalRows().get(1);
        assertTrue(session.viewState().selectionModel().isSelected(selectedPhysicalRow));

        BitSet external = new BitSet();
        external.set(panel.physicalRows().get(2));
        SwingUtilities.invokeAndWait(() -> session.viewState().selectionModel().replace(external));
        assertEquals(1, panel.table().getSelectedRowCount());
        assertEquals(2, panel.table().convertRowIndexToModel(panel.table().getSelectedRow()));
    }

    private static CompoundTableViewSpec spec() {
        return new CompoundTableViewSpec(
                "table", "Table", "all", "smiles",
                List.of(
                        new CompoundTableColumnSpec("compound_id", "Compound", null),
                        new CompoundTableColumnSpec("pIC50", "pIC50", "0.00")
                ),
                true,
                200
        );
    }

    private static PrismSession exampleSession() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        LinkedHashSet<String> rowIds = new LinkedHashSet<>();
        for (int row = 0; row < session.totalRowCount(); row++) rowIds.add(session.rowIdForPhysicalRow(row));
        session.addRowSet(new PrismRowSet("all", "All", "", rowIds, Map.of()));
        return session;
    }
}

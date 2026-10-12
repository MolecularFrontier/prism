package tech.molecules.structurized.prismlite.swing.workspace.views;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.NumericRangeFilter;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.ocl.CompoundTableColumnSpec;
import tech.molecules.structurized.prism.engine.ocl.CompoundTableViewSpec;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;
import tech.molecules.structurized.prismlite.swing.workspace.profile.ScoreDisplayService;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
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

    @Test
    void colorsDisplayedRawValueFromConfiguredScoreColumn() throws Exception {
        PrismSession session = exampleSession();
        AtomicReference<CompoundTablePanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(new CompoundTablePanel(
                new CompoundTableViewSpec(
                        "colored-table", "Colored Table", "all", "smiles",
                        List.of(new CompoundTableColumnSpec("pIC50", "pIC50", "0.00", "pIC50")),
                        true, 200),
                new PrismLiteWorkspaceModel(session))));
        CompoundTablePanel panel = panelRef.get();
        int modelRow = -1;
        for (int row = 0; row < panel.physicalRows().size(); row++) {
            if (!session.table().column("pIC50").isMissing(panel.physicalRows().get(row))) {
                modelRow = row;
                break;
            }
        }
        assertTrue(modelRow >= 0);
        int scoredModelRow = modelRow;
        int viewRow = panel.table().convertRowIndexToView(scoredModelRow);
        AtomicReference<Component> renderedRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> renderedRef.set(panel.table().prepareRenderer(
                panel.table().getCellRenderer(viewRow, 1), viewRow, 1)));

        double score = session.table().column("pIC50")
                .doubleValueAt(panel.physicalRows().get(scoredModelRow));
        Component rendered = renderedRef.get();
        assertEquals(ScoreDisplayService.softScoreColor(score), rendered.getBackground());
        assertTrue(((JComponent) rendered).getToolTipText().startsWith("Desirability score:"));
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

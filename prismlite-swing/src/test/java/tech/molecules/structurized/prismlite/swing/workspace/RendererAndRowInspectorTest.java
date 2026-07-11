package tech.molecules.structurized.prismlite.swing.workspace;

import com.actelion.research.chem.StereoMolecule;
import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeRenderUtil;
import tech.molecules.structurized.prismlite.swing.workspace.inspector.RowInspectorPanel;
import tech.molecules.structurized.prismlite.swing.workspace.table.MoleculeCellRenderer;
import tech.molecules.structurized.prismlite.swing.workspace.table.MoleculeColumnCellRendererProvider;

import javax.swing.SwingUtilities;
import javax.swing.JViewport;
import javax.swing.table.TableCellRenderer;
import java.awt.Dimension;
import java.awt.Point;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererAndRowInspectorTest {
    @Test
    void moleculeRendererProviderSupportsMoleculeColumns() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        PrismLiteWorkspaceModel model = new PrismLiteWorkspaceModel(session);
        PrismColumn structure = session.table().column("smiles");
        MoleculeColumnCellRendererProvider provider = new MoleculeColumnCellRendererProvider();

        assertTrue(provider.supports(structure));
        TableCellRenderer renderer = provider.createRenderer(model, structure);
        assertInstanceOf(MoleculeCellRenderer.class, renderer);
    }

    @Test
    void parsesMoleculeValueForPreview() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        PrismColumn structure = session.table().column("smiles");

        StereoMolecule molecule = MoleculeRenderUtil.parse(structure, 0);

        assertNotNull(molecule);
        assertTrue(molecule.getAllAtoms() > 0);
    }

    @Test
    void tableSelectionAndWidthsSurviveChromeRefresh() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        AtomicInteger selectedRow = new AtomicInteger(-1);
        AtomicInteger widthAfterFocus = new AtomicInteger(-1);

        SwingUtilities.invokeAndWait(() -> {
            PrismLiteWorkspacePanel panel = new PrismLiteWorkspacePanel(session);
            panel.table().getColumnModel().getColumn(0).setPreferredWidth(188);
            panel.table().getColumnModel().getColumn(0).setWidth(188);
            panel.table().setRowSelectionInterval(1, 1);

            panel.model().setFocusedColumn("pIC50");

            selectedRow.set(panel.table().getSelectedRow());
            widthAfterFocus.set(panel.table().getColumnModel().getColumn(0).getWidth());
        });

        assertEquals(1, selectedRow.get());
        assertEquals(188, widthAfterFocus.get());
    }

    @Test
    void tableSelectionDoesNotMoveScrolledViewport() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        AtomicInteger yBefore = new AtomicInteger(-1);
        AtomicInteger yAfter = new AtomicInteger(-1);

        SwingUtilities.invokeAndWait(() -> {
            PrismLiteWorkspacePanel panel = new PrismLiteWorkspacePanel(session);
            panel.setSize(new Dimension(900, 260));
            panel.doLayout();
            panel.table().setRowHeight(88);
            panel.table().doLayout();
            JViewport viewport = (JViewport) panel.table().getParent();
            viewport.setSize(new Dimension(520, 120));
            viewport.setViewPosition(new Point(0, 88));
            yBefore.set(viewport.getViewPosition().y);

            panel.table().setRowSelectionInterval(2, 2);

            yAfter.set(viewport.getViewPosition().y);
        });

        assertEquals(yBefore.get(), yAfter.get());
    }

    @Test
    void rowInspectorBuildsForFocusedRow() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        PrismLiteWorkspaceModel model = new PrismLiteWorkspaceModel(session);
        model.setFocusedVisibleRow(2);

        RowInspectorPanel panel = new RowInspectorPanel(model);

        assertEquals(1, panel.getComponentCount());
    }
}

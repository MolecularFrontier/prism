package tech.molecules.structurized.prismlite.swing.workspace;

import com.actelion.research.chem.StereoMolecule;
import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.CreateScatterPlotViewOperation;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.TextPatternMode;
import tech.molecules.structurized.prism.engine.TextPatternFilter;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.ocl.OclCreateStructureGridViewOperation;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeRenderUtil;
import tech.molecules.structurized.prismlite.swing.workspace.inspector.RowInspectorPanel;
import tech.molecules.structurized.prismlite.swing.workspace.table.MoleculeCellRenderer;
import tech.molecules.structurized.prismlite.swing.workspace.table.MoleculeColumnCellRendererProvider;
import tech.molecules.structurized.prismlite.swing.workspace.views.StructureGridViewRenderer;
import tech.molecules.structurized.prismlite.swing.workspace.views.ScatterPlotViewRenderer;

import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JViewport;
import javax.swing.JScrollPane;
import javax.swing.table.TableCellRenderer;
import java.awt.Dimension;
import java.awt.Container;
import java.awt.Component;
import java.awt.Point;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    void workspaceAddsTabForCreatedStructureGridView() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        session.operationRegistry().register(new OclCreateStructureGridViewOperation());
        AtomicInteger tabCount = new AtomicInteger(-1);
        AtomicReference<String> selectedTitle = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            PrismLiteWorkspacePanel panel = new PrismLiteWorkspacePanel(session);
            assertEquals(1, panel.workspaceTabCount());

            session.runOperation(OclCreateStructureGridViewOperation.ID, Map.of(
                    "viewId", "grid:test",
                    "title", "Test Grid",
                    "structureColumn", "smiles",
                    "endpointColumns", "pIC50",
                    "maxCompounds", "2",
                    "columns", "2"
            ));
            panel.refreshWorkspace();

            tabCount.set(panel.workspaceTabCount());
            selectedTitle.set(panel.selectedWorkspaceTabTitle());
        });

        assertEquals(2, tabCount.get());
        assertEquals("Test Grid", selectedTitle.get());
    }




    @Test
    void structureGridRowSetIntersectsCurrentFilters() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        session.operationRegistry().register(new OclCreateStructureGridViewOperation());
        session.addRowSet(new PrismRowSet("selected", "Selected", "", Set.of("CMPD-001", "CMPD-002", "CMPD-003"), Map.of()));
        session.runOperation(OclCreateStructureGridViewOperation.ID, Map.of(
                "viewId", "grid:filtered",
                "title", "Filtered Grid",
                "rowSetId", "selected",
                "structureColumn", "smiles",
                "endpointColumns", "compound_id",
                "columns", "2"
        ));
        session.addFilter(new TextPatternFilter("compound_id", "CMPD-001", TextPatternMode.SUBSTRING, false, false));

        JComponent component = new StructureGridViewRenderer().createComponent(
                session.view("grid:filtered"),
                new PrismLiteWorkspaceModel(session),
                null,
                () -> { }
        );

        assertEquals(1, structureGridCardCount(component));
    }

    @Test
    void structureGridRowSetShowsEmptyMessageWhenFiltersExcludeAllRows() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        session.operationRegistry().register(new OclCreateStructureGridViewOperation());
        session.addRowSet(new PrismRowSet("selected", "Selected", "", Set.of("CMPD-001", "CMPD-002"), Map.of()));
        session.runOperation(OclCreateStructureGridViewOperation.ID, Map.of(
                "viewId", "grid:empty-filtered",
                "title", "Empty Filtered Grid",
                "rowSetId", "selected",
                "structureColumn", "smiles"
        ));
        session.addFilter(new TextPatternFilter("compound_id", "NO-SUCH-COMPOUND", TextPatternMode.SUBSTRING, false, false));

        JComponent component = new StructureGridViewRenderer().createComponent(
                session.view("grid:empty-filtered"),
                new PrismLiteWorkspaceModel(session),
                null,
                () -> { }
        );

        assertEquals("No structures to display.", first(component, JLabel.class).getText());
    }

    @Test
    void structureGridConfigurationUpdatesStoredView() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        session.operationRegistry().register(new OclCreateStructureGridViewOperation());
        AtomicReference<String> title = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            session.runOperation(OclCreateStructureGridViewOperation.ID, Map.of(
                    "viewId", "grid:config",
                    "title", "Initial Grid",
                    "structureColumn", "smiles",
                    "endpointColumns", "pIC50"
            ));
            PrismLiteWorkspaceModel model = new PrismLiteWorkspaceModel(session);
            JComponent config = new StructureGridViewRenderer().createConfigurationComponent(
                    session.view("grid:config"),
                    model,
                    null,
                    () -> { }
            );

            textField(config, "Initial Grid").setText("Updated Grid");
            button(config, "Apply").doClick();
            title.set(session.view("grid:config").title());
        });

        assertEquals("Updated Grid", title.get());
    }


    @Test
    void scatterPlotRendererCreatesChartComponent() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        session.operationRegistry().register(new CreateScatterPlotViewOperation());
        session.runOperation(CreateScatterPlotViewOperation.ID, Map.of(
                "viewId", "scatter:test",
                "title", "Test Scatter",
                "xColumnId", "pIC50",
                "yColumnId", "HLM_CLint",
                "colorColumnId", "series"
        ));

        JComponent component = new ScatterPlotViewRenderer().createComponent(
                session.view("scatter:test"),
                new PrismLiteWorkspaceModel(session),
                null,
                () -> { }
        );

        assertTrue(component.getClass().getName().contains("XChartPanel"));
    }

    @Test
    void scatterPlotRendererShowsEmptyMessageWhenFiltersExcludeAllRows() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        session.operationRegistry().register(new CreateScatterPlotViewOperation());
        session.addRowSet(new PrismRowSet("selected", "Selected", "", Set.of("CMPD-001", "CMPD-002"), Map.of()));
        session.runOperation(CreateScatterPlotViewOperation.ID, Map.of(
                "viewId", "scatter:empty",
                "title", "Empty Scatter",
                "rowSetId", "selected",
                "xColumnId", "pIC50",
                "yColumnId", "HLM_CLint"
        ));
        session.addFilter(new TextPatternFilter("compound_id", "NO-SUCH-COMPOUND", TextPatternMode.SUBSTRING, false, false));

        JComponent component = new ScatterPlotViewRenderer().createComponent(
                session.view("scatter:empty"),
                new PrismLiteWorkspaceModel(session),
                null,
                () -> { }
        );

        assertEquals("No points to display.", first(component, JLabel.class).getText());
    }

    @Test
    void scatterPlotConfigurationUpdatesStoredView() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        session.operationRegistry().register(new CreateScatterPlotViewOperation());
        AtomicReference<String> title = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            session.runOperation(CreateScatterPlotViewOperation.ID, Map.of(
                    "viewId", "scatter:config",
                    "title", "Initial Scatter",
                    "xColumnId", "pIC50",
                    "yColumnId", "HLM_CLint"
            ));
            JComponent config = new ScatterPlotViewRenderer().createConfigurationComponent(
                    session.view("scatter:config"),
                    new PrismLiteWorkspaceModel(session),
                    null,
                    () -> { }
            );

            textField(config, "Initial Scatter").setText("Updated Scatter");
            button(config, "Apply").doClick();
            title.set(session.view("scatter:config").title());
        });

        assertEquals("Updated Scatter", title.get());
    }

    @Test
    void rowInspectorBuildsForFocusedRow() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        PrismLiteWorkspaceModel model = new PrismLiteWorkspaceModel(session);
        model.setFocusedVisibleRow(2);

        RowInspectorPanel panel = new RowInspectorPanel(model);

        assertEquals(1, panel.getComponentCount());
    }

    private static int structureGridCardCount(JComponent component) {
        JScrollPane scroll = (JScrollPane) component;
        JViewport viewport = scroll.getViewport();
        return ((Container) viewport.getView()).getComponentCount();
    }


    private static JButton button(Component root, String text) {
        if (root instanceof JButton button && text.equals(button.getText())) {
            return button;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                JButton match = button(child, text);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static JTextField textField(Component root, String text) {
        if (root instanceof JTextField field && text.equals(field.getText())) {
            return field;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTextField match = textField(child, text);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static <T extends Component> T first(Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T match = first(child, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

}

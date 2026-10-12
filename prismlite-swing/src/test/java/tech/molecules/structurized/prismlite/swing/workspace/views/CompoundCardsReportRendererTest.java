package tech.molecules.structurized.prismlite.swing.workspace.views;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prism.report.PrismReportParser;
import tech.molecules.structurized.prism.report.PrismReportViewSpec;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CompoundCardsReportRendererTest {
    @Test
    void embedsCardsAndOpensTheSameSpecificationAsAFullView() throws Exception {
        PrismSession session = exampleSession();
        var document = new PrismReportParser().parse(report());
        PrismReportViewSpec specification = new PrismReportViewSpec("report:cards", "Comparison", document);
        PrismViewRecord view = PrismViewRecord.of(specification);
        session.addView(view);
        AtomicReference<JComponent> rendered = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> rendered.set(new PrismReportViewRenderer().createComponent(
                view, new PrismLiteWorkspaceModel(session), null, () -> { })));
        assertNotNull(find(rendered.get(), CompoundCardsPanel.class));
        JButton open = findButton(rendered.get(), "Open as full view");
        assertNotNull(open);
        SwingUtilities.invokeAndWait(open::doClick);

        assertEquals("chemistry.compound-cards", session.views().getLast().type());
    }

    private static <T extends Component> T find(Component component, Class<T> type) {
        if (type.isInstance(component)) return type.cast(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JButton findButton(Component component, String text) {
        if (component instanceof JButton button && text.equals(button.getText())) return button;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JButton found = findButton(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static PrismSession exampleSession() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        LinkedHashSet<String> rowIds = new LinkedHashSet<>();
        for (int row = 0; row < session.totalRowCount(); row++) rowIds.add(session.rowIdForPhysicalRow(row));
        session.addRowSet(new PrismRowSet("all", "All", "", rowIds, Map.of()));
        return session;
    }

    private static String report() {
        return """
                ---
                prismReportVersion: 1
                dataset: current
                title: Comparison
                ---
                ~~~prism
                {
                  "type": "compound-cards",
                  "id": "cards",
                  "rowSet": "all",
                  "structureColumn": "smiles",
                  "titleColumn": "compound_id",
                  "referenceRow": "CMPD-002",
                  "properties": [
                    {"column": "pIC50", "format": "0.00", "showDelta": true}
                  ]
                }
                ~~~
                """;
    }
}

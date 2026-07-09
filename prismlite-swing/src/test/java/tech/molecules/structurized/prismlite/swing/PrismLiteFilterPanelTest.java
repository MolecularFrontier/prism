package tech.molecules.structurized.prismlite.swing;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.TextPatternMode;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrismLiteFilterPanelTest {
    @Test
    void programmaticNumericFilterUpdatesSessionAndTableModel() throws Exception {
        PrismSession session = exampleSession();
        PrismLiteTableModel model = new PrismLiteTableModel(session);
        PrismLiteFilterPanel panel = new PrismLiteFilterPanel(session, model::refresh);

        panel.setNumericRangeFilter("pIC50", 6.5, null, false);

        assertEquals(2, session.visibleRowCount());
        assertEquals(2, model.getRowCount());
        assertEquals("CMPD-001", model.getValueAt(0, 0));
        assertEquals("CMPD-002", model.getValueAt(1, 0));

        panel.clearAllFilters();

        assertEquals(3, session.visibleRowCount());
        assertEquals(3, model.getRowCount());
    }

    @Test
    void programmaticTextAndCategoryFiltersUpdateSession() throws Exception {
        PrismSession session = exampleSession();
        PrismLiteTableModel model = new PrismLiteTableModel(session);
        PrismLiteFilterPanel panel = new PrismLiteFilterPanel(session, model::refresh);

        panel.setTextFilter("compound_id", "CMPD-00[12]", TextPatternMode.REGEX, false, false);

        assertEquals(2, model.getRowCount());

        panel.clearAllFilters();
        panel.setCategoryFilter("series", Set.of("B"), false);

        assertEquals(1, model.getRowCount());
        assertEquals("CMPD-003", model.getValueAt(0, 0));
    }

    private static PrismSession exampleSession() throws Exception {
        return PrismSession.open(Path.of("..", "examples", "example.prismpack"));
    }
}

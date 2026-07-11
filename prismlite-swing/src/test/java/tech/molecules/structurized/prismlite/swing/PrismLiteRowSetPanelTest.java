package tech.molecules.structurized.prismlite.swing;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.NumericRangeFilter;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrismLiteRowSetPanelTest {
    @Test
    void rowSetPanelFiltersSelectedRowSet() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        PrismLiteTableModel model = new PrismLiteTableModel(session);
        session.addRowSet(new PrismRowSet("preferred", "Preferred", "", Set.of("CMPD-001", "CMPD-003"), Map.of()));
        PrismLiteRowSetPanel panel = new PrismLiteRowSetPanel(session, model::refresh);

        panel.selectRowSet("preferred");
        panel.filterSelectedRowSet();

        assertEquals(2, model.getRowCount());
        assertEquals("CMPD-001", model.getValueAt(0, 0));
        assertEquals("CMPD-003", model.getValueAt(1, 0));
    }


    @Test
    void rowSetPanelPreservesColumnFilters() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        PrismLiteTableModel model = new PrismLiteTableModel(session);
        session.addFilter(new NumericRangeFilter("pIC50", 6.5, null, false));
        session.addRowSet(new PrismRowSet("preferred", "Preferred", "", Set.of("CMPD-001", "CMPD-003"), Map.of()));
        PrismLiteRowSetPanel panel = new PrismLiteRowSetPanel(session, model::refresh);

        panel.selectRowSet("preferred");
        panel.filterSelectedRowSet();

        assertEquals(1, model.getRowCount());
        assertEquals("CMPD-001", model.getValueAt(0, 0));
        assertEquals(2, session.viewState().activeFilters().size());
    }
}

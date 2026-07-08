package tech.molecules.structurized.prismlite.swing;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.SortKey;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrismLiteTableModelTest {
    @Test
    void tableModelMapsVisibleRowsThroughEngine() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        session.setSortKeys(List.of(SortKey.asc("pIC50")));
        PrismLiteTableModel model = new PrismLiteTableModel(session);

        assertEquals(3, model.getRowCount());
        assertEquals("Compound ID", model.getColumnName(0));
        assertEquals("CMPD-003", model.getValueAt(0, 0));
        assertEquals(5.9, model.getValueAt(0, 3));
    }
}

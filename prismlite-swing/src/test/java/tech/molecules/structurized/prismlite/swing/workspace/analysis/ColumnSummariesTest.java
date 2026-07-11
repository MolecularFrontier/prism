package tech.molecules.structurized.prismlite.swing.workspace.analysis;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismSession;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ColumnSummariesTest {
    @Test
    void computesNumericSummaryAndHistogram() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));

        ColumnSummary summary = ColumnSummaries.compute(session.table().column("pIC50"));

        NumericColumnSummary numeric = assertInstanceOf(NumericColumnSummary.class, summary);
        assertEquals(3, numeric.validCount());
        assertEquals(0, numeric.missingCount());
        assertEquals(5.9, numeric.minimum());
        assertEquals(7.2, numeric.maximum());
        assertFalse(numeric.histogram().isEmpty());
    }

    @Test
    void computesCategoricalSummary() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));

        ColumnSummary summary = ColumnSummaries.compute(session.table().column("series"));

        CategoricalColumnSummary categorical = assertInstanceOf(CategoricalColumnSummary.class, summary);
        assertEquals(3, categorical.validCount());
        assertEquals(2, categorical.distinctCount());
        assertFalse(categorical.topValues().isEmpty());
    }
}

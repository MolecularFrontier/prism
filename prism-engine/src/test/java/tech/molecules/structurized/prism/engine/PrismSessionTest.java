package tech.molecules.structurized.prism.engine;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrismSessionTest {
    @Test
    void loadsExamplePackWithSchemaAndDefaultView() throws Exception {
        PrismSession session = exampleSession();

        assertEquals(3, session.totalRowCount());
        assertEquals(3, session.visibleRowCount());
        assertEquals("compound_id", session.visibleColumnId(0));
        assertEquals(PrismColumnType.MOLECULE, session.table().column("smiles").type());
        assertEquals(PrismColumnType.NUMERIC, session.table().column("pIC50").type());
    }

    @Test
    void numericRangeFilterComputesVisibleRows() throws Exception {
        PrismSession session = exampleSession();

        session.addFilter(new NumericRangeFilter("pIC50", 6.5, null, false));

        assertEquals(2, session.visibleRowCount());
        assertEquals("CMPD-001", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(0), "compound_id"));
        assertEquals("CMPD-002", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(1), "compound_id"));
    }

    @Test
    void textAndCategoryFiltersAreAndCombined() throws Exception {
        PrismSession session = exampleSession();

        session.setFilters(List.of(
                new CategoryIncludeFilter("series", Set.of("A"), false),
                new TextContainsFilter("comment", "interest", true, false)
        ));

        assertEquals(1, session.visibleRowCount());
        assertEquals("CMPD-001", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(0), "compound_id"));
    }

    @Test
    void textPatternFilterSupportsSubstringAndRegexModes() throws Exception {
        PrismSession session = exampleSession();

        session.addFilter(new TextPatternFilter("compound_id", "cmpd-00[12]", TextPatternMode.REGEX, true, false));

        assertEquals(2, session.visibleRowCount());
        assertEquals("CMPD-001", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(0), "compound_id"));
        assertEquals("CMPD-002", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(1), "compound_id"));

        session.setFilters(List.of(new TextPatternFilter("comment", "interesting", TextPatternMode.SUBSTRING, true, false)));

        assertEquals(1, session.visibleRowCount());
        assertEquals("CMPD-001", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(0), "compound_id"));
    }

    @Test
    void missingFilterFindsEmptyCells() throws Exception {
        PrismSession session = exampleSession();

        session.addFilter(new MissingValueFilter("comment", MissingValueMode.MISSING));

        assertEquals(1, session.visibleRowCount());
        assertEquals("CMPD-002", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(0), "compound_id"));
    }

    @Test
    void sortingUsesEngineVisibleOrderAndKeepsStablePhysicalRows() throws Exception {
        PrismSession session = exampleSession();

        session.setSortKeys(List.of(SortKey.desc("HLM_CLint")));

        assertEquals("CMPD-003", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(0), "compound_id"));
        assertEquals("CMPD-002", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(1), "compound_id"));
        assertEquals("CMPD-001", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(2), "compound_id"));
    }

    @Test
    void selectionAndFlagsReferencePhysicalRows() throws Exception {
        PrismSession session = exampleSession();
        session.viewState().selectionModel().setSelected(2, true);
        session.viewState().flagModel().setFlagged("Interesting", 0, true);

        session.setSortKeys(List.of(SortKey.asc("pIC50")));

        assertTrue(session.viewState().selectionModel().isSelected(2));
        assertTrue(session.viewState().flagModel().isFlagged("Interesting", 0));
        assertFalse(session.viewState().flagModel().isFlagged("Interesting", 1));
    }

    @Test
    void activeRowsReturnsDefensiveCopy() throws Exception {
        PrismSession session = exampleSession();
        BitSet activeRows = session.activeRows();
        activeRows.clear();

        assertEquals(3, session.activeRows().cardinality());
    }

    private static PrismSession exampleSession() throws Exception {
        return PrismSession.open(Path.of("..", "examples", "example.prismpack"));
    }
}

package tech.molecules.structurized.chembl;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChemblStructureExporterTest {
    @Test
    void streamsFiltersDeduplicatesAndWritesRows() throws Exception {
        List<ChemblRecord> records = List.of(
                ChemblRecord.minimal("CHEMBL1", "CCOc1ccc(N)cc1"),
                ChemblRecord.minimal("CHEMBL2", "CCOc1ccc(N)cc1"),
                ChemblRecord.minimal("CHEMBL3", "CCO.CCN"),
                ChemblRecord.minimal("CHEMBL4", "CC[Fe]"));
        ChemblExportOptions options = new ChemblExportOptions(10, 10, ChemblExportOptions.Selection.SEQUENTIAL, 0,
                new ChemblFilterOptions(1, 60, -2, 2, ChemblFilterOptions.defaults().allowedElements()), true);
        StringWriter output = new StringWriter();

        ChemblExportStats stats;
        try (ChemblTsvWriter writer = new ChemblTsvWriter(output)) {
            stats = new ChemblStructureExporter().export(new ListSource(records), options, writer);
        }

        assertEquals(4, stats.scannedCount());
        assertEquals(1, stats.acceptedCount());
        assertEquals(1, stats.rejections().get(ChemblRejection.DUPLICATE));
        assertTrue(output.toString().contains("CHEMBL1"));
        assertEquals(2, output.toString().lines().count());
    }

    private static final class ListSource implements ChemblSource {
        private final List<ChemblRecord> records;
        private int index;

        private ListSource(List<ChemblRecord> records) { this.records = new ArrayList<>(records); }
        @Override public boolean hasNext() { return index < records.size(); }
        @Override public ChemblRecord next() { return records.get(index++); }
    }
}

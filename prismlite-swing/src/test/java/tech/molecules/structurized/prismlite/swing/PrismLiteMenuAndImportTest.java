package tech.molecules.structurized.prismlite.swing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.io.PrismTsvDatasetLoader;
import tech.molecules.structurized.prism.provider.inmemory.InMemoryPrismDataset;

import javax.swing.JMenuBar;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PrismLiteMenuAndImportTest {
    @Test
    void frameInstallsExpectedMenubar() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        PrismLiteFrame frame = new PrismLiteFrame(session, Path.of("..", "examples", "example.prismpack"));
        try {
            JMenuBar menubar = frame.getJMenuBar();

            assertNotNull(menubar);
            assertEquals("File", menubar.getMenu(0).getText());
            assertEquals("Import ChEMBL Publication...", menubar.getMenu(0).getItem(2).getText());
            assertEquals("View", menubar.getMenu(1).getText());
            assertEquals("Data", menubar.getMenu(2).getText());
            assertEquals("Help", menubar.getMenu(3).getText());
        } finally {
            frame.dispose();
        }
    }

    @Test
    void importsSelectedTsvSubjectSetAndEndpoint(@TempDir Path tempDir) throws Exception {
        writeDataset(tempDir);
        InMemoryPrismDataset dataset = PrismTsvDatasetLoader.load(tempDir);

        PrismSession session = PrismLiteDatasetImporter.toSession(
                dataset,
                new PrismLiteDatasetImporter.ImportSelection(List.of("project:Project A"), List.of("ic50")),
                tempDir
        );

        assertEquals(1, session.totalRowCount());
        assertEquals("cmp-1", session.valueAtVisible(0, 0));
        assertEquals(7, session.visibleColumnCount());
        assertEquals("6.5", session.table().formattedValueAt(0, "ic50"));
        assertEquals(1, session.rowSets().size());
        assertEquals("project:Project A", session.rowSets().getFirst().id());
    }

    private static void writeDataset(Path directory) throws Exception {
        Files.writeString(directory.resolve("endpoints.prism.tsv"), """
                endpoint_id	name	path	datatype	endpoint_type	evaluation_mode	unit
                ic50	IC50	Potency/IC50	NUMERIC	MEASURED	IMMEDIATE	uM
                flag	Flag	Flags/Flag	BOOLEAN	MEASURED	IMMEDIATE	
                """);
        Files.writeString(directory.resolve("subjects.prism.tsv"), """
                subject_id	structure_id	batch_id	project	series	smiles
                cmp-1	str-1	batch-1	Project A	Series 1	CCO
                cmp-2	str-2	batch-2	Project B	Series 2	CCC
                """);
        Files.writeString(directory.resolve("values.prism.tsv"), """
                subject_id	endpoint_id	state	mean	value
                cmp-1	ic50	VALUE	6.5	
                cmp-2	ic50	VALUE	4.0	
                cmp-1	flag			true
                """);
        Files.writeString(directory.resolve("subject_sets.prism.tsv"), """
                subject_set_id	name	set_type	subject_set_scope
                project:Project A	Project A	PROJECT	PROJECTS
                """);
        Files.writeString(directory.resolve("subject_set_memberships.prism.tsv"), """
                subject_set_id	subject_id
                project:Project A	cmp-1
                """);
    }
}

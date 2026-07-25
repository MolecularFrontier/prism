package tech.molecules.structurized.prism.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.molecules.structurized.prism.prediction.PredictionCapability;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrismPackReaderTest {
    @Test
    void readsExampleDirectory() throws IOException {
        Path example = Path.of("..", "examples", "example.prismpack");

        PrismPack pack = PrismPackReader.read(example);

        assertEquals("0.1", pack.manifest().prismPackVersion());
        assertEquals("Example SAR Analysis", pack.manifest().title());
        assertEquals(7, pack.dataFrame().headers().size());
        assertEquals(3, pack.dataFrame().rows().size());
        assertEquals("smiles", pack.molecules().primaryStructureColumn());
        assertEquals(2, pack.endpoints().endpoints().size());
        assertEquals(1, pack.visualizations().visualizations().size());
        assertTrue(pack.warnings().isEmpty());
    }

    @Test
    void readsMoonshotMedchemExampleDirectory() throws IOException {
        Path example = Path.of("..", "examples", "moonshot-medchem.prismpack");

        PrismPack pack = PrismPackReader.read(example);

        assertEquals("0.1", pack.manifest().prismPackVersion());
        assertEquals("COVID Moonshot Medchem Full View", pack.manifest().title());
        assertEquals(13, pack.dataFrame().headers().size());
        assertEquals(2062, pack.dataFrame().rows().size());
        assertEquals("smiles", pack.molecules().primaryStructureColumn());
        assertEquals(6, pack.endpoints().endpoints().size());
        assertEquals(3, pack.visualizations().visualizations().size());
        assertEquals("mpro_fluorescence_pIC50", pack.tableView().sort().getFirst().column());
        assertTrue(pack.warnings().isEmpty());
    }

    @Test
    void readsCoaddAntimicrobialExampleDirectory() throws IOException {
        Path example = Path.of("..", "examples", "coadd-antimicrobial.prismpack");

        PrismPack pack = PrismPackReader.read(example);

        assertEquals("0.1", pack.manifest().prismPackVersion());
        assertEquals("CO-ADD Antimicrobial Overview", pack.manifest().title());
        assertEquals(52, pack.dataFrame().headers().size());
        assertEquals(4803, pack.dataFrame().rows().size());
        assertEquals("smiles", pack.molecules().primaryStructureColumn());
        assertEquals(46, pack.endpoints().endpoints().size());
        assertEquals(3, pack.visualizations().visualizations().size());
        assertEquals("coadd_mic_gn_001_ug_ml", pack.tableView().sort().getFirst().column());
        assertTrue(pack.warnings().isEmpty());
    }

    @Test
    void readsSparkAchaogenExampleDirectory() throws IOException {
        Path example = Path.of("..", "examples", "spark-achaogen.prismpack");

        PrismPack pack = PrismPackReader.read(example);

        assertEquals("0.1", pack.manifest().prismPackVersion());
        assertEquals("SPARK Achaogen LpxC Overview", pack.manifest().title());
        assertEquals(65, pack.dataFrame().headers().size());
        assertEquals(1873, pack.dataFrame().rows().size());
        assertEquals("smiles", pack.molecules().primaryStructureColumn());
        assertEquals(59, pack.endpoints().endpoints().size());
        assertEquals(3, pack.visualizations().visualizations().size());
        assertEquals("spark_achaogen_lpxc_pic50", pack.tableView().sort().getFirst().column());
        assertTrue(pack.warnings().isEmpty());
    }

    @Test
    void readsMoleculeAceExampleDirectory() throws IOException {
        Path example = Path.of("..", "examples", "moleculeace-chembl2034-ki.prismpack");

        PrismPack pack = PrismPackReader.read(example);

        assertEquals("0.1", pack.manifest().prismPackVersion());
        assertEquals("MoleculeACE GR Ki", pack.manifest().title());
        assertEquals(11, pack.dataFrame().headers().size());
        assertEquals(750, pack.dataFrame().rows().size());
        assertEquals("smiles", pack.molecules().primaryStructureColumn());
        assertEquals(3, pack.endpoints().endpoints().size());
        assertEquals(3, pack.visualizations().visualizations().size());
        assertEquals("p_activity", pack.tableView().sort().getFirst().column());
        assertTrue(pack.warnings().isEmpty());
    }

    @Test
    void readsChemblPublicationExampleDirectory() throws IOException {
        Path example = Path.of("..", "examples", "chembl-publication-chembl5360622.prismpack");

        PrismPack pack = PrismPackReader.read(example);

        assertEquals("0.1", pack.manifest().prismPackVersion());
        assertEquals("ChEMBL Publication CHEMBL5360622", pack.manifest().title());
        assertEquals(18, pack.dataFrame().headers().size());
        assertEquals(224, pack.dataFrame().rows().size());
        assertEquals("smiles", pack.molecules().primaryStructureColumn());
        assertEquals(12, pack.endpoints().endpoints().size());
        assertEquals(3, pack.visualizations().visualizations().size());
        assertEquals("chembl_chembl5360622_chembl5363957_ic50_chembl4203", pack.tableView().sort().getFirst().column());
        assertTrue(pack.warnings().isEmpty());
    }

    @Test
    void readsPackageWithRootDirectoryFromZip(@TempDir Path tempDir) throws IOException {
        Path source = Path.of("..", "examples", "example.prismpack");
        Path zip = tempDir.resolve("example.prismpack");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            try (var stream = Files.walk(source)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    String relative = source.relativize(file).toString().replace('\\', '/');
                    out.putNextEntry(new ZipEntry("example.prismpack/" + relative));
                    Files.copy(file, out);
                    out.closeEntry();
                }
            }
        }

        PrismPack pack = PrismPackReader.read(zip);

        assertEquals("CMPD-002", pack.dataFrame().valueAt(1, "compound_id"));
        assertEquals("Potency vs cLogP", pack.visualizations().visualizations().get(0).title());
    }

    @Test
    void writesZipThatReaderCanOpen(@TempDir Path tempDir) throws IOException {
        PrismPack source = PrismPackReader.read(Path.of("..", "examples", "example.prismpack"));
        Path zip = tempDir.resolve("written.prismpack");

        PrismPackWriter.writeZip(zip, source);
        PrismPack written = PrismPackReader.read(zip);

        assertEquals(source.manifest().title(), written.manifest().title());
        assertEquals(source.dataFrame().headers(), written.dataFrame().headers());
        assertEquals(source.dataFrame().rows(), written.dataFrame().rows());
        assertEquals(source.molecules().primaryStructureColumn(), written.molecules().primaryStructureColumn());
        assertTrue(written.warnings().isEmpty());
    }


    @Test
    void readsAndWritesInlineCellAttachments(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("data"));
        Files.createDirectories(tempDir.resolve("schema"));
        Files.createDirectories(tempDir.resolve("attachments"));
        Files.writeString(tempDir.resolve("prism-pack.json"), """
                {"prismPackVersion":"0.1","dataframe":{"path":"data/dataframe.tsv","schema":"schema/dataframe.schema.json"},"attachments":"attachments/attachments.json"}
                """);
        Files.writeString(tempDir.resolve("schema/dataframe.schema.json"), """
                {"columns":[{"name":"subject_id","type":"string"},{"name":"pIC50","type":"number"}]}
                """);
        Files.writeString(tempDir.resolve("data/dataframe.tsv"), "subject_id\tpIC50\nACT-1\t7.2\n");
        Files.writeString(tempDir.resolve("attachments/attachments.json"), """
                {"attachments":[{"id":"att-1","target":{"type":"cell","rowKeyColumn":"subject_id","rowKey":"ACT-1","column":"pIC50"},"name":"Raw endpoint values","mimeType":"text/plain","content":{"type":"inline","text":"Raw values: 7.1, 7.2"}}]}
                """);

        PrismPack pack = PrismPackReader.read(tempDir);
        Path zip = tempDir.resolve("with-attachments.prismpack");
        PrismPackWriter.writeZip(zip, pack);
        PrismPack written = PrismPackReader.read(zip);

        assertEquals(1, written.attachments().attachments().size());
        PrismPack.Attachment attachment = written.attachments().attachments().get(0);
        assertEquals("cell", attachment.target().type());
        assertEquals("ACT-1", attachment.target().rowKey());
        assertEquals("pIC50", attachment.target().column());
        assertEquals("Raw values: 7.1, 7.2", attachment.content().text());
        assertTrue(written.warnings().isEmpty());
    }

    @Test
    void readsWritesAndIndexesPredictionCapabilities(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("data"));
        Files.createDirectories(tempDir.resolve("schema"));
        Files.createDirectories(tempDir.resolve("semantics"));
        Files.writeString(tempDir.resolve("prism-pack.json"), """
                {"prismPackVersion":"0.2","dataframe":{"path":"data/dataframe.tsv","schema":"schema/dataframe.schema.json"},"molecules":"semantics/molecules.json","endpoints":"semantics/endpoints.json","predictions":"semantics/predictions.json"}
                """);
        Files.writeString(tempDir.resolve("schema/dataframe.schema.json"), """
                {"columns":[{"name":"compound_id","type":"string"},{"name":"smiles","type":"string","semanticType":"chemical_structure"},{"name":"hlm_clint","type":"number","endpointId":"hlm_clint"}]}
                """);
        Files.writeString(tempDir.resolve("data/dataframe.tsv"), """
                compound_id	smiles	hlm_clint
                CMP-1	CCN	12.0
                """);
        Files.writeString(tempDir.resolve("semantics/molecules.json"), """
                {"primaryStructureColumn":"smiles","structureFormat":"smiles","compoundIdColumn":"compound_id"}
                """);
        Files.writeString(tempDir.resolve("semantics/endpoints.json"), """
                {"endpoints":[{"id":"hlm_clint","column":"hlm_clint","displayName":"HLM CLint","unit":"uL/min/mg","direction":"lower_is_better"}]}
                """);
        Files.writeString(tempDir.resolve("semantics/predictions.json"), """
                {"capabilities":[{"capabilityId":"apy.hlm.production","endpointId":"hlm_clint","predictedEndpointId":"hlm_clint.predicted","displayName":"APY HLM production","providerId":"apy","workflowId":"apy://hlm-production","workflowVersion":"production","status":"available","priority":100,"structureColumn":"smiles","structureFormat":"smiles","metadata":{"assay":"HLM"}}]}
                """);

        PrismPack pack = PrismPackReader.read(tempDir);
        PredictionCapability capability = pack.predictions().capabilities().getFirst();
        Path zip = tempDir.resolve("with-predictions.prismpack");
        PrismPackWriter.writeZip(zip, pack);
        PrismPack written = PrismPackReader.read(zip);

        assertEquals("apy.hlm.production", capability.capabilityId());
        assertEquals("hlm_clint", capability.endpointId());
        assertEquals("apy://hlm-production", capability.workflowId());
        assertEquals("HLM", capability.metadata().get("assay"));
        assertEquals("semantics/predictions.json", written.manifest().predictionsPath());
        assertEquals("apy.hlm.production", written.predictions().capabilities().getFirst().capabilityId());
        assertTrue(written.warnings().isEmpty());
    }

    @Test
    void rejectsRowsWithTooManyCells(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("data"));
        Files.createDirectories(tempDir.resolve("schema"));
        Files.writeString(tempDir.resolve("prism-pack.json"), """
                {"prismPackVersion":"0.1","dataframe":{"path":"data/dataframe.tsv","schema":"schema/dataframe.schema.json"}}
                """);
        Files.writeString(tempDir.resolve("schema/dataframe.schema.json"), """
                {"columns":[{"name":"a","type":"string"}]}
                """);
        Files.writeString(tempDir.resolve("data/dataframe.tsv"), "a\n1\t2\n");

        try {
            PrismPackReader.read(tempDir);
        }
        catch (PrismPackException e) {
            assertTrue(e.getMessage().contains("more cells than the header"));
            return;
        }
        throw new AssertionError("Expected PrismPackException");
    }
}

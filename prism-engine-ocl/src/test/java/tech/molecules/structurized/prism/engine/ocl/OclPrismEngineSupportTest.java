package tech.molecules.structurized.prism.engine.ocl;

import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.CachePolicy;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.pack.PrismPack;
import tech.molecules.structurized.prism.pack.PrismPackWriter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclPrismEngineSupportTest {
    @Test
    void registersStructureColumnAndParsesSmilesLazily() {
        PrismSession session = PrismSession.from(smilesPack());

        OclPrismEngineSupport.registerStructureColumn(session, "smiles");

        StereoMolecule molecule = session.computedValues()
                .value(OclComputedValueIds.molecule("smiles"), 0, StereoMolecule.class);
        assertNotNull(molecule);
        assertEquals(9, molecule.getAllAtoms());
        assertEquals(1, session.computedValues().cache().size());
    }

    @Test
    void computesAndCachesFfp512() {
        PrismSession session = PrismSession.from(smilesPack());
        OclPrismEngineSupport.registerStructureColumn(session, "smiles");

        long[] first = session.computedValues().value(OclComputedValueIds.ffp512("smiles"), 0, long[].class);
        long[] second = session.computedValues().value(OclComputedValueIds.ffp512("smiles"), 0, long[].class);

        assertTrue(first.length > 0);
        assertTrue(first == second);
        assertEquals(2, session.computedValues().cache().size());
    }

    @Test
    void precomputeCachesMoleculeAndFfpForEveryRow() {
        PrismSession session = PrismSession.from(smilesPack());

        OclPrismEngineSupport.registerStructureColumn(session, "smiles", CachePolicy.PRECOMPUTE);

        assertEquals(8, session.computedValues().cache().size());
    }

    @Test
    void substructureFilterMatchesAromaticRowsAndReusesCache() throws Exception {
        PrismSession session = PrismSession.from(smilesPack());
        OclPrismEngineSupport.registerStructureColumn(session, "smiles");

        session.setFilters(List.of(new OclSubstructureFilter("smiles", query("c1ccccc1"))));

        assertEquals(2, session.visibleRowCount());
        assertEquals("CMPD-001", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(0), "compound_id"));
        assertEquals("CMPD-002", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(1), "compound_id"));
        int cacheSize = session.computedValues().cache().size();

        session.recompute();

        assertEquals(cacheSize, session.computedValues().cache().size());
    }

    @Test
    void idcodeStructureColumnParsesWhenFormatIsConfiguredExplicitly() throws Exception {
        StereoMolecule source = query("CCO");
        PrismSession session = PrismSession.from(idcodePack(source.getIDCode()));

        OclPrismEngineSupport.registerStructureColumn(session, "idcode", OclStructureFormat.IDCODE, null, CachePolicy.LAZY);

        StereoMolecule parsed = session.computedValues()
                .value(OclComputedValueIds.molecule("idcode"), 0, StereoMolecule.class);
        assertNotNull(parsed);
        assertEquals(source.getAllAtoms(), parsed.getAllAtoms());
    }

    private static StereoMolecule query(String smiles) throws Exception {
        StereoMolecule molecule = new StereoMolecule();
        new SmilesParser().parse(molecule, smiles);
        molecule.setFragment(true);
        molecule.ensureHelperArrays(StereoMolecule.cHelperCIP);
        return molecule;
    }

    private static PrismPack smilesPack() {
        PrismPack.DataframeRef dataframeRef = new PrismPack.DataframeRef(
                "main",
                PrismPackWriter.DATAFRAME_PATH,
                PrismPackWriter.SCHEMA_PATH,
                "compound",
                Map.of()
        );
        PrismPack.Manifest manifest = new PrismPack.Manifest(
                "0.1",
                "ocl-test",
                "OCL Test",
                null,
                null,
                "test",
                dataframeRef,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of()
        );
        PrismPack.DataFrame dataFrame = new PrismPack.DataFrame(
                List.of("compound_id", "smiles"),
                List.of(
                        List.of("CMPD-001", "CCOc1ccccc1"),
                        List.of("CMPD-002", "c1ccccc1N"),
                        List.of("CMPD-003", "CCCl"),
                        List.of("CMPD-004", "")
                )
        );
        PrismPack.DataFrameSchema schema = new PrismPack.DataFrameSchema(List.of(
                new PrismPack.Column("compound_id", "string", "compound_id", "Compound ID", "identifier", null, null, null, null, Map.of()),
                new PrismPack.Column("smiles", "string", "chemical_structure", "Structure", "primary_structure", null, null, null, "smiles", Map.of())
        ), Map.of());
        return new PrismPack(manifest, dataFrame, schema, null, null, null, null, null, Map.of(), List.of());
    }

    private static PrismPack idcodePack(String idcode) {
        PrismPack.DataframeRef dataframeRef = new PrismPack.DataframeRef(
                "main",
                PrismPackWriter.DATAFRAME_PATH,
                PrismPackWriter.SCHEMA_PATH,
                "compound",
                Map.of()
        );
        PrismPack.Manifest manifest = new PrismPack.Manifest(
                "0.1",
                "idcode-test",
                "IDCode Test",
                null,
                null,
                "test",
                dataframeRef,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of()
        );
        PrismPack.DataFrame dataFrame = new PrismPack.DataFrame(
                List.of("compound_id", "idcode"),
                List.of(List.of("CMPD-001", idcode))
        );
        PrismPack.DataFrameSchema schema = new PrismPack.DataFrameSchema(List.of(
                new PrismPack.Column("compound_id", "string", "compound_id", "Compound ID", "identifier", null, null, null, null, Map.of()),
                new PrismPack.Column("idcode", "string", "chemical_structure", "Structure", "primary_structure", null, null, null, "idcode", Map.of())
        ), Map.of());
        return new PrismPack(manifest, dataFrame, schema, null, null, null, null, null, Map.of(), List.of());
    }
}

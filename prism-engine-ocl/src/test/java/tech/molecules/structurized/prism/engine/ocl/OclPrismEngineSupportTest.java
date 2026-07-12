package tech.molecules.structurized.prism.engine.ocl;

import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.CachePolicy;
import tech.molecules.structurized.prism.engine.PrismOperationDescriptor;
import tech.molecules.structurized.prism.engine.PrismOperationEffect;
import tech.molecules.structurized.prism.engine.PrismOperationException;
import tech.molecules.structurized.prism.engine.PrismOperationResult;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.pack.PrismPack;
import tech.molecules.structurized.prism.pack.PrismPackWriter;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void substructureOperationCreatesNamedRowSet() {
        PrismSession session = PrismSession.from(smilesPack());
        OclPrismEngineSupport.registerCapabilities(session);

        PrismOperationDescriptor descriptor = session.operationRegistry().operation(OclCreateSubstructureRowSetOperation.ID).descriptor();
        assertEquals("Create substructure row set", descriptor.name());

        session.runOperation(OclCreateSubstructureRowSetOperation.ID, Map.of(
                "structureColumn", "smiles",
                "query", "c1ccccc1",
                "queryFormat", "SMILES",
                "stereoMode", "IGNORE_STEREO",
                "rowSetName", "Phenyl Matches"
        ));

        assertEquals(1, session.rowSets().size());
        assertEquals("Phenyl Matches", session.rowSet("ocl:substructure:phenyl-matches").name());
        assertEquals(Set.of("CMPD-001", "CMPD-002"), session.rowSet("ocl:substructure:phenyl-matches").rowIds());
    }


    @Test
    void substructureOperationCanBeScopedToSourceRowSet() {
        PrismSession session = PrismSession.from(smilesPack());
        OclPrismEngineSupport.registerCapabilities(session);
        session.addRowSet(new PrismRowSet("selected", "Selected", "", Set.of("CMPD-002", "CMPD-003"), Map.of()));

        session.runOperation(OclCreateSubstructureRowSetOperation.ID, Map.of(
                "structureColumn", "smiles",
                "sourceRowSetId", "selected",
                "query", "c1ccccc1",
                "queryFormat", "SMILES",
                "stereoMode", "IGNORE_STEREO",
                "rowSetName", "Scoped Phenyl"
        ));

        assertEquals(Set.of("CMPD-002"), session.rowSet("ocl:substructure:scoped-phenyl").rowIds());
    }

    @Test
    void structureGridOperationCreatesViewRecord() {
        PrismSession session = PrismSession.from(smilesPack());
        OclPrismEngineSupport.registerCapabilities(session);
        session.addRowSet(new PrismRowSet("preferred", "Preferred", "", Set.of("CMPD-001", "CMPD-003"), Map.of()));

        PrismOperationDescriptor descriptor = session.operationRegistry().operation(OclCreateStructureGridViewOperation.ID).descriptor();
        assertEquals("Create structure grid view", descriptor.name());
        assertTrue(descriptor.effects().contains(PrismOperationEffect.ADD_VIEWS));

        PrismOperationResult result = session.runOperation(OclCreateStructureGridViewOperation.ID, Map.of(
                "viewId", "grid:preferred",
                "title", "Preferred Structures",
                "rowSetId", "preferred",
                "structureColumn", "smiles",
                "endpointColumns", "compound_id",
                "sortColumn", "compound_id",
                "sortDirection", "DESCENDING",
                "maxCompounds", "12",
                "columns", "3"
        ));

        assertEquals(1, result.addedViews().size());
        assertEquals(1, session.views().size());
        StructureGridViewSpec spec = (StructureGridViewSpec) session.view("grid:preferred").specification();
        assertEquals("Preferred Structures", spec.title());
        assertEquals("preferred", spec.rowSetId());
        assertEquals("smiles", spec.structureColumnId());
        assertEquals(List.of("compound_id"), spec.endpointColumnIds());
        assertEquals(3, spec.columns());
    }


    @Test
    void substructureOperationReportsInvalidQueryAsParameterFailure() {
        PrismSession session = PrismSession.from(smilesPack());
        OclPrismEngineSupport.registerCapabilities(session);

        PrismOperationException exception = assertThrows(PrismOperationException.class, () ->
                session.runOperation(OclCreateSubstructureRowSetOperation.ID, Map.of(
                        "structureColumn", "smiles",
                        "query", "not a smiles",
                        "queryFormat", "SMILES",
                        "stereoMode", "IGNORE_STEREO",
                        "rowSetName", "Bad Query"
                )));

        assertEquals("INVALID_PARAMETER", exception.errorCode());
        assertEquals("query", exception.parameterName());
        assertEquals(0, session.rowSets().size());
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

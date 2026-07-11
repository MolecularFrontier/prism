package tech.molecules.structurized.prismlite.swing.chembl;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.provider.inmemory.InMemoryPrismDataset;
import tech.molecules.structurized.prism.result.NumericResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChemblPublicationImporterTest {
    @Test
    void importsPublicationAsPrismDataset() {
        InMemoryPrismDataset dataset = new ChemblPublicationImporter().importPublication(sourceData(), new ChemblPublicationImportOptions("chembl123", 2, 1));

        assertEquals(2, dataset.getSubjectRecords().size());
        assertEquals(1, dataset.getEndpointDefinitions().size());
        assertEquals(2, dataset.getEndpointValues().size());
        assertEquals(2, dataset.getSubjectSets().size());
        assertTrue(dataset.findSubjectRecord("CHEMBL_PARENT_1").orElseThrow().getMetadata().get("source_molecule_chembl_ids").contains("CHEMBL_CHILD_1"));

        String endpointId = dataset.getEndpointDefinitions().getFirst().getId();
        NumericResult value = (NumericResult) dataset.findEndpointValue("CHEMBL_PARENT_1", endpointId).orElseThrow().getResult();
        assertEquals(7.15, value.getMean(), 1.0e-9);
        assertEquals(2, value.getN());
        assertEquals(List.of(7.1, 7.2), value.getRawValues());
        assertEquals("CHEMBL_A1", value.getDetails().get("assay_chembl_id"));
    }

    @Test
    void rejectsWhenNoEndpointPassesCoverageFilter() {
        try {
            new ChemblPublicationImporter().importPublication(sourceData(), new ChemblPublicationImportOptions("CHEMBL123", 3, 1));
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("No endpoint groups"));
            return;
        }
        throw new AssertionError("Expected import to fail");
    }

    private static ChemblPublicationSourceData sourceData() {
        ChemblPublicationSourceData.DocumentInfo document = new ChemblPublicationSourceData.DocumentInfo(
                "CHEMBL123",
                "Synthetic publication",
                "J Med Chem",
                "2026",
                "10.0000/example",
                "123"
        );
        List<ChemblPublicationSourceData.ActivityInfo> activities = List.of(
                activity("1", "CHEMBL_CHILD_1", "CHEMBL_PARENT_1", "CHEMBL_A1", "CHEMBL_T1", 7.1, 80.0),
                activity("2", "CHEMBL_CHILD_1", "CHEMBL_PARENT_1", "CHEMBL_A1", "CHEMBL_T1", 7.2, 70.0),
                activity("3", "CHEMBL_MOL_2", null, "CHEMBL_A1", "CHEMBL_T1", 6.5, 300.0),
                activity("4", "CHEMBL_BAD", null, "CHEMBL_A1", "CHEMBL_T1", 6.0, 1000.0, ">")
        );
        Map<String, ChemblPublicationSourceData.AssayInfo> assays = new LinkedHashMap<>();
        assays.put("CHEMBL_A1", new ChemblPublicationSourceData.AssayInfo(
                "CHEMBL_A1",
                "Example biochemical inhibition assay",
                "B",
                9,
                "CHEMBL_T1",
                "Example kinase",
                "Homo sapiens"
        ));
        Map<String, ChemblPublicationSourceData.MoleculeInfo> molecules = new LinkedHashMap<>();
        molecules.put("CHEMBL_PARENT_1", new ChemblPublicationSourceData.MoleculeInfo("CHEMBL_PARENT_1", "Parent 1", "CCO"));
        molecules.put("CHEMBL_CHILD_1", new ChemblPublicationSourceData.MoleculeInfo("CHEMBL_CHILD_1", "Child 1", "CCO"));
        molecules.put("CHEMBL_MOL_2", new ChemblPublicationSourceData.MoleculeInfo("CHEMBL_MOL_2", "Mol 2", "CCC"));
        molecules.put("CHEMBL_BAD", new ChemblPublicationSourceData.MoleculeInfo("CHEMBL_BAD", "Bad", "CCN"));
        return new ChemblPublicationSourceData(document, activities, assays, molecules);
    }

    private static ChemblPublicationSourceData.ActivityInfo activity(
            String activityId,
            String moleculeId,
            String parentId,
            String assayId,
            String targetId,
            double pchembl,
            double standardValue
    ) {
        return activity(activityId, moleculeId, parentId, assayId, targetId, pchembl, standardValue, "=");
    }

    private static ChemblPublicationSourceData.ActivityInfo activity(
            String activityId,
            String moleculeId,
            String parentId,
            String assayId,
            String targetId,
            double pchembl,
            double standardValue,
            String relation
    ) {
        return new ChemblPublicationSourceData.ActivityInfo(
                activityId,
                moleculeId,
                parentId,
                assayId,
                targetId,
                "Example kinase",
                "IC50",
                relation,
                standardValue,
                "nM",
                pchembl,
                null,
                "0",
                null,
                null
        );
    }
}

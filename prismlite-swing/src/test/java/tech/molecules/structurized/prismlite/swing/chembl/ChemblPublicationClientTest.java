package tech.molecules.structurized.prismlite.swing.chembl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChemblPublicationClientTest {
    @Test
    void mapsPaginatedChemblApiResponses() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ChemblPublicationClient client = new ChemblPublicationClient(
                URI.create("https://example.test/chembl/api/data/"),
                mapper,
                (uri, objectMapper) -> objectMapper.readTree(responseFor(uri))
        );

        ChemblPublicationSourceData source = client.fetchPublication("chembl123");

        assertEquals("CHEMBL123", source.document().documentChemblId());
        assertEquals(2, source.activities().size());
        assertEquals("CHEMBL_A1", source.activities().getFirst().assayChemblId());
        assertEquals("CHEMBL_PARENT_1", source.activities().getFirst().parentMoleculeChemblId());
        assertEquals("Example kinase", source.assaysById().get("CHEMBL_A1").targetName());
        assertEquals("CCO", source.moleculesById().get("CHEMBL_PARENT_1").canonicalSmiles());
    }

    private static String responseFor(URI uri) {
        String text = uri.toString();
        if (text.contains("document.json")) {
            assertTrue(text.contains("document_chembl_id=CHEMBL123"));
            return """
                    {
                      "documents": [{"document_chembl_id":"CHEMBL123","title":"Example","journal":"J Med Chem","year":2026,"doi":"10/example","pubmed_id":"1"}],
                      "page_meta": {"next": null}
                    }
                    """;
        }
        if (text.contains("activity.json") && !text.contains("offset=1")) {
            return """
                    {
                      "activities": [{
                        "activity_id": 1,
                        "molecule_chembl_id": "CHEMBL_CHILD_1",
                        "parent_molecule_chembl_id": "CHEMBL_PARENT_1",
                        "assay_chembl_id": "CHEMBL_A1",
                        "target_chembl_id": "CHEMBL_T1",
                        "target_pref_name": "Example kinase",
                        "standard_type": "IC50",
                        "standard_relation": "=",
                        "standard_value": "100.0",
                        "standard_units": "nM",
                        "pchembl_value": "7.0",
                        "potential_duplicate": 0
                      }],
                      "page_meta": {"next": "/chembl/api/data/activity.json?offset=1"}
                    }
                    """;
        }
        if (text.contains("activity.json")) {
            return """
                    {
                      "activities": [{
                        "activity_id": 2,
                        "molecule_chembl_id": "CHEMBL_MOL_2",
                        "assay_chembl_id": "CHEMBL_A1",
                        "target_chembl_id": "CHEMBL_T1",
                        "target_pref_name": "Example kinase",
                        "standard_type": "IC50",
                        "standard_relation": "=",
                        "standard_value": "200.0",
                        "standard_units": "nM",
                        "pchembl_value": "6.7",
                        "potential_duplicate": 0
                      }],
                      "page_meta": {"next": null}
                    }
                    """;
        }
        if (text.contains("assay.json")) {
            return """
                    {
                      "assays": [{"assay_chembl_id":"CHEMBL_A1","description":"Assay text","assay_type":"B","confidence_score":9,"target_chembl_id":"CHEMBL_T1","target_pref_name":"Example kinase","target_organism":"Homo sapiens"}],
                      "page_meta": {"next": null}
                    }
                    """;
        }
        if (text.contains("molecule.json")) {
            return """
                    {
                      "molecules": [
                        {"molecule_chembl_id":"CHEMBL_PARENT_1","pref_name":"Parent","molecule_structures":{"canonical_smiles":"CCO"}},
                        {"molecule_chembl_id":"CHEMBL_CHILD_1","pref_name":"Child","molecule_structures":{"canonical_smiles":"CCO"}},
                        {"molecule_chembl_id":"CHEMBL_MOL_2","pref_name":"Mol 2","molecule_structures":{"canonical_smiles":"CCC"}}
                      ],
                      "page_meta": {"next": null}
                    }
                    """;
        }
        throw new AssertionError("Unexpected URI: " + uri);
    }
}

package tech.molecules.structurized.prism.report;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.ocl.StructureSketchViewSpec;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureSketchReportBlockTest {
    @Test
    void parsesInlineStructuresAndValidatesWithoutDatasetReferences() throws Exception {
        PrismReportDocument report = new PrismReportParser().parse(report("""
                      {"smiles": "c1ccccc1", "label": "benzene", "caption": "reference"},
                      "CC(=O)Oc1ccccc1C(=O)O"
                """));

        assertEquals(1, report.blocks().stream()
                .filter(block -> block instanceof EmbeddedPrismViewReportBlock)
                .count());
        EmbeddedPrismViewReportBlock block = (EmbeddedPrismViewReportBlock) report.blocks().stream()
                .filter(item -> item instanceof EmbeddedPrismViewReportBlock)
                .findFirst()
                .orElseThrow();
        StructureSketchViewSpec spec = (StructureSketchViewSpec) block.specification();

        assertEquals(StructureSketchViewSpec.VIEW_TYPE, spec.viewType());
        assertEquals(2, spec.structures().size());
        assertEquals("benzene", spec.structures().getFirst().label());
        assertEquals("reference", spec.structures().getFirst().caption());
        // a bare SMILES string is accepted as shorthand
        assertEquals("CC(=O)Oc1ccccc1C(=O)O", spec.structures().get(1).smiles());
        assertEquals(2, spec.gridColumns());

        // the block references no row set and no column, so it validates against any session
        assertTrue(spec.referencedRowSetIds().isEmpty());
        assertTrue(spec.referencedColumnIds().isEmpty());
        assertTrue(new PrismReportValidator()
                .validate(report, PrismReportParserValidatorTest.exampleSession())
                .isEmpty());
    }

    @Test
    void reportsUnparseableSmilesWithItsIndex() throws Exception {
        PrismReportDocument report = new PrismReportParser().parse(report("""
                      "c1ccccc1",
                      "this-is-not-smiles"
                """));

        List<PrismReportDiagnostic> diagnostics = new PrismReportValidator()
                .validate(report, PrismReportParserValidatorTest.exampleSession());

        assertEquals(1, diagnostics.size());
        PrismReportDiagnostic diagnostic = diagnostics.getFirst();
        assertEquals(PrismReportSeverity.ERROR, diagnostic.severity());
        assertEquals("INVALID_SMILES", diagnostic.code());
        assertTrue(diagnostic.message().contains("structures[1]"),
                "diagnostic should name the offending index: " + diagnostic.message());
    }

    private static String report(String structures) {
        return """
                ---
                prismReportVersion: 1
                dataset: current
                title: Sketch report
                ---

                # Sketch report

                ```prism
                {
                  "type": "structure-sketch",
                  "id": "sketches",
                  "title": "Proposed analogues",
                  "gridColumns": 2,
                  "structures": [
                %s
                  ]
                }
                ```
                """.formatted(structures);
    }
}

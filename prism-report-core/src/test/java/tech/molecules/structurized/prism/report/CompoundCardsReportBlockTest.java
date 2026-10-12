package tech.molecules.structurized.prism.report;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.ocl.CompoundCardsViewSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompoundCardsReportBlockTest {
    @Test
    void parsesAndValidatesComparisonCards() throws Exception {
        PrismReportDocument report = new PrismReportParser().parse(report("compound_id", "CMPD-002", "pIC50"));

        assertFalse(report.hasErrors());
        PrismViewReportBlock block = assertInstanceOf(PrismViewReportBlock.class, report.blocks().getFirst());
        CompoundCardsViewSpec specification = assertInstanceOf(CompoundCardsViewSpec.class, block.specification());
        assertEquals("CMPD-002", specification.referenceRowId());
        assertEquals("pIC50", specification.properties().getFirst().columnId());
        assertTrue(specification.properties().getFirst().showDelta());
        assertEquals("pIC50", specification.properties().getFirst().colorColumnId());
        assertTrue(new PrismReportValidator().validate(report,
                PrismReportParserValidatorTest.exampleSession()).stream()
                .noneMatch(item -> item.severity() == PrismReportSeverity.ERROR));
    }

    @Test
    void rejectsInvalidComparisonSemantics() throws Exception {
        PrismReportDocument report = new PrismReportParser().parse(report("smiles", "missing-row", "comment"));
        var diagnostics = new PrismReportValidator().validate(report,
                PrismReportParserValidatorTest.exampleSession());

        assertTrue(diagnostics.stream().anyMatch(item -> item.code().equals("INVALID_TITLE_COLUMN")));
        assertTrue(diagnostics.stream().anyMatch(item -> item.code().equals("DELTA_ON_NON_NUMERIC_COLUMN")));
        assertTrue(diagnostics.stream().anyMatch(item -> item.code().equals("INVALID_COLOR_COLUMN")));
        assertTrue(diagnostics.stream().anyMatch(item -> item.code().equals("UNKNOWN_REFERENCE_ROW")));
    }

    private static String report(String titleColumn, String referenceRow, String propertyColumn) {
        return """
                ---
                prismReportVersion: 1
                dataset: current
                title: Lead comparison
                ---
                ~~~prism
                {
                  "type": "compound-cards",
                  "id": "lead-comparison",
                  "rowSet": "all",
                  "structureColumn": "smiles",
                  "titleColumn": "%s",
                  "referenceRow": "%s",
                  "properties": [
                    {
                      "column": "%s",
                      "label": "Activity",
                      "format": "0.00",
                      "showDelta": true,
                      "colorColumn": "%s"
                    }
                  ],
                  "linkSelection": true,
                  "maxCards": 6
                }
                ~~~
                """.formatted(titleColumn, referenceRow, propertyColumn, propertyColumn);
    }
}

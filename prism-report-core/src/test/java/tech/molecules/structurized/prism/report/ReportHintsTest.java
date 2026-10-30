package tech.molecules.structurized.prism.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportHintsTest {

    // ---------- E1: distinguish a typo from a missing analysis ----------

    @Test
    void suggestsTheNearestNameForAPlainTypo() {
        String message = PrismReportHints.unknown("column", "pIC5O",
                java.util.List.of("pIC50", "mw", "logp"));

        assertTrue(message.contains("Did you mean: pIC50?"), message);
    }

    @Test
    void doesNotSuggestAnUnrelatedNearestMatch() {
        // "zzzzzzzz" is nearest to something, but suggesting it would be noise
        String message = PrismReportHints.unknown("column", "zzzzzzzz",
                java.util.List.of("pIC50", "mw"));

        assertFalse(message.contains("Did you mean"), message);
    }

    @Test
    void namesTheMissingAnalysisWhenNothingExistsUnderTheNamespace() {
        // the real failure: report authored against a session where the SAR analysis was run,
        // opened against one where it was not
        String message = PrismReportHints.unknown("column", "sar.macro.R_cap",
                java.util.List.of("pIC50", "mw", "logp"));

        assertTrue(message.contains("No columns exist under 'sar.macro'"), message);
        assertTrue(message.contains("materialize_prism_scaffold_analysis"), message);
        assertTrue(message.contains("output_prefix 'sar.macro'"), message);
    }

    @Test
    void doesNotBlameTheAnalysisWhenSiblingColumnsExist() {
        // sar.macro.* is present, so this really is a typo, not a missing analysis
        String message = PrismReportHints.unknown("column", "sar.macro.R_captain",
                java.util.List.of("sar.macro.R_cap", "sar.macro.R_pym"));

        assertFalse(message.contains("has probably not been run"), message);
        assertTrue(message.contains("Did you mean: sar.macro.R_cap?"), message);
    }

    // ---------- E2: actionable direction advice ----------

    @Test
    void advisesMinForEndpointsWhereLowerIsBetter() {
        assertTrue(PrismReportHints.directionAdvice("mc2r.ic50").contains("'min'"),
                PrismReportHints.directionAdvice("mc2r.ic50"));
        assertTrue(PrismReportHints.directionAdvice("hlm.clint").contains("'min'"),
                PrismReportHints.directionAdvice("hlm.clint"));
    }

    @Test
    void advisesMaxForEndpointsWhereHigherIsBetter() {
        assertTrue(PrismReportHints.directionAdvice("score__potency").contains("'max'"),
                PrismReportHints.directionAdvice("score__potency"));
        assertTrue(PrismReportHints.directionAdvice("solubility_fassif").contains("'max'"),
                PrismReportHints.directionAdvice("solubility_fassif"));
    }

    @Test
    void fallsBackToStatingBothWhenDirectionIsUnguessable() {
        String advice = PrismReportHints.directionAdvice("assay_value_7");

        assertTrue(advice.contains("'min' or 'max'"), advice);
    }
}

package tech.molecules.structurized.chembl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChemblNormalizerTest {
    private final ChemblNormalizer normalizer = new ChemblNormalizer();
    private final ChemblFilterOptions options = new ChemblFilterOptions(1, 60, -2, 2, ChemblFilterOptions.defaults().allowedElements());

    @Test
    void keepsLargestOrganicComponentAndReportsOriginalFragments() {
        NormalizedMolecule normalized = normalizer.normalize("CCOc1ccc(N)cc1.Cl", options);
        assertEquals(2, normalized.originalFragmentCount());
        assertEquals(10, normalized.molecule().getAtoms());
    }

    @Test
    void rejectsEquallyLargeOrganicComponents() {
        ChemblNormalizationException exception = assertThrows(ChemblNormalizationException.class,
                () -> normalizer.normalize("CCO.CCN", options));
        assertEquals(ChemblRejection.MULTIPLE_ORGANIC_COMPONENTS, exception.reason());
    }

    @Test
    void rejectsUnsupportedElements() {
        ChemblNormalizationException exception = assertThrows(ChemblNormalizationException.class,
                () -> normalizer.normalize("CC[Fe]", options));
        assertEquals(ChemblRejection.DISALLOWED_ELEMENT, exception.reason());
    }
}

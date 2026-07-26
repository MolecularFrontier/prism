package tech.molecules.structurized.chembl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChemblPropertyCalculatorTest {
    @Test
    void calculatesRingAndDrugLikeProperties() {
        NormalizedMolecule molecule = new ChemblNormalizer().normalize("CCOc1ccc(N)cc1", new ChemblFilterOptions(1, 60, -2, 2, ChemblFilterOptions.defaults().allowedElements()));
        ChemblProperties properties = new ChemblPropertyCalculator().calculate(molecule.molecule());

        assertEquals(10, properties.nonHydrogenAtoms());
        assertEquals(6, properties.ringAtoms());
        assertEquals(1, properties.rings());
        assertEquals(1, properties.aromaticRings());
        assertEquals(6, properties.largestRingSize());
        assertEquals(2, properties.heteroAtoms());
        assertTrue(properties.molecularWeight() > 130 && properties.molecularWeight() < 140);
        assertTrue(Double.isFinite(properties.clogp()));
        assertTrue(properties.tpsa() > 20);
    }
}

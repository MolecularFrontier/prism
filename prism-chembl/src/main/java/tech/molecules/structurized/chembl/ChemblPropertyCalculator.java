package tech.molecules.structurized.chembl;

import com.actelion.research.chem.MolecularFormula;
import com.actelion.research.chem.RingCollection;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.chem.prediction.PropertyCalculator;

public final class ChemblPropertyCalculator {
    public ChemblProperties calculate(StereoMolecule molecule) {
        molecule.ensureHelperArrays(StereoMolecule.cHelperRings);
        int heavy = 0;
        int ringAtoms = 0;
        int hetero = 0;
        int charge = 0;
        for (int atom = 0; atom < molecule.getAtoms(); atom++) {
            int atomicNo = molecule.getAtomicNo(atom);
            if (atomicNo != 1) heavy++;
            if (molecule.isRingAtom(atom)) ringAtoms++;
            if (atomicNo != 1 && atomicNo != 6) hetero++;
            charge += molecule.getAtomCharge(atom);
        }
        RingCollection rings = molecule.getRingSet();
        int aromatic = 0;
        int largestRing = 0;
        for (int ring = 0; ring < rings.getSize(); ring++) {
            if (rings.isAromatic(ring)) aromatic++;
            largestRing = Math.max(largestRing, rings.getRingSize(ring));
        }
        PropertyCalculator calculator = new PropertyCalculator(molecule);
        return new ChemblProperties(heavy, ringAtoms, rings.getSize(), aromatic, largestRing, calculator.getRotatableBondCount(), hetero, charge,
                new MolecularFormula(molecule).getRelativeWeight(), calculator.getLogP(), calculator.getPolarSurfaceArea());
    }
}

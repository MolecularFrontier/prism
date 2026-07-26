package tech.molecules.structurized.chembl;

import com.actelion.research.chem.StereoMolecule;

public record NormalizedMolecule(
        StereoMolecule molecule,
        int originalFragmentCount,
        String sourceSmiles
) {
}

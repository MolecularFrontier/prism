package tech.molecules.structurized.chembl;

public record ChemblProperties(
        int nonHydrogenAtoms,
        int ringAtoms,
        int rings,
        int aromaticRings,
        int largestRingSize,
        int rotatableBonds,
        int heteroAtoms,
        int formalCharge,
        double molecularWeight,
        double clogp,
        double tpsa
) {
}

package tech.molecules.structurized.chembl;

public record ChemblContextRow(
        String chemblId,
        String documentChemblId,
        String sourceId,
        String compoundKey,
        String assayChemblId,
        String targetChemblId
) {
}

package tech.molecules.structurized.chembl;

public record ChemblStructureRow(
        String chemblId,
        String sourceSmiles,
        String smiles,
        String idcode,
        String inchiKey,
        String parentChemblId,
        ChemblProperties properties,
        ChemblRecord source,
        int fragmentCount
) {
}

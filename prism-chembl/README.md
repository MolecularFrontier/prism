# prism-chembl

Headless ChEMBL structure export using OpenChemLib normalization.

Examples:

    mvn -q -pl prism-chembl -am package
    java -cp prism-chembl/target/classes:... tech.molecules.structurized.chembl.ChemblStructuresCli fetch --source sqlite --database chembl_XX.db --output structures.tsv


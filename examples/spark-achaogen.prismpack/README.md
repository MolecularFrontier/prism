# spark-achaogen.prismpack

Toy/demo PrismPack generated from the public SPARK Achaogen LpxC contribution.

Source: https://www.collaborativedrug.com/SPARK-data-downloads
Terms: https://www.collaborativedrug.com/hubfs/SPARK-data-terms-of-use.pdf

This is intentionally analysis-ready demo data, not an archival representation of the SPARK/Achaogen contribution. Per-strain MIC records are aggregated to species-level median endpoints. Censored numeric strings are imported as their numeric bound, and qualifier counts are retained in Prism TSV result details.

## Contents

- subjects: 1873
- endpoints: 59
- endpoint values: 28515
- strongest coverage: Primary panel pMIC - Pseudomonas aeruginosa: 1860, Primary panel MIC - Pseudomonas aeruginosa: 1860, Primary panel pMIC - Escherichia coli: 1835, Primary panel MIC - Escherichia coli: 1835, Primary panel pMIC - Klebsiella pneumoniae: 1597, Primary panel MIC - Klebsiella pneumoniae: 1597, Primary panel pMIC - Staphylococcus aureus: 1364, Primary panel MIC - Staphylococcus aureus: 1364

## Notes

- Includes LpxC biochemical potency, primary-panel MIC/pMIC, LpxC-panel MIC/pMIC, cytotoxicity, protein binding, population MIC50/90, P. aeruginosa clinical-isolate MIC5, and rat IV PK endpoints.
- The Prism TSV bundle includes explicit `ASSAY_MEASURED` subject sets for each endpoint.
- Use according to the SPARK Data Terms of Use and cite/attribute source datasets appropriately.

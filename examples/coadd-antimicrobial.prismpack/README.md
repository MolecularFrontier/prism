# coadd-antimicrobial.prismpack

Toy/demo PrismPack generated from the public CO-ADD dose-response CSV export.

Source: https://db.co-add.org/downloads

This is intentionally analysis-ready demo data. Censored values such as `>10` and `<0.1` are imported as their numeric bound for easy plotting/filtering. The original string and qualifier are retained in Prism result details in the TSV bundle.

## Contents

- subjects: 4803
- endpoints: 46
- dose-response rows imported: 42001
- endpoint value types: {'CC50': 4598, 'MIC': 35008, 'HC10': 2603}
- units: {'uM': 8379, 'ug/mL': 33808, 'missing': 22}

## Notes

- Endpoint IDs are assay/type/unit specific because CO-ADD contains both `ug/mL` and `uM` measurements.
- The Prism TSV bundle includes explicit `ASSAY_MEASURED` subject sets for each endpoint.
- The PrismPack default view foregrounds the main high-coverage organism, cytotoxicity, and haemolysis assays in `ug/mL`.

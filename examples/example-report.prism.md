---
prismReportVersion: 1
dataset: current
title: Example SAR overview
id: example-sar-overview
createdAt: 2026-08-22T10:00:00Z
---

# Example SAR overview

This report demonstrates a live compound table bound to the current Prism dataset.
Selecting compounds here also selects them in the main PrismLite table.

~~~prism
{
  "type": "compound-table",
  "id": "key-compounds",
  "rowSet": "all",
  "structureColumn": "smiles",
  "columns": [
    {
      "column": "compound_id",
      "label": "Compound"
    },
    {
      "column": "pIC50",
      "label": "pIC50",
      "format": "0.00"
    },
    {
      "column": "clogP",
      "label": "cLogP",
      "format": "0.0"
    }
  ],
  "linkSelection": true,
  "maxRows": 200
}
~~~

---
prismReportVersion: 1
dataset: current
title: Agent-materialized scaffold SAR
id: scaffold-sar-template
---

# Scaffold SAR

This report expects an agent to have materialized a scaffold analysis with
`output_prefix: "sar.series_a"`. Replace the endpoint and score column IDs with those returned
for the active Prism session.

## R1 overview

~~~prism
{
  "type": "sar-1d",
  "id": "series-a-r1",
  "title": "R1 SAR",
  "rowSet": "sar.series_a.matched",
  "substituentColumn": "sar.series_a.R1",
  "values": [
    {
      "column": "pIC50",
      "label": "pIC50",
      "format": "0.00",
      "aggregation": "best",
      "colorColumn": "pIC50.score"
    },
    {
      "column": "clogP",
      "label": "cLogP",
      "format": "0.0",
      "aggregation": "median",
      "colorColumn": "clogP.score"
    }
  ],
  "linkSelection": true
}
~~~

## R1 x R2 matrix

The mixed-context marker indicates that another observed R dimension varies inside a cell.

~~~prism
{
  "type": "sar-2d",
  "id": "series-a-r1-r2",
  "title": "R1 x R2 SAR",
  "rowSet": "sar.series_a.matched",
  "rowSubstituent": "sar.series_a.R1",
  "columnSubstituent": "sar.series_a.R2",
  "values": [
    {
      "column": "pIC50",
      "label": "pIC50",
      "format": "0.00",
      "aggregation": "best",
      "colorColumn": "pIC50.score"
    },
    {
      "column": "clogP",
      "label": "cLogP",
      "format": "0.0",
      "aggregation": "median",
      "colorColumn": "clogP.score"
    }
  ],
  "maxRowGroups": 24,
  "maxColumnGroups": 24,
  "linkSelection": true
}
~~~

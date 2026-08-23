---
prismReportVersion: 1
dataset: current
title: Example SAR overview
id: example-sar-overview
createdAt: 2026-08-22T10:00:00Z
---

# Example SAR overview

This report demonstrates reusable Prism views bound to the current dataset. Selection is
bidirectional: selecting compounds in a report block or another PrismLite view updates both.

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

## Focused lead comparison

Comparison cards place the reference first and calculate numeric differences without copying any
values into the report. Clicking a card selects that compound everywhere in PrismLite.

~~~prism
{
  "type": "compound-cards",
  "id": "lead-comparison",
  "title": "Lead progression",
  "rowSet": "all",
  "structureColumn": "smiles",
  "titleColumn": "compound_id",
  "referenceRow": "CMPD-002",
  "properties": [
    {
      "column": "pIC50",
      "label": "Activity",
      "format": "0.00",
      "showDelta": true
    },
    {
      "column": "clogP",
      "label": "cLogP",
      "format": "0.0",
      "showDelta": true
    },
    {
      "column": "HLM_CLint",
      "label": "HLM CLint",
      "format": "0",
      "showDelta": true
    }
  ],
  "linkSelection": true,
  "maxCards": 6
}
~~~

## Dataset summary

The same summary calculation used by Prism views can be embedded for a named row set.

~~~prism
{
  "type": "column-summary",
  "id": "property-overview",
  "title": "Property overview",
  "rowSet": "all",
  "columns": ["pIC50", "clogP", "HLM_CLint", "series"]
}
~~~

## Potency versus lipophilicity

The scatter plot participates in the shared row selection. Modifier-click toggles a point;
dragging selects a rectangular region. The block can also be opened as a full PrismLite view.

~~~prism
{
  "type": "scatter",
  "id": "potency-vs-clogp",
  "title": "pIC50 versus cLogP",
  "rowSet": "all",
  "xColumn": "clogP",
  "yColumn": "pIC50",
  "colorColumn": "series"
}
~~~

## Structure overview

~~~prism
{
  "type": "structure-grid",
  "id": "structure-overview",
  "title": "Structures ranked by potency",
  "rowSet": "all",
  "structureColumn": "smiles",
  "valueColumns": ["compound_id", "pIC50", "clogP"],
  "sortBy": "pIC50",
  "sortDirection": "descending",
  "maxCompounds": 24,
  "gridColumns": 4
}
~~~

## Agent-defined scores

An agent-created endpoint score is a normal numeric runtime column. After defining one through
Structurized MCP, a report can reference its returned column ID in any compatible block, for
example as a compound-table column, scatter axis, or scatter color column. Exporting the session
as a new PrismPack makes that score definition, column, and the current row sets portable.

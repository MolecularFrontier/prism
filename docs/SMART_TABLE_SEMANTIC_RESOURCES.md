Prism smart-table semantic resources
====================================

Status
------

This document is an incremental architecture. `PrismGrouping`, exclusive and
overlapping memberships, hierarchy metadata, runtime grouping facets,
`PrismRowGraph`, session validation, graph/grouping publication through
operations, and row-graph views are implemented. Similarity spaces, generic
resource-aware view validation, and additional semantic facets remain proposed
APIs. The surrounding implemented model is described in
[Prism advanced data model](PRISM_DATA_MODEL.md).

Design goal
-----------

Prism should be more expressive than a dataframe without becoming an analysis
framework for every scientific algorithm. The central boundary is:

> Prism understands how a result relates to stable rows. Providers and plugins
> understand how to calculate the result.

The model is a small algebra of row-anchored resources:

```text
Row -> scalar value             column
Set<Row>                        row set
Row -> Group(s)                 grouping
Row -> feature representation  similarity space
Row x Row -> explicit edge     row graph
Rows/resources -> display      view
algorithm-specific detail      external artifact
```

Every resource uses stable Prism row IDs, declares its source scope, and
records provenance. These common shapes let PrismLite, headless clients, and
agents navigate sophisticated results without Prism depending on clustering,
chemistry, or machine-learning implementations.

Groupings
---------

A grouping describes organized membership of session rows. It can represent:

* an exclusive hard clustering;
* chemical series;
* hierarchical clusters;
* overlapping series assignments;
* weighted or fuzzy memberships;
* classification labels;
* train, validation, and test partitions.

The implemented semantic shape is:

```text
PrismGrouping
    grouping ID, title, source scope, provenance
    groups
        stable group ID
        label and description
        optional parent group
        optional representative row
        group metadata
    memberships
        row ID
        group ID
        optional weight or confidence
        optional role
```

The grouping is the authoritative membership resource. A group can be turned
into a named `PrismRowSet` when it becomes an explicit reusable analysis scope,
but a grouping with hundreds of groups should not automatically create
hundreds of persistent row sets.

### Virtual table facets

Semantic resources should expose scalar table-compatible facets when that
mapping is lossless. An exclusive grouping automatically exposes a read-only
runtime categorical column backed by the grouping:

```text
grouping: series-clustering
facet:    series-clustering.group

row CMPD-001 -> stable value series-3 -> display label Aminopyridines
```

The facet participates in existing table display, sorting, filtering, coloring,
and operation inputs without copying membership data. It may be materialized
explicitly for persistence or export. The stable group ID is the cell value;
the group label is presentation metadata.

Overlapping membership must not be silently reduced to one category. Such a
grouping may expose an unambiguous `membership_count` facet, an explicitly
defined primary-membership facet, or an on-demand boolean facet for one group.
Views that support overlapping or hierarchical membership can consume the
grouping directly.

This adds a fourth runtime column lifecycle beside base, computed, and
materialized columns:

```text
semantic facet column
    read-only scalar adapter over a richer Prism resource
```

Similarity spaces
-----------------

A descriptor normally defines `Row -> Vector`, while its metric induces
`Row x Row -> Number`. Prism should not require a dense quadratic similarity
matrix. It should understand a named runtime capability that can compare rows:

```text
PrismSimilaritySpace
    ID and title
    source row scope
    source structure/data columns
    descriptor and metric metadata
    implementation fingerprint
    similarity(leftRowId, rightRowId)
    nearestNeighbors(rowId, scope, limit)
    provenance
```

Provider implementations may use chemical fingerprints, numerical descriptors,
learned embeddings, pharmacophore features, or assay profiles. Prism owns the
contract and stable-row validation; a plugin such as Structurized owns vector
generation, indexing, caches, and domain-specific configuration.

Generic Prism operations can use a similarity space to create:

* a similarity-to-query materialized column;
* a nearest-neighbor row set;
* a sparse nearest-neighbor row graph;
* a clustering grouping;
* a ranked or graph view.

A two-dimensional embedding is not itself a similarity space. Its coordinates
are ordinary materialized columns consumed by a scatter view, while the source
high-dimensional space may remain available for neighbor queries.

Row graphs
----------

`PrismRowGraph` is a typed property multigraph whose vertices are stable Prism
row IDs. It supports either directed or undirected semantics per graph,
parallel labeled edges, and typed edge properties.

```text
PrismRowGraph
    graph ID and title
    directionality
    domain row set
    typed edge schema
    edge table or provider
    provenance

edge
    stable edge ID
    source row ID
    target row ID
    edge label/type
    typed property values
```

Vertex properties are not copied into the graph. Renderers resolve structures,
labels, endpoints, and derived values from the runtime table through row IDs.
The graph domain row set includes isolated vertices as well as vertices with
edges.

An edge table can retain information that is not safely recomputable, including
scores, confidence, evidence, transformation IDs, property deltas, and artifact
references. Large chemistry-specific payloads such as detailed atom mappings
remain external artifacts referenced by a typed edge property.

The bounded initial graph model supports:

* directed or undirected graphs;
* stable edge IDs and parallel edges;
* edge labels and typed properties;
* neighbor and edge queries;
* immutable graph snapshots;
* filtering edges and deriving row sets;
* graph views and generic graph operations.

Hyperedges, non-row vertices, arbitrary Java edge objects, mixed directionality,
mutable graph editing, and a distributed graph database are explicitly
deferred.

Views and composition
---------------------

Ordinary visual channels should continue to consume scalar columns, including
virtual semantic facets:

```text
x, y, size -> numeric columns
color      -> categorical grouping facet
label      -> text column
```

Views that genuinely require richer behavior reference semantic resource IDs:

```text
graph view       -> row graph ID
group browser    -> grouping ID
hierarchy view   -> hierarchical grouping ID
```

View specifications therefore need generic referenced-resource validation in
addition to current column and row-set references. Each renderer declares the
resource types it understands.

The concepts compose through generic operations:

```text
similarity space
    -> nearest-neighbor graph
    -> graph clustering
    -> grouping
    -> virtual group column
    -> colored scatter or structure-grid view
```

```text
transformation graph
    -> connected components
    -> chemical-series grouping
    -> selected group row set
```

Ownership boundary
------------------

Prism owns:

* stable row identity and resource validation;
* columns, row sets, groupings, similarity-space contracts, and row graphs;
* virtual scalar facets over semantic resources;
* declarative views and generic transformations between resource shapes;
* standardized provenance, scope, and lifecycle rules.

Structurized and other providers own:

* chemistry toolkits, fingerprints, and descriptor implementations;
* clustering, decomposition, MMP, and prediction algorithms;
* caches and indexes;
* chemistry-specific interpretation;
* detailed and potentially large analysis artifacts.

A provider publishes only generic interactive projections into Prism. For
example, clustering may publish a grouping, a similarity facet, and an overview
view while retaining algorithm diagnostics in a provider-owned artifact.

Scope, provenance, and lifecycle
--------------------------------

Derived resources should use immutable source scopes. If an operation starts
from the current visible projection, it first freezes those stable row IDs into
a row set. Subsequent filter changes must not silently change the scientific
population that produced the result.

Every resource should identify:

* producer and operation;
* source session and row-set scope;
* source columns or resources;
* implementation and configuration fingerprints;
* source session revision;
* optional external artifact reference.

The initial resource model should favor immutable, add-only results. Replacing
or recomputing a scientific definition creates a distinguishable resource ID or
version instead of silently changing the meaning of an existing result.

Non-goals
---------

Prism should not add algorithm-specific core types such as
`ClusteringAnalysis`, `DecompositionAnalysis`, or `PredictionModel`. It should
also avoid an unrestricted generic resource container. The small typed row
algebra is intended to be expressive enough for advanced analysis while
remaining inspectable, validatable, and naturally connected to the table.

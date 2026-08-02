# Live evaluation runtime

## Purpose

The Prism live runtime evaluates fast or remote capabilities when a committed
session resource changes. Its first input type is a molecule document, but the
runtime is chemistry-independent and provider-driven.

The scheduling decision and the scientific computation are separate:

```text
committed molecule revision
    -> binding policy (automatic, manual, or off)
    -> quiet period and generation check
    -> capability provider
    -> shared prerequisite futures
    -> generic live result
```

This keeps debounce, cancellation-by-supersession, caching, and status handling
out of chemistry providers.

## Workspace ownership

`PrismWorkspace` is the authoritative interactive aggregate:

```text
PrismWorkspace
    PrismSession             immutable source table plus analysis state
    PrismMoleculeWorkspace   committed molecule lists and documents
    PrismLiveContext         providers, bindings, cache, and evaluations
```

Workspace revisions cover semantic shared state. Live evaluation progress has a
separate monotonic sequence because queued/running/completed transitions must
not invalidate optimistic workspace edits.

| Change | Workspace revision | Live sequence |
| --- | --- | --- |
| Molecule document commit | yes | evaluations are queued |
| Evaluator mode/configuration change | yes | yes |
| Queued to running | no | yes |
| Running to succeeded/failed | no | yes |

Revisions are synchronization guards, not undo history.

## Core contracts

`PrismLiveCapability<T>` identifies a typed reusable computation result.

`PrismLiveComputationProvider<T>` supplies:

- capability ID and output type;
- immutable provider version;
- input support check;
- deterministic input/configuration fingerprint;
- asynchronous computation.

`PrismLiveBinding` publishes a capability as a user-facing evaluator and stores
a stable binding ID, capability ID, execution mode, quiet period, and provider
configuration. Bindings are workspace state. Providers are application
capabilities and are registered when the workspace is created.

`PrismLiveResult` is the generic publishable result:

```text
schemaId
values
warnings
metadata
```

Providers may describe field labels and units in metadata. Clients must remain
usable for unknown schemas.

## Dependencies and reuse

A provider obtains prerequisites through:

```java
context.require(otherCapability)
```

The context resolves one future per:

```text
capability ID
+ provider version
+ input fingerprint
+ configuration fingerprint
```

Concurrent downstream evaluators therefore share in-flight work. Completed
results enter a bounded per-workspace LRU cache. Failed computations are not
cached. Dependency cycles fail explicitly.

For example, the OpenChemLib basic-properties and structure-summary evaluators
both require the same decoded molecule capability. One committed structure
revision is decoded once.

## Scheduling and stale work

Automatic bindings wait for their configured quiet period. Every new input
revision increments a root generation and supersedes the pending or running
generation.

Java futures are not forcibly interrupted. A slow provider may finish, but its
completion is published only when its generation is current and the document
still exists at the matching revision.

The last successful result remains available while a newer revision is queued,
running, or failed. `showingStaleResult()` lets a client label that value
honestly instead of blanking useful context.

Evaluation states are:

```text
QUEUED
RUNNING
SUCCEEDED
FAILED
UNSUPPORTED
DISABLED
```

Manual execution uses the same computation path and cache as automatic
execution.

## Frontend interaction

An editor gesture is local UI state. PrismLite Web waits 500 ms after the last
change, encodes one molecule snapshot, and commits one guarded document update:

```java
molecules.updateDocument(documentId, expectedRevision, ...);
```

If another client or agent committed first, Prism reports the current revision.
The UI can reload shared state or save its captured draft as a new document.
Live evaluators begin only after the document commit. They never observe mouse
movement or partially edited structures.

Background evaluation notifications enter Vaadin through `UI.access(...)` and
server push. Swing hosts should use the EDT for presentation updates. All
workspace mutations pass through the host-provided `PrismWorkspaceExecutor`.

## Boundaries

Prism owns orchestration, typed capabilities, bindings, status, caching, and
generic results. Domain modules own scientific calculations and integrations.
Prism does not own model training/deployment, chemistry algorithms, remote
credentials, durable task queues, UI widgets, or forced cancellation of
arbitrary provider code.

Structurized exposes the same live context through managed sessions and MCP.
PrismLite Web consumes it directly, so human and agent operations share one
document revision and evaluator state.

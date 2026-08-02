# Interactive Prism workspaces

## Purpose

`PrismSession` deliberately assumes a fixed base table. Row identity, computed
value caches, materialized columns, filters, groupings, graphs, and views all
depend on that invariant.

Interactive applications also need shared molecule documents, live evaluations,
ordered changes, and optimistic mutation guards. These belong to a small
workspace aggregate rather than to the immutable table model.

## Implemented `PrismWorkspace`

The generic workspace belongs in Prism rather than in a Swing, Vaadin, MCP, or
chemistry-specific module.

```java
final class PrismWorkspace {
    String workspaceId();
    long revision();
    PrismSession session();
    PrismMoleculeWorkspace molecules();
    PrismLiveContext liveContext();
    <T> T callAs(PrismWorkspaceChangeOrigin origin, Supplier<T> action);
    <T> T callAs(PrismWorkspaceChangeOrigin origin,
                 Long expectedRevision,
                 Supplier<T> action);
    PrismWorkspaceSubscription subscribe(Consumer<PrismWorkspaceChange> listener);
}
```

The exact Java shape may evolve, but the contract must provide:

- one fixed `PrismSession`;
- one molecule workspace;
- one live evaluation context;
- a monotonic notification revision;
- typed change notifications and an origin;
- serialized mutations;
- optional expected-revision guards.

Structurized `ManagedPrismSession` delegates these responsibilities to the
Prism workspace and retains canonical dataset context and MCP adaptation.

## Revisions are synchronization, not undo

A workspace revision answers:

> Has observable workspace state changed since the state I rendered?

It does not imply that the complete prior state is stored. Initial revisions
support stale-update detection, event ordering, refresh coalescing, optimistic
guards for agent mutations, diagnostics, and provenance.

Undo requires explicit reversible commands or snapshots and is deferred.

## Change model

The implemented change types cover:

```text
PROJECTION       filters, sorting, active/visible rows
STRUCTURE        columns, row sets, groupings, graphs
VIEWS            view records or view configuration
MOLECULES        molecule documents and lists
LIVE_CONFIGURATION evaluator bindings and execution modes
```

Every notification carries workspace, revision, type, and origin. Origins
distinguish local UI, agent/MCP, background system work, and external domain
refresh.

Listeners observe already committed state. Listener failure must not roll back
a mutation.

## Serialized mutation boundary

`PrismSession` is not designed for concurrent writes. Each workspace therefore
has one mutation executor. A mutation runs to completion before the next
mutation begins and emits at most one merged workspace notification.

This executor may be implemented differently by each host:

- direct/synchronized execution for tests;
- Swing EDT execution for a desktop application;
- a lock or single-thread executor for a server workspace.

The semantic behavior remains the same.

Agent-facing mutations may include `expectedRevision`. If the current revision
does not match, the host rejects the mutation and returns the current revision
instead of applying an instruction to unexpected state. Ordinary UI commands
may omit the guard when generated from the currently locked UI.

## Deferred: immutable session replacement

The current `PrismWorkspace` deliberately owns one fixed `PrismSession`.
Session replacement and rebase are a future host capability, not part of the
implemented runtime.

A future replacement flow may look like this:

```text
domain revision 17
    -> build immutable table snapshot 17
    -> create replacement PrismSession
    -> rebase compatible workspace state by stable IDs
    -> atomically install generation 8
    -> publish workspace revision 42
```

The old session remains valid for in-flight readers until replacement, but new
commands resolve against the installed generation.

### Rebase defaults

Rebasing uses stable row and column IDs, never physical row positions.

| State | Default behavior |
| --- | --- |
| Visible columns and order | Preserve IDs that still exist; append new default-visible columns |
| Sort keys | Preserve keys whose columns still exist |
| Filters | Preserve filters whose referenced columns/row sets remain valid |
| Focus and selection | Preserve surviving stable row IDs in client state |
| Row sets | Intersect with surviving rows and retain provenance |
| Materialized columns | Reindex row-ID-backed values; new rows are missing |
| Computed definitions | Re-register when dependency columns remain available |
| Groupings and graphs | Preserve only when all referenced IDs and schemas remain valid |
| Views | Preserve when all referenced columns/resources remain valid |
| Molecule workspace | Preserve unchanged |

The rebase result reports preserved, dropped, and modified items. Silent
reinterpretation is forbidden. An analysis produced for an older source scope
keeps its original provenance even if it remains displayable.

The first implementation may support only view state, filters, sorting, row
sets, focus, and selection. Unsupported derived resources should be dropped
with warnings rather than copied incorrectly.

## Shared source versus collaboration workspace

A multi-user design application has two separate revision domains:

```text
Project source revision
    shared durable facts and cached in-memory project snapshot

Prism workspace revision
    one human/agent collaboration session's analytical state
```

One project source change may cause several workspaces to rebase. Each
workspace retains its own filters, views, and molecule documents.

```text
                    +-> chemist A workspace
project snapshot ---+-> chemist B workspace
                    +-> automated analysis workspace
```

This prevents unrelated users from sharing transient analysis state while
still reflecting durable project changes promptly.

## Local and shared state

Shared within one workspace:

- applied filters and sorting;
- row sets, groupings, graphs, columns, and views;
- committed molecule documents and lists;
- operation results and provenance.

Local to one UI/browser tab:

- hover and open menus;
- current panel sizes;
- unfinished filter and sketcher gestures;
- current zoom/pan;
- potentially focus and selection until deliberately shared.

An agent receives committed semantic state. A UI should not stream every mouse
movement or sketcher drag into the workspace.

## Snapshot and reconnect

A client joining or reconnecting asks for a workspace snapshot containing:

- workspace ID and revision;
- active session generation;
- session/table summary and current projection;
- registered row sets, resources, and views;
- molecule-list summaries;
- capabilities and operation descriptors.

After loading the snapshot, the client subscribes from that revision. If the
host cannot replay intermediate events, it instructs the client to fetch a new
snapshot. Durable event sourcing is not required for the first implementation.

## Persistence boundary

`PrismWorkspace` is an interactive runtime, not a database. A host application
decides what to persist:

- PrismPack export may persist one analysis snapshot;
- user preferences may persist through a settings service;
- Drugforge commands persist through Drugforge services;
- molecule documents may remain ephemeral unless explicitly published.

Loading a persisted source and replacing a session are application concerns;
Prism provides the safe runtime mechanism.

## Acceptance criteria for the first host

- A UI and an agent can observe the same workspace revision.
- One agent operation results in one ordered client refresh.
- Concurrent mutations are serialized.
- Molecule updates reject stale expected document revisions.
- Rapid edits start one evaluation for the latest committed revision.
- Shared live prerequisites are computed once per fingerprint.
- Unrelated user workspaces do not share transient state.

## Related documents

- [Live evaluation runtime](LIVE_EVALUATION_RUNTIME.md)
- [PrismLite Web architecture](PRISMLITE_WEB_ARCHITECTURE.md)
- [Local and remote clients](LOCAL_AND_REMOTE_CLIENTS.md)
- [Prism advanced data model](../PRISM_DATA_MODEL.md)

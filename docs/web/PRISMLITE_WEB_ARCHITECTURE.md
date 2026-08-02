# PrismLite Web architecture

## Status

This document defines the intended architecture for an open-source Vaadin
frontend for Prism. It is a design target, not a description of an existing
module.

The primary product decision is:

> PrismLite Web is a native Vaadin presentation of Prism workspaces. It shares
> engine contracts and application behavior with PrismLite Swing, but it does
> not attempt to reuse Swing components.

## Product boundary

PrismLite Web should provide the generic, DataWarrior-like part of the system:

- open an in-memory `PrismSession` or PrismPack;
- inspect, sort, filter, select, and navigate wide scientific tables;
- render structures and other semantic column types;
- show column and row inspectors;
- manage row sets, groupings, graphs, and declarative views;
- edit session-local molecule documents and molecule lists;
- execute registered Prism operations;
- expose the same workspace to an agent when a host application enables it.

It should not contain:

- company-specific authentication or authorization;
- database schemas and JDBC services;
- project registration rules;
- Idorsia/Neon implementation classes;
- deployment-specific model-serving clients;
- assumptions about one design or registration workflow.

An internal application may depend on PrismLite Web and contribute those
capabilities through explicit extension contracts.

## Proposed repository and modules

The web frontend should be a separate open-source repository so its Vaadin and
Spring Boot release lifecycle does not constrain the core Prism artifacts.

```text
prism
    protocol, PrismPack, engine, operations, workspace contracts

prism-lite-web
    prismlite-web-components
        reusable Vaadin components and frontend-neutral presenters
    prismlite-web-spring
        Spring/Vaadin workspace scopes and lifecycle integration
    prismlite-web-app
        runnable reference application for local files and demos

Idorsia design application
    depends on Prism, PrismLite Web, Neon, and Drugforge V2
```

The component module should not open databases or create application-wide
singletons. Its public entry point receives a workspace and registries from the
host application.

## Main workspace

The initial web workspace follows the same interaction model as PrismLite
Swing, implemented with Vaadin components:

```text
+----------------+------------------------------+------------------+
| Column         |                              | Column / Row     |
| navigator      |          Data grid           | inspector        |
|                |                              |                  |
+----------------+------------------------------+------------------+
| Applied filter shelf            visible / total rows             |
+------------------------------------------------------------------+
| Views | Molecules | Operations | Tasks                            |
+------------------------------------------------------------------+
```

The table remains the primary workspace. Navigator and inspector panels are
collapsible. Compact desktop layouts are the first target; mobile editing is
not a first-version goal.

### Table projection

Vaadin `Grid` reads from the current Prism visible-row projection. The server
retains the authoritative table and row-ID mapping; the browser does not
receive the complete dataset merely to implement sorting or filtering.

Selection is translated to stable Prism row IDs before it leaves the component.
Visible and physical row numbers must not become application-level identity.

### Filters

Web filter editors use the same draft-versus-applied interaction as Swing:

- editor gestures update client/session-local drafts;
- Apply commits one semantic filter change;
- Apply All commits all drafts in one workspace mutation;
- the shelf renders applied `PrismFilter` instances only;
- a database-backed host does not interpret a Prism filter as a database write.

### Structures

Molecule rendering and sketching should use an OpenChemLib-compatible Vaadin
component. IDCode and supplied 2D coordinates remain distinct values. A
renderer may compute coordinates when missing, but it must cache that result
and must not mutate the source structure on repaint.

### Views

Prism view specifications remain frontend-independent. Web renderers are
registered by view type, just as Swing renderers are today. A missing renderer
produces an inspectable unsupported-view state rather than failing the whole
workspace.

## Extension contracts

The web frontend should offer small registries analogous to the current Swing
extension points:

```java
interface PrismWebColumnRendererProvider {
    boolean supports(PrismColumnSchema column);
    Component createCell(PrismWebCellContext context);
}

interface PrismWebInspectorSectionProvider {
    boolean supports(PrismInspectorContext context);
    Component create(PrismInspectorContext context);
}

interface PrismWebViewRenderer {
    String viewType();
    Component create(PrismWebViewContext context, PrismViewRecord view);
}
```

Equivalent registries should exist for commands/actions and optional workspace
panels. Provider ordering and duplicate IDs must be deterministic. These are UI
extension contracts; scientific operations continue to register with
`PrismOperationRegistry`.

An internal application can therefore add Drugforge design-set navigation,
registration and publishing actions, Neon endpoint details, synthesis and
comments, and internal graph renderers without changing public PrismLite Web
code.

## State ownership

| State | Owner | Typical lifetime |
| --- | --- | --- |
| Immutable dataframe and semantic definitions | Prism session snapshot | Workspace generation |
| Applied filters, sorting, row sets, views | Prism workspace | Human/agent collaboration session |
| Molecule documents and lists | Prism workspace | Human/agent collaboration session |
| Focus, hover, open dialogs, unfinished drafts | Vaadin UI | Browser tab |
| User preferences | Host application | User/account |
| Project and design entities | Host domain service/database | Durable |

A shared project cache must not imply one shared Prism analysis session for all
users. Otherwise one chemist's filters and selections would change another
chemist's screen.

## Vaadin lifecycle and concurrency

One browser UI subscribes to one workspace while attached and closes its
subscription when detached. Workspace notifications may originate on a
background thread, so UI changes must enter through `UI.access(...)`.

Server push is required when changes can originate from an MCP/agent operation,
another browser session, a background task, or a project snapshot refresh.
Rapid notifications should be coalesced by workspace revision.

All mutations of one workspace pass through its serialized executor. Vaadin's
session lock protects a browser UI; it does not by itself serialize MCP and
background-task mutations of the shared workspace.

## Errors and tasks

Long-running work should produce a task handle with progress, cancellation,
warnings, and a terminal result. Components display loading and failure states
without blocking the Vaadin request thread.

Domain command failures and analytical operation failures are separate:

- a failed database command leaves the domain snapshot unchanged;
- a failed Prism operation leaves the session unchanged;
- a failed UI refresh can be retried from the latest workspace snapshot.

## First implementation slice

1. Open a local PrismPack into one session-scoped workspace.
2. Show the table, navigator, inspectors, filter shelf, and row count.
3. Support numeric, categorical, text, and structure rendering/filtering.
4. Render existing structure-grid and scatter views.
5. Show molecule documents and lists.
6. Prove that an external operation is reflected through Vaadin push.

Database integration, cross-user collaboration, and Drugforge-specific
commands belong to the composition application, not this reference slice.

## Related documents

- [Live evaluation runtime](LIVE_EVALUATION_RUNTIME.md)
- [Interactive Prism workspaces](INTERACTIVE_PRISM_WORKSPACES.md)
- [Local and remote clients](LOCAL_AND_REMOTE_CLIENTS.md)
- [Prism advanced data model](../PRISM_DATA_MODEL.md)
- [Prism smart-table semantic resources](../SMART_TABLE_SEMANTIC_RESOURCES.md)

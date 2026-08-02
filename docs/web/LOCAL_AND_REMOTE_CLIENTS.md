# Local and remote Prism clients

## Decision

Vaadin is the primary frontend for the future web-based design application.
Prism should nevertheless keep the application layer independent of Vaadin so
other clients can use the same workspace later.

There is no practical automatic conversion between Vaadin and Swing widgets:

- Vaadin components render browser-side web components and communicate with a
  server-side component tree;
- Swing components render locally in a desktop JVM;
- layout, event, lifecycle, and threading models are different.

The reusable layer is the Prism data model, workspace, operations, commands,
revisions, descriptors, and extension metadata.

## Supported deployment shapes

### Hosted Vaadin application

This is the primary production design:

```text
browser -> Vaadin application -> Prism workspace -> domain services
                                      |
                                      +-> agent/MCP adapter
```

It provides centralized deployment, authentication, database access, and
collaboration. No scientific dataset needs to be copied into browser memory.

### Self-contained local Vaadin application

The cheapest desktop-like distribution packages the same Spring Boot/Vaadin
application as a runnable JAR:

```text
java -jar prismlite-web-app.jar dataset.prismpack
    -> bind localhost on an available port
    -> open the system browser
```

This reuses the complete web UI and can run server logic and data in one local
JVM. It should default to loopback-only binding, use a random or configurable
port, and shut down cleanly when requested.

A native embedded browser shell is optional and should not be required. JCEF,
JavaFX WebView, or similar wrappers add packaging and platform complexity while
providing little scientific functionality.

### Remote Swing client

A future PrismLite Swing client may connect to a hosted workspace:

```text
PrismLite Swing -> workspace transport -> server Prism workspace
```

This requires a real transport contract for authentication, workspace
discovery, snapshot loading, projected table reads, semantic commands,
operation execution, revision subscriptions, task progress, reconnect, and
stale-revision handling.

Existing Swing table, inspector, renderer, and molecule components can be
adapted, but this is a separate frontend implementation. It is not free merely
because the server is Java.

### Bundled server and Swing

The same Swing client may run beside a local server in one JVM. An in-process
transport implements the same client contract without HTTP or WebSocket:

```text
Swing client -> in-process workspace client -> Prism workspace
```

This can simplify local integration and testing, but it does not remove the
cost of building design-specific Swing components. It is useful only if native
desktop workflows offer a concrete advantage over the local Vaadin bundle.

## Transport policy

Do not design a public network protocol solely for hypothetical Swing support.
First define a frontend-neutral Java application API and use it in-process from
Vaadin. Introduce HTTP/WebSocket serialization when a real second-process
client is scheduled.

The Java API should avoid returning Vaadin types so it can map cleanly to a
transport later. Use stable IDs, immutable records, semantic commands, task
handles, and explicit errors.

## Capability discovery

Clients should discover available behavior rather than assume all plugins are
installed. A workspace description may expose:

- column semantic types and renderer hints;
- operation descriptors and parameters;
- supported view types;
- inspector/action contributions;
- whether molecule editing is enabled;
- whether durable publishing commands are available.

This lets the public reference app and the Idorsia composition app expose
different capabilities through the same client model.

## Recommendation

1. Build the Vaadin application first.
2. Make local browser-based deployment a supported mode.
3. Keep workspace and command APIs free of Vaadin types.
4. Continue extending PrismLite Swing for standalone analysis workflows.
5. Add a remote or bundled Swing design client only after identifying a
   workflow that materially benefits from native desktop integration.

## Related documents

- [PrismLite Web architecture](PRISMLITE_WEB_ARCHITECTURE.md)
- [Interactive Prism workspaces](INTERACTIVE_PRISM_WORKSPACES.md)

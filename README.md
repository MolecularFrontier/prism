prism
=====

Multi-module Java 23 Maven project for PRISM:

- `PRISM = Protocol for Representing Integrated Scientific Measurements`

Current modules
---------------

- `prism-core`
  - immutable PRISM model, result, provider, query, registry, validation, in-memory provider, and TSV bundle APIs

Coordinates
-----------

- Parent build: `tech.molecules:prism:0.1.0`
- Core module: `tech.molecules:prism-core:0.1.0`

Build
-----

- Requires Java 23 and Maven 3.9+
- Commands:
  - `mvn test`
  - `mvn -pl prism-core test`

Notes
-----

- Java package root: `tech.molecules.structurized.prism`
- Parent POM: [`pom.xml`](/home/lithom/dev_chem/prism/pom.xml)
- Core module POM: [`prism-core/pom.xml`](/home/lithom/dev_chem/prism/prism-core/pom.xml)
- Protocol notes: [`docs/PRISM_PROTOCOL.md`](/home/lithom/dev_chem/prism/docs/PRISM_PROTOCOL.md)
- TSV bundle notes: [`docs/PRISM_TSV_BUNDLE.md`](/home/lithom/dev_chem/prism/docs/PRISM_TSV_BUNDLE.md)

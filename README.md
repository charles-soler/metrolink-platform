# Metrolink Platform (BAS)

A modular Building Automation System (BAS) with a type-safe core, pluggable protocol connectors, and a path from offline edge to cloud.

## Repo layout
- **bas-core** — engine: ports (hexagonal), Kernel, Historian (demo), PollScheduler (demo), SPI
- **connector-sim** — demo connector implementing the SPI (discovery/read/write/subscribe)
- **smoke-app** — tiny console app proving end-to-end flow

## Quickstart
Requirements: JDK 21, Gradle wrapper (included)
```bash
# build everything
./gradlew build

# run the smoke console app
./gradlew :smoke-app:run

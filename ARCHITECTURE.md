# ARCHITECTURE

**Repo:** `metrolink-platform`  
**Status:** draft, kept in lockstep with code  
**Last updated:** 2025-08-16

---

## 1) Purpose

This document explains the structure and rationale of the Metrolink BAS platform so future you (or new collaborators)
can quickly understand:

- what each module does,
- how connectors plug in,
- how data flows end-to-end,
- and how to take this skeleton to production.

If you only have 60 seconds: read sections **2**, **3**, and **6**.

---

## 2) High-Level Overview

**Goal:** a modular, offline-capable Building Automation System with a **type-safe core** and **pluggable protocol
connectors** (BACnet first).

**Key patterns**

- **Hexagonal Architecture (Ports & Adapters):** the core defines narrow interfaces (“ports”); connectors implement
  them.
- **Microkernel/Plugin model:** connectors are discovered at runtime (via Java **ServiceLoader** for now).
- **Digital-twin friendly model:** devices/points normalize to simple **Node/Point** records with metadata (units,
  ranges, writability).

**Today’s working slice**

- `bas-core`: ports, `Kernel`, small `Historian` + `PollScheduler`, and the plugin SPI
- `connector-sim`: fake connector implementing the SPI
- `smoke-app`: console app proving discovery → read → subscribe flows

**Production direction (drop-in upgrades, no rewrites)**

- Add `connector-bacnet` wrapping **BACnet4J**
- Add `apps/edge-service` (Spring Boot shell) for HTTP/metrics/config
- Swap demo Historian/Scheduler for production implementations

---

## 3) Module Layout

```
metrolink-platform/
├─ bas-core/                 # Core engine (no Spring)
│  ├─ src/main/java/org/metrolink/bas/core/
│  │  ├─ Kernel.java
│  │  ├─ model/             # records: Device, Point, Value, Node, HealthStatus
│  │  ├─ ports/             # interfaces: Lifecycle, Discovery, Reader, Writer, Subscribe, Health
│  │  ├─ spi/               # ConnectorPlugin (the SPI)
│  │  ├─ historian/         # Historian, InMemoryHistorian (demo)
│  │  └─ scheduler/         # PollScheduler (demo)
├─ connector-sim/            # Demo connector (fake device), implements SPI
│  └─ src/main/resources/META-INF/services/
│     └─ org.metrolink.bas.core.spi.ConnectorPlugin
├─ smoke-app/                # Small console app; uses ServiceLoader + Kernel
└─ (later)
   ├─ connector-bacnet/      # Real BACnet connector using BACnet4J
   └─ apps/edge-service/     # Spring Boot shell (REST + Actuator/Metrics)
```

**Rules of the road**

- **Core is framework-free.** No Spring deps in `bas-core` or connectors.
- **Dependencies flow inward:** apps → connectors → core interfaces. Never the other way.
- **Connectors encapsulate protocol libraries.** (BACnet4J stays in `connector-bacnet`.)

---

## 4) Core Contracts (Ports)

Ports are minimal, purpose-named interfaces the core relies on. Connectors implement them.

```java
// Lifecycle of a connector
interface LifecyclePort {
  void init(Map<String,Object> cfg) throws Exception;
  void start() throws Exception;
  void stop() throws Exception;
}

// Device & point discovery
interface DiscoveryPort {
  List<Device> discoverDevices(Duration timeout) throws Exception;
  List<Point>  discoverPoints(Device device, Duration timeout) throws Exception;
}

// Telemetry
interface ReaderPort {
  Map<String,Value> read(List<String> pointIds) throws Exception;
}

// Commands
interface WriterPort {
  void write(String pointId, Object value, Map<String,Object> opts) throws Exception;
}

// Streaming updates (e.g., BACnet COV)
interface SubscribePort {
  AutoCloseable subscribe(List<String> pointIds, Flow.Subscriber<Value> sub) throws Exception;
}

// Health/metrics
interface HealthPort {
  HealthStatus health();
}
```

**SPI (plugin interface)**

```java
interface ConnectorPlugin extends LifecyclePort {
  String id(); // e.g., "sim", "bacnet"
  DiscoveryPort discovery();
  ReaderPort    reader();
  WriterPort    writer();
  SubscribePort subscribe();
  HealthPort    health();
}
```

**Data records (shallowly immutable)**

- `Device(id, name, meta)`
- `Point(id, deviceId, name, kind, writable, meta)`
- `Value(pointId, value, tsEpochMs)`
- `Node(id, deviceId, name, type, writable, meta)`
- `HealthStatus(up, metrics)`

> Records are final and identity-by-state. If a field holds a mutable collection, defensively copy in the canonical
> constructor for deep immutability.

---

## 5) Control Flows

**Discovery**

```
App → ServiceLoader → ConnectorPlugin
Kernel.discoverAndRegister():
  DiscoveryPort.discoverDevices()
  DiscoveryPort.discoverPoints(device)
  → build Nodes → register in-memory
```

**Read (poll)**

```
Scheduler → ReaderPort.read([pointIds]) → Map<String,Value>
→ Historian.append(value) → UI/CLI uses the historian (today: console prints)
```

**Subscribe (stream)**

```
SubscribePort.subscribe([pointIds], subscriber)
Connector pushes Value events → Historian.append → consumer reads “last N”
```

**Write**

```
Kernel.writeNow(pointId, value) → WriterPort.write(…, opts)
→ ack/err handling (opts may include BACnet priority, relinquish, etc.)
```

**Health**

```
HealthPort.health() → up? metrics map (latencies, error counts, etc.)
```

---

## 6) Design Decisions & Rationale

- **Java 21:** modern language features (`record`, `var`) and LTS stability.
- **ServiceLoader for plugins (now):** smallest thing that could work; zero framework.
    - **Later:** PF4J if/when you need hot-reload + classloader isolation.
- **No Spring in core/connectors:** keeps plugin boundary clean and testable.
    - **Spring only in the app shell:** HTTP endpoints, config, metrics (Actuator/Micrometer).
- **Type-safety first:** narrow, explicit interfaces; clear DTOs; unit-testable.
- **Backpressure & resilience in the core:** schedulers, retries, priorities (to be expanded).

---

## 7) From Demo to Production

**Replace demo pieces, keep contracts:**

- Historian → embedded DB (RocksDB/H2) with retention & compaction.
- Scheduler → priority lanes (critical/fast/slow), retry/backoff, device budgets.
- Connector-BACnet → real implementation (BACnet4J):
    - `Who-Is/I-Am` discovery; list objects/properties
    - `ReadPropertyMultiple` batching
    - `WriteProperty` with priority array & relinquish default
    - `SubscribeCOV` (+auto-renew) → map to `SubscribePort`
    - Health metrics: APDU timeouts, average read latency, COV heartbeats
- Edge service (Spring Boot):
    - `GET /nodes`, `GET /read?ids=…`, `POST /write`, `GET /health`
    - Actuator, Prometheus/OTel metrics
    - YAML/env config → per-connector `init(cfg)`

**Security (later)**

- Local auth for writes; audit log of commands
- Network posture (BACnet/IP segmentation, BBMD/SC)
- Secrets handling for credentials (if any)

---

## 8) Configuration

**Today:** pass a `Map<String,Object>` to `LifecyclePort.init(cfg)`.

**Tomorrow (Spring shell):** load YAML/env → map to typed config per connector, e.g.:

```yaml
connectors:
  bacnet:
    deviceId: 12345
    apduTimeoutMs: 3000
    bbmd:
      enabled: false
    cov:
      renewSeconds: 120
      defaultIncrement: 0.1
```

The Spring app hands this to the BACnet connector’s `init(cfg)`.

---

## 9) Threading Model (initial)

- **Connector threads:** a connector may run its own I/O or timers (e.g., the simulator’s 1s update).
- **Core scheduler:** `PollScheduler` uses a single scheduled executor for now.
- **Next:** one scheduler per priority lane; per-device concurrency limits; exponential backoff on timeouts;
  stop-the-world on overload to protect the historian.

---

## 10) Observability

- **Now:** `HealthPort.health()` returns up/metrics; `smoke-app` prints to console.
- **Next (Spring shell):**
    - **Actuator**: `/actuator/health`, `/actuator/metrics`
    - **Micrometer** counters/timers: read latency, write success rate, COV events/sec, queue depths
    - Structured logs with device/point context

---

## 11) Testing Strategy

- **Contract tests** (core): the same test suite runs against any `ConnectorPlugin`:
    - discovery → read → write → subscribe happy path
    - error handling, retries, timeouts
- **Sim connector** doubles as a test fixture.
- **BACnet tests**: emulator/simulator devices; golden test sequences (discovery/read/write/COV).
- **Chaos**: packet loss, device reboot mid-COV, slow responses → verify resilience.

---

## 12) Roadmap (short)

1. `connector-bacnet` (skeleton, compile-only)
2. `apps/edge-service` (Spring Boot shell: `/nodes`, `/read`, `/write`, Actuator)
3. Historian v1 (embedded store + retention)
4. Scheduler v1 (priority lanes + backoff)
5. BACnet COV auto-renew + health metrics
6. Security + audit (writes), packaging for edge

---

## 13) Glossary

- **Port:** an interface the core depends on (hexagonal boundary).
- **Adapter/Connector:** plugin that implements the ports for a specific protocol.
- **SPI:** Service Provider Interface — the Java plugin contract (`ConnectorPlugin`).
- **Historian:** time-series storage for telemetry.
- **COV:** Change of Value (BACnet subscription mechanism).

---

## 14) Conventions

- **Java:** 21, records for DTOs, explicit packages (`org.metrolink.bas.…`).
- **Commits:** Conventional Commits (`feat(core): …`, `docs(readme): …`).
- **CI:** GitHub Actions builds on each push/PR (`.github/workflows/gradle.yml`).
- **Dependencies:** protocol libraries live only in their connector module.

---

## 15) FAQ

**Why not Spring everywhere?**  
Keeping the core Spring-free preserves plugin isolation and test speed. Spring shines at the edges: HTTP, config,
metrics.

**Why ServiceLoader?**  
Simplest runtime discovery. If you later need hot-reload or classloader isolation, migrate the app shell to PF4J; the
SPI stays the same.

**Are records immutable?**  
Fields are final (shallow immutability). For deep immutability, defensively copy collections in the record’s canonical
constructor.

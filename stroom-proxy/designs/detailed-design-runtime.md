# Detailed Design — Runtime & Lifecycle

[← Back to master](detailed-design.md)

## 1. Overview

The runtime and lifecycle layer assembles the pipeline from configuration, creates queue and file-store instances, wires stage processors to production handlers, and manages the thread lifecycle of queue-consuming stages.

```mermaid
graph TD
    PPC["ProxyPipelineConfig"] --> PPA["ProxyPipelineAssembler"]
    PPA --> PPR["ProxyPipelineRuntime"]
    PPA --> PPL["ProxyPipelineLifecycle"]
    PPA --> RF["ReceiverFactory"]
    PPR --> RS["RuntimeStage (per stage)"]
    PPL --> PSR["PipelineStageRunner (per queue-consuming stage)"]
    PSR --> FGQW["FileGroupQueueWorker"]
    FGQW --> FGQ["FileGroupQueue"]
    FGQW --> FGQIP["FileGroupQueueItemProcessor"]
```

---

## 2. ProxyPipelineAssembler

### 2.1 Purpose

Bridges the new reference-message pipeline to existing production handlers. This is the top-level assembly class that wires everything together.

### 2.2 Assembly Sequence

```mermaid
sequenceDiagram
    participant A as ProxyPipelineAssembler
    participant QF as FileGroupQueueFactory
    participant FSF as FileStoreFactory
    participant FSR as FileStoreRegistry
    participant PPR as ProxyPipelineRuntime
    participant PPL as ProxyPipelineLifecycle

    Note over A: 1. Build factories from config
    A->>QF: new FileGroupQueueFactory(config, pathCreator)
    A->>FSF: new FileStoreFactory(config, pathCreator)

    Note over A: 2. Build file store registry
    A->>FSR: FileStoreRegistry.fromFactory(fileStoreFactory)

    Note over A: 3. Wire stage processors
    A->>A: Wire PreAggregateStageProcessor (PreAggregator::addDir)
    A->>A: Wire AggregateStageProcessor (Aggregator::addDir)
    A->>A: Wire ForwardStageProcessor (Forwarder::add)
    A->>A: Wire SplitZipStageProcessor (ZipSplitter::splitZip)

    Note over A: 4. Build runtime
    A->>PPR: ProxyPipelineRuntime.fromConfig(config, queueFactory, fileStoreFactory, processors)

    Note over A: 5. Wire receive stage
    A->>A: Create ReceiveStagePublisher
    A->>A: Set as destination on SimpleReceiver + ZipReceiver

    Note over A: 6. Build lifecycle
    A->>PPL: ProxyPipelineLifecycle.fromRuntime(runtime)
```

### 2.3 Stage Processor Wiring

| Stage | Processor | Production Wiring |
|---|---|---|
| **Pre-Aggregate** | `PreAggregateStageProcessor` | `preAggregateFunction` → `PreAggregator::addDir` |
| **Aggregate** | `AggregateStageProcessor` | `aggregateFunction` → `Aggregator::addDir` |
| **Forward** | `ForwardStageProcessor` | `fileGroupForwarder` → `(msg, dir) → forwarder.add(dir)` |
| **Split Zip** | `SplitZipStageProcessor` | `splitFunction` → `ZipSplitter::splitZip` wrapper |

### 2.4 Destination Callback Wiring

Both `PreAggregator` and `Aggregator` have their `destination` (a `Consumer<Path>`) set to an `AggregateClosePublisher`:

```mermaid
flowchart LR
    PA["PreAggregator"] -->|"destination"| ACP1["AggregateClosePublisher\n→ preAggregateStore\n→ aggregateInput"]
    AGG["Aggregator"] -->|"destination"| ACP2["AggregateClosePublisher\n→ aggregateStore\n→ forwardingInput"]
```

### 2.5 Outputs

| Property | Type | Description |
|---|---|---|
| `receiverFactory` | `ReceiverFactory` | For HTTP ingest — `StoringReceiverFactory(simpleReceiver, zipReceiver)` |
| `lifecycle` | `ProxyPipelineLifecycle` | For starting/stopping queue workers |
| `runtime` | `ProxyPipelineRuntime` | Full runtime model |

---

## 3. ProxyPipelineRuntime

### 3.1 Purpose

Immutable runtime model holding the topology, runtime stages, queues, and file stores.

### 3.2 Class Structure

```mermaid
classDiagram
    class ProxyPipelineRuntime {
        -ProxyPipelineTopology topology
        -Map~PipelineStageName, RuntimeStage~ stages
        -Map~String, FileGroupQueue~ queues
        -Map~String, FileStore~ fileStores
        +fromConfig(config, queueFactory, fileStoreFactory)$ ProxyPipelineRuntime
        +fromTopology(topology, queueFactory, fileStoreFactory)$ ProxyPipelineRuntime
        +getStage(PipelineStageName) Optional~RuntimeStage~
        +getWorker(PipelineStageName) Optional~FileGroupQueueWorker~
        +getQueues() Map
        +getFileStores() Map
        +close()
    }

    class RuntimeStage {
        <<record>>
        +PipelineStageName stageName
        +PipelineStageConfig config
        +FileGroupQueue inputQueue
        +FileGroupQueue outputQueue
        +FileGroupQueue splitZipQueue
        +FileStore fileStore
        +FileGroupQueueWorker worker
    }

    ProxyPipelineRuntime --> RuntimeStage
```

### 3.3 Construction Flow

```mermaid
flowchart TD
    A["fromConfig()"] --> B["Validate config\n(ProxyPipelineConfigValidator)"]
    B --> C["Build topology\n(ProxyPipelineTopology)"]
    C --> D["fromTopology()"]
    D --> E["For each enabled stage:"]
    E --> F["Resolve input queue\n(getOrCreate from factory)"]
    E --> G["Resolve output queue"]
    E --> H["Resolve split-zip queue"]
    E --> I["Resolve file store"]
    E --> J["Create FileGroupQueueWorker\n(if processor supplied)"]
    F & G & H & I & J --> K["Build RuntimeStage"]
    K --> L["ProxyPipelineRuntime"]
```

Queues and file stores are **deduplicated** — if two stages reference the same logical queue/store name, the same instance is shared.

---

## 4. ProxyPipelineLifecycle

### 4.1 Purpose

Manages the start/stop lifecycle of all queue-consuming stage runners.

### 4.2 Start/Stop Sequence

```mermaid
sequenceDiagram
    participant M as ManagedLifecycle
    participant L as ProxyPipelineLifecycle
    participant R1 as Runner (splitZip)
    participant R2 as Runner (preAggregate)
    participant R3 as Runner (aggregate)
    participant R4 as Runner (forward)

    Note over M,R4: Start (upstream → downstream)
    M->>L: start()
    L->>R1: start()
    L->>R2: start()
    L->>R3: start()
    L->>R4: start()

    Note over M,R4: Stop (downstream → upstream)
    M->>L: stop()
    L->>R4: stop(30s)
    L->>R3: stop(30s)
    L->>R2: stop(30s)
    L->>R1: stop(30s)
```

- **Start order**: upstream → downstream
- **Stop order**: downstream → upstream (reverse) — reduces in-flight work by stopping consumers before producers

---

## 5. PipelineStageRunner

### 5.1 Purpose

Manages N consumer threads for a single pipeline stage.

### 5.2 Consumer Loop

```mermaid
stateDiagram-v2
    [*] --> Polling
    Polling --> Processing : item available
    Polling --> Sleeping : no item
    Sleeping --> Polling : 100ms elapsed
    Processing --> Polling : success (loop immediately)
    Processing --> ErrorBackoff : exception
    ErrorBackoff --> Polling : 1s elapsed
    Polling --> [*] : shutdown requested
```

### 5.3 Key Properties

| Property | Default | Description |
|---|---|---|
| `threadCount` | From config | Number of consumer threads |
| `emptyPollBackoff` | 100ms | Sleep duration when queue is empty |
| `errorBackoff` | 1s | Sleep duration after unhandled error |

Threads are daemon threads named `stage-<configName>-<n>`.

---

## 6. FileGroupQueueWorker

### 6.1 Purpose

Centralises the queue processing contract. All stages use the same worker, which provides consistent at-least-once semantics, error handling, structured logging, and metrics.

### 6.2 Processing Flow

```mermaid
flowchart TD
    A["processNext()"] --> B["queue.next()"]
    B -->|empty| C["Return NO_ITEM"]
    B -->|item| D["processor.process(item)"]
    D -->|success| E["item.acknowledge()"]
    E --> F["Return PROCESSED"]
    D -->|exception| G["item.fail(error)"]
    G --> H["Return FAILED"]
    
    style D fill:#fff3cd
    style E fill:#d4edda
    style G fill:#f8d7da
```

### 6.3 Counters (FileGroupQueueWorkerCounters)

Thread-safe counters using `LongAdder`:

| Counter | Incremented When |
|---|---|
| `pollCount` | Every call to `processNext()` |
| `emptyPollCount` | Queue returns empty |
| `itemReceivedCount` | Queue returns an item |
| `itemProcessedCount` | `processor.process()` completes without exception |
| `itemAcknowledgedCount` | `item.acknowledge()` succeeds |
| `itemFailedCount` | `item.fail()` succeeds |
| `processorErrorCount` | `processor.process()` throws |
| `acknowledgeErrorCount` | `item.acknowledge()` throws |
| `failErrorCount` | `item.fail()` throws |
| `closeErrorCount` | `item.close()` throws |

### 6.4 MDC Structured Logging

Before calling `processor.process(item)`, the worker sets the following SLF4J MDC keys:

| MDC Key | Source | Description |
|---|---|---|
| `traceId` | `message.traceId()` | End-to-end correlation ID (only set if non-null) |
| `fileGroupId` | `message.fileGroupId()` | Unique file group identifier |
| `messageId` | `message.messageId()` | Queue message ID |
| `stageName` | `queue.getName()` | Pipeline stage name |

All MDC keys are cleared in a `finally` block after processing completes (success or failure).

### 6.5 Result Types

```mermaid
classDiagram
    class FileGroupQueueWorkerResult {
        <<record>>
        +Outcome outcome
        +String queueName
        +String itemId
        +FileGroupQueueMessage message
        +Throwable error
        +Duration processingDuration
    }

    class Outcome {
        <<enum>>
        NO_ITEM
        PROCESSED
        FAILED
    }

    FileGroupQueueWorkerResult --> Outcome
```

---

## 7. PipelineMonitorProvider

Collects runtime state into an immutable `PipelineMonitorSnapshot` for the admin monitoring endpoint:

```mermaid
classDiagram
    class PipelineMonitorSnapshot {
        <<record>>
        +boolean pipelineEnabled
        +List~StageSnapshot~ stages
        +List~QueueSnapshot~ queues
        +List~FileStoreSnapshot~ fileStores
    }

    class StageSnapshot {
        <<record>>
        +String name
        +boolean hasWorker
        +int threadCount
        +WorkerCounters.Snapshot counters
    }

    class QueueSnapshot {
        <<record>>
        +String name
        +String type
        +boolean healthy
        +String healthDetail
        +Map~String,Long~ depths
        +SqsHeartbeatCounters.Snapshot heartbeatCounters
    }

    class FileStoreSnapshot {
        <<record>>
        +String name
        +boolean healthy
        +String healthDetail
    }

    PipelineMonitorSnapshot --> StageSnapshot
    PipelineMonitorSnapshot --> QueueSnapshot
    PipelineMonitorSnapshot --> FileStoreSnapshot
```

The `buildSnapshot()` method now runs `healthCheck()` on each queue and file store, collects queue depths for `LocalFileGroupQueue` instances, and includes `SqsHeartbeatCounters.Snapshot` for SQS queues.

---

## 8. ProxyPipelineManagedLifecycle

Dropwizard `Managed` adapter that calls `lifecycle.start()` on startup and `lifecycle.stop()` on shutdown, integrating the pipeline with the Dropwizard application lifecycle.

---

## 9. PipelineHealthChecks

Aggregated Dropwizard health check implementing `HasHealthCheck`. Registered via `HasHealthCheckBinder` in `ProxyModule` and exposed on the admin `/healthcheck` endpoint.

### 9.1 Behaviour

- When the pipeline is **disabled**: returns healthy with message "Pipeline not enabled"
- When the pipeline is **enabled**: iterates all queues and file stores from the runtime, calling `healthCheck()` on each
- If **all** components are healthy: returns healthy with a components detail map
- If **any** component is unhealthy: returns unhealthy with message "One or more pipeline components are unhealthy" and component-level details

### 9.2 Dependencies

Uses `Provider<ProxyPipelineAssembler>` to lazily access the runtime (queues/stores are created dynamically at assembly time, not at injection time).

---

## 10. PipelineMetricsRegistrar

Registers Codahale gauges for the pipeline runtime. Called from `ProxyCoreModule` immediately after assembler construction. Gauges are bridged to Prometheus format by the existing `PrometheusModule`.

### 10.1 Registered Metrics

| Category | Metrics | Source |
|---|---|---|
| Per-stage items | `items.received`, `items.processed`, `items.acknowledged`, `items.failed` | `FileGroupQueueWorkerCounters` |
| Per-stage errors | `errors.processor`, `errors.acknowledge`, `errors.fail`, `errors.close` | `FileGroupQueueWorkerCounters` |
| Per-stage polls | `polls.total`, `polls.empty` | `FileGroupQueueWorkerCounters` |
| Per-queue depth | `pending`, `inflight`, `failed` | `LocalFileGroupQueue` only |
| Per-queue heartbeat | `heartbeat.attempts`, `heartbeat.successes`, `heartbeat.failures` | `SqsHeartbeatCounters` |

All metrics are prefixed with `stroom.proxy.pipeline.`. Stage metrics include the stage config name; queue metrics include the logical queue name.

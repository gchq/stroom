# Stroom Proxy Pipeline — Detailed Design

## 1. Introduction

This document provides a comprehensive technical reference for the Stroom Proxy pipeline architecture. The pipeline is a staged data-processing system that receives, splits, aggregates, and forwards file groups to downstream Stroom instances. Data flows through a sequence of independent stages connected by pluggable queues, with durable file stores providing persistence between stages.

The design prioritises **zero data loss**, **pluggable queue backends**, and **flexible deployment topologies** — from a single-process proxy to a fully distributed cluster.

## 2. High-Level Architecture

```mermaid
graph LR
    HTTP["HTTP POST"] --> R["Receive Stage"]
    R -->|splitZipInput| SZ["Split Zip Stage"]
    R -->|preAggregateInput| PA["Pre-Aggregate Stage"]
    SZ -->|preAggregateInput| PA
    PA -->|aggregateInput| A["Aggregate Stage"]
    A -->|forwardingInput| F["Forward Stage"]
    F --> DS["Downstream Stroom"]

    style R fill:#4a9eff,color:#fff
    style SZ fill:#ff9f43,color:#fff
    style PA fill:#ee5a24,color:#fff
    style A fill:#b71540,color:#fff
    style F fill:#6ab04c,color:#fff
```

### Data Flow Summary

```
HTTP ──► Receive ──► [splitZipInput] ──► SplitZip ──► [preAggregateInput] ──► PreAggregate ──► [aggregateInput] ──► Aggregate ──► [forwardingInput] ──► Forward ──► Downstream
                 └──────────────────────────────────► [preAggregateInput] ──► (single-feed bypass)
```

Each stage:

1. Reads a lightweight **reference message** from its input queue
2. Resolves the referenced file group from a **file store**
3. Performs its stage-specific processing
4. Writes output to its output **file store**
5. Publishes a reference message to its output **queue**
6. Deletes consumed input from the input file store
7. Acknowledges the input queue message

## 3. Core Design Principles

### 3.1 Reference Messages, Not Data Messages

Queue messages are lightweight JSON references (~500 bytes). Actual data lives in file stores. This decouples queue sizing from data volume and allows different queue backends without message-size constraints.

### 3.2 Ownership-Transfer Contract

Every stage follows a strict ordering: **write output → publish message → delete input → acknowledge**. This ensures at-least-once delivery with no data loss. See [§5 Ownership Transfer](#5-ownership-transfer-protocol) for details.

### 3.3 Stage Independence

Each stage only knows its input queue, output queue, and file store. All stages are enabled by default. For distributed deployments, individual stages can be disabled so that each process runs only the stages it is responsible for. Stages can be run in separate processes, scaled independently, and use different queue/store backends.

### 3.4 Idempotent Writes

File stores support deterministic write paths (`newDeterministicWrite(id)`) so reprocessing produces the same output path. Combined with `isComplete()` checks, duplicate processing is safe.

## 4. Package Structure

All pipeline classes reside in `stroom.proxy.app.pipeline`, organised into sub-packages by concern:

```mermaid
graph TD
    subgraph "stage/ (common infra)"
        FGQW["FileGroupQueueWorker"]
        FGQIP["FileGroupQueueItemProcessor"]
        FGQWC["FileGroupQueueWorkerCounters"]
        PSR["PipelineStageRunner"]
    end

    subgraph "stage.receive/"
        RSC["ReceiveStageConfig"]
        RSTC["ReceiveStageThreadsConfig"]
        RSP["ReceiveStagePublisher"]
    end

    subgraph "stage.splitzip/"
        SZSC["SplitZipStageConfig"]
        SZSP["SplitZipStageProcessor"]
    end

    subgraph "stage.preaggregate/"
        PASC["PreAggregateStageConfig"]
        PASTC["PreAggregateStageThreadsConfig"]
        PASP["PreAggregateStageProcessor"]
    end

    subgraph "stage.aggregate/"
        ASC["AggregateStageConfig"]
        ASP["AggregateStageProcessor"]
        ACP["AggregateClosePublisher"]
    end

    subgraph "stage.forward/"
        FSC["ForwardStageConfig"]
        FSP["ForwardStageProcessor"]
        FSFOF["ForwardStageFanOutForwarder"]
    end

    subgraph "config/"
        PPC["ProxyPipelineConfig"]
        PSC["PipelineStagesConfig"]
        CSTC["ConsumerStageThreadsConfig"]
        PPCV["ProxyPipelineConfigValidator"]
    end

    subgraph "queue/"
        FGQ["FileGroupQueue"]
        FGQM["FileGroupQueueMessage"]
        FGQI["FileGroupQueueItem"]
    end

    subgraph "queue.local/"
        LFGQ["LocalFileGroupQueue"]
    end

    subgraph "queue.sqs/"
        SQSFGQ["SqsFileGroupQueue"]
    end

    subgraph "queue.kafka/"
        KFGQ["KafkaFileGroupQueue"]
    end

    subgraph "store/"
        FS["FileStore"]
        FSW["FileStoreWrite"]
        FSL["FileStoreLocation"]
        FSR["FileStoreRegistry"]
    end

    subgraph "store.local/"
        LFS["LocalFileStore"]
    end

    subgraph "store.s3/"
        S3FS["S3FileStore"]
    end

    subgraph "runtime/"
        PPR["ProxyPipelineRuntime"]
        PPA["ProxyPipelineAssembler"]
        PPL["ProxyPipelineLifecycle"]
    end

    subgraph "monitor/"
        PHC["PipelineHealthChecks"]
        PMR["PipelineMetricsRegistrar"]
    end

    FGQW --> FGQ
    FGQW --> FGQIP
    RSP --> FS
    RSP --> FGQ
    SZSP --> FSR
    PASP --> FSR
    ASP --> FSR
    FSP --> FSR
    PPL --> PSR
    PSR --> FGQW
    PSC --> RSC
    PSC --> SZSC
    PSC --> PASC
    PSC --> ASC
    PSC --> FSC
    PASTC -.-> CSTC
```

## 5. Ownership-Transfer Protocol

```mermaid
sequenceDiagram
    participant W as Worker
    participant P as Processor
    participant IQ as Input Queue
    participant IS as Input Store
    participant OS as Output Store
    participant OQ as Output Queue

    W->>IQ: next()
    IQ-->>W: FileGroupQueueItem
    W->>P: process(item)
    P->>IS: resolve(location)
    IS-->>P: Path (source dir)
    P->>P: Stage-specific work
    P->>OS: newWrite() → write → commit()
    OS-->>P: FileStoreLocation
    P->>OQ: publish(message)
    P->>IS: delete(inputLocation)
    P-->>W: return
    W->>IQ: item.acknowledge()
```

### Crash Recovery Scenarios

| Crash Point | Recovery Behaviour |
|---|---|
| Before output commit | Input still in queue, redelivered, reprocessed |
| After commit, before publish | Input redelivered; deterministic writes detect existing output |
| After publish, before input delete | Input redelivered; output already exists (idempotent) |
| After delete, before ack | Input redelivered but source gone; fails to dead-letter |

## 6. Detailed Stage Documents

Each stage has its own detailed design document with class diagrams, sequence diagrams, and field-level descriptions:

| Stage | Document |
|---|---|
| **Receive** | [detailed-design-receive.md](detailed-design-receive.md) |
| **Split Zip** | [detailed-design-split-zip.md](detailed-design-split-zip.md) |
| **Pre-Aggregate** | [detailed-design-pre-aggregate.md](detailed-design-pre-aggregate.md) |
| **Aggregate** | [detailed-design-aggregate.md](detailed-design-aggregate.md) |
| **Forward** | [detailed-design-forward.md](detailed-design-forward.md) |

## 7. Infrastructure Documents

| Component | Document |
|---|---|
| **Queues** (Local, SQS, Kafka) | [detailed-design-queues.md](detailed-design-queues.md) |
| **File Stores** (Local, S3) | [detailed-design-file-stores.md](detailed-design-file-stores.md) |
| **Runtime & Lifecycle** | [detailed-design-runtime.md](detailed-design-runtime.md) |

## 8. Key Data Structures

### 8.1 FileGroupQueueMessage (Record)

The universal reference message carried by all queue implementations:

```java
public record FileGroupQueueMessage(
    int schemaVersion,          // Always 1
    String messageId,           // UUID
    String queueName,           // Logical queue name
    String fileGroupId,         // Logical file group identifier
    FileStoreLocation fileStoreLocation,  // Where the data lives
    String producingStage,      // Which stage produced this
    String producerId,          // Which node produced this
    Instant createdTime,        // Creation timestamp
    String traceId,             // Optional correlation ID
    Map<String, String> attributes  // Optional metadata
)
```

### 8.2 FileStoreLocation (Record)

A stable URI-based reference to data in a named file store:

```java
public record FileStoreLocation(
    String storeName,           // Logical store name (e.g. "receiveStore")
    LocationType locationType,  // LOCAL_FILESYSTEM or S3
    String uri,                 // file:///... or s3://bucket/key
    Map<String, String> attributes
)
```

### 8.3 FileGroupQueueItem (Interface)

A leased item from a queue with acknowledgement semantics:

| Method | Purpose |
|---|---|
| `getId()` | Queue-specific lease identifier |
| `getMessage()` | The `FileGroupQueueMessage` |
| `getMetadata()` | Queue-implementation diagnostics |
| `acknowledge()` | Confirm successful processing |
| `fail(Throwable)` | Return to queue for retry |
| `close()` | Release local resources |

## 9. Configuration Model

Each stage has its own typed configuration class with only the fields relevant to that stage.

```mermaid
classDiagram
    class ProxyPipelineConfig {
        +Map~String,QueueDefinition~ queues
        +Map~String,FileStoreDefinition~ fileStores
        +PipelineStagesConfig stages
    }

    class PipelineStagesConfig {
        +ReceiveStageConfig receive
        +SplitZipStageConfig splitZip
        +PreAggregateStageConfig preAggregate
        +AggregateStageConfig aggregate
        +ForwardStageConfig forward
    }

    class ReceiveStageConfig {
        +boolean enabled
        +String outputQueue
        +String splitZipQueue
        +String fileStore
        +ReceiveStageThreadsConfig threads
    }

    class SplitZipStageConfig {
        +boolean enabled
        +String inputQueue
        +String outputQueue
        +String fileStore
        +ConsumerStageThreadsConfig threads
    }

    class PreAggregateStageConfig {
        +boolean enabled
        +String inputQueue
        +String outputQueue
        +String fileStore
        +PreAggregateStageThreadsConfig threads
    }

    class AggregateStageConfig {
        +boolean enabled
        +String inputQueue
        +String outputQueue
        +String fileStore
        +ConsumerStageThreadsConfig threads
    }

    class ForwardStageConfig {
        +boolean enabled
        +String inputQueue
        +ConsumerStageThreadsConfig threads
    }

    class ConsumerStageThreadsConfig {
        +int consumerThreads = 1
    }

    class ReceiveStageThreadsConfig {
        +int maxConcurrentReceives = 7
    }

    class PreAggregateStageThreadsConfig {
        +int consumerThreads = 1
        +int closeOldAggregatesThreads = 1
    }

    ProxyPipelineConfig --> PipelineStagesConfig
    PipelineStagesConfig --> ReceiveStageConfig
    PipelineStagesConfig --> SplitZipStageConfig
    PipelineStagesConfig --> PreAggregateStageConfig
    PipelineStagesConfig --> AggregateStageConfig
    PipelineStagesConfig --> ForwardStageConfig
    ReceiveStageConfig --> ReceiveStageThreadsConfig
    SplitZipStageConfig --> ConsumerStageThreadsConfig
    PreAggregateStageConfig --> PreAggregateStageThreadsConfig
    AggregateStageConfig --> ConsumerStageThreadsConfig
    ForwardStageConfig --> ConsumerStageThreadsConfig
    PreAggregateStageThreadsConfig --|> ConsumerStageThreadsConfig
```

### Stage-Specific Thread Config

| Stage | Config Class | Fields |
|-------|-------------|--------|
| Receive | `ReceiveStageThreadsConfig` | `maxConcurrentReceives` (default: 7) |
| Split-Zip | `ConsumerStageThreadsConfig` | `consumerThreads` (default: 1) |
| Pre-Aggregate | `PreAggregateStageThreadsConfig` | `consumerThreads` (default: 1), `closeOldAggregatesThreads` (default: 1) |
| Aggregate | `ConsumerStageThreadsConfig` | `consumerThreads` (default: 1) |
| Forward | `ConsumerStageThreadsConfig` | `consumerThreads` (default: 1) |

## 10. Thread Model

```mermaid
graph TD
    subgraph "Per-Stage Thread Pool"
        PSR["PipelineStageRunner"] --> T1["stage-splitZip-1"]
        PSR --> T2["stage-splitZip-2"]
        PSR --> TN["stage-splitZip-N"]
    end

    subgraph "Worker Loop (each thread)"
        T1 --> POLL["queue.next()"]
        POLL -->|item| PROC["processor.process(item)"]
        PROC --> ACK["item.acknowledge()"]
        ACK --> POLL
        POLL -->|empty| SLEEP["sleep(100ms)"]
        SLEEP --> POLL
        PROC -->|exception| FAIL["item.fail(error)"]
        FAIL --> POLL
    end
```

Each `PipelineStageRunner` manages N daemon threads named `stage-<configName>-<n>`. Threads poll in a loop with 100ms empty-poll backoff (local queues) or long-poll blocking (SQS/Kafka). Errors trigger a 1-second backoff before retrying.

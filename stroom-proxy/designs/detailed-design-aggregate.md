# Detailed Design — Aggregate Stage

[← Back to master](detailed-design.md)

## 1. Purpose

The aggregate stage merges multiple per-feed file-group subdirectories (produced by pre-aggregation) into a single output zip with combined metadata headers. The merged result is published to the forwarding input queue.

Like the pre-aggregate stage, this stage uses a stateful `Aggregator` with a close callback.

## 2. Class Diagram

```mermaid
classDiagram
    class AggregateStageProcessor {
        -FileStoreRegistry fileStoreRegistry
        -AggregateFunction aggregateFunction
        +process(FileGroupQueueItem)
    }

    class FileGroupQueueItemProcessor {
        <<interface>>
        +process(FileGroupQueueItem)
    }

    class AggregateFunction {
        <<functional interface>>
        +addDir(Path sourceDir)
    }

    class AggregateClosePublisher {
        -FileStore outputStore
        -FileGroupQueue outputQueue
        -PipelineStageName stageName
        -String sourceNodeId
        +accept(Path aggregateDir)
    }

    class Aggregator {
        +addDir(Path sourceDir)
        +setDestination(Consumer~Path~)
    }

    AggregateStageProcessor ..|> FileGroupQueueItemProcessor
    AggregateStageProcessor --> AggregateFunction
    AggregateStageProcessor --> FileStoreRegistry
    Aggregator ..|> AggregateFunction : "::addDir"
    Aggregator --> AggregateClosePublisher : destination
    AggregateClosePublisher --> FileStore : aggregateStore
    AggregateClosePublisher --> FileGroupQueue : forwardingInput
```

## 3. Constructor Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `fileStoreRegistry` | `FileStoreRegistry` | Yes | Resolves input message locations |
| `aggregateFunction` | `AggregateFunction` | Yes | Merge logic (wraps `Aggregator::addDir`) |

## 4. Processing Sequence

```mermaid
sequenceDiagram
    participant W as Worker
    participant ASP as AggregateStageProcessor
    participant FSR as FileStoreRegistry
    participant AGG as Aggregator
    participant ACP as AggregateClosePublisher
    participant OS as aggregateStore
    participant OQ as forwardingInput
    participant IS as Input FileStore

    W->>ASP: process(item)
    ASP->>FSR: resolve(message)
    FSR-->>ASP: sourceDir
    ASP->>AGG: addDir(sourceDir)
    
    Note over AGG: Counts children.<br/>Single-child: pass-through.<br/>Multi-child: merge zips.

    opt Merge complete
        AGG->>ACP: accept(mergedDir)
        ACP->>OS: newWrite() → copy → commit()
        OS-->>ACP: FileStoreLocation
        ACP->>OQ: publish(message)
        ACP->>ACP: deleteRecursively(mergedDir)
    end

    ASP->>IS: delete(inputLocation)
    ASP-->>W: return
```

### Aggregator Internal Logic

The `Aggregator` handles three cases:

1. **Single child** — If the pre-aggregate directory contains only one subdirectory, the data passes through without modification (no zip merge needed).

2. **Multi-child** — Multiple per-feed subdirectories are merged:
   - Zip files from each child are combined into a single output zip
   - Common metadata headers are combined
   - The merged result is written to the destination callback

3. **Empty/Invalid** — Logs a warning and skips.

## 5. Relationship to Pre-Aggregate

```mermaid
flowchart LR
    subgraph "Pre-Aggregate Output"
        PA["preAggregateStore/\n├── 001/ (feed X, batch 1)\n├── 002/ (feed X, batch 2)\n└── 003/ (feed X, batch 3)"]
    end
    
    subgraph "Aggregate Output"
        A["aggregateStore/\n└── merged/\n    ├── proxy.meta\n    ├── proxy.zip (combined)\n    └── proxy.entries"]
    end

    PA -->|"Aggregator merges"| A
```

## 6. Acknowledgement Contract

Same pattern as all processors — `FileGroupQueueWorker` owns acknowledgement.

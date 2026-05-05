# Pluggable Queue Pipeline Architecture

## Overview

The Stroom Proxy pluggable queue pipeline is the exclusive data flow
architecture. Data flows through independently executable stages connected
by named queues. Each stage writes its output to a named file store and
publishes a reference message to the next stage's input queue.

```
HTTP POST
    │
    ▼
┌──────────┐    ┌───────────┐    ┌────────────────┐    ┌─────────────┐    ┌───────────┐
│ Receive  │───▶│ Split-Zip │───▶│ Pre-Aggregate  │───▶│  Aggregate  │───▶│  Forward  │
│  Stage   │    │   Stage   │    │     Stage       │    │    Stage    │    │   Stage   │
└──────────┘    └───────────┘    └────────────────┘    └─────────────┘    └───────────┘
     │               │                  │                    │                 │
     ▼               ▼                  ▼                    ▼                 ▼
 receiveStore    splitStore      preAggregateStore     aggregateStore     (forwarded)
```

### Key Design Principles

- **Reference messages**: Queues carry lightweight references (`FileStoreLocation`)
  to data already durably written to a file store. Data is never moved through
  the queue itself.
- **At-least-once delivery**: All queue types are treated as at-least-once.
  Stage processors should be idempotent where practical.
- **Independent stages**: Each stage can be enabled/disabled, scaled
  independently (via per-stage `threads` config), and backed by different queue types.
- **Always active**: The pipeline is unconditionally wired at startup — no
  `enabled` toggle. Default configuration wires all 5 stages with local
  filesystem queues and stores.

---

## Stages

### Receive

- **Type**: HTTP-driven (no queue worker)
- **Input**: HTTP POST via `SimpleReceiver` / `ZipReceiver`
- **Output**: Writes to `receiveStore`, publishes to `splitZipInput` queue
- **Component**: `ReceiveStagePublisher`

### Split-Zip

- **Type**: Queue-consuming
- **Input**: `splitZipInput` queue
- **Output**: Writes per-feed splits to `splitStore`, publishes to `preAggregateInput`
- **Component**: `SplitZipStageProcessor` + `ZipSplitter.splitZip()` (static utility)

### Pre-Aggregate

- **Type**: Queue-consuming
- **Input**: `preAggregateInput` queue
- **Output**: `PreAggregator` collects file groups; on closure writes to
  `preAggregateStore` and publishes to `aggregateInput`
- **Component**: `PreAggregateStageProcessor` + `AggregateClosePublisher`

### Aggregate

- **Type**: Queue-consuming
- **Input**: `aggregateInput` queue
- **Output**: `Aggregator` merges pre-aggregates; on closure writes to
  `aggregateStore` and publishes to `forwardingInput`
- **Component**: `AggregateStageProcessor` + `AggregateClosePublisher`

### Forward

- **Type**: Queue-consuming
- **Input**: `forwardingInput` queue
- **Output**: Delegates to `Forwarder.add()` for HTTP/file forwarding
- **Component**: `ForwardStageProcessor`
- **Multi-destination**: `ForwardStageFanOutForwarder` copies to per-destination
  file stores and queues

---

## Queue Types

| Type | Class | Use Case |
|------|-------|----------|
| `LOCAL_FILESYSTEM` | `LocalFileGroupQueue` | Default. Filesystem-backed with atomic writes. |
| `SQS` | `SqsFileGroupQueue` | AWS SQS for distributed deployments. |

All queue types implement `FileGroupQueue` and carry `FileGroupQueueMessage`
payloads serialised via `FileGroupQueueMessageCodec`.

---

## File Stores

| Store | Purpose |
|-------|---------|
| `receiveStore` | Incoming HTTP data before splitting |
| `splitStore` | Per-feed split output |
| `preAggregateStore` | Closed pre-aggregate batches |
| `aggregateStore` | Merged aggregate output ready for forwarding |

All stores implement `FileStore` with:
- `newWrite()` / `newDeterministicWrite(id)` for atomic writes
- `resolve(location)` to get a local `Path`
- `delete(location)` for ownership transfer cleanup
- `isComplete(location)` for idempotency checks

---

## Configuration

### Minimal (full pipeline with defaults)

```yaml
pipeline: {}
```

With no `stages` block, all 5 stages are auto-wired with standard
queue/store names. See `proxy-pipeline.yml` for the full reference config.

### Custom thread counts

```yaml
pipeline:
  stages:
    forward:
      enabled: true
      inputQueue: forwardingInput
      threads:
        consumerThreads: 4
```

### SQS queue backend

```yaml
pipeline:
  queues:
    splitZipInput:
      type: SQS
      sqsQueueUrl: https://sqs.eu-west-1.amazonaws.com/123456789/split-zip
```

---

## Lifecycle

The pipeline lifecycle is managed by `ProxyPipelineManagedLifecycle`, a
Dropwizard `Managed` component that:

1. On `start()`: creates `ProxyPipelineAssembler` (lazy), starts
   `PipelineStageRunner` threads for each queue-consuming stage
2. On `stop()`: stops runners in reverse order (forward → aggregate →
   pre-aggregate → split-zip) to drain in-flight work

Stage runners use `PipelineStageRunner` with configurable:
- `emptyPollBackoff` (default 100ms)
- `errorBackoff` (default 1s)
- Thread naming: `stage-<name>-<n>`

---

## Monitoring

When the pipeline is active, the `/queues` admin endpoint shows:

- **Pipeline Stages**: runner status, thread count, poll/process/ack/fail counters
- **Pipeline Queues**: queue type per named queue
- **Pipeline File Stores**: store names

Counter data comes from `FileGroupQueueWorkerCounters` snapshots exposed via
`PipelineMonitorProvider`.

---

## Guice Integration

| Binding | Source |
|---------|--------|
| `ReceiverFactory` | `ProxyCoreModule.provideReceiverFactory()` — always from `ProxyPipelineAssembler` |
| `ProxyPipelineAssembler` | `ProxyCoreModule.provideProxyPipelineAssembler()` — `@Singleton @Provides` |
| `ProxyPipelineManagedLifecycle` | `ProxyModule` — `Managed` multibinder |

---

## Idempotency

- `LocalFileStore.newDeterministicWrite(fileGroupId)` produces the same output
  path for the same ID, enabling skip-on-replay
- `isComplete(location)` checks for a `.committed` marker before re-processing
- At-least-once queue delivery + idempotent processing = safe replay

---

## ZipSplitter

`ZipSplitter` is a static utility class providing `splitZip()` and
`splitZipByFeed()` methods. It is used by `SplitZipStageProcessor` within the
pipeline. `ZipReceiver` no longer depends on `ZipSplitter` — multi-feed zips
are routed to the split-zip queue via `ReceiveStagePublisher`.

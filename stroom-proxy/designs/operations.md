# Stroom Proxy — Operations Guide

Configuring, deploying and monitoring the proxy pipeline. For the design behind
it see [architecture.md](architecture.md); for the end-to-end data path and
on-disk layout see [data-path.md](data-path.md).

## Use Cases

The proxy has always been shaped around a handful of deployment intentions.
Under the pipeline architecture these are no longer distinct code paths — they
are all the same five stages with different stages enabled and different
forwarding destinations configured.

| Use case | What it does | How it is configured |
|---|---|---|
| **Repeater (forward only)** | Receives and immediately repeats to a single destination without storing or processing. Forwarding errors go back to the sender. Minimal validation — enough to confirm the headers carry a feed name, which can be checked against a receipt policy. | Disable `splitZip`, `preAggregate` and `aggregate`; set `queueAndRetryEnabled: false` on the destination so failures surface to the sender rather than being queued |
| **Receive + store** | Receives and stores to disk for some other process to collect. | Disable `forward` |
| **Receive + store + forward** | Acknowledges receipt once stored, then forwards to one or more destinations. Forward errors are logged and retried, not returned to the sender. | All stages except `preAggregate`/`aggregate` |
| **Receive + store + aggregate + forward** | Also packs many small streams for the same feed into one archive before forwarding. Fewer connections and much less bandwidth, since log data compresses well. | The default — all five stages enabled |
| **Scan + forward** | Picks up data written to a directory by another process — typically a remote receive-only proxy — and forwards it. | Enable `dirScanner`; disable `preAggregate`/`aggregate` |
| **Scan + aggregate + forward** | As above, with aggregation. | Enable `dirScanner`; all stages enabled |

The stage-disabling mechanism is the same one used to distribute a pipeline
across processes — see
[deployments/split-stage-workers.yml](deployments/split-stage-workers.yml).
Scanning is an entry point rather than a stage; see
[infrastructure/entry-points.md](infrastructure/entry-points.md).

## Overview

The Stroom Proxy pipeline is a staged data-processing architecture that receives, splits, aggregates, and forwards file groups to downstream Stroom instances. Data flows through a sequence of independent stages connected by queues. Each stage reads from an input queue, processes data, writes its output to a durable file store, publishes a reference message to the next stage's input queue, deletes its consumed input, and acknowledges the input message.

This architecture is designed for **zero data loss**, **pluggable queue backends**, and **flexible deployment** — from a single-process proxy handling everything, to a distributed cluster where each stage runs as a separate process.

---

## Pipeline Stages

Data flows through up to five stages in sequence:

```
                                    ┌──────────────┐
                                    │   SplitZip   │
                                    │    Stage     │
                                    └──────┬───────┘
                                           │
HTTP ──► Receive ──► splitZipInput ────────┘
         Stage        (if multi-feed)       
           │                                
           └──► preAggregateInput ──► PreAggregate ──► aggregateInput ──► Aggregate ──► forwardingInput ──► Forward ──► Downstream
                (if single-feed)       Stage                              Stage                              Stage      Stroom
```

### Stage 1: Receive

**Purpose**: Accept incoming data via HTTP POST and introduce it into the pipeline.

**What it does**:
1. Accepts a temporary directory of received files (proxy.meta, proxy.zip, proxy.entries) from the HTTP receive handler
2. Copies the files into the **receive file store** (`receiveStore`)
3. Inspects `proxy.entries` to determine if the file group contains data from multiple feeds
4. If **multiple feeds** and a split-zip queue is configured → publishes a reference message to `splitZipInput`
5. If **single feed** or no split-zip queue configured → publishes a reference message to `preAggregateInput`
6. Deletes the temporary receive directory

**Class**: `ReceiveStagePublisher` (implements `Consumer<Path>`)

**Input**: Temporary directory from HTTP handler  
**Output file store**: `receiveStore`  
**Output queue**: `preAggregateInput` or `splitZipInput`

### Stage 2: Split Zip (Optional)

**Purpose**: Split multi-feed zip files into one file group per feed so that downstream aggregation is per-feed.

**What it does**:
1. Reads a reference message from `splitZipInput`
2. Resolves the source file group from the input file store
3. Splits the multi-feed zip into separate per-feed file groups
4. Writes each split output to the **split file store** (`splitStore`)
5. Publishes a reference message per split to `preAggregateInput`
6. **Deletes the consumed input** from the source file store
7. Worker acknowledges the input message

**Class**: `SplitZipStageProcessor`

**Input queue**: `splitZipInput`  
**Output file store**: `splitStore`  
**Output queue**: `preAggregateInput`

### Stage 3: Pre-Aggregate

**Purpose**: Accumulate incoming file groups into open aggregates keyed by feed, closing aggregates when size/count thresholds or age limits are reached.

**What it does**:
1. Reads a reference message from `preAggregateInput`
2. Resolves the source file group from the input file store
3. Delegates to the `PreAggregator`, which absorbs the data into its internal aggregate state
4. When an aggregate is closed (threshold reached), the close callback writes the aggregate to `preAggregateStore` and publishes a reference message to `aggregateInput`
5. **Deletes the consumed input** from the source file store
6. Worker acknowledges the input message

**Class**: `PreAggregateStageProcessor`

**Input queue**: `preAggregateInput`  
**Output file store**: `preAggregateStore`  
**Output queue**: `aggregateInput`

### Stage 4: Aggregate

**Purpose**: Merge multiple per-feed file-group subdirectories into a single output zip with combined metadata headers.

**What it does**:
1. Reads a reference message from `aggregateInput`
2. Resolves the pre-aggregated source directory from the input file store
3. Delegates to the `Aggregator`, which merges zip files and combines headers
4. The close callback writes the merged result to `aggregateStore` and publishes a reference message to `forwardingInput`
5. **Deletes the consumed input** from the source file store
6. Worker acknowledges the input message

**Class**: `AggregateStageProcessor`

**Input queue**: `aggregateInput`  
**Output file store**: `aggregateStore`  
**Output queue**: `forwardingInput`

### Stage 5: Forward

**Purpose**: Send fully aggregated data to one or more downstream Stroom instances.

**What it does**:
1. Reads a reference message from `forwardingInput`
2. Resolves the source file group from the input file store
3. Validates that proxy.meta, proxy.zip, and proxy.entries are all present
4. Delegates to the configured forwarder:
   - **Single destination**: sends data directly to one downstream Stroom
   - **Fan-out (multi-destination)**: copies the file group into each destination's file store, publishes a reference message to each destination's queue
5. **Deletes the consumed input** from the source file store
6. Worker acknowledges the input message

**Class**: `ForwardStageProcessor` with pluggable `FileGroupForwarder` (e.g. `ForwardStageFanOutForwarder`)

**Input queue**: `forwardingInput`  
**Output**: Downstream Stroom instances (terminal stage — no output queue)

---

## Data Durability & Protection Against Data Loss

The pipeline is designed so that **data is never lost, even if a process crashes mid-operation**. This is achieved through a strict ownership-transfer protocol.

### The Ownership-Transfer Contract

Every stage follows this exact sequence:

```
1. Resolve input      ── Read the reference message, resolve to a file group in the input store
2. Process            ── Do the stage's work (split, aggregate, forward, etc.)
3. Write output       ── Write result to the OUTPUT file store (durable commit)
4. Publish message    ── Publish a reference message to the OUTPUT queue
5. Delete input       ── Delete the consumed file group from the INPUT file store
6. Acknowledge        ── Ack the INPUT queue message (worker responsibility)
```

### Why This Order Matters

The critical insight is that **the input is only deleted after the output is durably committed and published**:

- **Steps 3–4 happen before step 5**: The output data exists in the output file store and a message has been published to the output queue *before* the input is deleted. If the process crashes between steps 4 and 5, the input still exists and the input message has not been acknowledged — so the item will be redelivered and reprocessed (at-least-once semantics).

- **Step 5 happens before step 6**: The input data is deleted before the input message is acknowledged. If the process crashes between steps 5 and 6, the input message will be redelivered, but the input data is gone — the processor will fail to resolve the input and the item will be sent to the error/dead-letter path. This is a trade-off: the data has already been safely written to the output store, so no data is lost.

- **If processing fails** (exception at step 2): The worker catches the exception, calls `item.fail()` instead of `item.acknowledge()`, and the item is returned to the queue for retry. The input data is untouched.

### Fan-Out Durability

For the forward stage with multiple destinations, the fan-out forwarder ensures durability by copying to **all destinations before any input is deleted**:

```
For each destination:
    1. Copy file group to destination's file store (durable write + commit)
    2. Publish reference message to destination's queue
Then:
    3. Delete input from source file store (ForwardStageProcessor)
    4. Acknowledge input message (Worker)
```

If a crash occurs after some destinations have been written but before all are complete, the input message will be redelivered. Some destinations may receive duplicate data (at-least-once), but no destination will miss data.

### Reference Messages, Not Data Messages

Queue messages are lightweight **references**, not data payloads. A `FileGroupQueueMessage` contains:

| Field | Purpose |
|-------|---------|
| `messageId` | Unique message identifier (UUID) |
| `queueName` | The logical queue this message belongs to |
| `fileGroupId` | Logical identifier for the file group |
| `fileStoreLocation` | Reference to where the data lives (store name + path/URI) |
| `producingStage` | Which stage produced this message |
| `producerId` | Which node produced this message |
| `createdTime` | When the message was created |
| `traceId` | Optional correlation ID for tracing |
| `attributes` | Optional key-value metadata |

The actual data (proxy.meta, proxy.zip, proxy.entries) lives in the file store. The queue only carries the reference. This means:

- Queue messages are small (~500 bytes) regardless of data size
- Data is written once and referenced, not copied through queues
- Different queue backends (local filesystem, SQS, Kafka) only need to handle small JSON messages

---

## Queue Types

The pipeline supports three queue backends. All share the same `FileGroupQueue` interface and `FileGroupQueueMessage` format.

### LOCAL_FILESYSTEM (Default)

**Best for**: Single-process deployments, development, testing.

Messages are stored as individual numbered JSON files in a filesystem directory. Items are claimed and acknowledged by moving the file between `pending/`, `in-flight/` and `failed/` subdirectories.

```yaml
queues:
  preAggregateInput:
    type: LOCAL_FILESYSTEM
    path: /data/proxy/queues/preAggregateInput  # Optional — derived if omitted
```

**Characteristics**:
- No external dependencies
- Roughly FIFO — `pending/` is drained in filename (sequence) order
- **Multi-threaded within one process, single-process only.** Competing consumer
  threads are safe: a claim is an atomic `pending/` → `in-flight/` move and the
  loser of a race retries. Two *processes* sharing one queue directory are not
  safe, because startup recovery returns every in-flight item to `pending/`.
- `next()` is non-blocking — returns `Optional.empty()` immediately if no messages
- Worker uses a 100ms empty-poll backoff to avoid busy-waiting
- Nothing drains `failed/`; monitor it (see
  [§Monitoring](#monitoring--observability))

### SQS (AWS Simple Queue Service)

**Best for**: Distributed AWS deployments, multi-process stages, auto-scaling.

Messages are sent to an AWS SQS queue. The queue uses SQS visibility timeout as the lease mechanism: when a message is received, it becomes invisible to other consumers for the configured timeout period.

```yaml
queues:
  forwardingInput:
    type: SQS
    queueUrl: https://sqs.eu-west-2.amazonaws.com/123456789/stroom-proxy-forwarding
    visibilityTimeout: PT30M    # 30 minutes
    waitTime: PT20S             # SQS long-poll (max 20s)
```

**Characteristics**:
- Multiple consumers can compete for messages across processes
- At-least-once delivery (messages may be redelivered if not acknowledged)
- **Visibility heartbeat**: a per-queue scheduler thread (`sqs-heartbeat-<queueName>`) re-issues `changeMessageVisibility` for each in-flight item every `2/3 × visibilityTimeout` (minimum 1 s), so processing that outlasts the timeout is not redelivered under itself. Heartbeat attempts, successes and failures are exported as metrics — a rising failure rate means items are at risk of duplicate processing.
- `next()` uses SQS long-polling (blocks up to `waitTime` waiting for a message)
- `acknowledge()` deletes the SQS message
- `fail()` sets visibility timeout to 0, making the message immediately available for retry

**Configuration fields**:

| Field | Required | Description |
|-------|----------|-------------|
| `queueUrl` | Yes | Full SQS queue URL |
| `visibilityTimeout` | No | Duration before unacknowledged messages reappear (default: 30 min) |
| `waitTime` | No | Long-poll wait time (default: 20s, SQS maximum) |

### KAFKA (Apache Kafka)

**Best for**: High-throughput distributed deployments, existing Kafka infrastructure.

Messages are published to a Kafka topic as key-value records. The key is the `fileGroupId` and the value is the JSON-serialised message.

```yaml
queues:
  aggregateInput:
    type: KAFKA
    topic: stroom-proxy-aggregate-input
    bootstrapServers: kafka-broker-1:9092,kafka-broker-2:9092
    producer:
      acks: all
      retries: "3"
    consumer:
      group.id: stroom-proxy-aggregate
      auto.offset.reset: earliest
```

**Characteristics**:
- Consumer group semantics — multiple consumers share partitions
- Offset-based acknowledgement (committed on `acknowledge()`)
- `fail()` does not commit the offset, so the message will be redelivered on next poll — immediately, with no back-off, so a poison message will spin
- `next()` polls with a 100 ms timeout (`DEFAULT_POLL_TIMEOUT`), so it is effectively non-blocking rather than long-polling like SQS

**Configuration fields**:

| Field | Required | Description |
|-------|----------|-------------|
| `topic` | Yes | Kafka topic name |
| `bootstrapServers` | Yes | Comma-separated Kafka broker addresses |
| `producer` | No | Additional Kafka producer properties |
| `consumer` | No | Additional Kafka consumer properties |

Some properties are **reserved** — the proxy sets them and configuration that
tries to override them is rejected at startup with `QUEUE_RESERVED_PROPERTY`:
`max.poll.records`, `enable.auto.commit`, the key/value deserialisers, `acks`
and the key/value serialisers. Each one breaks the queue in a way that is hard
to observe, so it fails loudly rather than being quietly discarded — see
[infrastructure/queues.md §4.2](infrastructure/queues.md#42-configuration).
Everything else, including `group.id`, `auto.offset.reset` and all transport and
security settings, is passed through untouched.

---

## File Store Types

File stores hold the actual data (file groups). Each stage writes to a named file store. Two backing types are supported.

### LOCAL_FILESYSTEM (Default)

**Best for**: Single-node deployments, shared NFS/EFS mounts, multi-node clusters with a shared filesystem.

File groups are stored as directories on the local or shared filesystem. Each write gets a unique sequentially-numbered directory. Writes use a staging area and atomic commit (rename) to ensure durability.

```yaml
fileStores:
  receiveStore:
    type: LOCAL_FILESYSTEM
    path: /data/proxy/stores/receiveStore  # Optional — derived if omitted
```

**Write flow**:
1. `newWrite()` creates a temporary directory in the staging area (`writing/<writerId>/`)
2. Files are written to this temporary directory
3. `commit()` atomically renames (moves) the directory into the stable area (`<writerId>/<sequenceId>/`) and returns a `FileStoreLocation`

There is no completion marker file — **the atomic move is the commit**. A file
group directory exists in the stable area only once it is complete, so other
stages cannot observe a partial write. If the filesystem does not support atomic
moves the store falls back to a plain move, which weakens that guarantee; avoid
filesystems where `ATOMIC_MOVE` is unsupported for store paths.

Closing an uncommitted write deletes the staging directory, so an abandoned
write leaves nothing behind. A hard crash does leave the staging directory —
see [future-work.md §10](future-work.md#10-orphaned-file-cleanup).

#### Multi-Node Write Safety

When multiple proxy nodes share the same filesystem (e.g. NFS, EFS, GlusterFS), each node writes to its own **writer subdirectory** within the store root. This is achieved via the `writerId` — a unique identifier assigned to each `LocalFileStore` instance. By default, the `writerId` is a random UUID generated at startup, guaranteeing that no two nodes will write to the same directory.

The resulting directory structure looks like this:

```
/data/proxy/stores/receiveStore/
├── a1b2c3d4-e5f6-7890-abcd-ef1234567890/   ← Node A's writer directory
│   ├── 0000000001/                           ← File group (committed)
│   │   ├── proxy.meta
│   │   ├── proxy.zip
│   │   └── proxy.entries
│   ├── 0000000002/
│   └── ...
├── f9e8d7c6-b5a4-3210-fedc-ba0987654321/   ← Node B's writer directory
│   ├── 0000000001/                           ← Independent sequence per writer
│   ├── 0000000002/
│   └── ...
└── writing/                                 ← Temp staging area (per writer)
    ├── a1b2c3d4-e5f6-7890-abcd-ef1234567890/
    │   └── write-1234567890/                 ← In-progress write (not yet committed)
    └── f9e8d7c6-b5a4-3210-fedc-ba0987654321/
```

**Key properties**:
- Each node has its **own sequence counter** (0000000001, 0000000002, …) scoped to its writer directory. There is no cross-node contention on sequence numbers.
- The staging area (`writing/`) is also partitioned by `writerId`, so in-progress writes from different nodes never collide.
- The `FileStoreLocation` returned by `commit()` contains the **full absolute path** including the writer directory, so consuming stages resolve the correct file regardless of which node wrote it.
- Consuming stages can resolve and delete file groups written by any node, because `FileStoreLocation` references are stored in queue messages and carry the complete path.

This means you can safely point multiple proxy nodes at the same shared store path — each node will write to its own isolated subtree, and the queue messages ensure consumers find the right files.

### S3 (AWS S3 / S3-Compatible)

**Best for**: Distributed deployments, durable cloud storage, cross-AZ resilience.

File groups are stored as objects in an S3 bucket. A local cache directory is used for staging uploads and caching downloads.

```yaml
fileStores:
  aggregateStore:
    type: S3
    region: eu-west-2
    bucket: stroom-proxy-data
    keyPrefix: aggregateStore/
    credentialsType: default           # Uses AWS SDK default chain
    localCachePath: /data/proxy/cache  # Optional — derived if omitted
```

**S3-compatible stores** (MinIO, LocalStack, Cloudflare R2):

```yaml
fileStores:
  receiveStore:
    type: S3
    bucket: stroom-proxy
    keyPrefix: receive/
    endpointOverride: http://minio.internal:9000
    credentialsType: basic
    accessKeyId: minioadmin
    secretAccessKey: minioadmin
    localCachePath: /data/proxy/cache
```

**Configuration fields**:

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `type` | No | `LOCAL_FILESYSTEM` | `LOCAL_FILESYSTEM` or `S3` |
| `path` | No | Derived | Filesystem path (local stores only) |
| `region` | For S3 | — | AWS region |
| `bucket` | For S3 | — | S3 bucket name |
| `keyPrefix` | No | `{storeName}/` | S3 key prefix |
| `endpointOverride` | No | — | Override for S3-compatible stores |
| `credentialsType` | No | `default` | `default`, `basic`, `environment`, `profile` |
| `accessKeyId` | If basic | — | AWS access key (basic credentials only) |
| `secretAccessKey` | If basic | — | AWS secret key (basic credentials only) |
| `localCachePath` | No | Derived | Local staging/cache directory |

---

## Configuration Reference

### Top-Level Structure

The pipeline is **always active**. With no explicit `pipeline` block, all 5 stages are auto-wired with local-filesystem queues and stores — zero configuration required.

```yaml
pipeline:
  queues:
    splitZipInput:     { ... }
    preAggregateInput: { ... }
    aggregateInput:    { ... }
    forwardingInput:   { ... }

  fileStores:
    receiveStore:      { ... }
    splitStore:        { ... }
    preAggregateStore: { ... }
    aggregateStore:    { ... }

  stages:
    receive:      { ... }
    splitZip:     { ... }
    preAggregate: { ... }
    aggregate:    { ... }
    forward:      { ... }
```

### Stage Configuration

Each stage has the following fields:

| Field | Description |
|-------|-------------|
| `enabled` | Whether this stage runs in this process. Defaults to `true` for any stage you list; disabling is explicit |
| `inputQueue` | Logical name of the input queue (references a key in `queues`) |
| `outputQueue` | Logical name of the output queue |
| `splitZipQueue` | Logical name of the split-zip queue (receive stage only) |
| `fileStore` | Logical name of the file store for this stage's output |
| `threads.consumerThreads` | Number of worker threads consuming from the input queue (default: `1`) |
| `threads.maxConcurrentReceives` | Max concurrent receives admitted to the receive file store and output queue (receive stage only, default: `5`) |
| `threads.closeOldAggregatesThreads` | Threads for closing aged aggregates (pre-aggregate only, default: `1`) |

### A `stages` Block Must List All Five Stages

Omit the `stages` block entirely and you get the standard full pipeline, all
stages enabled. **Write a `stages` block and you must list all five.** A stage
you leave out is a `STAGE_NOT_CONFIGURED` validation error naming it, and
because pipeline validation runs during assembly, the proxy refuses to start.

This is deliberate, because omission is ambiguous. On a single-process proxy,
naming one stage to raise a thread count means "leave the others alone". On a
single-purpose node in a distributed deployment, naming one stage means "run
only this one". Those are opposite intentions expressed by identical YAML, so
guessing either way is silently wrong for the other — the proxy starts and
quietly does too little, or quietly does too much. Refusing to guess costs a few
lines of boilerplate and removes a whole class of misconfiguration.

So a forward-only worker is written out in full:

```yaml
pipeline:
  stages:
    receive:
      enabled: false
    splitZip:
      enabled: false
    preAggregate:
      enabled: false
    aggregate:
      enabled: false
    forward:
      enabled: true
      inputQueue: forwardingInput
      threads:
        consumerThreads: 16
```

Every disabled stage additionally raises a `STAGE_DISABLED` **warning** naming
it. Disabling is legitimate — it is how work is split across processes — so this
is not an error; it puts each process's role in the startup log and shows which
queues this process is not draining.

A stage you *do* list without an explicit `enabled` defaults to enabled: naming
a stage means you want it, and turning it off is the explicit act.

### `maxConcurrentReceives`

This bounds how many receives may be writing to the receive file store and
publishing to the output queue at once. A receiving thread blocks on a fair
semaphore in `ReceiveStagePublisher` until a slot frees, which applies
backpressure back up the calling HTTP or scanner thread.

It bounds the *pipeline-facing* part of a receive, not the reading of the
request body — inbound connection concurrency is governed by the HTTP connector
configuration.

---

## Deployment Examples

> Complete, ready-to-copy versions of these live in
> [deployments/](deployments/). Each is a full `proxyConfig` block that has been
> checked to parse and to pass `ProxyPipelineConfigValidator` with no errors.
> The snippets below show the `pipeline` block alone, for reading.
>
> | Example | File |
> |---|---|
> | 1, 2 — single process | [single-process.yml](deployments/single-process.yml) |
> | 4 — SQS + S3 | [sqs-s3-distributed.yml](deployments/sqs-s3-distributed.yml) |
> | 6 — Kafka | [kafka-distributed.yml](deployments/kafka-distributed.yml) |
> | Per-stage worker processes | [split-stage-workers.yml](deployments/split-stage-workers.yml) |

### Example 1: Simple Single-Process (All Defaults)

The simplest deployment — all stages run in one process with local filesystem queues and stores. **No pipeline configuration is needed at all.** An empty `pipeline` block (or omitting it entirely) auto-wires all five stages.

```yaml
pipeline: {}
```

This is equivalent to the following fully-expanded configuration:

```yaml
pipeline:
  queues:
    splitZipInput:     { type: LOCAL_FILESYSTEM }
    preAggregateInput: { type: LOCAL_FILESYSTEM }
    aggregateInput:    { type: LOCAL_FILESYSTEM }
    forwardingInput:   { type: LOCAL_FILESYSTEM }
  fileStores:
    receiveStore:      { type: LOCAL_FILESYSTEM }
    splitStore:        { type: LOCAL_FILESYSTEM }
    preAggregateStore: { type: LOCAL_FILESYSTEM }
    aggregateStore:    { type: LOCAL_FILESYSTEM }
  stages:
    receive:
      outputQueue: preAggregateInput
      splitZipQueue: splitZipInput
      fileStore: receiveStore
    splitZip:
      inputQueue: splitZipInput
      outputQueue: preAggregateInput
      fileStore: splitStore
    preAggregate:
      inputQueue: preAggregateInput
      outputQueue: aggregateInput
      fileStore: preAggregateStore
    aggregate:
      inputQueue: aggregateInput
      outputQueue: forwardingInput
      fileStore: aggregateStore
    forward:
      inputQueue: forwardingInput
```

### Example 2: High-Throughput Single-Process

All stages in one process but with increased parallelism. Only the `threads` overrides need to be specified — queue wiring uses the auto-wired defaults:

```yaml
pipeline:
  stages:
    receive:
      threads:
        maxConcurrentReceives: 20
    splitZip:
      threads:
        consumerThreads: 4
    preAggregate:
      threads:
        consumerThreads: 4
        closeOldAggregatesThreads: 2
    aggregate:
      threads:
        consumerThreads: 2
    forward:
      threads:
        consumerThreads: 8
```

### Example 3: Skip Aggregation (Receive + Forward Only)

For proxies that only need to receive and forward without aggregation — disable the middle stages and route receive directly to forwarding:

```yaml
pipeline:
  queues:
    forwardingInput: { type: LOCAL_FILESYSTEM }
  fileStores:
    receiveStore: { type: LOCAL_FILESYSTEM }
  stages:
    receive:
      outputQueue: forwardingInput
      fileStore: receiveStore
    splitZip:
      enabled: false
    preAggregate:
      enabled: false
    aggregate:
      enabled: false
    forward:
      inputQueue: forwardingInput
```

### Example 4: Distributed AWS Deployment (SQS + S3)

Receive and forward run on separate auto-scaling groups. SQS queues enable work distribution across instances. S3 stores provide shared, durable storage visible to all processes.

**Receive instance config** (receives HTTP, publishes to SQS):

```yaml
pipeline:
  queues:
    splitZipInput:
      type: SQS
      queueUrl: https://sqs.eu-west-2.amazonaws.com/123/stroom-proxy-split
      visibilityTimeout: PT30M
    preAggregateInput:
      type: SQS
      queueUrl: https://sqs.eu-west-2.amazonaws.com/123/stroom-proxy-preagg
      visibilityTimeout: PT30M
  fileStores:
    receiveStore:
      type: S3
      region: eu-west-2
      bucket: stroom-proxy-data
      keyPrefix: receive/
  stages:
    receive:
      outputQueue: preAggregateInput
      splitZipQueue: splitZipInput
      fileStore: receiveStore
      threads:
        maxConcurrentReceives: 50
    splitZip:
      enabled: false
    preAggregate:
      enabled: false
    aggregate:
      enabled: false
    forward:
      enabled: false
```

**Forward instance config** (consumes from SQS, forwards to downstream Stroom):

```yaml
pipeline:
  queues:
    forwardingInput:
      type: SQS
      queueUrl: https://sqs.eu-west-2.amazonaws.com/123/stroom-proxy-fwd
      visibilityTimeout: PT1H
  fileStores:
    aggregateStore:
      type: S3
      region: eu-west-2
      bucket: stroom-proxy-data
      keyPrefix: aggregate/
  stages:
    receive:
      enabled: false
    splitZip:
      enabled: false
    preAggregate:
      enabled: false
    aggregate:
      enabled: false
    forward:
      inputQueue: forwardingInput
      threads:
        consumerThreads: 16
```

### Example 5: Mixed Queue Types

You can mix queue types within a single deployment. For example, use local queues for fast intra-process communication between tightly-coupled stages, and SQS for stages that need to scale independently. Only the non-default queues need to be specified:

```yaml
pipeline:
  queues:
    forwardingInput:
      type: SQS
      queueUrl: https://sqs.eu-west-2.amazonaws.com/123/stroom-proxy-fwd
      visibilityTimeout: PT1H
  stages:
    forward:
      threads:
        consumerThreads: 8
```

Queues not explicitly configured default to `LOCAL_FILESYSTEM`. In this example, `splitZipInput`, `preAggregateInput`, and `aggregateInput` all use local filesystem queues, while only `forwardingInput` uses SQS.

### Example 6: Kafka-Based Distributed Pipeline

Using Kafka for all inter-stage communication:

```yaml
pipeline:
  queues:
    splitZipInput:
      type: KAFKA
      topic: stroom-proxy-split-zip
      bootstrapServers: kafka-1:9092,kafka-2:9092,kafka-3:9092
      consumer:
        group.id: stroom-proxy-split
        auto.offset.reset: earliest
    preAggregateInput:
      type: KAFKA
      topic: stroom-proxy-pre-aggregate
      bootstrapServers: kafka-1:9092,kafka-2:9092,kafka-3:9092
      consumer:
        group.id: stroom-proxy-preagg
        auto.offset.reset: earliest
    aggregateInput:
      type: KAFKA
      topic: stroom-proxy-aggregate
      bootstrapServers: kafka-1:9092,kafka-2:9092,kafka-3:9092
      consumer:
        group.id: stroom-proxy-agg
        auto.offset.reset: earliest
    forwardingInput:
      type: KAFKA
      topic: stroom-proxy-forwarding
      bootstrapServers: kafka-1:9092,kafka-2:9092,kafka-3:9092
      consumer:
        group.id: stroom-proxy-fwd
        auto.offset.reset: earliest
      producer:
        acks: all
```

---

## Worker Thread Model

Each enabled stage that has an input queue runs one or more `FileGroupQueueWorker` threads, managed by a `PipelineStageRunner`.

### Processing Loop

Each worker thread runs a continuous poll loop:

```
while (running and not interrupted):
    result = worker.processNext()   // next() → process → acknowledge / fail
    if result is noItem:
        sleep(100ms)                // Empty-poll backoff, all queue types
    // processed and failed items loop immediately, with no delay
  on IOException or RuntimeException:
    sleep(1s)                       // Error backoff
```

The empty-poll backoff applies to **every** queue type, not just local queues.
For SQS it is additive to the long poll, so an idle SQS stage waits `waitTime`
plus 100 ms per cycle — negligible against a 20-second long poll. Both backoffs
are `PipelineStageRunner.DEFAULT_EMPTY_POLL_BACKOFF` (100 ms) and
`DEFAULT_ERROR_BACKOFF` (1 s); neither is currently exposed as configuration.

### Thread Counts

Thread counts are independently configurable per stage via the `threads` block:

- `consumerThreads` controls how many workers poll the input queue concurrently
- `maxConcurrentReceives` controls the HTTP receive concurrency (receive stage only)
- `closeOldAggregatesThreads` controls the background closer for aged aggregates (pre-aggregate stage only)

**Local queues are safe with multiple consumer threads.** `LocalFileGroupQueue.next()`
claims an item by atomically moving it from `pending/` to `in-flight/`; a thread
that loses the race gets `NoSuchFileException` and retries the loop. Raising
`consumerThreads` above 1 on a local queue is a supported way to scale a stage
within one process.

What local queues do **not** support is multiple *processes* sharing one queue
directory — startup recovery moves every in-flight item back to pending, which
would steal work from another running JVM. Use SQS or Kafka to distribute a
stage across processes; see
[future-work.md §5](future-work.md#5-local-queue-multi-process-consumers) and
[deployments/split-stage-workers.yml](deployments/split-stage-workers.yml).

For Kafka, each consumer thread gets its own consumer in the shared consumer
group, so the topic's partition count caps useful parallelism — threads beyond
the partition count are assigned no partitions and sit idle. Size partitions for
the total thread count across all processes consuming that topic, not per
process.

---

## Key Design Principles

### 1. Reference Messages, Not Data Messages

Queue messages are lightweight JSON references (~500 bytes). The actual data lives in file stores. This allows different queue backends to be used without worrying about message size limits (SQS has a 256KB limit, for example).

### 2. Write Once, Reference Many

A file group is written to a file store exactly once. Downstream stages reference it by its `FileStoreLocation`. Only when a stage has finished with the data does it delete it. Fan-out forwarding is the only case where data is copied — once per destination — and the source is deleted after all copies are durable.

### 3. Stages Are Independent

Each stage only knows about its input queue, output queue, and file store. Stages don't know about each other. This means you can:
- Enable or disable any stage independently
- Run different stages in different processes
- Scale stage thread counts independently
- Mix queue and file store types between stages

### 4. At-Least-Once Delivery

The pipeline guarantees at-least-once processing: a file group may be processed
more than once, but it will never be lost. Exactly-once is **not** attempted.

Duplicates are therefore normal, not exceptional. Every stage writes its output
with `newWrite()`, which allocates a fresh path per call, so a redelivered
message produces a second output and a second onward message — and the file
group reaches the downstream twice. Plan for it:

- **Downstream must tolerate duplicate receipt.** Each copy carries the same
  `fileGroupId`, which is the key to de-duplicate on if you need to.
- **Expect duplicates after any ungraceful stop** — `kill -9`, OOM kill, node
  loss, pod eviction — and after any transient processor failure that causes
  `item.fail()`.
- **A destination that is failing amplifies them.** With multiple forward
  destinations, a retry re-delivers to the destinations that already succeeded;
  see [future-work.md](future-work.md).

### 5. Crash Windows and What They Cost

The ownership ordering (write → publish → delete → acknowledge) is arranged so no
crash point loses data. Each has a different cost:

| Crash point | Result |
|---|---|
| Before output commit | Reprocessed cleanly; an uncommitted staging directory is left behind |
| After commit, before publish | Reprocessed; the first output is orphaned in the store |
| After publish, before delete | Reprocessed; **duplicate** output and onward message |
| After delete, before acknowledge | The redelivered message can never succeed — the input is gone — so it retries until it dead-letters |

That last row is worth recognising in an incident. The work *completed*: output
was committed and published before the input was deleted. The failed message
represents finished work, not lost work, and re-driving it will fail again for
the same reason. Check that the onward message exists before acting on it.

### 6. Retry & Dead-Letter Handling

When a stage processor fails, the worker calls `item.fail()` which returns the message to the queue for retry. Each queue backend handles retries and dead-letter routing using its own native mechanisms.

#### Local Queues

Failed items are moved from `in-flight/` back to `pending/` for immediate retry. A `.last-error.txt` file is written alongside the message with the stack trace of the last failure.

If a duplicate already exists in `pending/` (e.g. from a race condition), the item is moved to `failed/` instead. Items in `failed/` are not retried automatically — operators should investigate and either delete them or move them back to `pending/`.

The `failed/` directory count is included in the health check and the `stroom.proxy.pipeline.queue.<name>.failed` Prometheus metric.

#### SQS Queues

Failed items have their visibility timeout set to 0, making them immediately available for retry by any consumer. SQS tracks the number of receives via `ApproximateReceiveCount`.

To prevent infinite retries, configure a **redrive policy** on each SQS queue:

```json
{
  "RedrivePolicy": {
    "deadLetterTargetArn": "arn:aws:sqs:eu-west-2:123456789012:stroom-proxy-dlq",
    "maxReceiveCount": 5
  }
}
```

After `maxReceiveCount` deliveries, SQS automatically moves the message to the dead-letter queue. Monitor the DLQ depth via CloudWatch or the SQS console.

> **Tip:** Create one DLQ per pipeline queue (e.g. `splitZipInput-dlq`, `forwardingInput-dlq`) so that failed items from different stages don't get mixed together.

#### Kafka Queues

Failed items are retried via offset non-commit — the consumer re-polls the same record on the next iteration. Kafka does not have a built-in receive count, so retry limiting should be configured at the consumer or application level.

For dead-letter routing, consider:
- Configuring a Kafka Streams or consumer-level error handler to route failed records to a separate error topic
- Using a framework like Spring Kafka's `DefaultErrorHandler` with `DeadLetterPublishingRecoverer`

---

## Monitoring & Observability

The pipeline provides built-in monitoring through Dropwizard health checks, Prometheus metrics, structured logging, and an admin monitoring endpoint.

### 1. Health Checks

The pipeline registers a Dropwizard health check (`PipelineHealthChecks`) on the admin `/healthcheck` endpoint. The health check aggregates the status of all configured queues and file stores:

**Queue health checks:**

| Queue Type | Check Performed | Detail Fields |
|---|---|---|
| `LocalFileGroupQueue` | `pending/` and `in-flight/` directories exist and are writable | `pendingCount`, `inFlightCount`, `failedCount` |
| `SqsFileGroupQueue` | `GetQueueAttributes` call with `ApproximateNumberOfMessages` | `queueUrl`, `approximateMessages`, `approximateInFlight`, `activeHeartbeats` |
| `KafkaFileGroupQueue` | `AdminClient.describeTopics()` with 5-second timeout | `topic`, `partitions` |

**File store health checks:**

| Store Type | Check Performed | Detail Fields |
|---|---|---|
| `LocalFileStore` | Root and writer directories exist and are writable | `root`, `writable` |
| `S3FileStore` | `headBucket` call + local staging/cache directory writability | `bucket`, `keyPrefix`, `localStagingWritable` |

**Example healthy response (JSON excerpt):**

```json
{
  "PipelineHealthChecks": {
    "healthy": true,
    "message": null,
    "components": {
      "queue.splitZipInput.healthy": true,
      "queue.preAggregateInput.healthy": true,
      "queue.aggregateInput.healthy": true,
      "queue.forwardingInput.healthy": true,
      "fileStore.receiveStore.healthy": true,
      "fileStore.splitStore.healthy": true,
      "fileStore.preAggregateStore.healthy": true,
      "fileStore.aggregateStore.healthy": true
    }
  }
}
```

**Example unhealthy response:**

```json
{
  "PipelineHealthChecks": {
    "healthy": false,
    "message": "One or more pipeline components are unhealthy",
    "components": {
      "queue.splitZipInput.healthy": false,
      "queue.splitZipInput.message": "Directory check failed: pending=false, inFlight=true"
    }
  }
}
```

> **IAM permissions for SQS health checks:** The SQS health check requires `sqs:GetQueueAttributes` permission. This is the same permission required for CloudWatch metrics and should already be present in most IAM policies.

### 2. Prometheus Metrics

Pipeline metrics are exported via the admin `/metrics` endpoint in Prometheus format. All metrics use Codahale gauges backed by thread-safe `LongAdder` counters. The values are monotonically increasing totals — use Prometheus `rate()` to derive rates.

**Per-stage item counters:**

| Metric Name | Description |
|---|---|
| `stroom.proxy.pipeline.<stage>.items.received` | Total items received from queue |
| `stroom.proxy.pipeline.<stage>.items.processed` | Total items successfully processed |
| `stroom.proxy.pipeline.<stage>.items.acknowledged` | Total items acknowledged (removed from queue) |
| `stroom.proxy.pipeline.<stage>.items.failed` | Total items failed and returned to queue |

**Per-stage error counters:**

| Metric Name | Description |
|---|---|
| `stroom.proxy.pipeline.<stage>.errors.processor` | Errors during stage processing |
| `stroom.proxy.pipeline.<stage>.errors.acknowledge` | Errors during queue acknowledgement |
| `stroom.proxy.pipeline.<stage>.errors.fail` | Errors during fail-and-retry |
| `stroom.proxy.pipeline.<stage>.errors.close` | Errors during item close |

**Per-stage poll counters:**

| Metric Name | Description |
|---|---|
| `stroom.proxy.pipeline.<stage>.polls.total` | Total queue polls |
| `stroom.proxy.pipeline.<stage>.polls.empty` | Polls that returned no items |

**Per-queue depth gauges (local queues only):**

| Metric Name | Description |
|---|---|
| `stroom.proxy.pipeline.queue.<name>.pending` | Approximate pending items |
| `stroom.proxy.pipeline.queue.<name>.inflight` | Approximate in-flight items |
| `stroom.proxy.pipeline.queue.<name>.failed` | Approximate failed items |

**Per-queue SQS heartbeat counters:**

| Metric Name | Description |
|---|---|
| `stroom.proxy.pipeline.queue.<name>.heartbeat.attempts` | Total heartbeat (visibility extension) attempts |
| `stroom.proxy.pipeline.queue.<name>.heartbeat.successes` | Successful visibility extensions |
| `stroom.proxy.pipeline.queue.<name>.heartbeat.failures` | Failed visibility extensions |

**Example Prometheus queries:**

```promql
# Items processed per second by the forward stage
rate(stroom_proxy_pipeline_forward_items_processed[5m])

# Error rate for the forward stage
rate(stroom_proxy_pipeline_forward_errors_processor[5m])

# Current queue depth
stroom_proxy_pipeline_queue_forwardingInput_pending

# SQS heartbeat failure rate
rate(stroom_proxy_pipeline_queue_forwardingInput_heartbeat_failures[5m])

# Empty poll ratio (indicates queue saturation)
rate(stroom_proxy_pipeline_forward_polls_empty[5m])
  / rate(stroom_proxy_pipeline_forward_polls_total[5m])
```

> **Note:** Prometheus converts dots in metric names to underscores, so `stroom.proxy.pipeline.forward.items.processed` becomes `stroom_proxy_pipeline_forward_items_processed` in PromQL.

### 3. Structured Logging

The pipeline worker sets MDC (Mapped Diagnostic Context) fields before processing each queue item. These fields are automatically included in structured log output (e.g. logback JSON encoder) and enable log correlation across stages.

| MDC Key | Source | Description |
|---|---|---|
| `traceId` | `FileGroupQueueMessage.traceId()` | End-to-end trace ID (may be null) |
| `fileGroupId` | `FileGroupQueueMessage.fileGroupId()` | Unique file group identifier |
| `messageId` | `FileGroupQueueMessage.messageId()` | Queue message ID |
| `stageName` | `queue.getName()` | See caveat below |

Despite its name, `stageName` holds the **input queue name**, not the stage
name — `FileGroupQueueWorker` sets it from `queue.getName()`. With the default
topology the two are effectively interchangeable (the `aggregate` stage consumes
`aggregateInput`), but if you rename queues they will diverge. Filter on the
queue name you configured.

MDC values are automatically cleared after processing completes (success or failure). If `traceId` is null on the message, the MDC key is not set (no `NullPointerException`).

**Example logback configuration for JSON output:**

```xml
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>fileGroupId</includeMdcKeyName>
        <includeMdcKeyName>messageId</includeMdcKeyName>
        <includeMdcKeyName>stageName</includeMdcKeyName>
    </encoder>
</appender>
```

### 4. Admin Monitoring Endpoint

The existing `/queues` admin endpoint (`ProxyQueueMonitoringServlet`) displays pipeline information:

- **Pipeline Stages:** Shows worker thread count, item/poll counters, and error totals. Stages with errors are highlighted in red.
- **Pipeline Queues:** Shows queue type, health status (✓/✗), queue depths (for local queues), and SQS heartbeat counters. Unhealthy queues are highlighted in red.
- **Pipeline File Stores:** Shows health status (✓/✗) for each store. Unhealthy stores are highlighted in red.

This endpoint is unauthenticated and available on the admin port for operational monitoring.

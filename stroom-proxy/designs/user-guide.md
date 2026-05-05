# Stroom Proxy Pipeline Architecture

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

Messages are stored as individual numbered JSON files in a filesystem directory. The queue provides FIFO ordering within a single consumer, and uses file-rename-based acknowledgement.

```yaml
queues:
  preAggregateInput:
    type: LOCAL_FILESYSTEM
    path: /data/proxy/queues/preAggregateInput  # Optional — derived if omitted
```

**Characteristics**:
- No external dependencies
- FIFO ordering
- Single-consumer only (no competing consumers across processes)
- `next()` is non-blocking — returns `Optional.empty()` immediately if no messages
- Worker uses a 100ms empty-poll backoff to avoid busy-waiting

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
- **Visibility heartbeat**: A background thread automatically extends the visibility timeout for in-flight items at 2/3 of the configured interval, preventing premature redelivery during long-running processing
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
- `fail()` does not commit the offset, so the message will be redelivered on next poll
- `next()` uses Kafka polling with a 1-second timeout

**Configuration fields**:

| Field | Required | Description |
|-------|----------|-------------|
| `topic` | Yes | Kafka topic name |
| `bootstrapServers` | Yes | Comma-separated Kafka broker addresses |
| `producer` | No | Additional Kafka producer properties |
| `consumer` | No | Additional Kafka consumer properties |

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
1. `newWrite()` creates a temporary directory in the staging area (`.writing/<writerId>/`)
2. Files are written to this temporary directory
3. `commit()` atomically renames (moves) the directory into the stable area (`<writerId>/<sequenceId>/`) and returns a `FileStoreLocation`
4. A `.complete` marker file is written as the final step, enabling idempotency checks

This ensures that other stages only see complete, committed file groups — never partial writes.

#### Multi-Node Write Safety

When multiple proxy nodes share the same filesystem (e.g. NFS, EFS, GlusterFS), each node writes to its own **writer subdirectory** within the store root. This is achieved via the `writerId` — a unique identifier assigned to each `LocalFileStore` instance. By default, the `writerId` is a random UUID generated at startup, guaranteeing that no two nodes will write to the same directory.

The resulting directory structure looks like this:

```
/data/proxy/stores/receiveStore/
├── a1b2c3d4-e5f6-7890-abcd-ef1234567890/   ← Node A's writer directory
│   ├── 0000000001/                           ← File group (committed)
│   │   ├── proxy.meta
│   │   ├── proxy.zip
│   │   ├── proxy.entries
│   │   └── .complete
│   ├── 0000000002/
│   └── ...
├── f9e8d7c6-b5a4-3210-fedc-ba0987654321/   ← Node B's writer directory
│   ├── 0000000001/                           ← Independent sequence per writer
│   ├── 0000000002/
│   └── ...
└── .writing/                                 ← Temp staging area (per writer)
    ├── a1b2c3d4-e5f6-7890-abcd-ef1234567890/
    │   └── write-1234567890/                 ← In-progress write (not yet committed)
    └── f9e8d7c6-b5a4-3210-fedc-ba0987654321/
```

**Key properties**:
- Each node has its **own sequence counter** (0000000001, 0000000002, …) scoped to its writer directory. There is no cross-node contention on sequence numbers.
- The staging area (`.writing/`) is also partitioned by `writerId`, so in-progress writes from different nodes never collide.
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

```yaml
pipeline:
  enabled: true

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
| `enabled` | Whether this stage runs in this process (default: `false`) |
| `inputQueue` | Logical name of the input queue (references a key in `queues`) |
| `outputQueue` | Logical name of the output queue |
| `splitZipQueue` | Logical name of the split-zip queue (receive stage only) |
| `fileStore` | Logical name of the file store for this stage's output |
| `threads.consumerThreads` | Number of worker threads consuming from the input queue (default: `1`) |
| `threads.maxConcurrentReceives` | Max concurrent HTTP receives (receive stage only, default: `5`) |
| `threads.closeOldAggregatesThreads` | Threads for closing aged aggregates (pre-aggregate only, default: `1`) |

---

## Deployment Examples

### Example 1: Simple Single-Process (All Defaults)

The simplest deployment — all stages run in one process with local filesystem queues and stores. Setting `enabled: true` with no explicit stages configuration automatically wires all five stages.

```yaml
pipeline:
  enabled: true
```

This is equivalent to:

```yaml
pipeline:
  enabled: true
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
      enabled: true
      outputQueue: preAggregateInput
      splitZipQueue: splitZipInput
      fileStore: receiveStore
    splitZip:
      enabled: true
      inputQueue: splitZipInput
      outputQueue: preAggregateInput
      fileStore: splitStore
    preAggregate:
      enabled: true
      inputQueue: preAggregateInput
      outputQueue: aggregateInput
      fileStore: preAggregateStore
    aggregate:
      enabled: true
      inputQueue: aggregateInput
      outputQueue: forwardingInput
      fileStore: aggregateStore
    forward:
      enabled: true
      inputQueue: forwardingInput
```

### Example 2: High-Throughput Single-Process

All stages in one process but with increased parallelism:

```yaml
pipeline:
  enabled: true
  stages:
    receive:
      enabled: true
      outputQueue: preAggregateInput
      splitZipQueue: splitZipInput
      fileStore: receiveStore
      threads:
        maxConcurrentReceives: 20
    splitZip:
      enabled: true
      inputQueue: splitZipInput
      outputQueue: preAggregateInput
      fileStore: splitStore
      threads:
        consumerThreads: 4
    preAggregate:
      enabled: true
      inputQueue: preAggregateInput
      outputQueue: aggregateInput
      fileStore: preAggregateStore
      threads:
        consumerThreads: 4
        closeOldAggregatesThreads: 2
    aggregate:
      enabled: true
      inputQueue: aggregateInput
      outputQueue: forwardingInput
      fileStore: aggregateStore
      threads:
        consumerThreads: 2
    forward:
      enabled: true
      inputQueue: forwardingInput
      threads:
        consumerThreads: 8
```

### Example 3: Skip Aggregation (Receive + Forward Only)

For proxies that only need to receive and forward without aggregation — disable the middle stages and route receive directly to forwarding:

```yaml
pipeline:
  enabled: true
  queues:
    forwardingInput: { type: LOCAL_FILESYSTEM }
  fileStores:
    receiveStore: { type: LOCAL_FILESYSTEM }
  stages:
    receive:
      enabled: true
      outputQueue: forwardingInput
      fileStore: receiveStore
    splitZip:
      enabled: false
    preAggregate:
      enabled: false
    aggregate:
      enabled: false
    forward:
      enabled: true
      inputQueue: forwardingInput
```

### Example 4: Distributed AWS Deployment (SQS + S3)

Receive and forward run on separate auto-scaling groups. SQS queues enable work distribution across instances. S3 stores provide shared, durable storage visible to all processes.

**Receive instance config** (receives HTTP, publishes to SQS):

```yaml
pipeline:
  enabled: true
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
      enabled: true
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
  enabled: true
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
      enabled: true
      inputQueue: forwardingInput
      threads:
        consumerThreads: 16
```

### Example 5: Mixed Queue Types

You can mix queue types within a single deployment. For example, use local queues for fast intra-process communication between tightly-coupled stages, and SQS for stages that need to scale independently:

```yaml
pipeline:
  enabled: true
  queues:
    splitZipInput:
      type: LOCAL_FILESYSTEM
    preAggregateInput:
      type: LOCAL_FILESYSTEM
    aggregateInput:
      type: LOCAL_FILESYSTEM
    forwardingInput:
      type: SQS
      queueUrl: https://sqs.eu-west-2.amazonaws.com/123/stroom-proxy-fwd
      visibilityTimeout: PT1H
  stages:
    receive:
      enabled: true
      outputQueue: preAggregateInput
      splitZipQueue: splitZipInput
      fileStore: receiveStore
    splitZip:
      enabled: true
      inputQueue: splitZipInput
      outputQueue: preAggregateInput
      fileStore: splitStore
    preAggregate:
      enabled: true
      inputQueue: preAggregateInput
      outputQueue: aggregateInput
      fileStore: preAggregateStore
    aggregate:
      enabled: true
      inputQueue: aggregateInput
      outputQueue: forwardingInput
      fileStore: aggregateStore
    forward:
      enabled: true
      inputQueue: forwardingInput
      threads:
        consumerThreads: 8
```

### Example 6: Kafka-Based Distributed Pipeline

Using Kafka for all inter-stage communication:

```yaml
pipeline:
  enabled: true
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
while (not shutdown):
    item = queue.next()           // Non-blocking for local; long-poll for SQS/Kafka
    if item is empty:
        if local queue:
            sleep(100ms)          // Empty-poll backoff
        continue
    try:
        processor.process(item)   // Steps 1–5 of the ownership contract
        item.acknowledge()        // Step 6
    catch Exception:
        item.fail(error)          // Return to queue for retry
```

### Thread Counts

Thread counts are independently configurable per stage via the `threads` block:

- `consumerThreads` controls how many workers poll the input queue concurrently
- For **local queues**, only a single consumer thread makes sense (single-consumer design)
- For **SQS/Kafka**, multiple consumer threads enable parallel processing across partitions/messages
- `maxConcurrentReceives` controls the HTTP receive concurrency (receive stage only)
- `closeOldAggregatesThreads` controls the background closer for aged aggregates (pre-aggregate stage only)

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

### 4. Idempotent Writes

File stores use deterministic write IDs (`newDeterministicWrite(id)`) where possible, so that reprocessing the same item produces the same output path. Combined with `isComplete()` checks, this provides idempotency — reprocessing a message after a crash doesn't create duplicate data.

### 5. At-Least-Once Delivery

The pipeline guarantees at-least-once processing. A message may be processed more than once (e.g. after a crash between step 4 and step 6 of the ownership contract), but it will never be lost. Idempotent writes ensure that duplicate processing is safe.

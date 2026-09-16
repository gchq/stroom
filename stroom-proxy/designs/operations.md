# Stroom Proxy — Operations Guide

Configuring, deploying and monitoring the proxy pipeline. For the design behind
it see [architecture.md](architecture.md); for the end-to-end data path and
on-disk layout see [data-path.md](data-path.md).

## Use Cases

The proxy has always been shaped around a handful of deployment intentions.
Under the pipeline architecture these are no longer distinct code paths — they
are all the same four stages with different stages enabled and different
forwarding destinations configured.

| Use case | What it does | How it is configured |
|---|---|---|
| **Repeater (forward only)** | Receives and immediately repeats to a single destination without storing or processing. Forwarding errors go back to the sender. Minimal validation — enough to confirm the headers carry a feed name, which can be checked against a receipt policy. | Configure the one destination with `instant: true`; the pipeline is not assembled and the sender is answered only once the destination has accepted the data |
| **Receive + store** | Receives and stores to disk for some other process to collect. | Disable `splitZip`, `aggregate` **and** `forward`, and make `stages.receive.outputQueue` an **external** queue that the collecting process drains. A local queue nobody consumes is refused at boot — see the note below |
| **Receive + store + forward** | Acknowledges receipt once stored, then forwards to one or more destinations. Forward errors are logged and retried, not returned to the sender. | Disable `splitZip` and `aggregate`; **point `stages.receive.outputQueue` at `forwardingInput`** |
| **Receive + store + aggregate + forward** | Also packs many small streams for the same feed into one archive before forwarding. Fewer connections and much less bandwidth, since log data compresses well. | The default — all four stages enabled |
| **Scan + forward** | Picks up data written to a directory by another process — typically a remote receive-only proxy — and forwards it. | Enable `dirScanner`; disable `splitZip` and `aggregate`; **point `stages.receive.outputQueue` at `forwardingInput`** |
| **Scan + aggregate + forward** | As above, with aggregation. | Enable `dirScanner`; all four stages enabled |

> **Why the output queue keeps being mentioned.** `stages.receive.outputQueue` defaults to
> `aggregateInput`. Disable the aggregate stage without re-pointing it and the receive stage
> publishes into a local queue no enabled stage drains — data would accumulate there unread. The
> proxy refuses to start rather than let that happen (`LOCAL_QUEUE_HAS_NO_CONSUMER`), and the error
> names the three ways out: enable a consumer, re-point the publishing stage's output, or make the
> queue external so another process can drain it.
>
> **This is why "Receive + store" needs an external queue.** It is the one use case with no
> downstream stage at all, so the only honest terminus is a queue something else consumes. Written
> with local queues throughout, it cannot start — correctly, because the data would be stranded.
> *(The recipes above previously omitted all of this, and following the "Receive + store" row
> literally produced a configuration that could not start.)*

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

Data flows through up to four stages in sequence:

```
                                    ┌──────────────┐
                                    │   SplitZip   │
                                    │    Stage     │
                                    └──────┬───────┘
                                           │
HTTP ──► Receive ──► splitZipInput ────────┘
         Stage        (if multi-feed)       
           │                                
           └──► aggregateInput ──► Aggregate ──► forwardingInput ──► Forward ──► Downstream
                (if single-feed)     Stage                              Stage      Stroom
```

### Stage 1: Receive

**Purpose**: Accept incoming data via HTTP POST and introduce it into the pipeline.

**What it does**:
1. Runs the receipt policy once per feed the request holds, before writing anything
2. Writes the received data straight into a **receive file store** (`receiveStore`) write as a canonical proxy zip with `proxy.entries` and `proxy.meta`, and commits it
3. If the zip holds **one feed** → publishes a reference message carrying that feed to `aggregateInput`
4. If it holds **several feeds** → publishes a reference message to `splitZipInput`; with no split-zip queue configured such a zip is refused
5. Answers the sender only when both the commit and the publish have happened

**Class**: `StoringReceiver` (implements `Receiver`)

**Input**: HTTP request body, scanned zip file, or rolled event-store file
**Output file store**: `receiveStore`
**Output queue**: `aggregateInput` or `splitZipInput`

### Stage 2: Split Zip (Optional)

**Purpose**: Split a multi-feed zip into one file group per feed, each published with its feed as
the aggregation key, so that downstream aggregation is per feed and a feed's inputs can find each
other ([stages/split-zip.md](stages/split-zip.md)).

**What it does**:
1. Reads a reference message from `splitZipInput` and resolves the source file group
2. For each feed key in `proxy.entries`: writes that key's items straight into a **split file
   store** (`splitStore`) write as a canonical proxy zip, commits it, and publishes a reference
   message carrying the key to `aggregateInput`
3. **Deletes the consumed input** from the source file store
4. Worker acknowledges the input message

Nothing is written outside the store; there is no staging directory.

**Class**: `SplitZipStage`

**Input queue**: `splitZipInput`
**Output file store**: `splitStore`
**Output queue**: `aggregateInput`

### Stage 3: Aggregate

**Purpose**: Pack many small file groups for one feed into one archive before forwarding
([stages/aggregate.md](stages/aggregate.md)).

**What it does**:
1. A **claimer** thread claims a reference message from `aggregateInput`, resolves the input, reads
   its `proxy.entries`, and assigns its items to the open aggregate for its feed key. An open
   aggregate is nothing but the set of claimed inputs: nothing is moved or copied, the input stays
   in its store and its message stays claimed
2. When the next item would breach `maxItemsPerAggregate` or `maxUncompressedByteSize`, or an
   aggregate has been open longer than `aggregationFrequency`, the aggregate closes. A large input
   is divided by item range across consecutive aggregates
3. A **merge worker** copies the items of every slice straight into an **aggregate file store**
   (`aggregateStore`) write as one canonical proxy zip, commits it, publishes a reference message to
   `forwardingInput`, and **deletes each input whose last slice is now published**
4. The claimer acknowledges those inputs' messages on its next pass

An input is acknowledged only after every aggregate that took part of it is published, so a node
that dies mid-aggregate loses nothing: what it held is redelivered. The stage owns no directories
and does nothing at stop.

**Class**: `AggregateStage`

**Input queue**: `aggregateInput`
**Output file store**: `aggregateStore`
**Output queue**: `forwardingInput`

### Stage 4: Forward

**Purpose**: Send fully aggregated data to one or more destinations: a downstream Stroom or proxy
over HTTP, a directory tree another process collects from, or an S3 bucket. Design:
[stages/forward.md](stages/forward.md).

**What it does**, per destination:
1. Claims a reference message from its queue (`forwardingInput`, or `forward-<dest>` when fanning out)
2. Resolves the file group and checks `proxy.meta`, `proxy.zip` and `proxy.entries` are all present
3. Waits out the destination's back-off if it has just failed
4. Delivers the group. On acceptance, deletes the input and returns, so the worker acknowledges
5. On a transient failure (connection refused, timeout, `5xx`, a mount gone, an S3 call failed),
   throws, so the worker fails the message: it goes to the tail of the queue with the attempt
   counted, and the destination waits `retryDelay` (growing by `retryDelayGrowthFactor` to
   `maxRetryDelay`) before its next attempt on any group
6. On a refusal (feed not set to receive, rejected by policy, wrong type, no feed), or a transient
   failure on a group older than `maxRetryAge`, writes the group with an `error.log` to the
   destination's give-up destination, then deletes the input and returns

**With several destinations** the stage fans out: `forwardingInput` is consumed by `FanOutStage`,
which copies each group into every destination's `forward-<dest>` file store and publishes to its
`forward-<dest>` queue, then deletes the input; each destination's `ForwardStage` drains its own
queue with its own threads, retries and give-up. Both are named in the pipeline block, explicit or
fail, so that in shared mode they are shared like every other queue and store.

**Liveness.** A destination with a liveness check (an HTTP `GET` of its status URL, a read or touch
of a path) has it run every `livenessCheckInterval`; the destination's loop is paused while the
check fails and resumed when it passes. A paused loop holds no claims.

**Nothing of forwarding is on local disk.** A group waiting to be forwarded is in its pipeline
store with its message on the pipeline queue, claimed only while an attempt is being made. What the
stage knows about a destination's health is a failure count in memory.

**Classes**: `ForwardStage`, `FanOutStage`, `GiveUp`, `LivenessWatch`, `ForwardDestinations`; the
destinations `HttpDestination`, `FileDestination`, `S3Destination`.

**Input queue**: `forwardingInput`, plus `forward-<dest>` per destination when fanning out
**Output**: the destinations (terminal stage — no output queue)

#### What a file destination receives

A **file** destination is handed the group's directory - `proxy.zip`, `proxy.meta`,
`proxy.entries` - and renames it into its tree. A group that is retried is delivered as it is in
the store: nothing is written beside it between attempts. Only a give-up adds a file, `error.log`,
and it goes to the give-up destination rather than to the forward destination. When a file
destination is a directory another proxy scans, the scanner consumes `error.log` with the group.

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

- **Step 5 happens before step 6**: The input data is deleted before the input message is acknowledged. If the process crashes between steps 5 and 6, the input message will be redelivered, but the input data is gone — the store reports it absent and the worker **acknowledges** the message (R12: absence of data means the work was done). The output was committed and published before the input was deleted, so nothing is lost and nothing is dead-lettered.

- **If processing fails** (exception at step 2): The worker catches the exception, calls `item.fail()` instead of `item.acknowledge()`, and the item is returned to the queue for retry. The input data is untouched.

### Fan-Out Durability

For the forward stage with multiple destinations, `FanOutStage` copies to **all destinations before any input is deleted**:

```
For each destination:
    1. Copy file group to the destination's file store (durable write + commit)
    2. Publish a reference message to the destination's queue
Then:
    3. Delete the input from the source file store
    4. Acknowledge the input message (worker)
```

If a crash occurs after some destinations have been written but before all are complete, the input message will be redelivered. Some destinations may receive duplicate data (at-least-once), but no destination will miss data. From then on each destination delivers its own copy from its own queue, so one destination being down never delays, duplicates or gives up on another's delivery.

### Reference Messages, Not Data Messages

Queue messages are lightweight **references**, not data payloads. A `FileGroupQueueMessage` contains:

| Field | Purpose |
|-------|---------|
| `messageId` | Unique message identifier (UUID) |
| `fileStoreLocation` | Reference to where the data lives (store name + complete URI) |
| `feed`, `type` | The aggregation key, when the group is single-feed; absent for a multi-feed group on its way to be split. A partitioned backend keys on it so a feed's parts reach one consuming node |
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
  aggregateInput:
    type: LOCAL_FILESYSTEM
    path: /data/proxy/queues/aggregateInput  # Optional — derived if omitted
```

**Characteristics**:
- No external dependencies
- Roughly FIFO — `pending/` is drained in filename (sequence) order
- **Multi-threaded within one process, single-process only.** Competing consumer
  threads are safe: a claim is an atomic `pending/` → `in-flight/` move and the
  loser of a race retries. Two *processes* sharing one queue directory are not
  safe, because startup recovery returns every in-flight item to `pending/`.
- `next()` is non-blocking — returns `Optional.empty()` immediately if no messages
- The worker's `next()` waits up to a second for a message, so an idle stage does not spin
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
- **Held claims multiply the heartbeat.** The aggregate stage holds every input of an open aggregate claimed until the aggregate is published, so with `aggregateInput` on SQS the heartbeat runs once per held message per interval. At the default `visibilityTimeout` that is small; at a short one with thousands of inputs open it is not, and a heartbeat that falls behind hands a held message to another node. Keep `visibilityTimeout` long on `aggregateInput`; `aggregationFrequency` is what bounds the messages held
- `next()` uses SQS long-polling (blocks up to `waitTime` waiting for a message)
- `acknowledge()` deletes the SQS message
- `fail()` sets visibility timeout to 0, making the message immediately available for retry
- **Twelve hours is the ceiling.** SQS will not keep a message invisible for longer than that from the receive, heartbeat or not; a claim held past it is redelivered under its holder and the work duplicated. The validator warns (`SQS_HOLD_NEAR_VISIBILITY_CEILING`) when `aggregationFrequency` on an SQS input, or a forward destination's longest back-off wait on an SQS forward queue, is more than six hours

**Configuration fields**:

| Field | Required | Description |
|-------|----------|-------------|
| `queueUrl` | Yes | Full SQS queue URL |
| `visibilityTimeout` | No | Duration before unacknowledged messages reappear (default: 30 min) |
| `waitTime` | No | Long-poll wait time (default: 20s, SQS maximum) |

### KAFKA (Apache Kafka)

**Best for**: High-throughput distributed deployments, existing Kafka infrastructure.

Messages are published to a Kafka topic as key-value records. The key is the aggregation key (`feed` and `type`) when the message carries one, so every part of a feed lands on one partition and one consuming node, and the `messageId` otherwise. The value is the JSON-serialised message.

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
- `fail()` does not commit the offset, so the message will be redelivered on the next poll; the loop's failure backoff is what keeps a poison message from spinning
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

File stores hold the actual data (file groups). Each stage writes to a named file store, rooted
somewhere sensible for that stage. Three types are supported, and which are allowed follows from
`pipeline.mode`: a `LOCAL` deployment uses `LOCAL_FILESYSTEM` stores, a `SHARED` deployment uses
`SHARED_FILESYSTEM` or `S3`. The full design, including the contract every store meets, is
[infrastructure/file-stores.md](infrastructure/file-stores.md).

### LOCAL_FILESYSTEM (Default)

**Best for**: a single-node proxy. `mode: LOCAL` only.

```yaml
fileStores:
  receiveStore:
    type: LOCAL_FILESYSTEM
    path: /data/proxy/stores/receiveStore  # Optional — derived if omitted
    durability: FULL                       # Optional — FULL is the default for this type
```

File groups are numbered sequentially and laid out so that no directory holds more than 1000
entries: group 1 is `<root>/0/001/`, group 1000 is `<root>/1/001/001000/`. A write is filled
under `<root>/.staging/<id>/` and published by one atomic rename; a directory at a group's path
therefore *is* a complete group, and there is no marker file. `commit()` forces the group's files
and then the directory that publishes them before it returns (`durability: FULL`).

At start-up the proxy clears `.staging/` and deletes every committed group that no message in any
local queue names — the one moment the stores and the queues can be compared exactly, because
nothing is running. That is the only cleanup a local store gets; nothing runs on a timer. A fully
drained store restarts its numbering at 1.

### SHARED_FILESYSTEM

**Best for**: many nodes sharing a mount — Ceph, EFS, NFS. `mode: SHARED` only. Choosing this
type is the operator's assertion that every node sees the path **at the same absolute path**; the
proxy cannot check that. A location is a `file:` URI of the group's absolute path, and a node whose
mount point differs rejects every message another node published as outside its store root — the
message is failed, retried and dead-lettered, and the group is swept.

```yaml
fileStores:
    orphanAge: P7D                         # Required
    durability: FILESYSTEM                 # Optional — FILESYSTEM is the default for this type
```

The same layout and the same atomic rename, under a *writer root* of `<root>/<startId>/` that is a
fresh UUID per process start. Two nodes never share a numbered tree and a node restarting never
reuses one; each process's counter starts at zero with nothing to scan. Every location carries the
full path, so any node resolves any node's group.

Nothing on the mount is cleared at start-up — other nodes are writing under the same root, and a
node that left residue may have been scaled away for ever. Instead every node runs a sweep, hourly,
that deletes anything under the store older than `orphanAge`: stale staging, groups a crash left
unreferenced, empty numbering directories, and empty writer roots of nodes that no longer exist.
**A group older than `orphanAge` is deleted whether or not something still names it**, so
`orphanAge` must exceed the longest a live group can legitimately wait. The validator enforces the
two bounds it can compute — it must be **at least twice** the longest forward retry window (the
destinations' `maxRetryAge`) and at least twice `aggregationFrequency`, the second multiple covering
the one redelivery that follows a node death — and a queue backlog is the operator's judgement.
Watch `stroom.proxy.pipeline.fileStore.<name>.swept.groups`: the sweep cannot tell an orphan from
live work, and a redelivery that finds its group swept is acknowledged as done, silently. The count
should stay near zero; a steady rate under load means `orphanAge` is too short for the backlog.

Durability is left to the mount's own semantics by default; a local `fsync` says nothing about a
network filesystem. Set `durability: FULL` explicitly if the mount honours it and you want it.

### S3 (AWS S3 / S3-Compatible)

**Best for**: many nodes on AWS, or any S3-compatible store. `mode: SHARED` only.

File groups are objects under `<keyPrefix>/<startId>/<id>/`, one writer prefix per process start
as on a shared mount, with a `.complete` marker object PUT last: a group is several PUTs and only
the marker proves them all landed. A nested group is just a longer key. A directory on the node's
own disk (`localCachePath`) holds `staging/` before upload and one `resolve/` directory per
download; it is cleared at start-up.

The proxy does not sweep the bucket. Configure an **S3 lifecycle rule** on each prefix that
expires objects older than the longest a live group can legitimately wait — the same bound as
`orphanAge` on a shared mount.

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
| `type` | No | `LOCAL_FILESYSTEM` | `LOCAL_FILESYSTEM` (mode `LOCAL`), `SHARED_FILESYSTEM` or `S3` (mode `SHARED`) |
| `path` | No | Derived | Filesystem path (filesystem stores only) |
| `durability` | No | By type: `FULL` for local, `FILESYSTEM` for a shared mount | What `commit()` forces before returning; not used by S3 |
| `orphanAge` | For `SHARED_FILESYSTEM` | — | Anything under the store older than this is deleted by the hourly sweep. Must be at least twice the forward `maxRetryAge` and twice `aggregationFrequency`; validated |
| `region` | For S3 | — | AWS region |
| `bucket` | For S3 | — | S3 bucket name |
| `keyPrefix` | No | `{storeName}/` | S3 key prefix |
| `endpointOverride` | No | — | Override for S3-compatible stores |
| `credentialsType` | No | `default` | `default` (SDK chain — IRSA, container or instance role), `basic` (static keys, for S3-compatible endpoints), or `environment`. Anything else is a validation error. For a named AWS profile set `AWS_PROFILE` in the environment. |
| `accessKeyId` | If basic | — | AWS access key (basic credentials only) |
| `secretAccessKey` | If basic | — | AWS secret key (basic credentials only) |
| `localCachePath` | No | Derived | S3 only: node-local directory for staging and downloads |

---

## Configuration Reference

### Top-Level Structure

The pipeline is **always active** and its configuration is explicit or fail: the mode, every queue, every file store and every stage must be stated.

```yaml
pipeline:
  mode: LOCAL
  queues:
    splitZipInput:     { ... }
    aggregateInput:    { ... }
    forwardingInput:   { ... }

  fileStores:
    receiveStore:      { ... }
    splitStore:        { ... }
    aggregateStore:    { ... }

  stages:
    receive:      { ... }
    splitZip:     { ... }
    aggregate:    { ... }
    forward:      { ... }
```

### Stage Configuration

Each stage has the following fields:

| Field | Description |
|-------|-------------|
| `enabled` | Whether this stage runs in this process. Must be stated for every stage you list; a listed stage without it is refused |
| `inputQueue` | Logical name of the input queue (references a key in `queues`) |
| `outputQueue` | Logical name of the output queue |
| `splitZipQueue` | Logical name of the split-zip queue (receive stage only) |
| `fileStore` | Logical name of the file store for this stage's output |
| `threads.consumerThreads` | Number of worker threads consuming from the input queue (default: `1`). The aggregate stage also has `threads.mergeThreads`; for the forward stage this is the fan-out loop only, each destination's own `threads` driving its deliveries |
| `maxItemsPerAggregate`, `maxUncompressedByteSize`, `aggregationFrequency` | The aggregate stage's bounds; every one must be stated |

### A `stages` Block Must List All Four Stages

The `stages` block is required and **must list all four stages.** A stage
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

Disabling the receive stage has one further condition: `dirScanner.enabled` must
be `false` and no `sqsConnectors` may be configured on that node, or validation
refuses to start. Both hand what they find to the receiver, which on such a node
refuses everything; the scanner is enabled by default, so a worker node must
turn it off explicitly.

A stage you *do* list must say whether it is enabled: a listed stage without
`enabled` is a `STAGE_ENABLED_NOT_STATED` error, because a half-stated block is
ambiguous in the same way an omitted stage is.

### Receive concurrency

Receipt runs on its callers' threads and has no thread pool or semaphore of its
own: HTTP receives are bounded by the HTTP connector's thread pool, and the
directory scanner and the event-store forwarder are one thread each. The
`receive` stage therefore has no `threads` block.

---

## Deployment Examples

> Complete, ready-to-copy versions of these live in
> [deployments/](deployments/). Each is a full `proxyConfig` block that
> `TestDeploymentExamples` parses and passes through `ProxyPipelineConfigValidator` with no
> errors, except `split-stage-workers.yml`, which holds `stages` fragments to overlay on one of
> the others and is checked to parse only.
> The snippets below show the `pipeline` block alone, for reading.
>
> | Example | File |
> |---|---|
> | 1, 2 — single process | [single-process.yml](deployments/single-process.yml) |
> | 4 — SQS + S3 | [sqs-s3-distributed.yml](deployments/sqs-s3-distributed.yml) |
> | 6 — Kafka | [kafka-distributed.yml](deployments/kafka-distributed.yml) |
> | Per-stage worker processes | [split-stage-workers.yml](deployments/split-stage-workers.yml) |

> **Every example below is a complete, startable configuration in respect of the `pipeline` block —
> and that is not the whole of what a proxy needs to boot.** Two things bite in practice, and both
> used to be missing here:
>
> 1. **Every stage in a `stages` block must state `enabled`.** Listing four stages and leaving the
>    flag off one of them is `STAGE_ENABLED_NOT_STATED` and the proxy refuses to start. The examples
>    below now say `enabled: true` explicitly even where it is the default, because a half-stated
>    block is the error, not the omission of the block.
> 2. **`receiptCheckMode` defaults to `FEED_STATUS`, which needs a downstream to ask.** With no
>    `downstreamHost` configured there is nothing to ask, and the proxy refuses to start rather than
>    admit everything unchecked. Either point it at a downstream:
>
>    ```yaml
>    downstreamHost:
>      enabled: true
>      hostname: stroom.example.com
>    ```
>
>    or say plainly that this proxy does not check receipts:
>
>    ```yaml
>    receive:
>      receiptCheckMode: RECEIVE_ALL
>    ```
>
>    Both are legitimate; guessing between them is not, which is why there is no default that works
>    without one of them.

### Example 1: Simple Single-Process

The simplest deployment — all stages run in one process with local filesystem queues and stores.
The pipeline is **explicit or fail**: the mode, every queue, every file store and every stage must
be stated, and nothing is filled in for an omitted block. A proxy with no `pipeline` block, or with
one that omits any of them, refuses to start and lists what is missing. This is the whole block for
the single-process case:

```yaml
pipeline:
  mode: LOCAL
  queues:
    splitZipInput:     { type: LOCAL_FILESYSTEM }
    aggregateInput:    { type: LOCAL_FILESYSTEM }
    forwardingInput:   { type: LOCAL_FILESYSTEM }
  fileStores:
    receiveStore:      { type: LOCAL_FILESYSTEM }
    splitStore:        { type: LOCAL_FILESYSTEM }
    aggregateStore:    { type: LOCAL_FILESYSTEM }
  stages:
    receive:
      enabled: true
      outputQueue: aggregateInput
      splitZipQueue: splitZipInput
      fileStore: receiveStore
    splitZip:
      enabled: true
      inputQueue: splitZipInput
      outputQueue: aggregateInput
      fileStore: splitStore
    aggregate:
      enabled: true
      inputQueue: aggregateInput
      outputQueue: forwardingInput
      fileStore: aggregateStore
      maxItemsPerAggregate: 1000
      maxUncompressedByteSize: "1G"
      aggregationFrequency: PT10M
    forward:
      enabled: true
      inputQueue: forwardingInput
```

### Example 2: High-Throughput Single-Process

All stages in one process but with increased parallelism. The pipeline block is explicit or fail,
so the queues, stores and wiring are stated as in Example 1; only the thread counts differ:

```yaml
pipeline:
  mode: LOCAL
  queues:
    splitZipInput: { type: LOCAL_FILESYSTEM }
    aggregateInput: { type: LOCAL_FILESYSTEM }
    forwardingInput: { type: LOCAL_FILESYSTEM }
  fileStores:
    receiveStore: { type: LOCAL_FILESYSTEM }
    splitStore: { type: LOCAL_FILESYSTEM }
    aggregateStore: { type: LOCAL_FILESYSTEM }
  stages:
    receive:
      enabled: true
      fileStore: receiveStore
      outputQueue: aggregateInput
      splitZipQueue: splitZipInput
    splitZip:
      enabled: true
      inputQueue: splitZipInput
      outputQueue: aggregateInput
      fileStore: splitStore
      threads:
        consumerThreads: 4
    aggregate:
      enabled: true
      inputQueue: aggregateInput
      outputQueue: forwardingInput
      fileStore: aggregateStore
      maxItemsPerAggregate: 1000
      maxUncompressedByteSize: "1G"
      aggregationFrequency: PT10M
      threads:
        consumerThreads: 1
        mergeThreads: 2
    forward:
      enabled: true
      inputQueue: forwardingInput
forwardHttpDestinations:
  - name: downstream-stroom
    enabled: true
    forwardUrl: https://stroom.example.com/stroom/datafeed
    threads:
      consumerThreads: 8      # with one destination, the forward loop runs with the destination's threads
```

### Example 3: Skip Aggregation (Receive + Forward Only)

For proxies that only need to receive and forward without aggregation — disable the middle stages and route receive directly to forwarding:

```yaml
pipeline:
  mode: LOCAL
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
  mode: SHARED
  queues:
    splitZipInput:
      type: SQS
      queueUrl: https://sqs.eu-west-2.amazonaws.com/123/stroom-proxy-split
      visibilityTimeout: PT30M
    aggregateInput:
      type: SQS
      queueUrl: https://sqs.eu-west-2.amazonaws.com/123/stroom-proxy-aggregate
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
      outputQueue: aggregateInput
      splitZipQueue: splitZipInput
      fileStore: receiveStore
    splitZip:
      enabled: false
    aggregate:
      enabled: false
    forward:
      enabled: false
```

**Forward instance config** (consumes from SQS, forwards to downstream Stroom):

```yaml
pipeline:
  mode: SHARED
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
    aggregate:
      enabled: false
    forward:
      enabled: true
      inputQueue: forwardingInput
      threads:
        consumerThreads: 16
```

### Example 5: A Forward-Only Node

A node that only drains the shared forward queue. Every queue and store it uses is stated, and in
`SHARED` mode each must be shared; a local queue or store is refused. The give-up destination is
shared too, since this node may never be asked about a group again:

```yaml
dirScanner:
  enabled: false            # a node that does not receive must not scan a directory
pipeline:
  mode: SHARED
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
    aggregate:
      enabled: false
    forward:
      enabled: true
      inputQueue: forwardingInput
forwardHttpDestinations:
  - name: downstream-stroom
    enabled: true
    forwardUrl: https://stroom.example.com/stroom/datafeed
    failureDestination:
      type: S3
      s3Client:
        region: eu-west-2
        bucketName: stroom-proxy-give-up
        keyPattern: "downstream-stroom/${feed}/${year}${month}${day}/${uuid}.zip"
    threads:
      consumerThreads: 8
```

A mixture of local and shared queues is not a third mode: the validator refuses a local queue or
store in `SHARED` mode and a shared one in `LOCAL` mode.

### Example 6: Kafka-Based Distributed Pipeline

Using Kafka for all inter-stage communication. The complete, validated form, with its shared
file stores, stages and a shared give-up directory, is
[deployments/kafka-distributed.yml](deployments/kafka-distributed.yml); the queues are:

```yaml
pipeline:
  mode: SHARED
  queues:
    splitZipInput:
      type: KAFKA
      topic: stroom-proxy-split-zip
      bootstrapServers: kafka-1:9092,kafka-2:9092,kafka-3:9092
      consumer:
        group.id: stroom-proxy-split
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
  fileStores:
    # SHARED_FILESYSTEM stores must state their path on the mount every node sees
    receiveStore: { type: SHARED_FILESYSTEM, path: /mnt/proxy-shared/file-stores/receiveStore, orphanAge: P14D }
    splitStore: { type: SHARED_FILESYSTEM, path: /mnt/proxy-shared/file-stores/splitStore, orphanAge: P14D }
    aggregateStore: { type: SHARED_FILESYSTEM, path: /mnt/proxy-shared/file-stores/aggregateStore, orphanAge: P14D }
  stages:
    # as in Example 1
```

---

## Worker Thread Model

Each enabled stage that has an input queue is a loop in the proxy's `WorkRegistry`, running
`consumerThreads` `FileGroupQueueWorker` threads named `stage-<configName>-<n>`.

### Processing Loop

Each thread runs a continuous loop:

```
while running:
    outcome = worker.processNext()   // next() → process → acknowledge / fail
    if outcome is FAILED:
        consecutiveFailures++
        sleep(min(1s << (consecutiveFailures-1), 30s))   // Failure backoff
    else:
        consecutiveFailures = 0     // PROCESSED or NOTHING - loop immediately
  on any throwable:
    log, sleep(1s)                  // Error backoff; the thread never dies
```

There is no wait after an empty poll: the queue's `next()` is where the thread waits, so an idle
stage costs nothing beyond that.

**The failure backoff is what stops a stuck message becoming a hot loop.**
`item.fail()` puts the message straight back on the queue, so with no delay a
message that can never succeed is retried as fast as the thread can run and
burns a core. The forward stage adds its own, longer wait on top: after a
transient failure the destination backs off before its next attempt on any group.

The consecutive-failure count is per thread, not per message, so a stuck item is
retried about once per interval *per consumer thread*. Raising `consumerThreads`
therefore raises the retry rate for stuck items as well as the throughput for
healthy ones.

The delays are `WorkRegistry.Backoff.DEFAULT` — 1 s failure backoff doubling to 30 s, 1 s error
backoff — and are not exposed as configuration.

### Start, stop and health

Loops start in reverse data-flow order (forward first, the sources last) and stop in data-flow
order, so a stage has stopped feeding the next while the next is still draining. Each stop waits
up to 30 s for the loop's threads, then interrupts them and waits 5 s more; a thread still running
after that is logged at ERROR and abandoned. The `/healthcheck` endpoint lists every loop's
configured and live thread counts and whether it is paused, and is unhealthy if a started loop
has fewer live threads than configured.

### Thread Counts

Thread counts are independently configurable per queue-consuming stage via the `threads` block:

- `consumerThreads` controls how many workers poll the input queue concurrently
- the receive stage has none: receipt runs on the HTTP connector's threads

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

- **Downstream must tolerate duplicate receipt.** A redelivered message keeps its
  `messageId`; a stage re-run after a crash mints a new one, so there is no key
  that survives every duplicate.
- **Expect duplicates after any ungraceful stop** — `kill -9`, OOM kill, node
  loss, pod eviction — and after any transient processor failure that causes
  `item.fail()`.
- **A destination that is failing does not amplify them.** With multiple forward
  destinations each retries its own copy from its own queue; a destination that
  already accepted a group is not sent it again because another failed.

### 5. Crash Windows and What They Cost

The ownership ordering (write → publish → delete → acknowledge) is arranged so no
crash point loses data. Each has a different cost:

| Crash point | Result |
|---|---|
| Before output commit | Reprocessed cleanly; an uncommitted staging directory is left behind |
| After commit, before publish | Reprocessed; the first output is orphaned in the store |
| After publish, before delete | Reprocessed; **duplicate** output and onward message |
| After delete, before acknowledge | The redelivered message resolves to nothing and is acknowledged (R12): the output was committed and published before the input was deleted, so the absence is proof the work was done |

That last row needs no crash in shared mode: a visibility timeout or a Kafka
rebalance produces it in normal operation, and the `resolves to no data, so its
work was already done` line in the log is what it looks like.

### 6. Retry & Dead-Letter Handling

When a stage processor fails, the worker calls `item.fail()` which returns the message to the queue for retry. Each queue backend handles retries and dead-letter routing using its own native mechanisms.

#### Local Queues

A failed item is **re-published under a new id at the back of `pending/`**, with its delivery-attempt
count incremented — not moved back for immediate retry. Going to the back matters: an item that fails
repeatedly does not hold up everything behind it.

Once the count reaches `maxDeliveryAttempts` the item is **quarantined** instead, into `failed/` as
`<id>.max-delivery-attempts.<millis>.json`, with the error's stack trace beside it as
`.error.txt`. Give-up is bounded and lands somewhere a human can act on it, on every backend — the
same convention SQS and Kafka now use.

Items in `failed/` are never retried automatically. They hold a reference to a file group that is
still in its store, so each one needs a decision: re-inject it by moving the message back to
`pending/`, or delete it once the data has been dealt with by hand.

An item closed without being acknowledged or failed - which is what the worker does when
`acknowledge()` itself throws - is requeued the same way, with the attempt counted. An item is
claimed exactly while a consumer holds it; there is no lease bookkeeping and no reclaim scan.

The `failed/` directory count is included in the health check and the `stroom.proxy.pipeline.queue.<name>.failed` Prometheus metric.

#### SQS Queues

A failed item has its visibility timeout set to 0, making it immediately available to any consumer,
and SQS counts the receive. **Give-up is SQS's**: configure a **redrive policy** on each queue, whose
`maxReceiveCount` is the bound and whose dead-letter queue is where a human acts. The proxy neither
counts attempts nor writes anything to local disk for an SQS queue - a node may be scaled away with
its disk, so a quarantine there is somewhere no one else can find. `maxDeliveryAttempts` on an SQS
queue is a validation error.

```json
{
  "RedrivePolicy": {
    "deadLetterTargetArn": "arn:aws:sqs:eu-west-2:123456789012:stroom-proxy-dlq",
    "maxReceiveCount": 10080
  }
}
```

Size `maxReceiveCount` from the forward retry bounds - at least `maxRetryAge ÷ retryDelay`, which is
10,080 at the defaults of seven days and one minute - or the dead-letter queue fires before the
destination gives up ([forward.md §4.3](stages/forward.md#43-giving-up)). A failed message is made
visible at once and the back-off is served *after* the next receive, so a small count is minutes of
outage, not days.

After `maxReceiveCount` deliveries SQS moves the message to the dead-letter queue. Monitor the DLQ depth via CloudWatch or the SQS console, and treat a message there as one that needs a decision: the file group it names is still in its store **until the store's `orphanAge` or S3 lifecycle rule removes it**, so the decision has a deadline.

**Retention.** Set every pipeline queue's `MessageRetentionPeriod` above the longest forward
`maxRetryAge` - 14 days, the maximum, is the safe choice. SQS deletes a message that age after it
was sent regardless of visibility, and the proxy's retry on SQS never resends, so a shorter retention
lets SQS delete a message still being retried with nothing to say so. The proxy checks this, and the
presence of a redrive policy, when it builds each SQS queue and refuses to start if either is wrong.
A dead-letter queue keeps the original send time, so give it a longer retention still.

> **Tip:** Create one DLQ per pipeline queue (e.g. `splitZipInput-dlq`, `forwardingInput-dlq`) so that failed items from different stages don't get mixed together.

#### Kafka Queues

A partition is a sequence, so a record that cannot be processed must not block the ones behind it.
A failed item is **re-published to the tail of the topic** with the attempt count in a header, and
the original is committed past. After `maxDeliveryAttempts` the record is published to the topic's
**dead-letter topic, `<topic>.failed`**, and committed past; an undecodable record goes the same
way. The proxy creates the dead-letter topic at start-up if it is absent and refuses to start if it
can neither create nor find it. Nothing reads that topic; it exists so an operator can.

---

## Recovering Data the Proxy Has Given Up On

The proxy guarantees that data it cannot deliver lands somewhere a human can act on it, rather than
being dropped or retried forever. That guarantee is only worth having if the recovery is actually
performable, so this is what to do with each place data comes to rest, and the four things that will
bite you if you improvise.

### Where given-up data comes to rest

| Location | What is there | How it got there |
|---|---|---|
| `50_forwarding/<dest>/03_failure`, or the destination's `failureDestination` (a directory or an S3 bucket). In shared mode a directory holds one `<uuid>/` writer root per process start, and the groups are under those | Whole file groups: `proxy.zip`, `proxy.meta`, `proxy.entries`, `error.log` (on S3, the zip and `<key>.error.log`) | The destination refused the group, or it had been failing for longer than `maxRetryAge` |
| `<queue>/failed/` (local queue) | Queue **messages** as `<id>.<reason>.<millis>.json`, with `.error.txt` beside them | A queue message exceeded `maxDeliveryAttempts` |
| The SQS dead-letter queue | Queue messages | The queue's redrive policy moved them after `maxReceiveCount` receives |
| The Kafka topic `<topic>.failed` | Queue messages, with the attempt count header | The proxy gave up after `maxDeliveryAttempts`, or could not decode the record |
| `event/` → `event/failed/` | A single event file | The receiver refused it three times |
| `zip_file_ingest_failed/` | A scanned file and its sidecars | The directory scanner could not ingest it |

Nothing reads any of these. They exist so that an operator can.

### Re-injecting a failed file group

A group in a forward destination's give-up directory is replayed by moving it back into a
directory the **scanner** watches (`dirScanner`). Four things matter, and none of them is obvious:

**1. Move the zips only.** The per-entry `.meta` inside a proxy zip is authoritative for feed and
type — `cloneAndUpdateMetaEntry` writes it *on top* of the outer map, which is fallback-only — and
receive synthesises a `.meta` for any data entry that lacks one. So anything that reached
`03_failure` already carries its own attribution, entry by entry.

**2. The outer `proxy.meta` is therefore optional, and dropping it is usually right.** A group
re-ingested without one is given a fresh receipt id, which is appended to each entry's
`ReceiptIdPath` rather than replacing it — so the chain of custody grows rather than restarting, and
you can still see where the data originally came from.

**3. Every group's zip is named `proxy.zip`.** Move two groups into one flat directory and the second
clobbers the first. Preserve the directory structure — the scanner recurses — or rename each zip as
you go. This is the mistake that loses data during a recovery, and it looks like it worked.

**4. Copy `error.log` aside first.** It is the only record of *why* the group was given up on, how
many attempts were made and how old it was, and nothing else retains it. The scanner treats it as
part of the group and consumes it with the rest, so once you replay, the reason is gone.

### Expect duplicates, not gaps

Replay is **at-least-once by design**. The proxy is built so that at every power-loss point either
the input is still claimable or the output is durable, which means the failure mode it protects
against is a gap and the failure mode it accepts is a duplicate. If you replay a group that had in
fact been delivered, the downstream sees it twice. That is the intended trade, not a bug to work
around: deduplicate downstream if it matters, but do not let a fear of duplicates stop a recovery.

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
| `FilesystemFileStore` | Root and writer root exist and are writable | `root`, `type`, `writable` |
| `S3FileStore` | `headBucket` call + local staging/resolve directory writability | `bucket`, `keyPrefix`, `localStagingWritable` |

**Example healthy response (JSON excerpt):**

```json
{
  "PipelineHealthChecks": {
    "healthy": true,
    "message": null,
    "components": {
      "queue.splitZipInput.healthy": true,
      "queue.aggregateInput.healthy": true,
      "queue.forwardingInput.healthy": true,
      "fileStore.receiveStore.healthy": true,
      "fileStore.splitStore.healthy": true,
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

**Shared filesystem sweep** (per `SHARED_FILESYSTEM` store):

| Metric | Description |
|--------|-------------|
| `stroom.proxy.pipeline.fileStore.<name>.swept.groups` | Committed groups the sweep has deleted as older than `orphanAge`. Should stay near zero; a rate under load means the sweep is deleting live work, which is otherwise silent |
| `stroom.proxy.pipeline.fileStore.<name>.swept.staging` | Stale staging directories the sweep has deleted; always safe |

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

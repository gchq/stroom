# Pluggable Queue Implementation Plan

## Status

Proposed implementation plan.

This document describes a new Stroom Proxy queueing architecture where queues never physically move data. Instead, every queue implementation transports a small message that references data already written to a stage-defined `FileStore`.

This is intended to be a new flavour of the proxy codebase. The current atomic directory move based `DirQueue` model will be removed from this design rather than retained for backwards compatibility.

A secondary goal is to allow queue-separated stages, outside direct/simple/instant mode, to run as independently configured executables. This means one proxy process could perform only receive, another only pre-aggregation, another only aggregation, and another only forwarding, with queue messages forming the durable contract between them.

## Goals

1. Replace the current atomic filesystem move queue with a unified reference-message queue model.
2. Introduce queue implementations:
   - local filesystem message queue,
   - Kafka queue,
   - Kinesis queue,
   - SQS queue.
3. Ensure every queue implementation carries the same logical message contract:
   - message ID,
   - referenced file or directory location,
   - file-group identity,
   - producer/stage metadata,
   - trace/diagnostic metadata.
4. Ensure queue messages carry file-group references instead of file-group payloads.
5. Allow queue-separated stages to run in separate JVMs/processes.
6. Make queues configurable as named logical topics.
7. Allow stages to reference input and output queues by name.
8. Make each pipeline stage independently executable outside simple/instant mode.
9. Make `FileStore` the storage abstraction for stage-owned stable file locations.
10. Make local filesystem queueing use sequential message files with a global sequence file per queue.

## Non-goals

1. Do not move zip/file-group payloads into Kafka, Kinesis, SQS, or local filesystem queue messages.
2. Do not require all deployments to use remote queues.
3. Do not make simple/instant forwarding distributed; direct modes can remain direct.
4. Do not require Kafka, Kinesis, SQS, or a distributed filesystem for single-node deployments.
5. Do not redesign the proxy zip/file-group format in this work.

## Current design summary

The current queue implementation is centred on `DirQueue`.

Current properties:

- A queue item is a directory.
- `DirQueue.add(sourceDir)` atomically moves `sourceDir` into the queue's managed directory tree.
- `DirQueue.next()` blocks until a queued directory is available.
- `DirQueue.next()` returns a `Dir`.
- `Dir` exposes the queued `Path`.
- `Dir.close()` calls back into `DirQueue.close(...)` to clean up empty queue parent directories.
- `DirQueueTransfer` bridges a queue to a `Consumer<Path>`.

This provides an efficient local durable handoff model, but it couples queueing to physically moving the directory.

The new design deliberately removes this coupling. `DirQueue` is not retained as a target implementation. All queue implementations, including the local filesystem queue, will queue messages that reference file or directory locations in a `FileStore`.

## Desired future design

The desired future architecture separates two concepts:

1. **FileStore**
   - Where the file group actually lives.
   - Each stage defines its own `FileStore` location.
   - Examples:
     - local filesystem path,
     - shared filesystem path, e.g. NFS, CephFS/Ceph, or another cluster/shared filesystem,
     - mounted shared volume path,
     - object-store URI in a later extension.

2. **Queue item**
   - A durable message that refers to a file or directory location in a `FileStore`.
   - Examples:
     - local filesystem message file containing a path,
     - Kafka message containing a path,
     - Kinesis record containing a path,
     - SQS message containing a path.

All queue implementations enqueue messages that contain the location of already-complete data in a `FileStore`. They do not move the referenced data.

Each logical queue should be treated as a topic-like stream of work. Kafka maps naturally to a topic, Kinesis maps to a stream, SQS maps to a queue, and the local filesystem implementation maps to a directory of sequential message files. These logical queues/topics are defined in configuration and referenced by stages by name.

For external queues such as Kafka, Kinesis, or SQS, referenced locations must be on storage visible to every producer and consumer executable, normally a shared filesystem such as NFS, CephFS/Ceph, or an equivalent shared/cluster filesystem.

## Architectural principle

The queue abstraction should represent work scheduling, processing state, and acknowledgement state. It should not represent physical storage ownership by moving data.

Ownership of data location and retention belongs to the relevant `FileStore` and stage policy.

For all queue implementations, including local filesystem, Kafka, Kinesis, and SQS, work transfer is implemented by publishing a reference message and acknowledging that message after successful processing.

## Proposed queue abstraction

Introduce a small interface that publishes and consumes queue messages containing references to `FileStore` locations. The interface is intentionally independent of the queue transport so a named logical queue can be backed by Kafka, Kinesis, SQS, or local filesystem message files.

Suggested interface name:

- `FileGroupQueue`

Suggested shape:

~~~java
public interface FileGroupQueue {

    FileGroupQueueItem next();

    Optional<FileGroupQueueItem> next(long time, TimeUnit unit);

    void publish(FileGroupQueueMessage message);
}
~~~

Suggested item interface:

~~~java
public interface FileGroupQueueItem extends AutoCloseable {

    FileGroupQueueMessage getMessage();

    QueueItemId getId();

    QueueItemMetadata getMetadata();

    void acknowledge();

    void fail(Throwable error);

    @Override
    void close();
}
~~~

The canonical queue payload is the message. Stages must obtain referenced file or directory locations from the `FileGroupQueueMessage`.

## API transition

This is a new queueing model with no requirement to preserve `DirQueue` compatibility.

The queue API is message based:

~~~java
void publish(FileGroupQueueMessage message);
~~~

Stages must publish complete `FileGroupQueueMessage` instances containing the `FileStore` location, file-group identity, producer identity, trace IDs, and any routing or diagnostic metadata required by downstream stages.

## Recommended naming

Although `PathQueue` is generic, this queue is really intended to pass proxy file-group directories between pipeline stages.

Recommended final names:

| Concept | Recommended name |
| --- | --- |
| Queue interface | `FileGroupQueue` |
| Queue item/lease interface | `FileGroupQueueItem` |
| Queue message | `FileGroupQueueMessage` |
| Local filesystem message queue implementation | `LocalFileGroupQueue` |
| Kafka implementation | `KafkaFileGroupQueue` |
| SQS implementation | `SqsFileGroupQueue` |
| Kinesis implementation | `KinesisFileGroupQueue` |
| Queue factory | `FileGroupQueueFactory` |
| Transfer worker | `FileGroupQueueTransfer` |
| Stable data storage abstraction | `FileStore` |

The current `DirQueue`, `Dir`, `DirQueueFactory`, and `DirQueueTransfer` names are not part of the target design. They should be removed or replaced in this new flavour of the codebase.

## Queue implementation types

### 1. Local filesystem message queue

A local filesystem message queue stores small queue records in the filesystem. It does not move the referenced file group.

Producer:

- writes a file group or directory to a stage-defined `FileStore`,
- successfully completes the write,
- publishes a queue message containing the file or directory location.

Consumer:

- claims a queue message file,
- reads the referenced file or directory location,
- processes the referenced data,
- acknowledges or fails the queue message.

The local filesystem queue should use a global sequence file per queue. This is acceptable because this implementation is intended for the simple/local option that is usually backed by local disk.

Pros:

- same message/reference model as Kafka, Kinesis, and SQS,
- no external queue dependency,
- persisted across restarts using message files,
- simple local operational model,
- useful for single-node deployments and development.

Cons:

- local filesystem based,
- less suitable for high-scale distributed deployments than Kafka/Kinesis/SQS,
- needs explicit message ack/failure handling,
- needs strategy for stuck/in-flight records.

### 2. Kafka queue

Kafka messages contain file-group references.

Producer:

- writes a file group or directory to a `FileStore` location visible to the consumer stage,
- publishes a reference message to the configured Kafka topic.

Consumer:

- subscribes to topic,
- receives file-group reference,
- processes file group,
- commits offset after success.

Pros:

- strong fit for high-throughput distributed queueing,
- consumer groups allow independent horizontal scaling per stage,
- ordering can be controlled by partition key,
- good observability ecosystem.

Cons:

- consumers must have access to referenced files,
- offset commit semantics must be carefully matched to processing success,
- poison messages require retry/dead-letter strategy,
- exactly-once end-to-end behaviour is not automatic.

### 3. SQS queue

SQS messages contain file-group references.

Producer:

- writes a file group or directory to a `FileStore` location visible to the consumer stage,
- sends a reference message to SQS.

Consumer:

- receives message,
- processes file group,
- deletes message on success,
- relies on visibility timeout for retry.

Pros:

- simple managed queue,
- useful for distributed independent executables,
- visibility timeout provides natural retry behaviour.

Cons:

- message ordering only with FIFO queues and grouping constraints,
- at-least-once delivery requires idempotent processing,
- visibility timeout must exceed processing time or be extended,
- message size limits require references only.

### 4. Kinesis queue

Kinesis records contain file-group references.

Producer:

- writes a file group or directory to a `FileStore` location visible to the consumer stage,
- writes a reference record to Kinesis.

Consumer:

- reads shard records,
- checkpoints after processing success.

Pros:

- high-throughput ordered shard processing,
- useful where Kinesis is the standard event stream.

Cons:

- checkpoint and retry semantics are more complex,
- less naturally queue-like than SQS,
- consumers must handle replay and idempotency.

## Queue message contract

All non-moving queue implementations need a stable message contract.

Suggested logical fields:

| Field | Required | Purpose |
| --- | --- | --- |
| `schemaVersion` | yes | Allows message evolution. |
| `queueName` | yes | Logical queue name, e.g. `preAggregateInput`. |
| `stage` | yes | Producing stage name. |
| `itemId` | yes | Unique queue item ID. |
| `location` | yes | Absolute path or URI to the file or directory in a stage-defined `FileStore`. |
| `fileGroupId` | yes | Stable ID for idempotency. |
| `createdTime` | yes | Message creation time. |
| `proxyId` | yes | Producing proxy ID. |
| `attempt` | no | Attempt number, if implementation exposes it. |
| `attributes` | no | Small diagnostic/routing attributes, not full metadata. |
| `checksum` | no | Optional verification of message/file reference. |
| `traceId` | no | Correlation ID for logs/traces. |

Important rule:

- The queue message must not be the source of truth for the file-group contents.
- The source of truth remains the file or directory at `location` in the relevant `FileStore`.

## Future queue routing and partitioning

The initial pluggable queue architecture should establish a common queue message contract and common queue implementations. A later change should extend `FileGroupQueueMessage` with explicit routing and partitioning metadata.

This is particularly important for aggregation. For example, a Kafka-backed pre-aggregation queue should partition records so that similar items, such as the same feed/type combination, are consumed by the same pre-aggregation worker where possible. This improves aggregate size, compression, and batching efficiency, and reduces the need for distributed locking around open aggregate state.

Routing information should be part of the queue message, not hidden inside the physical storage location. The `FileStoreLocation` should describe where data is stored. Queue routing should describe how work should be distributed.

A future routing model should include fields such as:

| Field | Purpose |
| --- | --- |
| `partitionKey` | Queue implementation key used for Kafka record key, Kinesis partition key, SQS FIFO message group ID, etc. |
| `groupingKey` | Logical grouping key for related work. |
| `aggregationKey` | Stage-specific key used to group items for aggregation, normally based on feed/type. |
| `routingStrategy` | Strategy used to construct the key, e.g. `FEED_TYPE`, `FEED_TYPE_SHARDED`, `CUSTOM`. |
| `hashVersion` | Version of the key/hash algorithm for future compatibility. |
| `components` | Optional structured key parts, e.g. feed, type, shard, tenant. |

Queue implementations should use this metadata where the underlying transport supports it:

| Queue implementation | Routing use |
| --- | --- |
| Kafka | Use `partitionKey` as the Kafka record key. |
| Kinesis | Use `partitionKey` as the Kinesis partition key. |
| SQS FIFO | Use `partitionKey` or `groupingKey` as the message group ID. |
| SQS standard | Store routing metadata as message attributes; strict grouping is not guaranteed. |
| Local filesystem | Store routing metadata in the message; initial implementation may ignore it. |

Routing is deliberately deferred until after the initial pluggable queue architecture is in place.

## Path and storage assumptions

All queue implementations only carry references. This means producers and consumers must agree on how to resolve those references.

For local filesystem queue deployments, the referenced `FileStore` locations may be on local disk.

For independently executable distributed deployments using Kafka, Kinesis, or SQS, referenced locations must point to a shared filesystem, for example NFS, CephFS/Ceph, or another shared filesystem mounted consistently by all participating stage executables.

Supported reference types should be explicit:

| Reference type | Example | Notes |
| --- | --- | --- |
| local path | `/data/proxy/40_forwarding_input_queue/...` | Only valid for same host or shared mount. |
| shared filesystem path | `/mnt/proxy-shared/...` | Required for external queue deployments where separate executables exchange file or directory references; expected to be backed by NFS, CephFS/Ceph, or equivalent shared filesystem storage. |
| URI | `file:///mnt/proxy-shared/...` | More explicit form of shared path. |
| future object URI | `s3://bucket/key` | Not part of initial implementation unless explicitly added. |

Initial implementation should require `file` paths only. When those paths are used with Kafka, Kinesis, SQS, or any other external queue, they must refer to a shared filesystem location, not a process-local disk path. Object storage can be added later by extending the `FileStore` abstraction.

## Required file-group stability rule

Producers must not enqueue paths under temporary receive/work directories that may later be deleted or moved before the consumer runs.

Before publishing a queue message, the producer must ensure the file or directory has been successfully written to the relevant stage-defined `FileStore`.

The act of publishing the `FileStore` location to the queue is the finalisation/handoff point for the next stage. There is no separate queue-level finalisation step. A stage may still use temporary files or directories internally while writing, but it must only publish the final `FileStore` location after the write has completed successfully.

A separate completion marker or manifest can be added later if it proves useful for diagnostics, recovery, or validation, but it is not required for the initial design. The initial contract is simple: if a location is present in a queue message then the producing stage has successfully written it and considers it ready for consumption.

The queue does not define or own the stable storage location. Each stage defines its own `FileStore` location for its outputs.

## Proposed `FileStore` abstraction

Introduce `FileStore`.

Purpose:

- Provide stable locations for files and file groups before queue publication.
- Decouple "where data lives" from "how work is queued".
- Allow each stage to define its own output storage location.
- Allow storage layout to vary by stage without affecting queue implementations.

Suggested interface:

~~~java
public interface FileStore {

    FileStoreWrite createWrite(String purpose);

    Path completeWrite(FileStoreWrite write);

    Path resolve(FileStoreLocation location);

    void delete(FileStoreLocation location);
}
~~~

The exact storage layout is a `FileStore` concern. It may use:

- UUID based directories,
- a UUID writer root plus sequential IDs,
- sharding for filesystem performance,
- different root directories per stage or purpose.

There is no single shared stable directory prefix in this design. Each stage defines the `FileStore` location or locations it needs.

This is important because Kafka/SQS/Kinesis queues cannot safely point at a temporary directory that the producer will clean up.

## Future `FileStoreLocation` and object store support

The initial implementation can be filesystem-backed, but the queue message contract should avoid permanently coupling the design to `java.nio.file.Path`.

A future change should make `FileStoreLocation` the canonical storage reference in queue messages and `FileStore` APIs. A filesystem-backed `FileStore` can resolve a `FileStoreLocation` to a local or shared filesystem path, but object-store-backed implementations such as S3, Azure Blob Storage, or Google Cloud Storage would resolve the same logical reference differently.

A future `FileStoreLocation` should be able to represent:

| Storage type | Example |
| --- | --- |
| Local filesystem | `file:///data/proxy/store/receive/...` |
| Shared filesystem | `file:///mnt/proxy-shared/store/receive/...` |
| S3 | `s3://proxy-bucket/store/receive/...` |
| Future object stores | Provider-specific URI or structured location. |

The location should include enough information to identify the configured store as well as the object or file within it. For example:

| Field | Purpose |
| --- | --- |
| `storeName` | Name of the configured `FileStore`, allowing credentials and implementation details to remain in config. |
| `uri` | URI or provider-specific reference to the file, directory, prefix, or object group. |
| `locationType` | Optional type hint, e.g. `FILE`, `DIRECTORY`, `OBJECT_PREFIX`, `OBJECT`. |
| `attributes` | Optional provider-specific attributes. |

Object store support is not part of the initial pluggable queue implementation. The initial implementation may use filesystem paths internally, but the design should evolve toward `FileStoreLocation` as the queue message reference type so object stores can be added later without changing the queue abstraction.

## Queue semantics

### Unified reference-message semantics

For all queue implementations:

- `publish(message)` adds a reference message to the queue.
- `next()` returns a lease/message wrapper.
- `acknowledge()` confirms successful processing.
- `fail(error)` should make the item available for retry, dead-letter it, or leave it unacknowledged depending on implementation.

The transfer worker should be responsible for explicit ack/fail.

Equivalent future behaviour should be:

~~~java
FileGroupQueueItem item = null;
try {
    item = queue.next();
    consumer.accept(item.getMessage());
    item.acknowledge();
} catch (Exception e) {
    if (item != null) {
        item.fail(e);
    }
    throw e;
} finally {
    if (item != null) {
        item.close();
    }
}
~~~

## Proposed transfer worker design

Replace or evolve `DirQueueTransfer` into `FileGroupQueueTransfer`.

Current responsibility:

- get item,
- call consumer,
- close item.

Future responsibility:

- get queue item,
- call consumer,
- acknowledge on success,
- fail on exception,
- close resources.

Suggested behaviour:

1. `item = queue.next()`
2. `consumer.accept(item.getMessage())`
3. `item.acknowledge()`
4. `item.close()`

On error:

1. log context-rich error,
2. call `item.fail(error)`,
3. call `item.close()`,
4. rethrow or swallow according to worker policy.

This gives Kafka/SQS/Kinesis implementations a clear hook for commit/delete/checkpoint/failure.

## Acknowledgement mapping

| Queue implementation | `acknowledge()` | `fail(error)` |
| --- | --- | --- |
| local filesystem message queue | delete/mark queue message processed | release record, increment attempts, move to failed/in-flight-expired area |
| Kafka | commit offset after processing | do not commit, or publish to retry/dead-letter topic according to policy |
| SQS | delete message | do not delete; optionally change visibility or send to DLQ |
| Kinesis | checkpoint after processing | do not checkpoint; optionally publish failure record |

## Idempotency requirements

Remote/reference queues will normally be at-least-once. Therefore each stage must tolerate duplicate messages.

Required idempotency rules:

1. A file group should have a stable `fileGroupId`.
2. A stage output should be deterministic or collision-safe for a given input ID.
3. A stage should detect already-processed inputs where possible.
4. Cleanup should not delete input until ownership/output success is clear.
5. Forwarding should preserve or generate idempotency/receipt identifiers downstream where possible.
6. Failure handling should be safe if the same message is delivered more than once.

Suggested `fileGroupId` options:

- hash of stable path plus creation timestamp,
- UUID written as sidecar file when file group is created,
- existing receipt ID where available,
- queue-generated ID for generated aggregates.

Best option:

- write a small sidecar identity file into each file group when it is first created.
- propagate source IDs into aggregate outputs.

## Stage independence goal

Outside simple/instant mode, each component between queues should be able to run independently as a differently configured executable.

This implies splitting the monolithic in-process runtime topology into independently configurable stages.

## Proposed stage enablement and thread configuration

Do not use a single runtime mode enum as the primary runtime selector. Instead, each stage should have its own configuration containing:

- `enabled`,
- input queue references,
- output queue references,
- `FileStore` references where the stage writes data,
- stage-specific thread configuration,
- stage-specific processing configuration.

This allows a node to run any useful combination of stages, for example:

| Node shape | Enabled stages |
| --- | --- |
| all-in-one local node | receive, split, pre-aggregate, aggregate, forward |
| receive-only node | receive |
| ingress node | receive, directory scan, SQS ingest, event ingest |
| split worker | split zip |
| pre-aggregation worker | pre-aggregate |
| combined aggregation worker | pre-aggregate, aggregate |
| forwarding node | forward |
| diagnostics node | no processing stages, admin/status only |

Thread configuration should be owned by each stage rather than being global. A queue-consuming stage should normally have at least `consumerThreads`. Stages with additional scheduled or internal work may have further settings, for example:

| Stage | Example thread settings |
| --- | --- |
| receive | `maxConcurrentReceives`, `publishThreads` if publishing is decoupled |
| split zip | `consumerThreads`, optional `maxInFlightBytes` |
| pre-aggregate | `consumerThreads`, `closeOldAggregatesThreads` |
| aggregate | `consumerThreads`, optional `maxInFlightBytes` |
| forward | `consumerThreads`, plus destination-specific send/retry threads |

Queue transport configuration may also contain queue-specific polling or batching settings, for example Kafka `maxPollRecords`, SQS `maxMessagesPerPoll`, Kinesis `maxRecords`, or local filesystem scan settings. These transport settings are separate from stage processing thread counts.

Pre-aggregation thread configuration must be compatible with future routing and aggregation-key ownership. In particular, concurrent processing must not corrupt open aggregate state for the same aggregation key.

## Stage input/output contracts

Each independent stage needs explicit input and output queue config.

### Receive stage

Inputs:

- HTTP datafeed,
- event API,
- optional directory scanner,
- optional SQS/event input.

Outputs:

- split zip queue, when a zip needs splitting,
- pre-aggregate input queue, if aggregation enabled,
- forwarding input queue, if aggregation disabled.

### Split zip stage

Input:

- split zip input queue.

Outputs:

- pre-aggregate input queue, if aggregation enabled,
- forwarding input queue, if aggregation disabled.

### Pre-aggregate stage

Input:

- pre-aggregate input queue.

Outputs:

- aggregate input queue.

Local/stable working storage:

- open aggregates store,
- splitting work area,
- split output area.

### Aggregate stage

Input:

- aggregate input queue.

Outputs:

- forwarding input queue.

Local/stable working storage:

- aggregate creation work area.

### Forward stage

Input:

- forwarding input queue.

Outputs:

- destination-specific forward/retry/failure handling,
- HTTP destination,
- file destination.

## Stage executable design

The existing `App` can remain the entry point, but runtime startup should be conditional on per-stage `enabled` flags.

Current startup broadly creates everything.

Future startup should create:

1. shared services required by the configured node,
2. only servlets/resources required by enabled ingress stages,
3. only queue consumers required by enabled consumer stages,
4. only scheduled tasks required by enabled stages,
5. only destinations required by enabled forwarding stages.

This implies moving pipeline assembly out of `ReceiverFactoryProvider` into a more explicit topology builder.

## Proposed topology builder

Introduce:

- `ProxyPipelineTopology`
- `ProxyPipelineTopologyBuilder`
- `PipelineStage`
- `PipelineEdge`
- `QueueDefinition`

Responsibilities:

### `ProxyPipelineTopologyBuilder`

- reads `ProxyConfig`,
- reads configured stage enablement and thread settings,
- validates enabled queues and stages,
- creates queue definitions,
- creates stage definitions,
- wires producers and consumers.

### `PipelineStage`

Fields:

- stage name,
- enabled,
- input queues,
- output queues,
- stage thread config,
- stage-specific config.

### `PipelineEdge`

Fields:

- logical edge name,
- producer stage,
- consumer stage,
- queue definition,
- queue and `FileStore` policy.

### `QueueDefinition`

Fields:

- logical queue name,
- queue type,
- queue implementation config,
- message schema version,
- local fallback path,
- monitor name/order.

## Config model

Add a top-level queue/pipeline config to `ProxyConfig`.

Queues are defined separately as named logical queues/topics, then stages reference those queues by name for their inputs and outputs. This keeps queue transport configuration independent from stage behaviour. For example, the `preAggregateInput` logical queue may be backed by a local filesystem message queue in a single-node deployment, a Kafka topic in a distributed deployment, an SQS queue, or a Kinesis stream.

Suggested structure:

~~~yaml
proxy:
  pipeline:
    queues:
      splitZipInput:
        type: LOCAL_FILESYSTEM
        path: "${stroom.proxy.data}/queues/splitZipInput"

      preAggregateInput:
        type: LOCAL_FILESYSTEM
        path: "${stroom.proxy.data}/queues/preAggregateInput"

      aggregateInput:
        type: LOCAL_FILESYSTEM
        path: "${stroom.proxy.data}/queues/aggregateInput"

      forwardingInput:
        type: LOCAL_FILESYSTEM
        path: "${stroom.proxy.data}/queues/forwardingInput"

    stages:
      receive:
        enabled: true
        outputQueue: preAggregateInput
        splitZipQueue: splitZipInput
        fileStore: receiveStore
        threads:
          maxConcurrentReceives: 50

      splitZip:
        enabled: true
        inputQueue: splitZipInput
        outputQueue: preAggregateInput
        fileStore: splitStore
        threads:
          consumerThreads: 1

      preAggregate:
        enabled: true
        inputQueue: preAggregateInput
        outputQueue: aggregateInput
        fileStore: preAggregateStore
        threads:
          consumerThreads: 2
          closeOldAggregatesThreads: 1

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
          consumerThreads: 4

    fileStores:
      receiveStore:
        path: "${stroom.proxy.data}/store/receive"
      splitStore:
        path: "${stroom.proxy.data}/store/split"
      preAggregateStore:
        path: "${stroom.proxy.data}/store/preAggregate"
      aggregateStore:
        path: "${stroom.proxy.data}/store/aggregate"
~~~

For Kafka, the configured queue name maps to a Kafka topic and the referenced `fileStore` path must be on a shared filesystem, such as NFS or CephFS/Ceph, mounted by both the producing and consuming stage executables:

~~~yaml
proxy:
  pipeline:
    queues:
      preAggregateInput:
        type: KAFKA
        topic: "proxy.pre-aggregate-input"
        bootstrapServers: "kafka-1:9092,kafka-2:9092"
        producer:
          acks: all
        consumer:
          groupId: "proxy-pre-aggregate"
    stages:
      receive:
        enabled: true
        outputQueue: preAggregateInput
        fileStore: receiveStore
        threads:
          maxConcurrentReceives: 50

    fileStores:
      receiveStore:
        path: "/mnt/proxy-shared/store/receive"
~~~

For SQS, the configured queue name maps to an SQS queue and the referenced `fileStore` path must also be on a shared filesystem, such as NFS or CephFS/Ceph, because the SQS message only contains the file-group location:

~~~yaml
proxy:
  pipeline:
    queues:
      aggregateInput:
        type: SQS
        queueUrl: "https://sqs.eu-west-2.amazonaws.com/123456789012/proxy-aggregate-input"
        visibilityTimeout: PT30M
        waitTime: PT20S
    stages:
      aggregate:
        enabled: true
        inputQueue: aggregateInput
        outputQueue: forwardingInput
        fileStore: aggregateStore
        threads:
          consumerThreads: 2

    fileStores:
      aggregateStore:
        path: "/mnt/proxy-shared/store/aggregate"
~~~

For Kinesis, the configured queue name maps to a Kinesis stream and the referenced `fileStore` path must also be on a shared filesystem, such as NFS or CephFS/Ceph, because the Kinesis record only contains the file-group location:

~~~yaml
proxy:
  pipeline:
    queues:
      forwardingInput:
        type: KINESIS
        streamName: "proxy-forwarding-input"
        applicationName: "proxy-forwarder"
    stages:
      forward:
        enabled: true
        inputQueue: forwardingInput
        threads:
          consumerThreads: 4

    fileStores:
      forwardingStore:
        path: "/mnt/proxy-shared/store/forwarding"
~~~

## Queue type enum

Suggested queue types:

| Type | Meaning |
| --- | --- |
| `LOCAL_FILESYSTEM` | Local filesystem queue containing sequential path-reference message files. |
| `KAFKA` | Kafka topic containing path-reference messages. |
| `SQS` | SQS queue containing path-reference messages. |
| `KINESIS` | Kinesis stream containing path-reference records. |

## Config compatibility

This is a new proxy flavour with no requirement for backwards compatibility with the current `DirQueue` configuration.

If no explicit queue config is supplied, the new default should be:

- use `LOCAL_FILESYSTEM`,
- define local filesystem queues under the proxy data directory,
- define stage `FileStore` paths under the proxy data directory,
- enable the standard all-in-one set of stages.

The default should still be easy to run locally, but it should use the same reference-message queue model as Kafka, SQS, and Kinesis.

## Required validation

Add config validation for:

1. Queue type is supported.
2. Required fields for queue type are present.
3. Each stage references configured input and output queues by name.
4. Each stage that writes data references a configured `FileStore` by name.
5. Kafka/Kinesis/SQS-backed stages use `FileStore` paths on a shared filesystem, e.g. NFS, CephFS/Ceph, or equivalent shared storage visible to all relevant stage executables.
6. Each enabled stage has all required input/output queues.
7. Enabled receive stages cannot output to a queue that is not configured.
8. Enabled split zip stages require `splitZipInput` and a configured output queue.
9. Enabled pre-aggregate stages require `preAggregateInput` and `aggregateInput`.
10. Enabled aggregate stages require `aggregateInput` and `forwardingInput`.
11. Enabled forward stages require `forwardingInput` and at least one enabled destination.
12. Instant forwarding is not compatible with distributed queue-backed stage combinations.
13. Referenced `FileStore` paths are valid for the running process.
14. Kafka/Kinesis/SQS dependencies/config are present when selected.
15. Queue names/topics are unique where required.
16. Consumer group/application names are set for remote queue consumers.
17. Visibility timeout/checkpoint/retry settings are compatible with stage processing times.
18. Enabled queue-consuming stages have valid thread configuration, e.g. `consumerThreads >= 1`.
19. Queue transport polling/batching settings are compatible with stage thread settings.
20. Pre-aggregation thread settings are compatible with aggregation-key locking or future routing constraints.

## Independent executable examples

### Example 1: all-in-one local deployment

One process:

- queue type: `LOCAL_FILESYSTEM`
- enabled stages:
  - receive,
  - split zip,
  - pre-aggregate,
  - aggregate,
  - forward.

This is the simple all-in-one deployment shape, but using the new `FileStore` plus reference-message queue model rather than `DirQueue`.

### Example 2: receive and forward separated by Kafka

Process A:

- enabled stages:
  - receive
- output queue:
  - `forwardingInput`
  - `KAFKA`
- `FileStore`:
  - shared filesystem path, e.g. NFS or CephFS/Ceph

Process B:

- enabled stages:
  - forward
- input queue:
  - `forwardingInput`
  - same Kafka topic
- same shared filesystem mount

### Example 3: combined pre-aggregate and aggregate worker

Process A:

- enabled stages:
  - pre-aggregate,
  - aggregate
- consumes `preAggregateInput`
- publishes `aggregateInput`
- consumes `aggregateInput`
- publishes `forwardingInput`

### Example 4: full separated aggregation chain

Process A:

- enabled stages:
  - receive
- outputs `preAggregateInput`

Process B:

- enabled stages:
  - pre-aggregate
- consumes `preAggregateInput`
- outputs `aggregateInput`

Process C:

- enabled stages:
  - aggregate
- consumes `aggregateInput`
- outputs `forwardingInput`

Process D:

- enabled stages:
  - forward
- consumes `forwardingInput`
- sends to HTTP/file destinations

Each edge can use:

- Kafka,
- SQS,
- Kinesis,
- filesystem reference queue,
- filesystem move queue if shared filesystem queue directories are used.

## Topology diagram

~~~plantuml
@startuml
title Pluggable queue topology

package "Stage executable: RECEIVE" {
  [ReceiveDataServlet]
  [ProxyRequestHandler]
  [SimpleReceiver]
  [ZipReceiver]
}

package "Stage executable: PRE_AGGREGATE" {
  [PreAggregator]
}

package "Stage executable: AGGREGATE" {
  [Aggregator]
}

package "Stage executable: FORWARD" {
  [Forwarder]
  [RetryingForwardDestination]
  [HTTP/File destination]
}

database "Stage-defined FileStore(s)" as Store

queue "preAggregateInput\nFileGroupQueue" as Q1
queue "aggregateInput\nFileGroupQueue" as Q2
queue "forwardingInput\nFileGroupQueue" as Q3

[SimpleReceiver] --> Store : write complete file group
[ZipReceiver] --> Store : write complete file group
[SimpleReceiver] --> Q1 : enqueue path reference
[ZipReceiver] --> Q1 : enqueue path reference

Q1 --> [PreAggregator] : dequeue path reference
[PreAggregator] --> Store : create/update closed aggregate
[PreAggregator] --> Q2 : enqueue closed aggregate path

Q2 --> [Aggregator] : dequeue path reference
[Aggregator] --> Store : create forwardable file group
[Aggregator] --> Q3 : enqueue file-group path

Q3 --> [Forwarder] : dequeue path reference
[Forwarder] --> [RetryingForwardDestination]
[RetryingForwardDestination] --> [HTTP/File destination]

note bottom of Q1
Queue implementation may be:
- LOCAL_FILESYSTEM
- KAFKA
- SQS
- KINESIS
end note

@enduml
~~~

## Implementation phases

## Phase 1: Introduce `FileStore`

### Objective

Introduce the storage abstraction used by all stages before queue publication.

### Tasks

1. Add `FileStore` interface.
2. Add `FileStoreLocation` value type.
3. Add `FileStoreWrite` or equivalent write handle.
4. Add local filesystem `FileStore` implementation.
5. Support stage-defined store paths.
6. Support a storage layout based on:
   - UUID writer roots,
   - sequential IDs,
   - or a combination of UUID root plus sequential IDs.
7. Ensure data is only published to a queue after it has been successfully written.

### Expected result

Stages can write complete files/file groups to a stable stage-defined `FileStore` and obtain a location suitable for queue publication.

## Phase 2: Define queue message contract

### Objective

Create the universal message format used by local filesystem, Kafka, SQS, and Kinesis queues.

### Tasks

1. Add `FileGroupQueueMessage`.
2. Add message schema versioning.
3. Include:
   - message ID,
   - queue name,
   - file-group ID,
   - `FileStore` location/path,
   - producing stage,
   - producer ID,
   - created time,
   - trace ID,
   - optional routing/diagnostic attributes.
4. Add JSON serialisation/deserialisation.
5. Add validation for required fields.

### Expected result

All queue implementations use the same reference-message contract.

## Phase 3: Introduce queue definitions and factory

### Objective

Make queues configurable by logical queue name and implementation type.

### Tasks

1. Add `QueueType` enum.
2. Add `QueueDefinition` config class.
3. Add `ProxyPipelineConfig` or similar top-level config holder.
4. Add `FileGroupQueueFactory`.
5. Add named queue/topic definitions.
6. Add stage config that references input/output queues by name.
7. Default all queues to `LOCAL_FILESYSTEM`.
8. Update pipeline assembly to request logical queues from the new factory.

### Expected result

The topology uses named reference-message queues, and queue construction is driven by config.

## Phase 4: Local filesystem message queue

### Objective

Implement the local/simple queue option using the same reference-message contract as external queues.

### Tasks

1. Implement `LocalFileGroupQueue`.
2. Store each queue item as a message file containing queue item JSON with a `FileStore` location reference.
3. Use a global sequence file per named local filesystem queue to allocate sequential message numbers. This is deliberately chosen for the simple/local implementation where the queue is usually backed by local disk.
4. Add message directories:
   - pending,
   - in-flight,
   - failed/dead-letter or retry.
5. Implement `acknowledge()` by deleting or marking the message complete.
6. Implement `fail(error)` by making the item visible again or moving to failure based on retry policy.
7. Add metrics for:
   - write sequence,
   - consumed count,
   - ack count,
   - failed count,
   - approximate queue depth,
   - oldest pending item age.
8. Add tests for:
   - enqueue does not move source data,
   - consumer gets original `FileStore` location,
   - ack removes queue record,
   - failure retries or records failure,
   - restart recovery.

### Expected result

The local queue implementation uses persisted message files, not moved data directories.

## Phase 5: Stage enablement, thread configuration, and topology builder

### Objective

Allow individual stages, or arbitrary combinations of stages, to run independently with stage-specific thread configuration.

### Tasks

1. Add per-stage `enabled` configuration.
2. Add per-stage thread configuration, including common `consumerThreads` for queue-consuming stages.
3. Add stage-specific thread settings where needed, e.g. receive concurrency, pre-aggregate close-old-aggregate threads, or forwarding consumer threads.
4. Add queue transport polling/batching configuration separately from stage thread configuration.
5. Add `ProxyPipelineTopologyBuilder`.
6. Move queue/stage wiring out of `ReceiverFactoryProvider` where practical.
7. Update `ProxyLifecycle` to start only enabled stage services.
8. Update servlet/resource registration so only appropriate ingress endpoints are enabled by enabled stages.
9. Add validation for required queues, `FileStore`s, and thread settings for enabled stages.
10. Add validation that pre-aggregation thread settings are compatible with aggregation-key locking or future routing constraints.

### Expected result

A proxy process can be configured to run all, some, or one of the receive, split zip, pre-aggregate, aggregate, and forward stages with stage-specific concurrency settings.

## Phase 6: Kafka queue

### Objective

Add Kafka as a queue implementation.

### Tasks

1. Add Kafka queue config:
   - bootstrap servers,
   - topic,
   - producer properties,
   - consumer properties,
   - consumer group,
   - partition key strategy.
2. Implement producer:
   - serialise queue message JSON,
   - key by feed/type/fileGroupId where useful.
3. Implement consumer:
   - poll records,
   - expose records as `FileGroupQueueItem`,
   - commit offset on `acknowledge()`.
4. Implement failure strategy:
   - do not commit and allow retry, or
   - publish to retry/dead-letter topic.
5. Add metrics:
   - records produced,
   - records consumed,
   - commit lag if available,
   - failures.
6. Add integration tests with embedded/test Kafka if the project already supports it, otherwise isolate behind test fixtures.

### Expected result

Stages can communicate through Kafka by passing `FileStore` location messages.

## Phase 7: SQS queue

### Objective

Add SQS as a queue implementation.

### Tasks

1. Add SQS queue config:
   - queue URL,
   - region,
   - credentials/provider config if required,
   - wait time,
   - visibility timeout,
   - max messages.
2. Implement producer:
   - send queue message JSON.
3. Implement consumer:
   - long poll,
   - expose message as `FileGroupQueueItem`.
4. Implement `acknowledge()`:
   - delete SQS message.
5. Implement `fail(error)`:
   - leave message for visibility timeout,
   - optionally change visibility,
   - optionally send to failure queue.
6. Add visibility extension for long-running stages.
7. Add metrics.

### Expected result

Stages can communicate through SQS by passing `FileStore` location messages.

## Phase 8: Kinesis queue

### Objective

Add Kinesis as a queue implementation.

### Tasks

1. Add Kinesis config:
   - stream name,
   - application name,
   - region,
   - initial position,
   - checkpoint policy.
2. Implement producer:
   - put record with queue message JSON.
3. Implement consumer:
   - use Kinesis client/consumer library appropriate to existing dependencies.
4. Implement `acknowledge()`:
   - checkpoint after successful processing.
5. Implement `fail(error)`:
   - do not checkpoint,
   - optionally write failure event.
6. Add metrics:
   - records produced,
   - records consumed,
   - iterator age,
   - checkpoint failures.

### Expected result

Stages can communicate through Kinesis by passing `FileStore` location records.

## Phase 9: Monitoring and operations

### Objective

Expose queue state consistently across implementations.

### Tasks

1. Generalise `QueueMonitor` to support non-position-based queues.
2. Add fields:
   - implementation type,
   - logical queue name,
   - produced count,
   - consumed count,
   - acknowledged count,
   - failed count,
   - approximate lag/depth where available,
   - last error,
   - last produced/consumed timestamp.
3. Extend `ProxyQueueMonitoringServlet`.
4. Add logs for queue item lifecycle:
   - produce,
   - consume,
   - ack,
   - fail,
   - dead-letter.
5. Add health checks for configured external queues.
6. Add validation/status endpoint showing selected topology.

### Expected result

Operators can see both local and remote queue state from the proxy.

## Phase 10: Hardening

### Objective

Prepare for production use.

### Tasks

1. Add contract tests that every queue implementation must pass.
3. Add duplicate delivery tests for each stage.
4. Add crash/restart tests:
   - producer crashes after writing file group before queue publish,
   - producer crashes after queue publish,
   - consumer crashes before ack,
   - consumer crashes after output but before ack,
   - consumer crashes after ack.
5. Document operational deployment patterns.
6. Document storage requirements for independent executables.
7. Document queue-specific tuning.
8. Add deployment guide for local filesystem, Kafka, SQS, and Kinesis queue modes.
9. Add warnings for unsafe configs, e.g. external queue with non-shared local path.

## Future phase: queue routing and partitioning

### Objective

Add explicit routing metadata to `FileGroupQueueMessage` so queue implementations can partition or group related work efficiently.

This phase should be implemented after the initial pluggable queue architecture is working.

### Tasks

1. Add `QueueRouting` or equivalent to `FileGroupQueueMessage`.
2. Add fields for:
   - partition key,
   - grouping key,
   - aggregation key,
   - routing strategy,
   - hash version,
   - structured key components.
3. Update queue implementations:
   - Kafka uses the partition key as the record key,
   - Kinesis uses the partition key as the partition key,
   - SQS FIFO uses the partition/grouping key as the message group ID,
   - SQS standard stores routing metadata as attributes,
   - local filesystem stores routing metadata in the message.
4. Add aggregation routing strategies:
   - `FEED_TYPE`,
   - `FEED_TYPE_SHARDED`,
   - `CUSTOM`.
5. Add configuration for routing strategy per queue or per stage output.
6. Add tests proving similar aggregation items are routed consistently.
7. Add documentation describing hot-key trade-offs for high-volume feed/type combinations.

### Expected result

Aggregation stages can consume related items together where the queue implementation supports partitioning or grouping, while all queue implementations continue to share the same message contract.

## Future phase: `FileStoreLocation` and object stores

### Objective

Generalise storage references so queue messages and `FileStore` APIs can support future object-store implementations such as S3.

This phase should be implemented after the initial pluggable queue architecture is working.

### Tasks

1. Introduce `FileStoreLocation` as the canonical location/reference object.
2. Replace any remaining queue-message `Path` references with `FileStoreLocation`.
3. Add location fields such as:
   - store name,
   - URI,
   - location type,
   - optional provider attributes.
4. Update `FileStore` APIs to accept and return `FileStoreLocation`.
5. Keep filesystem `FileStore` support by resolving `FileStoreLocation` to paths internally where appropriate.
6. Define the object-store file-group representation, e.g. object prefix, manifest, or grouped objects.
7. Add validation so consumers can resolve the producing stage's `FileStoreLocation`.
8. Add an S3 design spike or prototype once the `FileStoreLocation` abstraction is in place.

### Expected result

The queue architecture remains independent of physical storage type, and future object stores can be supported without changing the core queue abstraction.

## Required contract tests

Every queue implementation should pass the same contract test suite.

### Producer tests

1. `publish(message)` makes an item available to a consumer.
2. `publish(message)` does not mutate the referenced source path.
3. queue item includes the expected `FileStore` location.
4. queue item has a stable ID.

### Consumer tests

1. `next()` blocks or waits when empty.
2. `next(timeout)` returns empty after timeout.
3. `next()` returns an available item.
4. `acknowledge()` prevents redelivery where the queue supports it.
5. `fail(error)` causes redelivery or failure handling according to policy.
6. `close()` releases local resources.

### Restart tests

1. unacknowledged item is recoverable.
2. acknowledged item is not redelivered.
3. failed item follows retry/dead-letter policy.
4. queue metadata remains consistent.

## Stage idempotency tests

Each independently executable stage should have duplicate-message tests.

### PreAggregator

- duplicate input should not corrupt aggregate state.
- duplicate input should not double count entries.
- duplicate input after aggregate closure should be detected or safely ignored.

### Aggregator

- duplicate closed aggregate message should not create conflicting duplicate output.
- duplicate processing should either reuse existing output or create a safe unique output and avoid double forwarding.

### Forwarder

- duplicate forwarding input should not cause unacceptable duplicate delivery if downstream idempotency is available.
- if duplicate delivery cannot be prevented, it must be documented as at-least-once.

## Failure model

## At-least-once is the baseline

Local filesystem, Kafka, SQS, and Kinesis queues should be treated as at-least-once delivery mechanisms.

The pipeline must therefore assume:

- duplicate queue messages are possible,
- consumers can crash after producing output but before acknowledging input,
- messages can be replayed,
- forwarding may be attempted more than once.

## Exactly-once should not be promised

Exactly-once across:

- filesystem writes,
- queue messages,
- aggregation,
- HTTP forwarding,
- cleanup,

is not realistic without a larger transactional design.

Instead, target:

- at-least-once queue delivery,
- idempotent stage processing where practical,
- clear failure and retry state,
- operator visibility.

## Cleanup and retention

In the unified reference-message model, queues do not imply physical data ownership. They only carry references to `FileStore` locations.

Multiple components may see references to the same storage area, and duplicate message delivery is possible. Cleanup therefore needs clear `FileStore` ownership and retention rules.

Recommended approach:

1. Producers write to a stage-defined `FileStore`.
2. Producers publish queue messages only after successful writes.
3. Consumers do not delete input immediately unless they own the lifecycle.
4. Each stage writes outputs to its own stage output `FileStore`.
5. Cleanup is either:
   - stage-local after successful output and ack,
   - or handled by a separate retention/cleanup process.
6. Retention should be configurable:
   - delete after successful downstream ack,
   - delete after age,
   - keep failed items,
   - keep all for audit/debug.

For the first implementation, avoid aggressive deletion in external queue deployments. Prefer retention-based cleanup until end-to-end ownership is fully explicit.

## Impacted classes

Likely impacted classes include:

| Area | Classes |
| --- | --- |
| Queue abstraction | `FileGroupQueue`, `FileGroupQueueItem`, `FileGroupQueueMessage`, `FileGroupQueueFactory`, `FileGroupQueueTransfer`, `LocalFileGroupQueue`, Kafka/SQS/Kinesis implementations |
| Pipeline assembly | `ReceiverFactoryProvider`, `ProxyLifecycle`, `ProxyServices` |
| Receive producers | `SimpleReceiver`, `ZipReceiver`, `ZipSplitter` |
| Aggregation producers/consumers | `PreAggregator`, `Aggregator` |
| Forwarding | `Forwarder`, `RetryingForwardDestination`, destination factories |
| Config | `ProxyConfig`, new pipeline/queue/stage/FileStore config classes |
| Monitoring | `QueueMonitor`, `QueueMonitors`, `FileStores`, `ProxyQueueMonitoringServlet` |
| Validation | `ProxyConfigValidator` and related config validation paths |

## Compatibility position

This is a new proxy queueing flavour with no backwards compatibility requirement for the current atomic move based `DirQueue`.

1. Remove `DirQueue` from the target design.
2. Default local/simple operation to `LOCAL_FILESYSTEM` message queues.
3. Use named queues/topics in config.
4. Use stage-defined `FileStore` locations in config.
5. Support the simple all-in-one runtime shape by enabling all standard stages in one process.
6. Use the same reference-message queue contract for local filesystem, Kafka, SQS, and Kinesis.
7. Convert receiver/aggregator/forwarder code to write to `FileStore` then publish queue messages.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Queue message points to a path not visible to consumer | Validate `FileStore` path base; require shared filesystem storage such as NFS or CephFS/Ceph for external queue deployments; add startup health check. |
| Consumer processes partial file group | Publish a queue message only after the producer has successfully written the referenced `FileStore` location. |
| Duplicate queue messages corrupt aggregation | Add file-group IDs, `FileStore` manifests where useful, and idempotency checks. |
| Message ack happens before output is durable | Ack only after the output has been written to `FileStore` and any downstream queue message has been published. |
| Output succeeds but ack fails | Make stage processing idempotent; allow safe replay. |
| SQS visibility timeout expires mid-processing | Add visibility extension or require timeout > max processing time. |
| Kafka poison message blocks partition | Add retry/dead-letter strategy. |
| Kinesis replay repeats large ranges | Checkpoint only after success; idempotent processing. |
| Cleanup deletes files still referenced | Introduce ownership/retention model; avoid aggressive deletion in the unified queue model initially. |
| Blast radius grows due to API changes | Introduce `FileStore`, queue message, and queue interfaces first; migrate stage by stage within the new queueing flavour. |

## Recommended implementation order

1. Add `FileStore` abstraction and local filesystem implementation.
2. Add queue message contract.
3. Add queue definitions, named topic/queue config, and queue factory.
4. Add local filesystem message queue using a global sequence file per queue.
5. Update transfer worker for explicit ack/fail.
6. Add stage enablement, stage thread configuration, and topology builder.
7. Convert receive/pre-aggregate/aggregate/forward to write to `FileStore` then publish queue messages.
8. Make receive/pre-aggregate/aggregate/forward independently runnable using local filesystem message queues.
9. Add Kafka implementation.
10. Add SQS implementation.
11. Add Kinesis implementation.
12. Extend monitoring and documentation.
13. Harden idempotency and cleanup.

## Success criteria

The work should be considered successful when:

1. The new proxy queueing flavour can run all standard stages in one process using local filesystem message queues without requiring external queue infrastructure.
2. Existing filesystem atomic move queues have been removed from the new queueing flavour.
3. All queue implementations use the same reference-message contract.
4. A file group can be written once to a stage-defined `FileStore` and passed between stages as a queue reference.
5. `RECEIVE`, `PRE_AGGREGATE`, `AGGREGATE`, and `FORWARD` can run as separate executables.
6. The local filesystem message queue implementation works using sequential queue message files.
7. At least one external queue implementation works end-to-end.
8. Queue implementations share a common contract test suite.
9. Monitoring shows the logical topology and queue health.
10. Documentation clearly explains storage visibility, delivery semantics, and failure behaviour.

## Summary

The current atomic move based `DirQueue` should be removed from this new queueing flavour.

The key design change is to separate queueing from physical data movement:

- stages write data to stage-defined `FileStore` locations,
- queues transfer work by publishing reference messages,
- local filesystem, Kafka, Kinesis, and SQS all use the same message contract,
- when queues are external systems such as Kafka, Kinesis, or SQS, referenced `FileStore` locations are expected to be on a shared filesystem such as NFS, CephFS/Ceph, or equivalent shared storage.

To support external queues and independent stage executables, the proxy also needs:

- `FileStore` configuration per stage,
- named queue/topic configuration,
- explicit ack/fail queue item semantics,
- per-stage enablement and thread configuration,
- topology-level queue configuration,
- idempotent stage processing,
- stronger monitoring and validation.

The safest path is incremental:

1. add `FileStore`,
2. define the queue message contract,
3. add local filesystem message queues,
4. convert stages to `FileStore` plus queue publication,
5. add independent stage execution,
6. then add Kafka/SQS/Kinesis implementations.
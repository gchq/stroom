# Pluggable Queue Implementation Plan

## Status

Proposed implementation plan and handover record.

This document is the authoritative design note for the new reference-message queueing flavour of Stroom Proxy. It records the current implementation position, the agreed ownership-transfer rule, and the remaining work needed to hand the implementation over to another developer.

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
11. Make data ownership explicit: after a stage has durably written onward output and published the onward queue message, or after final forwarding succeeds, that stage owns and may delete its input source directory.
12. Support multi-destination forwarding by fanning out to one destination-owned source directory per destination before forwarding or destination-specific queueing.
13. Keep `FileStoreLocation` URI based so future file-store backends such as mounted block storage, S3, and other object stores can be introduced without changing queue message semantics.
14. Add future configuration support for file-store backend type, URI/base URI, provider-specific settings, and validation of storage visibility for independently executable stages.

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
     - mounted block storage path, e.g. EBS, EFS, persistent volume, SAN, or another block-backed filesystem mounted by the relevant proxy executable,
     - object-store URI, e.g. S3, Azure Blob Storage, Google Cloud Storage, or another URI-addressed object store in a later extension.

2. **Queue item**
   - A durable message that refers to a file or directory location in a `FileStore`.
   - Examples:
     - local filesystem message file containing a path,
     - Kafka message containing a path,
     - Kinesis record containing a path,
     - SQS message containing a path.

All queue implementations enqueue messages that contain the location of already-complete data in a `FileStore`. They do not move the referenced data.

The fact that queues do not move data does not mean source data is retained forever. Data ownership transfers at processing boundaries. A consuming stage owns its input directory while processing it. Once it has successfully written its onward output to a new `FileStore` location and published the onward queue message, it may delete the input directory. The final forwarder similarly owns its source directory and may delete it after successful forwarding.

Each logical queue should be treated as a topic-like stream of work. Kafka maps naturally to a topic, Kinesis maps to a stream, SQS maps to a queue, and the local filesystem implementation maps to a directory of sequential message files. These logical queues/topics are defined in configuration and referenced by stages by name.

For external queues such as Kafka, Kinesis, or SQS, referenced locations must be on storage visible to every producer and consumer executable, normally a shared filesystem such as NFS, CephFS/Ceph, or an equivalent shared/cluster filesystem.

## Architectural principle

The queue abstraction should represent work scheduling, processing state, and acknowledgement state. It should not represent physical storage ownership by moving data.

Ownership of a source directory belongs to the stage currently processing it. A stage must not delete its input until the onward handoff is durable. The durable handoff point is:

1. the stage has completely written any onward file group to its output `FileStore`,
2. the stage has committed that write and obtained a stable `FileStoreLocation`,
3. the stage has published the onward `FileGroupQueueMessage`, and
4. the worker can then acknowledge the input queue item.

After that handoff has succeeded, the stage owns the input source directory and may delete it. This creates a chain of ownership transitions from receive through split, pre-aggregate, aggregate, and forward without requiring the queue to move data.

For final forwarding, the forwarder owns its source directory. After the data has been successfully sent or stored at the configured destination, the forwarder may delete or move the source directory as part of successful completion.

For multi-destination forwarding, a single source directory must not be handed directly to multiple destructive forwarders. The forward stage must first fan out by copying the source file group into one destination-owned source directory per destination and then queueing or forwarding those copies. Each destination forwarder can then delete its own source directory after successful forwarding without affecting other destinations.

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
| mounted block-backed filesystem path | `/mnt/proxy-block/...` | Valid when the mounted block-backed filesystem is visible to the stage executable that needs to resolve the reference. Multi-node use requires storage that can be safely mounted/shared by the relevant nodes. |
| URI | `file:///mnt/proxy-shared/...` | More explicit form of local, shared, or mounted block-backed filesystem path. |
| future S3 URI | `s3://bucket/prefix/file-group-id/` | Future object-store reference. Requires an object-store `FileStore` implementation and a file-group representation for object prefixes/manifests. |
| future object URI | `azure://container/prefix`, `gs://bucket/prefix` | Not part of the initial implementation, but the message contract should remain URI based so these can be added later. |

Initial implementation should require `file` paths only. When those paths are used with Kafka, Kinesis, SQS, or any other external queue, they must refer to storage visible to every stage executable that needs to resolve them. For independently executable deployments this normally means a shared filesystem, a consistently mounted block-backed filesystem, or an equivalent shared/cluster filesystem. Object storage can be added later by extending the `FileStore` abstraction while keeping queue messages as URI-based references.

## Required file-group stability and ownership-transfer rule

Producers must not enqueue paths under temporary receive/work directories that may later be deleted or moved before the consumer runs.

Before publishing a queue message, the producer must ensure the file or directory has been successfully written to the relevant stage-defined `FileStore`.

The act of publishing the `FileStore` location to the queue is the finalisation/handoff point for the next stage. There is no separate queue-level finalisation step. A stage may still use temporary files or directories internally while writing, but it must only publish the final `FileStore` location after the write has completed successfully.

A separate completion marker or manifest can be added later if it proves useful for diagnostics, recovery, or validation, but it is not required for the initial design. The initial contract is simple: if a location is present in a queue message then the producing stage has successfully written it and considers it ready for consumption.

The queue does not define or own the stable storage location. Each stage defines its own `FileStore` location for its outputs.

The consuming stage owns the input source directory while processing. After successful onward processing it may delete that input directory. Successful onward processing means:

1. for receive-like producers, the source has been written to the stage output `FileStore` and the queue message has been published;
2. for split, pre-aggregate, and aggregate stages, the new output file group or aggregate has been written, committed, and queued for the next stage;
3. for forward fan-out, every destination-owned source copy has been written, committed, and queued for the destination forwarder;
4. for final forwarding, the destination has accepted or persisted the data successfully.

Queue acknowledgement must happen only after this durable handoff has completed. If acknowledgement fails after output creation, the stage may be replayed, so each stage must be made idempotent or duplicate-safe.

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

## Future `FileStoreLocation`, backend types, and object-store support

The initial implementation is filesystem-backed, but the queue message contract must not be permanently coupled to `java.nio.file.Path`.

`FileStoreLocation` is the canonical storage reference in queue messages and `FileStore` APIs. A filesystem-backed `FileStore` can resolve a `FileStoreLocation` to a local, shared, or mounted block-backed filesystem path. Object-store-backed implementations such as S3, Azure Blob Storage, or Google Cloud Storage would resolve the same logical reference to provider-specific object operations.

A future `FileStoreLocation` should be able to represent:

| Storage type | Example | Notes |
| --- | --- | --- |
| Local filesystem | `file:///data/proxy/store/receive/...` | Suitable for single executable or same-host deployments. |
| Shared filesystem | `file:///mnt/proxy-shared/store/receive/...` | Suitable for multi-executable deployments when backed by NFS, CephFS/Ceph, EFS, or equivalent shared filesystem storage. |
| Mounted block-backed filesystem | `file:///mnt/proxy-block/store/receive/...` | Suitable when the block-backed filesystem is safely mounted and visible to the executable that must resolve it. Multi-node semantics depend on the storage product and mount mode. |
| S3 | `s3://proxy-bucket/store/receive/file-group-id/` | Future backend using bucket and prefix references. Requires object-store write, commit, resolve/read, and delete semantics. |
| Future object stores | Provider-specific URI or structured location. | Examples include Azure Blob Storage, Google Cloud Storage, and compatible object stores. |

The file-store definition should eventually include a backend type so validation and factory creation do not infer behaviour from URI strings alone. Suggested backend types:

| Backend type | Description |
| --- | --- |
| `LOCAL_FILESYSTEM` | Local or same-host filesystem path. |
| `SHARED_FILESYSTEM` | Filesystem path expected to be visible to multiple executables. |
| `BLOCK_FILESYSTEM` | Mounted block-backed filesystem path, e.g. persistent volume, SAN, or cloud block volume mounted into the proxy runtime. |
| `S3` | S3 bucket/prefix based object-store backend. |
| `OBJECT_STORE` | Generic future object-store backend where provider-specific config selects the implementation. |

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

Ownership:

- receive writes the received file group to its configured output `FileStore`;
- receive publishes the onward queue message only after the write has been committed;
- once the onward message has been published, any temporary receive source/work directory is owned by receive and may be deleted.

### Split zip stage

Input:

- split zip input queue.

Outputs:

- pre-aggregate input queue, if aggregation enabled,
- forwarding input queue, if aggregation disabled.

Ownership:

- split zip resolves the input message to a source file group and owns that source while splitting;
- split zip writes each onward file group to its configured output `FileStore`;
- split zip publishes onward queue messages only after output writes have been committed;
- after all required onward messages have been published, split zip may delete the input source directory.

### Pre-aggregate stage

Input:

- pre-aggregate input queue.

Outputs:

- aggregate input queue.

Local/stable working storage:

- open aggregates store,
- splitting work area,
- split output area.

Ownership:

- pre-aggregate resolves the input message to a source file group and owns that source while processing;
- pre-aggregate writes onward aggregate work/output to its configured `FileStore`;
- once the required onward output is committed and the aggregate input queue message has been published, pre-aggregate may delete the consumed source directory;
- open aggregate state remains owned by pre-aggregate until it is closed and handed off.

### Aggregate stage

Input:

- aggregate input queue.

Outputs:

- forwarding input queue.

Local/stable working storage:

- aggregate creation work area.

Ownership:

- aggregate resolves the input message to a source aggregate or aggregate work item and owns that source while processing;
- aggregate writes the forwardable file group to its configured output `FileStore`;
- aggregate publishes the forwarding input queue message only after the output write has been committed;
- after successful publication, aggregate may delete the consumed input source directory.

### Forward stage

Input:

- forwarding input queue.

Outputs:

- destination-specific forward/retry/failure handling,
- HTTP destination,
- file destination.

Ownership:

- forward resolves the input message to a source file group and owns that source while processing;
- if there is a single destination and no separate fan-out queue is needed, the destination forwarder may take ownership of that source directory directly and delete or move it after successful forwarding;
- if there are multiple destinations, forward must first create one destination-owned source directory per destination, commit those copies, and queue or hand those copies to the destination forwarders;
- after all destination-owned copies have been durably created and queued, forward may delete the original input source directory;
- each destination forwarder may delete its own destination-owned source directory after successful forwarding.

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
        type: LOCAL_FILESYSTEM
        path: "${stroom.proxy.data}/store/receive"
      splitStore:
        type: LOCAL_FILESYSTEM
        path: "${stroom.proxy.data}/store/split"
      preAggregateStore:
        type: LOCAL_FILESYSTEM
        path: "${stroom.proxy.data}/store/preAggregate"
      aggregateStore:
        type: LOCAL_FILESYSTEM
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

Example future block-backed filesystem file-store configuration:

~~~yaml
proxy:
  pipeline:
    fileStores:
      aggregateStore:
        type: BLOCK_FILESYSTEM
        uri: "file:///mnt/proxy-block/aggregate-store"
        mount:
          required: true
          expectedFileStoreId: "proxy-aggregate-pv"
        visibility:
          scope: NODE_LOCAL
~~~

Example future S3 file-store configuration:

~~~yaml
proxy:
  pipeline:
    fileStores:
      receiveStore:
        type: S3
        uri: "s3://proxy-bucket/receive-store/"
        region: "eu-west-2"
        credentialsProvider: "default"
        serverSideEncryption: true
        commitMode: MANIFEST
~~~

Example future generic object-store configuration:

~~~yaml
proxy:
  pipeline:
    fileStores:
      aggregateStore:
        type: OBJECT_STORE
        provider: "s3-compatible"
        uri: "s3://proxy-compatible-bucket/aggregate-store/"
        endpoint: "https://object-store.example.invalid"
        attributes:
          pathStyleAccess: "true"
~~~

These future configuration shapes are illustrative. The implementation should add typed config classes and validation before exposing these options as supported production configuration.

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

Current implementation status at handover:

Completed foundation work:

1. `FileStore`, `FileStoreWrite`, `FileStoreLocation`, and local filesystem `FileStore`.
2. `FileGroupQueueMessage` and JSON codec.
3. Queue abstraction interfaces and local filesystem queue implementation.
4. Pipeline config model, topology model, validation, queue factory, file-store factory, worker, and runtime assembly skeleton.
5. `FileStoreRegistry`.
6. Initial `ForwardStageProcessor` scaffold.
7. Initial `ForwardStageFanOutForwarder` scaffold for copying one input source into destination-owned source directories and publishing destination queue messages.

Important unresolved production wiring:

1. Existing receive, split zip, pre-aggregate, aggregate, and forward production code is not yet converted to the new reference-message pipeline.
2. `ReceiverFactoryProvider` and the existing `DirQueue` runtime assembly still represent the current production flow.
3. Kafka, SQS, and Kinesis queue implementations are not implemented.
4. Thread lifecycle/running of `FileGroupQueueWorker`s is not implemented.
5. End-to-end cleanup/delete support on `FileStore` is not implemented.
6. Idempotency and duplicate suppression are not complete.
7. Monitoring, health checks, and retention policies are not complete.

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
8. Add a delete/release operation or lifecycle policy so a stage can delete its input source directory after durable onward handoff.
9. Add tests proving a stage does not delete input before output commit and onward queue publication.

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
2. Add duplicate delivery tests for each stage.
3. Add crash/restart tests:
   - producer crashes after writing file group before queue publish,
   - producer crashes after queue publish,
   - consumer crashes before ack,
   - consumer crashes after output but before ack,
   - consumer crashes after ack.
4. Add ownership-transfer tests:
   - input source is not deleted before output commit,
   - input source is not deleted before onward queue publish,
   - input source may be deleted after successful onward publish and input acknowledgement,
   - final forward source may be deleted after successful forwarding,
   - multi-destination forward creates independent source directories before any destructive destination forwarding.
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

## Future phase: `FileStoreLocation`, backend configuration, and URI references

### Objective

Generalise storage references so queue messages and `FileStore` APIs can support local filesystems, shared filesystems, mounted block-backed filesystems, S3, and future object-store implementations without changing queue semantics.

This phase should be implemented after the initial pluggable queue architecture is working.

### Tasks

1. Treat `FileStoreLocation` as the canonical location/reference object in every queue message and stage processor.
2. Replace any remaining direct queue-message `Path` assumptions with `FileStoreLocation`.
3. Add or confirm location fields such as:
   - store name,
   - URI,
   - location type,
   - optional provider attributes.
4. Add file-store backend type configuration, initially including:
   - `LOCAL_FILESYSTEM`,
   - `SHARED_FILESYSTEM`,
   - `BLOCK_FILESYSTEM`,
   - `S3`,
   - future `OBJECT_STORE`.
5. Update `FileStoreFactory` to create implementations based on explicit backend type rather than assuming every store is local filesystem.
6. Keep filesystem `FileStore` support by resolving `FileStoreLocation` to paths internally where appropriate.
7. Validate that external queue deployments do not reference process-local storage that a consumer executable cannot resolve.
8. Add configuration-schema generation and validation for backend-specific fields.
9. Add tests proving URI references round-trip through JSON and resolve through the correct configured `FileStore`.

### Expected result

The queue architecture remains independent of physical storage type, and every queue implementation continues to transport the same URI-based `FileStoreLocation` message contract.

## Future phase: block-backed filesystem `FileStore`

### Objective

Support file stores backed by mounted block or persistent-volume storage while preserving the same queue message contract.

This is distinct from object storage. The store still resolves to `file:` URIs and local `Path` access, but configuration and validation should make the operational assumptions explicit.

### Tasks

1. Add `BLOCK_FILESYSTEM` to the file-store backend type enum.
2. Add config fields for:
   - `uri` or `path`,
   - expected mount point,
   - optional expected filesystem/device identifier,
   - visibility scope, e.g. `NODE_LOCAL`, `SHARED_READ_WRITE`, or `SINGLE_WRITER_MULTI_READER`,
   - free-space and writability checks.
3. Implement a block-backed filesystem `FileStore` by reusing the local filesystem store implementation where possible.
4. Add startup validation that the mount exists, is writable where required, and matches configured expectations.
5. Document safe deployment modes:
   - single executable with node-local block storage,
   - Kubernetes persistent volume mounted into one proxy pod,
   - shared block/cluster filesystem only when the storage product supports safe multi-node access.
6. Add tests for:
   - URI generation,
   - path resolution,
   - delete/release after ownership transfer,
   - startup validation failure when the mount is missing or read-only.

### Expected result

Operators can configure block-backed storage explicitly without pretending it is an arbitrary local path, and queue messages still contain standard `file:` URI references.

## Future phase: S3 `FileStore`

### Objective

Support S3-backed file groups using URI-based references such as `s3://bucket/prefix/file-group-id/`.

This phase should be implemented after the filesystem-backed queue architecture and ownership-transfer model are stable.

### Tasks

1. Add `S3` to the file-store backend type enum.
2. Add S3 file-store config fields for:
   - bucket,
   - prefix/base URI,
   - region,
   - credentials/provider selection,
   - endpoint override for compatible stores if required,
   - server-side encryption options,
   - object tagging/metadata options,
   - commit mode.
3. Define the S3 file-group layout:
   - object prefix per file group,
   - `proxy.meta`,
   - `proxy.zip`,
   - `proxy.entries`,
   - optional manifest/commit marker.
4. Define commit semantics:
   - write objects under a temporary prefix then publish/rename by manifest,
   - or write final objects and publish a manifest as the commit marker.
5. Implement `FileStoreWrite` for S3.
6. Implement `resolve` or add a richer read/access abstraction because S3 locations cannot always be resolved to a local `Path`.
7. Implement delete/release for ownership transfer.
8. Add S3-specific idempotency behaviour for replay after output creation but before input acknowledgement.
9. Add integration tests using an S3-compatible test fixture where available.
10. Add operational documentation for lifecycle policies, retention, encryption, and permissions.

### Expected result

Stages can exchange queue messages that reference S3-backed file groups without changing queue implementations. Stage processors use the `FileStore` abstraction rather than assuming local filesystem paths.

## Future phase: generic object-store `FileStore`

### Objective

Allow additional object-store providers to be added after S3 without changing the queue message contract.

### Tasks

1. Add a provider-specific extension point behind `OBJECT_STORE`.
2. Keep `FileStoreLocation` URI based with optional provider attributes.
3. Define common operations:
   - begin write,
   - commit write,
   - open/read file group,
   - delete/release after ownership transfer,
   - validate existence/completeness.
4. Add provider-specific config validation.
5. Document limitations where a provider cannot provide atomic rename or directory semantics.
6. Add contract tests all object-store implementations must pass.

### Expected result

Future object stores can be introduced as additional `FileStore` implementations while queues continue carrying the same reference-message contract.

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

Cleanup is governed by explicit stage ownership transfer, not by the queue transport.

Recommended approach:

1. Producers write to a stage-defined `FileStore`.
2. Producers publish queue messages only after successful writes.
3. Consumers own their input source directory while processing.
4. Each stage writes outputs to its own stage output `FileStore`.
5. Queue acknowledgement happens only after the stage has completed durable onward handoff.
6. After durable onward handoff, the consuming stage may delete the input source directory.
7. Final forwarders may delete or move their source directory after successful forwarding.
8. Multi-destination forwarding must fan out first:
   - copy the original source into one destination-owned source directory per destination,
   - commit each destination-owned source directory,
   - queue or hand each copy to its destination forwarder,
   - then delete the original source only after all destination handoffs have succeeded.
9. Retention should still be configurable:
   - delete after successful onward handoff,
   - delete after age,
   - keep failed items,
   - keep all for audit/debug.

Because delivery is at-least-once, cleanup must be paired with idempotency. If a stage crashes after output creation but before queue acknowledgement, the input message may be replayed. The replay must either reuse existing output, create safe duplicate output that is later deduplicated, or detect that the handoff has already completed.

## Impacted classes

Likely impacted classes include:

| Area | Classes |
| --- | --- |
| Queue abstraction | `FileGroupQueue`, `FileGroupQueueItem`, `FileGroupQueueMessage`, `FileGroupQueueFactory`, `FileGroupQueueTransfer`, `LocalFileGroupQueue`, Kafka/SQS/Kinesis implementations |
| File stores and ownership | `FileStore`, `FileStoreWrite`, `FileStoreLocation`, `LocalFileStore`, `FileStoreFactory`, `FileStoreRegistry`, future delete/release API |
| Pipeline assembly | `ReceiverFactoryProvider`, `ProxyLifecycle`, `ProxyServices`, `ProxyPipelineRuntime`, future runtime runner |
| Receive producers | `SimpleReceiver`, `ZipReceiver`, `ZipSplitter` |
| Aggregation producers/consumers | `PreAggregator`, `Aggregator` |
| Forwarding | `Forwarder`, `RetryingForwardDestination`, destination factories, `ForwardStageProcessor`, `ForwardStageFanOutForwarder`, future destination-specific forward workers |
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
| Queue message points to a path not visible to consumer | Validate `FileStore` path base and backend type; require shared filesystem, safely mounted block-backed storage, object storage, or equivalent storage visible to the consumer for external queue deployments; add startup health check. |
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

Completed or partially completed foundation:

1. Add `FileStore` abstraction and local filesystem implementation.
2. Add queue message contract.
3. Add queue definitions, named topic/queue config, and queue factory.
4. Add local filesystem message queue using a global sequence file per queue.
5. Update transfer worker for explicit ack/fail.
6. Add stage enablement, stage thread configuration, validation, topology model, and runtime assembly skeleton.
7. Add file-store registry and initial forward-stage processor/fan-out scaffolding.

Remaining handover checklist:

1. Add `FileStore` delete/release support or an explicit ownership cleanup service.
2. Add a runtime runner/lifecycle layer that starts `FileGroupQueueWorker`s using configured `consumerThreads` and shuts them down cleanly.
3. Convert receive to:
   - write incoming file groups to the receive output `FileStore`,
   - publish reference messages,
   - delete temporary receive work after successful queue publication.
4. Convert split zip to:
   - resolve input message,
   - write split outputs to its output `FileStore`,
   - publish onward messages,
   - delete the consumed source after successful onward handoff.
5. Convert pre-aggregate to:
   - resolve input message,
   - write/update pre-aggregate state and onward aggregate input,
   - publish aggregate input messages,
   - delete consumed sources after successful onward handoff.
6. Convert aggregate to:
   - resolve aggregate input messages,
   - write forwardable file groups to the aggregate output `FileStore`,
   - publish forwarding input messages,
   - delete consumed aggregate input sources after successful onward handoff.
7. Complete forward-stage production integration:
   - support single-destination destructive forwarding,
   - support multi-destination fan-out to destination-owned source directories,
   - queue destination-owned copies where destination retry queues are configured,
   - delete final source directories after successful forwarding.
8. Add idempotency for every stage so replay after output creation but before input ack is safe.
9. Make receive/pre-aggregate/aggregate/forward independently runnable using local filesystem message queues.
10. Add Kafka implementation.
11. Add SQS implementation.
12. Add Kinesis implementation.
13. Extend monitoring, health checks, and documentation.
14. Harden idempotency, cleanup, retention, and crash recovery.
15. Add explicit file-store backend types and configuration validation for local filesystem, shared filesystem, mounted block-backed filesystem, S3, and future object stores.
16. Implement block-backed filesystem file-store support where it differs from local/shared filesystem validation.
17. Implement S3 file-store support with URI references, object layout, commit markers/manifests, read/access abstraction, and delete/release semantics.
18. Add generic object-store extension points and contract tests.

## Success criteria

The work should be considered successful when:

1. The new proxy queueing flavour can run all standard stages in one process using local filesystem message queues without requiring external queue infrastructure.
2. Existing filesystem atomic move queues have been removed from the new queueing flavour.
3. All queue implementations use the same reference-message contract.
4. A file group can be written once to a stage-defined `FileStore` and passed between stages as a queue reference.
5. Each stage can assume ownership of its input source directory and delete it after successful durable onward handoff.
6. Multi-destination forwarding fans out to one destination-owned source directory per destination before any destructive forwarding.
7. Final forwarders delete or move their owned source directory after successful forwarding.
8. `RECEIVE`, `PRE_AGGREGATE`, `AGGREGATE`, and `FORWARD` can run as separate executables.
9. The local filesystem message queue implementation works using sequential queue message files.
10. At least one external queue implementation works end-to-end.
11. Queue implementations share a common contract test suite.
12. Monitoring shows the logical topology and queue health.
13. Documentation clearly explains storage visibility, ownership transfer, delivery semantics, cleanup, and failure behaviour.

## Summary

The current atomic move based `DirQueue` should be removed from this new queueing flavour.

The key design change is to separate queueing from physical data movement:

- stages write data to stage-defined `FileStore` locations,
- queues transfer work by publishing reference messages,
- each stage owns its input source directory while processing and may delete it after successful durable onward handoff,
- final forwarders own their source directory and may delete or move it after successful forwarding,
- multi-destination forwarding creates one destination-owned source directory per destination before destructive forwarding,
- local filesystem, Kafka, Kinesis, and SQS all use the same message contract,
- when queues are external systems such as Kafka, Kinesis, or SQS, referenced `FileStore` locations are expected to be on storage visible to the consuming executable, such as NFS, CephFS/Ceph, safely mounted block-backed storage, S3, or equivalent shared/object storage once the relevant `FileStore` backend is implemented.

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
4. add explicit ownership cleanup/delete support,
5. convert stages to `FileStore` plus queue publication,
6. make each stage delete its input only after successful durable onward handoff,
7. add independent stage execution,
8. complete forwarding fan-out and destination-owned source handling,
9. then add Kafka/SQS/Kinesis implementations.
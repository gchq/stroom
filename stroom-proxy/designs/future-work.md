# Future Work — Stroom Proxy Pipeline

This document captures recommended improvements and enhancements for the proxy pipeline architecture. Items are grouped by priority and area.

---

## Operational Hardening

### ~~1. Health Checks for External Queues~~ ✅ DONE

**Priority**: High  
**Origin**: Original design plan Phase 9  
**Status**: Implemented — `FileGroupQueue.healthCheck()` default method with overrides in `LocalFileGroupQueue` (dir writability + approximate counts), `SqsFileGroupQueue` (`GetQueueAttributes`), and `KafkaFileGroupQueue` (lazy `AdminClient.describeTopics`). Aggregated via `PipelineHealthChecks` on the admin `/healthcheck` endpoint. See [user-guide.md §Monitoring & Observability](user-guide.md#monitoring--observability).

### ~~2. Health Checks for File Stores~~ ✅ DONE

**Priority**: High  
**Status**: Implemented — `FileStore.healthCheck()` default method with overrides in `LocalFileStore` (root/writer dir writability) and `S3FileStore` (`headBucket` + local staging checks). Results aggregated in `PipelineHealthChecks` and shown on the admin `/healthcheck` endpoint.

### 3. Retry Attempt Tracking

**Priority**: Medium  
**Origin**: Original design plan (optional)

Add an `attempt` field to `FileGroupQueueMessage` that is incremented each time a message is redelivered after a failure. This provides visibility into retry behaviour and enables dead-letter routing after N attempts.

**Benefits**:
- Operators can see which items are stuck in retry loops
- A configurable `maxAttempts` threshold could route items to a dead-letter queue or error store instead of retrying indefinitely
- Monitoring dashboards could alert on high retry rates

### ~~4. SQS Heartbeat Monitoring~~ ✅ DONE

**Priority**: Medium  
**Status**: Implemented — `SqsHeartbeatCounters` (attempt/success/failure/cancelled) wired into the existing heartbeat lambda in `SqsFileGroupQueue`. Counters exported as Prometheus metrics (`stroom.proxy.pipeline.queue.<name>.heartbeat.*`) via `PipelineMetricsRegistrar`. Heartbeat stats also shown on the admin `/queues` endpoint.

---

## Testing

### 5. FileStore Contract Test Suite

**Priority**: High  
**Origin**: Original design plan

Create an `AbstractFileStoreContractTest` analogous to the existing `AbstractFileGroupQueueContractTest`. This would validate the `FileStore` contract (`newWrite`, `commit`, `resolve`, `delete`, `isComplete`, `newDeterministicWrite`) consistently across all implementations.

**Current state**: `TestS3FileStore` (13 tests) and `TestFileStoreIdempotency` exist independently but don't share a common test suite.

**Suggested tests**:
- Write + commit produces a resolvable location
- Uncommitted writes are cleaned up on close
- Delete is idempotent (deleting twice doesn't throw)
- `isComplete()` returns false before commit, true after
- Deterministic writes are idempotent (same ID → same location)
- Deterministic write skips upload if already complete
- Resolve rejects locations for the wrong store name
- Concurrent writes from different writers don't interfere

### 6. SQS/Kafka Contract Test Migration

**Priority**: Low

The `AbstractFileGroupQueueContractTest` currently only has `TestLocalFileGroupQueue` as a concrete implementation. The SQS and Kafka tests use mocks and have their own independent test classes. Consider:
- Adding a LocalStack-based integration test that extends the contract suite for SQS
- Adding a Testcontainers-based Kafka integration test
- This would provide stronger confidence that external queues satisfy the same guarantees as local queues

### 7. End-to-End Integration Test with Real Queues

**Priority**: Low

A full pipeline integration test using real (or containerised) external queues would verify:
- SQS visibility extension works correctly under actual network conditions
- Kafka consumer group rebalancing doesn't cause message loss
- S3 file stores work correctly with real (LocalStack) S3
- Multi-node write safety on shared filesystems

---

## Performance & Scalability

### 8. S3 Streaming Reads

**Priority**: Low

Currently, `S3FileStore.resolve()` downloads all files in a file group to a local cache directory before returning a `Path`. The original rationale for streaming was to reduce latency and disk pressure for large file groups.

**However, the realistic benefit is minimal.** Every stage processor reads the entire file group — `proxy.meta`, `proxy.zip`, and `proxy.entries` — and passes the complete directory to a production handler (`PreAggregator::addDir`, `Aggregator::addDir`, `Forwarder::add`, `ZipSplitter::splitZip`). There is no partial or selective file access at any stage. Streaming would still download exactly the same bytes; it would just bypass the local cache.

The local cache is actually **beneficial** for at-least-once delivery: when a message is redelivered after a crash, `resolve()` skips already-downloaded files (`if (!Files.exists(localFile))`), avoiding redundant S3 `GetObject` calls.

**Practical alternative**: The real concern is disk pressure from cached files accumulating. This is better addressed with:
- A size-based or time-based eviction policy on the local cache directory
- Cleaning up cache entries after the stage deletes the corresponding `FileStoreLocation`

A streaming API refactoring (changing `resolve()` from `Path` to `InputStream`) would require deep changes to all production handlers for marginal benefit.

### 9. S3 Multipart Upload for Large File Groups

**Priority**: Low

For file groups containing very large zip files, S3 multipart upload would improve reliability and throughput. The AWS Transfer Manager already supports this; the `S3FileStore` could enable it via configuration.

### 10. Local Queue Multi-Process Consumers

**Priority**: Low

The `LocalFileGroupQueue` supports multiple **threads** within a single process — `next()` uses `Files.move(ATOMIC_MOVE)` as a lock-free competing-consumer mechanism, and handles race conditions via `NoSuchFileException` retry loops. However, it does not safely support multiple **processes** (multiple JVMs) consuming from the same queue directory. The startup recovery step (`recoverInFlightMessages`) moves all in-flight items back to pending, which would interfere with items actively being processed by another JVM.

For multi-process deployments that want to scale a single stage's throughput, consider:
- File-based locking for multi-process consumption (coordinate recovery)
- Or an embedded lightweight queue (e.g. SQLite-backed) that supports cross-process competing consumers

In practice, most multi-process deployments should use SQS or Kafka instead.

---

## Observability

### 11. Pipeline Topology Dashboard

**Priority**: Medium

The monitoring servlet now shows queue health status, queue depths, heartbeat stats, and error highlighting (see items 1, 2, 4, 18). A visual topology dashboard would make it even easier to understand the pipeline at a glance:
- Show all configured stages with enabled/disabled status
- Show queue types and depths between stages (partially done — depths shown for local queues)
- Show file store types and disk/S3 usage
- Show per-stage throughput (items/sec) derived from the Prometheus metrics

### ~~12. Structured Logging with Trace IDs~~ ✅ DONE

**Priority**: Medium  
**Status**: Implemented — `FileGroupQueueWorker.processItem()` now sets MDC keys (`traceId`, `fileGroupId`, `messageId`, `stageName`) before processing and clears them in a `finally` block. Null `traceId` is handled gracefully. See [user-guide.md §Structured Logging](user-guide.md#3-structured-logging).

---

## Configuration & Deployment

### 13. Operational Deployment Guides

**Priority**: Medium  
**Origin**: Original design plan

The `pipeline-design.md` document covers architecture and configuration reference with examples. Operational deployment guides should cover:
- AWS deployment with Terraform/CloudFormation templates for SQS queues and S3 buckets
- Kubernetes deployment with shared PVC for local filesystem stores
- Monitoring and alerting setup (Prometheus/Grafana dashboards)
- Capacity planning guidelines (queue sizing, thread tuning, disk/S3 budgets)
- Disaster recovery procedures (queue drain, store backup/restore)

### 14. Configuration Validation Improvements

**Priority**: Low

The `ProxyPipelineConfigValidator` validates queue and file store definitions. Additional validations could include:
- Warn if a stage's input queue uses `LOCAL_FILESYSTEM` but the stage runs on multiple nodes (likely misconfigured — should use SQS/Kafka)
- Warn if a file store uses `LOCAL_FILESYSTEM` but consumer threads > 1 and no shared filesystem is configured
- Validate that all queue/store names referenced by stages exist in the `queues`/`fileStores` maps (partially done, could be strengthened)
- Validate SQS visibility timeout is reasonable for expected processing duration

### 15. Dynamic Configuration Reload

**Priority**: Low

Currently, pipeline configuration is read at startup. For long-running proxies, it would be useful to support:
- Thread count changes without restart
- Enabling/disabling stages dynamically
- Adding new forwarding destinations at runtime

This would require careful lifecycle management to drain in-flight work before reconfiguring.

---

## Architectural Enhancements

### 16. Dead-Letter Queue Support

**Priority**: Medium

Items that fail repeatedly should be routed to a dead-letter queue (DLQ) rather than retrying indefinitely. This requires:
- The `attempt` field on messages (item 3 above)
- A configurable `maxAttempts` per queue or stage
- A DLQ destination (another queue or a dedicated error store)
- SQS has native DLQ support via redrive policies — this should be documented as an option

### 17. Backpressure Between Stages

**Priority**: Low

If a downstream stage is overwhelmed (e.g. forwarding is slow), upstream stages continue producing work. Consider:
- Queue depth monitoring with configurable high-water marks
- Receive stage throttling when downstream queues exceed depth thresholds
- HTTP 503 responses to data senders when the pipeline is saturated

### ~~18. Metrics Export~~ ✅ DONE

**Priority**: Medium  
**Status**: Implemented — `PipelineMetricsRegistrar` registers Codahale gauges (bridged to Prometheus via the existing `PrometheusModule`) for all 10 per-stage counters (items received/processed/acknowledged/failed, 4 error types, polls total/empty), 3 per-queue depth gauges (local queues), and 3 SQS heartbeat counters. See [user-guide.md §Prometheus Metrics](user-guide.md#2-prometheus-metrics).

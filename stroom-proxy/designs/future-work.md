# Future Work — Stroom Proxy Pipeline

This document captures recommended improvements and enhancements for the proxy pipeline architecture. Items are grouped by priority and area.

---

## Operational Hardening

### 1. Health Checks for External Queues

**Priority**: High  
**Origin**: Original design plan Phase 9

When using SQS or Kafka queues, the proxy should expose health check endpoints that verify connectivity to each configured external queue. This allows load balancers and orchestrators to detect unhealthy proxy instances and route traffic accordingly.

**Suggested approach**:
- Add a `healthCheck()` method to the `FileGroupQueue` interface
- Local queues return healthy if the directory exists and is writable
- SQS queues perform a lightweight `GetQueueAttributes` call
- Kafka queues perform a `describeTopics` admin call
- Expose results via the existing Dropwizard health check registry

### 2. Health Checks for File Stores

**Priority**: High

Similar to queue health checks, file stores should verify they are accessible:
- Local stores: directory exists and is writable
- S3 stores: perform a `HeadBucket` or `ListObjectsV2` with `maxKeys=1` to verify bucket access

### 3. Retry Attempt Tracking

**Priority**: Medium  
**Origin**: Original design plan (optional)

Add an `attempt` field to `FileGroupQueueMessage` that is incremented each time a message is redelivered after a failure. This provides visibility into retry behaviour and enables dead-letter routing after N attempts.

**Benefits**:
- Operators can see which items are stuck in retry loops
- A configurable `maxAttempts` threshold could route items to a dead-letter queue or error store instead of retrying indefinitely
- Monitoring dashboards could alert on high retry rates

### 4. SQS Heartbeat Monitoring

**Priority**: Medium

The `SqsFileGroupQueue` runs a background heartbeat scheduler (`sqs-heartbeat-<name>`) that extends visibility timeouts for in-flight items. In production, this thread pool should be monitored:
- Expose heartbeat success/failure counts as metrics
- Alert if heartbeat extensions are consistently failing (indicates SQS connectivity issues)
- Monitor thread pool saturation under heavy load

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

**Priority**: Medium

Currently, `S3FileStore.resolve()` downloads all files in a file group to a local cache directory before returning a `Path`. For large file groups, this creates latency and disk pressure. A streaming approach would allow stage processors to read directly from S3 without a full download.

**Challenges**:
- The current `FileStoreWrite.getPath()` / `resolve()` contract returns a `Path`, which stage processors use for filesystem operations
- Streaming would require processors to accept an `InputStream` or `ReadableByteChannel` instead
- This is a deeper refactoring of the processor interface

**Incremental approach**:
- Start with a lazy-download cache that only downloads files as they are accessed
- Add a size-based eviction policy to the local cache
- Eventually introduce a streaming `resolve()` variant for processors that can use it

### 9. S3 Multipart Upload for Large File Groups

**Priority**: Low

For file groups containing very large zip files, S3 multipart upload would improve reliability and throughput. The AWS Transfer Manager already supports this; the `S3FileStore` could enable it via configuration.

### 10. Local Queue Competing Consumers

**Priority**: Low

The `LocalFileGroupQueue` currently supports only a single consumer process (file-based FIFO). For local-queue deployments that want to scale a single stage's throughput beyond one process, consider:
- File-based locking for multi-process consumption
- Or an embedded lightweight queue (e.g. SQLite-backed) that supports competing consumers

In practice, most multi-process deployments should use SQS or Kafka instead.

---

## Observability

### 11. Pipeline Topology Dashboard

**Priority**: Medium

The monitoring servlet provides queue health data, but a visual topology dashboard would make it easier to understand the pipeline at a glance:
- Show all configured stages with enabled/disabled status
- Show queue types and depths between stages
- Show file store types and disk/S3 usage
- Show per-stage throughput (items/sec) and error rates

### 12. Structured Logging with Trace IDs

**Priority**: Medium

The `FileGroupQueueMessage` already carries an optional `traceId` field. This could be propagated through structured logging (MDC) so that all log entries for a given data item can be correlated:
- Set MDC `traceId` at the start of `FileGroupQueueWorker.processItem()`
- Clear it after acknowledgement
- Include `traceId`, `fileGroupId`, `messageId`, and `stageName` in structured log output

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

### 18. Metrics Export

**Priority**: Medium

The `FileGroupQueueWorkerCounters` already track items received, processed, acknowledged, and errored. These should be exported as Prometheus metrics or JMX MBeans:
- `stroom_proxy_pipeline_items_received_total{stage="forward"}`
- `stroom_proxy_pipeline_items_processed_total{stage="forward"}`
- `stroom_proxy_pipeline_processing_duration_seconds{stage="forward"}`
- `stroom_proxy_pipeline_queue_depth{queue="forwardingInput"}`
- `stroom_proxy_pipeline_errors_total{stage="forward"}`

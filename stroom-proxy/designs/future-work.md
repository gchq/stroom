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

### ~~3. Retry Attempt Tracking~~ — Not Planned

**Priority**: ~~Medium~~ Not recommended  
**Origin**: Original design plan (optional)

Originally proposed adding an `attempt` field to `FileGroupQueueMessage` for retry visibility and dead-letter routing. After review, this adds complexity for minimal value:

- **SQS** already tracks `ApproximateReceiveCount` natively and supports dead-letter queues via redrive policies — this is the idiomatic AWS approach and requires zero application code.
- **Kafka** has its own retry and DLQ mechanisms (e.g. error topic routing via consumer configuration).
- **Local queues** are consumed within the same JVM, so persistent failures indicate code bugs rather than transient issues worth retrying with a counter. The existing `failed/` directory with `.last-error.txt` files provides sufficient diagnostics.

Custom attempt tracking across three backends with three different retry semantics would duplicate what the queue infrastructure already provides. Operators should configure retry/DLQ policies at the queue level (SQS redrive policy, Kafka consumer retry config) rather than in the proxy application layer.

### ~~4. SQS Heartbeat Monitoring~~ ✅ DONE

**Priority**: Medium  
**Status**: Implemented — `SqsHeartbeatCounters` (attempt/success/failure/cancelled) wired into the existing heartbeat lambda in `SqsFileGroupQueue`. Counters exported as Prometheus metrics (`stroom.proxy.pipeline.queue.<name>.heartbeat.*`) via `PipelineMetricsRegistrar`. Heartbeat stats also shown on the admin `/queues` endpoint.

---

## Testing

### ~~5. FileStore Contract Test Suite~~ ✅ DONE

**Priority**: High  
**Status**: Implemented — `AbstractFileStoreContractTest` provides 14 shared contract tests covering `newWrite`, `commit`, `resolve`, `delete`, `isComplete`, `newDeterministicWrite`, commit idempotency, delete+rewrite cycles, and health checks. Concrete subclasses `TestLocalFileStoreContract` and `TestS3FileStoreContract` (using `StubS3Client`) run the full suite against both implementations. The previous `TestFileStoreIdempotency` was deleted (all tests migrated) and `TestS3FileStore` was trimmed to 3 S3-specific tests only.

### ~~6. SQS/Kafka Contract Test Migration~~ ✅ DONE

**Priority**: Low  
**Status**: Implemented — `TestSqsFileGroupQueueContract` (LocalStack via Testcontainers) and `TestKafkaFileGroupQueueContract` (Testcontainers Kafka) extend the `AbstractFileGroupQueueContractTest` suite. Both use `@Testcontainers(disabledWithoutDocker = true)` and `@Tag("integration")` so they gracefully skip when Docker is unavailable and are excluded from normal `./gradlew test` runs. Run via `./gradlew :stroom-proxy:stroom-proxy-app:integrationTest`. The Kafka subclass overrides `contractAcknowledgePreventsRedelivery` with offset-commit verification to handle Kafka's consumer poll caching semantics.

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

### 16. Dead-Letter Queue Documentation

**Priority**: Low

Rather than implementing custom dead-letter routing in the proxy application (see §3 rationale), operators should use the native DLQ mechanisms provided by their queue backend:

- **SQS**: Configure a [redrive policy](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html) on each SQS queue with a `maxReceiveCount` and a target DLQ ARN. SQS automatically routes messages that exceed the receive count.
- **Kafka**: Configure error topic routing via the consumer or use a framework-level DLQ pattern.
- **Local queues**: Failed items are already moved to the `failed/` directory with `.last-error.txt` error details. Operators can monitor the `failed/` directory count via the existing health checks and Prometheus metrics.

The user guide should document these recommended configurations with examples.

### 17. Backpressure Between Stages

**Priority**: Low

If a downstream stage is overwhelmed (e.g. forwarding is slow), upstream stages continue producing work. Consider:
- Queue depth monitoring with configurable high-water marks
- Receive stage throttling when downstream queues exceed depth thresholds
- HTTP 503 responses to data senders when the pipeline is saturated

### ~~18. Metrics Export~~ ✅ DONE

**Priority**: Medium  
**Status**: Implemented — `PipelineMetricsRegistrar` registers Codahale gauges (bridged to Prometheus via the existing `PrometheusModule`) for all 10 per-stage counters (items received/processed/acknowledged/failed, 4 error types, polls total/empty), 3 per-queue depth gauges (local queues), and 3 SQS heartbeat counters. See [user-guide.md §Prometheus Metrics](user-guide.md#2-prometheus-metrics).

### 19. Orphaned File Cleanup

**Priority**: Medium

In normal operation the ownership-transfer contract ensures all files are eventually consumed and deleted. However, a hard crash (e.g. power outage, `kill -9`) at specific points in the processing lifecycle can leave orphaned files on disk or in S3 that are no longer referenced by any queue message.

**Identified orphan scenarios:**

| Location | Cause | What's Left |
|---|---|---|
| `LocalFileStore` `writing/` | Crash during `newWrite()` before `commit()` | Uncommitted staging directories (`write-*`) |
| `LocalFileStore` data dirs | Crash after `commit()` but before queue `publish()` | Committed file group with no queue message referencing it |
| `S3FileStore` `staging/` | Crash during S3 upload before `commit()` | Local staging files (partial upload may also leave S3 objects) |
| `S3FileStore` `cache/` | Message routed to DLQ externally; `delete()` never called | Cached downloads from `resolve()` |
| `S3FileStore` S3 objects | Same as local commit-before-publish scenario | Committed S3 objects with no queue message |
| `LocalFileGroupQueue` `tmp/` | Hard kill during `publish()` before atomic move | Temporary JSON files |
| `AggregateClosePublisher` | Crash after output `commit()` + `publish()` but before `deleteRecursively(aggregateDir)` | Source aggregate directory (data is safe — already published) |

**None of these scenarios cause data loss** — the at-least-once guarantee holds because input messages are redelivered and reprocessed. The orphans are wasted disk/S3 space only.

**Proposed strategy — periodic orphan scanner:**

A background `ScheduledExecutorService` task (e.g. hourly) that:

1. **Staging cleanup** (`writing/`, `staging/`, `tmp/`): Delete any staging directory or temp file older than a configurable age threshold (e.g. 1 hour). Since no write operation should take more than a few minutes, anything older than the threshold is safely orphaned. This is the simplest and lowest-risk cleanup — staging dirs are always transient.

2. **Committed file group cleanup** (local data dirs, S3 objects): More complex — requires cross-referencing file store contents against active queue messages. Strategy:
   - List all committed file groups in the store (those with a `.complete` marker for local, `.committed` for S3)
   - For each, check whether any queue message references it (requires scanning pending + in-flight message files for local queues, or maintaining a lightweight reference set)
   - Delete any committed file group older than the threshold that has no referencing message
   - **Risk**: must ensure the threshold is large enough that a file group committed but not yet published (the window between `commit()` and `publish()`) is not prematurely cleaned

3. **S3 cache cleanup**: Delete any `cache/` entry older than the threshold. Since cached files are re-downloadable from S3 on demand, this is always safe.

**Configuration:**

```yaml
pipeline:
  orphanCleanup:
    enabled: true
    intervalMinutes: 60
    maxStagingAgeMinutes: 60
    maxUnreferencedAgeMinutes: 1440  # 24 hours
```

**Implementation notes:**
- Start with (1) staging cleanup only — it's risk-free and addresses the most common orphan type
- (2) committed file group cleanup can be deferred as it requires more careful implementation
- The scanner should log every deletion at INFO level for audit trail
- Add a Prometheus counter (`stroom.proxy.pipeline.orphans.cleaned`) for visibility

# Future Work — Stroom Proxy Pipeline

Open items only. Work that has been completed has been removed from this list —
the design documents describe the built system, and git history records how it
got there. One item is kept despite being closed: §1, because "we deliberately
did not do this" is a live design decision that keeps getting re-proposed.

---

## Design Decisions Recorded

### 1. Retry Attempt Tracking — **DECIDED, 2026-09-07**

Decided with the queue rewrite; [infrastructure/queues.md §10 D2](infrastructure/queues.md#10-decisions).
Kept here because it has been re-proposed, and re-decided, more than once.

- **Local queue**: the proxy counts deliveries in the message and gives up at
  `maxDeliveryAttempts` into the queue's own `failed/` directory.
- **SQS**: the queue's redrive policy is the bound and its dead-letter queue is
  where a human acts. The proxy counts nothing; `maxDeliveryAttempts` on an SQS
  queue is a validation error.
- **Kafka**: the proxy carries the count in a header across a re-publish to the
  tail, and after `maxDeliveryAttempts` publishes the record to `<topic>.failed`,
  which it creates at start-up.

The principle that settled it: in shared mode a node may be scaled away with its
disk, so give-up must land in the broker's own infrastructure, where every node
and every operator can find it. The forward stage's downstream retry machinery is
a separate concern and tracks its own attempts on disk per file group — see
[infrastructure/forward-retry.md](infrastructure/forward-retry.md).

---

## Receipt

### 1a. Event Receipt in Shared Mode — **DESIGNED, 2026-09-15; not built**

**Priority**: High for any shared-mode deployment that receives single events.

Event receipt appends to files on the node's disk and acknowledges the sender once the append is
durable there. In shared mode a node may be removed for ever, and takes up to `maxAge` of
acknowledged events per feed with it, plus anything rolled and not yet received, plus its
quarantine. Ruled 2026-09-15: files are the local-mode queue; in shared mode an event is a Kafka
record keyed by feed, and a drain buffers records into batches and receives them, committing
only afterwards. The design is
[infrastructure/events-shared.md](infrastructure/events-shared.md), all of it decided; parked by
the owner on 2026-09-15. In the meantime a shared-mode node that receives logs a boot-time warning
naming the window (`EVENT_RECEIPT_NODE_LOCAL_IN_SHARED_MODE`).

---

## Testing

### 2. End-to-End Integration Test with Real Queues

**Priority**: Low

Per-queue contract tests exist against LocalStack and Testcontainers Kafka. A
full *pipeline* integration test using real external queues would additionally
verify:

- SQS visibility extension under actual network conditions
- Kafka consumer group rebalancing does not cause message loss
- S3 file stores against real (LocalStack) S3
- Multi-node write safety on shared filesystems

---

## Performance & Scalability

### 3. S3 Streaming Reads

**Priority**: Low

`S3FileStore.resolve()` downloads all files in a file group to a local cache
directory before returning a `Path`. The original rationale for streaming was to
reduce latency and disk pressure for large file groups.

**The realistic benefit is minimal.** Every stage processor reads the entire
file group — `proxy.meta`, `proxy.zip`, `proxy.entries` — and passes the
complete directory to the stage (the split-zip stage, the aggregate stage's merge,
`Forwarder::add`). There is no
partial or selective file access at any stage. Streaming would download exactly
the same bytes and merely bypass the cache.

The cache is actively **beneficial** for at-least-once delivery: on redelivery
after a crash, `resolve()` skips already-downloaded files
(`if (!Files.exists(localFile))`), avoiding redundant `GetObject` calls.

**Practical alternative**: the real concern is disk pressure from accumulated
cache entries, better addressed by size- or time-based eviction on the cache
directory, and by cleaning cache entries when the stage deletes the
corresponding `FileStoreLocation`. Overlaps with §10.

Changing `resolve()` from `Path` to `InputStream` would require deep changes to
every production handler for marginal benefit.

### 4. S3 Multipart Upload for Large File Groups

**Priority**: Low

For file groups containing very large zip files, multipart upload would improve
reliability and throughput. The AWS Transfer Manager already supports it;
`S3FileStore` could enable it via configuration.

### 5. Local Queue Multi-Process Consumers

**Priority**: Low

`LocalFileGroupQueue` supports multiple **threads** within one process —
`next()` uses `Files.move(ATOMIC_MOVE)` as a lock-free competing-consumer
mechanism, handling races via `NoSuchFileException` retry loops. It does **not**
safely support multiple **processes** consuming the same queue directory:
startup recovery (`recoverInFlightMessages`) moves all in-flight items back to
pending, which would interfere with items another JVM is actively processing.

Options for scaling a single stage across processes on local storage:

- File-based locking for multi-process consumption, coordinating recovery
- An embedded cross-process queue (e.g. SQLite-backed)

In practice, multi-process deployments should use SQS or Kafka — see
[deployments/split-stage-workers.yml](deployments/split-stage-workers.yml).

### 6. Per-Destination Delivery State Is Not Durable

**Priority**: Low

A fan-out retry no longer re-delivers to destinations that already succeeded —
see [stages/forward.md](stages/forward.md). The record of which destinations are
done is held in memory, so it is lost on restart and a destination may then see a
duplicate.

That is deliberate: at-least-once permits duplicates, and making the record
durable would amount to exactly-once delivery, which the pipeline explicitly does
not attempt. It is noted here only so the limit is written down rather than
discovered.

Two related gaps remain, both bounded rather than eliminated:

- The failing destination still accumulates one committed-but-unpublished file
  group per attempt in its own store. Ordinary orphan behaviour, now at the
  retry-backoff rate rather than unbounded — see §10.
- Split-zip re-publishes mint fresh `messageId`s for each split, so
  duplicate splits carry no stable key and cannot be correlated with the
  originals. Giving splits a derived, stable id would be a prerequisite for
  treating them the same way.

---

### 7. Backpressure Between Stages

**Priority**: Low

If a downstream stage is overwhelmed — slow forwarding, most commonly —
upstream stages keep producing. Consider:

- Queue depth monitoring with configurable high-water marks
- Receive stage throttling when downstream depths exceed thresholds
- HTTP 503 to senders when the pipeline is saturated

---

## Observability

### 8. Pipeline Topology Dashboard

**Priority**: Medium

The monitoring servlet shows queue health, depths, heartbeat stats and error
highlighting. A visual topology dashboard would make the pipeline legible at a
glance:

- All configured stages with enabled/disabled status
- Queue types and depths between stages (partially done — depths for local
  queues only)
- File store types and disk/S3 usage
- Per-stage throughput (items/sec) derived from the Prometheus metrics

---

## Configuration & Deployment

### 9. Operational Deployment Guides

**Priority**: Medium
**Origin**: Original design plan

[operations.md](operations.md) covers configuration reference and monitoring,
and [deployments/](deployments/) now holds validated sample configurations for
the common topologies. Still missing:

- AWS deployment with Terraform/CloudFormation templates for the SQS queues and
  S3 buckets the samples assume
- Kubernetes deployment with shared PVC for local filesystem stores
- Monitoring and alerting setup (Prometheus/Grafana dashboards)
- Capacity planning guidelines (queue sizing, thread tuning, disk/S3 budgets)
- Disaster recovery procedures (queue drain, store backup/restore)

### 10. Orphaned File Cleanup — **DECIDED, 2026-09-07**

Decided with the file-store rewrite; the mechanism differs by mode and is in
[infrastructure/file-stores.md §5.5](infrastructure/file-stores.md#55-clearing-what-is-left-behind).

- **Local mode**: at start-up, and never on a timer. The one process is the only
  writer, so staging is cleared unconditionally and every committed group that no
  message in any local queue names is deleted, by exact comparison.
- **Shared filesystem**: the proxy's own scheduled sweep, run by every node,
  deleting anything under the store older than the store's required `orphanAge`.
- **S3**: an S3 lifecycle rule the operator configures; the proxy runs nothing.

Not covered, deliberately: a forward destination's give-up destination
(`50_forwarding/*/03_failure` by default) and the dir-scanner failure directory are
quarantines and must not be swept — see
[data-path.md §5](data-path.md#5-where-data-can-accumulate).

### 11. Configuration Validation Improvements

**Priority**: Low

`ProxyPipelineConfigValidator` validates queue and file store definitions and
stage references. Additions worth making:

- Warn when a stage's input queue is `LOCAL_FILESYSTEM` but the deployment is
  multi-node — likely a misconfiguration that should use SQS/Kafka
- Warn when a file store is `LOCAL_FILESYSTEM` with consumer threads > 1 and no
  shared filesystem configured
- Validate SQS visibility timeout against expected processing duration

An incomplete `stages` block is now a `STAGE_NOT_CONFIGURED` error, and each
disabled stage raises a `STAGE_DISABLED` warning, so a process doing less than
intended is visible at startup. Both checks are per-process and cannot tell
whether *another* process consumes a stranded queue — a cluster-aware check
would need to see the whole deployment's configuration.

### 12. Dynamic Configuration Reload

**Priority**: Low

Pipeline configuration is read at startup. For long-running proxies it would be
useful to support thread count changes, stage enable/disable, and new forwarding
destinations without a restart. Requires careful lifecycle management to drain
in-flight work before reconfiguring.

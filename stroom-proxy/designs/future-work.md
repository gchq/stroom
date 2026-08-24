# Future Work — Stroom Proxy Pipeline

Open items only. Work that has been completed has been removed from this list —
the design documents describe the built system, and git history records how it
got there. One item is kept despite being closed: §1, because "we deliberately
did not do this" is a live design decision that keeps getting re-proposed.

---

## Design Decisions Recorded

### 1. Retry Attempt Tracking — Not Planned

**Priority**: Not recommended
**Origin**: Original design plan (optional)

Originally proposed adding an `attempt` field to `FileGroupQueueMessage` for
retry visibility and dead-letter routing. After review, this adds complexity for
minimal value:

- **SQS** already tracks `ApproximateReceiveCount` natively and supports
  dead-letter queues via redrive policies — the idiomatic AWS approach, and it
  requires zero application code.
- **Kafka** has its own retry and DLQ mechanisms (e.g. error topic routing via
  consumer configuration).
- **Local queues** are consumed within the same JVM, so persistent failures
  indicate code bugs rather than transient issues worth counting. The existing
  `failed/` directory with `.last-error.txt` files gives sufficient diagnostics.

Custom attempt tracking across three backends with three different retry
semantics would duplicate what the queue infrastructure already provides.
Operators should configure retry/DLQ policies at the queue level.

Note this applies to the *pipeline* queues only. The forward stage's downstream
retry machinery is a separate concern and does track attempts, on disk, per file
group — see [infrastructure/forward-retry.md](infrastructure/forward-retry.md).

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
complete directory to a production handler (`PreAggregator::addDir`,
`Aggregator::addDir`, `Forwarder::add`, `ZipSplitter::splitZip`). There is no
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
- Split-zip re-publishes assign fresh random `fileGroupId`s to each split, so
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

### 10. Orphaned File Cleanup

**Priority**: Medium

In normal operation the ownership-transfer contract ensures all files are
eventually consumed and deleted. A hard crash (power loss, `kill -9`) at
specific points can leave files on disk or in S3 that no queue message
references.

| Location | Cause | What's left |
|---|---|---|
| `LocalFileStore` `writing/` | Crash during `newWrite()` before `commit()` | Uncommitted staging dirs (`write-*`) |
| `LocalFileStore` data dirs | Crash after `commit()` before `publish()` | Committed group with no message referencing it |
| `S3FileStore` `staging/` | Crash during upload before `commit()` | Local staging files; possibly partial S3 objects |
| `S3FileStore` `cache/` | Message dead-lettered externally, `delete()` never called | Cached downloads from `resolve()` |
| `S3FileStore` S3 objects | As the local commit-before-publish case | Committed objects with no message |
| `LocalFileGroupQueue` `tmp/` | Hard kill during `publish()` before the atomic move | Temporary JSON files |
| `AggregateClosePublisher` | Crash after output `commit()` + `publish()` but before `deleteRecursively(aggregateDir)` | Source aggregate dir (data is safe — already published) |

**None of these lose data.** At-least-once holds because input messages are
redelivered. The cost is wasted space only.

**Proposed strategy — periodic orphan scanner.** A background scheduled task
(hourly, say) that:

1. **Staging cleanup** (`writing/`, `staging/`, `tmp/`) — delete any staging
   directory or temp file older than a configurable threshold. No write should
   take more than minutes, so anything older is safely orphaned. Lowest risk;
   start here.
2. **Committed file group cleanup** — requires cross-referencing store contents
   against active queue messages. Note there is no completeness marker to key
   off: `LocalFileStore` treats presence of the directory under `<writerId>/` as
   committed, and `S3FileStore` treats presence of objects under the file group
   key. So the scan must enumerate the store, check whether any pending or
   in-flight message references each group, and delete unreferenced groups older
   than the threshold. The threshold must exceed the `commit()`-to-`publish()`
   window or it will delete live data.
3. **S3 cache cleanup** — delete cache entries older than the threshold. Always
   safe; they are re-downloadable. Overlaps with §3.

```yaml
pipeline:
  orphanCleanup:
    enabled: true
    intervalMinutes: 60
    maxStagingAgeMinutes: 60
    maxUnreferencedAgeMinutes: 1440  # 24 hours
```

Log every deletion at `INFO` for audit, and add a
`stroom.proxy.pipeline.orphans.cleaned` counter.

Note this covers *pipeline* stores only. The forward-stage quarantine
(`50_forwarding/*/03_failure`) and the dir-scanner failure directory are
deliberate quarantines and must not be swept — see
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

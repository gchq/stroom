# Stroom Proxy — Design Documentation

Design and operations documentation for the Stroom Proxy pipeline: the staged
architecture that receives data, splits multi-feed zips, aggregates, and
forwards file groups to downstream Stroom instances.

The defining characteristic of this architecture is that stages are joined by
**pluggable queues carrying reference messages**, with **durable file stores**
holding the actual data. Because queue messages are small references rather than
data, the queue backend can be a local directory, SQS, or Kafka without regard
to payload size — and stages can therefore run in one process or be spread
across many.

## Where to Start

| If you want to… | Read |
|---|---|
| Know what must always be true, and who is responsible | [contracts.md](contracts.md) |
| Understand the architecture | [architecture.md](architecture.md) |
| Follow data end to end, or work out what a directory on disk is for | [data-path.md](data-path.md) |
| Configure, deploy, or monitor a proxy | [operations.md](operations.md) |
| Copy a working configuration | [deployments/](deployments/) |
| Understand one stage in detail | [stages/](stages/) |
| Understand queues, stores, or the runtime | [infrastructure/](infrastructure/) |
| Know what is not yet built | [future-work.md](future-work.md) |
| Know why it was built this way | [history/](history/) |

## Contents

### Core

- **[contracts.md](contracts.md)** — the invariants that hold *across* the seams
  between components: ownership on hand-off, work-before-acknowledgement, what a
  consumer must tolerate, configuration, give-up and replay, and durability.
  Carries the standing rulings, and marks every place the code does not yet meet
  them. **Read this before changing anything at a boundary** — most of what it
  records was decided after a fix got the boundary wrong.
- **[architecture.md](architecture.md)** — the pipeline: design principles,
  package structure, the ownership-transfer protocol, key data structures, the
  configuration model, and the thread model.
- **[data-path.md](data-path.md)** — the whole path from inbound byte to
  forwarded file group, the on-disk layout, and where data can accumulate.
- **[review-angles.md](review-angles.md)** — the thirteen readings a review is
  made of. Use it to review a layer after it is rewritten to its contract, and
  to audit your own fix before calling it done.
- **[stress-testing.md](stress-testing.md)** — the fault-injection suite: what
  it makes real, where it injects failures, the invariants it asserts, why a
  stall is not a loss, and how the tests are proved capable of failing.
- **[operations.md](operations.md)** — operator-facing: the supported use cases
  and how each maps onto stage enablement, queue and file store
  types, configuration reference, deployment examples, the worker thread model,
  health checks, Prometheus metrics, structured logging, and the admin
  monitoring endpoint.

### Stages

Each document covers one stage with class diagrams, sequence diagrams and
field-level detail.

- [stages/receive.md](stages/receive.md) — accept data and introduce it to the pipeline
- [stages/split-zip.md](stages/split-zip.md) — split multi-feed zips
- [stages/aggregate.md](stages/aggregate.md) — aggregation: one stage whose open aggregate is the set of held claims; built 2026-09-09
- [stages/pre-aggregate.md](stages/pre-aggregate.md) — superseded; points at aggregate.md
- [stages/forward.md](stages/forward.md) — forwarding, retry and give-up: a group stays claimed until delivered or given up on; built 2026-09-14

### Infrastructure

- [infrastructure/queues.md](infrastructure/queues.md) — `LocalFileGroupQueue`,
  `SqsFileGroupQueue`, `KafkaFileGroupQueue`, the message codec and the factory
- [infrastructure/file-stores.md](infrastructure/file-stores.md) —
  `FilesystemFileStore`, `S3FileStore`, the registry and the factory
- [infrastructure/runtime.md](infrastructure/runtime.md) — assembly, the runtime
  model, workers, health checks and metrics
- [infrastructure/entry-points.md](infrastructure/entry-points.md) — HTTP
- [infrastructure/events.md](infrastructure/events.md) — event receipt: batching single events into files the receiver takes (local mode)
- [infrastructure/events-shared.md](infrastructure/events-shared.md) — event receipt in shared mode: a topic as the queue, a drain that batches it; **proposed 2026-09-15, not built**
  datafeed, directory scanner, event store, SQS connector, and the
  processing-user contract they share
- [infrastructure/forward-retry.md](infrastructure/forward-retry.md) — superseded; points at stages/forward.md

### Deployments

Complete, validated sample configurations. Each parses as a `proxyConfig` block
and passes `ProxyPipelineConfigValidator` with no errors.
`split-stage-workers.yml` additionally raises four informational
`STAGE_DISABLED` warnings, one per stage that node deliberately does not run, and the two shared
examples raise `EVENT_RECEIPT_NODE_LOCAL_IN_SHARED_MODE`, since single-event receipt is still
node-local there ([infrastructure/events-shared.md](infrastructure/events-shared.md)).

- [deployments/single-process.yml](deployments/single-process.yml) — everything
  on one node with local queues and stores; the default shape, written out in full
- [deployments/sqs-s3-distributed.yml](deployments/sqs-s3-distributed.yml) — AWS
  with SQS queues and S3 file stores
- [deployments/kafka-distributed.yml](deployments/kafka-distributed.yml) —
  on-premise with Kafka queues and a shared filesystem
- [deployments/split-stage-workers.yml](deployments/split-stage-workers.yml) —
  one process per stage role, using `enabled` to partition responsibility

See also `stroom-proxy-app/proxy-pipeline.yml`, an annotated reference of the
whole `pipeline` block.

### Planning History

[history/](history/) holds the implementation plans this architecture was built
from. They are superseded by the documents above and are kept for the rationale
they record, not as a description of the current system.

## Conventions

- Diagrams are Mermaid, embedded in the Markdown. There are no separate diagram
  source files to keep in sync.
- Class and configuration names are given exactly as they appear in the code, so
  they can be grepped.
- Documents describe what is built. Anything proposed but not implemented
  belongs in [future-work.md](future-work.md), clearly marked.

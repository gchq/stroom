# Queues

[← Design documentation](../README.md) · [Contracts](../contracts.md) · [File stores](file-stores.md)

**Status: target design, agreed 2026-09-07.** This describes the queue layer as it is to be built.
[§9](#9-where-the-current-code-diverges) lists what the current implementation does differently;
[§10](#10-decisions) records the decisions taken and what each overturned.

---

## 1. What a queue is for

A queue carries **messages that name file groups** from the stage that wrote them to the stage
that consumes them. The message is small - a store name, a complete location, a few ids - and the
data it names is in a [file store](file-stores.md). That is what lets the queue be a directory on
disk, an SQS queue or a Kafka topic without regard to payload size, and lets consecutive stages
run in one process or many.

A queue promises one thing: **a published message is delivered, at least once, to some consumer of
that queue, until a consumer acknowledges it.** Everything else in this document exists to make
that true through process death, power loss and, in shared mode, the disappearance of the node
that was working on it.

This document covers `FileGroupQueue`, the transport between stages and the only queue the proxy
has: forwarding, which used to keep a directory queue of its own, now holds its claims on this one
([stages/forward.md](../stages/forward.md)).

## 2. The two modes

The mode is the deployment's ([file-stores.md §2](file-stores.md#2-the-two-modes)); every queue fits it.

| | **Local** | **Shared** |
|---|---|---|
| Queue | A directory on this node's disk | An SQS queue or a Kafka topic |
| Owned by | This one process, which takes a lock on the directory | The broker |
| A message is claimed by | Renaming its file from `pending/` to `in-flight/` | Receiving it, with a visibility timeout (SQS) or as the next record of an assigned partition (Kafka) |
| A claim that is never completed is released | When the process restarts, or when the item is closed without completing | By the broker: visibility expiry, or partition rebalance |
| Who recovers it | This node | Any node |
| Give-up lands in | `failed/` on this node's disk, beside the queue | The broker's own dead-letter mechanism, visible to every node |
| Durability of a publish | This process forces the message and the directory entry that publishes it | The broker's acknowledgement |

The single-owner model is what makes the local queue simple: it never has to guess whether a
consumer is alive, because every consumer is a thread of the process that holds the lock. The
distributed backends never know that, and do not try to; the broker's lease is the mechanism, and
the proxy only has to keep it alive while it works.

## 3. The contract

What must always be true of a queue, in every mode.

**Q1. A publish is durable before it returns.** On disk: the message bytes and the directory
entry that publishes them, forced as `proxyConfig.durability` says. On a broker: the broker's
acknowledgement. A publish that returns has happened; the caller may delete its input.

**Q2. A message carries a complete location and nothing node-relative.** Every node the mode
allows to consume it can resolve what it names ([file-stores.md C5](file-stores.md#3-the-contract)).

**Q3. A claimed message is delivered to one consumer at a time.** Two consumers never hold the
same message at once - within a process, across processes, or across nodes.

**Q4. No message is claimed for ever.** A claim its holder never completes - the process died, the
node was scaled away, `acknowledge()` threw - becomes deliverable again: in local mode when the
process restarts or the item is closed, in shared mode when the broker's lease lapses. Liveness is
the requirement; who held the claim is only a diagnostic.

**Q5. Work precedes acknowledgement, and the queue makes that possible.** An item is acknowledged
only by its holder, only after it says so, and the message is gone only then. The queue never
acknowledges on the consumer's behalf.

**Q6. A failure redelivers, later, with the attempt counted.** `fail()` puts the message back at
the tail, not the head - one message the pipeline cannot process must not stop the queue - and
the delivery attempt count travels with it.

**Q7. Give-up is bounded and lands somewhere a human can act, in the mode's own infrastructure.**
After `maxDeliveryAttempts` a message leaves the queue and is recorded: in local mode in `failed/`
beside the queue; in shared mode in the broker's dead-letter mechanism, because a node's disk is
not somewhere any other node, or an operator, can rely on finding it.

**Q8. Absence of data is not a failure.** A consumer whose input resolves to nothing acknowledges.
This is the consumer's rule, stated here because it is what makes Q4's redelivery safe under Q5's
ordering ([contracts.md §2.6](../contracts.md#26-absence-of-data-means-the-work-was-done)).

**Q9. Order is best effort.** A queue is FIFO by intent and a retry breaks it by design (Q6). No
stage may depend on order.

**Q10. Nothing depends on `close()`.** A process killed outright loses no message and strands no
claim for longer than the mode's release (Q4).

## 4. The interface

```java
public interface FileGroupQueue extends AutoCloseable {
    String getName();
    QueueType getType();
    void publish(FileGroupQueueMessage message) throws IOException;            // Q1
    Optional<FileGroupQueueItem> next(Duration maxWait) throws IOException;    // Q3
    HealthCheck.Result healthCheck();
    void close() throws IOException;
}

public interface FileGroupQueueItem extends AutoCloseable {
    FileGroupQueueMessage getMessage();
    int getDeliveryAttempt();                       // 1 on first delivery
    void acknowledge() throws IOException;          // Q5: the message is done
    void fail(Throwable error) throws IOException;  // Q6/Q7: redeliver later, or give up
    void close() throws IOException;                // Q4: release without completing
}

public record FileGroupQueueMessage(
        int schemaVersion,
        String messageId,
        FileStoreLocation location,
        String feed,             // the aggregation key, when the group is single-feed
        String type,
        String producingStage,
        String producerId,
        Instant createdTime,
        String traceId,
        Map<String, String> attributes) { }
```

**The aggregation key travels with the message.** Every producer knows it at publish time -
receive knows the feed of a single-feed group, split-zip emits one group per feed, an aggregate
close is per feed - and a multi-feed group (receive → split-zip) carries none. It exists so that a
partitioned backend can land every part of a feed on the same consuming node: an open aggregate is
the set of inputs one claimer holds, and affinity is what lets it fill rather than fragment across
nodes ([stages/aggregate.md §4.2](../stages/aggregate.md#42-one-claimer-a-pool-of-mergers)).

**A consumer may hold many claims at once.** Nothing in Q3 to Q5 says one, and the aggregate stage
holds every input of an open aggregate until the aggregate is published. Each backend must therefore
let claims complete in any order without committing past one still held; how each does is in §5.

**What changes.** `close()` on an item that was neither acknowledged nor failed **releases the
claim so the message is delivered again** - to disk that is a move back to `pending/`, on Kafka a
seek back, on SQS nothing (the visibility timeout does it). That is the one thing that lets the
local queue drop every piece of lease bookkeeping: an item is claimed exactly while a consumer
holds it, and a consumer that stops holding it without completing it releases it, so there is
never an in-flight message with nobody responsible for it while the process lives. A process that
dies leaves them to start-up recovery.

**What goes.** `queueName` inside the message - the queue knows its own name. `fileGroupId` -
every stage mints a fresh one on commit, so it identifies the message, and `messageId` already
does that; as an MDC field `messageId` replaces it, and as the Kafka key the aggregation key does
([§10 D4](#10-decisions)).

**Two conveniences stay.** `next()` with no wait is `next(Duration.ZERO)`, for callers that only
want what is there now. `FileGroupQueueItem.getId()` is the backend's own identity for the
delivery - a file id, an SQS message id, a topic-partition-offset - for logs.

## 5. Design

### 5.1 Local queue

```
<root>/
├── .queue-owner.lock          <- held for the process's life; a second process is refused
├── pending/<id>.json          <- deliverable, FIFO by id
├── in-flight/<id>.json        <- claimed by a consumer thread of this process
├── failed/<id>.<reason>.<millis>.json   (+ .error.txt)   <- given up on
└── tmp/                       <- a publish in progress
```

**Ids** are a per-queue sequence, 20 digits zero-padded so names sort, seeded at start-up from the
highest id in `pending/`, `in-flight/` and `failed/`. There is no persisted counter: nothing
depends on ids being monotonic across a restart of a drained queue, and a counter that can be lost
or restored out of step is a thing to defend against rather than rely on.

**Publish.** Write to `tmp/`, force the bytes if `durability` says so, rename into `pending/`,
force `pending/` if `durability` says so. The rename refuses to overwrite: an id collision is a loud
failure, never a lost message.

**Take.** List `pending/`, take the lowest id, rename it into `in-flight/`. A thread that loses the
rename to another thread tries the next. That rename is the whole claim; there is no lease set.
Consumers wait on a condition that `publish` signals, so an idle queue costs nothing and a new
message is taken at once.

**Acknowledge.** Delete the in-flight file.

**Fail.** If the attempt count has reached `maxDeliveryAttempts`, move the file to `failed/` with
the error beside it. Otherwise write the message back to `pending/` under a *new* id with the count
incremented (Q6), then delete the in-flight file - in that order, so a crash between the two costs
a duplicate rather than a loss.

**Close without completing.** Move the in-flight file back to `pending/` under a new id with the
count incremented. This is the path taken when `acknowledge()` or `fail()` threw; if the move
throws too the file stays in `in-flight/` and start-up recovery takes it.

**Start-up.** Take the ownership lock, or refuse to start. Move every `in-flight/` file back to
`pending/` with its count incremented - a crash mid-processing was an attempt. Clear `tmp/`. Then,
for the local-mode orphan sweep, read every message in `pending/`, `in-flight/` and `failed/` for
the location it names; a message that cannot be read stops the sweep, since the group it names
would otherwise be deleted - except one this queue itself quarantined as `invalid-message`, which
never named a group the proxy could resolve and is skipped rather than allowed to stop the sweep on
every start until an operator removes it.

**The attempt count** travels as a message attribute, rewritten on each requeue. That is what lets
a requeue take a new id and go to the tail.

**Durability** is `proxyConfig.durability`, which governs queue messages and directory-queue
hand-offs and nothing else ([file-stores.md §6](file-stores.md#6-durability) for the stores).

### 5.2 SQS

The broker owns the claim. `next()` receives one message with the configured `visibilityTimeout`
and starts a heartbeat that extends it while the item is held, so a slow stage is not redelivered
mid-work; `acknowledge()` stops the heartbeat and deletes the message; `close()` without
completing stops the heartbeat and lets the timeout lapse.

**Fail** makes the message visible again at once and lets SQS count the receive. A message that
cannot be decoded is never acknowledged, so it too reaches the dead-letter queue by the redrive
policy. **Give-up is SQS's**: the queue is configured with a redrive policy whose `maxReceiveCount` is the bound, and
the dead-letter queue is where a human acts. The proxy neither counts attempts nor writes anything
to local disk for an SQS queue; `maxDeliveryAttempts` does not apply to it, and the validator says
so if it is set.

**Retention is SQS's too, and it is checked at start-up.** SQS deletes a message
`MessageRetentionPeriod` after it was *sent* - four days by default, fourteen at most - and a
visibility change does not restart that clock. Since `fail()` here is a visibility change, a
retention shorter than the forward retry window would let SQS delete a message still being retried,
silently, and leave the group it names for the store's sweep. So, as the Kafka queue checks its
dead-letter topic, the SQS queue reads the queue's attributes once when it is built and refuses to
start if there is no redrive policy or the retention does not exceed the longest forward
`maxRetryAge` (`SqsFileGroupQueue.requireQueueFitsPipeline`, R9). A dead-letter queue keeps the
message's original send time, so give it a longer retention than the queue it serves.

The heartbeat has a ceiling too: SQS will not keep a message invisible for more than twelve hours
from the receive, however often its visibility is extended. A claim held past that is redelivered
to another node while this one still has it, and the work is duplicated. The validator warns
(`SQS_HOLD_NEAR_VISIBILITY_CEILING`) when a computable hold - `aggregationFrequency` on an SQS
input, or a forward destination's longest back-off wait on an SQS forward queue - is more than half
the ceiling, since the time on the queue before the claim and the work after the hold are not
computable.

### 5.3 Kafka

Each consumer thread has its own `Consumer` in one group; Kafka assigns partitions across them and
a rebalance is how another node takes over. Auto-commit is off; `acknowledge()` commits the offset
past the record. The assignor is `CooperativeStickyAssignor` unless the consumer config says
otherwise: a consumer holds records for minutes - every input of an open aggregate - and a revoked
partition drops its ledger, so under the eager protocol every node joining the group would redeliver
every held record on every node. Cooperative rebalancing revokes only the partitions that move.

A partition is a sequence, so one record that cannot be processed must not block the ones behind
it. **Fail** re-publishes the record to the tail of the topic with the attempt count in a header,
then commits past the original - a duplicate is possible if the process dies between the two, and
accepted. **Give-up** after `maxDeliveryAttempts` publishes the record to the topic's dead-letter
topic, `<topic>.failed`, and commits past it; the proxy creates that topic at start-up if it can and
refuses to start if it cannot and it does not exist. An undecodable record goes the same way.

**Many held records, contiguous commit.** A consumer thread keeps, per partition, a ledger of the
records it has polled and not completed. Completing one - acknowledge, fail, dead-letter - commits
the offset up to the first record still held, or one past the highest completed when none is, so a
record is never committed past while an earlier one is open and a crash redelivers from the oldest
open record. `close()` without completing seeks back to the record, which is positional and so
releases every later record this consumer holds on the partition too; the ledger drops them and
the items that held them become stale, so their later completion touches nothing. Only `close()`
releases: a `fail()` whose republish fails throws and leaves the record held. A partition revoked
in a rebalance drops its ledger the same way, since its records now belong to whoever is assigned
it, from the committed position. A record may only be completed or released on the thread that
polled it, which the Kafka consumer enforces.

**The partition key is the aggregation key** - `feed` and `type` - when the message carries one,
and `messageId` when it does not. Records for one feed therefore go to one partition, one consumer,
one node, and that node's aggregate claimer sees all of them. Parallelism is bounded by the partition
count, and a very hot feed is bounded by one consumer, which is the trade.

### 5.4 Worker

One loop per consumer thread: `next(wait)` → process → `acknowledge()`, or `fail(error)` on any
exception, or `acknowledge()` on `FileGroupNotFoundException` (Q8); `close()` in a `finally`. The
worker is the only caller of the item API and this is the whole of what it does with it.

## 6. How the queue and the store meet the at-least-once contract together

The protocol is fixed: resolve → work → commit output → publish → delete input → acknowledge. The
store guarantees commit and absence-reporting; the queue guarantees Q1 for the publish and Q4/Q5
for the acknowledgement. The one window neither closes - input deleted, acknowledgement lost - is
resolved by Q8, and it is the price of never acknowledging first.

| Interrupted after | Local mode | Shared mode |
|---|---|---|
| take, before acknowledge or fail | restart moves it back to `pending/`, attempt +1 | visibility lapses or the partition rebalances; another node takes it |
| `fail()`'s requeue, before its delete | the message is in `pending/` twice; two deliveries, two duplicates downstream | Kafka: same, via the re-publish; SQS: n/a, fail is one call |
| `acknowledge()` threw | `close()` requeues it; if that throws too, restart does | the broker's lease lapses |

## 7. Configuration

```yaml
pipeline:
  mode: LOCAL
  queues:
    aggregateInput:
      type: LOCAL_FILESYSTEM
      path: /data/proxy/queues/aggregateInput   # optional; derived from the name
      maxDeliveryAttempts: 100
```

```yaml
pipeline:
  mode: SHARED
  queues:
    splitZipInput:
      type: SQS
      queueUrl: https://sqs.eu-west-2.amazonaws.com/123/proxy-split-zip
      visibilityTimeout: PT5M
      waitTime: PT20S
      # give-up is the queue's redrive policy; maxDeliveryAttempts is rejected here
    aggregateInput:
      type: KAFKA
      topic: proxy-aggregate-input
      bootstrapServers: broker:9092
      maxDeliveryAttempts: 100                      # give-up publishes to proxy-aggregate-input.failed
```

| Property | Local | SQS | Kafka |
|---|---|---|---|
| `maxDeliveryAttempts` | yes | **rejected** - SQS redrive policy instead | yes |
| Retention | none: the file is the record | **checked at start-up**: `MessageRetentionPeriod` must exceed the longest forward `maxRetryAge`, and a redrive policy must exist | `retention.ms` is the operator's; a failed record is republished, which restarts it |
| `abandonedLeaseScanInterval` | **gone** | - | - |
| `visibilityTimeout`, `waitTime` | - | yes | - |
| `topic`, `bootstrapServers`, `producer`, `consumer` | - | - | yes |

## 8. What is deliberately not here

- **A persisted sequence counter.** See §5.1.
- **Lease tracking in the local queue.** Q4 is met by `close()` and start-up recovery; a set of
  live ids, a reclaim scan, a scan interval and a "leaked after 24 hours" fallback all existed to
  reclaim an item whose consumer stopped holding it without completing it, and `close()` is that
  consumer's own release.
- **Local quarantine for a distributed queue.** Q7: a node's disk is not shared infrastructure.
- **A visibility heartbeat for Kafka.** `max.poll.interval.ms` is the lease and is configured
  generously; a stage that exceeds it is redelivered, which is Q4 working.
- **Nested numbering for `pending/`.** FIFO needs the lowest id cheaply, and a directory of message
  files is not a directory of file groups; a backlog of a hundred thousand small files in one
  directory is well within what a modern filesystem lists quickly. Revisit if it is not.

## 9. What the implementation before this design did

Kept for the record; none of it is in the tree.

| Design | Previous code |
|---|---|
| §4: `close()` without completing releases the claim | Local: `close()` only removes the id from a lease set; the file stays in `in-flight/` until a reclaim scan or a restart. Kafka: seeks back (as designed). SQS: cancels the heartbeat (as designed) |
| §5.1: no lease set, no reclaim scan | `activeLeases`, `reclaimAbandonedLeases`, a scan interval, a scan-in-progress flag and a 24-hour staleness fallback - roughly 300 of the local queue's 1,236 lines |
| §5.1: no persisted counter | `sequence.txt`, written on close and reconciled against a directory scan at start-up |
| §4: no `queueName` or `fileGroupId`; `feed` and `type` carried | Both present and neither of the new fields; `queueName` is validated on publish, `fileGroupId` is the Kafka key and an MDC field |
| §5.3: Kafka partitions by aggregation key | Partitions by `fileGroupId`, a fresh UUID per group, so a feed's parts scatter across nodes |
| §5.2: SQS give-up is the redrive policy | The proxy reads `ApproximateReceiveCount`, and on exhaustion deletes the message and writes it to `failed/` on the node's local disk |
| §5.3: Kafka give-up is a dead-letter topic | Written to `failed/` on the node's local disk |
| §7: `maxDeliveryAttempts` rejected for SQS | Accepted and applied |
| §5.1: `tmp/` cleared at start-up | Not cleared |

## 10. Decisions

Taken 2026-09-07.

| | Decision | What it overturns |
|---|---|---|
| **D1** | **`close()` releases the claim.** An item closed without `acknowledge()` or `fail()` is requeued with its attempt counted (local), sought back (Kafka) or left to time out (SQS). An item is claimed exactly while a consumer holds it | The local queue's lease set, reclaim scan, scan interval and 24-hour staleness fallback, and the `abandonedLeaseScanInterval` property |
| **D2** | **Give-up on a distributed queue is the broker's.** SQS: the operator's redrive policy and dead-letter queue; `maxDeliveryAttempts` is rejected for an SQS queue. Kafka: after `maxDeliveryAttempts` the record is published to `<topic>.failed`, which the proxy creates at start-up if it can | `QueueQuarantine` writing to the node's local disk for SQS and Kafka, and the proxy counting SQS receives |
| **D3** | **No persisted sequence counter.** Ids are seeded from the queue's own directories at start-up | `sequence.txt` and its reconciliation |
| **D4** | **`fileGroupId` and `queueName` leave the message; `feed` and `type` join it.** `messageId` is the MDC field. **Kafka partitions by the aggregation key** when present, so every part of a feed lands on one aggregating node, and by `messageId` otherwise | Partitioning by a fresh UUID per group, which scattered a feed's parts across nodes and fragmented its aggregates |
| **D5** | **`pending/` stays flat**, sortable 20-digit names in one directory, for a cheap FIFO take | Nothing; stated so nesting is not assumed from the stores |
| **D6** | **Queue durability stays global** (`proxyConfig.durability`), governing queue messages and directory-queue hand-offs. *Taken on recommendation, not asked:* there is no case yet for per-queue durability, since a local queue is always on local disk | Nothing |
| **D7** | **A consumer may hold many claims, and Kafka commits the contiguous done prefix.** Added 2026-09-09 for the aggregate stage ([stages/aggregate.md D9](../stages/aggregate.md#8-decisions)): a per-partition ledger of held records; completing one commits up to the first still held; release is positional, drops every later held record, and happens only through `close()` - a `fail()` whose republish fails leaves the record held for its holder to release; a revoked partition drops its ledger | `acknowledge()` and `fail()` committing past their own record, which with several held would have committed past the ones still open, and `fail()` seeking back on a republish failure |


# Event receipt in shared mode

[← Design documentation](../README.md) · [Contracts](../contracts.md) · [Event receipt](events.md) · [Queues](queues.md) · [Receipt](../stages/receive.md)

**Status: proposed 2026-09-15, not built.** Ruled 2026-09-15: appending to files is the local-mode
mechanism; in shared mode an event is appended to a queue, and a drain turns the queue into
received batches. This
document is the design to build it to. [events.md](events.md) stays the description of the local
path, and of everything the two paths share. [§8](#8-decisions) records what was decided and
what is left to decide.

---

## 1. Why the file appender is wrong for shared mode

[events.md](events.md) acknowledges an event once it is in a file on this node's disk (V1), and
promises it reaches the pipeline because "a crash at any point leaves the file where the next start
finds and receives it" (V2). That is the local single-owner model, and it is exactly the assumption
shared mode was defined not to make: a node may be removed for ever at any time
([file-stores.md §2](file-stores.md#2-the-two-modes)). A removed node takes with it every open
file - up to `maxAge` of acknowledged events per feed - every closed file the roll had not yet
received, and everything in `event/failed`. The sender has a receipt id for each of them.

The aggregate and forward rewrites removed the same shape - node-local state between an
acknowledgement and the pipeline - by making the state a claim on a shared queue
([aggregate.md §7](../stages/aggregate.md#7-what-the-implementation-before-this-design-did),
[forward.md §7](../stages/forward.md#7-what-the-implementation-before-this-design-did)). This
does the same for events, and it is the more natural fit: a stream of small records keyed by feed
is what a message queue is for, and the proxy already has one.

**The boundary.** In: what `EventStore.accept` does in shared mode, the drain that replaces the
roll there, the topic, their configuration and validation, and where a refused batch goes. Out:
the resource and the SQS connector, which call `accept` and are unchanged; the receiver, which is
handed a body exactly as today; the local path, which is unchanged.

## 2. The contract

The contract is events.md's, with V1, V2 and V4 re-founded on the broker rather than the disk.

**V1'. An acknowledged event is durable on the broker.** The resource returns a receipt id, and
the SQS connector deletes its message, only after the broker has acknowledged the record with
`acks=all`. Nothing about the event is on this node's disk when the sender is answered.

**V2'. An acknowledged event reaches the pipeline at least once, from any node.** A record is
committed past only after a batch containing it has been received - committed to the receive
store and published to the pipeline. Until then it is claimable by whichever node next holds its
partition. A node removed mid-batch loses nothing: its uncommitted records are redelivered and
batched again elsewhere. A duplicate batch is possible and accepted (R1).

**V3. The receipt policy decides at receipt.** Unchanged: `accept` applies it before publishing,
and the receiver applies it again at the batch, as it does for every body (events.md D4).

**V4'. A batch that cannot be received is never dropped, and lands where every node can find it.**
A batch the receiver *refuses* - a status that will not change with time - goes, record by record,
to the topic's dead-letter topic `<topic>.failed`, and is then committed past; the proxy creates
that topic at start-up exactly as it does for a pipeline queue
([queues.md §5.3](queues.md#53-kafka)). A batch that fails because the store or the pipeline queue
is unavailable is retried, with the records held uncommitted, and if that outlasts the consumer's
`max.poll.interval.ms` the partition moves and another node retries it. There is no node-local
quarantine in shared mode.

**V5'. The drain is a registry loop, not a schedule.** One thread per node, with its own consumer,
in `INGRESS`, as the aggregate claimer is: it polls continuously, and a batch closes on the same
bounds a file closes on today.

**V6'. Nothing on this node is the only record of anything.** The topic is the queue. What the
drain writes to local disk while a batch is open is a buffer, cleared at start-up like every other
node-local scratch (R11), and losing it costs a redelivery.

## 3. The interface

Nothing changes for callers. `EventStore.accept(attributeMap, receiptId, event)` keeps its
signature and its contract; what it does after the policy depends on the mode. Internally:

```java
/** Where accepted events go: a file per feed on local disk, or a topic. Chosen by the mode. */
interface EventSink {
    /** Durable when this returns, by the mode's guarantee (V1 / V1'). */
    void append(FeedKey feedKey, byte[] line) throws IOException;
}

/** The shared-mode drain: one thread per node with its own consumer, batching records by feed into receipts. */
final class EventDrain {
    EventDrain(Consumer<String, byte[]> consumer, Path bufferDir, EventBatchBounds bounds,
               Receiver receiver, SecurityContext securityContext, Producer<String, byte[]> deadLetter);
    /** Poll, buffer, close what is due, receive it, commit. Registered as the event-drain loop. */
    LoopTask task();
}
```

The line `append` is given is the one `EventSerialiser` produces today - one JSON object per event
carrying the receipt id, feed, type, headers and body - so a buffered batch is byte-for-byte the
file the local appender writes, and `readAttributeMap` and the receiver see no difference.

## 4. Design

### 4.1 Append: a record per event, keyed by feed

In shared mode `accept` does what it does today up to the serialised line, then
`producer.send(new ProducerRecord<>(topic, feedKey.partitionKey(), line)).get()` with `acks=all`
and idempotence on, and returns. The key is the feed key in the form
`FileGroupQueueMessage.partitionKey()` uses, so every event of a feed lands on one partition and
one consumer sees whole feeds, oldest first ([queues.md D4](queues.md#10-decisions)). A send that
fails is the sender's error, exactly as a failed file write is today: the resource maps it to a
status, the SQS connector leaves its message for redelivery.

One producer per process, shared by the resource threads and the SQS pollers; the Kafka producer
is thread-safe and batches across them.

### 4.2 Drain: one thread per node, a buffer per feed, the file's bounds

The node's one drain thread owns a consumer in the group and polls. Every record polled is appended -
value, then newline - to a buffer file for its feed key under `<dataDir>/event/buffer/`, opened
on first record exactly as `EventAppender` opens a file today, with no forcing, since the file
is not the record of anything. The record's offset joins a per-partition ledger of what this
thread holds uncommitted, which is `KafkaFileGroupQueue`'s `PartitionLedger`
([queues.md §5.3](queues.md#53-kafka), D7) over event records rather than reference messages:
extracted into the queue package and shared, not copied.

A buffer closes on the bounds a file closes on today - older than `maxAge`, `maxEventCount`
records, or the lesser of `maxByteCount` and `receive.maxRequestSize` bytes - checked on every
pass, as the aggregate claimer checks age. A closed buffer is received at once, on the drain
thread: `readAttributeMap` from its first line, then `receiver.receive(...)` as the processing
user with the file as the body, then every record the buffer held is completed in the ledger,
which commits the contiguous prefix, then the file is deleted. That order is
[contracts §2.1](../contracts.md#21-work-precedes-acknowledgement): the batch is in the receive
store and on the pipeline queue before any offset moves.

Receiving on the drain thread rather than handing off keeps a Kafka rule that the aggregate
stage already lives with: a record may only be committed on the thread that polled it. A receipt
is a store write and a queue publish - milliseconds locally, a few round trips on S3 - and the
poll that follows it is well inside `max.poll.interval.ms`.

### 4.3 What a node holds, and what its loss costs

While a buffer is open its records are uncommitted on the broker and its bytes are in a file on
this node. Kill the node: the file is gone, the records are redelivered from the committed
position to whoever is assigned the partition, and that node buffers them again. Nothing is lost;
the batch boundaries differ, which nothing depends on. Kill it after `receive` returned and before
the commit: the batch is in the pipeline and its records are redelivered - a duplicate batch,
which R1 accepts and which is the same window every stage has.

The number of open buffers on a node is the number of feeds that have sent to its partitions in
the last `maxAge`, as the number of open files is today. Their total size is bounded per feed by
the byte bound. That is disk, not memory, and it is scratch.

### 4.4 Failure at the receiver

A refusal - a `StroomStreamException` below 500, which the sender was told the equivalent of and
which will not change with time - dead-letters the batch: each record it held is published to
`<topic>.failed` with its key and headers, and then completed in the ledger. The proxy neither
counts refusals nor waits for three of them: the batch was refused on the headers it will carry
on every retry, and a node-local count would not survive to the second one anyway. `error` is
logged with the reason, as `KafkaFileGroupQueue` logs a give-up.

Anything else - the store or the pipeline queue unavailable - is retried on the drain thread with
the loop's own back-off, holding the buffer and its records, and not polling meanwhile. If the
outage outlasts `max.poll.interval.ms` the partition is revoked, the ledger dropped, the buffer
discarded, and another node - or this one, re-assigned - buffers the records again from the
committed position. That is the mode releasing a claim its holder could not complete (Q4), and it
is the same thing that happens to an aggregate claimer whose hand-off queue stays full.

### 4.5 Start-up and stop

Start-up clears `<dataDir>/event/buffer/`: everything in it belongs to a batch whose records are
still uncommitted, so it is scratch (R11). The drain's consumers join the group and are assigned
partitions from their committed positions. Nothing else.

Stop stops the resource's callers and the SQS pollers first (`INGRESS`, as today), then the
drain loop, then closes the producer and the consumers. A buffer open at stop is left to the
next start's clear and its records to the next assignment. A clean stop and a kill leave the same
state (R8).

### 4.6 The SQS connector

Unchanged. It calls `accept`, which now publishes, and deletes its SQS message when `accept`
returns - after the broker's acknowledgement, so V1' holds for that path too. An event refused by
the policy is left on its queue for the redrive policy, as today (events.md D3).

### 4.7 Configuration

```yaml
proxyConfig:
  eventStore:
    rollFrequency: PT10S        # local mode: the roll schedule. Shared mode: unused
    maxAge: PT1M                # both modes: when a file, or a buffer, closes
    maxEventCount: 100000
    maxByteCount: 10M
    queue:                      # shared mode: required. Local mode: refused
      type: KAFKA
      topic: proxy-events
      bootstrapServers: broker:9092
      producer: { }
      consumer: { }
```

There is no thread count: one drain thread per node is the rule (D9). A feed reaches one consumer
whatever the count, so a second thread could only spread a node's partitions across two consumers,
and a receipt is short. More nodes, not more threads, is how the drain scales.

`queue` reuses `QueueDefinition`, so the reserved-property rules, the dead-letter topic and the
consumer defaults - `max.poll.records`, auto-commit off, the cooperative assignor - come with it.
Explicit or fail (R14): in `SHARED` mode a missing `eventStore.queue`, or one that is not `KAFKA`,
is a validation error whenever event receipt is enabled; in `LOCAL` mode a stated one is an error.
`rollFrequency` in shared mode is a warning, since nothing reads it.

**Kafka only.** SQS is not an option for this queue and is refused rather than allowed to degrade:
an SQS message is at most 256 KB where an event is bounded by `receive.maxRequestSize`; SQS has no
key, so a drain would see every feed's events interleaved across every node and batches would
fragment as aggregates do on SQS ([aggregate.md §4.2](../stages/aggregate.md#42-one-claimer-a-pool-of-mergers));
and an SQS receive returns ten messages, which for a stream of small events is the wrong shape by
orders of magnitude. An SQS deployment that needs event receipt runs a Kafka topic for it, or
posts files.

## 5. Failure, traced

Every row assumes a kill at that point.

| Kill... | Records | Buffer | Pipeline | On the next assignment |
|---|---|---|---|---|
| after the sender's request, before `send` returns | not on the broker; the sender got no receipt id | - | nothing | the sender resends |
| after `send` returned, before the response | on the broker, uncommitted | - | nothing | buffered and received; the sender resends: a **duplicate** event |
| while a buffer is open | uncommitted | on this node, lost with it | nothing | buffered and received again; nothing duplicated |
| after `receive` returned, before the commit | uncommitted | lost | has the batch | buffered and received again: a **duplicate batch** |
| after the commit, before the buffer's delete | committed | lost | has it | - |
| after the dead-letter publish, before the commit | uncommitted | lost | refusing | dead-lettered again: twice on `.failed`, never in the pipeline |

## 6. Tests

Against an embedded or Testcontainers Kafka, as `TestKafkaFileGroupQueueContract` is:

- **V1'.** `accept` returns only after the broker's acknowledgement; a producer that fails makes
  `accept` throw and nothing is acknowledged; nothing is written under `<dataDir>/event`.
- **V2', ordering.** With a recording receiver and consumer: for a batch, the receive call
  precedes the commit, and the commit precedes the buffer's delete. Killing the drain after
  `receive` and before the commit, then draining again, receives the batch twice.
- **Batching.** Events of two feeds on one partition become two batches; a batch closes on age,
  count and size; the receiver sees the first event's headers and receipt id; a buffer is
  byte-for-byte the file the local appender would have written.
- **V4'.** A refusal dead-letters every record of the batch, with keys and headers, and commits
  past them; a 5xx holds the records and retries; a partition revoked during the hold drops the
  buffer and the redelivery rebuilds it.
- **Start-up.** A buffer left from a previous run is removed and its events arrive again.
- **Validation.** Shared mode without `eventStore.queue` is an error when event receipt is
  enabled; local mode with one is an error; an SQS type is an error either way.

## 7. What goes in shared mode

The `event/` directory as the queue, `event/failed`, the refusal count and the three-strikes
rule, and the `event-store-roll` schedule. All of them stay for local mode.

## 8. Decisions

| # | Decision | Alternative, and why not |
|---|---|---|
| **D1** | **Files are the local-mode queue; a topic is the shared-mode queue.** Owner, 2026-09-15 | A shared file store as the event queue - append to a group per feed on the mount. Appends from several nodes to one file need a lock, which shared storage cannot give ([file-stores.md §5.6](file-stores.md#56-intermediate-directories-create-and-cascade-delete-without-a-lock)), and a group per event is the thing event receipt exists to avoid |
| **D2** | **Kafka only.** SQS is refused for this queue | §4.7. SQS with the body in S3 is the file appender by another name, plus a round trip per event |
| **D3** | **The buffer is scratch; the ledger is the truth.** Records stay uncommitted while their batch is open, and the file that accumulates them is cleared at start-up | Accumulate in memory: bounded per feed by the byte bound, but the number of feeds is not bounded, and a file is what the receiver takes anyway. Commit on append and rely on the buffer: the local model, which is the defect |
| **D4** | **Receive on the drain thread; no hand-off.** A record commits only on the thread that polled it, and a receipt is short | A pool of receivers reporting back to the poller, as the aggregate stage does for merges, which are long. Not worth the second queue until a receipt is measured to be slow |
| **D5** | **A refused batch is dead-lettered whole, at once, to `<topic>.failed`.** No count, no quarantine directory | Three refusals then quarantine, as local mode does: the count cannot survive a node's removal, and the quarantine would be on the disk that goes with it |
| **D6** | **`PartitionLedger` is extracted from `KafkaFileGroupQueue` and shared**, not re-implemented | Copying it: two ledgers to keep in step, and one subtle case has already been found in it ([aggregate.md D15](../stages/aggregate.md#8-decisions)) |
| **D7** | **Until this is built, a shared-mode node that receives logs a boot-time warning naming the window**, not an error. Owner, 2026-09-15; built the same day as the validator warning `EVENT_RECEIPT_NODE_LOCAL_IN_SHARED_MODE` | An error would take event receipt away from shared deployments with nothing to offer instead; R9 is about a control that cannot function, and this one functions with a stated loss window. Once built, a missing `eventStore.queue` in shared mode is the error (§4.7) |
| **D8** | **`maxAge`, `maxEventCount` and `maxByteCount` are the batch bounds in both modes**; `rollFrequency` is local-only | A second set of bounds for the drain: nothing would differ between them |
| **D9** | **One drain thread per node; no thread count.** Owner, 2026-09-15 | Allowing more, as the aggregate claimer does on Kafka (D12): a feed reaches one consumer whatever the count, and a receipt is short, so nothing is gained until a node is measured to need it |
| **D10** | **Parked.** Designed to build to; not built. Owner, 2026-09-15. Tracked in [future-work.md §1a](../future-work.md) | - |

Nothing is open.

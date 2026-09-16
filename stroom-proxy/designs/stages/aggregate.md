# Aggregation

[← Design documentation](../README.md) · [Contracts](../contracts.md) · [File stores](../infrastructure/file-stores.md) · [Queues](../infrastructure/queues.md) · [Execution](../infrastructure/execution.md) · [Receipt](receive.md)

**Status: built, 2026-09-09.** This describes the aggregate stage as it is. It covers what the code
used to call the pre-aggregate and aggregate stages, which share one contract and, by
[D1](#8-decisions), became one stage. [§7](#7-what-the-implementation-before-this-design-did) says what the implementation does
today; [§8](#8-decisions) records the decisions, all taken on 2026-09-09.

---

## 1. What aggregation is for

Aggregation packs many small file groups for one feed into one archive before forwarding. Fewer
connections, far less bandwidth, and downstream an aggregate is a unit of work rather than a batch
in transit ([contracts §7](../contracts.md#7-aggregation)). Its input is the stream of single-feed
canonical groups that receipt and split-zip publish; its output is one canonical group per
aggregate, committed to the aggregate store and published to the forward queue.

It is the only stage that holds state across messages. Every other stage takes one message,
produces its output, and is done. Aggregation must hold an input until it has enough company,
or until the input is old enough to go alone, and that holding is where every difficulty in this
stage lives: something the stage has taken and not yet shipped is data the pipeline has promised
not to lose.

**The boundary of this layer.** In: the stage that consumes the aggregate input queue, the open
aggregates it holds, the merge that closes one, its configuration, and the one addition the queue
layer needs for it ([§4.6](#46-what-the-queue-layer-must-add)). Out: split-zip, which prepares
single-feed inputs and is a stateless store-to-store stage ([D11](#8-decisions)); forwarding, which
consumes what this stage publishes.

## 2. The contract

**A1. Nothing is lost between claim and publish.** Every input this stage has claimed is, at every
instant until an aggregate containing it has been committed and published, still in its store and
still claimable by the mode's own release (queues Q4). The stage acknowledges an input only after
every aggregate that took part of it is published. A node that dies holding open aggregates loses
nothing: what it held is redelivered to another node, or to itself on restart.

This is R1 applied to a stage that holds state across messages, and it is the clause the
implementation before this design does not honour in shared mode ([§7](#7-what-the-implementation-before-this-design-did)).

**A2. The stage owns no durable state.** Its state is the set of claims it holds and where each
input's items are to go, in memory, on the one thread that claims. On disk it has nothing but the
store write it is committing at that moment. There is no open-aggregate directory, no split
scratch, no start-up rebuild, no quarantine of its own. A1 is what makes this possible: because the
inputs are still where they were, remembering them is all the stage has to do, and forgetting them
costs a redelivery, not data.

**A3. An aggregate is one feed.** The aggregation key is the message's `feed` and `type`, which
every publisher of a single-feed group sets ([queues §4](../infrastructure/queues.md#4-the-interface)).
Every item in an aggregate has the key of the aggregate. An input whose message carries no key is
failed (Q6), never absorbed: it is a multi-feed group that should have gone to split-zip.

**A4. Bounds are targets** ([contracts §7](../contracts.md#7-aggregation)). An aggregate closes
when the next item would take it past `maxItemsPerAggregate` or `maxUncompressedByteSize`, or when
it has been open longer than `aggregationFrequency`. A single item larger than the byte bound forms
an aggregate of one. Sizes are the declared sizes in `proxy.entries`, advisory and never negative
(receipt P8). An **item** is one base name's entries - the data entry and its sidecars - and is
never divided. An **input** is divided freely: its items are assigned to consecutive aggregates by
range, so a large input fills the current aggregate, then the next, and so on.

**A5. Every aggregate committed is a canonical proxy zip** (receipt P4): entries renumbered from
`0000000001` in the order their items were taken, each data entry preceded by its sidecars,
`proxy.entries` describing the whole of the zip beside it, `proxy.meta` holding the headers common
to every input the aggregate took, with `Feed` and `Type` set from the key. A consumer of the
aggregate store cannot tell an aggregate of one input from a received group.

**A6. Commit, publish, delete, acknowledge - in that order, per input.** The aggregate is committed
(store C1), then published (queue Q1), then every input all of whose items are now published is
deleted from its store and its message acknowledged. An input with items still open is neither. A
redelivered message whose input is gone is acknowledged (R12). A crash between publish and
acknowledge produces a duplicate downstream, which is at-least-once doing what it says.

**A7. Nothing depends on `close()`** (R8, Q10). Stop releases nothing and flushes nothing: the
loop threads leave, and the mode releases what they held - local start-up recovery moves in-flight
messages back to pending, an SQS message's visibility lapses when its heartbeat stops, a Kafka
consumer's uncommitted records are redelivered when its partitions are reassigned. A kill and a
clean stop leave exactly the same state.

**A8. Nothing is discarded silently.** An input that cannot be read - a missing member, an
unreadable zip or entries file - is failed alone, with the reason, and goes where the queue's
give-up puts it (Q7). An aggregate whose merge fails is not written; its inputs are failed with the
reason and redelivered. A bad input never takes good ones to the dead-letter queue with it, because
it is found at claim time, before it joins anything.

## 3. The interface

The stage is a `LoopTask` the registry runs, with the same shape as every other stage loop and one
difference: it does not use `FileGroupQueueWorker`, because the worker closes an item when
`process()` returns and this stage needs to keep it.

```java
/** The aggregate stage: one claimer holds the open aggregates; a pool of workers merges. */
public final class AggregateStage {

    AggregateStage(FileGroupQueue input,
                   FileStoreRegistry stores,      // resolves and deletes inputs
                   FileStore output,             // the aggregate store
                   FileGroupQueue forward,        // the forward queue
                   AggregateBounds bounds,
                   int mergeThreads,
                   String producerId,
                   Metrics metrics);

    /** The claimer's task: acknowledge what the workers finished, claim one input and add it,
     *  hand over whatever that filled or aged. Registered as the stage-aggregate loop. */
    LoopTask claimer();

    /** A worker's task: take one closed aggregate, merge, commit, publish, delete its inputs,
     *  and report back. Registered as the stage-aggregate-merge loop with mergeThreads threads. */
    LoopTask merger();
}

public record AggregateBounds(int maxItems, long maxBytes, Duration maxAge) { }
```

Both loops are phase `AGGREGATE`; the claimer is registered first so that it stops first. The
claimer loop has `consumerThreads` threads, which is one unless the input queue is Kafka
([D12](#8-decisions)). There is no close-old-aggregates schedule: the claimer checks the age of every
aggregate it holds on every pass, and a pass happens at least once a second because `next()` waits
at most that long.

Internally, on the claimer:

```
OpenAggregate   key, openedAt, items, bytes, slices
Slice           held input, from item, to item (exclusive)
Held            the queue item, the resolved path, items remaining to place
```

and between the two loops a bounded hand-off queue of closed aggregates in one direction and of
finished ones in the other.

## 4. Design

### 4.1 An open aggregate is a set of held claims

Adding an input is bookkeeping. The thread claims a message with `next()`, resolves its location
(a lent path on a filesystem, a download from S3), reads `proxy.entries` once to learn its items
and their sizes, and walks them: each item goes into the current aggregate for the key unless it
would breach a bound, in which case the aggregate closes ([§4.3](#43-closing)) and a new one opens
for the rest. Nothing is moved and nothing is copied. What the thread keeps is the item, the
resolved path and the slice boundaries.

Nothing is handed to a worker until every slice of the input exists. A worker deletes an input
once its slices are all published, so an aggregate handed over part-way through the walk could see
the input's slice count reach zero and delete what the later slices still need; the closed
aggregates are collected and handed over together after the last slice.

The walk is also where an input is checked: the group is complete, the zip opens, the entries file
parses, and every entry the entries name is in the zip. An input that fails any of these is failed
then and there, alone (A8), as is one whose store cannot resolve it for any reason but absence: a
claim is never held to something the claimer could not read. A message with no aggregation key is
failed without resolving it (A3).

**Memory.** Per held input: the queue item, a path, and two integers per slice. The entries
themselves are not retained; sizes are consumed as the walk goes. A thousand held inputs are a few
hundred kilobytes.

### 4.2 One claimer, a pool of mergers

The aim is the best aggregates the bounds allow, not merely bounded ones, so a feed's inputs on a
node must land in one aggregate whatever the backend. One thread, the **claimer**, therefore owns
every open aggregate on the node. It claims, resolves, walks and slices; it never merges. When an
aggregate closes it goes onto a bounded hand-off queue, and a pool of **merge workers** does the
merge, commit, publish and deletes ([§4.3](#43-closing)). A worker cannot acknowledge a Kafka
record - only the thread whose consumer polled it may, and the consumer enforces it - so a finished
aggregate goes back on a second queue and the claimer acknowledges its inputs on its next pass. The
lag is at most the claimer's one-second wait, and nothing depends on it (A7).

There are no locks around the aggregates: one thread reads and writes them. The two hand-off
queues are the only shared state. A full hand-off queue blocks the claimer, which is the
backpressure: the node stops claiming when it cannot merge fast enough.

**Threads.** `mergeThreads` sizes the pool and defaults to one, which on disk behaves exactly like
merging in line; on S3, where every step of a close is a network round trip, more workers overlap
them. `consumerThreads` is the number of claimers and is one, except on Kafka, where each claimer
is a consumer with its own partitions and a feed still reaches one of them; on SQS and on disk a
second claimer would only spread a feed across two aggregates, and the validator refuses it
([D12](#8-decisions)).

**What no thread model can fix.** On SQS a feed's inputs reach whichever node receives them, so a
cluster of N aggregating nodes closes N partial aggregates per feed per window. SQS has no routing,
and its FIFO message groups are the wrong tool: while one message of a group is in flight no
consumer receives another from that group, which would stall a held aggregate. Kafka's partitioner
sends a feed to one consumer and is the backend that gives one aggregate per feed across a cluster.
That is a property of the queue, stated in the queue design, and it is why split-zip stays: the
split is what restores a feed's key, and with it affinity, after a mixed zip ([D11](#8-decisions)).

### 4.3 Closing

A close merges the aggregate's slices into a fresh write on the aggregate store and lets it go:

1. `output.newWrite()`; write `proxy.zip` by walking each slice's zip in turn, skipping the items
   before `from`, copying the raw compressed bytes of every entry of items `from` to `to` under the
   next output name, and stopping at `to`. Build `proxy.entries` from what was written. Write
   `proxy.meta` as the intersection of the inputs' `proxy.meta` maps, with `Feed` and `Type` set
   from the key.
2. `commit()`.
3. Publish to the forward queue: the location, the key, `producingStage: aggregate`, this node's
   id, and no trace id, since an aggregate has many.
4. For every held input whose last slice this was: `delete()` its location and the path the
   store lent for it - the same directory on a filesystem store, a per-resolve download on an
   object store, which is the caller's to remove (C3) - then `acknowledge()` its message. Failure to
   delete is logged and the acknowledgement still happens: the group is an orphan the store's own
   housekeeping clears (C6), not data at risk. An input failed since the aggregate closed, because
   another merge it was part of failed, is being delivered again whole, so its slice is skipped.
5. Record the aggregate's item count, byte size and age in the three histograms.

A close is triggered by the walk that breaches a bound, or by the claimer's age check at the top of
each pass. Either way the claimer hands the aggregate to the pool and carries on; steps 1 to 4
except the acknowledgements run on a worker, and the acknowledgements run on the claimer when the
worker reports the aggregate finished. Between hand-off and report the inputs are held exactly as
they were while the aggregate was open.

An acknowledgement or a failure that itself throws releases the item through `close()`, as the
queue worker does, so no claim is held by nobody while the process lives (Q4); on Kafka that release
is positional and lets go of every later record the claimer holds on the partition, which are
delivered again. That holds for every completion the claimer makes, including the R12
acknowledgement of an input that resolves to nothing and the failure of one it refuses at claim -
neither has a `Held` behind it, and each is released the same way if it throws.

If step 1 or 2 throws - or anything else does, an `Error` included, which is reported to the
claimer before it is rethrown - the write handle's `close()` discards the write, and every input of
the aggregate is failed with the reason (A8): they are redelivered, later, with the attempt counted, and
a persistent failure reaches the dead-letter mechanism. If step 3 throws the committed group is an
orphan and the inputs are failed the same way. Failing, like acknowledging, is the claimer's: the
worker reports the aggregate failed with its reason and the claimer fails each input. Step 4 cannot
throw past one input into the next: each input is handled and logged on its own.

### 4.4 Age

An aggregate's clock starts when it opens on this thread, not from the messages' `createdTime`. The
alternative looks better - it measures how old the data is - but on redelivery after a restart every
message is already older than the bound, so every feed would close on its first item and a backlog
would be shipped as singletons. Starting the clock at open means a restart costs a bounded delay
and nothing else. A node that restarts more often than `aggregationFrequency` still closes on count
and size ([D4](#8-decisions)).

### 4.5 What the two modes see

Nothing in this stage varies by mode. In local mode a held message sits in the queue's `in-flight/`
directory and its input in the local store; in shared mode the message is invisible on SQS or
uncommitted on Kafka, and the input is in the shared store. A node scaled away mid-aggregate leaves
both exactly where every other node can find them. What differs is what each backend requires of a
consumer that holds many claims for minutes, which is [§4.6](#46-what-the-queue-layer-must-add).

Affinity is as it was: with Kafka a feed's inputs reach one consumer; with SQS every node holds its
own partial aggregate for every feed it happens to receive, and the window produces as many
aggregates per feed as there are aggregating nodes.

### 4.6 What the queue layer must add

The queue contract already permits a consumer to hold many claims: nothing in Q3 to Q5 says one.
The backends were built for one at a time, and one of them must change.

**Local: nothing.** `in-flight/` holds any number of files. On restart every one goes back to
`pending/` with its attempt counted, as it does today.

**SQS: nothing in code.** `next()` already starts a heartbeat that extends the message's visibility
for as long as it is held, so a claim outlives `visibilityTimeout` without the stage doing
anything. Two things follow that the other backends do not have. SQS long-polls for its own
`waitTime` rather than the claimer's one second, so on an idle SQS input the claimer's pass - and
with it the acknowledgement of finished aggregates and the age check - happens every `waitTime`,
twenty seconds by default, rather than every second. And each held message costs one visibility
extension per interval, so the heartbeat load scales with the messages in open aggregates; at the
default timeout that is small, at a short `visibilityTimeout` with thousands held it is not. What SQS imposes is a quota: a standard queue allows 120,000 in-flight messages and a
FIFO queue 20,000 (AWS's published limits at the time of writing; the standard one can be raised on
request). A node's share is the messages in its open aggregates - at most `maxItemsPerAggregate`
per feed per thread, and in practice the number of receipts a feed sees in `aggregationFrequency`.
The validator cannot see traffic, so this is an operational bound, stated in the deployment
example and the operations guide: on SQS, `aggregationFrequency` is the knob that bounds in-flight
messages.

**Kafka: contiguous commit.** The backend keeps, per partition, a ledger of the records the
consumer has polled and not completed, and commits only up to the first record still held, where
done means acknowledged, failed or dead-lettered. Releasing without completing is positional - a
seek back to the record releases every later held record with it - and only `close()` releases;
a `fail()` whose republish fails leaves the record held for its holder to release. A partition
revoked in a rebalance drops its ledger, so the records still held on it are stale to this
consumer and delivered again to whichever consumer is assigned it. This stage releases only an
item whose acknowledgement or failure threw (§4.3); otherwise the consumer's close releases all
(A7). This is a change to `KafkaFileGroupQueue` and
an addition to the queue design ([D9](#8-decisions)); the stage does not know about it. Every call
on a Kafka item is made by the claimer, which is the thread that polled it ([§4.2](#42-one-claimer-a-pool-of-mergers)).

The claimer polls on every pass and never merges, so Kafka's `max.poll.interval.ms` is not at
risk from a long merge. The one way to breach it is a hand-off queue that stays full for longer
than the interval, which is the backpressure doing its job on a node that cannot keep up; the
consequence is a reassignment and redelivery, duplicates and not loss.

### 4.7 Configuration

```yaml
pipeline:
  stages:
    aggregate:
      enabled: true
      inputQueue: aggregateInput
      outputQueue: forwardingInput
      fileStore: aggregateStore
      maxItemsPerAggregate: 1000
      maxUncompressedByteSize: 1G
      aggregationFrequency: PT10M
      threads:
        consumerThreads: 1   # claimers; more than one only with a Kafka input queue
        mergeThreads: 1      # merge workers
```

The three bounds move from `proxyConfig.aggregator` into the stage block, because the stage is the
only reader and every other stage's settings live with the stage ([D7](#8-decisions)). Each must be
stated (R14) and positive. `splitSources` goes: it existed because splitting an input cost a second
copy of it, and splitting by range costs nothing ([D5](#8-decisions)). `mergeThreads` is new and
must be at least one; `consumerThreads` above one is refused unless the input queue is Kafka
([D12](#8-decisions)).

`preAggregateInput`, `preAggregateStore` and `stages.preAggregate` go with the stage they served,
and the default output queue of receipt and split-zip becomes `aggregateInput`. Under R14 a
configuration that still names any of them refuses to boot, which is the loud failure R14 is for;
`BREAKING_CHANGES.md` says what to change.

### 4.8 Stop

Nothing. The registry stops the claimer, then the workers; the claimer leaves `next()`, and a
worker finishes the merge it is in or is interrupted out of it. Held claims are the mode's to
release (A7), including those of an aggregate a worker had finished but the claimer had not yet
acknowledged: they are redelivered and the aggregate is forwarded twice, a duplicate. On disk that is the next start's in-flight
recovery, with the attempt counted; on SQS the visibility lapse; on Kafka the reassignment that
follows the consumer's close. The one thing a stop can leave is a write in progress on the
aggregate store, which is staging the store clears (C6).

### 4.9 What goes

`PreAggregator` (1,240 lines), `Aggregator`, `AggregateClosePublisher`,
`PreAggregateStageProcessor`, `AggregateStageProcessor`, `PreAggregateStageConfig`,
`PreAggregateStageThreadsConfig`, `AggregatorConfig`, `PipelineStageName.PRE_AGGREGATE`,
`Phase.PRE_AGGREGATE`, the `close-old-aggregates` schedule, the striped feed-key lock, the
start-up rebuild and split-output recovery, `CleanupDirQueue`'s use by aggregation, and the
directories `21_pre_aggregates`, `22_splitting`, `23_split_output`, `24_pre_aggregate_failed` and
`31_aggregates`. The nested group - a directory of file groups - no longer exists anywhere in the
pipeline; the S3 store keeps its ability to hold one because it costs nothing, but nothing writes
one.

## 5. Failure, traced

Every row assumes a kill at that point; a clean stop is the first row.

| Kill... | Message | Input | Output | On redelivery |
|---|---|---|---|---|
| while an input is held | claimed; released by the mode | in its store | none | re-aggregated; nothing duplicated |
| during the merge | as above | as above | staging, cleared by the store | as above |
| after commit, before publish | as above | as above | an orphan, cleared by the store's housekeeping; never forwarded | as above |
| after publish, before the deletes | as above | as above | forwarded | re-aggregated and forwarded again: **duplicate** |
| part-way through the deletes | claimed; released by the mode | some deleted | forwarded | deleted ones resolve to nothing and are acknowledged (R12); the rest are re-aggregated: duplicate of those |
| part-way through the acknowledgements | some acknowledged | gone | forwarded | the rest resolve to nothing and are acknowledged |
| after the acknowledgements | gone | gone | forwarded | - |

An input sliced across two aggregates, the first published and the second still open, is
redelivered whole after a kill: its first slice is forwarded twice. Same row as "after publish".

## 6. Tests

The contract tests, against the local queue and store on a temporary directory, with a driver that
runs the claimer's and a worker's `run()` by hand, in the order the test needs:

- **A1 / A6, ordering.** After an input is added it is in `in-flight/` and in its store. After a
  close it is published before it is deleted and deleted before it is acknowledged, pinned by a
  store and queue that record the order of calls. An input with items still open is neither deleted
  nor acknowledged.
- **A2.** After any sequence of adds and closes the data directory holds nothing of this stage's.
- **A3.** A message with no key is failed without being resolved.
- **A4, bounds and ranges.** An input of 5,000 items with a bound of 1,000 yields five aggregates,
  and its message is acknowledged after the fifth. An item that would breach the byte bound closes
  the current aggregate and opens the next with itself first. A single item over the byte bound
  ships alone. A bound is never breached by more than one item.
- **A4, age.** An aggregate older than `maxAge` closes on the next `run()`, including on an empty
  poll. Age is measured from open, so an input whose message is old does not close on arrival.
- **A5.** Every output is canonical: entries renumbered, `proxy.entries` naming exactly the zip's
  entries, `proxy.meta` the intersection of the inputs' headers plus the key. The existing
  `assertEntriesFileDescribesTheZip` moves here.
- **A6, redelivery.** A redelivered message whose input is gone is acknowledged, not failed.
- **A1, the hand-off race.** With a worker running concurrently, an input divided across several
  aggregates in one walk is never deleted before its last slice is placed: every item reaches the
  forward queue. A store that lends a copy, as S3 does, has that copy removed once the input is
  published.
- **A8 at claim.** An entries file naming an entry the zip does not hold is refused at claim, alone.
- **Failure with a sliced input.** A merge failure on one of an input's aggregates drops its
  remainder from the open aggregate and skips its slice in any closed aggregate merged after the
  failure is settled; one merged before then is a duplicate. The redelivery re-aggregates the
  whole input and nothing is lost.
- **A7.** Killing the driver mid-aggregate and constructing a new queue over the same directory
  redelivers every held message with the attempt counted, including the inputs of an aggregate a
  worker had finished but the claimer had not acknowledged.
- **Hand-off.** A worker's failure reaches the claimer as a failure of every member, and a
  transient store failure is recovered from once the store is back with nothing lost. That a full
  hand-off queue blocks the claimer is exercised by reading rather than by a test: the tests drive
  claimer and worker on one thread, so they run with a pool wide enough never to block.
- **A8.** A group missing its zip, and a zip that does not open, are failed alone with a reason;
  the aggregate they would have joined closes normally without them. A merge that throws fails
  every member and writes nothing to the store.
- **Kafka** ([§4.6](#46-what-the-queue-layer-must-add)): with three outstanding records,
  acknowledging the third commits nothing; acknowledging the first commits past the first only;
  failing the second then commits past the third. Closing the consumer redelivers all outstanding.

The stress suite's `TransferStageProcessor` stand-in for the two aggregating stages is replaced by
the real stage, which is the point of the suite.

## 7. What the implementation before this design did

Two stages, two stores, two queues, five working directories, about 1,900 lines.

The pre-aggregate stage resolved an input and **moved it out of the store into
`21_pre_aggregates/<feed>__<n>/` on the node's own disk**, then deleted the input's location and
acknowledged its message. An oversized input was physically split into part zips in `22_splitting`,
moved to `23_split_output`, and the parts moved into aggregates from there. When an aggregate
closed - on count, size, or a ten-second schedule finding it old - `AggregateClosePublisher` copied
the whole directory of parts into the pre-aggregate store as a nested group, published it, and
deleted the original. At start-up the stage listed `21_pre_aggregates`, rebuilt the state of every
open aggregate by reading each part's entries file, seeded its age from the oldest part's
modification time, re-absorbed anything in `23_split_output`, removed empty aggregate directories,
and quarantined to `24_pre_aggregate_failed` any aggregate it could not attribute to a feed.

The aggregate stage resolved the nested group, checked every part was whole, merged them into a
zip in `31_aggregates`, and `AggregateClosePublisher` copied that into the aggregate store,
published it and deleted the temporary. On the local path an input's bytes were therefore copied
three times between receipt and forwarding; on S3 they crossed the network five times.

**The loss window.** Between the acknowledgement in the pre-aggregate stage and the publish at
close, the only copy of an input was on one node's local disk, for up to `aggregationFrequency` -
ten minutes by default - for every feed that node was aggregating. In local mode that disk survives
a restart and the start-up rebuild recovers it. In shared mode the node may never come back
([file-stores §2](../infrastructure/file-stores.md#2-the-two-modes)), and with it goes every open
aggregate it held. The state was not claimable by any other node and not named by any message, so
no redelivery could reach it. Every other piece of the stage's complexity - the rebuild, the age
seeding, the part-id accounting, the quarantine, the unique directory names, the empty-directory
sweep, the split recovery, the cross-filesystem move retry - existed to make that node-local state
survive a restart, which is the wrong problem: the state should not have been node-local.

## 8. Decisions

Each of these overturns something built or ruled. All thirteen were put to the owner on 2026-09-09
and decided; D3 was reopened and re-decided the same day. D14 to D16 followed the review of the
built layer. The alternatives are kept so that the reasoning survives.

**D1. One stage, not two.** *Recommended: one.* With the open aggregate a set of claims rather
than a directory, the pre-aggregate store's only remaining purpose - a durable checkpoint of an
aggregate that has closed but not been merged - buys nothing the still-claimable inputs do not
already provide, and costs a full copy of every byte (two transfers on S3) plus a store, a queue and
a stage block. The alternative keeps two stages: pre-aggregate holds claims and at close copies its
slices as a nested group into the pre-aggregate store, publishes, deletes and acknowledges;
aggregate merges statelessly as today. Its one advantage is that the merge can run on a different
node from the grouping.

**D2. The open aggregate is held claims, not node-local disk.** *Recommended: claims.* This is the
A1 fix and everything else follows from it. The alternative keeps node-local open aggregates and
requires an aggregating node in shared mode to have a persistent data directory that comes back
with the same identity, documenting the loss for any node that does not. That is a deployment rule
in place of a contract, and it is the opposite of what shared mode was defined to be.

**D3. One claimer per node owns the open aggregates; a bounded pool of merge workers closes
them.** *First taken as "per thread, no shared state", reopened the same day and re-decided.* The
owner's aim is the best aggregates the bounds allow, and per-thread aggregates spread a feed across
threads on SQS and on disk. With one claimer a feed's inputs on a node land in one aggregate on
every backend; the workers do the network- and disk-heavy close in parallel; completion returns to
the claimer, which acknowledges, because a Kafka record may only be acknowledged on the thread that
polled it ([§4.2](#42-one-claimer-a-pool-of-mergers)). `mergeThreads` defaults to one. The
per-thread alternative is simpler and was declined for the fragmentation.

**D4. The age clock starts when the aggregate opens on this thread.** *Recommended.* The
alternative, the oldest message's `createdTime`, ships a redelivered backlog as singletons
([§4.4](#44-age)). Consequence to accept: a restart restarts the clock.

**D5. Splitting is by range; `splitSources` goes.** *Recommended.* A physical split cost a second
copy of the input; a range costs nothing, so the switch that turned splitting off to save the copy
has nothing to turn off. The alternative, raised 2026-09-09, splits physically: the items that do
not fit are written as a fresh group, committed and published back to the input queue before the
aggregate is published and the original acknowledged. Every claim then ends with one close and the
Kafka commit prefix advances at every close, at the cost of copying the remainder, a store to hold
it, and a queue round-trip. Both satisfy A1 to A8.

**D6. Stop releases nothing.** *Recommended.* Flushing open aggregates at stop would ship small
aggregates, delay the stop by the merge time, and make correctness look as though it depended on
`close()` running, which R8 forbids. Releasing them explicitly would be sound on disk and SQS and
impossible on Kafka from any thread but the poller's, and the mode already does it. Consequence: on
disk every held message's attempt count goes up by one on each clean restart, as it does on a
crash; `maxDeliveryAttempts` is 100 by default.

**D7. Bounds live in `stages.aggregate`; `proxyConfig.aggregator` goes.** *Recommended.* Every
other stage's settings live with the stage. `AggregatorConfig` is in `stroom-proxy-repo`, is read
by nothing outside the proxy, and its `enabled` flag was already removed.

**D8. A merge failure fails every member.** *Recommended.* The inputs are redelivered later with
the attempt counted (Q6) and re-aggregated, most likely with different company. The alternative,
releasing them without counting, hides a store that is persistently unwritable. A bad *input* never
causes a merge failure, because it is refused when claimed (A8).

**D9. The Kafka queue commits the contiguous done prefix.** *Required by D2.* Recorded here and as
a decision in the queue design: `acknowledge()` and `fail()` mark a record done and commit up to the
first offset that is not; release is by consumer close only.

**D10. An aggregate of one whole input is still merged into a fresh group.** *Recommended.* The
alternative publishes a forward message naming the input's own location and copies nothing, at the
price of a second code path and a forward stage that deletes from the receive store. One path,
one copy.

**D11. Split-zip is rebuilt with this layer.** *Taken, and confirmed after a proposal to dissolve
it.* It is not in this design's boundary, but it is the only stage besides forward still on the old
shape, it is small, it shares the canonical-zip writing this stage needs, and its output is this
stage's input. Its own design is a page. A later proposal to dissolve it into this stage - slicing
a multi-feed input by feed as well as by range, since `proxy.entries` names the feed of every item -
was declined: the split is what restores a feed's affinity after a mixed zip, and on Kafka that
affinity is what lets a feed's aggregates fill rather than fragment across consumers. The
alternative of leaving it for its own turn after forwarding was also declined.

**D12. More than one claimer is refused unless the input queue is Kafka.** *Taken.* A second
claimer on SQS or on disk spreads a feed across two aggregates on one node for no gain; on Kafka
each claimer is a consumer with its own partitions and a feed still reaches one of them. Refusal
rather than a warning, so that a configuration that fragments its aggregates does not boot.

**D14. An acknowledgement or failure that throws releases the item.** *Taken on the review of
2026-09-09.* Logging alone left a claim held by nobody for the life of the process, which
Q4 forbids; `close()` is the holder's release, as the queue worker already does. On Kafka the
release is positional and lets go of every later record the claimer holds on the partition, which
are delivered again: duplicates, not loss.

**D15. A Kafka `fail()` whose republish fails leaves the record held.** *Same review.* A seek back
on a consumer holding many records would release every later one and let the claimer re-add inputs
it still held; only `close()` releases, and a revoked partition drops its ledger so stale items
commit nothing (queues.md D7).

**D16. The sweep must not reach an open aggregate's inputs.** *Same review.* A shared filesystem
store's `orphanAge` must exceed `aggregationFrequency`, enforced by the validator beside the
retry-window bound, since an input now waits in its store, unacknowledged, for as long as its
aggregate is open.

**D13. A message never carries facts about the data it names.** *Taken, and closed permanently by
the owner.* Putting the item count and byte total on the message would let the claimer plan without
resolving, and move S3 downloads into the workers. Declined because a message names a location and
nothing more: under R12 the data it names may already have been consumed and deleted by the time the
message is read, so anything the message said about that data would describe something that no
longer exists, and an aggregate planned from it would be planned around an absence. The claimer
resolves at claim, and absence is discovered where it belongs.

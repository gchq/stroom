# Forwarding

[← Design documentation](../README.md) · [Contracts](../contracts.md) · [Queues](../infrastructure/queues.md) · [File stores](../infrastructure/file-stores.md) · [Execution](../infrastructure/execution.md) · [Aggregation](aggregate.md)

**Status: built, 2026-09-14.** This covers the forward stage and what used to be the separate retry
and failure tier ([infrastructure/forward-retry.md](../infrastructure/forward-retry.md)), which this
design folds into the stage. [§7](#7-what-the-implementation-before-this-design-did) says what the
implementation did before; [§8](#8-decisions) records the decisions, all taken on 2026-09-09.

---

## 1. What forwarding is for

Forwarding is where a file group leaves the proxy: an HTTP `POST` to a downstream Stroom or proxy,
a move into a directory tree another process collects from, or an upload to an S3 bucket. It is the
last stage, so it is the one whose "done" means the pipeline's promise is kept. Everything before
it moved data between things the proxy owns; this stage hands it to something the proxy does not,
and has to know the difference between a destination that refused, one that is briefly away, and
one that is gone for good.

**The boundary of this layer.** In: the forward stage, the destinations it delivers to and their
configuration, how a failure is classified, when a group is tried again, when the proxy gives up
and where the data goes then, and fan-out to several destinations. Out: instant forwarding, which
is receipt's ([receive.md §4.7](receive.md#47-instant-forwarding)) and uses a destination without
this stage; the directory scanner that re-ingests what an operator replays.

## 2. The contract

**F1. A group is acknowledged only when every destination has accepted it durably.** Accepted means
the downstream answered `200` with a receipt, the directory has been renamed into place, or the
object store has acknowledged the upload and any notification has been sent. Until then the group
stays in its store and its message stays claimed. A node that dies mid-forward loses nothing: the
mode releases the claim and another node, or this one on restart, forwards it again.

This is R1 and contracts §2.5 applied to the last stage, and it is what the node-local retry tier
could not honour: it acknowledged the pipeline message the moment the group landed on one node's
disk ([§7](#7-what-the-implementation-before-this-design-did)).

**F2. The stage owns no durable state.** No queue of its own, no retry file beside the group, no
scratch copies, no cleanup queue. What it knows about a destination's health it holds in memory,
and losing that costs an attempt, not data.

**F3. A failure is permanent or transient, and the destination says which.** Permanent is a
downstream that has said the group will never be accepted as it is: the feed is not set to receive,
the policy rejected it, the type is wrong, there is no feed. Everything else is transient: a
connection refused, a timeout, a `5xx`, a mount that has gone away, an S3 call that failed. A
transient failure fails the message (Q6): it goes to the tail of the queue with the attempt counted
and is tried again later, and the groups behind it are forwarded meanwhile. A permanent failure
gives up now (F5).

That a failed group goes to the tail rather than the head is what makes the retry tier
unnecessary. The tier existed because the old directory-moving forwarder could not otherwise get
past a group it could not forward: it would have blocked everything behind it for ever. Every
queue backend now puts a failed message behind the ones waiting - a fresh file at the end of
`pending/`, a republished record at the end of the topic, a message made visible again among the
others - so a group that cannot be forwarded yet costs the destination one attempt per pass over
the queue and nothing else.

**F4. Retry is bounded, and the bound is an age.** A group is tried until it has been failing for
longer than `maxRetryAge`, measured from when the message was published, then given up on.
`maxDeliveryAttempts` on the queue is the backstop beneath it. Wall-clock, as before: a proxy down
for longer than `maxRetryAge` gives up on its backlog on the first pass back, which is why the age
is sized to outages rather than to processing.

**F5. Give-up lands where a human can act, and then the group is done** (R2, R6). A group given up
on is written whole to the destination's give-up destination - a directory or an S3 bucket,
configured independently of where the destination forwards - with an `error.log` beside it saying
why, how many attempts were made and how old it was. Only then is its message acknowledged and the
input deleted. In shared mode the give-up destination must itself be shared, since the node that
gives up may never be asked again; the validator refuses a node-local one.

**F6. A destination that is down pauses its loop, not the pipeline's memory.** Back-off is a
property of the destination, not of the group: after a transient failure the destination's loop
waits `retryDelay`, growing by `retryDelayGrowthFactor` to `maxRetryDelay` on each consecutive
failure, and resets on the first success. A liveness check, where the destination has one, pauses
the loop while the destination is known to be down and resumes it when it is back. A group waits on
the queue rather than in the loop.

**F7. Several destinations are several deliveries of one group, each with its own claim.** Fan-out
is a stage step: the group is copied once per destination into that destination's store and
published on that destination's queue, and each destination's loop forwards its own copy with its
own retries and its own give-up. One destination being down never delays, duplicates or gives up on
another's delivery. With one destination there is no fan-out and no copy.

**F8. Nothing depends on `close()`** (R8, Q10). Stop does nothing; the mode releases what the loops
held, and a delivery interrupted mid-attempt is attempted again by whoever claims the message
next: a duplicate downstream at worst.

## 3. The interface

The forward stage is a worker over a processor, like split-zip: the item is acknowledged when
`process()` returns and failed when it throws, and that is exactly F1 and F3.

```java
/** One destination's loop: claim a group, deliver it, or fail it, or give up on it. */
public final class ForwardStage implements FileGroupQueueItemProcessor {
    ForwardStage(FileStoreRegistry stores, Destination destination, GiveUp giveUp,
                 ForwardBounds bounds, BooleanSupplier stopping);
    @Override public void process(FileGroupQueueItem item) throws Exception;
}

/** What a destination does with a group. Throws Refused for a permanent failure, IOException for a
 *  transient one. */
public interface Destination {
    String getName();
    String getDescription();                                // a URL, a directory, a bucket
    void deliver(Path group) throws Refused, IOException;   // returns only once accepted durably
    Optional<LivenessCheck> livenessCheck();
}

public record ForwardBounds(Duration maxRetryAge, Duration retryDelay, double retryDelayGrowthFactor,
                            Duration maxRetryDelay) { }

/** Writes error.log beside a group and delivers it to the give-up destination (§4.3). */
public final class GiveUp { GiveUp(Destination destination); }

/** Runs a destination's liveness check on a schedule; pauses its loop while the check fails (§4.2). */
public final class LivenessWatch implements Runnable { LivenessWatch(String name, LivenessCheck check, Loop loop); }
```

`Refused` carries the downstream's status and message, and is what `error.log` records. `stopping`
is the registry's shutting-down flag, so that a back-off wait ends when the process stops.
`ForwardDestinations` builds one `Wiring` per enabled destination - the destination, its give-up,
its bounds, its liveness interval and its thread count - from the forwarder configuration, and the
assembler gives each a loop. The pieces live in `stroom.proxy.app.pipeline.stage.forward`; the
destinations and their configuration in `stroom.proxy.app.handler`.

The three destinations are the existing delivery mechanisms, kept: `HttpDestination` posts
`proxy.zip` with the meta as headers and classifies the response; `FileDestination` renames the
group into a templated directory tree, copying beside the target first where a rename cannot cross
the boundary; `S3Destination` uploads the zip under a templated key and, when configured, tells the
downstream by REST. None of them queues, retries or deletes anything any more: a destination is
given a path and either accepts it or throws.

**Fan-out** (F7) is `FanOutStage`, a processor on the pipeline's `forwardingInput` that is present
only when more than one destination is enabled: for each destination it copies the group into
`forward-<destination>` store and publishes to the `forward-<destination>` queue, then deletes the
input. Each destination's `ForwardStage` then consumes its own queue. Both are named in the
pipeline block, explicit or fail (R14), so that in shared mode they are shared like every other
queue and store.

## 4. Design

### 4.1 One attempt

`process()`:

1. Resolve the group (C3). If the destination's loop is paused for liveness, `process()` is not
   running. If the destination is in back-off, wait it out here, in one-second slices that watch
   for stop; the group is claimed throughout, which is what F1 asks.
2. `destination.deliver(path)`.
3. On return: delete the input's location and the lent path. Return; the worker acknowledges. The
   destination's consecutive-failure count resets to zero. A delete that fails is logged, naming
   the group left behind, and the message is still acknowledged: the destination has the group,
   and throwing would deliver it again on every redelivery for as long as the delete kept failing.
   The group no message names is the store's sweep's to reclaim, or the start-up sweep's in local
   mode (contracts §3.2).
4. On `Refused`: give up (§4.3). Return; the worker acknowledges.
5. On any other throw: log it with the attempt number and the group's age; if the age is past
   `maxRetryAge`, give up (§4.3) and return; otherwise count a consecutive failure against the
   destination and rethrow, so the worker fails the message (Q6) and it is tried again later, by
   any node.

The attempt number is `item.getDeliveryAttempt()`; the age is now minus the message's
`createdTime`. Neither is a fact about the data (aggregation D13); both are facts about the
message, which is what a retry bound is about.

Before any of this, a group that already carries an `error.log` has a give-up in progress: it was
decided on an earlier attempt and did not complete, because the give-up delivery threw or the
process died before the input was deleted. The give-up is resumed - a line appended to
`error.log`, the delivery made again - rather than the forward destination tried again, since the
decision stands: a refusal is permanent and an age only grows. This is the one file the stage
writes beside a group (F2 forbids retry state; this is a give-up record), and only a group in a
filesystem store can carry it back, since an object store lends a fresh download.

### 4.2 Back-off and liveness

A destination keeps, in memory, its consecutive transient failures, counted per round of attempts
rather than per thread: a failure that lands while a wait set by another thread is still running
is the same round and does not count again, so five threads failing together in an outage are one
failure. The delay before the next attempt on that destination is
`retryDelay × growthFactor ^ (failures − 1)`, capped at `maxRetryDelay`; with a growth factor of
one it is flat, and the cap is not consulted. The delay is served at the start of the
next attempt on any thread of the destination's loop, so a destination that has just failed is not
hammered by every thread in turn, and a group that arrives while the destination is healthy is
not delayed at all.

Where the destination has a liveness check - an HTTP `GET` of a status URL, a read or touch of a
path - the registry runs it every `livenessCheckInterval` and pauses the destination's loop while
it fails, resuming it when it passes. Pausing is the registry's, so a paused loop holds no claims:
the groups wait on the queue, claimable by any node that is not paused.

Neither the failure count nor the pause survives a restart, and neither needs to: the queue holds
the groups and the attempt count, and the first attempt after a restart discovers the destination's
state again.

The wait holds the claim, and on Kafka a consumer that does not poll within its
`max.poll.interval.ms` (30 minutes unless the queue's consumer config says otherwise) loses its
partitions and with them the claim. The validator therefore refuses, on a Kafka forward queue, a
longest wait - `retryDelay` when the growth factor is one, `maxRetryDelay` otherwise - that is not
shorter than that interval; the delivery after the wait adds to the gap, so the operator leaves a
margin. On SQS the per-message heartbeat runs throughout the wait, so the claim is safe there.

Pausing takes effect between tasks: a thread already waiting out a back-off when its loop is
paused keeps its claim until the wait ends, then makes its attempt.

### 4.3 Giving up

Give-up writes the group to the give-up destination as a `FileDestination` or `S3Destination` in
its own right, configured under the forwarder's `failureDestination` and defaulting to
`<data>/50_forwarding/<destination>/03_failure` in local mode. `error.log` is written beside the
group first, holding the final failure - its class, status and message - the attempt number and the
age. The give-up delivery must itself succeed before the message is acknowledged; if it throws, the
message is failed and the give-up is attempted again on redelivery, which is the same rule as any
other delivery.

In shared mode the give-up destination must be shared: an S3 bucket, or a directory at least two
levels below a `SHARED_FILESYSTEM` store's path, such as `<store path>/give-up/<name>`. A local
`03_failure` on a node that may never be asked again is refused by the validator. So is the store's
path itself or a direct child of it: the store's age sweep treats each direct child as a writer
root and looks inside it for numbered groups older than `orphanAge`, and a give-up directory laid
out without a date sub-path is numbered exactly like one. Two levels down, the sweep's walk never
reaches it, and given-up data is what it must be: never swept.

A shared give-up directory has several writers, and `FileDestination` numbers its targets from an
in-memory counter seeded once from the directory - safe only for one writer. So in shared mode a
file destination, give-up or forward, delivers under a **writer root** of its own, `<path>/<uuid>/`,
fresh per process start, exactly as the file stores do ([file-stores.md §5.1](../infrastructure/file-stores.md#51-file-group-ids-and-the-writer-root)):
a given-up group lands at `<path>/<uuid>/<date>/<feed>/0/001`, two nodes never count in one
directory, and a restarted node never continues a tree it left. The validator still sees the
configured path. A collision would never overwrite - a rename onto a non-empty directory fails - but
each one would cost a delivery attempt, which is why the roots are kept apart.

The queue's own give-up (Q7) stays as the backstop: a group that exhausts `maxDeliveryAttempts`
before its age is up reaches `failed/`, the SQS dead-letter queue or the `.failed` topic with its
data still in the store. The operator's replay for that case is the one queues.md describes; the
validator warns when `maxDeliveryAttempts × retryDelay` is shorter than `maxRetryAge` on a local or
Kafka queue, since the backstop would then fire first. On SQS the same bound is the redrive
policy's `maxReceiveCount`, which the proxy cannot read: size it to at least
`maxRetryAge ÷ retryDelay`, or the dead-letter queue fires before the destination gives up.

### 4.4 Fan-out

With one enabled destination, `forwardingInput` is consumed by that destination's `ForwardStage`
directly. With several, `FanOutStage` consumes `forwardingInput` and, per destination: opens a
write on `forward-<destination>` store, copies the group in, commits, publishes to
`forward-<destination>` queue with the message's key and trace id and a `forwardDestination`
attribute; then deletes the input. A crash part-way re-copies to every destination on redelivery,
which duplicates to the ones already done; that is the accepted direction and the same as
receipt's. A destination that is down affects only its own queue, which fills until it is back or
its groups age out.

The per-destination queues and stores are ordinary pipeline queues and stores: local in local
mode, SQS or Kafka and S3 or a shared mount in shared mode, and stated in the pipeline block.

### 4.5 Delivery mechanisms

Kept as they are, with the retry and cleanup responsibilities removed:

- **HTTP.** `POST` `proxy.zip` with `Compression: ZIP` and the allowed meta as headers, the API key
  or service token as before. `200` is acceptance; the four downstream refusals are `Refused`;
  every other response and every exception is transient. The liveness check `GET`s the status URL.
- **File.** Rename the group into `<path>/<subPathTemplate>/<n>` - in shared mode
  `<path>/<uuid>/<subPathTemplate>/<n>`, under a writer root per process start (§4.3) - or copy
  beside the target and rename when the rename cannot cross a filesystem boundary. The source is deleted only once the
  rename has succeeded, so an interruption duplicates rather than loses. A retry to a file
  destination delivers the group as it is in the store: there is no `retry.state` to travel with it
  any more, only `error.log` on give-up. The liveness check reads or touches a path.
- **S3.** Upload the zip under the templated key with the feed and type tags and the allowed meta,
  then the REST notification when configured. Acceptance is the upload and the notification both
  returning. An `error.log` beside the group, which only a give-up writes, is uploaded beside the
  zip as `<key>.error.log`, so an S3 give-up destination holds the reason as a file one does.

A destination is given the path the store lent. On a filesystem store in local mode that is the
committed group itself, which the file destination moves out and the others read in place; on an
object store it is the per-resolve download. After acceptance the stage deletes the location and
whatever the lent path still is (C3), as aggregation does.

### 4.6 Configuration

```yaml
forwardHttpDestinations:
  - name: downstream-stroom
    enabled: true
    forwardUrl: https://stroom.example.com/stroom/datafeed
    livenessCheckEnabled: true
    retry:
      maxRetryAge: P7D
      retryDelay: PT1M
      retryDelayGrowthFactor: 2
      maxRetryDelay: PT30M
      livenessCheckInterval: PT1M
    failureDestination:                   # where give-up data goes; shared in shared mode
      type: FILE
      path: /mnt/proxy-shared/give-up/downstream-stroom
    threads:
      consumerThreads: 5

pipeline:
  queues:
    forwardingInput: { ... }
    forward-downstream-stroom: { ... }    # only when more than one destination is enabled
    forward-archive: { ... }
  fileStores:
    forward-downstream-stroom: { ... }
    forward-archive: { ... }
  stages:
    forward:
      enabled: true
      inputQueue: forwardingInput
```

What goes from the forwarder's `queue` block: `queueAndRetryEnabled` (there is no node-local queue
to enable; a destination that should fail the sender rather than retry is an instant forwarder,
which is receipt's), `forwardDelay` (a test knob that shipped), `forwardThreadCount` and
`forwardRetryThreadCount` (one loop per destination, `consumerThreads`), `errorSubPathTemplate`
(the give-up destination has its own `subPathTemplate`). The block is renamed `retry`, since that
is what is left in it. `maxRetryDelay` no longer defaults equal to `retryDelay`, so a growth factor
above one does something on its own. `failureDestination` and `threads` sit beside `retry` on the
destination; `threads.consumerThreads` defaults to five, as `forwardThreadCount` did.

`stages.forward.threads.consumerThreads` is the fan-out loop's thread count and is used only when
more than one destination is enabled; with one destination the stage's loop is that destination's
loop and runs with the destination's `threads`.

The validator's rules for this stage, all applied when the forward stage is enabled in the process:
`FORWARD_NO_DESTINATION` (no enabled destination); `FORWARD_DESTINATION_QUEUE_NOT_STATED` and
`FORWARD_DESTINATION_STORE_NOT_STATED` (more than one destination and `forward-<name>` missing);
`FORWARD_GIVE_UP_NOT_SHARED` (shared mode and a give-up directory not under a `SHARED_FILESYSTEM`
store's path); `FORWARD_RETRY_DELAY_EXCEEDS_KAFKA_POLL_INTERVAL` (§4.2); and the warning
`FORWARD_ATTEMPTS_END_BEFORE_AGE` (§4.3). `ORPHAN_AGE_WITHIN_RETRY_WINDOW` (`orphanAge` at least twice the window) is computed from the
longest `maxRetryAge` among the destinations.

### 4.7 Stop

Nothing. The registry stops the loops; a delivery in progress finishes or is interrupted, and the
mode releases the claim either way. On disk that is the next start's in-flight recovery with the
attempt counted; on SQS the visibility lapse; on Kafka the reassignment.

### 4.8 What goes

`RetryingForwardDestination`, `DirQueue`, `DirQueueTransfer`, `DirQueueFactory`, `Dir`,
`CleanupDirQueue`, `RetryState`, `MultiForwardDestination`, `Forwarder`, the `ForwardDestination`
interface and its file and S3 sub-interfaces, the four `ForwardQueueConfig` classes, the
`01_forward`, `02_retry` and `temp_forward_copies` directories and `99_deleting`, `QueueMonitors`
and the queue-monitoring servlet's table of directory queues, `ForwardStageProcessor` and its
`FileGroupForwarder` seam, the assembler's refusal of a node-local retry queue on a shared forward
queue (there is no such queue), and `DirUtil`'s staging-residue sweep. `FileStores` keeps the
give-up directories for the monitoring endpoint; `DirNames.FORWARDING` names where the default
give-up directory lives.

## 5. Failure, traced

Every row assumes a kill at that point; a clean stop is the first row.

| Kill... | Message | Input | Downstream | On redelivery |
|---|---|---|---|---|
| while a group is claimed, before or during delivery | claimed; released by the mode | in its store | nothing, or a partial POST the downstream discards | delivered again; nothing duplicated |
| after the downstream accepted, before the delete | as above | as above | has it | delivered again: **duplicate** |
| after the delete, before the acknowledgement | as above | gone | has it | resolves to nothing, acknowledged (R12) |
| after `error.log` is written, before the give-up delivery | as above | as above, with `error.log` | refusing, or away | the give-up is resumed, not the forward attempt |
| after the give-up delivery, before the delete | as above | as above, with `error.log` | has nothing | the give-up is resumed: possibly twice at the give-up destination, never delivered downstream as well |
| during fan-out, after some destinations' copies are published | as above | as above | those destinations will deliver | re-copied to every destination: duplicates to the ones done |

## 6. Tests

- **F1.** After a transient failure the message is back on the queue with the attempt counted and
  the input is in its store; after acceptance the input is gone and the message acknowledged; a
  recording destination and store see deliver, delete, acknowledge in that order.
- **F3.** Each of the four refusal codes gives up at once; a `5xx`, an exception and a timeout fail
  the message; a file destination whose target has gone fails the message; an S3 upload that throws
  fails the message.
- **F4.** A message older than `maxRetryAge` is given up on at its next attempt, whatever the
  count; one younger is failed.
- **F5.** Give-up writes the group and `error.log` to the give-up destination before
  acknowledging; a give-up write that throws fails the message and nothing is acknowledged; the
  validator refuses a local give-up directory in shared mode.
- **F6.** Consecutive failures grow the delay to the cap and a success resets it; a failing
  liveness check pauses the loop and a passing one resumes it; a paused loop holds no claim.
- **F7.** With two destinations, one failing transiently on everything, the other delivers every
  group exactly once and the failing one's queue fills; the fan-out copies and publishes before
  deleting.
- **F8.** Killing the driver mid-delivery and reopening the queue redelivers the group with the
  attempt counted.
- The existing destination tests - templated paths, cross-filesystem move, liveness read and
  write, HTTP status classification, S3 key templating and notification - are ported to the new
  `Destination` interface.

## 7. What the implementation before this design did

The forward stage resolved the group and handed it to a `Forwarder`, which handed it to a
`RetryingForwardDestination` per destination. That **moved the group into a node-local directory
queue** under `50_forwarding/<destination>/01_forward` and returned, and the stage then deleted
the input and the worker acknowledged the message. Five forward threads drained the queue; a
failure appended to the group's `error.log`, wrote or updated a binary `retry.state` beside it, and
moved it to `02_retry`, where one retry thread slept at the head of the queue until the group's
delay had elapsed and tried again. A permanent refusal, or an age past `maxRetryAge`, moved the
group to `03_failure` on the same disk. Several destinations meant a copy of the group per
destination in `temp_forward_copies`, each handed to its own retrying destination. A successful
delivery moved the group into `99_deleting` before deleting it. About 7,600 lines, of which the
two directory queues and their utilities were 1,500.

**The loss window.** From the acknowledgement of the pipeline message to the delivery, the only
copy of a group was on one node's disk, for up to `maxRetryAge`, seven days by default, and its
give-up quarantine was on the same disk. In local mode the disk survives a restart and the queues
recover. In shared mode the node may never come back, and the assembler knew it: it refused
`queueAndRetryEnabled` on a shared forward queue, so a distributed deployment forwarded
synchronously with no retry, no back-off and no liveness pause at all. The retry tier
re-implemented, on node-local disk, what the pipeline queue already provides on every backend: a
durable claim, a counted attempt, redelivery, and a bounded give-up.

## 8. Decisions

All ten were put to the owner on 2026-09-09 and decided as recommended; the alternatives are kept
so that the reasoning survives. The owner's condition on D1 is recorded under F3: the retry tier
goes only because a failed group goes to the tail of the queue and never blocks the groups behind
it, which every backend now guarantees.

**D1. The retry tier goes; a group stays claimed until delivered or given up.** *Recommended.*
This is the F1 fix and the direction already taken for aggregation: no node-local state in shared
mode. The alternative keeps the directory queues and requires a persistent, same-identity data
directory for forwarding nodes in shared mode.

**D2. Back-off is per destination, in memory.** *Recommended.* A failed group goes to the tail of
the queue and the destination's loop waits before its next attempt on any group. The alternative,
a per-group not-before time, has nowhere durable to live without a retry file, and a per-group
delay at the head of a FIFO is what stalled the old retry queue.

**D3. The retry bound is an age from the message's `createdTime`, with the queue's attempt bound
beneath it.** *Recommended.* `createdTime` is when the aggregate stage published, which for a
forward queue is within seconds of the first attempt. The alternative is attempts only, which
gives up on a short outage with a fast redelivery and never on a slow one.

**D4. Give-up writes the group to the R6 destination and then acknowledges.** *Recommended.* The
data leaves the store for somewhere nothing sweeps, with `error.log` beside it, and the message is
done. The alternative leaves give-up to the queue's dead-letter mechanism with the data in the
store, which in shared mode is deleted by the store's age sweep unless replayed within `orphanAge`.

**D5. In shared mode the give-up destination must be shared.** *Recommended.* S3, or a directory
on a mount every node sees; a node-local `03_failure` is refused at boot. The alternative accepts
it and documents that give-up data is on whichever node gave up.

**D6. `error.log` records the final failure, the attempt count and the age; earlier failures are
in the send log.** *Recommended.* Without a file travelling with the group there is no per-attempt
append. The alternative writes an attempt record into the store beside the group on every failure,
which is a write per attempt and the retry file by another name.

**D7. Fan-out is a stage step with a copy per destination and a queue per destination.**
*Recommended.* A destination that is down affects only its own queue, and a healthy one is never
sent a group twice because another failed. The alternatives: deliver to every destination from one
claim, which re-sends to the healthy destinations on every retry of the failing one for up to
`maxRetryAge`; or pause every destination when any is down, which makes one outage everyone's.
The cost is that a multi-destination deployment states a queue and a store per destination in the
pipeline block.

**D8. The forwarder's `queue` block becomes `retry`, and `queueAndRetryEnabled`, `forwardDelay`,
`forwardThreadCount`, `forwardRetryThreadCount` and `errorSubPathTemplate` go.** *Recommended.*
Each names something that no longer exists. `maxRetryDelay` defaults to an hour rather than to
`retryDelay`.

**D9. The delivery mechanisms are kept.** *Recommended.* HTTP, templated file tree with the
cross-filesystem fallback, and templated S3 upload with the REST notification are the parts that
talk to the outside and none of them is the retry problem. They lose their queueing and cleanup
responsibilities and nothing else.

**D10. The forward stage keeps the queue worker.** *Recommended.* One claim per thread, acknowledged
when delivery returns and failed when it throws, is exactly what the worker does; the aggregate
stage needed its own loops only because it holds claims across items.

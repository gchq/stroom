# Event receipt

[← Design documentation](../README.md) · [Contracts](../contracts.md) · [Receipt](../stages/receive.md) · [Execution](execution.md) · [Entry points](entry-points.md)

**Status: built, 2026-09-08.** This describes the event path as it is, on top of the receipt
layer. [§6](#6-what-the-implementation-before-this-design-did) lists what
the implementation before it did differently; [§7](#7-decisions) records the decisions.

> **Local mode only, as of 2026-09-15.** V1 and V2 below rest on this node's disk surviving to
> the next start, which shared mode does not promise: a node removed with open or unreceived
> event files takes acknowledged events with it. The
> shared-mode path - a topic as the queue, a drain that batches it - is designed in
> [events-shared.md](events-shared.md) and not yet built. Until it is, event receipt on a
> shared-mode node has that loss window, and the validator says so at boot
> (`EVENT_RECEIPT_NODE_LOCAL_IN_SHARED_MODE`).

---

## 1. What event receipt is for

Some senders post one small event at a time, over REST or through an SQS queue, at rates where a
file group per event would be absurd. Event receipt batches them: each event is appended to an
open file for its feed, and when the file is old or large enough it is rolled and received -
handed to the receiver exactly as a posted body would be, becoming one group holding one data
entry of newline-delimited JSON events.

That is the whole of it: an endpoint, an appender that keeps concurrent writers from corrupting
a file, and a roll that turns the file into a receipt. Everything after that is the pipeline's.

**The boundary of this layer.** In: `EventStore`, `EventAppender`, the event file layout, the
event resource and the request helper it uses, the SQS connector, and their configuration. Out:
the receiver, which it calls; authentication and the policy chain, which are shared; the S3 event
consumer, which is an unimplemented stub for a different feature and is untouched.

## 2. The contract

**V1. An acknowledged event is durable.** The resource returns a receipt id only after the
event's bytes are on stable storage, by the configured `durability`: under `FULL` and
`QUEUE_ONLY` the append is synchronous to disk, because an acknowledgement is a promise the size
of a queue message; only `FILESYSTEM` rests on the mount's ordering. An SQS message is deleted
from its queue only after the same point. An event larger than `receive.maxRequestSize` is
refused before it is read in full, since a rolled file is one receipt and the receiver bounds a
receipt by that.

**V2. An acknowledged event reaches the pipeline at least once.** Every event file on disk is
either open, closed and waiting to be received, or quarantined; nothing else happens to one. A
closed file is deleted only after the receiver has returned, which means committed and published.
A crash at any point leaves the file where the next start finds and receives it.

**V3. The receipt policy decides at receipt.** For the resource, after the sender is
authenticated; for SQS, on the message's attributes, since a message carries no credentials and
the trust boundary is write access to the queue. A `REJECT` is the sender's answer, or is left on
the SQS queue for its redrive policy. A `DROP` is consumed and logged. Only accepted events are
appended.

**V4. A file that cannot be received is never deleted.** One the receiver *refuses* - a status
the sender would have been told, which will not change with time - is quarantined after three
refusals, moved to a failed directory nothing replays, with an ERROR naming the file and the
reason ([contracts §5](../contracts.md#5-give-up-and-replay)). One that fails because the store
or the queue is unavailable waits where it is and is tried again every tick: the data is safe on
disk and nobody else will retry it.

**V5. The event path owns no threads.** Rolling and receiving are one registry schedule in the
`INGRESS` phase, since it feeds the pipeline. Stop stops it, then closes the appenders; what is on
disk is picked up at the next start.

**V6. Nothing in memory is the only record of anything.** The files are the queue.

## 3. The interface

```java
@Singleton
public class EventStore {
    /**
     * Apply the receipt policy and, if it accepts, append the event to its feed's open file.
     * Returns when V1 holds; false if the policy dropped it; throws if it rejected it.
     */
    boolean accept(AttributeMap attributeMap, UniqueId receiptId, String event);
    /** Roll every open file that is due, then receive every closed file. A registry schedule. */
    void tryRoll();
    /** Close the open files. Called once every writer and the schedule have stopped. */
    void close();
}
```

The resource authenticates, builds the attribute map, elevates, calls `accept`, and returns the
receipt id or maps the exception to a status. The SQS connector builds the attribute map from the
message, elevates, calls the same, and deletes the message. One place decides.

## 4. Design

### 4.1 A roll is a receipt

```
<dataDir>/event/
    <feed>=<time>.log.open     an event file an appender is writing
    <feed>=<time>.log          a closed file, waiting to be received
    failed/<feed>=<time>.log   refused; needs a decision
```

Whether a file is open is a property of the file, not of memory: an appender writes to the
`.open` name and `close()` renames it, atomically, to its final name. `tryRoll`, every
`rollFrequency`, first closes every appender that is due, then lists the directory and receives
every `.log` in name order, which is oldest first within a feed: each is handed to the receiver
as a plain body with the attribute map rebuilt from its first event, and deleted when the
receiver returns. Nothing ending in `.log` is ever being written, so the roll needs no snapshot
of what is open and cannot receive a file mid-write. Nothing is queued in memory and nothing is
special at start-up beyond renaming: a `.open` file a previous run left behind is renamed closed
at construction, since nothing holds it, and received on the first tick after a trailing partial
line is trimmed, which is the only thing a power cut can leave in a file written one line per
synchronous write.

Refusals are counted per file in memory. After three the file moves to `failed/`. The count does
not survive a restart and need not: the file does, so a permanently refused file gets one bounded
run per start rather than an endless one. A refusal is a `StroomStreamException` with a status
below 500; anything else is retried on every tick, and logged each time. A file received but not
deleted is logged with its receipt id and received again as a duplicate.

### 4.2 Open files

One `EventAppender` per feed key, in a map. An appender opens its file on first write, with
`DSYNC` when the durability requires it, writes each event as one line in one write, and closes
when it is due: older than `maxAge`, or at `maxEventCount` events, or at the lesser of
`maxByteCount` and `receive.maxRequestSize` bytes, so a closed file is never more than the
receiver will take. Age alone bounds an appender's life whether or not its feed is still sending,
so the number of open files is the number of feeds that have sent in the last `maxAge`. The cache
that evicted and lazily reopened appenders to enforce a separate open-file limit goes with it.

A write happens inside the map's `compute` for its feed key, which is what keeps two writers of
one feed off the same file; a roll closes inside the same `compute`. The cost is that a feed
sharing a map bin with another waits for that feed's write, which is a synchronous disk write
under `FULL` durability. A close that fails is logged and the appender let go regardless: every
write was forced already, so the file is complete, and a closed appender kept in the map would
refuse its feed for ever.

The attribute map rebuilt for the receiver drops any `Compression` the sender put on its event
request: the file is plain text whatever the event was.

### 4.3 What the receiver sees

A rolled file is one plain body. Its attribute map is rebuilt from the first event's stored
headers, feed, type and receipt id, so the receiver's policy check sees what the sender sent and
the receipt id chain continues through `proxy.meta`. The receiver runs the policy again at that
point, as it does for every body; a feed whose status changed between append and roll is refused
there, and the file is quarantined under V4 rather than stored under a feed the downstream will not
take.

### 4.4 SQS

The connector polls with the configured long-poll wait, bounded batches per poll, and for each
message builds an attribute map from its attributes, refuses one with no `Feed`, and calls
`accept` as the processing user. Accepted or dropped, the message is then deleted. Refused, or
failed for any other reason, it is left on the queue, logged, and redelivered by SQS; the bound on
that is the queue's redrive policy, which is the queue's own infrastructure, as
[queues.md Q7](queues.md#3-the-contract) rules for the pipeline's SQS queues.

### 4.5 Stop

The registry stops the SQS pollers and then `event-store-roll`, all in `INGRESS`, before any
stage loop, then `ProxyLifecycle` closes the SQS clients and the store, which closes every open
appender under the map's lock, so a write racing the close either completes before it or is
refused. Every file left in the directory is the next start's. On a node whose receive stage is
disabled `accept` refuses every event with the same status the receiver would give, since a file
rolled there could only ever be refused later, with nobody left to tell.

### 4.6 What goes

The `event-store-forward` loop and `forwardNext`; `forwardQueue` and
`eventStore.forwardQueueSize`; `openAppendersCache` and `eventStore.openFilesCache`;
`forwardOldFiles`; `enqueueForForwarding`; the `event_failed` directory beside `event/`, which
becomes `event/failed`; the policy code the SQS connector and the request helper each carried.

## 5. Tests

- An accepted event is on disk in the serialised form; a file a previous run left behind is
  received on the first tick; a trailing partial line is trimmed first.
- Roll by age, count and size; a due file is received and deleted; the receiver sees the first
  event's headers and receipt id, as the processing user.
- A receiver failure leaves the file to the next tick until the third failure moves it to
  `failed/`.
- `accept`: reject throws, drop appends nothing, accept appends.
- Stop closes the appenders and refuses a late event.

## 6. What the implementation before this design did

- Rolled files went into a bounded in-memory `LinkedBlockingQueue`; a writer could block on a
  full queue, and had to be kept out of the map's `compute` to do so safely; a crash emptied the
  queue, so a separate start-up replay walked the directory; a forwarding failure re-offered the
  file to the queue and dropped it on the floor if the queue was full.
- Appenders were tracked twice, in the map and in a cache that evicted them to bound open files,
  with an appender re-registering itself when it found it had been evicted.
- The SQS connector applied the policy and minted receipt ids itself, in a second copy of what
  the request helper does.
- Quarantine lived beside the event directory rather than in it.

## 7. Decisions

| # | Decision | What it overturned |
|---|---|---|
| **D1** | A roll is a receipt: the schedule closes due files and hands every closed file to the receiver, oldest first. Owner's framing, 2026-09-08: a rolled batch is a received file, nothing more | The in-memory bounded queue, its size setting, the forward loop and the start-up replay |
| **D2** | Open files are bounded by rolling on age, count and size; no eviction cache | `openFilesCache` and the appender cache |
| **D3** | A rejected or failed SQS message is left on its queue for the redrive policy. Owner's decision | Unchanged in effect; stated |
| **D4** | A closed file goes through the receiver unchanged, so the policy runs again at roll on the first event's headers. Owner's decision | Unchanged in effect; the alternative was a receiver entry that skips the policy |
| **D5** | `EventStore.accept` applies the policy and appends; the resource and the SQS connector both call it | Two copies of the policy step |
| **D6** | Durability of an acknowledged event follows `durability`, with `FULL` meaning a synchronous write per event | Unchanged; stated as V1 |
| **D7** | Start-up trims a trailing partial line before rolling a file a crash left open | Nothing trimmed it |
| **D8** | Open and closed are told apart on disk, by the `.open` suffix an appender writes under and `close()` renames away | A memory snapshot of open appenders, which could not be ordered against a first write |
| **D9** | A refusal (status below 500) is quarantined after three; anything else waits and is retried every tick | Three attempts of anything, which quarantined every file rolled during a minute's outage |
| **D10** | An event is bounded by `receive.maxRequestSize` at the resource, and a closed file by the lesser of that and `maxByteCount` | A rolled file could exceed what the receiver takes, and a single event was unbounded |
| **D11** | A node whose receive stage is disabled refuses events at `accept`, and closes its SQS clients at stop | Events acknowledged and quarantined later; clients left to process exit |

# Receipt

[← Design documentation](../README.md) · [Contracts](../contracts.md) · [File stores](../infrastructure/file-stores.md) · [Queues](../infrastructure/queues.md) · [Execution](../infrastructure/execution.md) · [Entry points](../infrastructure/entry-points.md)

**Status: built, 2026-09-08.** This describes the receipt layer as it is, on top of the store,
queue, configuration and execution layers. [§7](#7-what-the-implementation-before-this-design-did)
lists what the implementation before it did differently; [§8](#8-decisions) records the decisions
and what each overturned.

---

## 1. What receipt is for

Receipt turns bytes arriving from outside into file groups the pipeline owns. Three entry points
bring bytes in - an HTTP `POST /datafeed`, a zip found by the directory scanner, and a rolled
event-store file - and all three end at one place: a **receiver** that writes what arrived into
the receive store, publishes a message naming it, and only then tells the caller it has happened.

Receipt is the pipeline's only synchronous edge. Everything after it is a loop draining a queue;
receipt runs on the caller's thread and answers the caller. That is what makes the sender's retry
the recovery mechanism for anything that goes wrong before the store has the data: the caller is
told no, keeps its copy, and sends again.

**The boundary of this layer.** In: the receiver, the HTTP handler that calls it
(`ProxyRequestHandler`), the receive stage's configuration, and the feed-status refresh
([D8 of execution](../infrastructure/execution.md#8-decisions)). Out: authentication and the
receipt-policy filter chain, which are `stroom-receive-common` shared with Stroom itself; the
directory scanner and event store, which are sources and only call the receiver; everything after
the message is published.

## 2. The contract

**P1. The caller is told success only when the group is durable in the store and its message is
published.** Store C1 and queue Q1 make each step durable; receipt does them in that order and
returns after the second. A sender that gets `200` holds a receipt the pipeline will honour.

**P2. A failed receipt leaves nothing that is not live work.** No committed group without a
message, and no scratch a start-up clear will not remove. The caller keeps its source - the HTTP
client its request, the scanner its file, the event store its rolled file - because every entry
point deletes or acknowledges only after `receive()` returns
([contracts §3](../contracts.md#3-hand-off-between-components)). The one exception is a publish
that fails after the commit: the group is then an orphan the store's own housekeeping clears, and
the sender's retry produces a second copy, which is at-least-once doing what it says (R1).

**P3. The receipt policy runs once per distinct feed, before any byte goes to the store.**
`REJECT` fails the whole request with the policy's status code. `DROP` discards that feed's
entries and the receipt proceeds with the rest; a request every feed of which is dropped is
consumed, logged as dropped, and answered `200` with its receipt id, storing nothing
([contracts §8](../contracts.md#8-data-that-is-dropped)). `RECEIVE` proceeds, under the feed the
policy accepted: a policy that names the feed itself, for data that arrived without one, names
what is stored and published.

**P4. Every group receipt commits is a canonical proxy zip.** Entries are renumbered
`0000000001.meta`, `0000000001.dat`, … in the order their base names first appear, each data entry
preceded by its meta, with `proxy.entries` describing the whole of the zip beside it and
`proxy.meta` beside that. A zip holding one feed has that feed in `proxy.meta` and on its message,
and goes to the output queue. A zip holding several goes to the split-zip queue as one group with
no feed on its message and none in `proxy.meta`, since the headers' feed describes none of its
entries, for the split-zip stage to separate; a deployment with no split-zip queue refuses such a
zip rather than strand it.

**P5. Nothing is discarded silently.** An entry that cannot be read (encrypted, or an unsupported
compression method), two entries with one name, a manifest anywhere but first, a zip with no data
entries, a body larger than `maxRequestSize`, a body declared gzip that is not one: each is a
rejection with a status code the sender can act on and a line in the receive log. Dropping is the policy's decision (P3), logged as such.
A sidecar with no data entry beside it describes nothing and is omitted with a warning naming it.

**P6. Receipt is elevated by its caller.** Every entry point wraps the call in
`asProcessingUser`, because the policy consults feed status and no caller's own identity may.
The receiver never elevates ([entry-points §2](../infrastructure/entry-points.md#2-the-processing-user-contract)).

**P7. Receipt owns no threads.** It runs on the HTTP thread, the scanner's schedule or the event
store's loop. Its one piece of background work, keeping cached feed statuses fresh, is a
registry schedule and not a thread pool of its own.

**P8. Bounds are on what is read, not on what is declared.** `maxRequestSize` bounds the bytes
read from the caller: the zip as uploaded, or the decompressed stream for gzip and plain bodies.
Each `.meta` entry read from a zip is bounded separately, because DEFLATE can expand a small
upload a thousandfold into the heap. Data entries are copied raw, without inflating, so the
sizes recorded in `proxy.entries` are the sizes the zip declares; they are advisory to
aggregation, never trusted for correctness, and never negative.

## 3. The interface

One interface, two ways in, three implementations.

```java
public interface Receiver {

    /** A request body, as the HTTP handler and the event store have it. */
    void receive(Instant startTime, AttributeMap attributeMap, String source,
                 InputStreamSupplier body);

    /** A zip already on local disk, as the scanner has it. The caller keeps the file. */
    void receiveZip(Instant startTime, AttributeMap attributeMap, String source, Path zipFile);
}
```

`attributeMap` is the caller's headers, with `Feed`, `Type` and `Compression` normalised and
the receipt id already minted. `source` is what the receive log records as the URL - the
request URI, `file://host/path`, or `event-store`. Both methods throw `StroomStreamException`
for anything the caller should report to its sender, and return only when P1 holds.

A node whose receive stage is disabled must also have the directory scanner off and no SQS
connectors: both hand what they find to the receiver, so on such a node they would only fill the
failure directory. Configuration validation refuses the combination (instant forwarding, which
receives without the stage, is exempt).

`Receiver` is bound in Guice by `ProxyCoreModule` to one of:

- **`StoringReceiver`** - the pipeline's. Built from the assembler's `ReceiveWiring`: the receive
  store, the output queue, and the split-zip queue or null.
- **`InstantForwardReceiver`** - when a forwarder is configured `instant`. Relays to that one
  destination during the receipt; the pipeline is never assembled.
- **`RefusingReceiver`** - on a node whose receive stage is disabled. Refuses every call with a
  status code, so `/datafeed` on a worker node answers honestly instead of writing to a store
  nothing drains.

There is no factory. The receiver decides from `Compression` whether the body is a zip, and
nothing else varies per request.

## 4. Design

### 4.1 Where the bytes go

`StoringReceiver` writes **directly into a store write handle**. `FileStore.newWrite()` gives it
a private directory; it fills that directory with `proxy.zip`, `proxy.entries` and `proxy.meta`;
`commit()` makes the group durable and returns the location to publish. If anything fails before
commit, `close()` discards the write. There is no receiving directory of the receiver's own, no
copy from it into the store, and no hand-off of a directory between components: the write handle
is the only place the group ever is, and the store's own staging rules ([file-stores §5.5](../infrastructure/file-stores.md#55-clearing-what-is-left-behind))
are the only cleanup.

The one piece of scratch is the **upload spool** for a zip body. A zip must be read through its
central directory, which is at the end, so the body is written to disk before it is read; that
file is not part of any group. It lives under `<dataDir>/01_receiving`, is deleted in a
`finally`, and the directory is cleared at construction (R11). The scanner's path has no spool:
its zip is already a file, and is read in place.

### 4.2 A plain or gzip body

1. Feed key from the headers. Policy for that one key (P3): reject throws; drop drains the body,
   logs `DROP`, returns.
2. Peek one byte: a body that is empty is logged and answered `200` with nothing stored, as the
   HTTP handler already does for `Content-Length: 0`.
3. `newWrite()`. Into it, through `ProxyZipWriter`: `0000000001.meta` holding the allowable
   headers, then `0000000001.dat` holding the body, gunzipped if `Compression: GZIP`, bounded by
   `maxRequestSize`. Then `proxy.entries` with the one group and its sizes, and `proxy.meta`.
4. `commit()`, publish with the feed, log `RECEIVE` with bytes and duration.

### 4.3 A zip body

1. Spool the body to `01_receiving/upload-*.zip`, bounded by `maxRequestSize`. (The scanner's
   path starts at step 2 with its own file.)
2. **Index** (`ReceivedZip.index`). Walk the central directory in physical order. Every entry
   must be readable (`canReadEntryData`), and no name may appear twice, or the zip is rejected
   (P5). Directory entries are skipped. Entries are grouped by `StroomZipEntries`' rules: an
   entry whose extension is not a known sidecar extension is **data**, whatever it is called - a
   received zip may be a plain collection of log files - and its whole name is its base name
   unless a sidecar claims a prefix of it; `.meta`, `.mf` and `.ctx` and their aliases (`.hdr`,
   `.header`, `.met`, `.manifest`, `.context`) attach to the data entry sharing their base name.
   Each `.meta` entry is read now, bounded, and merged over the allowable headers; the group's
   feed key is what that merge says, or the headers' feed and type when the group has no meta.
   Groups without a meta share one map made from the headers, so a zip of a hundred thousand
   plain files costs one map, not a hundred thousand.
   A sidecar-only group is set aside with a warning. A manifest on any but the first group, or
   a zip with no data groups, is rejected.
3. **Policy**, once per distinct feed key across the groups, on a copy of the headers with that
   feed and type set. Reject throws; drop removes that key's groups; a feed the policy names
   itself renames the key's groups and their metas. If nothing remains, log `DROP` and return
   `200`.
4. **Route.** One remaining feed key: the output queue, with that key. Several: the split-zip
   queue, or a refusal if there is none.
5. **Write** (`ReceivedZip.copyTo`), one store write: for each remaining group in arrival order,
   the next number, then `.mf` if present, `.meta` (the merged map), `.ctx` if present, `.dat`
   copied raw when the zip declares its size and inflated and counted otherwise. Then
   `proxy.entries` from the groups, and `proxy.meta` from the headers plus the feed and type
   when there is one.
6. `commit()`, publish, log one `RECEIVE` line with the bytes received.

Step 2 reads every `.meta` before any byte is written, so a rejection costs the spool and nothing
else.

### 4.4 Concurrency

Receipt is bounded by its callers: Jetty's request threads for HTTP, one thread each for the
scanner and the event-store roll. There is no semaphore and no `threads` block on the
receive stage.

### 4.5 Feed status refresh

`RemoteFeedStatusService` caches a status per feed. The first request for a feed loads it
synchronously, because there is nothing to answer from; every later request reads what is
cached. The registry schedule `feed-status-refresh`, in `HOUSEKEEPING` every thirty seconds,
reloads any entry older than a minute that has been read since it was loaded, outside any lock a
request takes, so a slow downstream never holds up a request that has an answer to read and a
feed nobody is sending costs nothing until someone does. Nothing is refreshed while the downstream
is disabled. A reload that fails keeps the previous answer, or the configured fallback on a first
load, and says so at ERROR. The service owns no threads and is not `Managed`; `ProxyLifecycle`
no longer starts or stops it.

### 4.6 The HTTP handler

`ProxyRequestHandler` keeps its shape: mint the receipt id, build the attribute map, authenticate,
normalise compression, decline zero content, elevate, call the receiver, answer `200` with the
id, or turn the exception into a status and a receive-log line. Compression is normalised once,
here.

### 4.7 Instant forwarding

`InstantForwardReceiver` runs the receipt policy once per request on the headers, then relays. To
an HTTP destination the body is streamed to `HttpSender` as it arrives and the sender is answered
when the downstream has accepted it, or with the downstream's own status code when it has not, so
a feed the downstream refuses is refused here rather than retried for ever. To a file destination the body and its headers are written
to a directory under `01_receiving` that the destination then takes; a directory the destination
refuses is removed here, because nothing else will. A dropped body is drained and logged as
dropped. `receiveZip` relays the file as a zip body. The policy chain is created per request, so
it follows the chain's own refresh.

### 4.8 What went

`SimpleReceiver`, `ZipReceiver`, `StoringReceiverFactory`, `ReceiverFactory`, `DropReceiver`,
`ReceiptHandOff`, `ReceiveStagePublisher`, `InstantForwardFile`, `InstantForwardHttpPost`,
`ReceiveStageThreadsConfig` and `stages.receive.threads.maxConcurrentReceives`, and the two
receiving directories `01_receiving_simple` and `01_receiving_zip`.

`FileGroup`, `ZipEntryGroup`, `ProxyZipWriter`, `ProxyZipValidator`, `ZipWriter` and the
split-zip stage stay: the first five are the file-group format every later stage reads or
writes, and the split-zip stage is what a multi-feed group is for.

## 5. Failure, traced

| Where it fails | What the caller sees | What is left |
|---|---|---|
| Authentication, unknown compression | Status code, receive log `REJECT`/`ERROR` | Nothing |
| Body exceeds `maxRequestSize` | `CONTENT_TOO_LARGE` | Spool deleted, write discarded |
| Entry unreadable, duplicate name, no data entries, gzip that is not gzip | `COMPRESSED_STREAM_INVALID` | Spool deleted |
| A `.meta` entry over its 1 MiB bound | `CONTENT_TOO_LARGE`, naming the entry | Spool deleted |
| Instant relay: the downstream refuses | The downstream's code | Nothing |
| Manifest not first, name the grouping cannot parse | `INVALID_FORMAT` | Spool deleted |
| Policy `REJECT` on any feed | The policy's code (`FEED_IS_NOT_SET_TO_RECEIVE_DATA`, `REJECTED_BY_POLICY_RULES`, …) | Spool deleted; nothing written |
| Policy `DROP` on every feed | `200`, receive log `DROP` | Nothing |
| Several feeds, no split-zip queue | `UNKNOWN_ERROR` with the reason | Spool deleted; nothing written |
| Store or queue failure before commit | `UNKNOWN_ERROR`, receive log `ERROR` | Write discarded; store housekeeping clears staging |
| Publish failure after commit | As above | An orphan group the sweep removes; the retry duplicates it |
| Process dies mid-receipt | Connection dropped; sender retries | Local: staging and spool cleared at start-up, orphan groups swept. Shared: cleared by age |

## 6. Tests

`TestReceivedZip` covers the index and the canonical copy: plain files as data with headers as
meta, sidecars attaching whatever the order and whatever the alias, feed keys distinct in order
of first appearance, a subset renumbered from one, a sidecar-only group omitted, a zip with no
data rejected, duplicate names rejected, an unreadable entry rejected, a manifest out of place
rejected.

`TestStoringReceiver` covers the receipt: plain, gzip and zip bodies each committed as one
canonical group and published with the right key to the right queue; a multi-feed zip refused
with no split-zip queue; a rejected feed refusing the whole zip and storing nothing; a dropped
feed never written and every feed dropped storing nothing; a body over the limit refused; an
empty body stored as nothing; a zip on disk read in place and left to the caller; a failed
publish reported; the spool cleared at construction and empty after every case.

`TestInstantForwardReceiver`, `TestRefusingReceiver`, `TestRemoteFeedStatusService` and
`TestInstantForwardingWiring` cover the other receivers, the refresh schedule's behaviour and
which receiver a configuration gets. `TestInnerProcessEndToEnd` and the `TestEndToEnd*` classes
drive the real handler through to a forwarded file.

## 7. What the implementation before this design did

- Two receivers behind a factory, each writing to its own numbered temp directory and handing
  the directory to a `Consumer<Path>` that copied it into the store, published, and deleted it:
  every received byte written twice, and a hand-off whose ownership rules were got wrong three
  times ([contracts §3](../contracts.md#3-hand-off-between-components)).
- The zip receiver cloned the upload entry by entry preserving names and order, then, if the
  policy dropped a feed, rewrote the clone a second time through a part file to remove that
  feed's entries. Whether the result was a canonical proxy zip was recorded in a flag that
  nothing acted on, and a single-feed non-canonical zip went to the pre-aggregator as it was.
- A zip with two entries of one name was accepted and one of them lost at the split.
- `maxConcurrentReceives` gated the copy-and-publish step only, after the clone that costs the
  most had already run.
- Feed statuses were refreshed by a cached thread pool from inside requests, and the service was
  a `Managed` bean whose stop order was alphabetical.
- `stages.receive.enabled: false` was accepted and ignored.
- Instant forwarding had two classes, its own receiver factory, and captured a policy filter chain
  once at boot that never refreshed.

## 8. Decisions

| # | Decision | What it overturned |
|---|---|---|
| **D1** | The split-zip stage stays; receipt commits a multi-feed zip as one canonical group for it. Owner's decision, 2026-09-08, against a proposal to split at receipt | Nothing; recorded because the alternative was put |
| **D2** | The receiver writes into a `FileStoreWrite` and commits; no receiving directory, no copy, no `Consumer<Path>` hand-off | `SimpleReceiver`/`ZipReceiver` → `ReceiveStagePublisher` and `ReceiptHandOff` |
| **D3** | One `Receiver` interface, bound in Guice to the storing, instant or refusing implementation | `ReceiverFactory`, `StoringReceiverFactory`, `DropReceiver`, and `enabled` being ignored |
| **D4** | Instant forwarding stays, as one `InstantForwardReceiver`. Owner's decision, 2026-09-08, against a proposal to delete it | `InstantForwardFile`, `InstantForwardHttpPost` |
| **D5** | No receive semaphore; the callers' threads are the bound | `maxConcurrentReceives` and `ReceiveStageThreadsConfig` |
| **D6** | Entries are grouped by `StroomZipEntries`' rules: unknown extension is data, sidecars augment the data entry of their base name, a sidecar with no data entry is omitted with a warning. Confirmed against master's `StroomStreamProcessor`, which did the same | The receiver's own parse, which treated only `.dat` as data by name and kept orphan sidecars in the zip |
| **D7** | Policy runs on the index, before any byte is written; a dropped feed's entries are never written rather than written and removed | `removeDroppedEntries` and its part-file rewrite |
| **D8** | Every stored zip is canonical: renumbered at receipt, `ProxyZipWriter`-validated | The `valid` flag nothing read, and non-canonical zips reaching the pre-aggregator |
| **D9** | Unreadable entries, duplicate names, a manifest out of place and a zip with no data are rejected with a status code | Silent discard of an encrypted zip; silent loss of a duplicate at the split |
| **D10** | A multi-feed zip with no split-zip queue configured is refused | Publishing it to the output queue, where the pre-aggregator mis-attributed it |
| **D11** | Feed-status refresh is a registry schedule; the service owns no threads and is not `Managed` | A cached thread pool and E7's interim start/stop in `ProxyLifecycle` |
| **D12** | Data entries are copied raw and their declared sizes recorded as advisory | Unchanged in effect; stated so that nothing downstream treats `proxy.entries` sizes as verified |

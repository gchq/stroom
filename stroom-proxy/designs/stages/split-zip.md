# Split-zip

[← Design documentation](../README.md) · [Contracts](../contracts.md) · [Receipt](receive.md) · [Aggregation](aggregate.md) · [File stores](../infrastructure/file-stores.md) · [Queues](../infrastructure/queues.md)

**Status: built, 2026-09-09.** Written with the aggregation design, whose
[D11](aggregate.md#8-decisions) keeps this stage and rebuilt it alongside. [§6](#6-what-the-implementation-before-this-design-did)
says what the implementation does today; [§7](#7-decisions) records the decisions.

---

## 1. What split-zip is for

A sender may post one zip whose entries belong to several feeds. Aggregation is per feed
([aggregate.md A3](aggregate.md#2-the-contract)), and on Kafka a feed's inputs reach one consumer
only when the message carries the feed as its key. Receipt commits a multi-feed zip as one
canonical group whose message carries no key, and sends it here. Split-zip turns it into one
canonical group per feed, each published with its key, so that from here on every input has a feed
and every feed's inputs can find each other. That restored affinity is the reason the stage exists
rather than being folded into aggregation ([aggregate.md D11](aggregate.md#8-decisions)).

It is stateless: one message in, N groups and N messages out, nothing held between messages.

**The boundary.** In: the stage processor, the split itself, and the stage's configuration. Out:
receipt, which decides what comes here; aggregation, which consumes what leaves.

## 2. The contract

**S1. One input, one output per feed key, every item in exactly one of them.** An **item** is one
base name's entries. The feed key of every item is what `proxy.entries` says, which receipt made
authoritative. No item is dropped, none is duplicated, and an output holds only items of its key.

**S2. Every output is a canonical proxy zip** (receipt P4): items renumbered from `0000000001`
in the order they appear in the input, each data entry preceded by its sidecars, the entries
copied raw and unchanged, `proxy.entries` describing the whole of the zip beside it, and
`proxy.meta` the input's headers with `Feed` and `Type` set to the key. An output is
indistinguishable from a single-feed group receipt would have committed.

**S3. Commit, publish, delete, acknowledge.** Each output is committed to the split store (C1)
and published to the output queue (Q1) with its key and the input's trace id, before the next is
begun. Once every output is published the input is deleted from its store and the message
acknowledged. The delete is housekeeping after the durable step ([contracts §3.2](../contracts.md#32-cleanup-after-a-durable-step-must-never-throw)):
a failure is logged, naming the group left for the store's sweep, and the message is still
acknowledged, since throwing would re-split the input and publish every output again on each
redelivery for as long as the delete kept failing. A redelivery that resolves to nothing is
acknowledged (R12). A crash after some
outputs are published re-splits the whole input and publishes those again: duplicates,
accepted.

**S4. Nothing is written outside the store.** Every byte of output goes straight into a store
write handle. There is no temporary directory, no staging root, and nothing to clear at start-up.

**S5. An input this stage cannot split is failed, with the reason, and never partly consumed.**
A group missing a member, an entries file naming an entry the zip does not hold, or an entries
file with no items, fails the message (Q6). Nothing is deleted. Receipt already refuses a zip with
two entries of one name, so a name in `proxy.entries` identifies one entry.

## 3. The interface

The stage keeps the worker: `SplitZipStage implements FileGroupQueueItemProcessor`, driven by
`FileGroupQueueWorker` on the `stage-splitZip` loop with `consumerThreads` threads. The item is
acknowledged or failed by the worker when `process()` returns or throws, as for forward.

```java
public final class SplitZipStage implements FileGroupQueueItemProcessor {
    SplitZipStage(FileStoreRegistry stores, FileStore output, FileGroupQueue outputQueue,
                  String producerId);
    @Override public void process(FileGroupQueueItem item) throws Exception;
}
```

The copying of items from one canonical zip into another - renumbering, raw entry copy, entry
groups rebuilt from what was written - is one class, `CanonicalZipCopier`, shared with the
aggregate stage's merge, which does the same thing for a range of items instead of a feed's.

## 4. Design

`process()`:

1. Resolve the input (C3). Read `proxy.entries` into a list of items in order; read `proxy.meta`.
   Group the items by feed key, keeping the input's order within each group.
2. For each feed key: `output.newWrite()`; open the input zip once for the whole split and copy
   that key's items through the copier into `proxy.zip` under the write's path, building
   `proxy.entries` as it goes; write `proxy.meta` as the input's headers with the key's feed and
   type; `commit()`; publish with the key, the input's trace id and `producingStage: splitZip`.
3. Delete the input's location, logging rather than throwing if that fails; return. The worker
   acknowledges.

A throw anywhere before step 3 leaves the current write handle to discard itself and the
already-committed outputs as orphans the store clears (C6); the worker fails the message and it is
redelivered. There is no `finally` and nothing to clean up, because nothing was written anywhere but
the store.

**Concurrency.** The stage has no state, so `consumerThreads` is a throughput setting and
nothing more.

**Configuration.** Unchanged in shape; the default output queue becomes `aggregateInput`.

```yaml
pipeline:
  stages:
    splitZip:
      enabled: true
      inputQueue: splitZipInput
      outputQueue: aggregateInput
      fileStore: splitStore
      threads:
        consumerThreads: 1
```

## 5. Tests

- **S1.** A zip of three feeds interleaved yields three outputs, each holding exactly its items in
  input order, and the union is the input.
- **S2.** Every output passes the canonical check used for receipt and aggregation: renumbered,
  `proxy.entries` names exactly the zip's entries, `proxy.meta` carries the key.
- **S3.** A recording store and queue see commit, publish, commit, publish, then delete; a
  redelivered message whose input is gone is acknowledged.
- **S4.** After a split, the data and temp directories hold nothing of this stage's.
- **S5.** A group missing its zip, an entries file naming a missing entry, and an empty entries
  file each fail the message with the reason and delete nothing.
- The existing `TestZipSplitter` cases - one key, two feeds, two feeds and two types, a
  disallowed feed already removed by receipt - are ported to the new interface.

## 6. What the implementation before this design did

`ZipSplitter` split into a temporary directory under `<path.temp>/pipeline/splitZip`, one
subdirectory per feed named by a sanitised, collision-suffixed feed key, then
`SplitZipStageProcessor` copied each subdirectory into a store write, published, deleted the input,
and deleted the temporary in a `finally`. Every entry's `.meta` was read, merged under the input's
headers and rewritten, which receipt has done already since 2026-09-08. Each entry was looked up by
name with a guard against duplicate names, which receipt now rejects at the edge. About 830 lines
across three classes.

## 7. Decisions

Taken on my recommendation; each is small enough that it is recorded here rather than put as a
question, and any of them can be overturned.

**D1. Entry metas are copied raw.** Receipt writes every `.meta` as the merge of the entry's own
meta over the allowable headers (receipt §4.3 step 5), so re-reading and rewriting each one here
repeated that work on every split. A raw copy is what the aggregate stage does too.

**D2. Entries are found by the name `proxy.entries` gives them.** Receipt refuses a zip with two
entries of one name, so the name identifies the entry; a name the zip does not hold fails the
input (S5) rather than being guarded against.

**D3. Commit and publish per output, interleaved.** Committing all outputs before publishing any
would leave the same duplicate window on a crash and hold more uncommitted writes open. Per-output
is simpler and bounds the open handles to one.

**D4. No temporary directory.** Writing straight into the store write handle removes the temp
root, its start-up clear, its cleanup `finally`, and the configuration that located it.

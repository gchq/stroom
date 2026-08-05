# Plan B merge idempotency for additive stores

Status: implemented (`MergeStatusDb`, `LmdbWriter.abort()`, merge reworks in `HistogramDb`/`MetricDb`,
quiescence gated pruning driven from `MergeProcessor.maintainShards`). Notable deviations from the
original proposal are listed at the end.
Related issue: [gh-5696](https://github.com/gchq/stroom/issues/5696) (follow-on work).

## Problem

Plan B merges data received from processing nodes into shard LMDB stores. Most store types merge with
plain puts, so merging the same source data twice is harmless. Two store types are **additive**:

* `HistogramDb` — always. `HistogramCountSerde.merge()` sums the source value into the existing value.
* `MetricDb` — when the doc's `MetricValueSchema` enables `storeCount` or `storeSum`.
  `MetricCountSerde.merge()` sums those two fields (latest value, min and max are idempotent). Most metric
  stores enable them, as mean is derived as sum/count at query time.

For additive stores, merging a source more than once double-counts. Re-merging is not a corner case, it is
the designed recovery mechanism: since gh-5696, data queued for merge survives a restart, and an
interrupted merge is expected to be rerun. Worse, merges commit incrementally (`LmdbWriter.tryCommit()`
commits every ~10k changes, and `LmdbWriter.close()` commits rather than aborts when the merge fails part
way through), so a rerun after an interruption re-adds the already committed prefix of the source.

The idempotent store types (state, temporal state, range state, session, trace, and metric without
count/sum) do not have this problem and are out of scope, except as noted in open questions.

## When a source is merged more than once

A "source" here is one part shard: the LMDB instance a processing node writes for one stream and posts to
the storage node(s), which travels as a zip into the staging store
(`SequentialFileStore`), is unzipped, and is moved as a dir onto a merge queue (`DirQueue`) under
`merging/<doc uuid>/`. There are three replay routes:

1. **Queue dir replay.** A merge fails or is interrupted, the dir stays on the queue on disk, and the
   recreated queue presents it again after the next boot.
2. **Staging zip replay.** The staged zip is only deleted after its dirs have been moved onto merge
   queues. A crash inside that window leaves both the queued dirs and the zip. After boot the zip is
   re-unzipped and queued again, so the *same source exists as two queue entries at once*.
3. **Sender retry.** With `synchroniseMerge`, the sender only gets its response after the merge completes.
   If the receiver fails after merging but before responding, the sender retries the same part, which can
   arrive at any time, not just around a boot.

Route 2 and 3 are why identity cannot be based on queue position: a replayed source can arrive as a new
queue entry at a new path, and `DirQueue` ids are reused once a queue drains (`writeId` restarts from the
max id on disk at boot).

## Design

### Source identity: an instance UUID inside the source itself

Every Plan B LMDB env already has an `info_db` DBI (`AbstractDb`) holding `InfoKey` entries
(schema version, key/value schema, hash clashes). Add:

* `InfoKey.INSTANCE_UUID` — a random UUID written **once**, when a writable env is first created and the
  key is absent, in the same transaction that writes the schema (`AbstractDb` constructor,
  `!env.isReadOnly()` block).

Because `ShardWriters` creates part shards through the same constructor (`PlanBDb.open(..., readOnly =
false)`), the UUID is minted on the processing node before the part is zipped. It is therefore baked into
`data.mdb` and survives the entire journey: zip, transfer, staging, unzip, queueing, and every replay
route above. A re-unzipped zip yields the same UUID; a genuinely reprocessed stream is a new writer
instance and gets a new UUID, so it is correctly not treated as a duplicate.

Target shards get a UUID too, which is harmless and gives snapshots provenance for free.

### Optional: source stream id for provenance

`ShardWriters.createWriter(Meta)` creates one `ShardWriter` per stream and holds the `Meta`, and the
per-doc source envs are created inside it, so the stream id is in scope when each source LMDB instance is
created. Add `InfoKey.SOURCE_META_ID`, written by the writer after opening the env (avoiding signature
changes to every store constructor; it can share a transaction with the instance UUID write).

This plays no part in dedupe — the UUID remains the identity, deliberately: a reprocessed stream produces
a new part with the *same* meta id that must not be deduped against the old one. The meta id is purely
provenance, letting a merged part (via its merge status record) be traced back to the exact stream that
produced it.

### Merge status: a dedicated DBI in the target shard

Each additive target store gains a `merge_status_db` DBI, keyed by the source instance UUID (16 bytes).
The value records the merge state for that source:

* `IN_PROGRESS` + the raw bytes of the **last merged source key** (the cursor) + last update time.
* `COMPLETE` + completion time.

Sources can be too large to merge in one LMDB transaction, so incremental commits stay, and per-key
progress must be tracked; a complete/absent flag alone is not enough. The cursor makes each batch commit
a consistent recovery point:

* The cursor is written in the **same transaction** as each batch commit. Where the merge loop currently
  calls `tryCommit()`, it instead checks `shouldCommit()`, writes the cursor for the current source key,
  then commits. Crash recovery can therefore trust it exactly.
* The `COMPLETE` record is written in the final transaction of the merge (committed by
  `LmdbWriter.close()`), so a source is atomically either fully merged and marked complete, or resumable
  from the cursor.

Cursor comparisons use raw source key bytes under LMDB's default unsigned lexicographic ordering, which is
also the iteration order of `LmdbIterable`. The source is immutable, so iteration order is identical on
every replay, and "skip keys <= cursor" resumes exactly after the last committed entry.

`StoreShard.merge()` holds the shard write lock for the whole merge, so `merge_status_db` has
single-writer semantics and needs no further coordination.

### Merge algorithm for additive stores

On merge of source dir with UUID `S` (read from the source's `info_db` via the existing read-only open):

1. `S` absent from `merge_status_db` → merge from the start, maintaining the cursor per batch, mark
   `COMPLETE` in the final transaction.
2. `S` is `IN_PROGRESS` → merge, skipping source entries `<= cursor`, then as above.
3. `S` is `COMPLETE` → skip entirely. Still delete the source dir and count down any synchronise-merge
   latch, exactly as a successful merge would.
4. Source has **no UUID** (written before this change) → legacy fallback: merge as today, no status
   tracking. This keeps the upgrade path a non-event; the window closes as old parts drain.

### Status record lifecycle

Records cannot be deleted when the source dir is deleted: replay routes 2 and 3 mean another copy of the
same source may still surface afterwards, and it must find the `COMPLETE` record. Instead:

* Records carry timestamps and are pruned by the existing shard maintenance pass
  (`ShardManager.condenseAll` → `deleteOldData`), which runs every 10 minutes.
* Prune `COMPLETE` records older than a retention period (suggested default 30 days, configurable).
* Pruning is additionally gated on **quiescence**, checked at prune time:
  1. the staging store has no pending zips, and
  2. the doc's merge queue directory contains **no dirs on disk**.

  (2) must be a filesystem check, not `DirQueue`'s in-memory `readId > writeId`: a dir whose merge failed
  is skipped past by the consumer but remains on disk awaiting boot-time replay, so the in-memory queue
  can look empty while a replayable copy exists.

  If both hold, every pre-existing copy of every source has been consumed, so any `COMPLETE` record older
  than the retention is dead. If the system is never quiet, pruning defers, which is safe; records just
  live longer. The gate also covers sender retries (route 3) with no notion of a "recovery phase": a
  retry lands in staging and blocks pruning until it has been consumed and skipped via its record.
* The residual race (record pruned in the same instant a copy arrives) requires that copy to be older
  than the retention, which at 30 days is not a realistic scenario. The retention and the gate back each
  other up.
* `IN_PROGRESS` records are not pruned on the normal retention (an interrupted merge may legitimately
  wait for the next boot). Prune them only at a much larger age as a backstop against sources that were
  deleted without ever completing.

### Rejected alternatives

* **Single-transaction merges with a complete-only marker.** Simplest by far, but sources can exceed a
  practical single LMDB transaction, so per-key progress tracking is required.
* **Queue path / id as identity.** Ids are reused after queues drain, and staging replay re-enters the
  queue at a new path.
* **Sidecar identity file stamped at unzip time.** Fixes path reuse but not staging replay (a re-unzip
  would stamp a fresh identity). The UUID inside the LMDB instance survives both.
* **Delete the status record when the source dir is deleted.** Unsafe: another copy of the same source
  (routes 2 and 3) can arrive after the deletion and would fully re-merge.

## Implementation notes

* `AbstractDb`: add `InfoKey.INSTANCE_UUID(4)`; write-once logic beside `writeSchema`; accessor to read a
  source's UUID.
* `HistogramDb` / `MetricDb`: open `merge_status_db`; merge loop changes (skip logic, cursor writes,
  complete marker). `MetricDb` only needs the mechanism when the schema enables count or sum.
* `PlanBEnv` `maxDbs`: bump for the stores gaining the extra DBI.
* `MergeProcessor` / `ShardManager`: expose the quiescence inputs (staging store drained; doc queue dir
  empty on disk) to the maintenance pass; add pruning to the maintain job.
* Snapshots zip the whole shard dir and `compact()` copies the whole env, so `merge_status_db` travels
  with both. That is correct: a snapshot restored as a shard retains the dedupe history.
* Config: retention duration for `COMPLETE` records (and larger backstop age for `IN_PROGRESS`).

## Testing

* Interrupt an additive merge after the first batch commit (fault-injecting source or wrapped writer),
  replay the same source, assert final counts are exact, not doubled.
* Replay a fully merged source (simulating routes 1 to 3), assert a no-op skip and dir deletion.
* Duplicate queue entries for the same UUID in one run (route 2), assert single counting.
* Legacy source without a UUID merges as today.
* Pruning: gated correctly (no prune while a stranded dir exists on disk or staging is non-empty), prunes
  only past retention, `IN_PROGRESS` survives normal retention.

## Implementation deviations from the proposal above

* **Abort on failure.** Additive merges previously ran as one transaction that `LmdbWriter.close()`
  committed even when the merge threw part way through — that commit-on-failure was the whole partial
  merge problem. `LmdbWriter.abort()` was added and additive merges abort on any exception, so commits
  only happen at deliberate points (each batch, with its cursor, and completion). A corollary: a legacy
  source with no instance UUID is merged as a single un-batched transaction with abort on failure, which
  is already exact, just unbounded in transaction size.
* **Batch commits were added, not moved.** The additive merges had no incremental commits; the ~10k
  change threshold batching (via `LmdbWriter.shouldCommit()`) was introduced along with the cursor, so
  big sources now also get bounded transactions.
* **Resume is an equality scan.** Rather than comparing keys against the cursor under LMDB's unsigned
  lexicographic order, the resumed merge skips entries until it sees the exact cursor key then merges the
  rest. Iteration order over the immutable source is identical on every run, so this is exact and avoids
  depending on a hand rolled comparator.
* **One retention for both states.** `IN_PROGRESS` records are pruned on the same retention as
  `COMPLETE` ones rather than a larger backstop. The quiescence gate carries the correctness burden: any
  on-disk copy blocks pruning regardless of age, and the only post-quiescence resurrection route (a
  sender retry of an unacknowledged part) has a horizon of minutes, far inside the retention.
* **No `maxDbs` bump was needed** — the additive stores already allowed 20 DBIs. `merge_status_db` is
  only opened on writable envs, as read only source opens never need it and could not create it.

## Open questions

* Whether idempotent stores should also consult the marker to skip whole re-merges as a performance
  optimisation (correctness does not require it).
* Typical additive source sizes in production, to sanity-check batch threshold and cursor overhead.

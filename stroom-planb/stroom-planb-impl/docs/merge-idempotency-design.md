# Plan B merge idempotency for additive stores

Related issue: [gh-5696](https://github.com/gchq/stroom/issues/5696).

## The problem this solves

Plan B merges data received from processing nodes into shard LMDB stores. Most store types merge with
plain puts, so merging the same source twice is harmless. Two store types are **additive**:

* `HistogramDb` — always. `HistogramCountSerde.merge()` sums the source value into the existing value.
* `MetricDb` — when the doc's `MetricValueSchema` enables `storeCount` or `storeSum`.
  `MetricCountSerde.merge()` sums those two fields (latest value, min and max are idempotent). Most metric
  stores enable them, as mean is derived as sum/count at query time.

For additive stores, merging a source more than once double counts, and re-merging is not a corner case:
data queued for merge survives a restart, and rerunning an interrupted merge is the designed recovery
mechanism. There were two double count routes before this mechanism existed: a fully merged source being
presented again, and — more subtly — `LmdbWriter.close()` **committing** rather than aborting the partial
work of a merge that failed part way through, which a rerun would then re-add.

A "source" is one part shard: the LMDB instance a processing node writes for one stream and posts to the
storage node(s), which travels as a zip into the staging store (`SequentialFileStore`), is unzipped, and
is moved as a dir onto a merge queue (`DirQueue`) under `merging/<doc uuid>/`.

## How a source can be merged more than once

1. **Queue dir replay.** A merge fails or is interrupted, the dir stays on the queue on disk, and the
   recreated queue presents it again after the next boot.
2. **Staging zip replay.** The staged zip is only deleted after its dirs have been moved onto merge
   queues. A crash inside that window leaves both the queued dirs and the zip. After boot the zip is
   re-unzipped and queued again, so the *same source exists as two queue entries at once*.
3. **Sender retry.** With `synchroniseMerge`, the sender only gets its response after the merge completes.
   If the receiver fails after merging but before responding, the sender retries the same part, which can
   arrive at any time, not just around a boot.

Routes 2 and 3 are why identity is not based on queue position: a replayed source can arrive as a new
queue entry at a new path, and `DirQueue` ids are reused once a queue drains.

## How it works

### Source identity

Every Plan B LMDB instance carries `InfoKey.INSTANCE_UUID` in its `info_db`: a random UUID written once,
when a writable env is first created, in the same transaction as the schema (`AbstractDb` constructor).
`ShardWriters` creates part shards through that constructor on the processing node, so the UUID is baked
into `data.mdb` before the part is zipped and it survives the entire journey, including every replay route
above. A re-unzipped zip yields the same UUID; a genuinely reprocessed stream is a new writer instance and
gets a new UUID, so it is correctly not treated as a duplicate.

`ShardWriters` also records `InfoKey.SOURCE_META_ID`, the id of the stream the part was written from. This
is provenance only and plays no part in de-duplication, deliberately: a reprocessed stream produces a new
part with the *same* meta id that must not be deduped against the old one.

### Merge status tracking (`MergeStatusDb`)

Each writable additive store owns a `merge_status_db` DBI, keyed by source instance UUID. The value is a
state byte, a timestamp, and for in-progress merges a cursor holding the raw bytes of the last source key
included in a commit. All status writes happen in the same transaction as the merged data they describe,
so status and data can never disagree:

* The merge loop is wrapped by `MergeStatusDb.MergeTracker`. Each entry increments the writer change
  count, and at the `LmdbWriter.shouldCommit()` threshold (~10k changes) the cursor is written and the
  batch committed — one atomic unit. (These batch commits are new; additive merges previously ran as one
  unbounded transaction.)
* On success the `COMPLETE` record is written into the final transaction, committed with the last batch of
  data by `LmdbWriter.close()`.
* On **any** `Throwable` the merge calls `LmdbWriter.abort()`, discarding the uncommitted tail, and
  rethrows. The shard is left exactly at the last batch commit, whose cursor matches. This closes the
  commit-on-failure hole in `close()`.

On merge start the tracker consults the status record:

* `COMPLETE` → the whole merge is skipped. The source dir is still deleted and any synchronise-merge
  latch counted down, exactly as a successful merge would.
* `IN_PROGRESS` → resume: entries are skipped until the exact cursor key is seen, then merging continues
  with the next entry. Iteration order over the immutable source is identical on every run, so an
  equality scan is exact and avoids depending on a byte-order comparator. If the cursor key is never seen
  (impossible unless the source content changed) the merge logs an error and marks complete rather than
  retrying forever.
* No record → merge from the start.
* **No UUID** (a legacy part written before instance UUIDs) → no status tracking. The merge runs as a
  single un-batched transaction with abort on failure, which is still exact, just unbounded in
  transaction size. The window closes as old parts drain.

`StoreShard.merge()` holds the shard write lock for the whole merge, so `merge_status_db` has
single-writer semantics.

### Status record lifecycle

Records are **not** deleted when the source dir is deleted: routes 2 and 3 mean another copy of the same
source may surface afterwards and must find the `COMPLETE` record. Instead they are pruned by age from the
maintenance job:

* `MergeProcessor.maintainShards()` (every 10 minutes) runs
  `ShardManager.deleteOldMergeStatus(quiescenceTest, deleteBefore)` after `condenseAll`, which calls
  `StoreShard.deleteOldMergeStatus()` (under the shard write lock) →
  `Db.deleteOldMergeStatus()` (default no-op; implemented by the additive stores).
* `deleteBefore` comes from `PlanBConfig.mergeStatusRetention` (default 30 days).
* Each doc must pass the **quiescence test** (`MergeProcessor.isQuiescent`) at prune time: the staging
  store is fully drained, and the doc's merge queue directory holds no dirs **on disk**. The disk check
  matters: a dir whose merge failed is skipped past by the queue consumer, so the in-memory queue looks
  empty while a replayable copy remains for the next boot. If either check fails, pruning defers, which is
  always safe.
* In-progress and complete records share one retention. The quiescence gate carries the correctness
  burden — any on-disk copy blocks pruning regardless of age — and an *active* merge's record is never at
  risk because every batch commit refreshes its timestamp. The only post-quiescence resurrection route, a
  sender retry of an unacknowledged part, has a horizon of minutes, far inside the retention; the
  retention also guards the instant between a passing quiescence test and the prune.

### Incidental properties

* Snapshots zip the whole shard dir and `compact()` copies the whole env, so `merge_status_db` travels
  with both. That is correct: a snapshot restored as a shard retains its dedupe history.
* Sources are opened read only and never open `merge_status_db`; the additive stores only open it on
  writable envs. No `maxDbs` change was needed (the stores allow 20 DBIs).
* The in-memory hash clash counter is not rolled back by an aborted merge, so it can slightly overstate
  after a failure. It is diagnostic only.
* An empty-but-present staging depth dir makes `SequentialFileStore.getMinStoreId()` return 0 rather
  than -1, which reads as not-quiescent and defers pruning. Safe direction; the store's own dir cleanup
  normally removes such dirs.

## Rejected alternatives

* **Single-transaction merges with a complete-only marker.** Simplest by far, but sources can exceed a
  practical single LMDB transaction, so per-key progress tracking is required.
* **Queue path / id as identity.** Ids are reused after queues drain, and staging replay re-enters the
  queue at a new path.
* **Sidecar identity file stamped at unzip time.** Fixes path reuse but not staging replay (a re-unzip
  would stamp a fresh identity). The UUID inside the LMDB instance survives both.
* **Delete the status record when the source dir is deleted.** Unsafe: another copy of the same source
  (routes 2 and 3) can arrive after the deletion and would fully re-merge.
* **Meta id as identity.** Already carried by `FileDescriptor`, but reprocessing legitimately reuses a
  meta id with different data, which must merge, not dedupe.

## Tests

* `TestMergeStatusDb` — tracker mechanics: interrupted merge resumes exactly after the last batch commit;
  abort discards uncommitted writes; legacy sources are untracked; pruning honours age.
* `TestAdditiveMergeStatus` — end to end: a bit-identical duplicate of a histogram part merges to a
  no-op; pruning the record deliberately re-enables the double count (proving the record is what prevents
  it); metric count and sum survive a duplicate merge unchanged.
* `TestMergeProcessor.mergeStatusPruningIsGatedOnQuiescence` — a stranded queue dir on disk or a waiting
  staged zip blocks pruning.
* `TestDbInfo` — instance UUID is minted once, survives reopen, and distinct instances differ; source
  meta id round-trips.

## Future work

* Idempotent stores could also consult the marker to skip whole re-merges as a performance optimisation
  (correctness does not require it).
* Measure typical additive source sizes in production to sanity-check the batch threshold and cursor
  overhead.

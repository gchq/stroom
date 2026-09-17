# Worker Node Task Selection — Design and implementation

**Status: built.** gh-5699, branch `gh-5699_task_selection_by_worker2` (uncommitted).

Stroom now has **two ways of getting processor tasks onto worker nodes**, selected by
`stroom.processor.claimTasksOnWorker`:

| | |
|---|---|
| **`true`** | **Worker claiming. Experimental, and NOT the default.** Each node works out which filters it may process, asks one query which of them have work, and claims tasks straight from CREATED to PROCESSING with `SELECT … FOR UPDATE SKIP LOCKED`. No master node is involved. Described in [§3](#3-worker-claiming-as-built). |
| **`false`** (default) | **The master queue.** The pre-existing behaviour, unchanged: the master node fills an in-memory queue and hands tasks out on request. Described in [§2](#2-the-master-queue-mode-unchanged). |

The property must be **identical on every node**, and changing it is a hard cutover: stop the whole
cluster, change it everywhere, start again. A mixed cluster is not supported. See
[§4.1](#41-deleting-the-master-queue) for why both are shipped rather than one.

**What was proved before it shipped** ([§8](#8-testing-and-measurement)):

- The availability summary's query plan is a loose index scan on an **existing** index, ~20ms warm
  at 2000 filters over 362k rows, with no migration. This was the design's one falsifiable
  condition and it passed at the degenerate no-profile filter counts, not just friendly ones.
- Measured against the master queue over the same data: throughput comparable at 200 filters and
  +37% at 1000, and **p99 dispatch latency 204ms against 13.3 seconds**. The tail, not the
  throughput, is the result.
- The summary's query rate is **independent of filter count** — 56 queries at both 200 and 1000
  filters — because it is bounded by (nodes × elapsed ÷ `taskAvailabilityInterval`).

**Consequently [Option B](#42-option-b--denormalise-priority-and-profile_id-onto-processor_task)
— denormalising `priority` and `profile_id` onto `processor_task` — was NOT taken**, and is
recorded in [§4](#4-future-work) as the fallback if worker claiming turns out not to keep a real
cluster fed. Nothing about it is built.

**Date:** designed 2026-08-03, built 2026-08-05/06.

**Goal:** let worker nodes find and claim their own processor tasks instead of being fed by the
master node's central queue, so that task selection knows what each node may actually do, and so
that processing does not depend on there being a master node.

**Standing constraint: no master node.** The longer-term direction is to remove any sense of a
master node from Stroom, so nothing here may introduce a new master responsibility — and in
particular **nothing may be cached on the master**, not even as an optimisation. Where the master
was load-bearing, this either removes the need or replaces it with a cluster lock (the reaper is
the worked example). The concrete target is **zero callers of `getMasterNode()` in
`stroom-processor`**; the claiming path has none, but the target is not met while the master queue
is retained alongside it — see [§4.1](#41-deleting-the-master-queue).

> **Provenance.** Originally verified against `97273a0d09`; §2 re-verified line by line against
> `d6c305c9ba` on 2026-08-04, seven stale references corrected and no architectural claim changed.
> §2 describes code that is still live, so re-verify it after any further work on
> `stroom-processor`.

**Related work:**

- gh#5679 — slow node task assignment on a 64-node cluster. Fixed the symptom (profiles gated
  assignment but not queueing); this design addresses the cause.
- #5282 — processing profiles, which introduced per-node eligibility.
- #3259 — split task creation from task queueing (2023). Master-only queueing is a vestige of
  the era when they were one process.
- #5683 — `minMetaId` watermark / poll lag. Same family of "silent skip" hazard; relevant
  precedent for cursor design (see [Rejected alternatives](#rejected-and-superseded-alternatives)).
- #5684 — an index on `processor_task` benchmarked and **rejected**. Precedent for how index
  proposals on this table are treated.
- #5690 — task creation linear backoff (`FilterPollBackoff`, `tracker.nextPollMs`,
  `filter.maxTaskCreationDelay`). Already landed.
- #5691 — per-processing-profile task creation budgets (`TaskCreationBudgets`). Already landed.
- `FilterFetchBackoff` — the same idea applied to the *queue fill* side, landed as part of #5691
  (`bd85b80422`). Directly overlaps §3.2; see there.

---

## 1. The problem

Task **assignment** is per-node — a filter with a processing profile may only be processed by
nodes in that profile's node group, during that profile's periods. Task **queueing** is
cluster-wide and happens on the master, which does not know which node will ask for work next.

The master therefore has to guess on behalf of nodes it cannot identify.
`ProcessorTaskQueueManagerImpl.getMaxConcurrentTasks()` (`:858`) is that guess: it sweeps *every
enabled node* through `ProcessorProfileCache` to decide whether *any* node could process a
filter, and releases the filter's queue if not.

Consequences, all structural rather than bugs:

- A high-priority filter bound to node-group A consumes queue budget on a fill that ends up
  serving node B.
- An enabled-but-never-requesting node can park tasks and stall lower-priority queueing
  (accepted as "live with it" during gh#5679; production signature is a `filterQueues` sysinfo
  entry that never drains).
- Every limit — filter `maxProcessingTasks`, profile `maxClusterThreads`, profile
  `maxNodeThreads` × allowed nodes — has to be collapsed into one cluster-wide number by
  `getMaxConcurrentTasks()`, losing the per-node distinction that motivated profiles.

A node knows its own group membership and its own profile eligibility exactly. Moving the
decision to the node removes the guess.

---

## 2. The master queue mode (unchanged)

This is what ran before this work and what still runs when `claimTasksOnWorker` is **false**. It is
described in the present tense because it is live code, not history. Verified 2026-08-03,
re-verified against `d6c305c9ba` 2026-08-04.

### Queueing (master only)

`ProcessorTaskQueueManagerImpl.doQueueNewTasks()` (`:703`) walks `prioritisedFilters.get()`
(`:715`) in priority order. Two skip conditions come before any query:

- **Per-profile queue budget** (#5691). `QueueProcessTasksState` now holds a `ProfileQueueState`
  (a nested type) per processing profile (plus one for the no-profile filters) rather than a
  single global budget. A full profile skips its filters and the loop only breaks once
  `isEveryQueueFull()` (`:748`).
- **`FilterFetchBackoff.isFetchDue()`** (`:752`). A filter whose last fetch found nothing is
  skipped for `skipEmptyFilterFetchDuration` (default 10s), because a profile with nothing to do
  never reaches its budget and would otherwise be re-queried on every fill.

For filters that survive both (`queueTasksForFilter:909` → `queueCreatedTasks:966`):

1. `getMaxConcurrentTasks(filter)` (`:858`) — release the queue and skip if no node could process
   it.
2. `findExistingCreatedTasks(lastTaskId, filterId, batchSize)` (`:999`) — CREATED tasks for the
   filter.
3. `metaService.findLockedMeta(metaIds)` (`:1024`) — drop tasks whose stream is still being
   written.
4. `processorTaskDao.queueTasks(idSet, nodeName)` (`:1037`) — CREATED→QUEUED, owned by the master.
5. Survivors go into an in-memory `ConcurrentHashMap<ProcessorFilter, ProcessorTaskQueue>`.
6. `recordEmptyFetch` / `recordFetchedTasks` (`:1074`/`:1076`) update the backoff state.

A trailing sweep releases queues for filters that are no longer enabled or whose profile has
closed (`releaseTasksIfProfileInactive:816`, called from `:795`).

### Creation (any node, cluster-locked — *not* master-only)

`ProcessorTaskCreatorImpl.exec()` (`:153`) takes a cluster lock
(`clusterLockService.tryLock(LOCK_NAME, …)`, `:167`) and calls `createNewTasks()` from `:170`
(declared at `:177`). The job runs on **every** enabled node; the lock merely ensures one at a
time. This is
the pattern the rest of the design follows for periodic work (§3, §3.4), and it is worth being
precise about because §3.2's re-arm analysis depends on it. Creation is already master-free.

`createNewTasks` applies:

- **Per-profile creation budgets** (#5691, `TaskCreationBudgets`) — same rationale as the queue
  budgets: a busy profile can no longer consume the whole run.
- **Linear poll backoff** (#5690, `FilterPollBackoff`) — a filter that created nothing last poll
  is skipped until `tracker.nextPollMs`, backing off up to `filter.maxTaskCreationDelay`.

`doCreateTasksForFilter` (`:431`) then counts the filter's existing CREATED tasks
(`countTasksForFilter`, `:444`) and both charges them to the profile budget
(`budgetUsed.add(currentCreatedTasks)`, `:445`) and subtracts them from what it may create
(`maxTasks = remaining - currentCreatedTasks`, `:447`). This matters to §6 item 6.

### Assignment

The REST entry point is `assignTasks(sourceTaskId, nodeName, count)` (`:202`), which wraps a task
context and delegates to `assignTasks(nodeName, count, taskContext)` (`:221`). That walks the same
filter list, applies the requesting node's profile limits, and polls each filter queue. On an
empty result it fills the queue synchronously and retries, up to `MAX_ASSIGNMENT_ATTEMPTS` (10,
`:89`).

### Worker side

`DistributedTaskFetcher.fetch()` (`stroom-job-impl`, `:136`) is demand-driven: a single loop
thread blocks on a condition and wakes when a task completes. `doFetch` (`:177`) asks for
`getTotalTaskLimit()` (declared `:283`, called `:192`) = **all currently free thread slots**, not
one.

**It already coalesces concurrent demand into the next fetch** — see §3.7, which an earlier draft
got wrong. `needsTasks.set(false)` (`:141`) happens *before* `doFetch`, so a completion during a
fetch calls `signal()` (called `:255`, declared `:268`) and sets it back to true; the loop then
goes straight round with no wait, and the next `getTotalTaskLimit()` picks up every slot freed
during the previous fetch in one batch. Only when nothing completed during the fetch does it block
on `condition.await(10s)` (`:157`).

`DataProcessorTaskFactory.fetch()` (`:76`) REST-calls the master's `assignTasks`.
`DataProcessorTaskHandler.exec()` (`:103`) then does QUEUED→PROCESSING via `changeTaskStatus`
(`:149`) and COMPLETE/FAILED in the `finally` block.

### Dead task recovery

`disownDeadTasks()` (`:607`) runs on the master only (gate at `:611`), every 1m
(`ProcessorModule:105-110`, `frequencySchedule("1m")`; staleness threshold
`disownDeadTasksAfter`, default 10m, `ProcessorConfig:106`). It maintains `lastNodeContactTime`
from `getEnabledActiveTargetNodeSet()`, then calls
`retainOwnedTasks(retainForNodes, statusOlderThan)` (`ProcessorTaskDaoImpl:313`), which resets
tasks that are stale **and** whose node is not in the retain set.

**This is the critical dependency.** `status_time_ms` is only written on status change, so a
three-hour task looks stale within 10 minutes. It survives purely because its node is in the
retain set — a whole-node signal, not a per-task one. Decentralised nodes stop calling the
master, so this signal disappears entirely and must be replaced.

### Task status model

`TaskStatus`: `CREATED`(0), `QUEUED`(1), `ASSIGNED`(2, `@Deprecated`), `PROCESSING`(3),
`COMPLETE`(10), `FAILED`(22), `DELETED`(99). `ASSIGNED` is already vestigial.

### Indexes on `processor_task`

| Index | Columns |
|---|---|
| PRIMARY | `id` |
| `processor_task_fk_processor_filter_id` | `fk_processor_filter_id` |
| `processor_task_fk_processor_node_id` | `fk_processor_node_id` |
| `processor_task_fk_processor_feed_id` | `fk_processor_feed_id` |
| `processor_task_meta_id_idx` | `meta_id` |
| `processor_task_status_create_time_ms_idx` | `status, create_time_ms` |
| `processor_task_status_time_ms_status_idx` | `status_time_ms, status` |
| `processor_task_filter_id_status_id_meta_id` | `fk_processor_filter_id, status, id, meta_id` |

There is **no** unique constraint on `(fk_processor_filter_id, meta_id)` (see #5683 notes), and
adding one is not as simple as it looks — the pair is **not** unique for event-based filters. See
[§3.8](#38-task-identity-uniqueness-and-filter-replicas).

Note also the `data` `longtext` column, which holds the serialised event ranges for search-based
filters and is `NULL` for ordinary stream-based ones.

### Environment

MySQL **8.0.23** (`stroomTestDb.yml:6`), no MariaDB support anywhere in the repo. jOOQ **3.20.10**
at runtime (`gradle/libs.versions.toml:15`); note `build.gradle:18` pins `jooqVer = 3.20.1`, but
that resolution strategy is scoped to the buildscript `classpath` configuration only, so it
governs the codegen/config-XML schema rather than the shipped library.
`SELECT ... FOR UPDATE SKIP LOCKED` is available and already used in
`DbClusterLock.getRecordLock()` (declared `:160`, `skipLocked()` at `:174`).

---

## 3. Worker claiming, as built

Each worker node finds and claims its own tasks. There is **no cache of task identities** — the
node queries for work and claims it in the same breath, so there is no staleness window and no
collision-management machinery.

The single state transition is **CREATED → PROCESSING**. `QUEUED` and `ASSIGNED` disappear from
the worker path entirely.

**Where it lives.** Each subsection below names the design decision; this is the code:

| Piece | Class | Section |
|---|---|---|
| Which filters may this node run? | `EligibleFilters` | §3.1 |
| Which of them have work? | `ProcessorTaskAvailability`, `ProcessorTaskDao.getTaskAvailability` | §3.2 |
| Take the work | `ProcessorTaskClaimer`, `ProcessorTaskDao.claimTasks` | §3.3 |
| Prove we are still alive | `ProcessorTaskHeartbeat`, `ProcessorTaskDao.renewTaskHeartbeats` | §3.4 |
| Recover a dead node's work | `ProcessorTaskReaper`, `reapDeadTasks`, `sweepQueuedTasks`, `countDeadTasks` | §3.4 |
| Don't re-ask a filter that gave nothing | `FilterFetchBackoff` (`recordEmptyClaim`/`isClaimDue`) | §3.2 |
| Entry point, mode switch | `DataProcessorTaskFactory` | §3.3 |
| What is the cluster doing? | `ProcessorClaimSystemInfo`, `ProcessorClaimStatus`, `ProcessorClaimStatusFactory` | §3.6 |
| A filter id means one body of work | `ProcessorFilterDaoImpl.restoreProcessorFilter`, `processor_filter.parent_filter_id` | §3.8 |

Configuration: `claimTasksOnWorker` (mode), `taskAvailabilityInterval` (summary refresh),
`taskLeaseTimeout` (heartbeat lease), `skipEmptyFilterFetchDuration` (claim backoff, shared with
the queue mode).

```
    ┌─────────────────────────────────────────────────────────┐
    │ ANY NODE, serialised by cluster lock (no master)         │
    │  • Processor Task Creator job → creates CREATED rows    │
    │  • Dead-task reaper (now heartbeat-based)               │
    │  • Retention / deletion jobs                            │
    └─────────────────────────────────────────────────────────┘
                              │ CREATED rows
                              ▼
    ┌─────────────────────────────────────────────────────────┐
    │ EVERY WORKER NODE, independently                        │
    │  1. eligible filters  = prioritised ∩ my profiles       │
    │  2. where's the work? = availability summary            │
    │  3. claim            = SKIP LOCKED, CREATED→PROCESSING  │
    │  4. heartbeat         = renew status_time_ms while busy │
    └─────────────────────────────────────────────────────────┘
```

**Nothing in the top box needs a master.** Creation already does not have one (§2) —
`ProcessorTaskCreatorImpl.exec()` (`:153`) takes a *cluster lock* (`:167`) and the job framework
has no master-only concept, so the job runs on every enabled node and the lock merely serialises
them. Reaping and
retention are the same shape of work — periodic, idempotent, needing only "not N at once" — so
they take the same treatment (§3.4). The cluster lock is the pattern to follow throughout;
`PhysicalDeleteExecutor` and `SQLStatisticAggregationManager` are further precedents.

The master survives in the processor module today only because the task *queue* lives in one
JVM's memory and therefore has to live in a *designated* JVM. Remove the queue and the reason
goes with it.

### 3.1 Eligibility (the actual win)

Each worker computes its own filter list locally from `prioritisedFilters.get()`:

- `filter.getProfileName() == null` → eligible (subject to `filter.maxProcessingTasks`).
- otherwise → `processorProfileCache.getProfile(thisNodeName, profileName)` (`:85`); drop the
  filter if the result is `ZERO`.

`ProcessorProfileCache` and `NodeGroupCache` are both DB-backed and already function on any
node, so this needs no new plumbing. It replaces `getMaxConcurrentTasks()`'s all-nodes sweep
outright.

A node that becomes ineligible simply stops looking. Nothing needs releasing, because nothing
was ever owned.

### 3.2 Finding work

The hard part. There are thousands of filters and most have no CREATED tasks at any moment, so
looping filters to find one with work is exactly what the master queue exists to amortise.

The resolution is a distinction that matters more than it looks:

> **"No queue" means "don't cache task identities". It does not mean "cache nothing".**
>
> - Cached **task ids** go stale into **collisions** — another node claimed it, your claim
>   fails, and you need cursors, wrap-around, low-water-marks and per-node offsets to cope.
> - Cached **filter availability** goes stale into **one wasted query** — you ask a filter that
>   turned out to be empty and move to the next. Self-correcting, no bookkeeping, and it can
>   never cause double-processing.

Note this cache is **node-local**. Each node caches availability for its own eligible filters, in
its own JVM, and answers only for itself. That is compatible with the no-master constraint; what
is not compatible is one node computing the answer *on behalf of others* (see the rejected
Option C in §5).

**What is built: the availability summary.** One query per node every few seconds, scoped to that node's eligible filter set:

```sql
SELECT fk_processor_filter_id, MIN(id)
FROM   processor_task
WHERE  fk_processor_filter_id IN (:eligible)
AND    status = :CREATED
GROUP BY fk_processor_filter_id
```

This is equality on the leading column of the **existing**
`processor_task_filter_id_status_id_meta_id` index, equality on the second, and `MIN` on the
third — one index descent per filter, one row per group, no row lookups, **no migration**.

Because the eligible list is supplied explicitly there is no reliance on optimiser skip-scan
heuristics. That it does not materialise and sort was the design's falsifiable condition; it is
asserted by `TestProcessorTaskQueryPlans` and passed (§8).

`MIN(id)` comes free and gives the oldest waiting task per filter — useful for ordering and for
diagnostics.

Per fetch:

1. Intersect the summary with the eligible set.
2. Sort by the existing priority comparator (see [§3.5](#35-fairness)).
3. Claim from the top filter; if it yields nothing, record an empty fetch and try the next.

Typical cost: one summary query per node per few seconds, plus one claim query per fetch.

**Reuse `FilterFetchBackoff`, don't invent a parallel negative cache.** The in-tree
`FilterFetchBackoff` already does exactly this job for the master fill: skip a filter that
recently yielded nothing, until either a timer expires or the filter gains work.

**How the re-arm signal actually behaves** (verified — an earlier draft of this document got it
wrong). `FilterFetchBackoff` is a `@Singleton` holding per-JVM in-memory state.
`recordTasksCreated(filter)` bumps a `creationVersion` that cancels the backoff, called from
`ProcessorTaskDaoImpl:586` after the creation transaction commits.

Task creation is **not** master-bound. `ProcessorTaskCreatorImpl.exec()` takes a *cluster lock*,
and the job framework has no master-only concept — the "Processor Task Creator" job runs on
every enabled node and the lock merely serialises them. The queue fill, by contrast, only runs
on the master, because it is reached through `assignTasks` which `DataProcessorTaskFactory`
routes to `getMasterNode()`.

So the re-arm only fires when the node that won the creation lock also happens to be the master —
roughly 1 in N for an N-node cluster. The comment at `ProcessorTaskDaoImpl:580` says as much
("If this node is also the master node then..."). Everywhere else the real guarantee is the
`skipEmptyFilterFetchDuration` timer (default 10s).

**Therefore the availability summary is not replacing a lost signal — it is replacing the
re-probing the timer forces.** With backoff alone, every dry filter is re-queried each time its
timer expires: at 2000 eligible filters and 64 nodes, on the order of 12,800 probe queries per
second cluster-wide, all returning nothing. One summary query per node per interval replaces all
of them. That justification holds regardless of whether the creation-version signal exists.

**The honest comparison, and what the Phase 1 `EXPLAIN` must actually test.** The 12,800/sec
figure above is the right comparison for *backoff-driven probing*, but it is not what the current
architecture does overall. Today the master performs filter discovery **once for the cluster**,
bounded further by `queueSize` breaking the loop early and by `FilterFetchBackoff` skipping dry
filters. The summary replaces O(filters) work done once with **O(eligible filters × nodes) work
done continuously**, whether or not there is anything to process.

That trade is clearly good when profiles partition filters cleanly across node groups, because
each node's eligible set is then a fraction of the total — the case this design was conceived for.
It is worst in the **degenerate no-profile case, where every filter is eligible on every node**,
which is what most existing deployments look like: 64 nodes × 2000 index descents every few
seconds, permanently, even on an idle cluster.

**The `EXPLAIN` therefore had to be run at realistic *no-profile* eligible counts, not the
well-partitioned ones.** It was, and it passed (§8).

### 3.3 Claiming

```sql
SELECT id, meta_id
FROM   processor_task
WHERE  fk_processor_filter_id = :filterId
AND    status = :CREATED
ORDER BY id
LIMIT  :n
FOR UPDATE SKIP LOCKED
```

then `UPDATE` those ids to `PROCESSING` with this node and `start_time_ms`, and commit.

`SKIP LOCKED` is what makes decentralisation cheap: concurrent nodes each get a **distinct** set
of rows on the first try. Not "one wins and the rest waste a round trip" — everybody gets work.
Contention stops being wasted effort and stops being something to tune.

FIFO is preserved: every node scans from the head in `id` order, so the oldest tasks genuinely
go first.

**No cursor is needed.** The cursor, wrap-around and low-water-mark machinery in earlier drafts
existed only to manage staleness in a cached candidate list. With scan and claim adjacent, the
window is microseconds and all of it is unnecessary.

`DataProcessorTaskHandler.exec()` skips its own `→PROCESSING` transition in this mode; the task
arrives already claimed.

#### Locked meta

Do **not** hold the row locks across `metaService.findLockedMeta()` — meta uses a separate
connection provider, so it is a separate transaction, and we would be holding InnoDB locks
across a cross-module call.

Instead: **claim first, then release the locked ones back to CREATED.** Tiny transaction,
self-correcting, and the locked minority costs one extra UPDATE. They get picked up naturally
on a later fetch once the stream finishes being written.

This is also why no cursor is needed for locked meta — we never advance past anything, so
nothing can be permanently skipped.

### 3.4 Heartbeat and reaper

**Mandatory, not optional.** Without it, decentralised nodes never contact the master, the
`lastNodeContactTime` retain set empties, and every long-running task gets disowned and
reprocessed.

**Heartbeat.** An in-memory registry of task ids this node holds in `PROCESSING`, populated at
claim and removed on COMPLETE/FAILED/abandon. A new scheduled job (~1m) issues:

```sql
UPDATE processor_task SET status_time_ms = :now
WHERE  id IN (:mine) AND status = :PROCESSING AND fk_processor_node_id = :me
```

chunked. It **must run on its own scheduler thread**, not the data-processing pool — a
saturated node that stops heart-beating gets its live work stolen.

**Reaper.** Replaces the node-contact heuristic:

```sql
UPDATE processor_task SET status = :CREATED, fk_processor_node_id = NULL
WHERE  status = :PROCESSING AND status_time_ms < :now - :timeout
```

A 10:1 ratio between heartbeat interval and reap timeout gives ample slack for a GC pause or DB
blip.

**Once the new mode is on, the reaper also sweeps `QUEUED` and `ASSIGNED`:**

```sql
UPDATE processor_task SET status = :CREATED, fk_processor_node_id = NULL
WHERE  status IN (:QUEUED, :ASSIGNED) AND status_time_ms < :now - :timeout
```

Nothing in the new mode ever writes either status, so any such row is residue: the master's
in-memory queue at the moment the mode flipped (drained CREATED→QUEUED but never assigned —
and everything that recovers QUEUED today is on §4's deletion list), or vestigial ASSIGNED rows
from older releases. Without this sweep they are stranded **forever** — the claim path wants
CREATED and the PROCESSING reap never looks at them — which means streams that silently never
get processed. Same index, same bounded-population argument (queue residue, not table size).
See §6 item 1 for why this is a standing duty rather than a one-time switchover migration, and
why it is safe even if it fires mid-roll.

**Index — correcting an earlier claim.** An earlier draft said
`processor_task_status_time_ms_status_idx` "supports this exactly". It does not; it is the worse
of the two candidates for this query shape:

| Index | Leading access | Rows scanned |
|---|---|---|
| `processor_task_status_time_ms_status_idx` `(status_time_ms, status)` | range on `status_time_ms < cutoff`, filter on status | **everything older than the cutoff, of any status** |
| `processor_task_status_create_time_ms_idx` `(status, create_time_ms)` | equality on `status = PROCESSING` | **only currently-PROCESSING rows** |

The first is bad precisely because of the heartbeat: every *live* task now has a recent
`status_time_ms`, so the sub-cutoff range consists almost entirely of terminal rows we do not
care about. `physicallyDeleteOldTasks` (`ProcessorTaskDaoImpl:1385`) only removes COMPLETE and
DELETED rows past a retention threshold, so that population is large and grows with retention.

The second bounds the scan by the number of PROCESSING rows cluster-wide, which is bounded by
total cluster thread count — thousands at most, regardless of table size. **The reap is therefore
a cheap, bounded query.** That fact is what makes a frequent standalone schedule affordable
(below). Confirm with `EXPLAIN` that the optimiser picks it; hint it if not.

(The old `retainOwnedTasks` (`:313`) genuinely does suit the `status_time_ms`-leading index — it
filters `STATUS_TIME_MS.lt(...)` across `ACTIVE_TASKS_STATUS_CONDITION`, a three-status `IN`, with
a node-set condition. Different query, different index. That is where the stale claim came from.)

#### Where the reaper runs

**Its own job, taking its own cluster lock** (e.g. `ProcessorTaskReaper`), scheduled independently
of task creation. *(Reversed 2026-08-04 — an earlier draft folded it into the creation run; see
below and §6 item 17.)*

Reaping is **recovery**, creation is **production**. They have different failure domains, want
different cadences, and there is no correctness relationship between them, so coupling them buys
nothing that a second cluster lock does not give more cheaply. The cluster-locked periodic job is
also the established pattern in this codebase — `PhysicalDeleteExecutor` and
`SQLStatisticAggregationManager` both work this way — which makes the fold-in the unusual choice
rather than the conservative one.

**A separate lock name is what makes this work**, and it is the whole difference from the variant
rejected earlier. A separate job sharing the *creation* lock would be starved exactly when the
cluster is busiest, because creation runs often and holds its lock for a long time. Two distinct
lock names never contend, so the reaper gets the guaranteed execution that the fold-in was chosen
to provide, without inheriting creation's schedule.

What this buys over folding in:

- **Cadence is explicit.** A `frequencySchedule` on the job, not an in-JVM "last reaped" stamp
  riding on however often creation happens to run. The reap interval becomes a property an
  operator can see and change.
- **The throttle stops being approximate.** The fold-in needed a per-node in-JVM gate, so the
  cluster-wide reap rate could reach N× nominal with N nodes (accepted there because the reap is
  cheap). A scheduled job needs no such gate — the schedule *is* the throttle, and the cluster lock
  serialises overlap.
- **No cadence coupling.** Disabling the "Processor Task Creator" job — a legitimate maintenance
  action when draining — no longer silently stops dead-task recovery. That was the fold-in's
  accepted cost and it is now simply gone.
- **Independently observable.** Its own job row, its own last-run time, its own failure signal.

Costs, and they are small: one more `cluster_lock` row, one more job in the UI, and no ordering
guarantee relative to creation. That last one is immaterial — reaped tasks return to **CREATED**,
which is the state the claiming path already looks for, so nothing needs to have "just run" for
them to be picked up. The only interaction is that a reap landing mid-creation-run slightly shifts
the CREATED counts that budget accounting reads, which is noise of the same order as normal task
completion.

Since the reaper is now the *only* recovery mechanism once nodes stop calling the master, its own
liveness matters more than before. Being a separate job makes it easier to monitor — and also
easier to forget, so keep the warning when it is disabled while PROCESSING rows exist.
**Built 2026-08-06, but detecting the effect rather than the configuration**: `countDeadTasks`
counts PROCESSING rows past the lease that the reaper has not taken, and `ProcessorClaimSystemInfo`
reports it with a warning naming the job. That needs no job-state introspection - which is what had
deferred it - and it also catches a reaper that is enabled but failing, which a check on the job's
enabled flag would not.

The current `disownDeadTasks()` (`ProcessorTaskQueueManagerImpl:607`) gates on
`thisNode.equals(getMasterNode())` only because it maintains `lastNodeContactTime` — in-JVM state
that is meaningless unless one designated node accumulates it. The heartbeat replaces that state
with a column, so the reap becomes a stateless idempotent sweep with no reason to care which node
it runs on.

**Use `tryLock`, not a blocking lock.** If another node is already reaping there is nothing to
wait for — the sweep is idempotent and the next scheduled run will pick up anything missed. This
is the same shape as `ProcessorTaskCreatorImpl:167`.

**No in-JVM throttle needed.** The job's schedule sets the cadence and the cluster lock prevents
concurrent sweeps, so the approximate per-node gate the fold-in required disappears along with the
reasoning about N× rates. Pick the interval on its own merits: the reap is bounded by cluster
PROCESSING count rather than table size given the index above, so it is cheap enough to run
frequently. Recovery latency is the lease timeout plus at most one reap interval, so a short
interval keeps recovery prompt — the current `disownDeadTasks` cadence (1m job, 10m threshold)
is the precedent — while running much more often than the heartbeat interval buys nothing
further.

**Still a strict improvement on today**, where reaping stops entirely whenever there is
no master — `getMasterNode()` throws `NullClusterStateException`, which `disownDeadTasks` swallows
at DEBUG — which is precisely the moment dead tasks are most likely to exist.

#### Rolling it out — two releases

The reap condition change ("stale AND node not in retain set" → "stale") is **not safe to ship
in the same release as the heartbeat**. During that release's deploy roll, not-yet-restarted
nodes hold live PROCESSING tasks and do not heartbeat — their `status_time_ms` was written once
at dispatch — so a new-code node running the "stale = reap" job would steal any of their tasks
older than the lease timeout, and long-running tasks are exactly the ones that run for hours.
Nor can the retain set ride along as a transitional guard: it is in-JVM master state fed by
`assignTasks` calls, and the new reaper is a cluster-locked job on whichever node wins the
lock, which does not have that state.

So Phase 0 ships across **two releases**:

1. **Heartbeat release.** Nodes heartbeat; the old master-gated `disownDeadTasks` keeps running
   unchanged. The combination is harmless — heartbeats only make tasks look *fresher*, so the
   retain set becomes progressively redundant but can never cause a false reap.
2. **Reaper release.** Every node now heartbeats, so "stale = reap" is safe on a rolling
   deploy. `disownDeadTasks`, the retain set and the master gate retire here.

This also gives the heartbeat a release of production soak before anything depends on it, which
§6 item 3 wanted anyway.

**DECIDED: reuse `status_time_ms`; no new column.** This avoids a migration and jOOQ regen, and
keeps `retainOwnedTasks` and `physicallyDeleteOldTasks` working unchanged (the latter only looks
at COMPLETE/DELETED). The accepted cost is that "Status Time" in the UI becomes "last heartbeat"
for PROCESSING rows — worth calling out in the release note, since it changes what an operator
reading that column is looking at.

#### The sharp edge

Reaping a task does not stop the original node processing it. A node that is alive but
DB-partitioned past the timeout means two nodes producing output for the same stream, and
duplicate output streams are not caught by the "data we seem to have created" guard in
`DataProcessorTaskHandler` (`:139`).

This risk exists today, but this design leans on it much harder. Two mitigations:

1. **On completion, do not force-write.** `changeTaskStatus` currently falls through to a
   reload-and-retry path when its version check fails (`ProcessorTaskDaoImpl:1088–1150`). In
   this mode, a failed version check means *we lost the lease* — log loudly and abandon the
   status write rather than stamping COMPLETE over a task another node now owns. The rule
   applies to **every** status transition, not just completion — including the old path's
   QUEUED→PROCESSING dispatch write — which is what makes the mixed-mode interactions in
   §6 item 1 degrade to an abandoned assignment rather than double processing.
2. **Self-fencing — DECIDED: terminate, in v1.** If a node's own heartbeat fails to renew for
   longer than the reap timeout, it terminates its in-flight tasks rather than merely logging.
   This is the only mechanism that actually prevents duplicate output streams; the "data we seem
   to have created" guard does not catch them. `taskContext.isTerminated()` plumbing already
   exists and is honoured by `DataProcessorTaskHandler` (`:164`).

   Termination must be driven by *failure to renew*, not by a reaper notification — a node that
   cannot reach the DB cannot be told anything. The heartbeat job tracks its own last successful
   renewal; once that exceeds the timeout, every task in the registry is terminated. A node in
   this state is by definition unable to write to the database for the whole timeout window, so
   the risk of killing genuine work over a transient blip is small at the ratios in use.

### 3.5 Fairness

Same-priority filters must keep getting a fair share. **The current mechanism is not
round-robin** — `ProcessorFilter.HIGHEST_PRIORITY_FIRST_COMPARATOR` (`:54`) ties equal
priorities on `tracker.getMinMetaId()`, then `getMinEventId()`, lowest first. A filter gets
tasks, its watermark advances, it falls behind its peers in the sort, and a peer goes first next
time. Alternation is emergent, not rotated.

Two properties come with that:

- **Granularity is ~10s**, not per-task — `PrioritisedFilters` is an `AsyncReference` on a 10s
  refresh (`:64`), so the order is frozen between refreshes.
- **Within a single fill there is no fairness between filters sharing a profile** —
  `ProfileQueueState.getRequiredTaskCount()` returns that profile's whole remaining budget, so
  the first filter of a profile can take all of it, and `assignTasks` drains queues in the same
  fixed order. #5691 bounded this *across* profiles by giving each its own budget; it did not
  address it *within* a profile.

Also note `minMetaId` is the *creation* watermark, advanced by the task creator, not by
completion. A filter that has created a huge backlog but processed none of it sorts *last* among
its peers. The existing fairness is a slightly leaky proxy.

**The availability summary preserves this exactly, at zero cost.** The comparator needs only `priority` and the
tracker, both already on `ProcessorFilter` and already available on every node via
`PrioritisedFilters`. Same comparator, same inputs, same granularity.

**Option B improves it.** Ordering by `(priority DESC, meta_id ASC)` gives the fairness
continuously and per-task: within a priority band the oldest data goes first regardless of which
filter owns it. Use `meta_id`, not `id` — task ids reflect creation order, so a filter added
later has higher task ids for the same data, whereas `meta_id` is comparable across filters by
construction.

**Optional upgrade, not taken — see §4.3:** the summary already returns `MIN(id)` per filter, a *live*
"oldest waiting task" signal rather than a 10s-stale creation watermark. Tie-breaking on that
would fix the leak noted above. It is a behaviour change, so only if wanted.

### 3.6 Observability — replacing the one central view

In queue mode `ProcessorTaskQueueManagerImpl.getSystemInfo()` gives **one place** to see what the
cluster is about to process: `filterQueues`, per-filter queue sizes on the master. When nodes claim
for themselves that view fragments across every node and there is nothing left to look at
centrally.

This was not a nice-to-have. The recorded production signature for the known "filter queue never
drains" hazard (accepted during gh#5679) *is* that sysinfo entry — so claiming would have deleted
the diagnostic an open issue depends on.

The replacement, built:

- Per-node: eligible filter count, summary size and age, claim attempts vs. rows won, locked-meta
  releases, in-flight task count, last successful heartbeat, and which mode the node is in
  (`ProcessorClaimStatus`, served by `GET /processorTask/v1/claimStatus/{nodeName}`).
- **Aggregated** across nodes, which is what an operator actually needs
  (`ProcessorClaimSystemInfo`, system info name `ProcessorTaskClaiming`).
- Plus `deadTaskCount`: PROCESSING rows past the lease that nothing has recovered, with a warning
  naming the reaper job. A disabled or failing reaper is otherwise silent, and this is the symptom
  rather than the configuration, so it catches both (§3.4).

**Aggregate at request time, on whichever node is asked — do not collect on the master.** The
earlier draft made this the master's job (pairing it with the now-rejected Option C), which would
have made the master the only place an operator could get a usable answer, and given it standing
in-memory state to hold. Instead: the node serving the request fans out to the enabled node set,
merges, and returns. Aggregation becomes a property of the *request*, not a role held by a node;
nothing is cached anywhere; any node answers identically; and the view degrades to partial rather
than absent when a node is unreachable.

This was **new plumbing** — `SystemInfoResource` is strictly per-node (no `NodeService` in
`stroom.core.sysinfo`, no fan-out path), so there was no existing mechanism to lean on. It was
built on the processor module's own node-targeted resource instead, which avoided a
`stroom-core` dependency purely to reach a path constant. A cluster-wide DB query over `processor_task`
grouped by node is a cheaper first cut for most of these numbers, but it cannot see in-JVM state
such as summary age or claim win rate, so it does not remove the need.

Claim win rate is worth exposing even though `SKIP LOCKED` should make it uninteresting — if it is
ever not ~1, something is wrong with an assumption in §3.3. For that reading to mean anything,
`emptyClaims` counts **only** losing the race: a claim that won but found every task's meta still
locked is a normal condition and is counted separately, or the metric would report contention that
was not there.

### 3.7 Fetch granularity — no debounce needed

**An earlier draft of this section was wrong and its conclusion is withdrawn.** It claimed a
saturated node asks for exactly 1 task per fetch, and proposed adding a **debounce** — wait a few
ms after a wake to accumulate free slots. Both the premise and the remedy are rejected.

**Do not add an artificial delay.** Waiting on the chance that more threads become free trades
latency on every fetch for batching that is only useful under load, which is the wrong way round:
the fetches that would be delayed are precisely the small, cheap, latency-sensitive ones.

**The existing fetcher already batches, without a timer.** `DistributedTaskFetcher` is
single-flight with coalescing:

- One loop thread fetches; there is never more than one fetch in flight.
- Completions during a fetch do not each trigger their own fetch — `signal()` (`:268`) just sets
  `needsTasks`.
- `needsTasks` is cleared *before* the fetch (`:141`), so those completions are not lost.
- When the fetch returns with `needsTasks` set, the loop immediately refetches, and
  `getTotalTaskLimit()` (`:283`) requests **every** slot freed in the meantime, in one batch.

So additional demand arriving during a fetch is deferred and merged into the *next* fetch, exactly
as if it had been debounced — but with the window bounded by real work rather than a constant.

**The batching window is the fetch's own in-flight time.** Batch size ≈ completion rate × fetch
latency. That gives the property a fixed debounce cannot:

| Condition | Behaviour |
|---|---|
| Idle / lightly loaded | Nothing in flight → fetch starts **immediately**, batch of 1, no added latency |
| Heavily loaded | Completions accumulate during each fetch → larger batches, fewer round trips |
| Database slow or contended | Fetch latency rises → batch size rises → **fetch rate falls automatically** |

The last row is the important one for this design, and the earlier draft missed it entirely. It is
a **negative feedback loop**: DB pressure lengthens fetches, which enlarges batches, which reduces
the query rate per node. The mechanism is self-governing under exactly the conditions that make
decentralised fetching risky — 64 nodes querying `processor_task` independently — and it needs no
tuning parameter to do it. This is group-commit behaviour, self-clocked like TCP ACK clocking
rather than timer-driven like Nagle.

**Consequence for this design: nothing to build.** The per-fetch overhead concern is real but
already mitigated by machinery that predates this work and improves rather than degrades as load
rises. Measure it in Phase 3 (§8) and only act if the numbers say otherwise. If they do, the
remedy to reach for is raising the *claim* batch size — asking for more than the free-slot count
and holding a small surplus — not delaying the fetch.

One thing to preserve when `DataProcessorTaskFactory` switches to self-claiming: a request for N
tasks may need more than one claim query if the top filter cannot supply N (§3.3). Keep that loop
inside the single in-flight fetch so the coalescing property above still holds.

### 3.8 Task identity: uniqueness and filter replicas

Raised 2026-08-04: should `processor_task` carry a uniqueness constraint, given that a filter
should never produce two tasks for the same meta? Investigated, and the answer changes the shape
of the proposal.

#### The premise is true for stream filters and false for event filters

For ordinary stream-based filters it holds. Creation is a strictly forward walk of `meta_id` from
`tracker.minMetaId`, and the code already knows the invariant is unenforced —
`ProcessorTaskDaoImpl:509-512`:

> `// Never move the tracker backwards. The max meta id can be lower than where the tracker has`
> `// already got to, e.g. feed dependencies can move the effective max backwards, and re-scanning`
> `// meta we have already created tasks for would create duplicate tasks as there is no unique`
> `// constraint on (filter, meta).`

For **event/search-based filters it is false by design**. `ProcessorTaskDaoImpl:490-496`:

```java
if (creationState.eventIdRange != null) {
    tracker.setMinMetaId(creationState.streamIdRange.getMax());      // note: NOT +1
    tracker.setMinEventId(creationState.eventIdRange.getMax() + 1);
}
```

The cursor deliberately stays *inside* the same meta and advances by event instead. Combined with
`eventRefs.isReachedLimit()` (`ProcessorTaskCreatorImpl:864`), which caps how much search-based
creation one run may do, a single meta legitimately receives **several tasks carrying disjoint
event ranges**, serialised into the `data` column (`:422-424`, `:449-450`).

So the real invariant is:

> **`(filter_id, meta_id, event_range)`** — which collapses to `(filter_id, meta_id)` only when
> `data IS NULL`.

#### Two things that break a naive constraint

**1. MySQL's NULL handling inverts the intent.** `UNIQUE (fk_processor_filter_id, meta_id,
data(n))` enforces *nothing at all* for stream tasks, because InnoDB treats NULLs as distinct —
unlimited `(1, 100, NULL)` rows remain legal. It would constrain only the event case, which is the
one that must not be constrained. MySQL has no partial/filtered indexes, so the workable
formulation is a stored generated column (e.g. `IFNULL(MD5(data), '')`) carried in the key. A raw
prefix index is worse than useless: a truncated prefix *falsely rejects* legitimately distinct
long ranges.

**2. Terminal rows are the harder blocker.** A unique index spans every row regardless of status,
and COMPLETE/DELETED tasks survive a retention window (`physicallyDeleteOldTasks:1385-1392`).
Logically deleting a filter sets its tasks to DELETED, so after a restore-with-reset there is
*guaranteed* to be a DELETED row for every meta already processed. The current reset
(`ProcessorFilterDaoImpl:361-392`) guards only against **active** tasks —
`STATUS NOT IN (COMPLETE, DELETED, FAILED)` — and says nothing about terminal ones. **Under a
unique constraint, restore-with-reset would fail on its first insert, every time.** Not a corner
case: it is the certain path.

**Therefore the replica model below is a precondition for the constraint, not a companion to it.**

**3. The failure mode is a poison pill.** Inserts are batched (`insertTasks:738-753`). A
duplicate-key error aborts the statement and the enclosing transaction, so a single unexpected
duplicate kills that filter's entire creation run, repeatedly, indefinitely. A bare unique index
converts a silent data anomaly into an availability incident. If it is ever added it wants
`ON DUPLICATE KEY UPDATE id = id` (or `INSERT IGNORE`) plus a monitored rejection counter, so the
constraint self-heals and reports rather than stalls.

#### Filter replicas instead of tracker resets

**Decision: reprocessing a filter's range should produce a new filter with a new id, not reset the
tracker of the existing one.**

The strongest argument is not uniqueness — it is that **filter id should be a stable key for a
fixed body of work**. A tracker reset mutates a filter's meaning in place while its id stays
constant, and that single fact is what makes uniqueness impossible *and* makes every id-keyed
cache silently wrong.

Supporting points:

- **It is already the established pattern.** `ProcessorFilterServiceImpl.reprocess():654-686`
  resets nothing — it creates a *new* filter with `reprocess = true` that queries by `PARENT_ID`.
  "Reprocessing means a new filter" is existing precedent; the tracker reset is the outlier, and
  it exists on exactly one path (`restore(docRef, resetTracker)`).
- **It matters directly for this design.** `FilterFetchBackoff`, `ProcessorProfileCache` and the
  per-node availability summary are all keyed by filter id. A reset changes what an id means
  without changing the id, so every node's cached state for it is stale with no invalidation
  signal. A new id invalidates naturally. With no master to coordinate a flush across 64 nodes,
  that is a structural advantage rather than a tidiness argument.
- **Audit survives.** `resetTracker` blanks counts, ranges and poll times. Worse, retained
  COMPLETE tasks then point at a filter whose tracker claims it never ran — an inconsistency that
  exists in the current code, not merely in theory.

Accepted costs, to be designed rather than waved away:

- **Filter-count inflation**, which rubs against the availability summary: it is
  O(eligible filters), so replicas inflate exactly the quantity being kept small.
  `logicallyDeleteOldProcessorFilters` reaps COMPLETE filters with no outstanding tasks after a
  threshold, so it is bounded — but the bound depends on that reaper keeping up.
- **Identity.** Filters carry a UUID. A replica needs a new one, or UUID uniqueness breaks — so
  external references to the old filter do not follow it. Wants an explicit lineage column
  (`parent_filter_id`) rather than an implicit relationship.
- **Settings fork.** The replica copies priority, profile, enabled state and limits, and diverges
  from the original thereafter.

#### Sequencing

These are separable and their risk profiles are opposite:

| | Replica model | Unique constraint |
|---|---|---|
| Cost | One service-layer change plus a lineage column | Generated column + full index build on the hottest table |
| Reversible | Yes | No |
| Failure mode | Behavioural, visible in the UI | Insert throws → creation stalls (see poison pill above) |
| Depends on | Nothing | The replica model landing first |

**Land the replica model on its own; treat the unique constraint as conditional.** *(Done — the
replica model landed 2026-08-06, see [§7](#7-phasing). The unique constraint remains unbuilt and
conditional: Phase 1's `EXPLAIN` passed, so Option B is not being taken, so neither is the index
build that was the only reason to take the constraint with it.)* There is a real
synergy — if Phase 1's `EXPLAIN` sends the design to Option B, a `processor_task` index build is
being paid for anyway, and folding the unique key into that single migration beats two — but it
also merges two independent go/no-go risks into one irreversible step. Only take it if B is taken.

---

## 4. Future work

Nothing in this section is built. It is here so that the options are recorded with their reasoning
rather than rediscovered, and so that the conditions that would make each one worth taking are
written down before anyone is under pressure to decide.

They are listed in the order they would most likely be wanted.

### 4.1 Deleting the master queue

**Condition: worker claiming has enough production time behind it that giving up the fallback is
an easy call.**

The master queue is retained purely as a way back. Deleting it was built — the whole of the list
below was removed, `getMasterNode()` went to zero callers in `stroom-processor`, and the suite
passed — and then deliberately reversed, because deleting it makes the first production run of
worker claiming irreversible: if claiming does not keep a large cluster fed, the remedy would be a
code revert and redeploy rather than a config change. That trade is only worth taking once
claiming has been seen to work at real scale.

When it is taken, this is what goes:

- The whole master fill path: `queueNewTasks`, `doQueueNewTasks`, `queueTasksForFilter`,
  `queueCreatedTasks`, `fillTaskQueueSync`, `fillTaskQueueAsync`, `queueMap`,
  `QueueProcessTasksState`, `ProgressMonitor` fill phases.
- `getMaxConcurrentTasks()` and its all-nodes profile sweep.
- `releaseTasksIfProfileInactive`, `releaseQueuedFilterTasks`, `releaseAll`, `clearTaskStore`,
  `releaseOldQueuedTasks`.
- `assignTasks`/`abandonTasks` and their REST endpoints. **No compat shim is needed**: the modes
  are a hard cutover, so there is never a node on the old release calling them (§4.1 was built
  once with shims, before that was settled).
- `TaskStatus.QUEUED` and `TaskStatus.ASSIGNED` from the live path — and with them the reaper's
  sweep and its mode gate, since nothing would ever write those statuses again.
- Config: `queueSize`, `assignTasks`, `fillTaskQueue`, `waitToQueueTasksDuration`, **and
  `claimTasksOnWorker` itself** — a toggle with one remaining position is worse than no toggle.
- `QueueProcessTasksState` / `ProfileQueueState` — per-profile *queue* budgets exist to stop one
  profile monopolising a shared central queue. With no central queue there is nothing to divide,
  so this goes. The per-profile *creation* budgets (#5691) stay — creation is still central.

`FilterFetchBackoff` is **kept** — it already serves both modes, having gained a claim-side re-arm
signal keyed on the summary (§3.2).

Also to do at that point: `ProcessorTaskQueueManagerImpl.startup()`'s release of tasks this node
still owns is the only part of its lifecycle worth keeping, and needs a home. It was built as a
standalone `ProcessorTaskStartup` and removed again with the reversal. There is deliberately **no
matching shutdown release**: what a node holds at shutdown is work that is running, whose own
terminal status writes finish it, and the reaper covers a hard kill.

#### The master-free checkpoint

`stroom-processor` had **four** `getMasterNode()` callers when this started. One is already gone;
the remaining three are all on the master queue path and go with it:

| Caller | State |
|---|---|
| `ProcessorTaskQueueManagerImpl` (`disownDeadTasks`) | **Gone** — replaced by the cluster-locked reaper (§3.4). The one part of the no-master goal already achieved, and it stands on its own: dead-task recovery no longer stops when there is no master |
| `DataProcessorTaskFactory` (fetch) | Only reached when `claimTasksOnWorker` is false; goes with `assignTasks` |
| `DataProcessorTaskFactory` (abandon) | As above, for the task release |
| `ProcessorTaskQueueManagerImpl` (`releaseOldQueuedTasks`) | Goes with the queue. Pure master-failover compensation |

**When the queue goes, the module has no notion of a master node.** Worth stating as an objective
rather than noting as a side effect, because it is cheap to verify — `rg getMasterNode
stroom-processor/` returns nothing — and it makes this work's contribution to the wider no-master
direction concrete. That check passed when Phase 4 was built, so the end state is known to be
reachable; it is only the deletion that is deferred.

`releaseOldQueuedTasks` deserves a mention on its own: it is pure compensating machinery for
master failover — tasks were QUEUED to a node that is no longer entitled to hold them, so they
must be handed back. Work that is claimed directly by the node that will run it never enters that
state, so the failover case it handles stops existing rather than needing a new home — with one
exception: the rows sitting QUEUED at the moment the mode is switched to claiming are exactly its
case, one last time. That residue is recovered by the reaper's QUEUED/ASSIGNED sweep (§3.4, §6
item 1), which is gated on the mode precisely so that it never touches a live queue.

---

### 4.2 Option B — denormalise `priority` and `profile_id` onto `processor_task`

**Condition: worker claiming does not keep the cluster fed, and the availability summary is shown
to be why.**

This is the fallback if §3.2 fails in production the way it did not fail in testing. It is a
different design, not a tuning of the current one: it **deletes** the "find work" step rather than
optimising it.

**The measurements say it is not needed** (§8). The concern it answers was that the summary turns
O(filters)-once into O(eligible × nodes)-continuous; measured, the summary's query *rate* is
independent of filter count, because it is bounded by the refresh interval rather than by how many
filters there are. And the query plan gate passed at no-profile filter counts, which is the worst
case. So B is recorded, not scheduled.

**It is also the only thing that would justify the `processor_task` uniqueness constraint**
(§3.8): the constraint needs an index build on that table, and B needs one anyway, so a single
migration would serve both. Alone, neither is worth it.

#### How it would work

Removes the filter search entirely. Index `(status, profile_id, priority, meta_id)` and query
directly, ordered and early-stopping.

**Why the columns help — the mechanism, since this is the part that gets skipped.** The root
problem is that `processor_task` knows nothing about *eligibility* or *priority*; both live on
`processor_filter`. So "what should I run next?" cannot be asked of the task table at all, and
every design above works around it in the same shape: compute eligible filters in memory, **ask
the DB which of them have CREATED work** (the expensive step), sort by the comparator, claim from
the top. Stamping the two values onto the task row at creation makes the question directly
answerable:

```sql
SELECT id, meta_id FROM processor_task
WHERE  status = :CREATED AND profile_id = :myProfile
ORDER BY priority DESC, meta_id ASC
LIMIT  :n
FOR UPDATE SKIP LOCKED
```

With `(status, profile_id, priority, meta_id)` that is equality on `status`, equality on
`profile_id`, and the index is then *already in* `priority` order and, within a priority, in
`meta_id` order. One descent, walk forward, stop after `n` rows. The two columns map exactly onto
the two things needed: **`profile_id` is eligibility** (filter to "work I may do" without joining
or enumerating) and **`priority` is ordering** (rows arrive pre-sorted, no filesort, no in-memory
merge across filters).

**The cost becomes O(rows returned), not O(filters)** — the query never enumerates filters at all,
so ten filters or ten thousand, mostly dry or mostly busy, it touches only the rows it hands back.
That is why B is A's fallback rather than a tuning of it: it does not optimise the "find work"
step, it **deletes** it. The index becomes the queue.

**Bonus: fairness stops being emergent.** Today equal priorities tie on `tracker.getMinMetaId()`
refreshed every ~10s (§3.5), which is the *creation* watermark — so a filter with a large
uncollected backlog sorts last among its peers. `ORDER BY priority DESC, meta_id ASC` gives
continuous per-task fairness: within a priority band, oldest data first regardless of owning
filter. (`meta_id`, not `id` — task ids follow creation order, so a filter added later carries
higher ids for the same data.)

The original objection — priority or profile changing after task creation — is answerable:

- **A stale value affects ordering, not correctness.** Nothing breaks if a task carries
  yesterday's priority for a few seconds. The sync can be lazy and best-effort.
- Update at the edit point in `ProcessorFilterServiceImpl`, *and* keep a reconciliation pass in
  the task creator as a self-healing backstop for any edit path that gets missed.
- Only `CREATED` rows need updating; anything already claimed is irrelevant. Bounded by that
  filter's backlog, and filter edits are rare.

**Wrinkle:** `profile_id IN (a, b, c)` combined with `ORDER BY priority DESC` defeats early
stopping — an IN-list on a middle index column makes MySQL merge ranges and sort the lot, which
is the full-scan behaviour we are trying to avoid. The fix is one query *per profile the node
belongs to* plus one for `profile_id IS NULL`, merged in the application. That is 2–4 queries,
bounded by profile count rather than filter count, which preserves the point — but it means the
claim can no longer be a single clean `FOR UPDATE SKIP LOCKED` statement. You would either hold
locks across the merge or fall back to optimistic batch claiming over a millisecond-wide window.

**Cost:** two new columns and a composite index on the highest-insert-rate table in the system.
#5684 is the precedent for how that needs benchmarking. Three costs usually get conflated here and
are worth separating, because the schema change sounds worse than it is and the index sounds
better:

- **Adding the columns is cheap.** MySQL 8.0 adds a trailing column `INSTANT` — metadata only.
- **The backfill is bounded.** Only `CREATED` rows need real values; terminal rows never match the
  query. So it is proportional to the CREATED backlog, not to the table.
- **The index build is the real cost**, and it is precisely the trade #5684 measured and refused.
  It is online in 8.0, but it is a full build across every row, and since most rows are
  COMPLETE/DELETED the index is largely dead weight — plus permanent write amplification on the
  highest-insert-rate table in the system, forever.

Summary: the query shape is excellent and the migration is more tractable than it first sounds —
but it buys that with an ongoing insert-path tax on `processor_task`, on a table where that exact
trade has already been rejected once (#5684). **The `EXPLAIN` it was contingent on has since been
run and passed (§8), so none of this is needed unless production disagrees with the measurements.**

### 4.3 Continuous fairness within a priority band

**Condition: someone reports same-priority filters getting an unfair share, or wants the
alternation to be tighter than ~10s.**

Described in §3.5 as the "optional upgrade". Equal priorities currently tie on the tracker's
`minMetaId`, which is the *creation* watermark refreshed every ~10s, so a filter with a large
uncollected backlog sorts last among its peers — a slightly leaky proxy. The availability summary
already returns `MIN(id)` per filter, which is a *live* "oldest waiting task" signal, so
tie-breaking on that instead would fix the leak for free.

Not taken because it is a **behaviour change** to fairness, not a bug fix: measured fairness is
already even (§8, three same-priority filters took 20/20/20 over twelve rounds). Take it if the
current behaviour is actually complained about.

### 4.4 A uniqueness constraint on `processor_task`

**Condition: only alongside §4.2, never alone.**

Fully worked through in §3.8, including the three traps that make a naive index wrong. The
prerequisite — filter replicas instead of tracker resets — **is built**, so the constraint is no
longer blocked. It is still not worth taking on its own: it needs an index build on the hottest
table in the system, which is exactly the trade #5684 measured and refused. If §4.2 is ever taken
it needs that index build anyway, and one migration can serve both.

## 5. Rejected and superseded alternatives

Recorded so they are not re-proposed.

**Cache task identities on the worker (the original sketch).** A per-node
`ConcurrentHashMap<ProcessorFilter, Queue<ExistingCreatedTask>>` of *unclaimed* candidates.
Superseded: every problem it created — claim collisions, per-filter cursors, wrap-on-exhaustion,
locked-meta low-water-marks, per-node scan offsets — was a consequence of the staleness window
it introduced. Scanning and claiming together removes the window and all of it.

**Per-node random scan offsets.** Proposed to stop N nodes colliding on the head of the CREATED
range. Rejected on the correct objection that it breaks oldest-first with no bound on how long a
region can be skipped, making "when will this stream be processed" unanswerable. Superseded
entirely by `SKIP LOCKED`, which removes collision by construction while keeping FIFO.

**Claim into `QUEUED` owned by self, then `QUEUED`→`PROCESSING` at dispatch.** Avoids
contention and drains the CREATED backlog, at the cost of keeping two transitions. Moot under
the no-cache design — there is nothing to hold between the two states.

**Claim-at-fill straight to `PROCESSING` into a local queue.** Rejected: tasks sitting in a
queue would count against profile thread budgets while not running, breaking concurrency
accounting, and would need the dead-task reaper for merely-queued work.

**Blind fill-for-every-node / superset-of-group-nodes fill** (from gh#5679). Rejected then,
still rejected — both walk all filters on every fill.

**Master serves the availability summary over REST, cached (former Option C).** Proposed as a
fallback if Option A's query did not perform: the master computes the summary once and all
workers read it, degrading to direct probing if unreachable. Rejected on the standing no-master
constraint — it is a master-node cache, and the fact that it sits off the correctness path as an
"optimisation oracle" does not change that. It also fails on its own terms in the way these
things usually do: the fallback path (every node probing its own eligible filters) is the
behaviour C exists to avoid, so a cluster under stress loses the optimisation exactly when it
needs it, and in the meantime the master carries state whose staleness every operator has to
reason about. If A does not perform, the answer is B (§3.2) or keeping the central queue —
which at least is an honest central queue rather than a cache pretending not to be one.

---

## 6. Decisions and open questions

### Settled

1. **Single cluster-wide setting, no mixed mode.** The master's fill goes off wholesale, which
   removes the hazard of a master draining CREATED→QUEUED where self-fetching nodes cannot see
   it.

   Note that a rolling config change **necessarily passes through mixed mode**: while the roll is
   in progress, old nodes still call `assignTasks`, the master still fills and drains
   CREATED→QUEUED, and switched nodes see less available work. This is **safe, just suboptimal** —
   both paths claim atomically, so there is no double-processing window, only degraded throughput
   until the roll completes. "No mixed mode" is a statement about steady state and what is
   supported, not about correctness. Say so in the release note so nobody panics at the numbers
   mid-roll.

   The **end** of the roll needs one more thing said: whatever the master had drained
   CREATED→QUEUED but not yet assigned when the last old-mode node switched is **stranded** —
   the new claim path only looks for CREATED, the PROCESSING reap never touches QUEUED, and
   everything that recovers QUEUED today (`releaseOldQueuedTasks`, the clean-shutdown release
   path) is deleted in §4. Recovery is a **standing reaper duty, not a one-time migration**
   (§3.4): in the new mode the reaper also returns QUEUED/ASSIGNED rows stale past the lease
   timeout to CREATED. Standing beats one-shot because it needs no "has the roll finished?"
   detection, is idempotent, and self-heals stragglers such as a node briefly restarted with old
   config. It is also safe if it fires mid-roll: a legitimately parked QUEUED task that gets
   swept and later assigned by the old master fails its version check at dispatch, and from
   Phase 0 a failed version check on **any** transition means abandon, not force-write (§3.4) —
   worst case is a wasted assignment, the same outcome as a lost lease. A sweep/re-queue
   ping-pong during a long roll is possible but bounded and harmless. In steady state the sweep
   is one indexed probe that finds nothing.

2. **Cluster-wide limits use a short-TTL count cache.** Cache the per-filter cluster PROCESSING
   count for ~1s, and only for filters that actually have a limit
   (`isProcessingTaskCountBounded()`, or a profile with `maxClusterThreads < MAX_VALUE`).
   The cached figure is **carried forward by the change in this node's heartbeat registry since
   the query**, so a count taken before a claim cannot be spent again by the next claim inside the
   window, and capacity freed by this node's own tasks completing is seen immediately. A delta
   rather than an absolute local count, because the registry only knows tasks *this JVM* claimed —
   rows left PROCESSING under this node's name by a previous run are real cluster load until the
   reaper takes them, and the query counts them.
   Overshoot is therefore bounded by (**other** nodes × batch) within the TTL, which is accepted,
   consistent with the standing decision that all concurrency limits deliberately over-provision.

   `maxNodeThreads` needs **no query at all** — the node knows its own PROCESSING count exactly
   from the heartbeat registry. Only genuinely cluster-wide limits touch the database.

3. **Heartbeat ships first, as its own change** (see §7 Phase 0). It is a strict improvement to
   the current architecture on its own, and everything here rests on it being correct.
   *Refined 2026-08-05:* "first" means a **full release ahead** — the heartbeat must be live on
   every node before the reap condition changes (§3.4 two-release rollout, item 18).

4. **Heartbeat writes `status_time_ms`; no new column** (§3.4).

5. **Self-fencing terminates in-flight tasks in v1**, driven by failure to renew (§3.4).

### Open

These are the questions that were open when the design was written. Most were closed by building
it; what is left is marked. Items 6 and 7 are pre-existing behaviours of task creation and
queueing, not of this work.

6. **Budget charging of pre-existing stock — earlier "this is the starvation" claim WITHDRAWN
   (2026-08-04). It is deliberate, and it is symmetric across creation and queueing.**

   Both sides charge a filter's *pre-existing* stock against the budget shared by its profile,
   and both then subtract that stock from what they will add:

   | | Charge to shared budget | Subtract from what it adds |
   |---|---|---|
   | Creation | `budgetUsed.add(currentCreatedTasks)` (`ProcessorTaskCreatorImpl:445`) | `maxTasks = remaining - currentCreatedTasks` (`:447`) |
   | Queueing | `addTotalQueuedTasks(initialQueueSize + totalAddedTasks)` (`ProcessorTaskQueueManagerImpl:1089`/`1091`) | `tasksToAdd = getRequiredTaskCount() - initialQueueSize` (`:980`) |

   Two independent implementations of the same rule is a design, not an oversight. The model is
   **"the budget is how much work this profile should have available"**, not "how many this run
   may create" — under which charging existing stock is correct, because a profile that already
   has its fill waiting does not need more created for any of its filters. Both classes' javadoc
   describes the budget in exactly those terms.

   **A follow-up "the creation side is missing the queueing side's cap" claim was also wrong and
   is withdrawn.** The queueing cap `Math.min(maxConcurrentTasks, initialQueueSize +
   totalAddedTasks)` (`:1084–1089`) is not a general guard against one filter exhausting a shared
   budget. `maxConcurrentTasks` is a limit on **concurrent processing**, and the queue is a buffer
   that exists to feed it — so queueing 500 tasks for a filter that can only run 10 at once does
   not represent 500 units of available work, and counting it as such would stop other filters
   being queued for no gain. That is what the cap corrects.

   `currentCreatedTasks` is a different quantity: a **durable backlog** of work that is genuinely
   pending and will all eventually be processed, however slowly its concurrency limit lets it run.
   Capping that charge at a concurrency limit would make `budgetUsed` under-count, so the budget
   would rarely trip and creation would keep adding to a filter that already has a large backlog.
   The two sides differ because they are measuring different things, and each measures the right
   one.

   **Net: nothing to fix here.** Item 6 is closed as "working as designed" apart from the
   observation below.

   Relevance to this design: the charge grows under decentralisation, because tasks stay CREATED
   until claimed instead of being drained to QUEUED. That raises the stock each visited filter
   reports and so consumes more of the shared budget per filter — a behaviour change to watch in
   Phase 3, not a defect in the current code.

   (The gh#5679-era note that the exponential-backoff creation idea was unbuilt is out of date —
   #5690 built a linear backoff.)

7. **Fairness within a profile** (§3.5). Neither #5691 nor this design addresses the first
   filter of a profile taking that profile's whole budget. Raised here because the availability
   summary makes it visible for the first time — `MIN(id)` per filter is exactly the signal a
   within-profile fairness fix would need. Note this is the same underlying effect as item 6:
   whichever filter is visited first charges its stock and can exhaust the shared budget, so the
   outcome depends on visit order. Item 6 is about *how much* is charged; this is about *who gets
   charged first*.

### Settled (round 2)

8. **CLOSED by building it, and the answer came out as *one* property, not the pair this
   expected.** The intent was that heartbeat timing should not overload `disownDeadTasksAfter`,
   whose semantics would shift under it. As built, `disownDeadTasksAfter` is gone and replaced by
   `taskLeaseTimeout`, which names what it actually is. There is deliberately **no heartbeat
   interval property**: the "Processor Task Heartbeat" job's schedule is the interval, it is
   already visible and editable where operators look for such things, and a second property that
   had to be kept consistent with it would be a way to get it wrong (§3.4).

9. ~~**Fetcher debounce is built in Phase 2**, not deferred.~~ **Reversed 2026-08-04 — no
   debounce, nothing to build** (§3.7). The premise was wrong: `DistributedTaskFetcher` does not
   ask for one task at a time, and it already coalesces demand arriving during an in-flight fetch
   into the next one. An artificial delay would add latency to precisely the cheap, idle-path
   fetches that least need batching, and the existing self-clocked window is strictly better —
   it widens under DB pressure on its own.

10. **CLOSED — built and run, see §8.** **Validation is a local scaled-down many-node harness** with short tasks (§8), not the
    reporter's cluster. Repeatable and safe to break; the scaling claim stays unproven against
    real filter counts, which is an accepted limitation of the first pass.

11. **CLOSED — A holds; B is not being taken** (§4.2). **Option A vs Option B** (§3.2) — started with A; the measurements said keep it.

### Settled (round 3)

12. **No master node, and specifically no master-node cache.** A standing constraint, not a
    preference to be traded against performance. Consequences already applied throughout:
    Option C rejected (§3.2, §5); the reaper moved to a cluster lock (§3.4); aggregated sysinfo
    computed at request time rather than collected centrally (§3.6); the four `getMasterNode()`
    callers tracked to zero (§4).

    The general rule where a "one node must do this" need remains: **cluster lock, not master.**
    A master is a designated node with standing in-memory state; a cluster lock is mutual
    exclusion with none. Every remaining periodic job in this design fits the latter.

    The `ClusterLockService` javadoc used to claim "tryLock and release lock utilises the cluster
    but the master node must be up to work", which was stale in every particular —
    `ClusterLockServiceImpl` delegates entirely to `DbClusterLock`, a `SELECT ... FOR UPDATE` on
    the `cluster_lock` table with no master and no inter-node communication, and the interface has
    had no "release lock" operation for some time. **Fixed** (2026-08-04): the javadoc now states
    that no master is involved and that a cluster lock is the preferred alternative to gating work
    on master status, which is the point this design keeps needing to make.

    Accepted cost: the availability summary has no cheap fallback (§3.2). Judged worth paying, because the
    fallback was the kind of thing that becomes permanent.

13. ~~**The reaper folds into the task creation run**, throttled to run on only some runs, rather
    than becoming its own job with its own lock (§3.4).~~ **Reversed 2026-08-04 — see item 17.**

### Settled (round 4)

14. **`(filter_id, meta_id)` is NOT unique, and the exception is deliberate** (§3.8). Event-based
    filters legitimately produce several tasks for one meta, each carrying a disjoint event range
    in the `data` column, because the tracker advances by event *within* a meta
    (`ProcessorTaskDaoImpl:490-496`) and search-based creation is capped per run
    (`ProcessorTaskCreatorImpl:864`). The true invariant is
    `(filter_id, meta_id, event_range)`. Any future constraint must account for this, and must not
    rely on a plain multi-column unique index, because InnoDB treats NULLs as distinct and would
    therefore enforce nothing for exactly the stream-based case that needs it.

15. **Reprocessing produces a filter replica with a new id; tracker resets are retired** (§3.8).
    The decisive argument is that **filter id should be a stable key for a fixed body of work** —
    a reset changes what an id means without changing the id, which is what defeats both a
    uniqueness constraint and every id-keyed cache in this design (`FilterFetchBackoff`,
    `ProcessorProfileCache`, the availability summary). `reprocess()` already works this way
    (`ProcessorFilterServiceImpl:654-686`); the reset on `restore(docRef, resetTracker)` is the
    outlier. Accepted costs: filter-count inflation against the summary's O(eligible filters), a new
    UUID plus an explicit lineage column, and settings forking from the original.

16. **A uniqueness constraint on `processor_task` is deferred and conditional** (§3.8). It is
    blocked outright until item 15 lands, because retained COMPLETE/DELETED rows survive a
    retention window and restore-with-reset would fail its first insert every time. Even then it
    is only worth taking **if Phase 1 sends the design to Option B**, so that one index build on
    the hot table pays for both. If taken, it needs `ON DUPLICATE KEY UPDATE id = id` and a
    monitored rejection counter — a bare unique index turns a silent anomaly into a poison pill
    that stalls a filter's batched creation run indefinitely.

17. **The reaper is a separate job with its own cluster lock** (§3.4) — **reversing item 13**.
    Reaping is recovery and creation is production; they have different failure domains and want
    different cadences, and no correctness relationship forces them together. **The distinct lock
    name is the point** — the variant rejected in round 3 was a separate job sharing the
    *creation* lock, which would starve under `tryLock` exactly when the cluster is busiest. Two
    lock names never contend, so the reaper keeps guaranteed execution without inheriting
    creation's schedule. This also retires both accepted costs of the fold-in: the approximate
    per-node throttle (the job's schedule is now the throttle) and the cadence coupling whereby
    disabling the creator job silently stopped dead-task recovery. Matches the established
    cluster-locked job pattern (`PhysicalDeleteExecutor`, `SQLStatisticAggregationManager`).
    Costs: one more `cluster_lock` row and one more job in the UI, plus no ordering guarantee
    against creation — immaterial, because reaped tasks return to CREATED, which is the state the
    claiming path already looks for.

### Settled (round 5)

18. **Phase 0 ships across two releases** (§3.4): heartbeat first; the "stale = reap" condition
    and the retirement of `disownDeadTasks` the release after. A single-release roll would let
    the new reaper steal live long-running tasks from not-yet-upgraded nodes, and the retain set
    cannot serve as a transitional guard because it is master in-JVM state that the
    cluster-locked reaper does not have.

19. **Stranded `QUEUED`/`ASSIGNED` recovery is a standing reaper duty in the new mode, not a
    switchover migration** (§3.4, §6 item 1). Nothing in the new mode writes either status, so a
    stale row in them is residue by definition. A standing sweep back to CREATED is idempotent,
    needs no roll-completion detection, and is made mid-roll-safe by the no-force-write rule,
    which is explicitly generalised from completion writes to **every** status transition
    (§3.4). Gated on the mode flag, so it ships in Phase 2, not Phase 0.

---

## 7. Implementation history

How it was built, kept because the sequencing decisions are the reusable part — particularly the
two-release rollout, which still applies to anyone deploying this for the first time, and the
Phase 4 reversal, which is the one place the built system deliberately departs from the design.

- **Phase 0 — ships independently, ahead of everything else, as two releases** (§3.4): first the
  heartbeat registry and heartbeat job (own scheduler thread); the release after, the
  heartbeat-based reaper, self-fencing on failure to renew, and the no-force-write change on lost
  lease (every status transition, §3.4). A single-release roll would let the new reaper steal
  live tasks from not-yet-upgraded, non-heartbeating nodes. Lands against the *current*
  architecture, where it already
  improves on the node-contact heuristic, and gets soak time before anything depends on it.
  Note the reaper's condition changes from "stale AND node not in retain set" to "stale", so the
  retain-set logic in `disownDeadTasks` is retired at this point, not later — and with it the
  master gate at `ProcessorTaskQueueManagerImpl:611`, as the reap becomes its own cluster-locked,
  master-free job (§3.4). **One of the four
  `getMasterNode()` callers therefore goes in Phase 0**, independently of everything downstream;
  that is a self-contained win even if the rest of this design is never built. `EXPLAIN` the reap
  query here too — it must use `processor_task_status_create_time_ms_idx`, not the
  `status_time_ms`-leading index (§3.4); the frequent standalone schedule depends on the reap
  staying cheap.
- **Phase 0b — filter replicas replace tracker resets** (§3.8). **Implemented 2026-08-06.**
  Independent of everything else
  here, cheap, reversible, and useful on its own; it also removes the id-stability problem that
  otherwise undermines every id-keyed cache in Phases 1–2. Sequenced before any consideration of a
  uniqueness constraint, which is blocked on it. As built: `parent_filter_id` added by
  `V07_14_00_004` as a **plain nullable column, deliberately not a foreign key** — filters are
  physically deleted once their tasks have gone, and a constraint would keep every superseded
  ancestor alive for as long as any descendant existed, so lineage is allowed to dangle.
  `ProcessorFilterDaoImpl.restoreProcessorFilter` now creates the replica in one transaction and
  the deleted filter **gives up its uuid** to it (rotated to a fresh one) — the uuid is unique and
  it is the live filter that the doc ref must resolve to, which matters because the sole caller is
  import (`ProcessorFilterImportExportHandlerImpl`), and it re-imports by uuid. `resetTracker` and
  its active-task guard are gone: with a replica there is no reason to refuse a filter that still
  has tasks, because those tasks stay with the filter that owns them.
- **Phase 1** — per-node eligible-filter computation and the availability summary.
  **Implemented 2026-08-06** as `EligibleFilters`, `ProcessorTaskDao.getTaskAvailability` and
  `ProcessorTaskAvailability`; read-only, nothing calls them yet, wiring is Phase 2.
  Testable in isolation, harmless if unused. **`EXPLAIN` the summary query against a
  realistic filter count here** — it is the design's falsifiable condition (§3.2) and must not
  wait for Phase 3. **Run it at *no-profile* eligible counts** (every filter eligible on every
  node), which is the degenerate case most existing deployments are in; passing only the
  well-partitioned case does not clear the gate. **Gate passed 2026-08-05**
  (`TestProcessorTaskQueryPlans`). Two decisions taken while building it: the summary is cached
  node-locally for `taskAvailabilityInterval` (PT5S) and intersected with the **current** eligible
  set on each call rather than the set it was taken over, so a newly eligible filter costs at most
  one interval of latency and never a wrong answer; and a filter's own `maxProcessingTasks` is
  **not** eligibility — it bounds concurrency, so it belongs to claiming, and treating it as
  eligibility would hide a busy filter's remaining work.
- **Phase 2** — **Implemented 2026-08-06.** `SKIP LOCKED` claim DAO method +
  `DataProcessorTaskFactory` switch behind the
  flag. Move `FilterFetchBackoff` to the worker with the summary as its re-arm signal. Per-node
  sysinfo **and the request-time aggregation fan-out** (§3.6) — new plumbing, not free, and needed
  before Phase 3 can be interpreted. **No fetcher debounce** (§3.7); keep the multi-filter claim
  loop inside the single in-flight fetch so the existing coalescing still applies. The reaper's
  QUEUED/ASSIGNED sweep (§3.4) is gated on the same flag and ships here — it is what un-strands
  the master's queue residue after the switchover roll (§6 item 1).

  As built, with the decisions taken along the way:
    - Flag is `stroom.processor.claimTasksOnWorker`, default **false**. `DataProcessorTaskFactory`
      splits into `claim()` and `assign()`; the claim path touches neither `TargetNodeSetFactory`
      nor `ProcessorTaskResource`, so **there is nothing for it to fail on when there is no
      master**. `abandon()` releases locally in the new mode rather than asking a master to.
    - `DataProcessorTaskHandler.exec(task, alreadyClaimed)` skips its own →PROCESSING write for a
      claimed task. `RunnableFactory.create` gained the same flag.
    - **Heartbeat registration moved forward to claim time**, with a null stroom task id that the
      handler fills in when processing starts. A claimed task is owned from the moment it is
      claimed, so from that moment a gap in heartbeats has to mean this node is dead; registering
      only at execution would leave a claimed-but-not-yet-started task looking reapable.
    - The heartbeat registry now carries the **filter id** per task, which makes a per-node
      concurrency limit exact and free (§6 item 2's "no query at all"). Only genuinely
      cluster-wide limits query, and only for filters that have one, cached 1s
      (`ProcessorTaskClaimer.CLUSTER_COUNT_CACHE_MS`, deliberately not a property) and carried
      forward by the registry delta since the query, so the window over-provisions only for what
      *other* nodes have done.
    - **Locked meta: claim first, release after** as §3.3 requires, outside the claim transaction.
    - `FilterFetchBackoff` gained `recordEmptyClaim`/`isClaimDue` keyed on the summary's
      `MIN(id)` **availability marker** — a distinct pair rather than an overload, because the
      existing `recordEmptyFetch(filter, duration, long)` has the same erasure. An empty *claim*
      means another node won the race for work we were told was there, so what re-arms it is the
      summary reporting *different* waiting work, not just the timer.
    - Sysinfo: new `ProcessorClaimStatus` DTO + `GET /processorTask/v1/claimStatus/{nodeName}`,
      with `ProcessorClaimSystemInfo` fanning out over `getEnabledNodesByPriority()`. Reusing the
      module's existing node-targeted resource avoided depending on `stroom-core` from
      `stroom-processor-impl` just to reach `SystemInfoResource`'s path. Reading it is
      **side-effect free** — the summary is reported as it stands, never refreshed, so looking at
      the diagnostic cannot perturb what it measures.
- **Phase 3** — **Measured 2026-08-06, results in §8.** Tune. Only then consider Option B — and
  only alongside B, if at
  all, the `processor_task` uniqueness constraint (§3.8), so that a single index build on the hot
  table serves both. **The measurements say do not take Option B**: Option A's dispatch tail is
  flat in filter count where the queue path's is not, and the summary's query rate turns out to be
  independent of filter count, which was the one number B existed to fix. B and the uniqueness
  constraint stay on the shelf.
- **Phase 4 — REVISED 2026-08-06, revised again 2026-09-02. Off by default; §4 NOT deleted.**

  Phase 4 was built as specified (queue path deleted, `getMasterNode` eliminated) and then
  **deliberately reversed** at the user's direction. The reason is deployment risk, and it is worth
  recording because it overrides a design decision rather than refining one:

  > Deleting the master queue makes the first production run of worker claiming irreversible. If
  > claiming does not keep a large cluster fed, the only remedy is a code revert and redeploy.
  > Keeping both paths behind a property turns that into a config change.

  The 2026-09-02 revision took the same argument one step further, again at the user's direction:
  a fallback nobody has ever exercised is not a fallback. If claiming is the default, the first
  production run of it is still the first time anyone finds out, and the *queue* becomes the
  untried path. So the mode ships **off**, as an experiment to be turned on deliberately, and the
  default upgrade path is the behaviour that already works.

  So the shipped state is:
    - `stroom.processor.claimTasksOnWorker` **defaults to false** — a new or upgraded deployment
      runs the master queue, exactly as before. Worker claiming is **experimental** and opted into.
    - The claiming path is retained in full and selected by setting it to **true**.
    - **Being off by default must not mean being untested.** Everything in §3 is covered by tests
      that set the mode explicitly rather than inheriting it: the DAO-level mechanics in
      `TestProcessorTaskClaimer` / `TestProcessorTaskClaiming` / `TestProcessorTaskAvailability` /
      `TestProcessorTaskHeartbeat` / `TestProcessorTaskReaper` / `TestProcessorTaskClaimFairness`,
      the mode switch itself in `TestDataProcessorTaskFactory` (both branches, and an assertion
      that the default is off), and the whole route end to end against a real database in
      `TestWorkerTaskClaiming`, which also runs the same scenario through both modes and asserts
      they hand out the same tasks. Without that, an unrelated change could break claiming and
      nothing would say so until someone turned it on in anger.
    - **Hard cutover only.** The property must be identical on every node, and changing it means
      stopping the whole cluster, changing it everywhere, and starting again. A mixed cluster is
      not supported: the two modes use different task states and neither can see the other's
      in-flight work.
    - **No mixed-mode accommodations anywhere.** The compat shims Phase 4 added to the REST
      endpoints are gone; `assignTasks`/`abandonTasks` are the real implementations again, used by
      the queue path and unused by the claiming path.
    - Switching in either direction loses no work. Tasks the abandoned mode left behind go back to
      CREATED: a node's own leftovers by the queue manager's startup release, QUEUED/ASSIGNED
      residue by the reaper's sweep (still gated on the mode, because in queue mode those rows are
      the live queue and sweeping them would destroy it). The cost of a switch is that tasks in
      flight when the cluster stopped wait out `taskLeaseTimeout`.

  **What this leaves undone:** §4's deletion list and the master-free checkpoint, and now also
  making claiming the default. They remain correct as a description of where this ends up; they
  are simply deferred until worker claiming has enough trial time behind it. The order is now:
  turn it on in a trial deployment, then default it on, then delete the queue. When that last
  step comes, Phase 4 is the change already proven to work — it built and passed cleanly before
  it was reversed.

## 8. Testing and measurement

**DECIDED: a local scaled-down many-node harness**, not the reporter's cluster. Repeatable, safe
to break, full control. Accepted limitation: it will not reproduce real data volumes or real
filter counts, so the scaling claim stays unproven against production until later.

**The comparison that decides it:** current master-queue path vs. no-queue `SKIP LOCKED`,
measuring cluster-wide tasks/sec, statements/sec against `processor_task`, and p99 dispatch
latency.

Run it with **short tasks and many filters** — short tasks maximise dispatch rate and therefore
per-fetch overhead; a high filter count is what stresses the availability summary, which is the
part of this design most likely to fail. Both need to be dialled up together, because the local
harness cannot get there by data volume.

Record **observed batch size per fetch**, not just tasks/sec. It is the direct evidence for §3.7's
claim that the fetcher self-clocks: batch size should rise with load and with induced DB latency.
If it stays pinned at 1 under heavy load, the coalescing is not working as read and the per-fetch
overhead question reopens.

Needed tests:

- Concurrent claim: M threads/nodes claiming against a shared CREATED pool → each task claimed
  exactly once, all eventually claimed, no lost updates.
- `EXPLAIN` on the summary query with a realistic filter count — must not materialise and sort.
- Heartbeat + reaper with an injectable clock. There is no injectable clock in `stroom-util`, so
  factor "now" as a parameter at write time (same approach as #5683).
- `EXPLAIN` on the reap query — must use `processor_task_status_create_time_ms_idx` (§3.4). Test
  it against a table with a large backlog of retained COMPLETE rows, not a clean one, since that
  is the population that makes the wrong index bad.
- Fairness: same-priority filters get comparable throughput over time.
- Lease loss: a reaped task whose original node later completes must not stamp COMPLETE.
- Switchover residue: rows left QUEUED/ASSIGNED when the mode flips are returned to CREATED by
  the sweep and processed; an old-mode assignment of an already-swept task is abandoned at the
  version check, not forced (§6 item 1).

### Results — measured 2026-08-06

Harness: `TestProcessorTaskDispatchBenchmark` (`-DrunProcessorBenchmark=true`). Both paths driven
over the **same seeded data in the same MySQL**, 8 "nodes" as threads, batch 20, tasks that do no
work so the numbers are dispatch and nothing else. Statements counted at the DAO.

> Both paths are retained (see Phase 4 above), so this comparison remains reproducible with
> `-DrunProcessorBenchmark=true`.

| | 200 filters × 50 tasks | | 1000 filters × 10 tasks | |
|---|---|---|---|---|
| | **claiming** | queueing | **claiming** | queueing |
| tasks/sec | **305** | 282 | **295** | 215 |
| statements per task | 1.21 | 1.12 | 1.83 | 1.65 |
| fetch p50 (ms) | 36 | **4** | 72 | **13** |
| fetch p99 (ms) | **182** | 1,672 | **204** | 13,276 |
| summary queries | 56 | n/a | 56 | n/a |

**1. The tail is the finding, not the throughput.** Median dispatch latency is *worse* under
claiming (72ms vs 13ms at 1000 filters) — the claim does real database work inline where the
assign mostly drains an in-memory queue. But the p99 is 204ms against 13.3 **seconds**, because a
worker on the old path waits on the master's fill, and that fill walks every filter. Claiming's p99
barely moves as filter count rises (182 → 204ms); queueing's goes up eightfold (1.7s → 13.3s). A
node that waits 13s for work is 13s of idle processing capacity, which is the shape of the
complaint in gh#5679.

**2. The "O(eligible × nodes) continuous" cost did not materialise, because the summary is
cached.** `getTaskAvailability` ran **56 times in both configurations** — five times the filters,
identical query count — since the rate is bounded by (nodes × elapsed ÷ `taskAvailabilityInterval`),
not by filter count. Filter count changes what one summary costs (one index descent each, ~20ms at
2000 filters per the Phase 1 gate), not how many are run. Statements per task came out ~10% above
the queue path, not the multiples the concern implied.

**3. Throughput is comparable at 200 filters and clearly better at 1000** (+37%). Do not read the
200-filter figure as a win; on one machine, with threads for nodes, 8% is noise.

**What this harness does *not* measure, stated plainly:**
- **§3.7's fetcher coalescing is untested by it.** The harness calls `claimTasks`/`assignTasks`
  directly with a fixed batch, so the observed "batch per fetch" of ~20 is just the batch that was
  asked for — it says nothing about whether `DistributedTaskFetcher` self-clocks. That claim still
  rests on the code reading in §3.7, not on measurement. Exercising it needs the job framework in
  the loop, which this harness deliberately does not carry.
- Tasks do no work, threads are not nodes, and 1000 filters is far short of the deployments this
  design is for. **It can falsify the dispatch claims; it cannot confirm the scaling claim.**
- Seeding dominates the run (~1.5 min for 10k tasks, one insert per task), so scaling the harness
  up much further wants a batched seed first.

## 9. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Duplicate processing after a false reap | High | Generous timeout, no force-write on lost version, self-fencing terminates in-flight work (§3.4) |
| Self-fencing kills genuine work on a DB blip | Low | Only fires after a full reap timeout with no successful renewal; such a node is already unable to complete its tasks |
| Summary query degrades with filter count | **High** (was Medium) | `EXPLAIN` in Phase 1, not Phase 3. Raised because Option C is rejected (§5), so the only fallback is B — itself a high-severity change — or abandoning the design |
| Summary turns O(filters)-once into O(eligible × nodes)-continuous, worst in the no-profile case | **Low** (was High) | **Lowered 2026-08-06 by measurement (§8).** The summary rate is bounded by (nodes × elapsed ÷ `taskAvailabilityInterval`), not by filter count: 56 summary queries at both 200 and 1000 filters. Filter count changes what one summary costs, not how many run. Phase 1's `EXPLAIN` at no-profile counts already bounded the per-query cost |
| Unique constraint on `processor_task` stalls creation as a poison pill | High if taken | Blocked until filter replicas land (§3.8); if taken, `ON DUPLICATE KEY UPDATE id = id` plus a monitored rejection counter, never a bare index |
| Unique constraint silently enforces nothing for stream tasks (InnoDB NULL semantics) | Medium if taken | Stored generated column in the key, not a `data` prefix — a prefix also falsely rejects distinct long event ranges (§3.8) |
| Filter replicas inflate the eligible-filter count that the availability summary scales with | Medium | `logicallyDeleteOldProcessorFilters` already reaps COMPLETE filters with no tasks; verify it keeps pace once replicas are routine (§3.8) |
| Loss of the central `filterQueues` diagnostic | Medium | Per-node sysinfo plus request-time aggregation (§3.6), which is new plumbing costed into Phase 2; this deletes the signature an open issue relies on |
| Per-fetch overhead at high dispatch rates | **Low** | Already mitigated by the fetcher's existing single-flight coalescing, whose batch window widens as DB latency rises (§3.7). **Still unmeasured after Phase 3**: the benchmark drives the DAO directly, so it never exercises `DistributedTaskFetcher`; the claim rests on code reading alone. If it does bite, raise the claim batch size rather than delaying the fetch |
| `SKIP LOCKED` lock behaviour under 64-node load | Medium | Load test; gap-lock behaviour on secondary index needs observing. 8 concurrent claimers behave correctly and give distinct rows (§8, `TestProcessorTaskClaiming`), but 8 threads on one machine is not 64 nodes |
| Reap query picks the `status_time_ms`-leading index and scans the retention backlog | Medium | `EXPLAIN` in Phase 0; must use `(status, create_time_ms)`. The frequent standalone schedule (§3.4) is only affordable while the reap stays bounded by PROCESSING count |
| QUEUED/ASSIGNED rows stranded at mode switchover — streams silently never processed | High if unhandled | Standing reaper sweep of stale QUEUED/ASSIGNED back to CREATED in the new mode (§3.4, §6 item 1); mid-roll safe because no-force-write covers every status transition |
| Phase 0 reaper steals live tasks from not-yet-upgraded nodes during the deploy roll | High if single-release | Two-release rollout (§3.4, item 18): heartbeat ships and soaks a full release before the reap condition changes |
| Disabling the reaper job silently stops dead-task recovery | Low | No longer coupled to the creator job (§3.4, item 17), but a standalone job is easier to forget. **Addressed 2026-08-06**: `ProcessorClaimSystemInfo` reports `deadTaskCount` — PROCESSING rows past the lease that nothing has recovered — with a warning naming the job. Detects the symptom, so it also catches a reaper that is running but failing |
| Creation throttling from a larger CREATED pool | Low | Open question 4 |
| Option B index cost on the hot table | High if taken | Do not take it before Phase 3 says so; #5684 precedent |

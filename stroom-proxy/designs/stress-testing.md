# Stress and Fault Injection

How the pipeline is tested against sustained load and deliberate failure, what
the tests assert, and how to run them.

Source: `stroom-proxy-app/src/test/java/stroom/proxy/app/pipeline/stress/`

## 1. Why This Exists Separately

The pipeline's ordinary tests answer "does this stage do the right thing". They
cannot answer the question that actually decides whether a proxy is trustworthy:
*when a store or a queue fails part-way through an operation, does anything get
lost?*

That question needs three things the ordinary tests do not have — a whole
pipeline running concurrently, failures injected at the exact points where an
operation is half-done, and a way to account for every byte submitted. This
suite provides all three.

## 2. Running It

```bash
# The five scenarios, a few seconds
./gradlew :stroom-proxy:stroom-proxy-app:stressTest

# The same scenarios with twenty times the load (a few minutes)
./gradlew :stroom-proxy:stroom-proxy-app:stressTest -PstressScale=20
```

The scenarios are tagged `stress` and the root build excludes that tag from the
normal `test` task, so they never slow down an ordinary build.

Scaling the load rather than maintaining a separate "long" suite is deliberate:
a soak run that asserted something different from the quick run would be a
second suite pretending to be the same one.

## 3. What Is Real

| Component | In the stress harness |
|---|---|
| Queues | Real `LocalFileGroupQueue`, wrapped in a fault decorator |
| File stores | Real `LocalFileStore`, wrapped in a fault decorator |
| Queue worker | Real `FileGroupQueueWorker` |
| Stage runners | Real `PipelineStageRunner`, with scaled-down backoff durations |
| Receive stage | Real `ReceiveStagePublisher` |
| Split-zip stage | Real `SplitZipStageProcessor`, with a pass-through split |
| Forward stage | Real `ForwardStageProcessor` |
| Pre-aggregate, aggregate | `TransferStageProcessor` stand-in |
| Path resolution | Real `SimplePathCreator`, rooted at the test directory |

Two stand-ins, both for the same reason. The real pre-aggregate and aggregate
stages batch many inputs into one output, which destroys the one-in-one-out
accounting the ledger depends on; `TransferStageProcessor` performs the
ownership transfer and nothing else. The split function emits a single child so
payload identity survives the stage — real fan-out would give several outputs
one payload id, which the ledger would read as duplicate delivery of a single
submission. Both aggregation and splitting have their own tests.

The runner backoffs are scaled down (10 ms doubling to 100 ms, rather than 1 s
doubling to 30 s) because under deliberate fault injection a run would otherwise
spend nearly all its wall-clock asleep and would be testing the sleeping. The
backoff *logic* is the real code and is exercised on every injected failure.

## 4. Fault Points

`FaultPolicy` decides when to fail; `FaultPoint` says where.

| Point | Models |
|---|---|
| `QUEUE_PUBLISH` | Publish never reached the queue |
| `QUEUE_PUBLISH_AFTER` | Publish landed, caller told it failed |
| `QUEUE_NEXT` | Lease failed |
| `QUEUE_ACK` | Acknowledgement never recorded |
| `QUEUE_ACK_AFTER` | Acknowledgement landed, caller told it failed |
| `QUEUE_FAIL` | Returning a failed item to the queue failed |
| `STORE_NEW_WRITE` | No writable location |
| `STORE_COMMIT` | Output never became visible |
| `STORE_COMMIT_AFTER` | Output committed, caller told it failed |
| `STORE_DELETE` | Consumed input could not be released |
| `STORE_RESOLVE` | Location could not be resolved |

The `_AFTER` points are the ones that matter. They let the underlying operation
succeed and *then* throw, modelling a process that dies between an effect
becoming durable and the caller learning that it did. That gap is what the
ownership-transfer contract is built around — write output, publish, delete
input, acknowledge — and the reason the contract is at-least-once rather than
exactly-once. A harness that only injected before-the-effect faults would never
produce a duplicate and so would never test the part of the contract most likely
to be got wrong.

`STORE_COMMIT_AFTER` also produces **orphans**: committed data referenced by
nothing, because the writing stage believed the commit failed and wrote it
again. Orphans cost disk and nothing else. The scenarios assert they are never
counted as a delivery.

### Determinism

Each thread draws from its own `Random`, seeded from the policy seed and the
order in which threads first reached the policy. A given thread therefore sees
the same sequence of draws on every run. The *interleaving* of threads is not
reproducible and the harness does not pretend otherwise. What the seed buys is a
stable fault rate and a stable per-thread sequence, which is enough to make
re-running a failure worthwhile — every assertion message carries the seed.

## 5. The Invariants

`DeliveryLedger` records what was submitted and what came out of the far end.
Every payload carries its own id and a CRC of its body, so the terminal stage can
answer both "which submission is this" and "is it intact" without any shared
in-memory state.

- **No loss.** Every submitted payload is delivered at least once. This is the
  invariant the pipeline actually promises.
- **No corruption.** Every delivery matches its own checksum and has all three
  file-group members. Faults may duplicate work; they must never produce a
  half-copied group, and a stage must never publish a reference to data it did
  not finish writing.
- **No invention.** Nothing is delivered that was never submitted — in
  particular, no orphan.
- **Duplicates are counted, not forbidden.** A duplicate is the *correct*
  outcome of a fault between publishing and deleting. Only the baseline
  scenario, which injects nothing, demands exactly-once — a pipeline that
  duplicates when nothing has gone wrong is broken.
- **Faults actually fired.** Every fault scenario asserts a non-zero injection
  count. A stress suite that quietly stopped injecting would otherwise pass
  forever while testing nothing, which is the most likely way for a suite like
  this to rot.

## 6. Stall Is Not Loss

If `acknowledge()` or `fail()` throws, `FileGroupQueueWorker` logs and rethrows,
and the item stays leased in `in-flight`. The work is not lost, but it has
stopped, and the two must not be confused — reporting a stall as data loss is
both wrong and the more alarming of the two.

This was the first thing the harness found. Recovery used to happen only in
`LocalFileGroupQueue`'s constructor, so stranded work waited for somebody to
restart the proxy. The queue now reclaims it while running:
`reclaimAbandonedLeases()` returns to pending any in-flight message whose lease
is held by no live consumer.

That check is exact rather than a guess. The local queue is confined to one
process, so it can know which in-flight messages a consumer still holds — the
lease is claimed before the pending file moves into `in-flight` and released when
the item is closed. It is therefore **not** a visibility timeout and cannot take
work away from a consumer that is merely slow, which is the failure mode that
makes SQS visibility timeouts awkward to tune. SQS and Kafka have no equivalent
option: being distributed, they must infer abandonment from elapsed time or a
rebalance.

The scan runs only when a poll finds nothing pending, and no more often than
`abandonedLeaseScanInterval` (default `PT10S`, configurable per queue). Anything
it reclaims is logged at `WARN`, because a consumer that neither acknowledges nor
fails its work is a bug somewhere.

`StressPipeline.quiesceAndDrain` still restarts the pipeline if delivery stops
moving, and reports whether it had to. That backstop stays because only a restart
recovers a message whose consumer never closed the item at all — a hard kill.

## 7. Head-of-Line Blocking

The stall above was the first thing the harness found. Running the same scenarios
at `-PstressScale=5` found something larger.

`findNextPendingFile()` always takes the lowest id, and `fail()` used to return a
message to `pending/` under its original id — putting it straight back at the
head. A message the pipeline could not process was therefore handed out again
immediately, and everything behind it waited. At scale 1 there was rarely such a
message and the scenarios passed; at scale 5 the queues froze with thousands of
messages pending, zero in flight, and delivery stopped dead.

At-least-once delivery is what makes unprocessable messages normal rather than
exotic: a message can reference a file group that an earlier duplicate already
consumed, and it will fail every time it is tried. Combined with the production
failure backoff — one second doubling to thirty — a single such message was
enough to stop a queue indefinitely.

`fail()` now re-queues at the back under a new id and quarantines after
`maxDeliveryAttempts` (§2.7 of [queues.md](infrastructure/queues.md)). The effect
on the suite is the measurement: the 5× run went from three failures in 3m28s to
green in 19s.

This is the argument for the harness existing. Nothing in the per-class tests was
wrong, the defect needed concurrency, sustained load and injected duplicates
together before it showed at all, and it would have presented in production as
"the proxy stops keeping up after a while".

## 8. The Scenarios

| Scenario | Injects | Asserts |
|---|---|---|
| Baseline | nothing | Exactly-once, every store and queue empty afterwards |
| Store faults | all five store points | No loss, no corruption; orphans never delivered |
| Queue faults | publish, publish-after, next, fail | No loss, no corruption; duplicates occurred |
| Everything at once | all of the above plus delays, 4 concurrent submitters, both routes | No loss, no corruption; receive rejected and retried |
| Unclean restart | nothing; items leased and abandoned by hand | In-flight recovered to pending, all delivered, no duplicates |

The "everything at once" scenario also routes half its submitters through the
split-zip stage by writing multi-feed entries, so both paths out of the receive
stage are covered.

Delays are injected as well as failures. A stage that is merely slow rather than
broken is what surfaces lock-holding bugs, queue-depth blow-ups and backoff
mistakes, and none of those need an exception to reproduce.

## 9. Proving The Tests Can Fail

An invariant checker that never fires is worse than no checker: it produces a
green run that reads as evidence.

`TestStressHarnessDetectsRegressions` breaks each invariant on purpose and
asserts the harness notices — loss, invention, a truncated body, a missing
file-group member, a policy that has stopped injecting. It also reproduces a
real, already-fixed bug end to end: `LocalFileGroupQueue` used to name pending
files from a counter persisted only on clean close, so after an unclean stop a
restart handed out ids that were already in use and silently overwrote a queued
message. The test reopens a queue without closing it and asserts every published
message survives.

These tests are deliberately **not** tagged `stress`. They are fast and they run
on every build, because the thing most likely to rot here is the detection rather
than the pipeline.

## 10. Extending It

To add a fault point: add it to `FaultPoint`, intercept it in the relevant
decorator, and enable it in a scenario. If it is an after-the-effect fault, say
in its javadoc what residue it leaves — a duplicate, an orphan, a stranded
lease — because that is what tells a future reader whether a scenario failure is
a bug or the modelled behaviour.

To add a backend: the decorators wrap the `FileGroupQueue` and `FileStore`
interfaces, not the local implementations, so an SQS or Kafka queue can be
dropped in by changing which factory `StressPipeline` extends. The invariants do
not change; the recovery behaviour does, and §6 is where that difference is
recorded.

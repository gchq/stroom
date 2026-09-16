# Contracts

[← Design documentation](README.md) · [Architecture](architecture.md) · [Data path](data-path.md)

**What must always be true, who is responsible for it, and what breaks if it stops being
true.**

The rest of the design set describes *mechanism* — what each component does, in what
order, with which files. This document describes *contracts* — the invariants that hold
across the seams **between** components. It exists because the seams are where the same
questions kept being re-answered, differently, by successive changes:

- may a caller move or delete a path a file store handed it? (asked four times: **D20**,
  three findings)
- must a lease be released before or after the work? (two failed fixes)
- does an absent config value mean "default" or "not stated"? (two failed fixes)

Those answers were either absent or scattered. Where one *was* written down it held up well —
[architecture.md §3.2 and §5](architecture.md#5-ownership-transfer-protocol) state the
cross-stage ordering contract clearly, and it has not been re-litigated once. The failures are
where no contract was stated at all (`FileStore.resolve` is documented as a five-step
procedure with nothing about what the caller may do with the result), and where a contract was
stated but its *consequences* were recorded as acceptable rather than as work (§2.2). Each fix
then re-derived an answer from code that was not itself consistent, so every derivation was
defensible and some were wrong.

> **Scope.** Where the code does **not** currently honour a contract, that is stated inline
> and listed again in [§8](#9-known-divergences) with its finding id — this document says what
> must be true, not what is true today, and the difference is always marked.
>
> **Provenance.** Each contract is tagged **Ruled** or **Derived**.
>
> - **Ruled** — decided by the owner, with the date. Authoritative.
> - **Derived** — my inference from a ruling plus what the code already does. Defensible, but
>   *not* decided, and each one is where this document is most likely to be wrong. Derived
>   contracts are collected in [§9](#10-derived-contracts--all-resolved-2026-09-02) so they can
>   be confirmed or overturned as a set rather than found by accident. The first five were put
>   and resolved on 2026-09-02; four were confirmed and one was corrected.
>
> A derived contract that later gets ruled should be re-tagged, not left ambiguous — the
> ambiguity between "we decided this" and "someone inferred this" is where most of the seam
> defects came from.

---

## 1. Standing rulings

The four original rulings (2026-08-25) and everything ruled since. These govern the
contracts below; a contract that conflicts with a ruling is wrong.

| | Ruling | Date |
|---|---|---|
| **R1** | **Power-loss durability on every path.** At-least-once for all data paths, through power loss. Idempotency is **not** enforced, so duplicates on replay are acceptable and expected. Constraint: achieve it without much code or processing overhead. | 2026-08-25 |
| **R1a** | **Who may recover the work depends on the queue backend** — it is not a property of the pipeline. With a **distributed queue and shared storage another node takes over; the original node is not needed.** The single-owner, restart-to-recover model applies to the **local queue backend only**. See [§2.5](#25-who-may-recover-in-flight-work). | 2026-09-02, correcting R1 |
| **R2** | **No stranded data.** Nothing may be left in limbo while the queues are intact. Data that cannot be forwarded must be retried, and when retry gives up the data must land **somewhere a human can decide to resend it**. Re-injecting file groups into the scanner's input area is an acceptable recovery mechanism. | 2026-08-25 |
| **R3** | ~~S3 is not supported for `preAggregateStore`.~~ **Retired 2026-09-07.** The reason was an S3 store that flattened keys; the rewritten store holds a directory tree on every backend, so a nested group is just a longer key and a shared deployment needs one shared storage, not two. See [file-stores.md §10 D2](infrastructure/file-stores.md#10-decisions). | 2026-08-25, retired 2026-09-07 |
| **R4** | **Every data-loss fix gets a regression test that would have failed before it.** Test code is not product complexity. | 2026-08-25 |
| **R5** | **Explicit or fail, for pipeline configuration.** A `stages` block is required, must list all four stages, and each must state `enabled`. No reading of silence. | 2026-09-01 |
| **R6** | **The give-up destination is configurable and independent of the forward destination.** A file forwarder may quarantine to S3 and an S3 forwarder to local disk. | 2026-09-01 |
| **R7** | **Ownership transfers on hand-off** — but the receiver must finish with the item *before* it leaves the upstream queue. See [§3](#3-hand-off-between-components). | 2026-09-02 |
| **R9** | **A control that cannot function must stop the proxy, not degrade quietly.** If configuration asks for a check that cannot run — a receipt check with no reachable downstream, say — that is a boot-time failure, not a warning to start past. Refusing to start is diagnosable in minutes; admitting everything while reporting success is not discoverable at all. *(The flag that once allowed booting past invalid configuration was removed under R15.)* | 2026-09-02 |
| **R8** | **Shut down cleanly, but never depend on it.** Clean shutdown is worth having; correctness must continue to rest on power loss and `kill -9` being survivable. | 2026-09-02 |
| **R10** | **Completeness is proved by one thing that appears atomically, last.** Where the write is *already* atomic — a single object PUT, a directory published by a rename — its own presence is that proof and nothing is added. Where it is not — a group of objects written one at a time — a marker written after all of them supplies it. Nothing else may be read as proof: not the existence of a directory, not a partial listing. See [§3.3](#33-presence-is-not-proof). | 2026-09-03 |
| **R12** | **Absence of data means the work was done.** A queue item that resolves to nothing is acknowledged, not failed and not quarantined — unconditionally, without consulting the delivery-attempt count. This is what [§2.1](#21-work-precedes-acknowledgement)'s ordering *means*: the input is deleted only after the output is durable and its onward message is published, so there is no state this pipeline produces in which the input is gone and the work is not done. | 2026-09-03 |
| **R11** | **Clear all transient state at start-up, wherever that cannot lose data.** Recovering from power loss by discarding scratch on the way up is cheaper and far easier to reason about than checks during execution or a background cleanup job. *Transient* means anything not committed and not claimable: staging, caches, per-resolve copies, temp split output. It does **not** mean in-flight queue state, which must be recovered rather than dropped. *Refined 2026-09-07 for shared storage:* a node may only clear what it can prove is its own, and on a shared mount it can prove nothing - other nodes are writing and a node that left residue may never return - so there, and only there, residue is cleared **by age** instead. See [§3.4](#34-transient-state-is-cleared-at-start-up). | 2026-09-03, refined 2026-09-07 |
| **R13** | **There is no version to be compatible with.** The pipeline is new and unreleased, so nothing on disk, in a config file or in a queue was written by an earlier version. Fixes are made *right*, not made migratable: no format shims, no "an older version may still have written this" tolerance, no upgrade notes. A config property that should not exist is deleted rather than deprecated, even though the proxy sets `FAIL_ON_UNKNOWN_PROPERTIES`. This is the one ruling with an expiry date — it holds until the first release, and every accommodation it authorises removing is one that would otherwise have to be carried forever. | 2026-09-04 |
| **R14** | **Explicit or fail, for the whole pipeline block.** R5 said it of `stages`; it holds for `mode`, `queues` and `fileStores` too. Nothing is merged into a pipeline from compile-time defaults; an absent block is reported absent. See [§4](#4-configuration). | 2026-09-08 |
| **R15** | **One validator, run once at boot, reporting every error, halting the boot on any.** No flag to boot past invalid configuration, no second validator in the runtime, no bean-validation rules on the pipeline tree. See [§4](#4-configuration). | 2026-09-08 |

### 1.1 Intent, and how it should weight everything below

Three points of intent, stated by the owner on 2026-09-02. They are here rather than in
`architecture.md` because each one changes how a contract above should be *weighted*, and that
weighting has already been got wrong once.

**Distributed is the direction of travel — and it is new.** External queues with shared
storage are where this is heading, not a capability bolted onto a single-node product. It is
recent, which is why several documents (and R1 itself) were written local-first and read as
though one node were the normal case. **Read every contract here with the distributed
deployment as the target and the single local node as the special case.** The practical
consequence is a re-ranking: a defect that needs a power cut on one node can be routine under
load across several — absence of data is exactly that — while local-only concerns such as the lease
handling matter less than their severity suggests.

**Aggregation serves ingest, distribution and processing efficiency.** Not only transfer — the
aggregate is a *unit of work* downstream, not just a batch in transit.

**But size and item count are targets, not guarantees** — see [§7](#7-aggregation).
Occasional misbehaviour is tolerated and expected. *(I overstated this first: I called aggregate
sizing "correctness-adjacent", which reads as though a mis-sized aggregate were a defect. It is
not. Shipping an under-sized aggregate is normal and often required.)*

**The proxy is deployed at various trust positions, in different modes.** There is no single
trust boundary to design against. A control that is vacuous in one deployment is load-bearing
in another, so security findings must be weighted by mode rather than by whether they are
reachable in the deployment nearest to hand. `ZipDirScanner`'s note that the trust boundary for
file ingest is *write access to the scanned directory* is a real constraint in some modes, not
a theoretical one.

---

R1's operative form is worth stating separately, because it is what makes at-least-once
achievable without idempotency:

> **At every interruption point, either the input is still claimable or the output is
> durable.** Nothing needs to be atomic — only *ordered*.

---

## 2. Queue ↔ consumer

Mechanism: [infrastructure/queues.md](infrastructure/queues.md).

### 2.1 Work precedes acknowledgement

**Ruled** (R7, 2026-09-02), and already documented in [architecture.md §3.2](architecture.md#32-ownership-transfer-contract).

**A consumer must complete its work, including disposing of the input, before it
acknowledges the message.** Acknowledging first would lose data on a crash in the window;
working first can only duplicate, which R1 permits.

`ForwardStage` is the reference implementation: it delivers, then deletes the input, and only
then returns to the worker that acknowledges.

### 2.2 A queue item may point at data that is already gone

**Ruled** (R7, 2026-09-02) — the owner stated this consequence explicitly.

This is the direct consequence of §2.1. If the process dies between disposing of the input
and acknowledging, the message is redelivered — and the data it names no longer exists,
**because the work succeeded**.

> **This scenario is not new.** [architecture.md §5](architecture.md#5-ownership-transfer-protocol)
> already documents it, as the last row of its crash-recovery table: *"the message can never
> succeed and is retried until it dead-letters — even though its work completed"*, called
> "the operational trap worth knowing", with the advice to check whether the onward message
> exists and decide by hand. **R7 supersedes that disposition.** What was written down as an
> accepted cost is now a defect to fix: an operator should not have to adjudicate a message
> the system already has enough information to resolve. That table row must be corrected when
> the absence ruling landed.

**A missing input is therefore an expected state, not an error.** A consumer must
distinguish:

| Situation | Meaning | Correct response |
|---|---|---|
| Input missing on a **redelivery** | The work was already completed | Acknowledge and drop the message |
| Input missing on a **first delivery** | A genuinely broken hand-off | Fail the message |

The discriminator is the delivery-attempt count. `LocalFileGroupQueue` already tracks it
(`deliveryAttempts(message)`); SQS exposes it as `ApproximateReceiveCount` and Kafka needs a
header.

**On a distributed backend this is routine, not a crash window.** No failure is required: if a
node finishes the work and deletes the input but its SQS visibility timeout expires before it
acknowledges — a slow ack, a long stage, a GC pause — the message is handed to another node,
which finds the data gone. The same follows from a Kafka rebalance. So the situation this
section describes is ordinary operation under load in exactly the deployments the distributed
backends exist to serve, and only *looks* like an edge case when reasoning from the
single-node local queue.

> **Divergence, since resolved.** All four stage processors currently throw on a missing source
> directory, and `FileGroupQueueWorker` converts that into `item.fail(..)`. So completed work
> is recorded as failure: local quarantines it after `maxDeliveryAttempts`, SQS redelivers
> indefinitely, and **Kafka never commits the offset, so the partition stops**.

### 2.3 A claim is released by its holder, or by the mode

**Ruled** (2026-09-02; restated 2026-09-07 with the queue rewrite,
[queues.md §3 Q4](infrastructure/queues.md#3-the-contract)).

**The requirement on a claim is liveness, not identity.** A claimed item must **eventually become
claimable by something else** if its holder stops holding it - another consumer on the distributed
backends, or the same process on restart for the local one. No failure mode may leave an item
claimed for ever. Recording who holds a claim is a diagnostic, never the mechanism.

**Closing an item without acknowledging or failing it releases the claim.** That is the holder's own
release, and it is what the worker does in a `finally` when `acknowledge()` or `fail()` has thrown:
on the local queue the message goes back to `pending/` with the attempt counted, on Kafka the consumer
seeks back, on SQS the visibility timeout lapses. It is still **not a completion** - the message is
delivered again - and it is what lets the local queue carry no lease bookkeeping at all: an item is
claimed exactly while a consumer holds it, and a process that dies holding one leaves it to start-up
recovery.

### 2.4 Give-up is bounded and lands somewhere visible, in the mode's own infrastructure

**Ruled** (2026-09-02, confirming a derivation from R2; refined 2026-09-07,
[queues.md §10 D2](infrastructure/queues.md#10-decisions)).

Under R2 retry must **terminate**, and terminate somewhere a human can act. This is the
*queue-level* give-up, distinct from the forward-level one in [§5](#5-give-up-and-replay).

**Where it lands is the mode's.** In local mode the queue's own `failed/` directory, beside the
queue, after `maxDeliveryAttempts`. In shared mode a node's disk is not somewhere any other node, or
an operator, can rely on finding - the node may have been scaled away with it - so give-up is the
broker's: an SQS queue's redrive policy and dead-letter queue, with `maxDeliveryAttempts` refused
for SQS because the proxy counts nothing there; a Kafka topic's dead-letter topic `<topic>.failed`,
after `maxDeliveryAttempts`, which the proxy creates at start-up.

*Added 2026-09-15.* The broker's **retention** is the same kind of bound, and the one that loses data
rather than parking it: SQS deletes a message a fixed time after it was sent whatever its visibility,
so a retention shorter than the forward retry window deletes a message still being retried and leaves
its group to the sweep. An SQS queue is therefore checked at start-up for a redrive policy and for a
retention longer than the longest `maxRetryAge`, and the proxy refuses to start otherwise (R9;
[queues.md §5.2](infrastructure/queues.md#52-sqs)). A dead-lettered message is subject to the same
sweep: the group it names is in its store only until `orphanAge` or the lifecycle rule.

### 2.5 Who may recover in-flight work

**Ruled** (R1a, 2026-09-02).

**R1a.** This is a property of the deployment, not of the pipeline, and getting it wrong in
either direction is harmful.

| Backend | Storage | Who recovers in-flight work |
|---|---|---|
| **Local** (`LocalFileGroupQueue`) | Node-local | **Only the same node, by restarting.** `recoverInFlightMessages()` moves *everything* in `in-flight/` back to `pending/` on construction, so a second JVM starting against the same directory would reclaim items the first is actively processing. A local queue must not be shared. |
| **SQS** | Shared (S3 or shared filesystem) | **Any consumer.** Visibility-timeout expiry hands the message to another node. The original node need never return. |
| **Kafka** | Shared (S3 or shared filesystem) | **Any consumer.** Partition rebalance moves the work. The original node need never return. |

The requirement this places on the rest of the system: **for the distributed backends, every
location in a queue message must be resolvable by every node** — which is why external-queue
deployments require shared storage, and why `FileStoreLocation` carries a complete path
including the writer id rather than anything node-relative.

R1's original text generalised the local backend's single-owner model to the whole system.
It does not hold: taking over another node's work is the entire point of the distributed
backends, and both are documented as the supported answer for multi-process deployments.

---

### 2.6 Absence of data means the work was done

**Ruled** (R12, 2026-09-03), resolving §2.2's divergence.

**A queue item that resolves to nothing is acknowledged.** Not failed, not quarantined, and
**without consulting the delivery-attempt count** — the count is not the discriminator here.

The reasoning is that §2.1's ordering already answers it. Output is made durable, its onward message
is published, and only *then* is the input deleted; the acknowledgement comes last. So the input
being gone is itself proof that everything before it happened. There is no state this pipeline
produces in which the input is absent and the work is not done — absence is not ambiguous, and
treating it as ambiguous is what made absence look like it needed a discriminator.

> **This ruling supersedes the analysis it was asked about**, which had recorded
> the delivery-attempt count as a *hard prerequisite* for the absence ruling — the reasoning being that a first
> delivery whose input is missing must be a broken hand-off and so must fail, which needs the count
> to tell first from subsequent. That distinction does not exist: under §2.1 a first delivery cannot
> find its input missing unless something deleted it out of band, which is not a state the pipeline
> creates. **The absence ruling therefore needs no count at all.** The count is still required, but for
> [§2.4](#24-give-up-is-bounded-and-lands-somewhere-visible)'s bound — which is what the queue layer shipped it
> for.

**What this rests on**, stated so that it is checkable rather than assumed: §2.1's ordering, and
[§3.3](#33-presence-is-not-proof)'s guarantee that a store reports absence accurately rather than
returning an empty directory. If either stops holding, this ruling is unsafe — a store that reported
absence when data was merely unreachable would, under this rule, silently acknowledge live work.

## 3. Hand-off between components

**R7. When a component hands a directory to another — `resolve()` to a processor, a receiver
to `destination.accept()`, a forwarder to a queue — ownership transfers to the receiver.**

The receiver may process the data, move it, delete it, or publish it onward. It owns the
directory from that moment.

Three consequences, each of which has been got wrong at least once:

1. **The receiver cleans up on its own failure.** If `destination.accept(dir)` throws, the
   directory is the destination's to deal with, not the caller's. The caller must not delete
   it — after a partial move the caller cannot know what is left.
2. **The giver must not touch it afterwards.** No parallel cleanup, no "tidy up if it looks
   stale".
3. **But the receiver must finish before the item leaves the upstream queue** (§2.1), which
   is what makes the disappearance in §2.2 both expected and safe.

> **Resolved 2026-09-08 by removing the hand-off.** Receipt no longer builds a directory and
> hands it to a destination: it writes into a store write handle and commits, and a failed receipt
> closes the handle, which discards the write ([stages/receive.md §4.1](stages/receive.md#41-where-the-bytes-go)).
> The rule stands for the hand-offs that remain - `resolve()` to a processor, a forwarder to a
> queue, and the instant file forwarder's directory to its destination.

### 3.1 A resolved store path belongs to the caller

**Ruled** (2026-09-02), extending R7 from destination hand-off to store lending. **Refined
2026-09-07** with the file-store rewrite ([file-stores.md §3 C3, §10 D6](infrastructure/file-stores.md)).

**`FileStore.resolve()` returns a path the caller may move or delete, and the store does not touch
it afterwards.** "Private" means *no one else is reading it*, and that is something the queue
provides rather than something the store has to manufacture: a committed group has exactly one
reader, the holder of the one leased message that names it.

So a **filesystem store lends the committed directory itself** - no copy, because none is needed -
and the forwarder moves it out, after which the stage's `delete()` finds nothing and treats that
as success; the aggregate stage reads it in place and deletes it when the aggregate is published. An **object store downloads a fresh copy per resolve**, because there is
no local path to lend. The re-download on a redelivered message is the accepted cost of the contract
being the same on both.

**The store owns what it lends** (R11, [§3.4](#34-transient-state-is-cleared-at-start-up)): a
per-resolve copy that a crash leaves unconsumed is the store's scratch, cleared by its own
housekeeping and never by the stage processor's failure path - that structure is what
[§3](#3-hand-off-between-components) clause 2 warns against.

### 3.2 Cleanup after a durable step must never throw

**Derived** from R7 and the call sites. Uncontroversial — a throw here is read as a failure of the
step it follows — but not separately ruled.

**Once a step is durable, no housekeeping that follows it may throw.** A throw is
indistinguishable, to the caller, from a failure of the durable step itself, so the caller undoes or
repeats work that in fact succeeded. Cleanup reports failure by **logging**, naming the consequence
of the leak it is leaving behind.

*Broadened 2026-09-03 by layer L0.* This clause used to name only the forward destinations' cleanup
queue, called after a successful send. Four more sites had exactly the same shape
and all three of the findings against them were the same defect: a post-durability delete that
threw. `AggregateClosePublisher` deleted the source aggregate after committing *and* publishing it,
and the throw skipped `PreAggregator`'s state-map removal, so the next close **republished**
everything; `ReceiveStagePublisher` reported a temp-directory failure to the sender as a
**failed POST**; `S3FileStore.commit` reported a **completed upload as a failed commit**;
and `SplitZipStageProcessor` cleaned up in a `finally`, where a throw does not add to the in-flight
exception but **replaces** it.

Two corollaries the L0 work established:

- **There is one recursive-deletion implementation**, `stroom.util.io.FileUtil.deleteDir`. It
  cannot throw and reports failure by return value, which is what makes the clause structural
  rather than a rule each site has to remember. Every caller must read that result — discarding it
  makes a failed delete silent twice over.
- **Where the delete *is* the operation, it must still be loud.** `FileStore.delete()` and the
  clearing of an obstruction before a write are not housekeeping; they must reconstruct a throw
  from the return value. Note that `deleteDir` no-ops and returns `true` for a non-directory, so a
  site that must be loud has to dispatch on shape.

The forward stage's delete of its input after a destination has accepted the group is housekeeping
of exactly this shape: a failure is logged, naming the group left behind for the store's sweep, and
the message is still acknowledged, because throwing would deliver the group downstream again on
every redelivery for as long as the delete kept failing
([stages/forward.md §4.1](stages/forward.md#41-one-attempt)).

---

### 3.3 Presence is not proof

**Ruled** (R10, 2026-09-03).

**A store must be able to distinguish a complete write from a path that merely exists**, and a
consumer relies on that absolutely: it reads absence as proof the work was done
([§2.6](#26-absence-of-data-means-the-work-was-done)), so a store that returned an empty or partial
directory for a group that was never finished, or was already consumed, would silently lose or
truncate data.

**The rule is one rule, and it is not per-store.** Completeness is proved by one thing that appears
atomically, last. A marker is required *exactly where the write is not already atomic*; what varies
between stores is only whether they already have something atomic to point at.

| What is written | Already atomic? | What proves it complete |
|---|---|---|
| A single S3 object - `S3Destination` PUTs one `.zip` per file group | **Yes.** A PUT either happened or it did not | **The object itself** |
| A multi-object S3 group - `S3FileStore` PUTs every file of the tree one at a time | **No.** Any subset can survive a crash | **A marker object at the group's own key, PUT last**, only once every other PUT has been acknowledged. Objects without it are an interrupted upload, whoever wrote them |
| A filesystem group - `FilesystemFileStore` renames staging into place | **Yes.** Staging is under the same writer root, so the rename is always within one filesystem | **The group directory itself.** The only way one appears at a group path is that rename |

`resolve()` therefore either hands back the whole group or throws `FileGroupNotFoundException`. It
never returns a directory it created itself, never a partial listing, and never reads a marker
found deeper in a nested tree as proof of the group above it.

**No flag is needed to make this correct, and none is needed to detect the filesystem.** A store's
staging is always on the same filesystem as its committed groups, so the rename that publishes is
atomic wherever the store sits. `ForwardFileConfig.atomicMoveEnabled` exists and stays what it is:
an *optimisation* on a forward destination the operator already knows is remote. It does not change
what is guaranteed.

> **Ruled 2026-09-03: do not extend that flag to the file stores.** A store-level flag would be
> configuration that changes nothing. Recorded because the reasoning that suggests it - *"our code
> cannot know at runtime whether an atomic move will work"* - is sound, and will suggest it again to
> the next person. The answer is that it does not need to know.

### 3.4 Transient state is cleared at start-up

**Ruled** (R11, 2026-09-03; refined 2026-09-07 for shared storage).

**Discarding scratch on the way up is the recovery mechanism for power loss** - not checks during
execution, and not a background cleanup job - wherever one process is the only writer. It is
cheaper, and far easier to reason about: there is one moment when the invariant is established,
and it is a moment when nothing else is running.

This names a practice the proxy follows throughout: `StoringReceiver` and `InstantForwardReceiver`
each clear their own working directory on construction; split-zip, aggregation and forwarding have
none, since they write only into stores and deliver from them. A **local**
file store does the same, and goes further: at start-up the runtime compares every local store with
every local queue and deletes every committed group no message names - the one moment that
comparison is exact ([file-stores.md §5.5](infrastructure/file-stores.md#55-clearing-what-is-left-behind)).
That is the only cleanup a local store gets.

**What counts as transient** is anything neither committed nor claimable: staging directories,
per-resolve copies, temp split output, partially-written scratch, and a committed group that no
message names.

**What does not**, and the caveat is load-bearing: in-flight queue state must be **recovered, not
dropped**. `LocalFileGroupQueue.recoverInFlightMessages()` moves everything in `in-flight/` back to
`pending/` at construction - that is this rule applied correctly, because the state is claimable
work and deleting it would lose data. The test is not "is it left over from last time" but "would
discarding it lose something no longer held anywhere else".

**On shared storage, start-up clears nothing shared.** A node may only clear what it can prove is
its own, and on a mount every node writes to it can prove nothing: other nodes are running, and a
node that left residue may have been scaled away and will never start again. There the mechanism is
**age**: a shared filesystem store is swept on a schedule by every node, deleting anything under it
older than the store's required `orphanAge`, and an S3 store is cleared by a lifecycle rule the
operator configures. A group older than `orphanAge` is deleted whether or not something still
names it, so the bound must exceed the longest a live group can legitimately wait; the validator
enforces the two bounds it can compute - `orphanAge` must be **at least twice** the forward retry
window and at least twice the aggregation window, since an aggregate's inputs wait in their stores
for as long as the aggregate is open, and the second multiple covers the one redelivery shared mode
exists to survive *(ruled 2026-09-15)*. The sweep cannot tell an orphan from live work
and R12 makes the loss silent, so the count of groups it deletes is exported per store; it should
stay near zero. Node-local scratch - an S3 store's
staging and downloads - is still cleared at start-up, because one process owns that disk.

## 4. Configuration

**Ruled** (R5, 2026-09-01; broadened to R14 and joined by R15 on 2026-09-08).

Configuration is not a layer of its own. Each component's configuration is designed with the
component - the stores' with the stores, the queues' with the queues - and the whole tree is read
through once, as an operator would, at the end. What belongs to nobody else is the substrate every
block inherits, and that is these three rules.

1. **Explicit or fail (R14).** A block that is absent is absent, and the validator says what is
   missing. No compile-time default is merged into the pipeline: an unstated `mode` is null,
   unstated `queues` or `fileStores` are empty, an unstated stage set is unconfigured, and every one
   of those is an error at boot. This is what makes "not stated" detectable at all - a merged-in
   default can never be told from a written one, which is how two earlier fixes silently never fired.
   Values *within* a stated entry may default (a queue's type, a store's path); entries never do.
   Where validation is bypassed the fallback is **disabled**, never busy.
2. **One validator, once, all of it, then halt (R15).** The pipeline has one validator,
   `ProxyPipelineConfigValidator`, run once at boot before anything is built. It reports every error
   it can find rather than the first, its warnings are logged, and the boot halts on any error.
   There is no flag to boot past invalid configuration, and no rule lives anywhere else: bean
   validation annotations, Dropwizard `@ValidationMethod`s and a second check inside the runtime
   are all places a rule can hide, and none of them exist on the pipeline tree.
3. **A control that cannot function stops the proxy (R9).** A check that finds itself unable to run
   is an error, not a warning - a receipt check with no reachable downstream, say - which is why the
   shipped deployment examples each state a downstream host rather than inherit one.

## 5. Give-up and replay

**Ruled** (R2, R6). The replay properties in §5.2 are verified behaviour, not rulings — they describe what the code does today and what an operator must therefore do.

**R2 and R6.** Mechanism: [stages/forward.md §4.3](stages/forward.md#43-giving-up),
[operations.md](operations.md).

### 5.1 Give-up must land somewhere a human can act

A forward destination that is refused a group, or that has been failing to deliver it for longer
than `maxRetryAge`, writes the group whole to its give-up destination, by default `03_failure`
beneath its own forwarding directory, and only then acknowledges the message. The location is
configurable and **independent of where the destination forwards** (R6), so a file forwarder can
quarantine to S3 and an S3 forwarder to local disk. In shared pipeline mode it must be shared - an
S3 bucket, or a directory under a `SHARED_FILESYSTEM` store's path - because the node that gives up
may never be asked again; the validator refuses a node-local one.

A give-up destination must be able to *hold data for an operator to come back to*. HTTP cannot
and is rejected at construction.

`error.log` is written beside the group at give-up - the final failure, the attempt count, the
age - and travels with it; it is the **only** record of why the group was given up on. Nothing
else retains it.

### 5.2 Quarantined data is replayable, and how matters

R2 is only satisfied if the operator can actually get the data back in. The route is the
scanned ingest directory, and these properties are what make it work:

- **Move the zips.** The per-entry `.meta` inside a proxy zip is authoritative for feed and
  type — the outer `proxy.meta` is merged underneath it as a fallback only. Receive
  synthesises a `.meta` for any data entry lacking one, so anything that reached `03_failure`
  already carries its own.
- **Sidecars are optional.** Dropping `proxy.meta` correctly mints a fresh receipt id, which
  is appended to each entry's `ReceiptIdPath`, preserving the chain. Nothing on the ingest
  path reads `proxy.entries` or `error.log`; both are consumed with the group.
- **Preserve the directory structure, or rename.** Every group's zip is named `proxy.zip`.
  The scanner recurses into subdirectories; flattening several groups into one directory
  makes them collide.
- **Copy `error.log` aside first** — replaying discards the only record of the failure.
- **Only `.zip` files are ingested.** Receipt is gated on the extension, so a stray file can
  never be received as data; unknown files are moved to the scanner's own failure directory.
- **Replay is at-least-once**, consistent with R1.

---

## 6. Durability

**R1.** Mechanism: [infrastructure/queues.md §5.1](infrastructure/queues.md#51-local-queue).

**Ruled** — R1 requires the durability, and it is **configurable** via a `durability`
enum, and the three commit points below were confirmed on 2026-09-02.

The invariant is ordering, not atomicity: **at every power-loss point, either the input is
still claimable or the output is durable.** Duplicates on replay are accepted, so nothing
needs to be atomic.

That makes the requirement precise — the *commit points* must be durable before the step that
publishes them is observable:

| Commit point | Requirement |
|---|---|
| Message published to `pending/` | The message bytes **and** the directory entry that publishes them |
| `FilesystemFileStore.commit()`'s rename | The file group's files, before the rename; after it, the directory the rename landed in, every numbering directory the commit created above it, and the existing directory that gained the entry naming the highest new one ([file-stores.md §5.2](infrastructure/file-stores.md#52-filesystem-store)) |

The file-store commit point is met by the store itself, under its own per-store `durability`
(`FULL` by default on local disk). The queue commit points are the queue layer's, governed by
`proxyConfig.durability`; their status is recorded in
[infrastructure/queues.md §5.1](infrastructure/queues.md#51-local-queue).

### 6.1 Durability on non-local storage

**Ruled 2026-09-02**, closing what had been left open here.

**The contract does not change with the storage; only the mechanism does.** R1's invariant is
storage-agnostic — the output must be durable before the input is released — and it is each
store's own responsibility to satisfy it. **`commit()` must not return until the committed data
is durable by that storage's own guarantee.**

| Storage | What makes a commit durable | What the pipeline must do |
|---|---|---|
| Local filesystem (`LOCAL_FILESYSTEM`) | `fsync` of the file group's files, then of the directory entries that publish them | The store's `durability` defaults to `FULL` |
| Object store (`S3`) | The write is acknowledged by the store | Nothing extra — the acknowledgement *is* the durability point |
| Shared filesystem (`SHARED_FILESYSTEM`) | The mount's own semantics | The store's `durability` defaults to `FILESYSTEM`; a local `fsync` says nothing about a network mount. An operator who knows the mount honours it may set `FULL` |

So the fsync is not a general durability feature: it is **the local store meeting an obligation
the object store already meets by construction**. A caller must not need to know which store it
has — that is the point of the contract living here rather than in each implementation.

The three local commit points above therefore stand as the local-storage row of this table,
not as the definition of durability.

**Shutdown is not a durability mechanism** (R8). Correctness must not depend on `close()`
running. When adding anything to a close path, ask: *what happens if this never executes?*

---

## 7. Aggregation

**Ruled 2026-09-02.**

Aggregation exists to serve ingest, distribution and processing efficiency downstream: an
aggregate is a unit of work, not merely a batch in transit.

**Its size and item count are targets, not guarantees.** A consumer must not assume an
aggregate meets the configured `maxUncompressedByteSize` or `maxItemsPerAggregate`.

- **Shipping an under-sized aggregate is normal and often required.** Age-based close
  (`aggregationFrequency`) deliberately closes aggregates that have not reached either limit,
  because old data must move rather than wait for company. That is the intended behaviour, not
  a degradation of it.
- **Occasional misbehaviour is tolerated and expected.** Aggregate sizing is best-effort. A
  mis-sized aggregate is not a defect, and nothing downstream may be built on the assumption
  that the limits were met.

> **What this does *not* excuse.** A limit that stops working altogether is still a defect —
> not because the resulting aggregate is mis-sized, but because a check that can never fire is
> indistinguishable from one that was never written. A wrapped byte total was that case: it
> made `totalBytes >= maxUncompressedByteSize` false permanently, so the size limit was dead
> rather than approximate. The aggregate remained bounded by item count and age, which is why
> the consequence was pressure rather than loss.

---

## 8. Data that is dropped

**Ruled** (2026-09-02, confirming a generalisation of R2).

Two dispositions are legitimate and must not be confused:

- **Dropped by receipt policy** — the decision is made at receive, and it is executed there
  rather than deferred to a later stage that may not run. A dropped feed's entries are
  removed from the zip so that `proxy.entries` continues to describe the whole of the zip
  beside it.
- **Given up on after retry** — quarantined per [§5](#5-give-up-and-replay), never deleted.

**Nothing else may discard data.** A component that cannot proceed must fail loudly and leave
the data where a recovery routine or an operator can find it. Silence is the failure mode most
often found at a seam.

---

## 9. Known divergences

Where the code does not currently honour a contract above; this table exists so the document can
be trusted as a statement of intent without being mistaken for a statement of fact.

| Contract | Divergence | Finding |
|---|---|---|
| §6 Commit points are durable | The file-store point is met; the queue points are met by the local queue under `proxyConfig.durability` ([queues.md §5.1](infrastructure/queues.md#51-local-queue)) | — |

---

## 10. Derived contracts — all resolved 2026-09-02

This section held five points that were **my inference** rather than a decision. They were put
to the owner as a set and all five resolved the same day. Kept as a record, because the
mechanism is the point: separating "we decided this" from "someone inferred this" turned five
silent assumptions into four confirmations and one correction — and the correction was to the
one I had been most confident about.

| § | Derived contract | Outcome |
|---|---|---|
| [3.1](#31-a-resolved-store-path-belongs-to-the-caller) | `resolve()` returns a caller-owned path | **Confirmed.** The S3 re-download cost is accepted: *"happy with the performance hit for now as long as the contract is clean"* |
| [2.3](#23-a-claim-is-released-by-its-holder-or-by-the-mode) | A lease must record its **owner** | **Corrected.** The requirement is **liveness** — a leased item must eventually become leasable by something else if its holder dies. Owner identity is a debugging aid, not a contract |
| [6](#6-durability) | The three fsync commit points | **Confirmed**, and configurable |
| [2.4](#24-give-up-is-bounded-and-lands-somewhere-visible-in-the-modes-own-infrastructure) | `maxDeliveryAttempts` honoured on every backend | **Confirmed**, then refined 2026-09-07: on SQS the bound is the redrive policy, not a proxy count |
| [7](#8-data-that-is-dropped) | Nothing else may discard data | **Confirmed** |

*The one question that was still open here — what R1 requires of an object store or a shared
mount — was ruled on 2026-09-02 and is now [§6.1](#61-durability-on-non-local-storage). Nothing
in this document is currently unresolved.*

---

## Maintaining this document

Three rules, drawn from why the existing documents drifted:

1. **State contracts, not procedures.** "Validates the store name, converts the URI, returns
   the path" describes an implementation and goes stale when it changes. "The caller may move
   or delete what it is given" is a contract and stays true until someone decides otherwise.
2. **Record the decision here when it is made.** Rulings taken during a piece of work belong
   in this file, not only in that work's tracking document — those are archived when the work
   finishes, and the constraint is then lost.
3. **Mark divergences rather than describing the current behaviour.** A contract the code
   does not yet meet is still the contract. Deleting it to match the code silently converts a
   known defect into intended behaviour.

# File stores

[← Design documentation](../README.md) · [Contracts](../contracts.md) · [Queues](queues.md)

**Status: target design, agreed 2026-09-07.** This describes the store layer as it is to be built.
It is the first layer of a contract-first rewrite, and it is deliberately smaller than what exists
today. [§9](#9-where-the-current-code-diverges) lists what the current implementation does
differently; [§10](#10-decisions) records the decisions taken and what each overturned.

---

## 1. What a file store is for

A file store holds **file groups**: the data the pipeline moves. A queue carries a small message
that names a file group; the store holds the group itself. That separation is what lets the queue
be a directory, SQS or Kafka without regard to payload size, and lets stages run in one process
or many.

A file group is a **directory tree** — today always `proxy.meta`, `proxy.zip`, `proxy.entries`,
though the store does not care: it holds trees, nested or not, and that is the whole data model
on every backend.

A file group in a store is **committed, complete and immutable**. There is no such thing as a
partially-present file group that a reader can see: either `resolve()` hands back the whole
thing, or it reports that there is nothing there.

A proxy has **several** stores, one per stage that writes — receive, split, aggregate — each
rooted somewhere sensible for that stage: its own directory on a filesystem, its
own prefix and possibly its own bucket on S3. Everything in this document is per store. Each has
its own root, its own writer root and counter, its own staging and its own sweep; they share
nothing but the mode, and a location names which store it belongs to.

## 2. The two modes

The proxy runs in one of two modes, and the mode is a property of the deployment, not of any
individual queue or store.

| | **Local** | **Shared** |
|---|---|---|
| Nodes | One | Many, and any of them may be removed for ever at any time |
| Queue | Local directory queue | SQS or Kafka |
| Store | Local filesystem | S3, or a filesystem every node mounts at the same path (Ceph, EFS, NFS) — a location is an absolute `file:` URI |
| Who recovers in-flight work | The same node, by restarting | Any node — the original need never return |
| A published location must be readable by | This node | Every node — until a consumer finishes with it |
| Durability of a commit | This process's `fsync` | The storage's own guarantee: an S3 acknowledgement, or the mount's semantics |
| Stale staging and orphans are cleared | At start-up, by the one process — and at no other time | By age — an S3 lifecycle rule, or the proxy's own scheduled task on a mount |

These are different systems with the same interface. The interface is the same because every
stage is written against it once; the modes are different because the failure model is
different. A local deployment has one process that can be killed and restarted; a shared
deployment has a message that may be handed to another node at any moment — after a GC pause,
a slow acknowledgement, a partition rebalance — with no failure having occurred at all, and a
node that was working a moment ago may have been scaled away and will never start again.

**The mode is stated, not inferred.** Every queue in a deployment is local or every queue is
distributed; every store is local or every store is shared. A local queue cannot carry a
reference to another node, and a distributed queue cannot carry a reference to a node-local
path, so a mixture is not a third mode: it is a misconfiguration, and it is refused at
start-up (see [§7](#7-configuration)).

## 3. The contract

What must always be true of a store, in every mode. Everything in
[§5](#5-design) exists to make one of these hold.

**C1. A committed file group is durable before `commit()` returns.** Durable by the
storage's own guarantee ([contracts.md §6.1](../contracts.md#61-durability-on-non-local-storage)):
forced to disk on a local filesystem; acknowledged by the object store; written to the mount on a
shared filesystem. The caller must not need to know which.

**C2. Completeness is proved by one thing that appears atomically, last**
([R10](../contracts.md#1-standing-rulings)). On a filesystem that is the rename that publishes
the directory; on S3 it is a marker object written after every other object has been
acknowledged. Nothing else — not the existence of a directory, not a partial listing — may be
read as completeness.

**C3. `resolve()` returns a path holding a complete file group that no one else is reading, or
throws `FileGroupNotFoundException`.** The path belongs to the caller
([contracts.md §3.1](../contracts.md#31-a-resolved-store-path-belongs-to-the-caller)): it may be
moved, deleted or published onward, and the store must not touch it afterwards. It is never an
empty or partial directory. "No one else is reading" is what the queue provides — a group has
exactly one reader, the holder of the one leased message that names it — so on a filesystem the
store may lend the committed path itself. Absence is a normal state, not a fault — see C5.

**C4. `delete()` is idempotent.** Deleting what is not there is success. A redelivered message
whose input has already gone must not fail on this call.

**C5. At the moment a location is published, its file group is readable by every node the mode
allows to consume it.** In local mode that is this node; in shared mode it is every node. The
location therefore carries everything needed to find the group — store name and a complete URI —
and nothing node-relative.

This is a guarantee about *publish time* only. The consumer deletes the input before it
acknowledges, so if the acknowledgement is lost — a crash, a visibility timeout, a rebalance — the
message is redelivered and the group it names is gone, **because the work was done**. A node that
resolves a message and finds nothing has not hit a fault; it has received proof that another
consumer finished, and it acknowledges (R12). A store's obligation is therefore to make the group
reachable *before* the location is handed out, and to report its later absence exactly — not to
keep it reachable.

**C6. Nothing the store leaves behind is ever the only copy of live work, and nothing depends on
a particular node returning to clear it.** Staging for a write that never committed is scratch: the
work is still claimable on a queue. A committed group that a crash left unreferenced is an orphan:
space, not data. Both are bounded and both are cleared — at start-up in local mode, where the one
process is the only writer; **by age** in shared mode, where the node that wrote them may never
exist again ([§5.5](#55-clearing-what-is-left-behind)). What the store must never do is clear
scratch that another live node is using, which is why a shared store's start-up clears nothing on
the shared storage.

**C7. Nothing depends on `close()`** ([R8](../contracts.md#1-standing-rulings)). Correctness
rests on `kill -9` and power loss being survivable. `close()` releases connections and nothing
more.

**C8. Housekeeping after a durable step never throws**
([contracts.md §3.2](../contracts.md#32-cleanup-after-a-durable-step-must-never-throw)). Once the
commit is durable, a failure to delete staging is logged, not raised: raising it would tell the
caller a successful commit had failed.

## 4. The interface

Five operations. Nothing else.

```java
public interface FileStore extends AutoCloseable {
    String getName();
    FileStoreWrite newWrite() throws IOException;
    Path resolve(FileStoreLocation location) throws IOException;   // C3
    void delete(FileStoreLocation location) throws IOException;    // C4
    HealthCheck.Result healthCheck();
}

public interface FileStoreWrite extends AutoCloseable {
    Path getPath();                                // a private directory to fill
    FileStoreLocation commit() throws IOException; // C1, C2 — returns the publishable location
    void close();                                  // discards the write if not committed
}

public record FileStoreLocation(String storeName, String uri) { }
```

`FileStoreLocation` is a store name and a URI. The scheme (`file:`, `s3:`) says which kind of
store wrote it, and the store name says which instance; a store rejects a location whose name or
scheme is not its own. There is no separate type field and no attribute map — both existed and
neither carried information the URI did not.

**What is not here, and why.** `newDeterministicWrite` is gone: no stage calls it, the pipeline
is at-least-once and accepts duplicates, and a store that offers replay-safe writes nobody uses
has to carry two extra handle classes per backend to do it. `isCommitted` is gone: a caller
holds the handle it asked for and knows whether it called `commit()`. If either is wanted later
it is added for a caller, not in anticipation of one
([R13](../contracts.md#1-standing-rulings)).

## 5. Design

Two implementations, because there are two kinds of storage: a filesystem, and an object store.
A shared filesystem is the filesystem implementation pointed at a mount; it is not a third class.
The store holds no node identity: nothing in it needs to know which node it is on.

### 5.1 File group ids and the writer root

File groups are numbered **sequentially** from an in-memory counter, and the number is laid out
as a nested path so that no directory ever holds more than 1000 entries — the scheme
`DirUtil.createPath` already implements and the file forward destination already relies on:

```
<writerRoot>/0/001/                  ← id 1
<writerRoot>/0/999/                  ← id 999
<writerRoot>/1/001/001000/           ← id 1,000
<writerRoot>/2/002/300/002300123/    ← id 2,300,123
```

Sequential ids are the cheapest thing to allocate (one `incrementAndGet`, no I/O), they sort, and
the nesting keeps directory listings fast for the life of a deployment. This was tested and chosen
deliberately, and the design keeps it.

What makes a per-process counter safe is **where it counts from** — the *writer root* — and that
differs by mode:

| Mode | Writer root | Counter at start-up |
|---|---|---|
| Local | `<root>` itself — one writer, no sub-directory | Re-established from the highest id present, **after** the orphan sweep ([§5.5](#55-clearing-what-is-left-behind)) |
| Shared | `<root>/<startId>/` — a fresh UUID **per process start** | Starts at zero. The directory is new, so there is nothing to scan and nothing to clash with |

So a local store has one numbered tree that survives restarts, and a shared store has one numbered
tree per process lifetime. Two nodes never share a writer root; one node restarting never reuses
one. No listing of other writers is ever needed, on a mount or on S3.

**A local store that has fully drained restarts its numbering at 1, and that is safe** — but only
because the orphan sweep that precedes the re-seed has just proved that nothing references any id
that is gone. A `failed/` message naming id 5 keeps `0/005` on disk and the maximum at 5 or more.
The counter is not persisted, and must not be: persisting it would be defending against a reuse
the sweep already rules out.

The location URI carries the full path including the writer root, so it is unique across the
cluster even though the number alone is not. The queue message carries no separate group id: its
`messageId` identifies the delivery, and the location identifies the group
([queues.md §10 D4](queues.md#10-decisions)).

### 5.2 Filesystem store

Used for local mode, and for shared mode on a mount.

Local mode — the writer root is `<root>`:

```
<root>/
├── 0/001/                     ← committed file group 1; presence IS completeness (C2)
│   ├── proxy.meta
│   ├── proxy.zip
│   └── proxy.entries
├── 1/001/001000/              ← file group 1000
│   └── <part>/...             ← a nested group is just a deeper tree
└── .staging/
    └── 001001/                ← a write in progress
```

Shared mode — one writer root per process start:

```
<root>/
├── 3f2a…/                     ← this process's writer root
│   ├── 0/001/
│   └── .staging/
│       └── 000002/
└── 9c41…/                     ← another node's, or this node's previous life
    └── 0/001/
```

**Write.** `newWrite()` takes the next id, creates `<writerRoot>/.staging/<id>/` and hands it back.
`commit()` forces what the durability mode requires, then renames the directory to
`DirUtil.createPath(writerRoot, id)` with `ATOMIC_MOVE`, creating the intermediate directories
first. Staging is always under the writer root, so the rename is always within one filesystem and
is always atomic — there is no cross-filesystem fallback in the store, because the store never
needs one. On a shared mount, staging on the mount is also one write rather than a local write
followed by a copy.

The create-then-rename pair is retried — see [§5.6](#56-intermediate-directories-create-and-cascade-delete-without-a-lock).

**Durability** ([§6](#6-durability)). In local mode, before the rename: `fsync` every file in the
group, then the group directory; after the rename: `fsync` the parent the rename landed in,
every numbering directory this commit created above it, **and the existing directory that gained
the entry naming the highest new one** - that entry is what publishes the whole new subtree, and
forcing the new directories alone says nothing about it; once per thousand groups that is more than
one. On a shared mount the mount's own semantics apply and the store does what its
`durability` setting says.

**Resolve.** The URI is `file:` plus the group's full path. The store checks the path is under
`<root>` and is a directory, and returns it. No copy: the group has one reader (C3), and `delete()`
being idempotent means a caller that moved the directory does no harm when the stage later deletes
it.

**Delete.** Remove the group directory recursively; not there is success. Then cascade: `rmdir`
each ancestor up to, but never including, the writer root, stopping at the first that is not empty
or cannot be removed. Best effort; a failure is ignored ([§5.6](#56-intermediate-directories-create-and-cascade-delete-without-a-lock)).

**Start-up, local mode only.** The store exposes one operation beyond the interface, used by the
runtime once at start-up: *delete every group that is not in this set*. After it, the store
re-establishes its counter with `DirUtil.getMaxDirId(root)`. See [§5.5](#55-clearing-what-is-left-behind).

### 5.3 S3 store

Used for shared mode on an object store.

```
s3://<bucket>/<prefix>/<startId>/<id>/proxy.meta
s3://<bucket>/<prefix>/<startId>/<id>/proxy.zip
s3://<bucket>/<prefix>/<startId>/<id>/proxy.entries
s3://<bucket>/<prefix>/<startId>/<id>/<part>/proxy.zip   ← nested groups are just longer keys
s3://<bucket>/<prefix>/<startId>/<id>/.complete          ← marker, PUT last (C2)
```

The writer root is `<prefix>/<startId>/`, fresh per process start, exactly as on a shared mount.
S3 has no directories to fill, so the id is used flat (`000000000001`) rather than nested.

Local scratch, on this node's own disk:

```
<localRoot>/
├── staging/<id>/               ← files being written before upload
└── resolve/<startId>-<id>-<n>/ ← a download handed to a caller
```

**Write.** `newWrite()` takes the next id and creates `staging/<id>/`. `commit()` walks the tree,
PUTs every file under `<prefix>/<startId>/<id>/` preserving its relative path, and only when every
PUT has returned PUTs the marker. The marker's own PUT is atomic, so a crash anywhere before it leaves objects with
no marker, which no reader will accept. Then the staging directory is deleted, and a failure to
delete it is logged (C8).

**Durability.** An acknowledged PUT is durable. There is nothing to force.

**Resolve.** List the group's prefix. No marker → `FileGroupNotFoundException`. Otherwise
download every object except the marker into a fresh `resolve/` directory, preserving relative
paths, and hand that directory back. This is the one place a copy is unavoidable: there is no local path
to lend.

**Delete.** Delete the marker first, then every remaining object under the prefix. A crash between
the two leaves objects with no marker — an orphan, not a phantom group.

**Nested groups are supported.** A key is a string; a deeper tree is a longer key. The old
refusal to upload a nested group, and the ruling that pre-aggregate could not use S3, both
followed from an implementation that flattened keys; both are retired
([§10 D2](#10-decisions)).

### 5.4 Registry and factory

A `FileStoreRegistry` maps store names to instances so a stage can resolve any message's location
without holding every store. A `FileStoreFactory` builds instances from configuration and caches
them by name. Neither has any logic beyond that.

### 5.5 Clearing what is left behind

Two kinds of residue, from the interruption points in [§8](#8-how-the-store-and-queue-meet-the-at-least-once-contract-together):
**stale staging** (a write that never committed) and **orphans** (a committed group that a crash
left unreferenced). Neither is ever the only copy of live work (C6). The mechanism differs by
mode, because who can be relied on to exist differs by mode.

| Where | Cleared by | When |
|---|---|---|
| Local filesystem store, `<root>/.staging/` | The store, at start-up | The one process is the only writer, so everything in staging is dead |
| Local filesystem store, orphaned `<root>/<groupId>/` | The runtime, at start-up | Every group that no message in any local queue names |
| Shared filesystem store, every writer root's `.staging/` **and** orphaned groups | **The proxy's scheduled task**, run by every node | Anything older than the store's `orphanAge`, in every writer root including dead ones; a writer root left empty goes too. Deletes are idempotent; two nodes sweeping at once is harmless |
| S3 store, objects under `<prefix>/` | **An S3 lifecycle rule** the operator configures | Expire objects older than the same bound. The proxy documents the rule; it does not run one |
| S3 store, node-local `staging/` and `resolve/` | The store, at start-up | Node-local disk has one process on it. On an ephemeral node that never restarts, the disk goes with it |

**Local mode: the sweep is exact, and it is the only cleanup there is.** At start-up nothing is
running, so the set of groups that are live is precisely the set named by a message in some local
queue — `pending/`, `in-flight/` and `failed/` together, since a quarantined message is replayable
and its group must stay. The runtime reads every local queue, collects the locations, and tells
each local store to delete every group not in that set and every empty intermediate, after which
the store re-establishes its counter from the highest id left. That is the one moment the store and the queues can be compared
without a race, and it is cheap: one listing per queue, one per store. No age threshold, no
scheduled task, and nothing that runs while the pipeline does. This is a
runtime operation over both layers, not something the store can do alone — which is why it is a
method on the filesystem store and not on the `FileStore` interface.

**Shared mode: the age bound is the whole safety argument.** A group older than `orphanAge` is deleted whether
or not something still names it, so `orphanAge` must exceed the longest a live group can
legitimately wait: a queue backlog, a paused forwarder, the retry window. The validator enforces
the two bounds it can compute — `orphanAge` must be **at least twice** the forward stage's
`maxRetryAge` and at least twice `aggregationFrequency` — and the task refuses to run if
`orphanAge` is unset. Twice, because one window covers the wait and the second covers the one
redelivery shared mode exists to survive: a node dying mid-aggregate and another re-holding the
input for a fresh window, or the final forward attempt at `maxRetryAge` racing the hourly sweep
during its give-up. Two node deaths in a row on one input are not covered, and that is accepted.
Backlog age is the operator's judgement, exactly as it is for an S3 lifecycle rule — set the rule
with the same margin.

**The sweep cannot tell an orphan from live work, and R12 makes the loss silent** — the
redelivery resolves to nothing and is acknowledged as done. So the sweep counts what it deletes,
and the count of groups is exported per store as
`stroom.proxy.pipeline.fileStore.<name>.swept.groups` (staging apart, as `swept.staging`, since
deleting staging is always safe). It should stay near zero: orphans only come from a crash between
commit and publish. A steady rate under load is the sweep reaching live work and `orphanAge` being
too short for the backlog.

### 5.6 Intermediate directories: create and cascade-delete without a lock

With sequential ids every thousand groups leaves one empty intermediate behind, and a writer root
lives as long as its process — so a busy node would leave hundreds of thousands a day if nothing
removed them. The consumer that deletes a group therefore cascades upward, and the writer that
commits one creates downward, into the same tree at the same time, from different nodes.

This used to be serialised with a directory lock. It is not any more, in either mode, because a
lock cannot be made to work on shared storage — a lock file on a mount is exactly the kind of state
that goes stale when the node holding it is scaled away — and a mechanism that is correct without
one is simpler than two mechanisms.

**It is correct without one because every primitive is already atomic.** `mkdir` is idempotent;
`rmdir` succeeds only on an empty directory; `rename` fails cleanly if its parent has gone. The
only non-atomic thing in the store is the creator's *pair* — create the intermediates, then rename
the staging directory into place — and that pair is retried:

```
for attempt in 1..N:
    createDirectories(parent)          // idempotent; recreates whatever a cascade removed
    try:  move(staging, leaf, ATOMIC_MOVE); return
    catch NoSuchFileException:  continue   // a cascade took the parent between the two calls
fail the commit                         // the message is redelivered; nothing is lost
```

Every interleaving lands on one of four outcomes, and none loses a group or leaves a phantom:

| Cascade removes the intermediate… | Result |
|---|---|
| before the creator's `mkdir` | recreated; rename succeeds |
| between `mkdir` and `rename` | rename throws; retry recreates; succeeds |
| after the creator's `rename` | `rmdir` fails non-empty; cascade stops |
| and climbs to the grandparent after the creator recreated below it | `rmdir` fails non-empty; cascade stops |

The retry fires at most once per contended intermediate, so *N* is small; ten is generous. One
detail found by running the race rather than reasoning about it: the JDK's `createDirectories` is
itself create-then-check, so a directory removed between its two steps surfaces as "already
exists", and the retry absorbs that too.

**The cascade is best effort, and it stops below the writer root.** A consumer removes what it
emptied and ignores any failure — non-empty, permission, a transient error on a mount. It never
removes another process's writer root: an empty writer root is the age sweep's, once it is old
enough. What the cascade misses is also the sweep's: in local mode the start-up sweep removes
empty intermediates; in shared mode the age sweep removes empty intermediates and empty writer
roots older than `orphanAge`. Both sweeps are covered by the same retry on the creator's side —
a sweep is just a cascade that happens later.

## 6. Durability

[Contracts §6](../contracts.md#6-durability) states the invariant: at every interruption point,
either the input is still claimable or the output is durable. The store's half of that is C1.

| Store | Default `durability` | What `commit()` forces |
|---|---|---|
| `LOCAL_FILESYSTEM` | `FULL` | Every file, then the group directory, then `<root>` after the rename |
| `SHARED_FILESYSTEM` | `FILESYSTEM` | Nothing — the mount's own semantics are the guarantee, and a local `fsync` says nothing about them |
| `S3` | — | Nothing — the acknowledgement is the durability point |

The default follows the store type; an operator who sets it explicitly is stating that they know
their storage. `QUEUE_ONLY` remains available for a local filesystem where the operator accepts
the mount's ordering for payloads and wants the queue message forced.

## 7. Configuration

The mode is stated once, and the validator checks the shape against it.

```yaml
pipeline:
  mode: SHARED                      # or LOCAL
  fileStores:
    receiveStore:
      type: S3
      bucket: proxy
      keyPrefix: receive/
    aggregateStore:
      type: SHARED_FILESYSTEM       # a mount every node sees at this same path; the operator asserts it
      path: /mnt/proxy/aggregate
      orphanAge: P7D                # required for SHARED_FILESYSTEM; must exceed maxRetryAge
```

| `mode` | Every queue must be | Every store must be |
|---|---|---|
| `LOCAL` | `LOCAL_FILESYSTEM` | `LOCAL_FILESYSTEM` |
| `SHARED` | `SQS` or `KAFKA` | `S3` or `SHARED_FILESYSTEM` |

A store type that does not fit the mode is a **validation error** — the proxy does not start. This
replaces the warning that says a local store "must be on storage every node can reach — the proxy
cannot verify that". It still cannot; what changes is that the operator now states it, by choosing
`SHARED_FILESYSTEM`, instead of the proxy guessing from a path and warning either way.

`LOCAL_FILESYSTEM` and `SHARED_FILESYSTEM` are the same implementation. The type exists so the
configuration says what the storage is, the validator can check it against the mode, the
durability default can follow it, and the age-based cleanup runs only where it is the mechanism.

## 8. How the store and queue meet the at-least-once contract together

Neither is sufficient alone, and this is the section to read when changing either.

The stage protocol is fixed ([architecture.md §5](../architecture.md#5-ownership-transfer-protocol)):
resolve input → work → **commit output** → **publish message** → **delete input** → **acknowledge**.
The store makes the output durable and reachable before the message exists, and later reports its
absence exactly; the queue keeps the message claimable until it is acknowledged. Between those two
sits the one window the design accepts rather than closes: the input is deleted *before* the
acknowledgement, so a lost acknowledgement redelivers a message whose data is gone. That is not a
loss — it is the last row of the table below, and it is the price of never acknowledging first.

| Interrupted after | What is true | Recovery | Mode difference |
|---|---|---|---|
| resolve, before commit | Input still claimable. Output is in staging, unreferenced | Message redelivered; work repeated; staging is stale | Local: cleared at next start. Shared: a *different* node repeats the work; the stale staging ages out |
| commit, before publish | Output durable, referenced by nothing | As above. The first output is an orphan | Local: cleared at next start, as unreferenced by any queue. Shared: ages out |
| publish, before delete | Two messages name two outputs; one input still present | Redelivered; work repeated; a **duplicate** goes downstream. Accepted under R1 | Same |
| delete, before acknowledge | Output published; input gone | Redelivered; `resolve()` throws `FileGroupNotFoundException`; consumer **acknowledges** (R12) | In shared mode this needs no crash: a visibility timeout or rebalance produces it in normal operation |

Three things the table depends on, each of which is one of the contracts above:

- **The store reports absence exactly (C2, C3).** If it ever returned an empty directory for a
  group that had been deleted, the last row would silently acknowledge live work. If it ever
  returned a partial group, the third row would forward half a file group.
- **The location is resolvable from wherever the message lands (C5).** In shared mode the
  redelivery goes to any node; a node-local path in the message is data that node cannot reach
  and a message that can never succeed.
- **Nothing is cleared that a live node still needs (C6).** The first row's staging is the *only*
  copy of work in progress on the node doing it. In shared mode other nodes are running while this
  one starts, so start-up clears nothing shared, and the age bound is what keeps the sweep away
  from live work.

## 9. What the implementation before this design did

Kept for the record; none of it is in the tree.

| Design | Previous code |
|---|---|
| C6: a shared store's start-up clears nothing shared; residue ages out | Both stores sweep the **parent** of every writer's staging on start-up. On a shared mount that deletes another node's in-progress writes. The comment says so: "assumes one process per data directory" — the local mode's assumption applied to the shared one. There is no age-based cleanup anywhere, and no start-up sweep of orphaned groups against the queues |
| §5.1: sequential ids in `DirUtil`'s nested layout; writer root is `<root>` locally and `<root>/<startId>/` shared; shared counters start at zero | Ids are flat ten-digit names (`0000000001`) in a directory that grows without bound; **every** mode uses a per-construction UUID writer directory, so a local store's numbering restarts under a new directory each boot and the orphan sweep has nothing to seed from; the S3 store lists its own fresh prefix at start-up to seed a counter that is necessarily zero; and `nextStablePath` carries a skip loop for a collision the fresh directory makes impossible |
| §5.3: nested groups on S3 | `uploadDirectory` refuses a nested tree; R3 rules S3 out for `preAggregateStore` and the validator enforces it, so an S3 deployment must also mount a shared filesystem |
| §5.2: `resolve()` lends the committed path | The local store already does this; S3 copies, as it must. No change in behaviour, only in what is written down |
| §4: no `newDeterministicWrite` | Present on the interface, with three handle classes in the local store and two in S3, and no caller |
| §4: location is name + URI | `locationType` and `attributes` fields, used only to check what the URI scheme already says |
| §7: mode is stated | Inferred per store; a warning the validator itself says it cannot check |
| §5.2: staging under root, rename is always atomic | Commit goes through `DirUtil.moveDir`, which carries a cross-filesystem copy-and-rename path the store cannot reach if staging is under root |
| §5.5: `orphanAge` validated against `maxRetryAge` | No such property; no such check |
| §5.6: cascade-delete with a retried create, no lock | Intermediates are never cascaded by the stores at all — flat naming means there are none — and the directory-lock mechanism that used to manage cascading elsewhere is what this replaces |

## 10. Decisions

Taken 2026-09-07. Each overturns or refines something that was written down, and is recorded so
the next reader knows it was decided rather than drifted into.

| | Decision | What it overturns |
|---|---|---|
| **D1** | `pipeline.mode` is explicit; `SHARED_FILESYSTEM` is a store type; a queue or store that does not fit the mode is a validation **error** | The `EXTERNAL_QUEUE_REQUIRES_SHARED_FILE_STORE` warning, and shareability inferred from a path |
| **D2** | S3 holds nested groups; `preAggregateStore` may be S3; **R3 is retired** | R3, its validator rule, and the shared-mount requirement in an otherwise S3 deployment |
| **D3** | **Sequential ids stay**, in `DirUtil`'s nested layout: cheap to allocate, sortable, and no directory over 1000 entries — tested and chosen deliberately. What changes is the writer root: `<root>` in local mode, seeded from the highest id after the orphan sweep; `<root>/<startId>/` per process start in shared mode, starting at zero with nothing to scan | My proposal of random time-ordered ids in a flat namespace — withdrawn the same day. It solved the cross-node clash by giving up the ordering and the nesting, when moving the writer root solves it and keeps both |
| **D4** | **The store holds no node identity.** In shared mode a node may be removed for ever (a scaled-down pod), so nothing may depend on it returning to clear what it left. Local mode clears everything — staging and orphans — at start-up and never at runtime; shared mode clears by age | The proposal to construct stores with `ProxyId` and clear per-node staging at start-up — withdrawn on the day it was made, for the reason above |
| **D5** | `newDeterministicWrite` and `isCommitted` are dropped | Both, and five handle classes |
| **D6** | On a filesystem, `resolve()` lends the committed path; S3 copies because it must. C3's "private" means "no other reader", which the queue lease guarantees | The reading of contracts §3.1 as "always a copy" |
| **D7** | Orphans on S3 are cleared by an **S3 lifecycle rule** the operator configures; the proxy documents the rule and runs nothing. Orphans in a **local** store are deleted at start-up by comparing the store against the local queues | future-work §10's proposed scanner, for S3 and for local |
| **D8** | Orphans and stale staging on a **shared filesystem** are cleared by **the proxy's own scheduled task**, run on every node, deleting anything under the store older than `orphanAge`. It refuses to run if `orphanAge` is unset | future-work §10's proposal, now decided for the shared-filesystem case |
| **D9** | The validator enforces `orphanAge ≥ 2 × maxRetryAge` and `≥ 2 × aggregationFrequency` — the two bounds it can compute, with the margin covering one redelivery (*raised to `≥ 2×` on 2026-09-15*). Backlog age remains the operator's judgement, and the sweep's group count is exported so that a wrong judgement is visible | Nothing; new |
| **D10** | Durability defaults follow the store type: `FULL` for local, `FILESYSTEM` for a shared mount, nothing for S3 | A single default for every store |
| **D11** | Empty intermediate directories are cascade-deleted by the consumer, best effort and ignoring failure; the creator retries its create-then-rename pair. **No directory lock in either mode** | The directory-lock mechanism; and the alternative of leaving intermediates to accumulate |

Contracts and architecture documents still carry R3 and the per-store shareability wording; they
are updated when this design's code lands, not before, so that they continue to describe what is
built.

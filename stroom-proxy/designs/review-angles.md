# Review angles

[← Design documentation](README.md)

**What this is.** The set of readings a review of proxy code is made of — kept from the 2026
audit as its one reusable method. It is a checklist for reviewing a layer once it is rewritten to
its contract, and for auditing one's own fix before calling it done. The audit's findings were
retired with the code they described, or became contract tests in the tree; the archive of them
has been deleted. "Ledger" below means whatever record the review in hand keeps.

The set of independent readings an audit of the proxy is made of. An angle is
defined by **the act of reading**, not by a topic. That distinction is the whole
point: two auditors given "correctness" and "robustness" read the same diff the
same way and file the same list twice, whereas an auditor told *read every line in
order* and an auditor told *assume every call throws* perform genuinely different
work and cannot collide.

Thirteen angles in three groups. The weighting is deliberate: eight of the
thirteen hunt defects, four judge fitness, one judges operability. An audit that
splits its effort evenly between "is it correct" and "is it pretty" returns
mostly polish — the failure mode this set is shaped to avoid.

Each angle below states: **the act** (what the auditor literally does), **what it
finds**, **the questions**, and **the boundary** (what it must *not* report, so
the angles stay disjoint and the findings stay deduplicated).

---

## Group A — Defect angles

### A1. Line-by-line

**The act.** Read every line of the assigned files in source order, in isolation,
assuming nothing about the rest of the system. No jumping to callers; the file
must make sense on its own terms.

**What it finds.** Inverted conditions, off-by-one, wrong variable, a result
computed and dropped, a branch that cannot be reached, a `null` that can arrive,
integer overflow, a loop that does not advance, string/charset assumptions.

**The questions.** For each statement: what values can reach here? Is the
operator the intended one? Is the return value used? Can this loop fail to make
progress? Does every field this method reads have a defined value at this point?

**The boundary.** Do not report anything requiring a second file to see — that is
A5. Do not report style — that is B2.

### A2. Failure paths and resources

**The act.** Assume every call that *can* throw *does* throw, at every point.
Walk each method a second time along its exception edges. Then find every
resource acquisition — file handle, stream, lock, temp file, LMDB txn, thread,
HTTP connection, queue lease — and prove it is released on every path.

**What it finds.** Leaks, temp files orphaned by a mid-operation throw, state
left half-updated, `catch` blocks that swallow, `InterruptedException` discarded
without re-setting the flag, `finally` blocks that themselves throw and mask the
original, close() that is not idempotent, error paths that log and continue with
a broken invariant.

**The questions.** If this throws here, what is left behind on disk, in memory,
in the queue? Does the caller learn? Is the operation retryable after this
failure, and does retrying do the right thing? Is the exception's message enough
to diagnose it from a log alone?

**The boundary.** Concurrency-only failures belong to A3; crash-only failures
belong to A4. This angle assumes a single thread and a live process.

*Standing exemption:* catch/log-at-debug/rethrow is not a finding — the proxy's
exception mappers put the message in the response body, so the caller is informed.

### A3. Concurrency and lifecycle

**The act.** Two passes. First, for every mutable field and every shared
structure, tabulate: which threads write it, which read it, under which lock or
happens-before edge. Second, walk the component's lifecycle — construction,
start, running, stop, close, and start-again — and ask what a caller arriving in
each state gets.

**What it finds.** Unsynchronised shared mutable state, check-then-act races,
non-atomic compound updates, locks taken in inconsistent orders, work submitted
to an executor that is already shutting down, a stop that returns before its
threads have, a shutdown ordering that lets a producer outlive its consumer,
lost wakeups, unbounded queues with no backpressure, `volatile` doing a job that
needs a lock.

**The questions.** Which thread runs this? What else runs at the same time? What
happens if two of these run concurrently on the same file group, lease, or
directory? During shutdown, who stops first, and is that ordering enforced or
incidental? Is an in-flight item's ownership unambiguous at every instant?

**The boundary.** This is the highest-yield angle in this codebase and the one
line-by-line reading structurally cannot reach — a race is invisible in a single
reading of a single method.

### A4. Crash and restart

**The act.** For each durable operation, walk it step by step and kill the
process between every pair of steps. At each kill point, describe exactly what
the next start-up finds and what it will do.

**What it finds.** Duplicate delivery on replay, data loss, orphaned temp or
in-flight directories that nothing sweeps, leases that never expire, messages
acknowledged before their effect is durable (or made durable and then never
acknowledged), non-idempotent replay, ordering assumptions that a restart
violates, `fsync`/rename ordering that does not survive power loss.

**The questions.** Is the effect durable before the acknowledgement? If this is
replayed from the start, is the result the same? Who cleans up what the crash
left? Can two proxies — or one proxy restarted — both believe they own the same
file group?

**The boundary.** This is the defining risk of a store-and-forward proxy and
deserves its own reading. Where a known non-idempotency is already a recorded
decision, cite the decision rather than re-filing it.

### A5. Cross-file contract trace

**The act.** For each public type and method, find every caller and every
implementor, and check the contract at each site. Follow the call graph outward
until the value's fate is known.

**What it finds.** An invariant asserted in one class and violated by a caller in
another; a nullability, ordering, threading, or units contract stated in javadoc
and broken at a call site; an interface whose implementations disagree about what
"returns empty" means; ownership transfers where both sides believe they own the
object, or neither does; a parameter that is validated in one path and not in
the parallel one.

**The questions.** Who calls this, and does each caller satisfy the precondition?
Who implements this, and does each honour the postcondition? Where does this
object's ownership pass, and is that transfer total? If this returns
`Optional.empty()`, does every caller handle it, or does one treat it as
impossible?

**The boundary.** Findings visible in a single file belong to A1.

### A6. Removed and changed behaviour

**The act.** Read the branch diff for what is *gone* or *different*, not for what
is new: deleted methods, deleted branches, narrowed conditions, loosened
validation, a default that moved, a call that is no longer made.

**What it finds.** Behaviour dropped in a refactor with nothing replacing it;
guards removed as "dead" that were not; a stricter check quietly relaxed; a code
path whose only caller was deleted, leaving behaviour unreachable but still
documented.

**The questions.** For each removal: what called this, and what do they do now?
Was it dead, or merely unreferenced from the paths the author was looking at?
Was the removed check ever load-bearing?

**The boundary.** Diff-scoped by definition; skip on whole-tree sweeps of
unchanged files.

### A7. Boundaries and the input domain

**The act.** For every input — wire data, config value, file name, header, path,
size, count — enumerate the domain and probe its edges. Assume the input is
hostile, because on the receive path it is.

**What it finds.** Empty and zero and one and max; sizes that overflow `int`;
malformed UTF-8; a zip with no entries, one entry, a hostile entry name, or an
entry that expands beyond the disk; path traversal in a feed or type name;
absent, blank, or duplicated headers; clock values that go backwards; unbounded
allocation driven by an attacker-supplied count.

**The questions.** What is the largest, smallest, and emptiest value that can
reach here? What does a caller who wants to hurt us send? Is size validated
before it is allocated? Is a name from the wire ever used to build a path?

**The boundary.** Report the input that breaks it, not the general absence of
validation.

### A8. Backend parity

**The act.** For each abstraction with more than one implementation —
`FileGroupQueue` over local/Kafka/SQS, the file stores over local/S3, the
forwarders — read the implementations *side by side against the interface
contract*, one contract clause at a time.

**What it finds.** The classic "works on local, breaks on Kafka": one backend's
`next()` blocks and another's returns immediately; delivery guarantees that
differ silently (at-least-once here, at-most-once there); ordering promised by
one and not the others; error and retry semantics that diverge; a lease or
visibility timeout that only one implementation honours; health checks that mean
different things; configuration validated for one backend only.

**The questions.** Take each sentence of the interface's contract: does every
implementation satisfy it? Where they must differ, is the difference documented
at the interface, and does every caller cope with both?

**The boundary.** Differences that are documented, deliberate, and handled by
callers are not findings — say so once and move on.

---

## Group B — Fitness angles

### B1. Design fit (altitude, reuse, simplification)

**The act.** One reading that asks whether the code is in the right place, said
once, and no more complicated than the problem.

**What it finds.** Logic at the wrong altitude — a stage doing a store's job, a
handler reaching past its abstraction; the same logic implemented two or three
times; a hand-rolled version of something `stroom-util` or the JDK already
provides; indirection with a single implementation and no prospect of a second;
configuration knobs nothing reads; dead vocabulary left from an earlier shape of
the design.

**The questions.** Should this code live in this class at all? Has this been
written elsewhere in the proxy? Is there a simpler formulation with the same
behaviour? What would this look like written from a blank page today?

**The boundary.** Deletion is the preferred fix for unused vocabulary; do not
propose wiring something up until a case demands it. Do not propose
architectural change here — a finding that requires redesign is a *decision*,
not a fix.

### B2. Conventions and naming

**The act.** Read for the house style of the surrounding code: naming, package
placement, logging levels and their message shapes, null and `Optional` idiom,
javadoc presence on public API, builder and immutability patterns.

**What it finds.** Names that lie about what a thing does; a class in the wrong
package; `LOGGER.error` for an expected condition or `debug` for a real one; log
messages without the identifiers needed to correlate them; inconsistent idiom
between neighbouring classes; public API with no javadoc.

**The boundary.** Style only. Anything that changes behaviour belongs to Group A.

### B3. Doc-versus-code drift

**The act.** Read `designs/` and the javadoc *as a specification*, then check the
code against it, clause by clause. Both directions are findings: documentation
that is wrong, and behaviour significant enough that its absence from the design
docs is itself the defect.

**What it finds.** A design document describing an earlier shape of the code; a
javadoc contract the implementation does not honour (a lying contract on a
correctness-critical method is a high-severity finding, not a low one, because
it is what the next reader will trust); a mermaid diagram missing a class or an
edge; an operations page naming a config key that no longer exists.

**The questions.** Is every sentence here still true? Is every promise kept? Does
the design tree describe the code as it is, or as it was planned?

**The boundary.** Comments carrying performance rationale or known-issue context
are **kept**, always — narration and history noise goes.

### B4. Test adequacy

**The act.** For each behaviour worth having, ask whether a test pins it — and
for each recent fix, ask whether the test would actually have *failed* before the
fix. Then read the tests themselves as production code.

**What it finds.** Fixes landed unpinned; tests that assert something weaker than
the behaviour they claim to cover; tests that pass for the wrong reason; tests
depending on wall-clock timing, sleeps, or iteration order; shared mutable state
between tests; a test that cannot fail. Also the inverse: behaviour so
load-bearing that its absence from the suite is a finding on its own.

**The questions.** Which assertion would break if this line were reverted? Does
this test exercise the failure path, or only the happy one? Is this test
deterministic on a loaded machine?

**The boundary.** Tests are read as *evidence* throughout the audit; this angle
is where findings about the tests themselves are filed.

---

## Group C — Operability angle

### C1. Configuration and observability

**The act.** Enumerate every configuration property and every signal the proxy
emits. For each property: default, validation, effect at its extremes, and
whether the shipped/expected YAML and the operations docs agree. For each
failure the other angles found: could an operator diagnose it from the logs,
metrics, and health checks alone?

**What it finds.** Properties with no validation and a hostile default; a value
that silently does nothing; validation that exists for one backend only;
`proxy-expected.yaml` out of step with the config classes; failures that produce
no signal, or a health check that stays green while the pipeline is stalled;
metrics that cannot distinguish "idle" from "stuck".

**The questions.** What does this property do at 0, at 1, at `Integer.MAX_VALUE`,
and unset? If this component fails in production at 3am, what does the operator
see? Which of the findings from A2, A3, and A4 would be invisible?

---

## Method rules

These bind the audit as much as the angles do.

**Verify before you file.** Every finding is re-read against the code — and
reproduced at runtime where it can be — before it is written down. A finding that
does not survive verification is recorded as **rejected**, with what refuted it.
An audit whose findings are all "plausible" is worth less than a shorter one
whose findings are all real.

**Fixes are a separate act.** The audit produces the ledger. Fixing happens
afterwards, against the ledger, so that findings are not quietly reshaped by the
convenience of fixing them.

**Judgement calls become decisions, not fixes.** A finding whose resolution
depends on intent — which of two behaviours is wanted, whether a cost is
acceptable — is filed as a **decision** for the owner to rule on, and stays
unresolved until ruled.

**Pinned behaviour is out of scope.** Behaviour fixed by an existing ruling,
recorded design decision, or deliberate divergence is excluded by brief. Cite the
ruling; do not re-litigate it. (The additive-merge non-idempotency is one such:
recorded, deferred, not a finding.)

**Every fix is pinned.** A defect fixed without a test that would have failed
before it is not finished. A fix whose reproduction cannot be pinned says so
explicitly.

**Every fix is audited before it is called done.** A fix is a change like any
other, so **A6** is run against it — and A2 and A3 where it touches failure paths
or concurrency — *before* the plan item is marked done, not on request. Three
fixes in the original audit introduced a defect of the family they were closing,
every one of them with a green suite, and every one of them found by this step. A green suite is not the
gate; the audit is. Findings from it are filed like any other, against the fix.

**Disjoint ownership when running in parallel.** Auditors on the same angle take
disjoint file sets; auditors on the same files take different angles. Overlap is
for cross-checking, and is deliberate when it happens.

**Severity means impact, not effort.**

| Severity | Meaning |
|---|---|
| **CRITICAL** | Data loss, duplication, or corruption; a security hole. |
| **HIGH** | Wrong behaviour on a reachable path; a stall or leak that accumulates. |
| **MED** | Wrong behaviour on an unlikely path; a contract or parity violation not yet reachable; a misleading contract on critical code. |
| **LOW** | Hygiene, naming, docs, dead code, test polish. |

**Ledger statuses.** `pending` (verified, awaiting fix) · `fixed` (with the
commit) · `decision` (needs a ruling) · `rejected` (did not survive
verification, with the refutation).

**No silent caps.** If the audit bounds its own coverage — files skipped, a
generated package excluded, an angle not run — the ledger says so, in the
coverage table. An audit that quietly covers 80% reads as if it covered all of it.

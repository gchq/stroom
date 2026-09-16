# Execution

[← Design documentation](../README.md) · [Contracts](../contracts.md) · [Queues](queues.md) · [Runtime](runtime.md)

**Status: target design, agreed 2026-09-08.** This describes how the proxy runs its
work - threads, loops, schedules, start and stop - as it is to be built. [§7](#7-what-the-implementation-before-this-design-did)
records what the previous implementation did; [§8](#8-decisions) records the decisions taken.

---

## 1. What execution is for

Everything the proxy does in the background is one of two shapes of work:

- **A loop.** *N* threads each run a task again and again; the task does its own waiting - a
  stage consumer waits on its queue - and reports whether it processed something, found nothing,
  or failed. The stage consumers, each forward destination's loop when the stage fans out, and the
  event store's forwarder are loops.
- **A schedule.** One thread runs a task at a fixed delay. The directory scanner, the SQS
  connector's poll, the event store's roll, a destination's liveness check, the cache refresh and
  the shared-store sweep are schedules.

Execution exists to run both with threads the proxy owns, start them in an order where a consumer
is always ready before its producer, stop them in an order where a producer always stops before
its consumer, never hang doing either, and never let a thread die without saying so. It is one
mechanism, not three, because every one of those properties was got wrong at least once by having
the pipeline's loops, the handlers' loops and the handlers' schedules each own their own threads.

Execution is per process. The deployment mode ([file-stores.md §2](file-stores.md#2-the-two-modes))
changes what a process is configured to run, not how it runs it. A process configured for
instant forwarding runs no pipeline at all.

## 2. The contract

**E1. Every thread the proxy starts is registered, named, and stopped by the registry.** There
is one `WorkRegistry`; a component that needs background work registers a loop or a schedule
with it, and never creates an executor of its own. A resource that owns threads for its own
internal purposes - an SQS queue's visibility heartbeat, a Kafka client's network threads - is
closed by its owner's `close()`, which the registry's stop sequence reaches.

**E2. Start and stop are ordered by the flow of data, not by the order of registration.** Every
registration names a *phase*. Stop runs the phases in data-flow order - sources first, then each
stage from upstream to downstream, then egress, then housekeeping - so a producer has stopped
before its consumer is asked to. Start runs them in reverse, so a consumer is draining before its
producer feeds it. Within a phase, registration order.

**E3. Stop is bounded.** Every loop and schedule is asked to stop, waited for up to a bounded
time, interrupted if it has not, waited a short grace, and then abandoned with an `ERROR` naming
it. Nothing correct depends on stop completing (R8): a task interrupted mid-item leaves the item
to its queue's claim release ([queues.md Q4](queues.md#3-the-contract)).

**E4. No thread dies silently.** A task that throws anything - `Error` included - is logged and
its thread continues after an error backoff. The number of live threads in each loop is observable
and is part of health: a loop with fewer live threads than it was given is unhealthy.

**E5. Failure does not spin, and nothing waits twice.** A loop task reports one of three
outcomes. *Processed*: loop again at once. *Nothing*: loop again at once too - the task has
already waited, on its queue or its poll, and the loop adds no sleep of its own. *Failed*: wait
before the next attempt, doubling per consecutive failure on that thread up to a cap, reset by the
next success. A throw waits a fixed error backoff.

**E6. A loop can be paused.** Pausing lets running tasks finish and starts no new ones; resuming
starts them again. This is how a forward destination stops sending to a downstream its liveness
check has found dead. Stop resumes a paused loop first, or it could never finish.

**E7. A component the pipeline depends on stops after the pipeline.** Anything a stage calls into
while processing - the feed-status service, the token refresh manager - either registers its own
background work in the housekeeping phase (the feed-status refresh does), or is started and stopped
by `ProxyLifecycle` itself, before the registry starts and after it has stopped (the token refresh
manager is). It is not a Dropwizard `Managed` of its own:
the proxy registers those in alphabetical order of class name, which is no order at all. Stopping
a dependency first is how a refresh submits to a dead executor and latches a control off for ever.

## 3. The interface

```java
public interface LoopTask {
    /** Do one unit of work, waiting inside if there is nothing to do yet. */
    Outcome run() throws Exception;

    /** A blocking runnable whose every return is work done. */
    static LoopTask ofRunnable(Runnable runnable);

    enum Outcome { PROCESSED, NOTHING, FAILED }
}

@Singleton
public class WorkRegistry {
    WorkRegistry();                                    // Backoff.DEFAULT, 30 s stop timeout
    WorkRegistry(Backoff backoff, Duration stopTimeout);  // tests

    Loop loop(String name, Phase phase, int threads, LoopTask task);
    void schedule(String name, Phase phase, Duration delay, Runnable task);
    void start();          // phases in reverse order; idempotent
    void stop();           // phases in order; bounded; never throws; idempotent
    boolean isStarted();
    boolean isShuttingDown();
    List<Loop> loops();    // for health

    record Backoff(Duration failure, Duration maxFailure, Duration error) { static Backoff DEFAULT; }
}

public interface Loop {
    String getName();
    Phase getPhase();
    void pause();
    void resume();
    boolean isPaused();
    int liveThreads();
    int configuredThreads();
}

public enum Phase {
    INGRESS,        // directory scanner, SQS connector, event store forwarder
    SPLIT_ZIP,      // the stages, in pipeline order; receive is Dropwizard's HTTP threads
    AGGREGATE,      // the aggregate stage's claimers, then its merge workers
    FORWARD,        // the forward stage's loop, each destination's loop when fanning out, their liveness watches
    HOUSEKEEPING    // event store roll, cache refresh, shared-store sweep, dependency refreshes
}
```

The stage consumer is `worker.processNext().loopOutcome()`: no item is `NOTHING`, a failed item
`FAILED`, anything else `PROCESSED`. `ProxyPipelineAssembler.registerStages(registry, runtime)`
registers one loop per queue-consuming stage, `stage-<configName>` in the stage's phase with its
configured consumer threads; the assembler also registers the shared-store sweep. The proxy's one
Dropwizard `Managed`, `ProxyLifecycle`, registers the sources, assembles the pipeline, starts the
registry, and on stop stops the registry and then closes the runtime's queues and stores.
Registering after start starts the work at once; registering once stop has begun is refused.

## 4. Design

### 4.1 Threads

Every thread is `<name>-<n>`, a daemon in the proxy's thread group at one below normal priority.
Daemon, because under R8 nothing correct depends on shutdown completing, and a thread that
ignores interruption must not be able to keep the JVM alive.

### 4.2 A loop

*N* threads run `task.run()` in a loop until stop. Each thread holds its own consecutive-failure
count; `FAILED` sleeps `1s × 2^(failures−1)` capped at `30s`, a throw sleeps `1s`, `PROCESSED`
and `NOTHING` do not sleep. The sleeps are interruptible; stop interrupts. The backoffs are
constants, not configuration - there has been no case for tuning them and a wrong value is a
silent stall or a spin.

A paused loop's threads wait at a gate before each `run()`; resume, or stop, releases them.

A task that waits bounds its wait and reports `NOTHING`, so an idle loop leaves well inside the
stop timeout rather than at it: the local queue waits a second at most, Kafka's poll 100 ms, and
an SQS long poll its `waitTime`, 20 s by default. A forward back-off wait is served in one-second
slices that watch the registry's shutting-down flag.

`liveThreads()` counts threads currently inside the loop, not threads ever created.

### 4.3 A schedule

One thread, `scheduleWithFixedDelay`, the first run at start and the task's exceptions contained
and logged. A delay of zero or less is refused at registration: it is a busy loop, not a schedule.

### 4.4 Start and stop

```
start:  HOUSEKEEPING → EGRESS → FORWARD → AGGREGATE → SPLIT_ZIP → INGRESS
stop:   INGRESS → SPLIT_ZIP → AGGREGATE → FORWARD → EGRESS → HOUSEKEEPING
```

Stop, per registration: resume if paused; signal stop; wait up to the stop timeout (30 s) for the
threads to leave the loop; interrupt; wait 5 s more; if still running, log `ERROR` naming the
loop and move on. The registry's stop never throws and always reaches every registration. A task
that throws because stop interrupted it is logged at `DEBUG`, not as a task error. Once stopped, a
registry cannot be started again.

Only stop may end a loop thread. A thread that leaves the loop while the loop is still running -
something else interrupted it - is logged at `ERROR`, and health shows the loop short a thread.

`isShuttingDown()` is true from the moment stop begins, so a task that would otherwise start a
long piece of work - a forward with retries - can decline to.

### 4.5 Health

`PipelineHealthChecks` reports, per loop, `threads.configured`, `threads.live` and `paused`,
and once the registry is started is unhealthy when live is below configured. A probe of a queue
or store that throws is logged at `WARN` as well as reported, so a failing backend is visible in
the log and not only in the health endpoint.

### 4.6 Where it lives

`stroom.proxy.app.execution`: `WorkRegistry`, `Loop`, `LoopTask`, `Phase`. The registry is a
Guice singleton injected wherever work is registered: `ProxyLifecycle`, `ProxyPipelineAssembler`,
`ProxyCacheServiceImpl`. The forward stage's destination loops and liveness watches are registered
by the assembler.

## 5. Stop, traced

| Phase stopping | What stops | What it may still need, and why it is still there |
|---|---|---|
| INGRESS | scanner, SQS connector, event store forwarder | the receive store and its output queue - stages have not stopped |
| SPLIT_ZIP … FORWARD | each stage's consumer loop, in order | its input queue, to finish or release the item it holds; its output queue and store, to publish - all open until the runtime closes |
| HOUSEKEEPING | sweeps, rolls, refreshes | nothing |
| then | `runtime.close()`: queues, then stores | - |

An item held by a stage thread when stop is called finishes if it can within the timeout, or is
interrupted and released by `close()` on its item. Either way no message is lost and no claim is
held for ever.

## 6. What is deliberately not here

- **Per-message backoff.** The queue counts attempts per message; the loop backs off per thread.
  A poison message is bounded by the queue's give-up, not by the loop.
- **Configurable backoffs.** Constants; see §4.2.
- **A supervisor that restarts dead threads.** A loop thread never dies (E4), so there is nothing
  to restart; a loop with fewer live threads than configured is a defect to see in health, not
  to paper over.
- **A second wait after an empty poll.** The task waited; see E5.

## 7. What the implementation before this design did

| Design | Previous code |
|---|---|
| E1: one registry | Three mechanisms: `PipelineStageRunner` (pipeline stages, its own executor per stage), `ParallelExecutor` and `FrequencyExecutor` under `ProxyServices` (handlers), plus `ProxyPipelineLifecycle` owning the runners and the sweeper |
| E2: phases | `ProxyServices` stopped in reverse registration order, which put consumers before producers; the pipeline stopped separately after it |
| E3: bounded, awaited | `FrequencyExecutor.stop()` called `shutdownNow()` and returned without waiting, so the documented ordering was not enforced |
| E4: live threads in health | Health checked queues and stores only; a stage whose thread had been retired by an `Error` reported healthy |
| E5: no double wait | The worker waited up to 1 s on the queue, then the runner slept another 100 ms on `NOTHING` |
| E7: dependencies stop last | `RemoteFeedStatusService` was a separate `Managed` stopped before the pipeline that called it |

## 8. Decisions

Taken 2026-09-08.

| | Decision | What it overturns |
|---|---|---|
| **D1** | **One `WorkRegistry` with phases** replaces `PipelineStageRunner`, `ProxyPipelineLifecycle`, `ProxyServices`, `ParallelExecutor` and `FrequencyExecutor`; handlers register loops and schedules naming a phase | Three mechanisms, each owning its threads, ordered by hand and by registration |
| **D2** | **Stop order is the data-flow phase order** of §4.4; start is its reverse | Reverse-registration stop, which put consumers before producers |
| **D3** | **No sleep after an empty poll**; the task waits | The runner's 100 ms sleep on top of the worker's 1 s wait |
| **D4** | **A loop with fewer live threads than configured is unhealthy**, and backend probe failures are logged at `WARN` as well as reported | Health that saw queues and stores only |
| **D5** | **Daemon threads everywhere** | Non-daemon handler threads |
| **D6** | **Backoffs stay constants**: 1 s doubling to 30 s on failure, 1 s on a throw | Nothing; stated so they are not made configuration without a case |
| **D7** | The three `stroom-proxy-repo` classes are deleted; the new ones live in `stroom.proxy.app.execution` | - |
| **D8** | `RemoteFeedStatusService`'s refresh is the registry's `feed-status-refresh` schedule (done with the receipt layer, 2026-09-08); `RefreshManager` is started and stopped by `ProxyLifecycle` before the registry starts and after it stops | Both were `Managed` beans, which Dropwizard stopped *before* `ProxyLifecycle` because it orders them by class name |

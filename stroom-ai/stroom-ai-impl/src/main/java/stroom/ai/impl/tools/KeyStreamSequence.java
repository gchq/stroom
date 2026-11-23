/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.ai.impl.tools;

import stroom.ai.api.ElasticEsql;
import stroom.ai.api.ElasticKeyStreams;
import stroom.ai.api.ElasticKeyStreams.KeyCount;
import stroom.ai.api.ElasticKeyStreams.KeyField;
import stroom.ai.api.ElasticKeyStreams.KeyPage;
import stroom.ai.api.ElasticKeyStreams.KeyRange;
import stroom.ai.api.ElasticKeyStreams.KeyRequest;
import stroom.ai.api.ElasticKeyStreams.Refusal;
import stroom.ai.api.ElasticKeyStreams.Row;
import stroom.ai.api.ElasticKeyStreams.Sample;
import stroom.ai.api.ElasticKeyStreams.SampleRequest;
import stroom.ai.api.ElasticKeyStreams.Side;
import stroom.ai.api.ElasticKeyStreams.Transform;
import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.tools.EqlQueryWriter.Step;
import stroom.ai.impl.tools.EqlSequenceResults.Entity;
import stroom.ai.impl.tools.EqlSequenceResults.Hit;
import stroom.ai.impl.tools.EqlSequenceResults.Review;
import stroom.ai.impl.tools.KeyRanges.Chunk;
import stroom.ai.impl.tools.KeyRanges.Planned;
import stroom.ai.impl.tools.KeyStreamJoin.Found;
import stroom.ai.impl.tools.KeyStreamJoin.RangeResult;
import stroom.ai.impl.tools.KeyStreamJoin.StepRole;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.docref.DocRef;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.QueryField;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskTerminatedException;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// `sequence_query`'s streaming engine: a sort-merge join run in Stroom over Elastic Index docs, each step's events
/// read in join-key order ([ElasticKeyStreams]) and merged holding only the current key's events, so no side is
/// bounded by Elasticsearch's 10,000-hit window, its buckets limit, ES|QL's 10,000 rows, or EQL's per-sequence
/// fetches. Steps in time within a key ("X then Y within N after X", [StreamSequences]); a step read as keys only
/// (an entity condition at any time, e.g. a team's users in a directory); join-field transforms (lowercase, the
/// domain stripped, a path's file name) computed by Elasticsearch as the rows stream, so values written differently
/// by two sources compare equal.
///
/// Before it reads, each step's size is estimated with ES|QL ([ElasticEsql#query], its events counted and its keys
/// counted with `countTotal`, a few of the commonest kept as examples), and a join near the caps (rows read, keys
/// merged: `stroom.ai` settings) is asked about before it runs (refused with the estimate over MCP, unless
/// `runAnyway`). A step with few keys (under the push-down setting) is read first and its keys pushed down as terms
/// on the other steps (batched under Elasticsearch's terms limit), so the large sides are cut before they stream.
/// The key space is cut into ranges for workers ([KeyRanges]: from the pushed-down keys, or a sample of the largest
/// side's keys by events), each worker merging its range alone as the user; the results are put together in key
/// order. Progress is reported as it runs; the sequences go to a workspace file, a summary to the model. A join that
/// finds nothing says why it may have (each step's events and keys, the keys step 1 shares with each other step,
/// and a few values of each when none are shared, with a transform that would make them meet); a bounded sequence
/// counts the keys that began and never ended, with their sources.
class KeyStreamSequence {

    /// What the result says the engine is.
    static final String ENGINE = "stream";
    /// The events or keys a page reads.
    static final int PAGE_SIZE = ElasticKeyStreams.MAX_PAGE;
    /// The most sequences a call may keep and write.
    static final int MAX_LIMIT = 100_000;
    /// The sequences kept when the call doesn't say.
    static final int DEFAULT_LIMIT = 10_000;
    /// How near a cap an estimate must be for the user to be asked first (a fraction of the cap).
    static final double NEAR_CAP = 0.8;
    /// Fewer estimated rows than this are read by one worker: cutting them into ranges costs more than it saves.
    static final long MIN_ROWS_FOR_WORKERS = 50_000;
    /// About how many rows a worker reads a second, for the estimate's time: conservative, from the benchmark of
    /// 2026-11-23 (`TestBenchStreamingJoin`, single-node test container), where a worker read about 42,000 a second
    /// alone, 32,000 as one of four, and 19,000 to 23,000 with a transform (a Painless runtime field).
    static final long ROWS_PER_SECOND = 20_000;
    /// How many times fewer keys a step must have than another step's events to be pushed down: a side as large as
    /// the other costs more to read first and send as terms than it cuts.
    static final long PUSH_DOWN_FACTOR = 10;
    /// The most workers, whatever the setting says.
    static final int MAX_WORKERS = 16;
    /// How often progress is reported while the workers run.
    static final Duration PROGRESS_EVERY = Duration.ofSeconds(2);
    /// The examples of each step's values given in a diagnosis.
    static final int EXAMPLES = 3;
    /// The form's key for the question asked of a join near the caps.
    static final String RUN_FORM = "run";
    static final String RUN = "Run it";
    static final String DONT_RUN = "Don't run it";
    /// What every result says of the events.
    static final String CAVEAT = "The events come straight from Elasticsearch, as esql_query's counts do: they may "
                                 + "include events of streams the user can't see or that have been deleted, so a "
                                 + "Stroom search may find fewer.";
    /// What the result says of how sequences are counted.
    static final String COUNTED = "Counted exactly as the steps say: each sequence starts at the latest step 1 "
                                  + "event before its next step (as EQL keeps the latest), and one entity's "
                                  + "sequences don't overlap.";
    // The transforms tried on two sides' examples, to say which would make them meet.
    private static final List<List<Transform>> TRIED = List.of(List.of(Transform.LOWERCASE),
            List.of(Transform.STRIP_DOMAIN), List.of(Transform.STRIP_DOMAIN, Transform.LOWERCASE),
            List.of(Transform.FILE_NAME), List.of(Transform.FILE_NAME, Transform.LOWERCASE));

    private final Provider<ElasticKeyStreams> keyStreamsProvider;
    private final Provider<ElasticEsql> elasticEsqlProvider;
    private final SecurityContext securityContext;
    private final AiAuditLog auditLog;

    /// @param keyStreamsProvider  Reads the steps' sides in key order.
    /// @param elasticEsqlProvider Estimates each step's size.
    /// @param securityContext     Who the call is for, so its workers read as them.
    /// @param auditLog            Audits what each doc was searched for.
    @Inject
    KeyStreamSequence(final Provider<ElasticKeyStreams> keyStreamsProvider,
                      final Provider<ElasticEsql> elasticEsqlProvider,
                      final SecurityContext securityContext,
                      final AiAuditLog auditLog) {
        this.keyStreamsProvider = keyStreamsProvider;
        this.elasticEsqlProvider = elasticEsqlProvider;
        this.securityContext = securityContext;
        this.auditLog = auditLog;
    }

    /// The docs' cluster, and each doc's time field and fields, for the steps to be checked against.
    ///
    /// @param docs The Elastic Index docs, in order.
    /// @return What they are.
    /// @throws WorkspaceException saying why they can't be joined.
    ElasticKeyStreams.Target target(final List<DocRef> docs) {
        try {
            return keyStreamsProvider.get().target(docs);
        } catch (final Refusal refusal) {
            throw new WorkspaceException(refusal.getMessage() + (refusal.reason()
                                                                 == ElasticKeyStreams.Reason.DIFFERENT_CLUSTERS
                    ? ". Run a sequence per cluster."
                    : "."));
        } catch (final PermissionException e) {
            throw new WorkspaceException(notUsable(docs, e));
        }
    }

    /// Runs the join.
    ///
    /// @param call What to join, and where the result goes.
    /// @return The result: a summary, the sequences in the workspace.
    ToolResult run(final Call call) {
        final AiWorkspaceConfig config = call.config();
        final Instant started = Instant.now();
        final int workers = Math.clamp(config.getJoinWorkers(), 1, MAX_WORKERS);
        final List<JoinStep> steps = call.steps();
        final List<Side> sides = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            sides.add(side(steps.get(i), "Step " + (i + 1), call.timeRange()));
        }
        final Side untilSide = call.until() == null
                ? null
                : side(call.until(), "The until step", call.timeRange());
        final List<JoinKeys.Kind> kinds = steps.getFirst().kinds();
        final Comparator<List<Object>> comparator = JoinKeys.comparator(kinds);

        // The estimate, before anything streams.
        final Estimate estimate = estimate(call, started.plus(call.timeLimit()));
        final int pushed = pushedDown(steps, estimate, config.getJoinPushDownKeys());
        final long estimatedRows = estimate.rows();
        final boolean nearCaps = pushed < 0 && estimate.known()
                                 && (estimatedRows >= NEAR_CAP * config.getJoinMaxRows()
                                     || estimate.keys() >= NEAR_CAP * config.getJoinMaxKeys());
        final TimeLimit timeLimit = timeLimit(call, estimate, workers, nearCaps);
        final Instant deadline = Instant.now().plus(timeLimit.limit());

        final JoinBudget budget = new JoinBudget(config.getJoinMaxRows(), config.getJoinMaxKeys(), deadline,
                call.context().stopped());
        final ElasticKeyStreams streams = keyStreamsProvider.get();
        final List<KeyCount> pushedKeys = pushed < 0
                ? List.of()
                : readKeys(streams, sides.get(pushed), budget);
        final List<Planned> ranges;
        String sampled = null;
        if (pushed >= 0) {
            ranges = KeyRanges.fromKeys(pushedKeys, workers, ElasticKeyStreams.MAX_TERMS);
        } else if (workers > 1 && estimate.known() && estimatedRows >= MIN_ROWS_FOR_WORKERS) {
            final int largest = largest(steps, estimate);
            final Sample sample = sample(streams, sides.get(largest), estimate.steps().get(largest), workers,
                    budget);
            ranges = KeyRanges.fromBoundaries(sample.boundaries());
            sampled = "The key space was cut into " + ranges.size() + " ranges of about the same events from step "
                      + (largest + 1) + "'s " + sample.keys() + " keys.";
        } else {
            ranges = KeyRanges.fromBoundaries(List.of());
        }

        final List<RangeResult> results = ranges.isEmpty()
                ? List.of()
                : runRanges(call, sides, untilSide, ranges, pushed, pushedKeys, comparator, kinds, budget);
        if (budget.stoppedByUser()) {
            // The user stopped the chat: the run ends stopped.
            throw new TaskTerminatedException();
        }
        final Outcome outcome = outcome(call, estimate, workers, ranges.size(), pushed, pushedKeys.size(), sampled,
                results, budget, Duration.between(started, Instant.now()), timeLimit);
        audit(call, outcome);
        final Map<String, Object> structured = structured(call, outcome);
        return call.context().session().isChat()
                ? ToolResult.text(markdown(call, outcome)).withStructuredContent(structured)
                : ToolResult.text(JsonUtil.writeValueAsString(structured, false)).withStructuredContent(structured);
    }

    // A step's side: its doc, join fields, conditions, and the time range unless it is read as keys (an entity
    // condition holds at any time).
    private static Side side(final JoinStep step, final String what, final TimeRange timeRange) {
        return new Side(step.doc(), step.keys(), StreamFilters.expression(step.step(), what), step.keysOnly()
                ? null
                : timeRange, KeyRange.ALL, List.of());
    }

    // ------------------------------------------------------------------------------------------------
    // The estimate

    // Each step's events (counted up to the rows cap) and keys (with a few of the commonest), by ES|QL; unknown
    // where ES|QL can't be used.
    private Estimate estimate(final Call call, final Instant deadline) {
        final List<StepEstimate> estimates = new ArrayList<>();
        final long cap = call.config().getJoinMaxRows();
        String note = null;
        for (final JoinStep step : call.steps()) {
            if (note != null) {
                estimates.add(StepEstimate.UNKNOWN);
                continue;
            }
            final TimeRange range = step.keysOnly()
                    ? null
                    : call.timeRange();
            try {
                final ElasticEsql.Result rows = elasticEsqlProvider.get().query(new ElasticEsql.Request(step.doc(),
                        EqlQueryWriter.countPipeline(step.step(), cap), null, range, 1, remaining(deadline), false,
                        call.context().stopped()));
                final String by = String.join(", ", step.step().joinFields().stream().map(EsqlHints::name).toList());
                final ElasticEsql.Result keys = elasticEsqlProvider.get().query(new ElasticEsql.Request(step.doc(),
                        EqlQueryWriter.wherePipeline(step.step()) + "| STATS n = COUNT(*) BY " + by
                        + " | SORT n DESC | LIMIT " + EXAMPLES, null, range, EXAMPLES, remaining(deadline), true,
                        call.context().stopped()));
                final List<List<Object>> examples = new ArrayList<>();
                for (final List<Object> row : keys.rows()) {
                    examples.add(new ArrayList<>(row.subList(1, row.size())));
                }
                final long keyCount = keys.totalRows() != null
                        ? keys.totalRows()
                        : keys.rows().size();
                estimates.add(new StepEstimate(count(rows), keyCount, examples));
            } catch (final ElasticEsql.Refusal refusal) {
                if (refusal.reason() == ElasticEsql.Reason.STOPPED) {
                    throw new TaskTerminatedException();
                }
                note = "The steps weren't estimated first (" + NullSafe.trim(refusal.getMessage()) + ").";
                estimates.add(StepEstimate.UNKNOWN);
            } catch (final PermissionException e) {
                throw new WorkspaceException(notUsable(List.of(step.doc()), e));
            }
        }
        return new Estimate(estimates, call.steps(), note);
    }

    private static Long count(final ElasticEsql.Result result) {
        if (result.rows().isEmpty() || result.rows().getFirst().isEmpty()
            || !(result.rows().getFirst().getFirst() instanceof final Number number)) {
            return 0L;
        }
        return number.longValue();
    }

    /// The step to push down: of the steps that must happen, the one with the fewest keys, if they are at most
    /// `pushDownKeys` and at most a [#PUSH_DOWN_FACTOR]th of another step's events, so there is a side to cut.
    ///
    /// @param steps        The steps.
    /// @param estimate     Their estimate.
    /// @param pushDownKeys The most keys a pushed-down step may have.
    /// @return Its position, or -1 for none.
    static int pushedDown(final List<JoinStep> steps, final Estimate estimate, final int pushDownKeys) {
        if (!estimate.known() || steps.size() < 2) {
            return -1;
        }
        int best = -1;
        for (int i = 0; i < steps.size(); i++) {
            final StepEstimate step = estimate.steps().get(i);
            if (steps.get(i).step().missing() || step.keys() == null || step.keys() > pushDownKeys) {
                continue;
            }
            long largestOther = 0;
            for (int j = 0; j < steps.size(); j++) {
                if (j != i) {
                    largestOther = Math.max(largestOther, rowsOf(estimate.steps().get(j)));
                }
            }
            if (step.keys() * PUSH_DOWN_FACTOR > largestOther) {
                continue;
            }
            if (best < 0 || step.keys() < estimate.steps().get(best).keys()) {
                best = i;
            }
        }
        return best;
    }

    // The step with the most rows, whose keys the ranges are cut from.
    private static int largest(final List<JoinStep> steps, final Estimate estimate) {
        int best = 0;
        for (int i = 1; i < steps.size(); i++) {
            if (rowsOf(estimate.steps().get(i)) > rowsOf(estimate.steps().get(best))) {
                best = i;
            }
        }
        return best;
    }

    private static long rowsOf(final StepEstimate step) {
        return step.rows() == null
                ? 0
                : step.rows();
    }

    /// @param estimate The estimate.
    /// @param workers  The workers.
    /// @return About how long the join takes, in seconds (at least 1), at [#ROWS_PER_SECOND] a worker.
    static long estimatedSeconds(final Estimate estimate, final int workers) {
        return Math.max(1, estimate.rows() / (ROWS_PER_SECOND * workers));
    }

    /// @param estimate The estimate.
    /// @param workers  The workers.
    /// @param config   The caps.
    /// @return What the estimate says, e.g. "about 2,000,000 rows and 90,000 keys, 4 workers, about 2 minutes".
    static String estimateText(final Estimate estimate, final int workers, final AiWorkspaceConfig config) {
        return "about " + number(estimate.rows()) + " rows to read (the cap is " + number(config.getJoinMaxRows())
               + ") and " + number(estimate.keys()) + " keys (the cap is " + number(config.getJoinMaxKeys()) + "), "
               + workers + " worker" + plural(workers) + ", about " + duration(estimatedSeconds(estimate, workers))
               + (workers > 1
                ? " (" + workers + " workers put " + workers + " times the search load on the cluster)"
                : "");
    }

    /// How long the join may run: the query time limit, or the join time limit (`joinTimeLimit`) where the
    /// estimate says it won't fit the query time limit and the user agrees (in the chat's form, or `runAnyway`). A
    /// join near the caps, or past the query time limit, is asked about in the chat; over MCP one near the caps is
    /// refused unless `runAnyway`, and one past the query time limit runs within it, saying so. A join estimated
    /// past even the join time limit says it will likely end early.
    ///
    /// @param call     The call.
    /// @param estimate The estimate.
    /// @param workers  The workers.
    /// @param nearCaps True if the estimate is near a cap.
    /// @return The time limit, and what the result says of it (null for nothing).
    static TimeLimit timeLimit(final Call call, final Estimate estimate, final int workers, final boolean nearCaps) {
        final Duration queryLimit = call.timeLimit();
        final Duration configured = NullSafe.getOrElse(call.config().getJoinTimeLimit(),
                duration -> duration.getDuration(), queryLimit);
        final Duration joinLimit = configured.compareTo(queryLimit) > 0
                ? configured
                : queryLimit;
        final long seconds = estimate.known()
                ? estimatedSeconds(estimate, workers)
                : 0;
        final boolean overQuery = estimate.known() && seconds > queryLimit.toSeconds();
        final boolean longer = overQuery && joinLimit.compareTo(queryLimit) > 0;
        final String beyondJoin = estimate.known() && seconds > joinLimit.toSeconds()
                ? " It is estimated to take longer than even the join time limit (" + duration(joinLimit.toSeconds())
                  + "), so it will likely end early, keeping what it has found."
                : "";
        final String text = estimateText(estimate, workers, call.config());
        if (call.runAnyway()) {
            return new TimeLimit(joinLimit, overQuery
                    ? "Run up to the join time limit (" + duration(joinLimit.toSeconds()) + "), as runAnyway asked: "
                      + text + "." + beyondJoin
                    : null);
        }
        if (!nearCaps && !overQuery) {
            return new TimeLimit(queryLimit, null);
        }
        final ToolCallContext context = call.context();
        if (!context.session().isChat() || !context.userQuestions().canAsk()) {
            if (nearCaps) {
                throw new WorkspaceException("Not run: the join is near its caps: " + text + ". Narrow the time "
                                             + "range or the steps, or ask the user and call again with "
                                             + SequenceQueryTool.RUN_ANYWAY_ARG + "=true to run it anyway.");
            }
            return new TimeLimit(queryLimit, "Run within the query time limit (" + duration(queryLimit.toSeconds())
                                             + "), though estimated at " + text + ", so it may end early: call "
                                             + "again with " + SequenceQueryTool.RUN_ANYWAY_ARG + "=true to run it "
                                             + "up to the join time limit (" + duration(joinLimit.toSeconds())
                                             + ")." + beyondJoin);
        }
        final String runLonger = "Run it, for up to " + duration(joinLimit.toSeconds());
        final String runWithin = "Run it within " + duration(queryLimit.toSeconds());
        final List<String> choices = longer
                ? List.of(runLonger, runWithin, DONT_RUN)
                : List.of(RUN, DONT_RUN);
        final String question = "This join reads " + text + (nearCaps
                ? ", and stops at the caps, keeping what it has found."
                : ".") + (overQuery
                ? " That is longer than the query time limit (" + duration(queryLimit.toSeconds()) + ")"
                  + (longer
                        ? ": it can run for up to " + duration(joinLimit.toSeconds()) + "."
                        : ", so it may end early.")
                : "") + beyondJoin + " Run it?";
        final Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "string");
        field.put("title", "Run the join?");
        field.put("enum", choices);
        field.put("default", choices.getFirst());
        final Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "object");
        form.put("properties", Map.of(RUN_FORM, field));
        form.put("required", List.of(RUN_FORM));
        final ElicitResult answer = context.userQuestions().ask(SequenceQueryTool.NAME,
                UserQuestions.formCallId(call.callId(), RUN_FORM), question, form);
        final String chosen = answer.action() == ElicitResult.Action.ACCEPT
                ? String.valueOf(NullSafe.map(answer.content()).get(RUN_FORM))
                : DONT_RUN;
        if (runLonger.equals(chosen)) {
            return new TimeLimit(joinLimit, "Run for up to " + duration(joinLimit.toSeconds()) + ", as the user "
                                            + "chose: " + text + "." + beyondJoin);
        } else if (runWithin.equals(chosen) || RUN.equals(chosen)) {
            return new TimeLimit(queryLimit, overQuery
                    ? "Run within the query time limit (" + duration(queryLimit.toSeconds()) + "), as the user "
                      + "chose, though estimated at " + text + ", so it may end early."
                    : null);
        }
        throw new WorkspaceException("The user chose not to run the join (" + text + "), so nothing was read. "
                                     + "Narrow the time range or the steps with them.");
    }

    // ------------------------------------------------------------------------------------------------
    // Reading

    // Every key of a side (a step pushed down), in key order.
    private static List<KeyCount> readKeys(final ElasticKeyStreams streams, final Side side, final JoinBudget budget) {
        final List<KeyCount> keys = new ArrayList<>();
        List<Object> after = null;
        do {
            if (budget.check()) {
                break;
            }
            final List<Object> from = after;
            final KeyPage page = refused(() -> streams.keyPage(new KeyRequest(side, from, PAGE_SIZE,
                    budget.remaining(), budget::stoppedByUser)));
            budget.readRows(page.keys().size());
            keys.addAll(page.keys());
            after = page.keys().size() < PAGE_SIZE
                    ? null
                    : page.after();
        } while (after != null);
        return keys;
    }

    private static Sample sample(final ElasticKeyStreams streams,
                                 final Side side,
                                 final StepEstimate estimate,
                                 final int workers,
                                 final JoinBudget budget) {
        final long rows = Math.max(1, rowsOf(estimate));
        return refused(() -> streams.sampleKeys(new SampleRequest(side, (rows + workers - 1) / workers, workers - 1,
                budget.remaining(), budget::stoppedByUser)));
    }

    // Runs each range in a worker, as the user, reporting progress; the results in key order.
    private List<RangeResult> runRanges(final Call call,
                                        final List<Side> sides,
                                        final Side untilSide,
                                        final List<Planned> ranges,
                                        final int pushed,
                                        final List<KeyCount> pushedKeys,
                                        final Comparator<List<Object>> comparator,
                                        final List<JoinKeys.Kind> kinds,
                                        final JoinBudget budget) {
        final ElasticKeyStreams streams = keyStreamsProvider.get();
        final UserRef user = securityContext.getUserRef();
        final int threads = Math.min(ranges.size(), Math.clamp(call.config().getJoinWorkers(), 1, MAX_WORKERS));
        final AtomicInteger done = new AtomicInteger();
        final AtomicInteger named = new AtomicInteger();
        final ExecutorService pool = Executors.newFixedThreadPool(threads, runnable -> {
            final Thread thread = new Thread(runnable, "Stroom AI join worker " + named.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        try {
            final List<Future<RangeResult>> futures = new ArrayList<>();
            for (final Planned range : ranges) {
                final List<KeyCount> rangeKeys = pushed < 0
                        ? List.of()
                        : inRange(pushedKeys, range.range(), kinds.getFirst());
                futures.add(pool.submit(() -> {
                    try {
                        return asUser(user, () -> runRange(call, streams, sides, untilSide, range, pushed, rangeKeys,
                                comparator, budget));
                    } finally {
                        done.incrementAndGet();
                    }
                }));
            }
            final List<RangeResult> results = new ArrayList<>();
            for (final Future<RangeResult> future : futures) {
                results.add(await(future, call, ranges.size(), done, budget));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    // Waits for one range, reporting progress across them all as it waits.
    private static RangeResult await(final Future<RangeResult> future,
                                     final Call call,
                                     final int ranges,
                                     final AtomicInteger done,
                                     final JoinBudget budget) {
        while (true) {
            try {
                return future.get(PROGRESS_EVERY.toMillis(), TimeUnit.MILLISECONDS);
            } catch (final TimeoutException e) {
                call.context().progress().report("Streaming join: read " + number(budget.rows()) + " rows, merged "
                                                 + number(budget.keys()) + " keys; " + done.get() + " of " + ranges
                                                 + " key range" + plural(ranges) + " done.");
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TaskTerminatedException();
            } catch (final ExecutionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof final RuntimeException runtime) {
                    throw runtime;
                }
                throw new WorkspaceException("The join failed: " + cause);
            }
        }
    }

    private <T> T asUser(final UserRef user, final Supplier<T> work) {
        return user == null
                ? work.get()
                : securityContext.asUserResult(user, work);
    }

    // One range: each step's side over the range's chunks, merged.
    private static RangeResult runRange(final Call call,
                                        final ElasticKeyStreams streams,
                                        final List<Side> sides,
                                        final Side untilSide,
                                        final Planned range,
                                        final int pushed,
                                        final List<KeyCount> rangeKeys,
                                        final Comparator<List<Object>> comparator,
                                        final JoinBudget budget) {
        final List<JoinStep> steps = call.steps();
        final List<KeyGroupSource> sources = new ArrayList<>();
        KeyGroupSource until = null;
        try {
            for (int i = 0; i < steps.size(); i++) {
                final JoinStep step = steps.get(i);
                if (i == pushed && step.keysOnly()) {
                    sources.add(KeyGroupSource.listed(rangeKeys));
                } else if (step.keysOnly()) {
                    sources.add(KeyGroupSource.keys(streams, chunks(sides.get(i), range), PAGE_SIZE, budget));
                } else {
                    sources.add(KeyGroupSource.scan(streams, chunks(sides.get(i), range), fetched(step), PAGE_SIZE,
                            comparator, budget));
                }
            }
            if (untilSide != null) {
                until = KeyGroupSource.scan(streams, chunks(untilSide, range), List.of(), PAGE_SIZE, comparator,
                        budget);
            }
            final List<StepRole> roles = steps.stream()
                    .map(step -> new StepRole(!step.keysOnly(), step.step().missing(), step.step().runs()))
                    .toList();
            final long maxspanMs = call.maxspan() == null
                    ? Long.MAX_VALUE / 4
                    : call.maxspan().toMillis();
            final KeyGroupSource untilSource = until;
            return refused(() -> KeyStreamJoin.run(roles, sources, untilSource, comparator, maxspanMs, call.limit(),
                    budget));
        } finally {
            sources.forEach(KeyGroupSource::close);
            if (until != null) {
                until.close();
            }
        }
    }

    private static List<Side> chunks(final Side side, final Planned range) {
        return range.chunks().stream()
                .map((Chunk chunk) -> side.restricted(chunk.range(), chunk.terms()))
                .toList();
    }

    private static List<String> fetched(final JoinStep step) {
        final Set<String> fields = new LinkedHashSet<>();
        fields.add(EqlSequenceResults.STREAM_ID);
        fields.add(EqlSequenceResults.EVENT_ID);
        fields.addAll(step.step().fields());
        return List.copyOf(fields);
    }

    /// @param keys  Keys, in key order.
    /// @param range A range of the first join field's values.
    /// @param kind  How the first join field's values compare.
    /// @return The keys in the range.
    static List<KeyCount> inRange(final List<KeyCount> keys, final KeyRange range, final JoinKeys.Kind kind) {
        return keys.stream()
                .filter(key -> {
                    final Object first = key.key().isEmpty()
                            ? null
                            : key.key().getFirst();
                    return (range.after() == null || JoinKeys.compare(kind, first, range.after()) > 0)
                           && (range.upTo() == null || JoinKeys.compare(kind, first, range.upTo()) <= 0);
                })
                .toList();
    }

    // Runs a read, its refusal said in the model's terms; the user's stop ends the run.
    private static <T> T refused(final Supplier<T> read) {
        try {
            return read.get();
        } catch (final Refusal refusal) {
            if (refusal.reason() == ElasticKeyStreams.Reason.STOPPED) {
                throw new TaskTerminatedException();
            }
            throw new WorkspaceException(refusal(refusal));
        }
    }

    /// @param refusal Why a read wasn't made.
    /// @return What the model is told.
    static String refusal(final Refusal refusal) {
        final String message = NullSafe.trim(refusal.getMessage());
        return switch (refusal.reason()) {
            case TIMEOUT -> message + ": narrow the time range or the steps, or ask an administrator to raise the "
                            + "query time limit (stroom.ai.workspace.queryTimeLimit).";
            case KEY_NOT_USABLE -> message + ". Join on another field, or without the transform.";
            case UNKNOWN_FIELD -> message + ". describe_data_source gives the index's fields.";
            case SCAN_EXPIRED -> message + ". Call again.";
            default -> message;
        };
    }

    private static Duration remaining(final Instant deadline) {
        final Duration left = Duration.between(Instant.now(), deadline);
        if (left.isNegative() || left.isZero()) {
            throw new WorkspaceException("The time limit ran out before the join could run: narrow the time range "
                                         + "or the steps.");
        }
        return left;
    }

    // ------------------------------------------------------------------------------------------------
    // The outcome

    private Outcome outcome(final Call call,
                            final Estimate estimate,
                            final int workers,
                            final int ranges,
                            final int pushed,
                            final int pushedKeys,
                            final String sampled,
                            final List<RangeResult> results,
                            final JoinBudget budget,
                            final Duration took,
                            final TimeLimit timeLimit) {
        final int n = call.steps().size();
        long matches = 0;
        long entities = 0;
        long opened = 0;
        long cut = 0;
        final List<Found> kept = new ArrayList<>();
        final long[] keys = new long[n];
        final long[] shared = new long[n];
        final List<List<List<Object>>> examples = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            examples.add(new ArrayList<>());
        }
        final List<List<Object>> openedExamples = new ArrayList<>();
        for (final RangeResult result : results) {
            matches += result.matches();
            entities += result.entities();
            opened += result.openedNotClosed();
            cut += result.cutKeys();
            for (final Found found : result.kept()) {
                if (kept.size() < call.limit()) {
                    kept.add(found);
                }
            }
            for (int i = 0; i < n; i++) {
                keys[i] += result.keysByStep().get(i);
                shared[i] += result.sharedWithFirst().get(i);
                for (final List<Object> key : result.examplesByStep().get(i)) {
                    if (examples.get(i).size() < EXAMPLES) {
                        examples.get(i).add(key);
                    }
                }
            }
            for (final List<Object> key : result.openedExamples()) {
                if (openedExamples.size() < KeyStreamJoin.EXAMPLES) {
                    openedExamples.add(key);
                }
            }
        }
        final List<Long> keysByStep = new ArrayList<>();
        final List<Long> sharedWithFirst = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            keysByStep.add(keys[i]);
            sharedWithFirst.add(shared[i]);
        }
        final boolean timed = call.steps().stream().anyMatch(step -> !step.keysOnly());
        final List<Step> tableSteps = tableSteps(call.steps());
        final List<EqlSequenceResults.Row> rows = timed
                ? kept.stream().map(found -> row(found, call.steps())).toList()
                : List.of();
        final List<String> columns;
        final List<List<String>> cells;
        if (timed) {
            columns = EqlSequenceResults.columns(tableSteps);
            cells = rows.stream()
                    .map(row -> EqlSequenceResults.cells(row, tableSteps).stream().map(call.zone()::inZone).toList())
                    .toList();
        } else {
            columns = entityColumns(call.steps());
            cells = kept.stream().map(KeyStreamSequence::entityCells).toList();
        }
        if (!cells.isEmpty()) {
            ToolTables.write(call.workspace(), call.out(), columns, cells, call.docs().getFirst().getUuid());
        }
        final List<Entity> entityRows = timed
                ? EqlSequenceResults.entities(rows)
                : List.of();
        final List<ReviewQuery> reviews = timed
                ? reviews(call, rows, tableSteps)
                : List.of();
        final List<String> diagnosis = matches == 0
                ? diagnosis(call, estimate, keysByStep, sharedWithFirst, examples)
                : List.of();
        final DataQuality quality = dataQuality(call, estimate, pushed, keysByStep, opened, openedExamples);
        return new Outcome(estimate, workers, ranges, pushed, pushedKeys, sampled, matches, entities, kept.size(),
                columns, cells, rows, entityRows, reviews, diagnosis, quality, budget.reached(), budget.rows(),
                budget.keys(), cut, keysByStep, took, timed, timeLimit);
    }

    // The steps as the sequence table writes them: a step read as keys has no events, so no columns (as a missing
    // step has none).
    private static List<Step> tableSteps(final List<JoinStep> steps) {
        return steps.stream()
                .map(step -> step.keysOnly()
                        ? new Step(step.step().doc(), step.step().filters(), step.step().anyOf(),
                        step.step().joinFields(), true, 1, List.of(), step.step().types(), step.step().restricted())
                        : step.step())
                .toList();
    }

    // A sequence as the sequence table reads it.
    private static EqlSequenceResults.Row row(final Found found, final List<JoinStep> steps) {
        final List<List<Hit>> byStep = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            final List<Hit> hits = new ArrayList<>();
            for (final Row row : found.byStep().get(i)) {
                hits.add(new Hit(row.time(), EqlSequenceResults.text(first(row.fields().get(EqlSequenceResults
                        .STREAM_ID))), EqlSequenceResults.text(first(row.fields().get(EqlSequenceResults.EVENT_ID))),
                        row.fields()));
            }
            byStep.add(hits);
        }
        return new EqlSequenceResults.Row(found.key(), byStep);
    }

    private static Object first(final List<Object> values) {
        return values == null || values.isEmpty()
                ? null
                : values.getFirst();
    }

    private static List<String> entityColumns(final List<JoinStep> steps) {
        final List<String> columns = new ArrayList<>(steps.getFirst().step().joinFields());
        for (int i = 0; i < steps.size(); i++) {
            if (!steps.get(i).step().missing()) {
                columns.add("s" + (i + 1) + "_count");
            }
        }
        return columns;
    }

    private static List<String> entityCells(final Found found) {
        final List<String> cells = new ArrayList<>(found.key().stream().map(JoinKeys::text).toList());
        for (final Long count : found.counts()) {
            if (count > 0) {
                cells.add(String.valueOf(count));
            }
        }
        return cells;
    }

    // The review query per doc with hits, each validated.
    private static List<ReviewQuery> reviews(final Call call,
                                             final List<EqlSequenceResults.Row> rows,
                                             final List<Step> tableSteps) {
        if (rows.isEmpty()) {
            return List.of();
        }
        final List<ReviewQuery> reviews = new ArrayList<>();
        for (int d = 0; d < call.docs().size(); d++) {
            final DocRef doc = call.docs().get(d);
            final String timeField = call.timeFields().get(d);
            if (timeField == null) {
                continue;
            }
            final Set<String> names = new LinkedHashSet<>();
            call.fieldsByDoc().getOrDefault(doc, List.of()).forEach(field -> names.add(field.getFldName()));
            final boolean hasIds = names.contains(EqlSequenceResults.STREAM_ID)
                                   && names.contains(EqlSequenceResults.EVENT_ID);
            final Review review = EqlSequenceResults.review(doc, timeField, hasIds, rows, tableSteps, d + 1,
                    call.maxQueryLength());
            if (review != null) {
                reviews.add(new ReviewQuery(review, call.invalid().apply(review.query()),
                        SequenceQueryTool.reviewNote(review, call.maxQueryLength())));
            }
        }
        return reviews;
    }

    /// Why a join may have found nothing: each step's events and keys; for each later step that shares no key with
    /// step 1, a few values of each and a transform that would make them meet; and the steps that did meet but not
    /// in order within the window.
    ///
    /// @param call            The call.
    /// @param estimate        The estimate.
    /// @param keysByStep      The keys each step had, as read.
    /// @param sharedWithFirst The keys step 1 shared with each step.
    /// @param read            A few keys of each step, as read.
    /// @return The diagnosis, a sentence each.
    static List<String> diagnosis(final Call call,
                                  final Estimate estimate,
                                  final List<Long> keysByStep,
                                  final List<Long> sharedWithFirst,
                                  final List<List<List<Object>>> read) {
        final List<String> notes = new ArrayList<>();
        final List<JoinStep> steps = call.steps();
        final List<String> alone = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            final StepEstimate step = estimate.steps().get(i);
            alone.add("step " + (i + 1) + " (" + steps.get(i).doc().getName() + "): "
                      + (step.rows() == null
                    ? "?"
                    : number(step.rows())) + " events, " + number(Math.max(keysByStep.get(i), step.keys() == null
                    ? 0
                    : step.keys())) + " keys");
            if (step.rows() != null && step.rows() == 0 && !steps.get(i).step().missing()) {
                notes.add("Step " + (i + 1) + " matches no events on its own: check its conditions.");
            }
        }
        notes.addFirst("Each step alone: " + String.join("; ", alone) + ".");
        boolean anyUnshared = false;
        for (int i = 1; i < steps.size(); i++) {
            // A pushed-down step's keys cut the others before they are read, so the estimate's keys count too.
            if (steps.get(i).step().missing() || keys(keysByStep, estimate, 0) == 0
                || keys(keysByStep, estimate, i) == 0 || sharedWithFirst.get(i) > 0) {
                continue;
            }
            anyUnshared = true;
            final List<String> first = values(estimate.steps().getFirst().examples(), read.getFirst());
            final List<String> other = values(estimate.steps().get(i).examples(), read.get(i));
            final String fieldA = String.join(", ", steps.getFirst().step().joinFields());
            final String fieldB = String.join(", ", steps.get(i).step().joinFields());
            final StringBuilder sb = new StringBuilder("No shared values between step 1 (")
                    .append(steps.getFirst().doc().getName()).append(": ").append(fieldA).append(") and step ")
                    .append(i + 1).append(" (").append(steps.get(i).doc().getName()).append(": ").append(fieldB)
                    .append(")");
            if (!first.isEmpty() && !other.isEmpty()) {
                sb.append(", e.g. `").append(first.getFirst()).append("` vs `").append(other.getFirst()).append('`');
            }
            sb.append('.');
            final List<Transform> bridge = bridge(first, other);
            if (bridge != null) {
                sb.append(" With transform ").append(bridge.stream()
                                .map(transform -> transform.name().toLowerCase(Locale.ROOT))
                                .toList())
                        .append(" on both steps' join fields they may meet: the sources write the value "
                                + "differently, which the transform bridges (the indexes are left as they are).");
            } else {
                sb.append(" The join fields may not hold the same thing in both indexes: check them with "
                          + "esql_query (STATS COUNT(*) BY <field> | LIMIT 5).");
            }
            notes.add(sb.toString());
        }
        if (!anyUnshared && steps.stream().filter(step -> !step.keysOnly()).count() > 1) {
            notes.add("The steps' entities meet, but no sequence is in order within the window"
                      + (call.maxspan() == null
                    ? ""
                    : " (" + EqlQueryWriter.maxspanText(call.maxspan()) + ")") + ": widen maxspan, or check the "
                      + "steps' order.");
        }
        return notes;
    }

    // A step's keys: as read, or as estimated if more.
    private static long keys(final List<Long> keysByStep, final Estimate estimate, final int i) {
        return Math.max(keysByStep.get(i), Objects.requireNonNullElse(estimate.steps().get(i).keys(), 0L));
    }

    // The examples to show: the commonest (from the estimate), else the first read.
    private static List<String> values(final List<List<Object>> commonest, final List<List<Object>> read) {
        final List<List<Object>> chosen = commonest.isEmpty()
                ? read
                : commonest;
        return chosen.stream()
                .map(key -> String.join(", ", key.stream().map(JoinKeys::text).toList()))
                .limit(EXAMPLES)
                .toList();
    }

    /// The transforms that make two sides' values meet: the first of those tried that makes some of the examples
    /// equal; else, by their shapes, a domain on one side only (`CORP\jsmith`, `jsmith@corp`), a path on one side
    /// only, or case on one side only (the examples needn't be the same entities, as each side's commonest are).
    ///
    /// @param first One side's values.
    /// @param other The other's.
    /// @return The transforms, or null if neither the examples nor their shapes suggest one.
    static List<Transform> bridge(final List<String> first, final List<String> other) {
        for (final List<Transform> transforms : TRIED) {
            final Set<String> a = new LinkedHashSet<>();
            first.forEach(value -> a.add(Transform.applyAll(transforms, value)));
            for (final String value : other) {
                if (a.contains(Transform.applyAll(transforms, value))) {
                    return transforms;
                }
            }
        }
        if (first.isEmpty() || other.isEmpty()) {
            return null;
        }
        final List<Transform> transforms = new ArrayList<>();
        if (anyHas(first, KeyStreamSequence::hasDomain) != anyHas(other, KeyStreamSequence::hasDomain)) {
            transforms.add(Transform.STRIP_DOMAIN);
        } else if (anyHas(first, KeyStreamSequence::hasPath) != anyHas(other, KeyStreamSequence::hasPath)) {
            transforms.add(Transform.FILE_NAME);
        }
        final List<String> a = first.stream().map(value -> Transform.applyAll(transforms, value)).toList();
        final List<String> b = other.stream().map(value -> Transform.applyAll(transforms, value)).toList();
        if (anyHas(a, KeyStreamSequence::hasUpper) != anyHas(b, KeyStreamSequence::hasUpper)) {
            transforms.add(Transform.LOWERCASE);
        }
        return transforms.isEmpty()
                ? null
                : transforms;
    }

    private static boolean anyHas(final List<String> values, final Predicate<String> test) {
        return values.stream().anyMatch(test);
    }

    private static boolean hasDomain(final String value) {
        return value.contains("\\") || value.indexOf('@') > 0;
    }

    private static boolean hasPath(final String value) {
        return value.indexOf('/') >= 0 || value.contains("\\");
    }

    private static boolean hasUpper(final String value) {
        return !value.equals(value.toLowerCase(Locale.ROOT));
    }

    // The keys whose first timed step happened and whose last never did, with their sources. Where the last step
    // was pushed down, only its keys' first steps were read: the rest are estimated from the first step's keys.
    private static DataQuality dataQuality(final Call call,
                                           final Estimate estimate,
                                           final int pushed,
                                           final List<Long> keysByStep,
                                           final long opened,
                                           final List<List<Object>> examples) {
        int opener = -1;
        int closer = -1;
        for (int i = 0; i < call.steps().size(); i++) {
            final JoinStep step = call.steps().get(i);
            if (!step.keysOnly() && !step.step().missing()) {
                if (opener < 0) {
                    opener = i;
                }
                closer = i;
            }
        }
        if (opener < 0 || closer <= opener) {
            return null;
        }
        if (pushed == closer) {
            final long estimated = Math.max(0, Objects.requireNonNullElse(estimate.steps().get(opener).keys(), 0L)
                                               - keysByStep.get(opener));
            return new DataQuality(estimated, opener, call.steps().get(opener).doc(), closer,
                    call.steps().get(closer).doc(), List.of(), true);
        }
        return new DataQuality(opened, opener, call.steps().get(opener).doc(), closer,
                call.steps().get(closer).doc(), examples.stream()
                .map(key -> String.join(", ", key.stream().map(JoinKeys::text).toList()))
                .toList(), false);
    }

    private void audit(final Call call, final Outcome outcome) {
        for (int d = 0; d < call.docs().size(); d++) {
            final DocRef doc = call.docs().get(d);
            final StringBuilder sb = new StringBuilder("streaming join (sequence_query, engine stream)");
            for (int i = 0; i < call.steps().size(); i++) {
                final JoinStep step = call.steps().get(i);
                if (step.doc().equals(doc)) {
                    sb.append("\nstep ").append(i + 1).append(step.keysOnly()
                                    ? " (keys)"
                                    : "").append(": ").append(EqlQueryWriter.wherePipeline(step.step()).strip())
                            .append(" by ").append(step.step().joinFields());
                }
            }
            if (call.maxspan() != null) {
                sb.append("\nmaxspan: ").append(EqlQueryWriter.maxspanText(call.maxspan()));
            }
            if (call.timeRange() != null) {
                sb.append("\ntime: ").append(NullSafe.nonBlankStringElse(call.timeRange().getFrom(), "any"))
                        .append(" to ").append(NullSafe.nonBlankStringElse(call.timeRange().getTo(), "any"));
            }
            final long events = outcome.timed()
                    ? EqlSequenceResults.eventCount(outcome.rows(), tableSteps(call.steps()), d + 1).events()
                    : outcome.kept();
            auditLog.searched(call.context().session(), doc, sb.toString(), events,
                    "Streaming join by the " + SequenceQueryTool.NAME + " tool");
        }
    }

    // ------------------------------------------------------------------------------------------------
    // The result

    private static Map<String, Object> structured(final Call call, final Outcome outcome) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("engine", ENGINE);
        map.put("engine_reason", call.reason());
        map.put("indexes", call.docs().stream().map(doc -> {
            final Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("type", doc.getType());
            ref.put("uuid", doc.getUuid());
            ref.put("name", doc.getName());
            return ref;
        }).toList());
        if (call.maxspan() != null) {
            map.put("maxspan", EqlQueryWriter.maxspanText(call.maxspan()));
            map.put("maxspan_source", call.maxspanSource());
        }
        if (call.timeRange() != null) {
            final Map<String, Object> time = new LinkedHashMap<>();
            time.put("from", call.timeRange().getFrom());
            time.put("to", call.timeRange().getTo());
            map.put("time_range", time);
        }
        map.putAll(call.zone().structured());
        map.put("steps", stepsStructured(call, outcome));
        if (!call.fieldNotes().isEmpty()) {
            map.put("field_notes", call.fieldNotes());
        }
        final Estimate estimate = outcome.estimate();
        if (estimate.known()) {
            final Map<String, Object> estimated = new LinkedHashMap<>();
            estimated.put("rows", estimate.rows());
            estimated.put("keys", estimate.keys());
            estimated.put("text", estimateText(estimate, outcome.workers(), call.config()));
            map.put("estimate", estimated);
        } else if (estimate.note() != null) {
            map.put("estimate_note", estimate.note());
        }
        map.put("workers", outcome.workers());
        map.put("key_ranges", outcome.ranges());
        if (outcome.sampled() != null) {
            map.put("ranges_note", outcome.sampled());
        }
        if (outcome.pushed() >= 0) {
            map.put("pushed_down", pushedNote(outcome));
        }
        map.put("rows_read", outcome.rowsRead());
        map.put("keys_merged", outcome.keysMerged());
        map.put("took_ms", outcome.took().toMillis());
        map.put("time_limit_s", outcome.timeLimit().limit().toSeconds());
        if (outcome.timeLimit().note() != null) {
            map.put("time_note", outcome.timeLimit().note());
        }
        map.put(outcome.timed()
                ? "sequence_count"
                : "entity_match_count", outcome.matches());
        map.put("entity_count", outcome.entities());
        map.put("limit", call.limit());
        map.put("truncated", outcome.matches() > outcome.kept());
        if (outcome.reached() != null) {
            map.put("ended_early", endedEarly(outcome, call.config()));
        }
        if (outcome.cutKeys() > 0) {
            map.put("keys_cut", outcome.cutKeys());
        }
        map.put("note", note(call, outcome));
        if (outcome.timed()) {
            map.put("entities", outcome.entityRows().stream().limit(SequenceQueryTool.SHOWN_ROWS).map(entity -> {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("keys", entity.keys());
                row.put("sequences", entity.sequences());
                row.put("first", call.zone().inZone(EqlSequenceResults.iso(entity.first())));
                row.put("last", call.zone().inZone(EqlSequenceResults.iso(entity.last())));
                row.put("shortest_gap_s", EqlSequenceResults.seconds(entity.shortestSpan()));
                return row;
            }).toList());
        }
        map.put("columns", outcome.columns());
        map.put("rows", outcome.cells().stream().limit(SequenceQueryTool.SHOWN_ROWS).toList());
        if (!outcome.cells().isEmpty()) {
            map.put("table", call.out().toString());
        }
        if (!outcome.diagnosis().isEmpty()) {
            map.put("diagnosis", outcome.diagnosis());
        }
        if (outcome.quality() != null && outcome.quality().count() > 0) {
            final DataQuality quality = outcome.quality();
            final Map<String, Object> dq = new LinkedHashMap<>();
            dq.put("opened_not_closed", quality.count());
            dq.put("opening_step", quality.opener() + 1);
            dq.put("opening_index", quality.openerDoc().getName());
            dq.put("closing_step", quality.closer() + 1);
            dq.put("closing_index", quality.closerDoc().getName());
            dq.put("examples", quality.examples());
            if (quality.estimated()) {
                dq.put("estimated", true);
            }
            dq.put("note", qualityNote(quality));
            map.put("data_quality", dq);
        }
        map.put("caveat", CAVEAT);
        map.put("review_queries", outcome.reviews().stream().map(review -> {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", review.review().doc().getName());
            entry.put("uuid", review.review().doc().getUuid());
            entry.put("query", review.review().query());
            entry.put("exact", review.review().exact());
            entry.put("valid", review.invalid() == null);
            if (review.invalid() != null) {
                entry.put("invalid", review.invalid());
            }
            entry.put("note", review.note());
            return entry;
        }).toList());
        if (!outcome.reviews().isEmpty()) {
            map.put("review_hint", SequenceQueryTool.reviewHint(outcome.reviews().size()));
        }
        return map;
    }

    private static List<Map<String, Object>> stepsStructured(final Call call, final Outcome outcome) {
        final List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < call.steps().size(); i++) {
            final JoinStep step = call.steps().get(i);
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("step", i + 1);
            entry.put("index", step.doc().getName());
            entry.put("by", step.step().joinFields());
            if (!step.keys().getFirst().transforms().isEmpty()) {
                entry.put("transform", step.keys().getFirst().transforms().stream()
                        .map(transform -> transform.name().toLowerCase(Locale.ROOT))
                        .toList());
            }
            entry.put("read_as", step.keysOnly()
                    ? "keys"
                    : "events");
            final StepEstimate estimate = outcome.estimate().steps().get(i);
            if (estimate.rows() != null) {
                entry.put("events", estimate.rows());
            }
            if (estimate.keys() != null) {
                entry.put("keys", estimate.keys());
            }
            entry.put("keys_read", outcome.keysByStep().get(i));
            steps.add(entry);
        }
        return steps;
    }

    private static String pushedNote(final Outcome outcome) {
        return "Step " + (outcome.pushed() + 1) + " has " + number(outcome.pushedKeys()) + " keys, so it was read "
               + "first and its keys pushed down as a terms filter on the other steps, cutting them before they "
               + "streamed.";
    }

    private static String endedEarly(final Outcome outcome, final AiWorkspaceConfig config) {
        return switch (outcome.reached()) {
            case ROWS -> "The join stopped at its cap of " + number(config.getJoinMaxRows()) + " rows read, so "
                         + "these are the matches up to there, not all: narrow the time range or the steps.";
            case KEYS -> "The join stopped at its cap of " + number(config.getJoinMaxKeys()) + " keys merged, so "
                         + "these are the matches up to there, not all: narrow the time range or the steps.";
            case TIME -> "The join stopped at its time limit (" + duration(outcome.timeLimit().limit().toSeconds())
                         + "), so these are the matches up to there, not all: narrow the time range or the steps.";
            case STOPPED -> "The user stopped the join.";
        };
    }

    // How many matches, and what the call should know of them.
    private static String note(final Call call, final Outcome outcome) {
        final StringBuilder sb = new StringBuilder();
        if (outcome.timed()) {
            sb.append(number(outcome.matches())).append(" sequence").append(plural(outcome.matches()))
                    .append(" found, for ").append(number(outcome.entities())).append(" entit")
                    .append(outcome.entities() == 1
                            ? "y"
                            : "ies").append(". ").append(COUNTED);
        } else {
            sb.append(number(outcome.matches())).append(" entit").append(outcome.matches() == 1
                    ? "y holds"
                    : "ies hold").append(" every step.");
        }
        if (outcome.matches() > outcome.kept()) {
            sb.append(" The first ").append(number(outcome.kept())).append(" in key order are in the table (limit ")
                    .append(number(call.limit())).append(", at most ").append(number(MAX_LIMIT))
                    .append("): don't present them as all.");
        }
        if (outcome.reached() != null) {
            sb.append(' ').append(endedEarly(outcome, call.config()));
        }
        if (outcome.cutKeys() > 0) {
            sb.append(' ').append(number(outcome.cutKeys())).append(" key").append(plural(outcome.cutKeys()))
                    .append(" had more than ").append(number(KeyGroupSource.MAX_KEY_ROWS)).append(" events in a "
                                                                                                + "step, of which "
                                                                                                + "only the first "
                                                                                                + "were matched.");
        }
        return sb.toString();
    }

    private static String qualityNote(final DataQuality quality) {
        return number(quality.count()) + " entit" + (quality.count() == 1
                ? "y"
                : "ies") + " had step " + (quality.opener() + 1) + " (" + quality.openerDoc().getName()
               + ") but never step " + (quality.closer() + 1) + " (" + quality.closerDoc().getName() + ") in the "
               + "period: either it didn't happen, or " + quality.closerDoc().getName() + "'s source may be missing "
               + "events. Check that source's events for them before saying it didn't happen." + (quality.estimated()
                ? " (Estimated: step " + (quality.closer() + 1) + " was pushed down, so only its entities' step "
                  + (quality.opener() + 1) + " events were read.)"
                : "");
    }

    private static String markdown(final Call call, final Outcome outcome) {
        final StringBuilder sb = new StringBuilder("Ran the sequence as a streaming join in Stroom on ")
                .append(String.join(", ", call.docs().stream().map(DataSourceAccess::describe).toList()))
                .append(" (").append(outcome.workers()).append(" worker").append(plural(outcome.workers()))
                .append(" over ").append(outcome.ranges()).append(" key range").append(plural(outcome.ranges()))
                .append(", ").append(number(outcome.rowsRead())).append(" rows read, ")
                .append(number(outcome.took().toMillis())).append(" ms)");
        if (call.maxspan() != null) {
            sb.append(", with maxspan ").append(EqlQueryWriter.maxspanText(call.maxspan())).append(" (")
                    .append(call.maxspanSource()).append(')');
        }
        sb.append(". ").append(call.reason()).append('\n');
        call.fieldNotes().forEach(note -> sb.append(note).append('\n'));
        if (call.timeRange() != null) {
            sb.append("Time: ").append(NullSafe.nonBlankStringElse(call.timeRange().getFrom(), "any"))
                    .append(" to ").append(NullSafe.nonBlankStringElse(call.timeRange().getTo(), "any"))
                    .append(".\n");
        }
        sb.append(call.zone().stated()).append('\n');
        if (outcome.estimate().known()) {
            sb.append("\nEstimated first: ").append(estimateText(outcome.estimate(), outcome.workers(),
                    call.config())).append(".");
        } else if (outcome.estimate().note() != null) {
            sb.append('\n').append(outcome.estimate().note());
        }
        if (outcome.pushed() >= 0) {
            sb.append(' ').append(pushedNote(outcome));
        }
        if (outcome.timeLimit().note() != null) {
            sb.append(' ').append(outcome.timeLimit().note());
        }
        if (outcome.sampled() != null) {
            sb.append(' ').append(outcome.sampled());
        }
        sb.append("\n\n").append(note(call, outcome)).append('\n');
        if (outcome.timed() && !outcome.entityRows().isEmpty()) {
            final List<String> columns = new ArrayList<>(call.steps().getFirst().step().joinFields());
            columns.addAll(List.of("sequences", "first", "last", "shortest gap (s)"));
            final List<List<String>> cells = outcome.entityRows().stream()
                    .map(entity -> {
                        final List<String> row = new ArrayList<>(entity.keys());
                        row.add(String.valueOf(entity.sequences()));
                        row.add(call.zone().inZone(EqlSequenceResults.iso(entity.first())));
                        row.add(call.zone().inZone(EqlSequenceResults.iso(entity.last())));
                        row.add(EqlSequenceResults.seconds(entity.shortestSpan()));
                        return row;
                    })
                    .toList();
            sb.append("\nBy entity (of the sequences in the table):\n\n")
                    .append(ToolTables.preview(columns, cells, SequenceQueryTool.SHOWN_ROWS)).append('\n');
        }
        if (!outcome.cells().isEmpty()) {
            sb.append('\n').append(outcome.timed()
                            ? "Sequences"
                            : "Entities").append(":\n\n")
                    .append(ToolTables.preview(outcome.columns(), outcome.cells(), SequenceQueryTool.SHOWN_ROWS))
                    .append("\n\nThey are in ").append(call.out()).append(outcome.timed()
                            ? " (each step's StreamId and EventId, for fetch_events and search_events)."
                            : ".").append('\n');
        }
        if (!outcome.diagnosis().isEmpty()) {
            sb.append("\nWhy nothing may have been found:\n");
            outcome.diagnosis().forEach(line -> sb.append("- ").append(line).append('\n'));
        }
        if (outcome.quality() != null && outcome.quality().count() > 0) {
            sb.append("\nData quality: ").append(qualityNote(outcome.quality()));
            if (!outcome.quality().examples().isEmpty()) {
                sb.append(" E.g. ").append(String.join("; ", outcome.quality().examples())).append('.');
            }
            sb.append('\n');
        }
        sb.append('\n').append(CAVEAT);
        if (!outcome.reviews().isEmpty()) {
            sb.append("\n\n").append(SequenceQueryTool.reviewHint(outcome.reviews().size()));
            for (final ReviewQuery review : outcome.reviews()) {
                sb.append("\n\n").append(DataSourceAccess.describe(review.review().doc())).append(": ")
                        .append(review.note());
                if (review.invalid() != null) {
                    sb.append(" It isn't valid StroomQL (").append(review.invalid()).append("), so don't offer it.");
                }
                sb.append("\n\n```\n").append(review.review().query()).append("\n```");
            }
        }
        return sb.toString();
    }

    private static String notUsable(final List<DocRef> docs, final PermissionException e) {
        final String text = NullSafe.string(e.getMessage());
        return docs.stream()
                .filter(doc -> text.contains(doc.getUuid()))
                .findFirst()
                .map(doc -> "You may not use Elastic Index '" + doc.getName() + "' (" + doc.getUuid() + "), so the "
                            + "join can't read it.")
                .orElse(DataSourceAccess.NOT_FOUND);
    }

    /// @param n A number.
    /// @return It with thousands separated by commas.
    static String number(final long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static String plural(final long n) {
        return n == 1
                ? ""
                : "s";
    }

    private static String duration(final long seconds) {
        if (seconds < 90) {
            return seconds + " second" + plural(seconds);
        }
        final long minutes = Math.round(seconds / 60.0);
        return minutes + " minute" + plural(minutes);
    }


    // --------------------------------------------------------------------------------


    /// One step of a join.
    ///
    /// @param step      The step: its doc's position, conditions, join fields, missing, runs and fields to show.
    /// @param doc       Its doc.
    /// @param keys      Its join fields as read, with the step's transforms.
    /// @param kinds     How each join field's values compare.
    /// @param keysOnly  True if it is read as keys (an entity condition at any time), not events in time.
    /// @param timeField Its doc's time field, or null.
    record JoinStep(Step step,
                    DocRef doc,
                    List<KeyField> keys,
                    List<JoinKeys.Kind> kinds,
                    boolean keysOnly,
                    String timeField) {

    }


    // --------------------------------------------------------------------------------


    /// What to join, and where the result goes.
    ///
    /// @param steps         The steps.
    /// @param until         The until step, or null.
    /// @param docs          The docs, in order (the steps' positions).
    /// @param timeFields    Each doc's time field, or null.
    /// @param fieldsByDoc   Each doc's fields.
    /// @param maxspan       The window, or null when fewer than two steps are read in time.
    /// @param maxspanSource Where the window came from.
    /// @param timeRange     The time range, or null.
    /// @param limit         The most sequences to keep.
    /// @param reason        Why the streaming engine ran.
    /// @param fieldNotes    What was done to the steps' fields.
    /// @param runAnyway     True to run a join near the caps without asking.
    /// @param callId        The call's id, for the form asked.
    /// @param context       The call's context.
    /// @param config        The limits.
    /// @param workspace     The session's workspace.
    /// @param out           The table's file.
    /// @param zone          The zone the result's times are in.
    /// @param timeLimit     The query time limit, which the join runs within unless the user agrees to the join
    ///                      time limit.
    /// @param invalid       Why a StroomQL query isn't valid, or null if it is.
    /// @param maxQueryLength The longest a review query may be.
    record Call(List<JoinStep> steps,
                JoinStep until,
                List<DocRef> docs,
                List<String> timeFields,
                Map<DocRef, List<QueryField>> fieldsByDoc,
                Duration maxspan,
                String maxspanSource,
                TimeRange timeRange,
                int limit,
                String reason,
                List<String> fieldNotes,
                boolean runAnyway,
                String callId,
                ToolCallContext context,
                AiWorkspaceConfig config,
                SessionWorkspace workspace,
                WorkspacePath out,
                ToolTimeZone zone,
                Duration timeLimit,
                Function<String, String> invalid,
                int maxQueryLength) {

        /// Copies the lists.
        Call {
            steps = List.copyOf(steps);
            docs = List.copyOf(docs);
            timeFields = Collections.unmodifiableList(new ArrayList<>(timeFields));
            fieldNotes = List.copyOf(fieldNotes);
        }
    }


    // --------------------------------------------------------------------------------


    /// How long a join may run.
    ///
    /// @param limit The time limit its workers share.
    /// @param note  What the result says of it, or null for nothing.
    record TimeLimit(Duration limit, String note) {

    }


    // --------------------------------------------------------------------------------


    /// One step's estimate.
    ///
    /// @param rows     Its events (up to the rows cap and one), or null if not known.
    /// @param keys     Its keys, or null if not known.
    /// @param examples Its commonest keys.
    record StepEstimate(Long rows, Long keys, List<List<Object>> examples) {

        /// Not known.
        static final StepEstimate UNKNOWN = new StepEstimate(null, null, List.of());

        /// Copies the list.
        StepEstimate {
            examples = examples == null
                    ? List.of()
                    : List.copyOf(examples);
        }
    }


    // --------------------------------------------------------------------------------


    /// The join's estimate.
    ///
    /// @param steps     Each step's.
    /// @param joinSteps The steps, for which are read as events.
    /// @param note      Why it isn't known, or null.
    record Estimate(List<StepEstimate> steps, List<JoinStep> joinSteps, String note) {

        /// @return True if every step was estimated.
        boolean known() {
            return steps.stream().allMatch(step -> step.rows() != null && step.keys() != null);
        }

        /// @return The rows the join reads: each step's events (its keys for a step read as keys).
        long rows() {
            long rows = 0;
            for (int i = 0; i < steps.size(); i++) {
                final StepEstimate step = steps.get(i);
                rows += joinSteps.get(i).keysOnly()
                        ? Objects.requireNonNullElse(step.keys(), 0L)
                        : Objects.requireNonNullElse(step.rows(), 0L);
            }
            return rows;
        }

        /// @return The most keys of a step.
        long keys() {
            return steps.stream()
                    .mapToLong(step -> Objects.requireNonNullElse(step.keys(), 0L))
                    .max()
                    .orElse(0);
        }
    }


    // --------------------------------------------------------------------------------


    /// The keys whose first timed step happened and whose last never did.
    ///
    /// @param count     How many.
    /// @param opener    The first timed step's position.
    /// @param openerDoc Its doc.
    /// @param closer    The last timed step's position.
    /// @param closerDoc Its doc.
    /// @param examples  A few of them.
    /// @param estimated True if the count is estimated (the last step was pushed down).
    record DataQuality(long count,
                       int opener,
                       DocRef openerDoc,
                       int closer,
                       DocRef closerDoc,
                       List<String> examples,
                       boolean estimated) {

    }


    // --------------------------------------------------------------------------------


    /// A review query, with whether it is valid and what it finds.
    ///
    /// @param review  The query.
    /// @param invalid Why it isn't valid StroomQL, or null if it is.
    /// @param note    What it finds.
    record ReviewQuery(Review review, String invalid, String note) {

    }


    // --------------------------------------------------------------------------------


    /// Everything the result says.
    ///
    /// @param estimate   The estimate.
    /// @param workers    The workers.
    /// @param ranges     The key ranges.
    /// @param pushed     The step pushed down, or -1.
    /// @param pushedKeys Its keys.
    /// @param sampled    How the ranges were cut from a sample, or null.
    /// @param matches    The matches (sequences, or entities when every step is read as keys).
    /// @param entities   The entities with a match.
    /// @param kept       The matches in the table.
    /// @param columns    The table's columns.
    /// @param cells      The table's rows.
    /// @param rows       The sequences in the table.
    /// @param entityRows The summary per entity of the sequences in the table.
    /// @param reviews    The review queries.
    /// @param diagnosis  Why nothing may have been found; empty if something was.
    /// @param quality    The keys that began and never ended, or null for a join that isn't bounded.
    /// @param reached    Why the join ended early, or null.
    /// @param rowsRead   The rows read.
    /// @param keysMerged The keys merged.
    /// @param cutKeys    The keys with more events than are held at once.
    /// @param keysByStep The keys each step had, as read.
    /// @param took       How long it took.
    /// @param timed      True if any step is read as events.
    /// @param timeLimit  How long the join could run, and what was said of it.
    record Outcome(Estimate estimate,
                   int workers,
                   int ranges,
                   int pushed,
                   int pushedKeys,
                   String sampled,
                   long matches,
                   long entities,
                   int kept,
                   List<String> columns,
                   List<List<String>> cells,
                   List<EqlSequenceResults.Row> rows,
                   List<Entity> entityRows,
                   List<ReviewQuery> reviews,
                   List<String> diagnosis,
                   DataQuality quality,
                   JoinBudget.Limit reached,
                   long rowsRead,
                   long keysMerged,
                   long cutKeys,
                   List<Long> keysByStep,
                   Duration took,
                   boolean timed,
                   TimeLimit timeLimit) {

    }
}

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

package stroom.mcp.impl.tools;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.AcceptedErrors;
import stroom.ai.impl.tools.CallProgress;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ErrorTriage;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.SteppingWalks.Walk;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.ai.impl.tools.validation.XsltChecks;
import stroom.ai.impl.tools.validation.XsltSafety;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.docref.DocRef;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.factory.ElementRegistry;
import stroom.pipeline.factory.ElementRegistryFactory;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/// What the stepping tools (`step_pipeline`, `step_sample`, `step_records` and `compare_outputs`) share, ported from
/// the Stroom MCP server's `tools/stepping.py`: reading the pipeline, refusing draft XSLT check_xslt rejects, a
/// pipeline with a mistyped property or reference data not loaded yet, triaging what the steps met, the blocking
/// check for EventDetail/Unknown nobody agreed to, recording a clean step in the build's record, stepping records
/// spread over several streams, and keeping within the session's time limit for a long call.
///
/// Stepping is done through Stroom's stepping service in-process, as the user, who needs the Stepping permission;
/// a stepping session lives on this node. Each stream's steps are made in one session ([SteppingWalks]).
class PipelineStepping {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PipelineStepping.class);

    /// The most records one call steps (the Stroom MCP server's max_sample_records).
    static final int MAX_SAMPLE_RECORDS = EventLocations.MAX_RECORDS;
    /// The most characters of output a result gives in all (the Stroom MCP server's max_stream_chars).
    static final int MAX_STREAM_CHARS = 20_000;
    /// The longest one step may take.
    static final Duration STEP_TIME_LIMIT = EventLocations.STEP_TIME_LIMIT;
    /// What is kept of the session's time limit for a long call for the call's work after stepping (triage, the
    /// build's record, the plan).
    static final Duration MARGIN = Duration.ofSeconds(10);
    /// The examples kept per triaged group.
    static final int EXAMPLES = 3;
    /// What a call stopped by its client says.
    static final String CANCELLED = "Stepping was stopped: the call was cancelled.";
    /// The description of `draft_code`, as each stepping tool gives it.
    static final String DRAFT_CODE_DESCRIPTION = "Unsaved code to step with instead of the saved documents, keyed "
                                                 + "by element id, e.g. {'translationFilter': '<xsl:stylesheet "
                                                 + "...>'}. Nothing is saved.";
    /// The description of `pipeline_uuid`, as each stepping tool gives it.
    static final String PIPELINE_UUID_DESCRIPTION = "UUID of the pipeline to step.";

    private final SteppingWalks steppingWalks;
    private final DocumentAccess documentAccess;
    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<ElementRegistryFactory> elementRegistryFactoryProvider;
    private final StreamSummaries streamSummaries;
    private final AcceptedErrors acceptedErrors;
    private final CheckXsltTool checkXslt;
    private final StreamReader streamReader;
    private final SampleStreamTexts sampleStreamTexts;
    private final WriteGuard guard;
    private final KeptMappings keptMappings;
    private final BuildRecords buildRecords;
    private final BuildPlan plan;
    private final AiAuditLog auditLog;

    @Inject
    PipelineStepping(final SteppingWalks steppingWalks,
                     final DocumentAccess documentAccess,
                     final Provider<SecurityContext> securityContextProvider,
                     final Provider<ElementRegistryFactory> elementRegistryFactoryProvider,
                     final StreamSummaries streamSummaries,
                     final AcceptedErrors acceptedErrors,
                     final CheckXsltTool checkXslt,
                     final StreamReader streamReader,
                     final SampleStreamTexts sampleStreamTexts,
                     final WriteGuard guard,
                     final KeptMappings keptMappings,
                     final BuildRecords buildRecords,
                     final BuildPlan plan,
                     final AiAuditLog auditLog) {
        this.steppingWalks = steppingWalks;
        this.documentAccess = documentAccess;
        this.securityContextProvider = securityContextProvider;
        this.elementRegistryFactoryProvider = elementRegistryFactoryProvider;
        this.streamSummaries = streamSummaries;
        this.acceptedErrors = acceptedErrors;
        this.checkXslt = checkXslt;
        this.streamReader = streamReader;
        this.sampleStreamTexts = sampleStreamTexts;
        this.guard = guard;
        this.keptMappings = keptMappings;
        this.buildRecords = buildRecords;
        this.plan = plan;
        this.auditLog = auditLog;
    }

    // --------------------------------------------------------------------------------
    // Before stepping.
    // --------------------------------------------------------------------------------

    /// @throws ToolRefusal if the user doesn't have the Stepping permission.
    void checkCanStep() {
        boolean can;
        try {
            can = securityContextProvider.get().hasAppPermission(AppPermission.STEPPING_PERMISSION);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to check the Stepping permission", e);
            can = false;
        }
        if (!can) {
            throw new ToolRefusal("You don't have permission to step pipelines (Stepping), so nothing can be "
                                  + "stepped.");
        }
    }

    /// Refuses draft XSLT that check_xslt rejects, with its reasons. Stepping would otherwise report the symptom
    /// only: an unknown stroom: function as a compile error, or a missing namespace as empty output.
    ///
    /// A function the element's saved XSLT already calls (e.g. stroom:http-call()) stays allowed, as when the code
    /// is saved: a person put it there.
    ///
    /// @param pipelineUuid The pipeline stepped.
    /// @param draftCode    Draft code by element, or null.
    /// @throws ToolRefusal if a draft XSLT is rejected.
    void checkDrafts(final String pipelineUuid, final Map<String, String> draftCode) {
        NullSafe.map(draftCode).forEach((element, code) -> {
            if (code == null || !code.contains(XmlTrees.XSL)) {
                return;
            }
            final Map<String, Object> result = checkXslt.check(code, null, null);
            if (!Boolean.TRUE.equals(result.get("ok"))) {
                final Set<String> allowed = XsltSafety.keptBy(XsltChecks.unescaped(code).text(),
                        savedXslt(pipelineUuid, element));
                final List<String> errors = NullSafe.list((List<?>) result.get("errors")).stream()
                        .map(String::valueOf)
                        .filter(error -> !allowed.contains(error))
                        .toList();
                if (!errors.isEmpty()) {
                    throw new ToolRefusal("draft_code[" + PyText.repr(element)
                                          + "] is not stepped: " + String.join("; ", errors));
                }
            }
        });
    }

    /// Refuses draft XSLT that calls what the tools won't save ([XsltSafety#unsafeInXslt(String)]), unless the
    /// element's saved XSLT calls it too: stepping runs it with Stroom's access, as processing would.
    ///
    /// @param pipelineUuid The pipeline stepped.
    /// @param draftCode    Draft code by element, or null.
    /// @throws ToolRefusal if a draft XSLT is refused.
    void refuseUnsafeDrafts(final String pipelineUuid, final Map<String, String> draftCode) {
        NullSafe.map(draftCode).forEach((element, code) -> {
            if (code == null || !code.contains(XmlTrees.XSL)) {
                return;
            }
            final String xslt = XsltChecks.unescaped(code).text();
            final List<XsltSafety.Unsafe> unsafe = XsltSafety.unsafeInXslt(xslt);
            if (unsafe.isEmpty()) {
                return;
            }
            final Set<String> allowed = XsltSafety.keptBy(xslt, savedXslt(pipelineUuid, element));
            final List<String> refused = unsafe.stream()
                    .map(XsltSafety.Unsafe::why)
                    .filter(why -> !allowed.contains(why))
                    .toList();
            if (!refused.isEmpty()) {
                throw new ToolRefusal("draft_code[" + PyText.repr(element) + "] is not stepped: "
                                      + String.join("; ", refused));
            }
        });
    }

    // The text of the XSLT the pipeline's element runs now, or null if it has none or it can't be read.
    private String savedXslt(final String pipelineUuid, final String element) {
        try {
            return MergedPipeline.load(documentAccess, pipelineUuid).stream()
                    .flatMap(merged -> merged.translationDocs(pipelineUuid).stream())
                    .filter(doc -> element.equals(doc.element()) && XsltDoc.TYPE.equals(doc.doc().getType()))
                    .findFirst()
                    .flatMap(doc -> documentAccess.read(doc.doc(), XsltDoc.class))
                    .map(XsltDoc::getData)
                    .orElse(null);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the saved XSLT of " + element + ": " + e.getMessage());
            return null;
        }
    }

    /// @param uuid A pipeline's UUID.
    /// @return It, as stepping needs it.
    /// @throws ToolRefusal if it, or a template it inherits from, can't be read.
    SteppedPipeline load(final String uuid) {
        final MergedPipeline merged = MergedPipeline.load(documentAccess, uuid)
                .orElseThrow(() -> new ToolRefusal("Pipeline " + uuid + " can't be read: it doesn't exist, or you "
                                                   + "don't have permission to view it or a template it inherits "
                                                   + "from"));
        final ElementRegistry registry = elementRegistry();
        return SteppedPipeline.of(merged, (type, name) -> declaredType(registry, type, name));
    }

    /// Building and validating pipelines uses only a feed's own streams.
    ///
    /// @param streamIds Sample streams.
    /// @throws ToolRefusal if any is older than its feed.
    void refuseOlderThanFeed(final List<Long> streamIds) {
        sampleStreamTexts.refuseOlderThanFeed(streamIds);
    }

    /// A pipeline reference to a feed with no processed reference stream yet: a step now finds no reference data,
    /// and Stroom keeps that answer for 10 minutes (its effective stream cache), so steps after the reference data
    /// is processed still find none (seen: eval case 15, Haiku, which then gave up on reference data for
    /// dictionaries).
    ///
    /// @param pipeline The pipeline.
    /// @throws ToolRefusal if a reference feed has no stream of its type yet.
    void refuseUnloadedReferences(final SteppedPipeline pipeline) {
        final List<String> missing = new ArrayList<>();
        for (final PipelineReference reference : pipeline.references()) {
            final String feed = NullSafe.get(reference.getFeed(), DocRef::getName);
            if (NullSafe.isBlankString(feed)) {
                continue;
            }
            final String kind = NullSafe.isBlankString(reference.getStreamType())
                    ? "Reference"
                    : reference.getStreamType();
            final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                    .addTextTerm(MetaFields.FEED, Condition.EQUALS, feed)
                    .addTextTerm(MetaFields.TYPE, Condition.EQUALS, kind)
                    .build();
            final List<MetaRow> rows = NullSafe.list(NullSafe.get(streamReader.find(expression, 5, true),
                    ResultPage::getValues));
            if (rows.stream().noneMatch(row -> row.getMeta() != null
                                               && row.getMeta().getStatus() != Status.DELETED)) {
                missing.add(feed + " (" + kind + ")");
            }
        }
        if (!missing.isEmpty()) {
            throw new ToolRefusal("Pipeline '" + pipeline.name() + "' looks up reference data from "
                                  + String.join(", ", missing) + ", which has no stream of that type yet. Process "
                                  + "the reference data first (create_processor_filter on its reference pipeline, "
                                  + "then wait_for_processing), then step: a step now would find none, and Stroom "
                                  + "keeps that answer for 10 minutes, so steps after it is loaded would still "
                                  + "fail.");
        }
    }

    // --------------------------------------------------------------------------------
    // Stepping.
    // --------------------------------------------------------------------------------

    /// @param pipeline The pipeline.
    /// @param streamId The stream to walk.
    /// @param code     Draft code by element, or null for the saved code.
    /// @return A walk of the stream, in one stepping session, to close when done.
    Walk open(final SteppedPipeline pipeline, final long streamId, final Map<String, String> code) {
        return steppingWalks.open(pipeline.doc(), streamId, code, STEP_TIME_LIMIT);
    }

    /// Up to `cap` records stepped, an even share from each stream, then what a short stream left over taken from
    /// those with more. In stream order, then record order.
    ///
    /// Stroom before gchq/stroom#5662 read a stream from its start for every step, so a step cost more the further
    /// in its record was (seen in production: 0.15 s near the start, 1.1 s at the 151st record), and 200 records
    /// from one stream took 132 s of a write_documentation call. Fifty from each of four are a fraction of that,
    /// and a better sample: kinds that only the later streams hold are in it. Each stream's share is stepped in
    /// one session, so Stroom captures the stream once for it.
    ///
    /// @param context   The call's context, for its progress.
    /// @param pipeline  The pipeline.
    /// @param streamIds The streams.
    /// @param code      Draft code by element, or null for the saved code.
    /// @param cap       The most records.
    /// @return The steps, and whether the call's time ran out before they were all made.
    /// @throws ToolTimeoutException if the call is cancelled.
    Spread stepSpread(final ToolCallContext context,
                      final SteppedPipeline pipeline,
                      final List<Long> streamIds,
                      final Map<String, String> code,
                      final int cap) {
        final Map<Long, SpreadState> states = new LinkedHashMap<>();
        streamIds.forEach(id -> states.put(id, new SpreadState()));
        // Told once, then now and then while stepping (seen: 200 records took 132 s of a write_documentation
        // call).
        final CallProgress progress = CallProgress.of(context);
        progress.say("Stepping up to " + cap + " records of " + streamIds.size() + " stream"
                     + (streamIds.size() != 1
                ? "s"
                : "") + " through " + pipeline.name());
        boolean stopped = false;
        for (final long streamId : streamIds) {
            stopped = take(progress, pipeline, streamId, states, code, ceilDiv(cap, Math.max(1, streamIds.size())),
                    cap);
            if (stopped) {
                break;
            }
        }
        while (!stopped) {
            final int left = cap - total(states);
            final List<Long> more = streamIds.stream().filter(id -> !states.get(id).done).toList();
            if (left <= 0 || more.isEmpty()) {
                break;
            }
            for (final long streamId : more) {
                stopped = take(progress, pipeline, streamId, states, code, ceilDiv(left, more.size()), cap);
                if (stopped) {
                    break;
                }
            }
        }
        final List<Spread.Step> steps = new ArrayList<>();
        streamIds.forEach(id -> steps.addAll(states.get(id).steps));
        return new Spread(steps.subList(0, Math.min(cap, steps.size())), stopped);
    }

    // One stream's share, in a session for the share, closed with it. True if the call's time ran out.
    private boolean take(final CallProgress progress,
                         final SteppedPipeline pipeline,
                         final long streamId,
                         final Map<Long, SpreadState> states,
                         final Map<String, String> code,
                         final int share,
                         final int cap) {
        final SpreadState state = states.get(streamId);
        int budget = share;
        try (final Walk walk = open(pipeline, streamId, code)) {
            if (state.result == null) {
                state.result = walk.first();
            }
            while (budget > 0 && isFound(state.result)) {
                final StepLocation location = state.result.getFoundLocation();
                state.steps.add(new Spread.Step(streamId, location, state.result));
                budget--;
                progress.nowAndThen("Stepping through " + pipeline.name() + ": " + total(states) + " of up to "
                                    + cap + " records stepped");
                if (timeIsUp()) {
                    // Before the call's time runs out: fewer records, rather than a call refused part way.
                    state.done = true;
                    return true;
                }
                state.result = walk.forward(location);
            }
            state.done = !isFound(state.result);
            return false;
        } catch (final ToolTimeoutException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw e;
            }
            state.done = true;
            return true;
        }
    }

    /// Each record's output at the element, up to `cap` records spread over the streams ([#stepSpread]).
    ///
    /// @return The outputs by record key, and whether the call's time ran out first.
    Outputs outputs(final ToolCallContext context,
                    final SteppedPipeline pipeline,
                    final List<Long> streamIds,
                    final String element,
                    final Map<String, String> code,
                    final int cap) {
        final Spread spread = stepSpread(context, pipeline, streamIds, code, cap);
        final Map<String, String> outputs = new LinkedHashMap<>();
        for (final Spread.Step step : spread.steps()) {
            outputs.put(SteppingWalks.recordKey(step.streamId(), step.location()),
                    SteppingWalks.output(step.result(), element));
        }
        return new Outputs(outputs, spread.stopped());
    }

    // --------------------------------------------------------------------------------
    // After stepping.
    // --------------------------------------------------------------------------------

    /// @param markers     The errors the steps met.
    /// @param pipeline    The pipeline stepped.
    /// @param recordCount How many records were stepped, or null if not known.
    /// @return The markers triaged by the environment's rules and Stroom's own, with the errors the user accepted
    /// as benign for the pipeline: `verdict`, `groups_by_class` and `groups`.
    Map<String, Object> triage(final List<ErrorTriage.Marker> markers,
                               final SteppedPipeline pipeline,
                               final Integer recordCount) {
        return streamSummaries.errorTriage().triage(markers, pipeline.own(), recordCount, EXAMPLES,
                acceptedErrors.acceptedFor(pipeline.doc().getUuid()));
    }

    /// A blocking group when a build's own pipeline writes EventDetail/Unknown from an XSLT saved without a
    /// mapping: build_translation_xslt refuses Unknown where the records show an action and puts the rest to the
    /// user, and a hand-written XSLT got past both (seen: every TRAFFIC record of a firewall sample as an empty
    /// Unknown).
    ///
    /// @param pipeline  The pipeline stepped.
    /// @param element   Its output element.
    /// @param unknown   The records that came out as Unknown.
    /// @param total     How many records were stepped.
    /// @param draftCode Draft code by element, or null.
    /// @param held      What the Unknown events hold, or an empty string.
    /// @return The group, or null when nothing needs agreeing.
    Map<String, Object> unagreedUnknown(final SteppedPipeline pipeline,
                                        final String element,
                                        final List<String> unknown,
                                        final int total,
                                        final Map<String, String> draftCode,
                                        final String held) {
        if (unknown.isEmpty() || draftCode != null) {
            return null;
        }
        try {
            final DocRef ref = pipeline.ref();
            if (!guard.tags(ref).contains(WriteGuard.MANAGED) || guard.copyOf(ref).isPresent()) {
                // Not the build's own new pipeline: someone else's design.
                return null;
            }
            if (keptMappings.keptMapping(ref.getUuid()).isPresent()) {
                // Saved from a mapping, where Unknown was checked and agreed.
                return null;
            }
        } catch (final RuntimeException e) {
            // The check is a guard on top; never fail the step over it.
            LOGGER.warn(() -> "Couldn't check pipeline " + pipeline.doc().getUuid() + " for Unknown events: "
                              + e.getMessage());
            return null;
        }
        final String message = unknown.size() + " of " + total + " records come out as EventDetail/Unknown (e.g. "
                               + unknown.getFirst() + (held.isEmpty()
                ? ""
                : "; they hold " + held) + "), from an XSLT saved without a mapping, so nobody agreed to Unknown "
                               + "for them. Records whose values show what happened take that action element: a "
                               + "connection allowed or denied Network/Permit or Network/Deny, a logon "
                               + "Authenticate, a configuration change Update, an alert Alert. Draft the mapping "
                               + "from the sample (draft_translation_mapping) and save the XSLT with "
                               + "build_translation_xslt: it checks each rule against the records, and puts "
                               + "Unknown to the user for the records nothing else describes.";
        return SteppingVerdicts.blocking("Events written as EventDetail/Unknown that nobody agreed to", element,
                unknown.size(), message, new ArrayList<>(new TreeSet<>(unknown)));
    }

    /// @param streamId A raw stream.
    /// @return True if the head of its first part is a JSON array; false if not, or if it can't be read (or the
    /// user may not view data).
    boolean streamIsArray(final long streamId) {
        if (!documentAccess.canViewData()) {
            return false;
        }
        try {
            final String text = streamReader.readHead(streamId, 0, MAX_STREAM_CHARS).text();
            return SampleProfiler.JSON_ARRAY.equals(SampleProfiler.profile(text).get("format"));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to profile stream " + streamId, e);
            return false;
        }
    }

    /// Records the code a clean step_sample or step_records ran, for promotion's checks, and the streams it ran on
    /// (write_documentation's default sample); the result says when the record couldn't be saved.
    ///
    /// @param pipeline  The pipeline stepped.
    /// @param draftCode Draft code by element, or null.
    /// @param result    The tool's result: recorded only when its verdict is clean and it stepped records.
    /// @param streams   The streams stepped.
    void rememberClean(final DocRef pipeline,
                       final Map<String, String> draftCode,
                       final Map<String, Object> result,
                       final List<Long> streams) {
        if (!"clean".equals(result.get("verdict"))
            || !(result.get("records_stepped") instanceof final Number stepped) || stepped.intValue() == 0) {
            return;
        }
        final Map<String, Object> extra = streams.isEmpty() || draftCode != null
                ? null
                : Map.of("stepped_streams", new TreeSet<>(streams).stream().limit(20).toList());
        buildRecords.recordPassed(pipeline, BuildRecords.STEPPED, draftCode, extra)
                .filter(outcome -> !"recorded".equals(outcome))
                .ifPresent(outcome -> result.put("record", outcome));
    }

    /// @param context  The call's context.
    /// @param pipeline The pipeline stepped.
    /// @param result   The tool's result.
    /// @return The result with the plan's next step, for a pipeline in a build.
    Map<String, Object> withNext(final ToolCallContext context,
                                 final DocRef pipeline,
                                 final Map<String, Object> result) {
        return plan.withNext(context, plan.buildOf(pipeline).orElse(null), result);
    }

    /// Records that the streams were stepped, as the data they hold is shown.
    ///
    /// @param context   The call's context.
    /// @param streamIds The streams stepped.
    /// @param purpose   What was done, e.g. "Stepped stream(s) through ACME with the step_sample tool".
    void audit(final ToolCallContext context, final List<Long> streamIds, final String purpose) {
        try {
            auditLog.streamsViewed(context.session(), new TreeSet<>(streamIds), purpose);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to audit the streams stepped", e);
        }
    }

    // --------------------------------------------------------------------------------
    // Time limits and arguments.
    // --------------------------------------------------------------------------------

    /// @param context A call's context.
    /// @return The session's time limit for a long call (its query time limit), or empty if it sets none.
    static Optional<Duration> timeLimit(final ToolCallContext context) {
        return Optional.ofNullable(NullSafe.get(context.limits().workspace(), AiWorkspaceConfig::getQueryTimeLimit,
                StroomDuration::getDuration));
    }

    /// Steps within the session's time limit for a long call, less [#MARGIN] for what is done after.
    ///
    /// @param context A call's context.
    /// @param work    The stepping.
    /// @param <T>     What it gives.
    /// @return What it gives.
    static <T> T withinTimeLimit(final ToolCallContext context, final Supplier<T> work) {
        final Optional<Duration> limit = timeLimit(context);
        if (limit.isEmpty()) {
            return work.get();
        }
        final Duration stepping = limit.get().minus(MARGIN);
        return ToolDeadline.run(stepping.isNegative()
                ? Duration.ZERO
                : stepping, work);
    }

    /// @return True once the call's time for stepping has passed.
    /// @throws ToolTimeoutException at once if the call's thread is interrupted (the run stopped, or the client gave
    /// up on the call).
    static boolean timeIsUp() {
        try {
            ToolDeadline.check();
            return false;
        } catch (final ToolTimeoutException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw e;
            }
            return true;
        }
    }

    /// @param context A call's context.
    /// @param what    Where it stopped, e.g. `after 40 records`.
    /// @return What a call that stopped before the session's time limit ran out tells the agent (the Stroom MCP
    /// server's `out_of_time`, which named the caller's sign-in).
    static String outOfTime(final ToolCallContext context, final String what) {
        return "Stopped " + what + " before this session's time limit for a call ("
               + timeLimit(context).map(limit -> limit.toSeconds() + " s").orElse("none") + ") ran out. Call "
               + "again: each call has its own time, so there is nothing for the user to do.";
    }

    /// @param call A call.
    /// @param name The draft code argument's name.
    /// @return The draft code by element, or null if none was given.
    /// @throws ToolRefusal if it isn't code by element id.
    static Map<String, String> draftCode(final ToolCall call, final String name) {
        Object value = AbstractBuiltInTool.argsMap(call).get(name);
        if (value instanceof final String text && !text.isBlank()) {
            try {
                value = JsonUtil.readValue(text, Map.class);
            } catch (final RuntimeException e) {
                value = text;
            }
        }
        if (value == null || (value instanceof final String text && text.isBlank())) {
            return null;
        }
        if (!(value instanceof final Map<?, ?> map)) {
            throw new ToolRefusal(name + " is unsaved code keyed by element id, e.g. {'translationFilter': "
                                  + "'<xsl:stylesheet ...>'}");
        }
        final Map<String, String> code = new LinkedHashMap<>();
        map.forEach((element, text) -> code.put(String.valueOf(element), text == null
                ? null
                : String.valueOf(text)));
        return code.isEmpty()
                ? null
                : code;
    }

    /// @param draftCode Draft code by element, or null.
    /// @return The elements it is for, sorted (a result's `draft_code_used`).
    static List<String> draftCodeUsed(final Map<String, String> draftCode) {
        return List.copyOf(new TreeSet<>(NullSafe.map(draftCode).keySet()));
    }

    /// @param result A step, or null.
    /// @return True if it found a record.
    static boolean isFound(final SteppingResult result) {
        return result != null && result.isFoundRecord();
    }

    private ElementRegistry elementRegistry() {
        try {
            return elementRegistryFactoryProvider.get().get();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the pipeline element types", e);
            return null;
        }
    }

    // The type Stroom declares for an element type's property, or null.
    private static String declaredType(final ElementRegistry registry, final String elementType, final String name) {
        if (registry == null || elementType == null) {
            return null;
        }
        try {
            final PipelineElementType type = registry.getElementType(elementType);
            final PipelinePropertyType property = type == null
                    ? null
                    : registry.getPropertyType(type, name);
            return NullSafe.get(property, PipelinePropertyType::getType);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static int total(final Map<Long, SpreadState> states) {
        return states.values().stream().mapToInt(state -> state.steps.size()).sum();
    }

    private static int ceilDiv(final int a, final int b) {
        return -Math.floorDiv(-a, b);
    }


    // --------------------------------------------------------------------------------


    /// Records stepped spread over streams.
    ///
    /// @param steps   The steps, in stream order, then record order.
    /// @param stopped True if the call's time ran out before they were all made.
    record Spread(List<Step> steps, boolean stopped) {

        /// One record stepped.
        ///
        /// @param streamId The stream.
        /// @param location Where in it.
        /// @param result   The step.
        record Step(long streamId, StepLocation location, SteppingResult result) {

        }
    }

    /// Records' outputs at an element.
    ///
    /// @param outputs Each record's output, by record key.
    /// @param stopped True if the call's time ran out before every record was stepped.
    record Outputs(Map<String, String> outputs, boolean stopped) {

    }

    // Where one stream's walk is.
    private static final class SpreadState {

        private final List<Spread.Step> steps = new ArrayList<>();
        private SteppingResult result;
        private boolean done;
    }
}

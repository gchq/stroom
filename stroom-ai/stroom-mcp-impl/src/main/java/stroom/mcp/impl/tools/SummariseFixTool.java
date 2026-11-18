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

import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.CallProgress;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ErrorTriage;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.MergedPipeline.TranslationDoc;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.HasData;
import stroom.util.shared.NullSafe;
import stroom.util.shared.diff.LineDiff;
import stroom.util.shared.diff.LineDiffResult;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/// `summarise_fix` - proves a fix and packages it for the user. Steps every record of the streams (and exactly the
/// records of the indexed events given) with the fix (a draft, or the code saved on a copy of the pipeline), diffs the
/// outputs against the pipeline's saved code (only the expected paths should change), and returns the code diff,
/// whether the fix is ready, and step-by-step instructions for applying it by hand. Saves nothing.
///
/// It is made of the stepping tools: compare_outputs' comparison, and step_sample's and step_records' verdicts,
/// once with the fix and once with the saved code, so a blocking error the saved code already has doesn't hold
/// the fix back. Stepped as the user, who needs the Stepping permission, on this node. It takes a run slot over
/// `/mcp`, says where it is now and then, stops at once when its thread is interrupted, and stops before the
/// session's time limit for a long call, saying the fix isn't proven.
class SummariseFixTool extends AbstractBuiltInTool {

    static final String NAME = "summarise_fix";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String ELEMENT_ARG = "element";
    static final String DRAFT_ARG = "draft";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String EXPECTED_PATHS_ARG = "expected_paths";
    static final String ISSUE_ARG = "issue";
    static final String EVENTS_ARG = CompareOutputsTool.EVENTS_ARG;
    static final String FIXED_PIPELINE_UUID_ARG = "fixed_pipeline_uuid";
    /// The verdicts, least to most serious.
    private static final List<String> VERDICTS = List.of("clean", ErrorTriage.REVIEW, ErrorTriage.BLOCKING);

    private final PipelineStepping stepping;
    private final EventLocations eventLocations;
    private final CompareOutputsTool compareOutputs;
    private final StepSampleTool stepSample;
    private final StepRecordsTool stepRecords;
    private final DocumentAccess documentAccess;

    @Inject
    SummariseFixTool(final PipelineStepping stepping,
                     final EventLocations eventLocations,
                     final CompareOutputsTool compareOutputs,
                     final StepSampleTool stepSample,
                     final StepRecordsTool stepRecords,
                     final DocumentAccess documentAccess) {
        this.stepping = stepping;
        this.eventLocations = eventLocations;
        this.compareOutputs = compareOutputs;
        this.stepSample = stepSample;
        this.stepRecords = stepRecords;
        this.documentAccess = documentAccess;
    }

    @Override
    public ToolDefinition definition() {
        final ObjectSchema event = ObjectSchema.builder()
                .addIntegerProperty("stream_id", "Its Events stream (the StreamId an index holds).")
                .addIntegerProperty("event_id", "The event within it (EventId), from 1.")
                .required("stream_id", "event_id")
                .build();
        return ToolDefinition.builder()
                .name(NAME)
                .description("Prove a fix and package it for the user. Steps every record of the streams with the "
                             + "fix (a draft, or the code saved on a copy: fixed_pipeline_uuid), diffs outputs "
                             + "against the pipeline's saved code (only expected_paths should change), and returns "
                             + "the code diff, whether the fix is ready, and step-by-step instructions for applying "
                             + "it by hand. Saves nothing: ask the user whether to apply it (a copy of the pipeline "
                             + "in a build, changed, then promoted) or do it themselves.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PIPELINE_UUID_ARG, "The pipeline being fixed.")
                        .addStringProperty(ELEMENT_ARG, "Element whose XSLT or text converter the fix changes. "
                                                        + "With fixed_pipeline_uuid, left out: the one whose code "
                                                        + "differs.")
                        .addStringProperty(DRAFT_ARG, "The complete fixed XSLT or text converter; or give "
                                                      + "fixed_pipeline_uuid instead.")
                        .addProperty(STREAM_IDS_ARG, stringArraySchema("Raw streams to prove it on: the reported "
                                                                       + "one plus recent production streams."))
                        .addProperty(EXPECTED_PATHS_ARG, stringArraySchema("Output field paths the fix should "
                                                                           + "change, e.g. "
                                                                           + "['Event/EventSource/User/Id']."))
                        .addStringProperty(ISSUE_ARG, "The problem in the user's words, for the write-up.")
                        .addProperty(EVENTS_ARG, ArraySchema.builder()
                                .items(event)
                                .description("Indexed events to prove it on, each {stream_id, event_id} as "
                                             + "search_events with an index gives them: exactly those records are "
                                             + "stepped, whatever streams they are in (with stream_ids too, or "
                                             + "instead).")
                                .build())
                        .addStringProperty(FIXED_PIPELINE_UUID_ARG, "A change already saved on a copy of the "
                                                                    + "pipeline (copy_pipeline: a new version or "
                                                                    + "working copy): its code is the draft, proven "
                                                                    + "against the pipeline's own saved code, in "
                                                                    + "place of draft.")
                        .required(PIPELINE_UUID_ARG)
                        .build())
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public boolean isReadOnly() {
        // Read-only (readOnlyHint true): it saves nothing. Like step_sample and step_records, whose verdicts it is made
        // of, a clean verdict may be recorded in the build's record of the pipeline stepped: the tools' own
        // bookkeeping, changing no data, document content or processing.
        return true;
    }

    @Override
    public boolean usesRunSlot() {
        // It steps the records three times: compared both ways, then stepped with the fix and with the saved code.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.STEPPING_PERMISSION)
                .unlockedBy(AppPermission.VIEW_DATA_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .note("Stepped as the user, only through pipelines they may view, and streams of feeds they may "
                      + "view, with the code of XSLTs and text converters they may view. A clean verdict is "
                      + "recorded in the build record of the pipeline stepped, when it is in a build.")
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        if (pipelineUuid == null) {
            return JsonResults.refused("Give pipeline_uuid: the pipeline being fixed.");
        }
        try {
            final String draft = rawArg(call, DRAFT_ARG);
            final Request request = new Request(pipelineUuid,
                    stringArg(call, ELEMENT_ARG),
                    NullSafe.isBlankString(draft)
                            ? null
                            : draft,
                    StartOnboardingTool.streamIds(call, STREAM_IDS_ARG),
                    stringListArg(call, EXPECTED_PATHS_ARG),
                    NullSafe.string(stringArg(call, ISSUE_ARG)),
                    CompareOutputsTool.events(call),
                    stringArg(call, FIXED_PIPELINE_UUID_ARG));
            return JsonResults.of(summariseFix(call, context, request));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final EventLocations.LocateException e) {
            return JsonResults.refused(e.getMessage());
        } catch (final ToolTimeoutException e) {
            return JsonResults.refused(PipelineStepping.CANCELLED);
        } catch (final AiChatRunManager.WaitingForUserException e) {
            // A form waits for the user: the call is made again with the answer.
            throw e;
        } catch (final RuntimeException e) {
            return JsonResults.refused(StreamSummaries.reason(e));
        }
    }

    /// The fix, proven and packaged: `issue`, `pipeline`, `element`, `doc`, `inherited_from_template`, `set_by`,
    /// `ready`, `problems`, `records_compared`, `records_changed`, `fields_changed`, `step_verdict`, `step_groups`, any
    /// `errors_resolved`, `errors_before_too` and its note, `diff`, `draft`, `manual_steps` and `hint`. When the call's
    /// time ran out first, a shorter result that isn't ready, with `stopped_early` and a hint.
    ///
    /// @param call    The call, whose id tells its forms apart.
    /// @param context The call's context.
    /// @param request The fix to prove.
    /// @return The fix, proven and packaged.
    /// @throws ToolRefusal if the fix can't be proven as asked, saying why.
    /// @throws ToolTimeoutException if the call is cancelled.
    Map<String, Object> summariseFix(final ToolCall call, final ToolCallContext context, final Request request) {
        final String element;
        final String draft;
        if (request.fixedPipelineUuid() != null) {
            final Map.Entry<String, String> copys = copysCode(request.pipelineUuid(), request.fixedPipelineUuid(),
                    request.element());
            element = copys.getKey();
            draft = copys.getValue();
        } else if (request.element() == null || request.draft() == null) {
            throw new ToolRefusal("Give element and draft (the complete fixed code), or fixed_pipeline_uuid: a copy "
                                  + "of the pipeline with the change saved on it");
        } else {
            element = request.element();
            draft = request.draft();
        }
        if (request.streamIds().isEmpty() && request.events().isEmpty()) {
            throw new ToolRefusal("Give stream_ids (raw streams) or events (from search_events with an index) to "
                                  + "prove the fix on");
        }
        if (request.expectedPaths().isEmpty()) {
            throw new ToolRefusal("Give expected_paths: the output fields the fix should change");
        }
        stepping.checkCanStep();
        final SteppedPipeline pipeline = stepping.load(request.pipelineUuid());
        final Map<String, TranslationDoc> docs = translationDocs(pipeline, request.pipelineUuid());
        if (!docs.containsKey(element)) {
            throw new ToolRefusal("Element '" + element + "' has no XSLT or text converter; elements with code: "
                                  + ToolText.list(new TreeSet<>(docs.keySet())));
        }
        final TranslationDoc target = docs.get(element);
        final String saved = code(target);
        if (saved.equals(draft)) {
            throw new ToolRefusal("The draft is the same as the saved code");
        }
        final String diff = diff(saved, draft, target.doc().getName());
        final Map<String, String> code = Map.of(element, draft);
        final Proof proof = PipelineStepping.withinTimeLimit(context, () -> prove(call, context, request, element,
                target, code));
        if (proof.stopped()) {
            return stoppedEarly(context, request, pipeline, element, target);
        }
        return packaged(request, pipeline, element, target, draft, diff, proof);
    }

    // The fix compared with the saved code, then stepped with each, until the call's time is up.
    private Proof prove(final ToolCall call,
                        final ToolCallContext context,
                        final Request request,
                        final String element,
                        final TranslationDoc target,
                        final Map<String, String> code) {
        final CallProgress progress = CallProgress.of(context);
        final List<Map<String, Object>> located;
        if (request.events().isEmpty()) {
            located = List.of();
        } else {
            progress.say("Proving the fix: finding the records of " + request.events().size() + " event(s)");
            try {
                located = eventLocations.eventLocations(request.events());
            } catch (final ToolTimeoutException e) {
                if (Thread.currentThread().isInterrupted()) {
                    throw e;
                }
                return Proof.STOPPED;
            }
        }
        progress.say("Proving the fix: comparing outputs with the saved code's");
        final Map<String, Object> comparison = compareOutputs.compareLocated(call, context,
                new CompareOutputsTool.Request(request.pipelineUuid(), request.streamIds(), code, null,
                        XsltDoc.TYPE.equals(target.doc().getType())
                                ? element
                                : null,
                        CompareOutputsTool.DEFAULT_MAX_RECORDS, List.of()),
                located);
        if (stoppedEarly(comparison)) {
            return Proof.STOPPED;
        }
        progress.say("Proving the fix: stepping with the fix");
        final Stepped stepped = stepped(call, context, request.pipelineUuid(), request.streamIds(), located, code);
        if (stepped.stopped()) {
            return Proof.STOPPED;
        }
        // The saved code stepped too: blocking errors it already has are not the fix's doing, and must not stop a
        // fix for something else (a pipeline can have more than one problem); errors only the draft has are.
        progress.say("Proving the fix: stepping with the saved code");
        final Stepped before = stepped(call, context, request.pipelineUuid(), request.streamIds(), located, null);
        if (before.stopped()) {
            return Proof.STOPPED;
        }
        return new Proof(located, comparison, stepped, before, false);
    }

    /// One verdict over the streams (step_sample) and the located events' records (step_records).
    ///
    /// @param code Draft code by element, or null for the saved code.
    private Stepped stepped(final ToolCall call,
                            final ToolCallContext context,
                            final String pipelineUuid,
                            final List<Long> streamIds,
                            final List<Map<String, Object>> located,
                            final Map<String, String> code) {
        final List<Map<String, Object>> parts = new ArrayList<>();
        if (!streamIds.isEmpty()) {
            parts.add(stepSample.stepSample(call, context, pipelineUuid, streamIds, code, null, null));
        }
        if (!located.isEmpty()) {
            final List<StepRecordsTool.Location> locations = located.stream()
                    .map(loc -> new StepRecordsTool.Location(whole(loc.get("raw")), whole(loc.get("part")),
                            whole(loc.get("record")), null, StepRecordsTool.EXPECT_EVENT))
                    .toList();
            parts.add(stepRecords.stepRecords(call, context, pipelineUuid, locations, code));
        }
        return combined(parts);
    }

    /// @param parts The results of step_sample and step_records.
    /// @return Their most serious verdict, all their groups, and whether either stopped early.
    static Stepped combined(final List<Map<String, Object>> parts) {
        String verdict = VERDICTS.getFirst();
        final List<Map<String, Object>> groups = new ArrayList<>();
        boolean stopped = false;
        for (final Map<String, Object> part : parts) {
            final String partVerdict = String.valueOf(part.get("verdict"));
            if (VERDICTS.indexOf(partVerdict) > VERDICTS.indexOf(verdict)) {
                verdict = partVerdict;
            }
            groups.addAll(NullSafe.list(SteppingVerdicts.groups(part)));
            stopped = stopped || stoppedEarly(part);
        }
        return new Stepped(verdict, groups, stopped);
    }

    // The result: what the fix changes, what it breaks or clears, and how to apply it.
    private Map<String, Object> packaged(final Request request,
                                         final SteppedPipeline pipeline,
                                         final String element,
                                         final TranslationDoc target,
                                         final String draft,
                                         final String diff,
                                         final Proof proof) {
        final List<Map<String, Object>> blockingBefore = blocking(proof.before().groups());
        final List<Map<String, Object>> blockingNow = blocking(proof.stepped().groups());
        final Set<List<String>> was = blockingBefore.stream().map(SummariseFixTool::key).collect(Collectors.toSet());
        final Set<List<String>> now = blockingNow.stream().map(SummariseFixTool::key).collect(Collectors.toSet());
        final List<Map<String, Object>> introduced = blockingNow.stream().filter(g -> !was.contains(key(g))).toList();
        final List<Map<String, Object>> preExisting = blockingNow.stream().filter(g -> was.contains(key(g))).toList();
        final List<Map<String, Object>> resolved = blockingBefore.stream().filter(g -> !now.contains(key(g)))
                .toList();
        final List<Map<String, Object>> fieldsChanged = fieldsChanged(proof.comparison());
        final List<String> changed = fieldsChanged.stream().map(field -> String.valueOf(field.get("path"))).toList();
        final List<String> problems = problems(changed, changedOutside(changed, request.expectedPaths()), introduced);

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("issue", request.issue());
        result.put("pipeline", pipelineMap(request.pipelineUuid(), pipeline));
        result.put("element", element);
        result.put("doc", MergedPipeline.docRef(target.doc()));
        result.put("inherited_from_template", target.inheritedFromTemplate());
        result.put("set_by", target.setBy());
        result.put("ready", problems.isEmpty());
        result.put("problems", problems);
        result.put("records_compared", proof.comparison().get("records_compared"));
        result.put("records_changed", proof.comparison().get("records_changed"));
        result.put("fields_changed", fieldsChanged.subList(0, Math.min(20, fieldsChanged.size())));
        result.put("step_verdict", proof.stepped().verdict());
        result.put("step_groups", proof.stepped().groups().stream()
                .filter(g -> !ErrorTriage.BENIGN.equals(g.get("class")))
                .limit(10)
                .toList());
        if (!resolved.isEmpty()) {
            result.put("errors_resolved", resolved.stream().map(SummariseFixTool::brief).toList());
        }
        if (!preExisting.isEmpty()) {
            result.put("errors_before_too", preExisting.stream().map(SummariseFixTool::brief).toList());
            result.put("errors_before_too_note", "The saved code has these as well: not caused by this fix, and not "
                                                 + "in its way. Tell the user; they are a separate issue (an "
                                                 + "evaluation of the pipeline lists them).");
        }
        result.put("diff", diff.length() > PipelineStepping.MAX_STREAM_CHARS
                ? diff.substring(0, PipelineStepping.MAX_STREAM_CHARS)
                : diff);
        result.put("draft", draft);
        result.put("manual_steps", manualSteps(request, pipeline, target, proof.located()));
        result.put("hint", problems.isEmpty()
                ? "Show the user the diff and the fields changed, then ask whether to apply the fix to the "
                  + "pipeline or give them the manual steps."
                : "Revise the draft and call summarise_fix again.");
        return result;
    }

    // What holds the fix back.
    private static List<String> problems(final List<String> changed,
                                         final List<String> unexpected,
                                         final List<Map<String, Object>> introduced) {
        final List<String> problems = new ArrayList<>();
        if (changed.isEmpty()) {
            problems.add("The draft changes no output on these records: it does not reach the reported case");
        }
        if (!unexpected.isEmpty()) {
            problems.add("Fields outside expected_paths change too: " + ToolText.list(unexpected));
        }
        if (!introduced.isEmpty()) {
            problems.add("Stepping with the draft has blocking errors the saved code does not: "
                         + introduced.stream()
                                 .map(g -> "(" + ToolText.repr(stringOrNull(g.get("element"))) + ", "
                                           + ToolText.repr(cut(exampleMessage(g), 150)) + ")")
                                 .collect(Collectors.joining(", ", "[", "]")));
        }
        return problems;
    }

    // How to apply the fix by hand.
    private static List<String> manualSteps(final Request request,
                                            final SteppedPipeline pipeline,
                                            final TranslationDoc target,
                                            final List<Map<String, Object>> located) {
        final String kind = kind(target);
        final Set<Long> streams = new TreeSet<>(request.streamIds());
        located.forEach(loc -> streams.add(whole(loc.get("raw"))));
        final List<String> manual = new ArrayList<>();
        if (target.inheritedFromTemplate()) {
            manual.add("Note: this " + kind + " is set by template pipeline '" + target.setBy() + "', so the change "
                       + "affects every pipeline built from that template.");
        }
        manual.add("Open the " + kind + " '" + target.doc().getName() + "' (uuid " + target.doc().getUuid()
                   + ") in Stroom.");
        manual.add("Apply the change in 'diff' below (or paste the complete fixed code), then save.");
        manual.add("Open pipeline '" + pipeline.name() + "', enter stepping on raw stream(s) "
                   + ToolText.repr(streams) + " and check the reported record now gives the expected output "
                   + "with no errors.");
        manual.add("Reprocess the affected streams yourself if you want existing events corrected; new data picks up "
                   + "the fix automatically.");
        return manual;
    }

    // What a call whose time ran out before the fix was proven gives.
    private static Map<String, Object> stoppedEarly(final ToolCallContext context,
                                                    final Request request,
                                                    final SteppedPipeline pipeline,
                                                    final String element,
                                                    final TranslationDoc target) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("issue", request.issue());
        result.put("pipeline", pipelineMap(request.pipelineUuid(), pipeline));
        result.put("element", element);
        result.put("doc", MergedPipeline.docRef(target.doc()));
        result.put("ready", false);
        result.put("stopped_early", true);
        result.put("hint", PipelineStepping.outOfTime(context, "while proving the fix") + " The fix is not proven: "
                           + "give fewer stream_ids or events to prove it on.");
        return result;
    }

    /// `(element, code)`: the copy's code for the element, or for the one element whose code differs from the
    /// pipeline's.
    ///
    /// @param pipelineUuid The pipeline being fixed.
    /// @param copyUuid     The copy with the change saved on it.
    /// @param element      The element whose code the fix changes, or null for the one that differs.
    /// @return The element and the copy's code for it.
    /// @throws ToolRefusal if the copy has no code there, or not just one element's code differs.
    Map.Entry<String, String> copysCode(final String pipelineUuid, final String copyUuid, final String element) {
        final Map<String, String> mine = codes(pipelineUuid);
        final Map<String, String> copy = codes(copyUuid);
        if (element != null) {
            if (!copy.containsKey(element)) {
                throw new ToolRefusal("The copy has no XSLT or text converter on '" + element + "'; it has: "
                                      + ToolText.list(new TreeSet<>(copy.keySet())));
            }
            return Map.entry(element, copy.get(element));
        }
        final List<String> differing = copy.keySet().stream()
                .filter(e -> mine.containsKey(e) && !copy.get(e).equals(mine.get(e)))
                .toList();
        if (differing.size() != 1) {
            throw new ToolRefusal(differing.isEmpty()
                    ? "The copy's code is the same as the pipeline's: nothing to prove"
                    : "Give element: the copy's code differs from the pipeline's on " + ToolText.list(differing));
        }
        return Map.entry(differing.getFirst(), copy.get(differing.getFirst()));
    }

    // Each element's code, of the pipeline's XSLTs and text converters.
    private Map<String, String> codes(final String uuid) {
        final Map<String, String> codes = new LinkedHashMap<>();
        translationDocs(stepping.load(uuid), uuid).forEach((element, doc) -> codes.put(element, code(doc)));
        return codes;
    }

    private static Map<String, TranslationDoc> translationDocs(final SteppedPipeline pipeline, final String uuid) {
        final Map<String, TranslationDoc> docs = new LinkedHashMap<>();
        pipeline.merged().translationDocs(uuid).forEach(doc -> docs.put(doc.element(), doc));
        return docs;
    }

    // The saved code of an XSLT or text converter.
    private String code(final TranslationDoc doc) {
        return documentAccess.read(doc.doc(), HasData.class)
                .map(found -> NullSafe.string(found.getData()))
                .orElseThrow(() -> new ToolRefusal(kind(doc) + " '" + doc.doc().getName() + "' (uuid "
                                                   + doc.doc().getUuid() + ") can't be read: it doesn't exist, or "
                                                   + "you don't have permission to view it"));
    }

    /// @param saved The saved code.
    /// @param draft The fixed code.
    /// @param name  The document's name.
    /// @return The change as a unified diff, `<name> (saved)` to `<name> (fixed)`.
    static String diff(final String saved, final String draft, final String name) {
        final LineDiffResult diff = LineDiff.diff(saved, draft);
        return "--- " + name + " (saved)\n+++ " + name + " (fixed)\n"
               + diff.toUnifiedDiff(LineDiffResult.DEFAULT_CONTEXT_LINES) + "\n";
    }

    /// @param paths    Output field paths that changed.
    /// @param expected The paths the fix should change.
    /// @return The paths that are neither an expected path nor below one.
    static List<String> changedOutside(final List<String> paths, final List<String> expected) {
        return paths.stream()
                .filter(path -> expected.stream().noneMatch(e -> path.equals(e)
                                                                 || path.startsWith(stripTrailingSlashes(e) + "/")))
                .toList();
    }

    /// @param group An error group.
    /// @return It, short: where, how often, and one example.
    static Map<String, Object> brief(final Map<String, Object> group) {
        final Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("element", group.get("element"));
        brief.put("severity", group.get("severity"));
        brief.put("count", group.get("count"));
        brief.put("reason", group.get("reason"));
        brief.put("example", cut(exampleMessage(group), 300));
        return brief;
    }

    // A group, as the same error with the draft or the saved code: where, how bad, and what it says.
    private static List<String> key(final Map<String, Object> group) {
        final String message = exampleMessage(group);
        final String example = message.isEmpty()
                ? NullSafe.string(stringOrNull(group.get("reason")))
                : message;
        return Arrays.asList(stringOrNull(group.get("element")), stringOrNull(group.get("severity")),
                ErrorTriage.normalise(example));
    }

    private static List<Map<String, Object>> blocking(final List<Map<String, Object>> groups) {
        return groups.stream().filter(g -> ErrorTriage.BLOCKING.equals(g.get("class"))).toList();
    }

    // The first example's message, or an empty string.
    private static String exampleMessage(final Map<String, Object> group) {
        if (group.get("examples") instanceof final List<?> examples && !examples.isEmpty()
            && examples.getFirst() instanceof final Map<?, ?> example && example.get("message") != null) {
            return String.valueOf(example.get("message"));
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fieldsChanged(final Map<String, Object> comparison) {
        return comparison.get("fields_changed") instanceof final List<?> fields
                ? (List<Map<String, Object>>) fields
                : List.of();
    }

    private static Map<String, Object> pipelineMap(final String uuid, final SteppedPipeline pipeline) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("uuid", uuid);
        map.put("name", pipeline.name());
        return map;
    }

    private static String kind(final TranslationDoc doc) {
        return XsltDoc.TYPE.equals(doc.doc().getType())
                ? "XSLT"
                : "Text Converter";
    }

    private static boolean stoppedEarly(final Map<String, Object> result) {
        return Boolean.TRUE.equals(result.get("stopped_early"));
    }

    private static String stripTrailingSlashes(final String path) {
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(0, end);
    }

    private static String cut(final String text, final int most) {
        return text.length() > most
                ? text.substring(0, most)
                : text;
    }

    private static String stringOrNull(final Object value) {
        return value == null
                ? null
                : String.valueOf(value);
    }

    private static long whole(final Object value) {
        return value instanceof final Number number
                ? number.longValue()
                : 0L;
    }


    // --------------------------------------------------------------------------------


    /// A fix to prove.
    ///
    /// @param pipelineUuid      The pipeline being fixed.
    /// @param element           The element whose XSLT or text converter the fix changes, or null.
    /// @param draft             The complete fixed code, or null.
    /// @param streamIds         Raw streams to prove it on.
    /// @param expectedPaths     Output field paths the fix should change.
    /// @param issue             The problem in the user's words.
    /// @param events            Indexed events to prove it on.
    /// @param fixedPipelineUuid A copy of the pipeline with the change saved on it, or null.
    record Request(String pipelineUuid,
                   String element,
                   String draft,
                   List<Long> streamIds,
                   List<String> expectedPaths,
                   String issue,
                   List<EventLocations.EventRef> events,
                   String fixedPipelineUuid) {

    }

    /// One verdict over what was stepped.
    ///
    /// @param verdict The most serious verdict: `clean`, `review` or `blocking`.
    /// @param groups  Every error group.
    /// @param stopped True if the call's time ran out before every record was stepped.
    record Stepped(String verdict, List<Map<String, Object>> groups, boolean stopped) {

    }

    // What proving the fix found: the located records, the comparison, and the verdicts with the fix and without.
    private record Proof(List<Map<String, Object>> located,
                         Map<String, Object> comparison,
                         Stepped stepped,
                         Stepped before,
                         boolean stopped) {

        private static final Proof STOPPED = new Proof(List.of(), Map.of(), null, null, true);
    }
}

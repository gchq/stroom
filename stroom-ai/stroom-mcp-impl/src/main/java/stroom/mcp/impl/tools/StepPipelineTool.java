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

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.CallProgress;
import stroom.ai.impl.tools.ErrorTriage;
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.SteppingWalks.Walk;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.validation.PythonText;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// `step_pipeline` - steps one record through a pipeline and shows what each element does to it: the input and
/// output of the chosen elements, and every element's errors and warnings, triaged; draft code tries a change
/// without saving it. Ported from the Stroom MCP server's `tools/stepping.py`.
///
/// Stepped as the user, who needs the Stepping permission, in a stepping session of its own on this node, ended
/// when the step is done. It changes nothing, and takes a run slot over `/mcp`. It stops when its thread is
/// interrupted (the client gave up on the call, or the run was stopped), and at the session's time limit for a
/// long call.
class StepPipelineTool extends AbstractBuiltInTool {

    static final String NAME = "step_pipeline";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String STREAM_ID_ARG = "stream_id";
    static final String RECORD_ARG = "record";
    static final String PART_ARG = "part";
    static final String DRAFT_CODE_ARG = "draft_code";
    static final String SHOW_ARG = "show";

    private final PipelineStepping stepping;

    @Inject
    StepPipelineTool(final PipelineStepping stepping) {
        this.stepping = stepping;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Step one record through a pipeline and show what each element does to it: the "
                             + "input and output of the chosen elements, and every element's errors and warnings, "
                             + "triaged. Use draft_code to try a translation change without saving it.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PIPELINE_UUID_ARG, PipelineStepping.PIPELINE_UUID_DESCRIPTION)
                        .addIntegerProperty(STREAM_ID_ARG, "Stream to step through, e.g. a Raw Events sample.")
                        .addStringProperty(RECORD_ARG, "Zero-based record index within the part, or 'first' / "
                                                       + "'last'.")
                        .addIntegerProperty(PART_ARG, "Zero-based part of a multi-part stream (locate_event gives "
                                                      + "it).")
                        .addProperty(DRAFT_CODE_ARG, ObjectSchema.builder()
                                .description(PipelineStepping.DRAFT_CODE_DESCRIPTION)
                                .build())
                        .addProperty(SHOW_ARG, stringArraySchema("Element ids whose input and output to return. "
                                                                 + "Defaults to the pipeline's own XSLT steps."))
                        .required(PIPELINE_UUID_ARG, STREAM_ID_ARG)
                        .build())
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public boolean usesRunSlot() {
        // It steps a stream, which Stroom captures whole.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.STEPPING_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .note("Stepped as the user, only through pipelines they may view, and streams of feeds they may "
                      + "view.")
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        if (pipelineUuid == null) {
            return JsonResults.refused("Give pipeline_uuid: the pipeline to step.");
        }
        final Integer streamId = intArg(call, STREAM_ID_ARG);
        if (streamId == null) {
            return JsonResults.refused("Give stream_id: the stream to step through, e.g. a Raw Events sample.");
        }
        try {
            final Record record = record(call);
            final Integer part = intArg(call, PART_ARG);
            if (stringArg(call, PART_ARG) != null && (part == null || part < 0)) {
                throw new ToolRefusal("part must be a whole number from 0, not '" + stringArg(call, PART_ARG)
                                      + "'");
            }
            final Map<String, String> draftCode = PipelineStepping.draftCode(call, DRAFT_CODE_ARG);
            final Map<String, Object> result = PipelineStepping.withinTimeLimit(context, () -> step(context,
                    pipelineUuid, streamId, record, part == null
                            ? 0
                            : part, draftCode, stringListArg(call, SHOW_ARG)));
            return JsonResults.of(result);
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final ToolTimeoutException e) {
            return JsonResults.refused(Thread.currentThread().isInterrupted()
                    ? PipelineStepping.CANCELLED
                    : "The step ran past this session's time limit for a call ("
                      + PipelineStepping.timeLimit(context).map(limit -> limit.toSeconds() + " s").orElse("none")
                      + "): step a smaller stream, or call again.");
        } catch (final RuntimeException e) {
            return JsonResults.refused(StreamSummaries.reason(e));
        }
    }

    /// One record stepped, as the Stroom MCP server gave it: `pipeline`, `stream_id`, `part`, `record`,
    /// `draft_code_used`, `elements` (each chosen element's `type`, `input` and `output`), and the triage
    /// (`verdict`, `groups_by_class`, `groups`).
    Map<String, Object> step(final ToolCallContext context,
                             final String pipelineUuid,
                             final long streamId,
                             final Record record,
                             final int part,
                             final Map<String, String> draftCode,
                             final List<String> show) {
        stepping.checkCanStep();
        stepping.checkDrafts(draftCode);
        final SteppedPipeline pipeline = stepping.load(pipelineUuid);
        pipeline.refuseMistyped();
        CallProgress.of(context).say("Stepping record " + record.said() + " of stream " + streamId + " through "
                                     + pipeline.name());
        final SteppingResult result;
        try (final Walk walk = stepping.open(pipeline, streamId, draftCode)) {
            result = switch (record.kind()) {
                case "first" -> walk.first();
                case "last" -> walk.last();
                default -> walk.refresh(new StepLocation(streamId, (long) part, record.index()));
            };
        } finally {
            stepping.audit(context, List.of(streamId), "Stepped stream " + streamId + " through "
                                                       + pipeline.name() + " with the " + NAME + " tool");
        }
        if (!PipelineStepping.isFound(result)) {
            throw new ToolRefusal("No record found in stream " + streamId + " for " + record.said());
        }

        final List<String> wanted = show.isEmpty()
                ? pipeline.defaultOutputs()
                : show;
        final int budget = PipelineStepping.MAX_STREAM_CHARS / Math.max(1, 2 * wanted.size());
        final Map<String, Object> outputs = new LinkedHashMap<>();
        for (final String element : wanted) {
            if (SteppingWalks.elements(result).containsKey(element)) {
                final Map<String, Object> shown = new LinkedHashMap<>();
                shown.put("type", pipeline.types().get(element));
                shown.put("input", cut(SteppingWalks.input(result, element), budget));
                shown.put("output", cut(SteppingWalks.output(result, element), budget));
                outputs.put(element, shown);
            }
        }
        final StepLocation location = result.getFoundLocation();
        final String key = location == null
                ? null
                : String.valueOf(location.getRecordIndex());
        final List<ErrorTriage.Marker> markers = new ArrayList<>(SteppingWalks.markers(result, key));
        markers.addAll(SteppingVerdicts.emptyOutput(result, pipeline.outputElement(), key));
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("pipeline", pipeline.name());
        out.put("stream_id", streamId);
        out.put("part", location == null
                ? null
                : location.getPartIndex());
        out.put("record", location == null
                ? null
                : location.getRecordIndex());
        out.put("draft_code_used", PipelineStepping.draftCodeUsed(draftCode));
        out.put("elements", outputs);
        out.putAll(stepping.triage(markers, pipeline, null));
        return out;
    }

    // The record argument: an index, or first or last.
    private Record record(final ToolCall call) {
        final String given = stringArg(call, RECORD_ARG);
        if (given == null || "first".equalsIgnoreCase(given) || "last".equalsIgnoreCase(given)) {
            final String kind = given == null
                    ? "first"
                    : given.toLowerCase(Locale.ROOT);
            return new Record(kind, 0, PythonText.repr(kind));
        }
        final Integer index = intArg(call, RECORD_ARG);
        if (index == null || index < 0) {
            throw new ToolRefusal("record is a zero-based record index within the part, or 'first' / 'last', not '"
                                  + given + "'");
        }
        return new Record("index", index, String.valueOf(index));
    }

    private static String cut(final String text, final int budget) {
        final String value = text == null
                ? ""
                : text;
        return value.length() > budget
                ? value.substring(0, budget)
                : value;
    }


    // --------------------------------------------------------------------------------


    /// The record to step.
    ///
    /// @param kind  `first`, `last` or `index`.
    /// @param index The zero-based index within the part, for `index`.
    /// @param said  It as the Stroom MCP server said it (`4`, `'first'`).
    record Record(String kind, long index, String said) {

    }
}

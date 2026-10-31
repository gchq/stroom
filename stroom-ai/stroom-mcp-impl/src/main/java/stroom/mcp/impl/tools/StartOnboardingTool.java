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
import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolDescription;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.IntegerSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolSchema.StringSchema;
import stroom.ai.impl.tools.sample.SampleTexts;
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/// `start_onboarding` - starts onboarding a source: profiles every sample file, creates the build, and returns
/// the plan with its first step and the standing instructions that apply (the Stroom MCP server's
/// `tools/plan.py` `start_onboarding`). It creates only the build's folder (and the workspace folder, the first
/// time), as the user, through [WriteGuard].
class StartOnboardingTool extends AbstractBuiltInTool {

    static final String NAME = "start_onboarding";
    static final String SOURCE_NAME_ARG = "source_name";
    static final String SAMPLES_ARG = "samples";
    static final String FILES_ARG = "files";
    static final String BUILD_ARG = "build";
    static final String FOLDERS_ARG = "folders";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String KIND_ARG = "kind";

    private final WriteGuard guard;
    private final BuildPlan plan;
    private final SampleProfiling sampleProfiler;
    private final SampleStreamTexts sampleStreamTexts;
    private final PipelineTemplates pipelineTemplates;
    private final AgentInstructions agentInstructions;
    private final BuildRecords buildRecords;

    @Inject
    StartOnboardingTool(final WriteGuard guard,
                        final BuildPlan plan,
                        final SampleProfiling sampleProfiler,
                        final SampleStreamTexts sampleStreamTexts,
                        final PipelineTemplates pipelineTemplates,
                        final AgentInstructions agentInstructions,
                        final BuildRecords buildRecords) {
        this.guard = guard;
        this.plan = plan;
        this.sampleProfiler = sampleProfiler;
        this.sampleStreamTexts = sampleStreamTexts;
        this.pipelineTemplates = pipelineTemplates;
        this.agentInstructions = agentInstructions;
        this.buildRecords = buildRecords;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It makes the build's folder.
        return false;
    }

    @Override
    public boolean changesOnlyItsBuild() {
        // Through WriteGuard, only what is in the build's folder.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .note("The build's folder is made in the workspace folder (stroom.ai.builds.workspaceFolder) as the "
                      + "user, who needs permission to create folders there; the sample streams are read as the "
                      + "user, who needs View on their feeds.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Start onboarding a source: profiles every sample file (format, fields, timestamp "
                             + "patterns, what differs between files, which parser and template to use, whether a "
                             + "text converter is needed), creates the build, and returns the plan with its first "
                             + "step and the standing instructions that apply. The work is not done until "
                             + "build_status shows every step done and promote_build has run; each tool's result "
                             + "says what is next. Samples that are files: give files= (their paths) and no text; "
                             + "the server reads them once uploaded (stream_ids). Never call profile_sample as "
                             + "well: this profiles them.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(SOURCE_NAME_ARG, "The source, e.g. 'Acme door controller' (names the "
                                                            + "build).")
                        .addProperty(SAMPLES_ARG, ArraySchema.builder()
                                .items(StringSchema.builder().build())
                                .description("Only for samples that are not files (pasted text): every sample's "
                                             + "text, by name or as a list, to tell the format and fields from: "
                                             + SampleTexts.SAMPLE_GUIDE + ". Never trimmed further, completed "
                                             + "or repaired. Not paths: Stroom cannot read the client's "
                                             + "files.")
                                .build())
                        .addProperty(FILES_ARG, stringArraySchema("The preferred start when the samples are files: "
                                                                  + "their paths on the user's disk, every file they "
                                                                  + "have. The build is created and the next steps "
                                                                  + "given: create_feed, upload_sample files=these "
                                                                  + "paths, then start_onboarding again with build "
                                                                  + "and the stream_ids, which profiles them. Their "
                                                                  + "text never passes through you."))
                        .addStringProperty(BUILD_ARG, "Build name; defaults to one made from the source name.")
                        .addProperty(FOLDERS_ARG, stringArraySchema("Folders the work will be promoted to, if "
                                                                    + "known."))
                        .addProperty(STREAM_IDS_ARG, ArraySchema.builder()
                                .items(new IntegerSchema(ToolDescription.of("A stream id.")))
                                .description("Sample streams to read the data from, in place of its text (e.g. "
                                             + "after upload_sample), so the text need not be sent again; the "
                                             + "server reads them itself.")
                                .build())
                        .addEnumProperty(KIND_ARG, BuildRecords.BUILD_KINDS, "The kind of work, when it is not an "
                                                                             + "events pipeline: discovery (raw data "
                                                                             + "indexed as it is, for exploration) "
                                                                             + "or reference (a table to look values "
                                                                             + "up in); the plan's steps follow it.")
                        .required(SOURCE_NAME_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String sourceName = stringArg(call, SOURCE_NAME_ARG);
        if (sourceName == null) {
            return JsonResults.refused("Give source_name: the source, e.g. 'Acme door controller'.");
        }
        try {
            final Object samples = argsMap(call).get(SAMPLES_ARG);
            final List<String> files = stringListArg(call, FILES_ARG);
            final List<String> folders = stringListArg(call, FOLDERS_ARG);
            final List<Long> streamIds = streamIds(call, STREAM_IDS_ARG);
            final String build = buildName(sourceName, stringArg(call, BUILD_ARG));
            final String kind = stringArg(call, KIND_ARG);
            if (kind != null && !BuildRecords.BUILD_KINDS.contains(kind)) {
                return JsonResults.refused("kind is one of " + PyText.list(BuildRecords.BUILD_KINDS) + ", not '"
                                           + kind + "'");
            }
            if (!files.isEmpty() && samples == null && streamIds.isEmpty()) {
                return JsonResults.of(filesFirst(context, sourceName, files, build, folders, kind));
            }
            return JsonResults.of(start(context, sourceName, samples, build, folders, streamIds, kind));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> start(final ToolCallContext context,
                                      final String sourceName,
                                      final Object samples,
                                      final String build,
                                      final List<String> folders,
                                      final List<Long> streamIds,
                                      final String kind) {
        List<String> notes = List.of();
        final Map<String, String> named;
        if (!streamIds.isEmpty() && samples == null) {
            final SampleStreamTexts.Texts read = sampleStreamTexts.read(streamIds);
            named = read.texts();
            notes = read.notes();
        } else {
            named = SampleTexts.asNamedSamples(samples, null);
        }
        if (named.isEmpty()) {
            throw new ToolRefusal("Give the sample files' text (samples by file name), or stream_ids once they are "
                                  + "uploaded; ask the user for every file they have");
        }
        final WriteGuard.Folder folder = guard.buildFolder(build);
        plan.rememberBuild(context, build);
        remember(build, kind);
        final Map<String, Object> profiled = sampleProfiler.profileAll(named);
        final Optional<String> tooLong = samples != null
                ? Optional.ofNullable(SampleTexts.longSamplesNote(named))
                : Optional.empty();
        final String format = String.valueOf(profiled.get("format"));
        final String parser = BuildPlan.PARSER_FOR_FORMAT.getOrDefault(format, List.of("DSParser")).getFirst();
        final List<Map<String, Object>> steps = steps(build, kind);
        // Given streams (or a kind), the build itself says what is next: the feed and samples exist already, or the
        // kind's steps differ from an onboarding's.
        final Optional<Map<String, Object>> next = streamIds.isEmpty() && kind == null
                ? Optional.empty()
                : plan.nextStep(build, null);
        final Map<String, Object> result = new LinkedHashMap<>();
        if (!notes.isEmpty()) {
            result.put("read", notes);
        }
        tooLong.ifPresent(note -> result.put("sample_note", note));
        result.put("build", build);
        result.put("folder", folder.path());
        result.put("source", sourceName);
        result.put("profile", profiled);
        result.put("parser", parser);
        result.put("template", "reference".equals(kind)
                ? "find_pipeline_templates stage=reference: the Reference Data template, its parser reading the "
                  + "table (stroom://guide/reference-data)"
                : "discovery".equals(kind)
                        ? "find_pipeline_templates stage=discovery: a template whose parser reads " + format
                        : templatesReading(format));
        if (kind != null) {
            result.put("kind", kind);
        }
        result.put("text_converter", BuildPlan.TEXT_FORMATS.contains(format)
                ? "needed: build_data_splitter from a spec, then save_text_converter"
                : "xml fragments".equals(format)
                        ? "needed: the XML fragment wrapper (profile text_converter); build_data_splitter "
                          + "save_as=<name> saves it"
                        : "not needed: the template's parser reads this format");
        result.put("plan", steps);
        result.put("task_list", BuildPlan.TASK_LIST);
        result.put("next", next.orElseGet(() -> firstStep(build, steps)));
        result.put("done", false);
        result.put("standing_instructions", instructions(context, folders));
        result.put("hint", "Propose the feed name from sibling feeds and create_feed; upload each file as its own "
                           + "stream; from then on give tools the sample streams (stream_ids), not the text again: "
                           + "the converter (if needed), build_translation_xslt with the streams and build and name "
                           + "(it saves the XSLT with the mapping), the pipeline, step_sample over all streams until "
                           + "clean, process, validate, write_documentation, index. build_status shows what remains "
                           + "at any point. Ask the user now only what stage 1 needs: the indexing backend and "
                           + "field naming are asked by stage 2's tools when next reaches them.");
        result.put("tools", BuildPlan.HIDDEN_TOOLS);
        return result;
    }

    // Onboarding from files on the user's disk: the build now, the profile once they are uploaded. Seen (Gemma 4
    // 31B in VS Code, ~22 tokens a second): the sample's text written into profile_sample and again into
    // start_onboarding took 77 s each; reading 100 lines of it, the next call would have taken over ten minutes.
    private Map<String, Object> filesFirst(final ToolCallContext context,
                                           final String sourceName,
                                           final List<String> files,
                                           final String build,
                                           final List<String> folders,
                                           final String kind) {
        final WriteGuard.Folder folder = guard.buildFolder(build);
        plan.rememberBuild(context, build);
        remember(build, kind);
        final List<Map<String, Object>> steps = steps(build, kind);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("build", build);
        result.put("folder", folder.path());
        result.put("source", sourceName);
        result.put("files", files);
        result.put("plan", steps);
        result.put("task_list", BuildPlan.TASK_LIST);
        result.put("next", kind == null
                ? firstStep(build, steps)
                : plan.nextStep(build, null).orElseGet(() -> firstStep(build, steps)));
        result.put("then", List.of(
                "upload_sample feed=<the feed> files=" + PyText.list(files) + ": run each command it "
                + "gives in the user's terminal; each prints the stream_id",
                "start_onboarding source_name='" + sourceName + "' build='" + build + "' stream_ids=<those stream "
                + "ids>: it profiles the uploaded files (format, fields, parser, template, converter) and says what "
                + "is next"));
        result.put("done", false);
        result.put("standing_instructions", instructions(context, folders));
        result.put("hint", "Propose the feed name from sibling feeds and create_feed. Don't read the files' text into "
                           + "the conversation: the server reads the uploaded streams. Ask the user now only what "
                           + "stage 1 needs, not the indexing backend.");
        result.put("tools", BuildPlan.HIDDEN_TOOLS);
        return result;
    }

    // The kind of work the build is, when named: kept in its record, so the plan's steps follow it from the start.
    private void remember(final String build, final String kind) {
        if (kind != null) {
            buildRecords.rememberKind(build, kind);
        }
    }

    // A new source's onboarding steps; the build's own, once its kind is named.
    private List<Map<String, Object>> steps(final String build, final String kind) {
        return kind == null
                ? BuildPlan.onboardingChecklist()
                : plan.plannedSteps(build);
    }

    private static Map<String, Object> firstStep(final String build, final List<Map<String, Object>> steps) {
        final BuildPlan.NextCall call = BuildPlan.nextCall("feed", BuildPlan.CallArgs.of(build, List.of(), List.of(),
                List.of(), null, null));
        final Map<String, Object> next = new LinkedHashMap<>();
        next.put("step", "feed");
        next.put("do", steps.getFirst().get("what"));
        next.put("call", call.call());
        next.put("then", call.then());
        return next;
    }

    /// The environment's translation templates whose parser reads the format, by name as they are there: no
    /// template is assumed to exist, or to be called anything (seen: guidance naming 'Event Data (XML)' wherever).
    ///
    /// @param format A profiled format.
    /// @return Which templates read it, or what to do when none does.
    String templatesReading(final String format) {
        final List<String> readers = BuildPlan.PARSER_FOR_FORMAT.getOrDefault(format,
                List.of("DSParser", "CombinedParser"));
        List<Map<?, ?>> candidates;
        try {
            candidates = pipelineTemplates.find("translation").get("candidates") instanceof final List<?> list
                    ? list.stream().filter(Map.class::isInstance).<Map<?, ?>>map(item -> (Map<?, ?>) item).toList()
                    : List.of();
        } catch (final RuntimeException e) {
            candidates = List.of();
        }
        final List<String> fitting = new ArrayList<>();
        candidates.stream()
                .filter(candidate -> readers.contains(String.valueOf(candidate.get("parser"))))
                .forEach(candidate -> fitting.add(String.valueOf(candidate.get("name"))));
        if (!fitting.isEmpty()) {
            return String.join(", ", fitting.subList(0, Math.min(3, fitting.size()))) + " (its parser, "
                   + readers.getFirst() + ", reads " + format + "; find_pipeline_templates stage=translation)";
        }
        // No template has the parser: one whose parser can be swapped for it, the XMLParser's first for XML
        // fragments (seen: a Stroom with no XMLParser template was told only to ask the user).
        final String reader = readers.getFirst();
        if (PipelineWrites.PARSERS.contains(reader)) {
            final List<Map<?, ?>> parsing = candidates.stream()
                    .filter(candidate -> PipelineWrites.PARSERS.contains(String.valueOf(candidate.get("parser"))))
                    .toList();
            Optional<Map<?, ?>> swappable = "xml fragments".equals(format)
                    ? parsing.stream().filter(candidate -> "XMLParser".equals(candidate.get("parser"))).findFirst()
                    : Optional.empty();
            if (swappable.isEmpty()) {
                swappable = parsing.stream().findFirst();
            }
            if (swappable.isPresent()) {
                return swappable.get().get("name") + " with replace_parser='" + reader + "' (no template here has "
                       + "a " + reader + "; its " + swappable.get().get("parser") + " is replaced)";
            }
        }
        return "none found that reads " + format + " (a " + reader + "): ask the user which pipeline to base it on "
               + "(find_pipeline_templates stage=translation lists what there is)" + (candidates.isEmpty()
                ? "; only if they have none, create_pipeline standalone='events' builds one on its own (a last "
                  + "resort)"
                : "");
    }

    private Map<String, Object> instructions(final ToolCallContext context, final List<String> folders) {
        return agentInstructions.applicableInstructions(context.session(), "Standing instructions read by the "
                                                                            + NAME + " tool",
                agentInstructions.targets(folders, List.of(), List.of(), List.of()));
    }

    /// @param sourceName The source's name.
    /// @param build      The build named, or null.
    /// @return The build: the one named, else one made from the source's name, e.g. `onboard-acme-door`.
    static String buildName(final String sourceName, final String build) {
        if (NullSafe.isNonBlankString(build)) {
            return build;
        }
        final StringBuilder sb = new StringBuilder();
        sourceName.toLowerCase(Locale.ROOT).codePoints().forEach(c -> {
            if (Character.isLetterOrDigit(c)) {
                sb.appendCodePoint(c);
            } else {
                sb.append('-');
            }
        });
        String name = sb.toString();
        int start = 0;
        int end = name.length();
        while (start < end && name.charAt(start) == '-') {
            start++;
        }
        while (end > start && name.charAt(end - 1) == '-') {
            end--;
        }
        name = name.substring(start, end);
        return "onboard-" + name.substring(0, Math.min(40, name.length()));
    }

    /// The stream ids given: a list, one id, or ids in a string, separated by commas.
    ///
    /// @param call The call.
    /// @param name The argument that gives them, e.g. `stream_ids`.
    /// @return The ids, in the order given; empty if none are given.
    /// @throws ToolRefusal if one is not a number.
    static List<Long> streamIds(final ToolCall call, final String name) {
        final List<Long> ids = new ArrayList<>();
        for (final String value : stringListArg(call, name)) {
            try {
                ids.add((long) Double.parseDouble(value.replace("[", "").replace("]", "").strip()));
            } catch (final NumberFormatException e) {
                throw new ToolRefusal(name + " are stream ids (numbers): '" + value + "' is not one");
            }
        }
        return ids;
    }
}

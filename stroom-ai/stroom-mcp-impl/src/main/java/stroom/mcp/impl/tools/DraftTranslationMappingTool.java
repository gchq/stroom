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
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.draft.DraftMapping;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.generation.MappingCompaction;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.SampleTexts;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.workspace.PatternTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/// `draft_translation_mapping` - a starting translation mapping drafted from the sample, to edit rather than write
/// from nothing (the Stroom MCP server's `tools/generation.py` `draft_translation_mapping`, on [DraftMapping]). The
/// user chooses the event-logging version it writes ([EventVersions]), and the draft is kept in the session's
/// workspace ([SentMappings]) so the first save sends only what changes.
class DraftTranslationMappingTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DraftTranslationMappingTool.class);

    static final String NAME = "draft_translation_mapping";
    static final String SAMPLES_ARG = "samples";
    static final String SOURCE_NAME_ARG = "source_name";
    static final String SYSTEM_NAME_ARG = "system_name";
    static final String ENVIRONMENT_ARG = "environment";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String SPLITTER_ARG = "splitter";
    static final String BUILD_ARG = "build";
    static final String SCHEMA_VERSION_ARG = "schema_version";

    /// What `stream_ids` is, for the tools that read sample streams in place of their text.
    static final String SAMPLE_STREAMS = "Sample streams to read the data from, in place of its text (e.g. after "
                                         + "upload_sample), so the text need not be sent again; the server reads "
                                         + "them itself.";

    private static final Pattern SENTENCE_END = Pattern.compile("(?<=\\.) ");

    private final EventVersions eventVersions;
    private final SampleStreamTexts sampleStreamTexts;
    private final TranslationBuilds builds;
    private final SampleProfiling sampleProfiling;
    private final CurrentTranslations currentTranslations;

    @Inject
    DraftTranslationMappingTool(final EventVersions eventVersions,
                                final SampleStreamTexts sampleStreamTexts,
                                final TranslationBuilds builds,
                                final SampleProfiling sampleProfiling,
                                final CurrentTranslations currentTranslations) {
        this.eventVersions = eventVersions;
        this.sampleStreamTexts = sampleStreamTexts;
        this.builds = builds;
        this.sampleProfiling = sampleProfiling;
        this.currentTranslations = currentTranslations;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .unlockedBy(AppPermission.VIEW_DATA_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .note("Sample text is drafted from as it is given; sample streams (stream_ids) and the build's "
                      + "source notes are read as the user.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "A starting translation mapping drafted from the sample, to edit rather than write "
                             + "from nothing: the input kind and layout from the profile, the obvious event-logging "
                             + "homes for fields by name (time with its pattern, host, client and server addresses "
                             + "and ports, user, event type, message), one rule per kind of event the naming field "
                             + "shows (Authenticate for logon and logoff kinds, Unknown with Data for the rest, to "
                             + "replace with the right action element: build_translation_xslt refuses a rule with "
                             + "conditions that keeps Unknown, unless it sets allow_unknown), every other field "
                             + "carried as Data, a text of key/value pairs (Key: value, Key: [value], key=value) "
                             + "read as one extract entry a key, its keys then fields like any other (the kind of "
                             + "event among them), and notes on what is left to decide. For text formats the Data "
                             + "Splitter spec comes with it. Then build_translation_xslt with the edited mapping, "
                             + "the sample and the splitter. The user chooses the event-logging version it writes "
                             + "(a form, or needs_guidance), from those this Stroom holds.")
                .parameters(ObjectSchema.builder()
                        .addProperty(SAMPLES_ARG, stringArraySchema("Only for samples that are not files (pasted "
                                                                    + "text): their text, by name or as a list, to "
                                                                    + "tell the format and fields from: "
                                                                    + SampleTexts.SAMPLE_GUIDE + ". Uploaded "
                                                                    + "samples are read from their streams "
                                                                    + "(stream_ids)."))
                        .addStringProperty(SOURCE_NAME_ARG, "The source, e.g. 'Acme door controller': names the "
                                                            + "system and generator until the user confirms them.")
                        .addStringProperty(SYSTEM_NAME_ARG, "EventSource/System/Name, if the user has said.")
                        .addStringProperty(ENVIRONMENT_ARG, "EventSource/System/Environment, if the user has said, "
                                                            + "e.g. Prod.")
                        .addProperty(STREAM_IDS_ARG, BuildTranslationXsltTool.streamIdsSchema(SAMPLE_STREAMS))
                        .addProperty(SPLITTER_ARG, TranslationArgs.object("The Data Splitter spec the text is read "
                                                                          + "with (build_data_splitter's spec), "
                                                                          + "when you settled on one, e.g. named "
                                                                          + "columns for a file with no header: the "
                                                                          + "draft then uses its field names. "
                                                                          + "Inferred from the sample when left "
                                                                          + "out."))
                        .addStringProperty(BUILD_ARG, "The build holding the source notes (record_source_notes): "
                                                      + "the draft then follows the user's documentation.")
                        .addStringProperty(SCHEMA_VERSION_ARG, "Event-logging version the translation writes, e.g. "
                                                               + "'4.0.2', if the user has said. Left out, the user "
                                                               + "is asked which of the versions this Stroom holds "
                                                               + "(the newest recommended).")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(draft(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final PatternTimeoutException e) {
            // A pattern of the agent's took too long on the sample: saying so is the answer, not a fault.
            return JsonResults.refused(e.getMessage());
        }
    }

    private Map<String, Object> draft(final ToolCall call, final ToolCallContext context) {
        final Object given = ToolArgs.raw(call, SAMPLES_ARG);
        final List<Long> streamIds = StartOnboardingTool.streamIds(call, STREAM_IDS_ARG);
        final String build = stringArg(call, BUILD_ARG);
        if (given == null && streamIds.isEmpty()) {
            throw new ToolRefusal("Give the samples' text, or stream_ids of the uploaded sample streams");
        }
        // Asked first: the schema the draft is checked against, and nothing read before the user answers.
        final EventVersions.Target target = eventVersions.target(call, context, stringArg(call, SCHEMA_VERSION_ARG),
                build);
        if (target.version() == null) {
            return target.question();
        }
        final String version = target.version();
        final Map<String, String> samples = given == null
                ? sampleStreamTexts.read(streamIds).texts()
                : SampleTexts.asNamedSamples(given, null);
        if (samples.isEmpty()) {
            throw new ToolRefusal("Give the samples' text, or stream_ids of the uploaded sample streams");
        }
        final Map<String, Object> sourceNotes = build == null
                ? null
                : builds.notesInBuild(build);
        final Map<String, Object> result = drafted(samples, call, sourceNotes, detailCheck(sourceNotes, version));
        @SuppressWarnings("unchecked") final Map<String, Object> mapping = MappingCompaction.compactPairs(
                MappingCompaction.compactRules((Map<String, Object>) result.get("mapping")));
        result.put("mapping", mapping);
        if ("xml_fragments".equals(mapping.get("input"))) {
            // Read in the namespace of the wrapper the environment uses, which need not be records:2.
            fragmentNamespace(context, build, samples.values().iterator().next())
                    .ifPresent(namespace -> mapping.put("xml_namespace", namespace));
        }
        result.put("schema_check", schemaCheck(mapping, version));
        result.put("schema_version", version);
        final Optional<String> settled = eventVersions.settled(context, build, version);
        settled.ifPresent(text -> result.put("schema_version_settled", text));
        currentTranslation(build, result);
        // Kept for this session, so the first save sends only what changes (seen in testing: a 50 KB draft, written
        // out whole in one call, never reached the server).
        SentMappings.remember(context, SentMappings.DRAFT + NullSafe.string(build), mapping);
        if (build != null) {
            SentMappings.remember(context, SentMappings.DRAFT, mapping);
        }
        result.put("hint", settled.map(text -> text + " ").orElse("")
                           + "Decide what the notes ask (the action element per kind of event, System Name and "
                           + "Environment, the time zone), then build_translation_xslt(build=, name= to save it, "
                           + "changes= only the rules and entries you change, schema_version=this schema_version, "
                           + "stream_ids=the sample streams (or sample=the texts), splitter=this splitter): the "
                           + "server keeps this draft for the session, so it needn't be sent whole. A rule's data "
                           + "lists the fields it carries as Data of its action element; keep it when you change the "
                           + "element.");
        return result;
    }

    // A next version drafted from the sample: what the current XSLT looks up is in neither (seen in testing: V1.3
    // drafted for a feed V1.2 translates, and its IP lookups were gone, though the user meant to keep them).
    private void currentTranslation(final String build, final Map<String, Object> result) {
        final Map<String, Object> current = build == null
                ? null
                : currentTranslations.current(build).orElse(null);
        if (CurrentTranslations.lookupsOf(current)) {
            result.put("current_translation", current);
            final List<Object> notes = new ArrayList<>(NullSafe.list((List<?>) result.get("notes")));
            notes.add(CurrentTranslations.lookupsNote(current));
            result.put("notes", notes);
        }
    }

    private Map<String, Object> drafted(final Map<String, String> samples,
                                        final ToolCall call,
                                        final Map<String, Object> sourceNotes,
                                        final Function<String, String> detailCheck) {
        final SplitterSpec splitter = BuildTranslationXsltTool.Request.splitter(ToolArgs.raw(call,
                SPLITTER_ARG));
        return new LinkedHashMap<>(DraftMapping.draft(samples, NullSafe.string(rawArg(call, SOURCE_NAME_ARG)),
                stringArg(call, SYSTEM_NAME_ARG), stringArg(call, ENVIRONMENT_ARG), sourceNotes, detailCheck,
                splitter));
    }

    // Why a catalogued event can't be its action element; the notes are followed unchecked rather than not at all
    // when the schema can't be read.
    private Function<String, String> detailCheck(final Map<String, Object> sourceNotes, final String version) {
        if (sourceNotes == null || !(sourceNotes.get("events") instanceof final List<?> events) || events.isEmpty()) {
            return null;
        }
        try {
            final EventSchema schema = eventVersions.eventSchema(version);
            return detail -> SourceNotes.detailProblem(schema, detail);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The catalogue's event details not checked: {}", e.getMessage()));
            return null;
        }
    }

    private Optional<String> fragmentNamespace(final ToolCallContext context, final String build, final String text) {
        final Map<String, Object> setup = sampleProfiling.withFragmentSetup(sampleProfiling.profile(text),
                eventVersions.chosen(context, build).orElse(null));
        return setup.get("xslt_input") instanceof final Map<?, ?> input && input.get("namespace") != null
                ? Optional.of(String.valueOf(input.get("namespace")))
                : Optional.empty();
    }

    // Each problem's first sentence: build_translation_xslt gives them whole, and the draft's notes say the rest.
    private Map<String, Object> schemaCheck(final Map<String, Object> mapping, final String version) {
        final Map<String, Object> check = new LinkedHashMap<>();
        try {
            final XsltGenerator.Generated checked = XsltGenerator.generate(TranslationMapping.parse(mapping),
                    eventVersions.eventSchema(version), version);
            check.put("ok", checked.ok());
            check.put("problems", MappingCompaction.grouped(checked.problems()).stream()
                    .map(DraftTranslationMappingTool::firstSentence)
                    .toList());
            final List<String> warnings = MappingCompaction.grouped(checked.warnings());
            check.put("warnings", warnings.subList(0, Math.min(6, warnings.size())));
        } catch (final ToolRefusal e) {
            check.put("ok", null);
            check.put("note", e.getMessage());
        }
        return check;
    }

    /// @param text A problem.
    /// @return Its first sentence, cut at a word near 220 characters.
    static String firstSentence(final String text) {
        final String first = SENTENCE_END.split(text, 2)[0];
        if (first.length() <= 220) {
            return first;
        }
        final String start = first.substring(0, 200);
        final int space = start.lastIndexOf(' ');
        return (space < 0
                ? start
                : start.substring(0, space)) + " …";
    }
}

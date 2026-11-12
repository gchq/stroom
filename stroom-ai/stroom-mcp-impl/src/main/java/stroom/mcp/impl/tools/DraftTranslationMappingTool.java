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
import stroom.ai.impl.tools.InstructionRules;
import stroom.ai.impl.tools.JsonValues;
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
import stroom.ai.impl.tools.sample.ToolJson;
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
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/// `draft_translation_mapping` - a starting translation mapping drafted from the sample, to edit rather than write from
/// nothing (on [DraftMapping]). The user chooses the event-logging version it writes ([EventVersions]), and the draft
/// is kept in the session's workspace ([SentMappings]) so the first save sends only what changes.
class DraftTranslationMappingTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DraftTranslationMappingTool.class);

    static final String NAME = "draft_translation_mapping";
    static final String SAMPLES_ARG = "samples";
    static final String SOURCE_NAME_ARG = "source_name";
    static final String SYSTEM_NAME_ARG = "system_name";
    static final String ENVIRONMENT_ARG = "environment";
    static final String USER_CHANGED_ARG = "user_changed";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String SPLITTER_ARG = "splitter";
    static final String BUILD_ARG = "build";
    static final String SCHEMA_VERSION_ARG = "schema_version";
    static final String CONTINUATION_LINES_ARG = "continuation_lines";
    /// The question put to the user when records carry stack traces.
    static final String TRACE_QUESTION = "Records carry stack traces. How much of each should its Event keep?";
    /// The answers, by the lines of each trace they keep (0: all of it).
    static final Map<String, Integer> TRACE_ANSWERS = traceAnswers();

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
    private final SiblingSharedCalls siblingSharedCalls;
    private final Consent consent;
    private final Provider<BuildRecords> buildRecords;

    @Inject
    DraftTranslationMappingTool(final EventVersions eventVersions,
                                final SampleStreamTexts sampleStreamTexts,
                                final TranslationBuilds builds,
                                final SampleProfiling sampleProfiling,
                                final CurrentTranslations currentTranslations,
                                final SiblingSharedCalls siblingSharedCalls,
                                final Consent consent,
                                final Provider<BuildRecords> buildRecords) {
        this.eventVersions = eventVersions;
        this.sampleStreamTexts = sampleStreamTexts;
        this.builds = builds;
        this.sampleProfiling = sampleProfiling;
        this.currentTranslations = currentTranslations;
        this.siblingSharedCalls = siblingSharedCalls;
        this.consent = consent;
        this.buildRecords = buildRecords;
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
                                                            + "generator. The system's name and environment are the "
                                                            + "user's to give (system_name, environment).")
                        .addStringProperty(SYSTEM_NAME_ARG, "EventSource/System/Name, if the user has said: one "
                                                            + "name, or for a stream of several products a JSON "
                                                            + "object of each variant's, e.g. {\"cef\": "
                                                            + "\"SecureGate\"}.")
                        .addStringProperty(ENVIRONMENT_ARG, "EventSource/System/Environment, if the user has said, "
                                                            + "e.g. Prod.")
                        .addBooleanProperty(USER_CHANGED_ARG, "True when the user has changed the system_name or "
                                                              + "environment they gave earlier for this build (the "
                                                              + "draft keeps what they first gave, and refuses "
                                                              + "others).")
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
                        .addStringProperty(SCHEMA_VERSION_ARG, "Event-logging version the translation writes, "
                                                               + "if the user has chosen one of those this Stroom "
                                                               + "holds. Left out, the user "
                                                               + "is asked which of the versions this Stroom holds "
                                                               + "(the newest recommended). Where it holds the "
                                                               + "version in more than one XML schema document, "
                                                               + "the UUID of the one the user chose."
                                                               + " Where it also holds the environment's fork of the "
                                                               + "event-logging schema, the schema is settled first, "
                                                               + "by namespace (event-logging:3 or the fork's), as "
                                                               + "a fork's versions are its own: give the namespace "
                                                               + "chosen (or a fork document's UUID); a version "
                                                               + "alone is read within it.")
                        .addStringProperty(TimeSources.TIME_SOURCE_ARG, "Records with no time: received or created "
                                                                        + "(the stream's, an approximation) or none, "
                                                                        + "as the user chose; asked in a form when "
                                                                        + "left out.")
                        .addStringProperty(TimeSources.DATE_SOURCE_ARG, "Times with no date and no header giving "
                                                                        + "it: received, created, file_name or none, "
                                                                        + "as the user chose; asked in a form when "
                                                                        + "left out.")
                        .addStringProperty(TimeSources.TIMEZONE_ARG, "Times with no zone or offset: the zone the "
                                                                     + "source writes them in, as the user said (an "
                                                                     + "IANA name or +10:00), or field:NAME / "
                                                                     + "meta:NAME for each record's own (|ZONE the "
                                                                     + "default); asked in a form when left out.")
                        .addIntegerProperty(CONTINUATION_LINES_ARG, "Records with stack traces: the most lines of "
                                                                    + "each its Event keeps, as the user chose (0: "
                                                                    + "all of it); asked in a form when left out.")
                        .build())
                .build();
    }

    private static Map<String, Integer> traceAnswers() {
        final Map<String, Integer> answers = new LinkedHashMap<>();
        answers.put("All of it (recommended)", 0);
        answers.put("Its first 20 lines", 20);
        answers.put("Only its first line (the exception)", 1);
        return answers;
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
        // Sample streams of an aggregated feed's build with a scope: read through it, so the rules drafted are the
        // scope's (records of other source types left out as they are read).
        final SampleStreamTexts.Scoped scoped = given == null
                ? builds.scope(build).map(scope -> SampleStreamTexts.inScope(sampleStreamTexts.read(streamIds), scope))
                        .orElse(null)
                : null;
        final Map<String, String> samples = scoped != null
                ? scoped.texts().texts()
                : given == null
                        ? sampleStreamTexts.read(streamIds).texts()
                        : SampleTexts.asNamedSamples(given, null);
        if (samples.isEmpty()) {
            throw new ToolRefusal("Give the samples' text, or stream_ids of the uploaded sample streams");
        }
        final Map<String, Object> sourceNotes = build == null
                ? null
                : builds.notesInBuild(build);
        awaitingConfirmation(sourceNotes);
        final SystemGiven system = system(call, build);
        // The spec given, else the build's saved Data Splitter, as classify_kinds and the sample check read the
        // records (seen in chat 169: the draft inferred a splitter of its own, and the agent drafted again with the
        // saved one's spec).
        SplitterSpec splitter = BuildTranslationXsltTool.Request.splitter(ToolArgs.raw(call, SPLITTER_ARG));
        String readWith = null;
        if (splitter == null && build != null) {
            final Optional<Map.Entry<String, String>> saved = builds.dataSplitter(build, null);
            if (saved.isPresent()) {
                splitter = SplitterSpec.ofConverter(saved.get().getValue());
                readWith = saved.get().getKey();
            }
        }
        final Map<String, Object> result = drafted(samples, call, system, sourceNotes,
                detailCheck(context, build, sourceNotes, version), splitter);
        if (readWith != null) {
            result.put("read_with", "the build's Data Splitter '" + readWith + "'");
        }
        if (scoped != null) {
            result.put("scope", scoped.said());
        }
        @SuppressWarnings("unchecked") final Map<String, Object> mapping = MappingCompaction.compactPairs(
                MappingCompaction.compactRules((Map<String, Object>) result.get("mapping")));
        result.put("mapping", mapping);
        if ("xml_fragments".equals(mapping.get("input"))) {
            // Read in the namespace of the wrapper the environment uses, which need not be records:2.
            fragmentNamespace(context, build, samples.values().iterator().next())
                    .ifPresent(namespace -> mapping.put("xml_namespace", namespace));
        }
        // The environment's common elements written as its event pipelines write them, with their shared templates:
        // in the draft, not found at the pipeline step after the XSLT was generated without them.
        final List<String> sharedNotes = SiblingSharedCalls.intoMapping(
                siblingSharedCalls.forStage(InstructionRules.TRANSLATION), mapping);
        if (!sharedNotes.isEmpty()) {
            final List<Object> notes = new ArrayList<>(NullSafe.list((List<?>) result.get("notes")));
            notes.addAll(sharedNotes);
            result.put("notes", notes);
        }
        traceLines(call, context, result, mapping);
        // Records with no time, or times with no date: where they come from is the user's to say.
        final List<Map<String, String>> headers = streamIds.stream().map(sampleStreamTexts::receiptHeaders).toList();
        TimeSources.apply(call, context, consent, NAME, result, mapping, headerValues(headers, "RemoteFile"),
                headerValues(headers, "ReceivedTime"));
        // Several formats, each its own rules: a System/Name each, unless one was given (seen: one name for five
        // products).
        final List<Object> systemNotes = new ArrayList<>(JsonValues.listOrEmpty(result.get("notes")));
        SystemNames.apply(mapping, variantFieldOf(splitter, result.get("splitter")), system.systemName(), systemNotes);
        result.put("notes", systemNotes);
        result.put("schema_check", schemaCheck(context, build, mapping, version));
        dataPlacement(context, build, mapping, version, result);
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
                           + "element. get_instructions guide=mapping lists every key a mapping and its entries take, "
                           + "the time formats, and how changes= merges.");
        return result;
    }

    // Records with stack traces: how much of each the Events keep, as the user chooses (asked for by the user,
    // the whole trace by default).
    private void traceLines(final ToolCall call,
                            final ToolCallContext context,
                            final Map<String, Object> result,
                            final Map<String, Object> mapping) {
        if (!NullSafe.list((List<?>) result.get("fields_seen")).contains(SplitterSpec.CONTINUATION)) {
            return;
        }
        Integer lines = intArg(call, CONTINUATION_LINES_ARG);
        if (lines == null) {
            lines = consent.choose(call, context, NAME, TRACE_QUESTION, List.copyOf(TRACE_ANSWERS.keySet()), null)
                    .map(TRACE_ANSWERS::get)
                    .orElse(null);
        }
        final List<Object> notes = new ArrayList<>(NullSafe.list((List<?>) result.get("notes")));
        if (lines == null) {
            notes.add("Each record's stack trace is kept whole as Data '" + SplitterSpec.CONTINUATION + "'. Ask the "
                      + "user whether its Events should keep less, and draft again with continuation_lines= the "
                      + "most lines to keep.");
        } else if (DraftMapping.withContinuationLines(mapping, lines) > 0) {
            notes.add("Each record's stack trace is cut to its first " + lines + " line(s), as the user chose.");
        }
        result.put("notes", notes);
    }

    // The field an alternatives splitter writes each record's variant in, or null for one of one format.
    private static String variantFieldOf(final SplitterSpec given, final Object splitter) {
        // A saved converter's formats, from its XML.
        final Optional<String> saved = given == null
                ? Optional.empty()
                : given.converterVariants().map(SplitterSpec.ConverterVariants::field);
        if (saved.isPresent()) {
            return saved.get();
        }
        if (splitter instanceof final Map<?, ?> spec && SplitterSpec.ALTERNATIVES.equals(spec.get("kind"))) {
            return spec.get("variant_field") == null
                    ? SplitterSpec.VARIANT
                    : String.valueOf(spec.get("variant_field"));
        }
        return null;
    }

    // A receipt header's values across the sample streams, those they carry.
    private static List<String> headerValues(final List<Map<String, String>> headers, final String name) {
        return headers.stream()
                .map(header -> header.get(name))
                .filter(value -> value != null && !value.isBlank())
                .toList();
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
                                        final SystemGiven system,
                                        final Map<String, Object> sourceNotes,
                                        final Function<String, String> detailCheck,
                                        final SplitterSpec splitter) {
        return new LinkedHashMap<>(DraftMapping.draft(samples, NullSafe.string(rawArg(call, SOURCE_NAME_ARG)),
                SystemNames.single(system.systemName()), system.environment(), sourceNotes, detailCheck, splitter));
    }

    // Classifications shown to the user and not yet confirmed: the draft would leave them out, so it waits for them
    // (seen in chat 169: the agent drafted with eight kinds still to confirm, then searched its own history for 20
    // calls to find why the plan said classify).
    private static void awaitingConfirmation(final Map<String, Object> sourceNotes) {
        if (sourceNotes == null) {
            return;
        }
        final int waiting = JsonValues.maps(sourceNotes.get(RecordSourceNotesTool.PROPOSED)).size();
        if (waiting > 0) {
            throw new ToolRefusal(waiting + " classification(s) shown to the user are still to confirm: call "
                                  + "classify_kinds build=... confirm=true (a short form asks the user), then draft.");
        }
    }

    // The System/Name and Environment the draft writes: those the user gave, as the build's record keeps them (from
    // the chat 163 review: the user answered Test, and the second draft was given Prod). Given first, they are
    // recorded; left out later, the recorded ones are used; given otherwise, the call is refused, unless the user
    // changed them (user_changed).
    private SystemGiven system(final ToolCall call, final String build) {
        final Object givenName = ToolArgs.raw(call, SYSTEM_NAME_ARG);
        final String givenEnvironment = stringArg(call, ENVIRONMENT_ARG);
        if (NullSafe.isBlankString(build)) {
            return new SystemGiven(givenName, givenEnvironment);
        }
        final String nameText = givenName == null
                ? null
                : givenName instanceof final String text
                        ? text
                        : ToolJson.dumpsSortedKeys(givenName);
        final Map<String, Object> recorded = recordedSystem(build);
        final String recordedName = (String) recorded.get("system_name");
        final String recordedEnvironment = (String) recorded.get("environment");
        final boolean changed = ToolArgs.flag(call, USER_CHANGED_ARG);
        final boolean otherName = nameText != null && recordedName != null && !nameText.equals(recordedName);
        final boolean otherEnvironment = givenEnvironment != null && recordedEnvironment != null
                                         && !givenEnvironment.equals(recordedEnvironment);
        if ((otherName || otherEnvironment) && !changed) {
            final List<String> differences = new ArrayList<>();
            if (otherName) {
                differences.add("system_name " + recordedName + " (not " + nameText + ")");
            }
            if (otherEnvironment) {
                differences.add("environment " + recordedEnvironment + " (not " + givenEnvironment + ")");
            }
            throw new ToolRefusal("The user gave this build " + String.join(" and ", differences)
                                  + ": leave them out to use the user's, or, if the user has changed them, call again "
                                  + "with user_changed=true and the new ones.");
        }
        final String name = nameText != null
                ? nameText
                : recordedName;
        final String environment = givenEnvironment != null
                ? givenEnvironment
                : recordedEnvironment;
        if ((nameText != null || givenEnvironment != null)
            && (!Objects.equals(name, recordedName) || !Objects.equals(environment, recordedEnvironment))) {
            try {
                buildRecords.get().rememberSystem(build, name, environment);
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Couldn't keep the system in build {}'s record: {}", build,
                        e.getMessage()), e);
            }
        }
        return new SystemGiven(name != null && name.startsWith("{")
                ? ToolJson.loads(name)
                : name, environment);
    }

    // The System/Name and Environment the build's record keeps; none when it keeps none or can't be read.
    private Map<String, Object> recordedSystem(final String build) {
        try {
            return buildRecords.get().system(build).orElse(Map.of());
        } catch (final RuntimeException e) {
            return Map.of();
        }
    }

    // Why a catalogued event can't be its action element; the notes are followed unchecked rather than not at all
    // when the schema can't be read.
    private Function<String, String> detailCheck(final ToolCallContext context,
                                                 final String build,
                                                 final Map<String, Object> sourceNotes,
                                                 final String version) {
        if (sourceNotes == null || !(sourceNotes.get("events") instanceof final List<?> events) || events.isEmpty()) {
            return null;
        }
        try {
            final EventSchema schema = eventVersions.eventSchema(context, build, version);
            return detail -> SourceNotes.detailProblem(schema, detail);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The catalogue's event details not checked: {}", e.getMessage()));
            return null;
        }
    }

    private Optional<String> fragmentNamespace(final ToolCallContext context, final String build, final String text) {
        final Map<String, Object> setup = sampleProfiling.withFragmentSetup(sampleProfiling.profile(text),
                eventVersions.chosen(context, build).orElse(null),
                eventVersions.chosenFork(context, build).orElse(null));
        return setup.get("xslt_input") instanceof final Map<?, ?> input && input.get("namespace") != null
                ? Optional.of(String.valueOf(input.get("namespace")))
                : Optional.empty();
    }

    // The mapping without choose_from on any entry (a copy).
    @SuppressWarnings("unchecked")
    static Map<String, Object> withoutChoices(final Map<String, Object> mapping) {
        final Map<String, Object> copy = new LinkedHashMap<>(mapping);
        copy.put("common", JsonValues.maps(mapping.get("common")).stream().map(DraftTranslationMappingTool::unmarked)
                .toList());
        copy.put("events", JsonValues.maps(mapping.get("events")).stream().map(rule -> {
            final Map<String, Object> out = new LinkedHashMap<>(rule);
            if (rule.get("fields") != null) {
                out.put("fields", JsonValues.maps(rule.get("fields")).stream()
                        .map(DraftTranslationMappingTool::unmarked)
                        .toList());
            }
            return out;
        }).toList());
        return copy;
    }

    private static Map<String, Object> unmarked(final Map<String, Object> entry) {
        final Map<String, Object> out = new LinkedHashMap<>(entry);
        out.remove(DraftMapping.CHOOSE_FROM);
        return out;
    }

    // The fields the draft carries as Data, beside the elements EventSource and each rule's action element have,
    // for the agent to place each where one means it (asked for by the user: seen in testing, a city, a latitude,
    // a URL, an HTTP method and a status code left as Data until the user asked for them to be mapped).
    private void dataPlacement(final ToolCallContext context,
                               final String build,
                               final Map<String, Object> mapping,
                               final String version,
                               final Map<String, Object> result) {
        final Map<String, Object> placement;
        try {
            placement = DataPlacements.of(mapping, eventVersions.eventSchema(context, build, version));
        } catch (final ToolRefusal e) {
            return;
        }
        if (placement.isEmpty()) {
            return;
        }
        result.put("data_placement", placement);
        final List<Object> notes = new ArrayList<>(JsonValues.listOrEmpty(result.get("notes")));
        notes.add("Some fields are carried as Data (data_placement lists them per rule, beside the elements "
                  + "EventSource and the rule's action element have): place each in the element that means it "
                  + "(a city, coordinates, a URL, a method, a status code, a rule each have one), in EventSource "
                  + "or the action element, before build_translation_xslt; keep Data only for those no element "
                  + "means.");
        result.put("notes", notes);
    }

    // Each problem's first sentence: build_translation_xslt gives them whole, and the draft's notes say the rest.
    private Map<String, Object> schemaCheck(final ToolCallContext context,
                                            final String build,
                                            final Map<String, Object> mapping,
                                            final String version) {
        final Map<String, Object> check = new LinkedHashMap<>();
        try {
            // Checked as the agent's choices will be: without the marks on times it must choose between.
            final XsltGenerator.Generated checked = XsltGenerator.generate(TranslationMapping.parse(
                            withoutChoices(mapping)),
                    eventVersions.eventSchema(context, build, version), version);
            check.put("ok", checked.ok());
            check.put("problems", MappingCompaction.grouped(checked.problems()).stream()
                    .map(DraftTranslationMappingTool::firstSentence)
                    .toList());
            if (checked.problems().stream().anyMatch(p -> p.contains(" needs one of ")
                                                          || p.contains("is required by the schema"))) {
                // Asked for by the user: what a rule's element still needs is said at the draft, not found by a
                // refused build.
                check.put("unfilled", "Some rules' action elements need parts the draft couldn't fill (problems "
                                      + "says which): fill each before build_translation_xslt. The user or object "
                                      + "a kind acts on goes in its catalogue entry's affected and affected_as "
                                      + "(classify_kinds), or straight into the rule's fields.");
            }
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


    // --------------------------------------------------------------------------------


    // The System/Name (one name, or a map of each variant's) and Environment the draft writes.
    private record SystemGiven(Object systemName, String environment) {

    }
}

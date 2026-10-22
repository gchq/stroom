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
import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.InstructionRules;
import stroom.ai.impl.tools.InstructionRules.RequiredImport;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolDescription;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.IntegerSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.draft.DraftMapping;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.fielddoc.EventFieldDocs;
import stroom.ai.impl.tools.fielddoc.FieldDocs;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.EventRule;
import stroom.ai.impl.tools.generation.MappingChangeException;
import stroom.ai.impl.tools.generation.MappingChanges;
import stroom.ai.impl.tools.generation.MappingCompaction;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.generation.SharedFunctions;
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.sample.SampleRefusal;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.ai.impl.workspace.PatternTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/// `build_translation_xslt` - writes the event-logging translation XSLT from a field mapping instead of by hand, and
/// with build and name (or uuid) saves it with its mapping (the Stroom MCP server's `tools/generation.py`
/// `build_translation_xslt`). The mapping is checked against the schema, compiled as Stroom compiles it, and checked
/// against the sample; rules kept as Unknown are agreed with the user; an edit made by hand in Stroom since the
/// tools saved the XSLT is kept ([HandEditGate]).
class BuildTranslationXsltTool extends AbstractBuiltInTool {

    static final String NAME = "build_translation_xslt";
    static final String MAPPING_ARG = "mapping";
    static final String SCHEMA_VERSION_ARG = "schema_version";
    static final String FEEDS_ARG = "feeds";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String MAX_RECORDS_ARG = "max_records";
    static final String FIELD_MAPPING_ARG = "field_mapping";
    static final String SAMPLE_ARG = "sample";
    static final String SPLITTER_ARG = "splitter";
    static final String BUILD_ARG = "build";
    static final String NAME_ARG = "name";
    static final String UUID_ARG = "uuid";
    static final String INCLUDE_XSLT_ARG = "include_xslt";
    static final String CONFIRMATION_ID_ARG = "confirmation_id";
    static final String REVIEWED_ARG = "reviewed";
    static final String CHANGE_ARG = "change";
    static final String CHANGES_ARG = "changes";
    static final String DISCARD_HAND_EDIT_ARG = "discard_hand_edit";
    static final String HAND_EDIT_CHOICES_ARG = "hand_edit_choices";

    private static final int DEFAULT_RECORDS = 50;
    private static final int MAX_RECORDS = 1000;

    private final TranslationDocs translationDocs;
    private final TranslationBuilds builds;
    private final EventVersions eventVersions;
    private final RequiredImports requiredImports;
    private final Consent consent;
    private final SampleStreamTexts sampleStreamTexts;
    private final PipelineStepping stepping;
    private final AgentInstructions agentInstructions;
    private final Provider<AiAuditLog> auditLogProvider;
    private final Clock clock;

    @Inject
    BuildTranslationXsltTool(final TranslationDocs translationDocs,
                             final TranslationBuilds builds,
                             final EventVersions eventVersions,
                             final RequiredImports requiredImports,
                             final Consent consent,
                             final SampleStreamTexts sampleStreamTexts,
                             final PipelineStepping stepping,
                             final AgentInstructions agentInstructions,
                             final Provider<AiAuditLog> auditLogProvider) {
        this(translationDocs, builds, eventVersions, requiredImports, consent, sampleStreamTexts, stepping,
                agentInstructions, auditLogProvider, Clock.systemUTC());
    }

    /// As the injected constructor, with the clock the sample check's time is read against, for tests.
    BuildTranslationXsltTool(final TranslationDocs translationDocs,
                             final TranslationBuilds builds,
                             final EventVersions eventVersions,
                             final RequiredImports requiredImports,
                             final Consent consent,
                             final SampleStreamTexts sampleStreamTexts,
                             final PipelineStepping stepping,
                             final AgentInstructions agentInstructions,
                             final Provider<AiAuditLog> auditLogProvider,
                             final Clock clock) {
        this.translationDocs = translationDocs;
        this.builds = builds;
        this.eventVersions = eventVersions;
        this.requiredImports = requiredImports;
        this.consent = consent;
        this.sampleStreamTexts = sampleStreamTexts;
        this.stepping = stepping;
        this.agentInstructions = agentInstructions;
        this.auditLogProvider = auditLogProvider;
        this.clock = clock;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // With build and name, or uuid, it saves the XSLT.
        return false;
    }

    @Override
    public boolean isReadOnly(final ToolCall call) {
        // Without them nothing is saved: the code comes back.
        return stringArg(call, BUILD_ARG) == null && stringArg(call, UUID_ARG) == null;
    }

    @Override
    public boolean hasReadOnlyCalls() {
        return true;
    }

    @Override
    public boolean usesRunSlot() {
        // It checks the mapping against the whole sample, and may step it.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .unlockedBy(AppPermission.VIEW_DATA_PERMISSION)
                .note("The XSLT is saved in the build folder, or changed, as the user, who needs Create there and "
                      + "Edit on it; sample streams are read as the user (View Data and View on their feeds), and "
                      + "the field mapping preview steps the pipeline (Stepping).")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Write the event-logging translation XSLT from a field mapping instead of by hand. "
                             + "Give the input kind (data_splitter, json or xml), fields every event shares (time, "
                             + "System, Device...), and one rule per kind of event with its conditions and fields. "
                             + "Text fields that hold several values (a message string with a time, user and "
                             + "action) are parsed by the mapping's extract list, not a function: entries {field, "
                             + "regex, names}, whose regex groups become fields named by names, used then like any "
                             + "input field (by field=, never $name in an xpath); no substring-before/after chains. "
                             + "JSON held in a string is read with an xpath using json-to-xml(). An xpath reads "
                             + "from the record (.//x, not //x: processing reads a stream's records as one "
                             + "document). Paths are checked against the schema: unknown paths come back with "
                             + "suggestions, constants are checked against allowed values, and elements are "
                             + "written in schema order with empty inputs left out; the XSLT is compiled, and one "
                             + "that doesn't compile is not saved. Fix any problems and call again with changes= "
                             + "only the rules or entries that change, not the whole mapping. With build and name "
                             + "(or uuid, to replace the XSLT saved before) the XSLT is saved with its mapping and "
                             + "the document comes back instead of the code, so step_sample steps it as saved; "
                             + "without them nothing is saved and the code comes back. The standing instructions "
                             + "(AGENTS docs) that apply come back with the result: check the mapping follows "
                             + "them, and set mapping.style from any XSLT style section in them (naming, "
                             + "variables, xsl:maps). With pipeline_uuid and stream_ids it also returns "
                             + "field_mapping, the Field mapping section of the pipeline's documentation, to read "
                             + "before writing it; the saved doc's section is generated by write_documentation "
                             + "itself from the mapping kept with the XSLT, stepped over the sample streams.")
                .parameters(ObjectSchema.builder()
                        .addProperty(MAPPING_ARG, TranslationArgs.object(
                                "The translation mapping: {input, common: [{path, field|value|...}], events: [{name, "
                                + "when, fields}]}. Start from draft_translation_mapping and edit it. A field "
                                + "inventory is not a mapping. To fix one already sent, give changes instead. "
                                + "Leave both out, with uuid=, to regenerate a saved XSLT from the mapping kept "
                                + "with it (as the generator now writes it: its current style defaults); never "
                                + "send a kept mapping back whole."))
                        .addStringProperty(SCHEMA_VERSION_ARG, "Event-logging version the translation writes, e.g. "
                                                               + "'4.0.2': the one the user chose (as "
                                                               + "draft_translation_mapping returned it). Left out: "
                                                               + "a saved XSLT's own, else the user is asked which "
                                                               + "of the versions this Stroom holds (the newest "
                                                               + "recommended).")
                        .addProperty(FEEDS_ARG, stringArraySchema("Feeds the translation is for, so the standing "
                                                                  + "instructions for their folders are included."))
                        .addStringProperty(PIPELINE_UUID_ARG, "Only with field_mapping=true: the pipeline to step "
                                                              + "the generated XSLT through (nothing is saved). "
                                                              + "Without field_mapping it is not stepped.")
                        .addProperty(STREAM_IDS_ARG, streamIdsSchema("The sample streams: the mapping is checked "
                                                                     + "against them (when no sample text is "
                                                                     + "given, so the text need not be sent "
                                                                     + "again), and with pipeline_uuid stepped for "
                                                                     + "field_mapping."))
                        .addIntegerProperty(MAX_RECORDS_ARG, "Records to step for field_mapping (1 to 1000; "
                                                             + "default 50).")
                        .addBooleanProperty(FIELD_MAPPING_ARG, "Step pipeline_uuid over stream_ids for a preview of "
                                                               + "the Field mapping section (the values the events "
                                                               + "get). Slow: a Stroom step per record. Once at "
                                                               + "most, when the mapping is settled; never while "
                                                               + "fixing it. write_documentation makes the section "
                                                               + "itself.")
                        .addProperty(SAMPLE_ARG, stringArraySchema("The raw sample, or a list of sample files, to "
                                                                   + "check the mapping against before stepping: "
                                                                   + "fields no record has, and time formats the "
                                                                   + "values do not fit. For data_splitter input "
                                                                   + "give splitter too."))
                        .addProperty(SPLITTER_ARG, TranslationArgs.object("The Data Splitter spec "
                                                                          + "(build_data_splitter) that parses the "
                                                                          + "sample, for the check."))
                        .addStringProperty(BUILD_ARG, "Save the XSLT in this build, with the mapping kept with it, "
                                                      + "and return the document instead of the code: the XSLT "
                                                      + "never has to pass through you. With name for a new XSLT.")
                        .addStringProperty(NAME_ARG, "With build: the new XSLT's name, following the environment's "
                                                     + "naming.")
                        .addStringProperty(UUID_ARG, "Replace an XSLT this server created (the one an earlier call "
                                                     + "saved) with the code generated now; build is then not "
                                                     + "needed.")
                        .addBooleanProperty(INCLUDE_XSLT_ARG, "When saving, also return the code (to read it).")
                        .addStringProperty(CONFIRMATION_ID_ARG, "From an earlier needs_confirmation reply (rules "
                                                                + "kept as Unknown).")
                        .addStringProperty(REVIEWED_ARG, "The review_id of a needs_review reply, once its show_user "
                                                         + "(the records to translate as Unknown) is shown to the "
                                                         + "user in the chat: the short form to agree to it then "
                                                         + "opens. Not true: what changes gets a new id.")
                        .addStringProperty(CHANGE_ARG, TranslationArgs.CHANGE)
                        .addProperty(CHANGES_ARG, TranslationArgs.object(
                                "Instead of mapping, to fix one: only what changes, merged into the mapping last "
                                + "sent for this XSLT (the same uuid, or build and name; else the one saved with "
                                + "it; else, for a build and name not saved yet, the draft "
                                + "draft_translation_mapping made this session). events: rules, each replacing the "
                                + "rule of that name whole (or added); common: entries, each replacing the one with "
                                + "that path (and data_name); {name or path, \"remove\": true} removes one; any "
                                + "other key replaces it. Much less to write than the whole mapping again."))
                        .addBooleanProperty(DISCARD_HAND_EDIT_ARG, "Save over an edit made by hand in Stroom's "
                                                                   + "editor since the server saved the XSLT, though "
                                                                   + "the new code undoes it. Only when the user has "
                                                                   + "said to drop their edit: a save that would "
                                                                   + "undo one is refused, saying what it changed, "
                                                                   + "so it can be carried into the mapping "
                                                                   + "(rebuild_mapping) instead.")
                        .addProperty(HAND_EDIT_CHOICES_ARG, TranslationArgs.object(TranslationArgs.HAND_EDIT_CHOICES))
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(build(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final PatternTimeoutException e) {
            // A pattern of the agent's took too long on the sample: saying so is the answer, not a fault.
            return JsonResults.refused(e.getMessage());
        }
    }

    /// @param description What the stream ids are.
    /// @return The schema of a list of stream ids.
    static ArraySchema streamIdsSchema(final String description) {
        return ArraySchema.builder()
                .items(new IntegerSchema(ToolDescription.of("A stream id.")))
                .description(description)
                .build();
    }

    private Map<String, Object> build(final ToolCall call, final ToolCallContext context) {
        final Request request = Request.of(call, this);
        final Build b = new Build(request);
        final Optional<Map<String, Object>> early = readMapping(context, b);
        if (early.isPresent()) {
            return early.get();
        }
        final Optional<Map<String, Object>> question = settleVersion(call, context, b);
        if (question.isPresent()) {
            return question.get();
        }
        generate(context, b);
        if (!TranslationBuilds.problems(b.result).isEmpty()) {
            // Fast: what the schema says is wrong comes back before the sample is read and checked (seen: minutes
            // a call on a large sample, for a mapping that could not have generated anyway).
            b.result.put("warnings", MappingCompaction.grouped(TranslationBuilds.warnings(b.result)));
            b.result.put("sample_check", "not run: fix the problems first");
            b.result.put("hint", TranslationBuilds.fixHint(request.uuid(), request.build(), request.name())
                                 + " The sample is checked once they are fixed.");
            withChanges(b);
            return b.result;
        }
        checkSample(context, b);
        checkAgainstNotes(b);
        jsonProperties(b);
        fieldMapping(context, b);
        final Optional<Map<String, Object>> round = save(call, context, b);
        if (round.isPresent()) {
            return round.get();
        }
        withChanges(b);
        hint(b);
        final Map<String, Object> instructions = agentInstructions.applicableInstructions(context.session(),
                "Standing instructions read by the " + NAME + " tool",
                agentInstructions.targets(List.of(), List.of(), request.feeds(), List.of()));
        if (instructions.get("instructions") instanceof final List<?> applying && !applying.isEmpty()) {
            b.result.put("standing_instructions", applying);
            b.result.put("hint", b.result.get("hint") + " Check the mapping against standing_instructions (the "
                                 + "most specific last).");
        }
        b.result.put("warnings", MappingCompaction.grouped(TranslationBuilds.warnings(b.result)));
        return b.result;
    }

    // The mapping to generate from: given whole, changes merged into the one sent or kept, or the one kept with
    // the XSLT. A list of fields given with a sample comes back as a draft instead.
    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> readMapping(final ToolCallContext context, final Build b) {
        final Request request = b.request;
        Object given = request.mapping();
        if (request.changes() != null) {
            if (given != null) {
                throw new ToolRefusal("Give mapping (the whole one) or changes (only what changes), not both");
            }
            Object changes = request.changes();
            if (changes instanceof final String text) {
                try {
                    changes = PyJson.loads(text);
                } catch (final PyJson.JsonDecodeException e) {
                    throw new ToolRefusal("changes is text that is not JSON (" + e.msg() + " at character " + e.pos()
                                          + "): pass it as an object. " + MappingProblems.ESCAPING);
                }
            }
            final TranslationBuilds.Base base = builds.mappingToChange(context, request.target(), request.uuid(),
                    request.build(), request.name());
            b.basedOn = base.basedOn();
            try {
                final MappingChanges.Applied applied = MappingChanges.applyChanges(base.mapping(), changes);
                given = applied.merged();
                b.applied = applied.done();
            } catch (final MappingChangeException e) {
                throw new ToolRefusal(e.getMessage());
            }
        } else if (given == null) {
            if (request.target().isEmpty()) {
                throw new ToolRefusal("Give mapping (draft_translation_mapping drafts one), changes to fix the one "
                                      + "sent before, or uuid= a saved XSLT alone to regenerate it from the mapping "
                                      + "kept with it");
            }
            // Regenerated from the mapping kept with the XSLT, as the generator writes it now (seen: an agent asked
            // to restyle an XSLT sent its 1,400-line kept mapping back whole, after a first attempt written from
            // memory).
            final TranslationBuilds.Kept kept = builds.keptMapping(context, request.target(), request.uuid());
            given = kept.mapping();
            b.version = kept.schemaVersion();
            b.change = NullSafe.nonBlankStringElse(b.change, "Regenerated from its mapping");
        }
        if (given instanceof final String text) {
            try {
                given = PyJson.loads(text);
            } catch (final PyJson.JsonDecodeException e) {
                final String around = text.substring(Math.max(0, e.pos() - 30), Math.min(text.length(),
                        e.pos() + 30));
                throw new ToolRefusal("mapping is text that is not JSON (" + e.msg() + " at character " + e.pos()
                                      + ", around `" + around + "`): pass the mapping as an object, not a string. "
                                      + MappingProblems.ESCAPING);
            }
        }
        final List<String> unknownKeys = new ArrayList<>();
        if (given instanceof final Map<?, ?> map) {
            SentMappings.remember(context, request.target(), (Map<String, Object>) map);
            final Set<String> keys = new TreeSet<>();
            map.keySet().forEach(key -> keys.add(String.valueOf(key)));
            keys.removeAll(MappingProblems.mappingKeys());
            unknownKeys.addAll(keys);
        }
        b.unknownKeys = unknownKeys;
        try {
            b.mapping = TranslationMapping.parse(given);
            return Optional.empty();
        } catch (final MappingValidationException e) {
            return Optional.of(refusedMapping(given, e, unknownKeys, request));
        }
    }

    // A mapping that doesn't validate: a draft for a list of fields given with a sample, else what is wrong.
    private Map<String, Object> refusedMapping(final Object given,
                                               final MappingValidationException e,
                                               final List<String> unknownKeys,
                                               final Request request) {
        final List<String> problems = MappingProblems.problems(e, unknownKeys);
        final boolean inventory = given instanceof final List<?> list && !list.isEmpty()
                                  && list.stream().allMatch(item -> item instanceof final Map<?, ?> map
                                                                    && map.containsKey("field"));
        // The draft is made only for a list of fields given as the mapping (small models do), not for every
        // mistake: drafting a large sample took long, to answer what a precise error answers at once.
        if (request.samples() != null && inventory) {
            final Map<String, Object> draft = DraftMapping.draft(request.samples(), "", null, null, null, null, null);
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "needs_mapping");
            result.put("ok", false);
            final List<String> all = new ArrayList<>(List.of("mapping is a list of fields, not a translation "
                                                             + "mapping"));
            all.addAll(problems);
            result.put("problems", all);
            result.put("draft_mapping", draft.get("mapping"));
            result.put("splitter", draft.get("splitter"));
            result.put("notes", draft.get("notes"));
            result.put("hint", "Edit draft_mapping (the notes say what to decide) and call build_translation_xslt "
                               + "again with it as mapping, the same sample, and the splitter if there is one.");
            return result;
        }
        // The whole shape, where the mapping's shape is what's wrong (a key missing or misplaced), not a value.
        throw new ToolRefusal("mapping is not a translation mapping: " + String.join("; ", problems) + ". Fix these "
                              + "in the mapping you have; to start again, draft_translation_mapping "
                              + "(stream_ids=the sample streams) gives one." + (MappingProblems.structural(e)
                ? " A whole mapping looks like: " + PyJson.dumps(MappingProblems.MAPPING_EXAMPLE)
                : ""));
    }

    // The event-logging version: a saved XSLT's own, else the one given, chosen or held alone, else the question.
    private Optional<Map<String, Object>> settleVersion(final ToolCall call,
                                                        final ToolCallContext context,
                                                        final Build b) {
        final Request request = b.request;
        if (b.version == null && request.uuid() != null && request.schemaVersion() == null) {
            // A saved XSLT keeps the version it writes; one that names none (made empty) is asked about like a new
            // one.
            final XsltDoc saved = translationDocs.readXslt(request.uuid());
            b.version = KeptMapping.readMapping(saved.getDescription())
                    .filter(kept -> KeptMapping.TRANSLATION.equals(kept.kind()))
                    .map(kept -> KeptCode.keptVersion(kept.payload()))
                    .orElseGet(() -> HeldSchemas.eventLoggingVersion(saved.getData()));
        }
        if (request.schemaVersion() != null || b.version == null) {
            final EventVersions.Target target = eventVersions.target(call, context, request.schemaVersion(),
                    request.build());
            if (target.version() == null) {
                return Optional.of(target.question());
            }
            b.version = target.version();
        }
        return Optional.empty();
    }

    // The XSLT generated, with the imports standing instructions require.
    private void generate(final ToolCallContext context, final Build b) {
        final Request request = b.request;
        b.schema = eventVersions.eventSchema(b.version);
        final List<RequiredImport> required = requiredImports.requiredFor(context.session(),
                InstructionRules.TRANSLATION, request.build(), request.feeds());
        final Set<String> have = new LinkedHashSet<>(b.mapping.imports());
        b.mapping.shared().stream().map(SharedTemplate::href).forEach(have::add);
        b.mapping.functions().stream().map(SharedFunctions::href).forEach(have::add);
        final List<String> missing = required.stream()
                .map(RequiredImport::href)
                .filter(href -> !have.contains(href))
                .toList();
        if (!missing.isEmpty()) {
            final List<String> imports = new ArrayList<>(b.mapping.imports());
            imports.addAll(missing);
            b.mapping = TranslationBuilds.withImports(b.mapping, imports);
        }
        b.result = builds.generated(b.mapping, b.schema, b.version);
        b.result.put("schema_version", b.version);
        if (request.schemaVersion() == null) {
            eventVersions.settled(context, request.build(), b.version)
                    .ifPresent(settled -> b.result.put("schema_version_settled", settled));
        }
        if (!required.isEmpty()) {
            b.result.put("required_imports", requiredImports.importNotes(required, true));
        }
        if (!b.unknownKeys.isEmpty()) {
            TranslationBuilds.warnings(b.result).add("mapping keys ignored, as a mapping has no such key: "
                                                     + SampleArguments.pythonList(b.unknownKeys) + " (it has "
                                                     + SampleArguments.pythonList(MappingProblems.mappingKeys())
                                                     + ")");
        }
    }

    // The mapping checked against the sample: its text, or its streams'.
    private void checkSample(final ToolCallContext context, final Build b) {
        final Request request = b.request;
        final List<String> warnings = TranslationBuilds.warnings(b.result);
        List<String> samples = request.samples();
        if (samples == null && !request.streamIds().isEmpty()) {
            final SampleStreamTexts.Texts texts = sampleStreamTexts.read(request.streamIds());
            samples = new ArrayList<>(texts.texts().values());
            texts.notes().forEach(note -> warnings.add("sample: " + note));
        } else if (samples != null) {
            for (final String text : samples) {
                SampleArguments.whyNotData(text, SAMPLE_ARG).ifPresent(why -> {
                    throw new ToolRefusal(why);
                });
            }
        }
        if (!request.streamIds().isEmpty()) {
            warnings.addAll(builds.metaNotSent(b.mapping, request.streamIds()));
        }
        warnings.addAll(TranslationBuilds.zoneNotKept(b.mapping));
        SplitterSpec splitter = request.splitter();
        boolean inferred = false;
        if (samples != null && !samples.isEmpty() && "data_splitter".equals(b.mapping.input()) && splitter == null) {
            // Without the spec no record could be read, so nothing was checked: the rule for the rest went to the
            // user as "no sample records checked", logoffs and all. The spec is inferred, as build_data_splitter
            // does.
            splitter = inferredSpec(samples.getFirst());
            inferred = splitter != null;
            if (inferred) {
                warnings.add("sample: read with the Data Splitter spec inferred from it (as build_data_splitter "
                             + "does); give splitter if the build's converter differs");
            }
        }
        if (samples == null) {
            return;
        }
        final Map<String, Object> check = MappingSampleCheck.check(b.mapping, samples, splitter, b.schema, inferred,
                clock);
        b.result.put("sample_check", check);
        strings(check.get("warnings")).forEach(warning -> warnings.add("sample: " + warning));
        final List<String> found = strings(check.get("problems"));
        if (!found.isEmpty()) {
            // A time format no sample value fits fails every record at stepping: as good as a schema problem.
            found.forEach(problem -> TranslationBuilds.problems(b.result).add("sample: " + problem));
            b.result.put("ok", false);
            b.result.put("xslt", null);
        }
    }

    // The user's documentation, where the build keeps notes from it: a rule that contradicts the catalogue.
    private void checkAgainstNotes(final Build b) {
        if (b.request.build() == null) {
            return;
        }
        final Map<String, Object> catalogue = builds.notesInBuild(b.request.build());
        if (!(catalogue.get("events") instanceof final List<?> events) || events.isEmpty()) {
            return;
        }
        final List<String> contradictions = SourceNotes.checkMapping(b.mapping.toJson(DumpMode.WITHOUT_NONE),
                catalogue);
        b.result.put("source_notes_check", contradictions.isEmpty()
                ? "the mapping agrees with the event catalogue"
                : contradictions);
        contradictions.forEach(c -> TranslationBuilds.warnings(b.result).add("source documentation: " + c));
    }

    // As create_pipeline takes them (set_properties): seen, a dict passed on as is, to a parameter of another name.
    private static void jsonProperties(final Build b) {
        if (!"json".equals(b.mapping.input())) {
            return;
        }
        final boolean lines = "lines".equals(b.mapping.jsonLayout());
        final Map<String, Object> property = new LinkedHashMap<>();
        property.put("element", "jsonParser");
        property.put("name", "addRootObject");
        property.put("value", lines);
        b.result.put("set_properties", List.of(property));
        b.result.put("set_properties_note", "For create_pipeline's set_properties (the element id as the template "
                                            + "names its JSON parser). " + (lines
                ? "JSON lines need the parser's root map (addRootObject true, the default), which the XSLT matches "
                  + "as /map/map."
                : "A JSON array is matched with or without the parser's root map; set addRootObject false on "
                  + "create_pipeline to keep the output simple, as sibling pipelines do.") + " No text converter.");
    }

    // The Field mapping section previewed by stepping the pipeline over the sample, once asked for.
    private void fieldMapping(final ToolCallContext context, final Build b) {
        if (!ok(b)) {
            return;
        }
        final Request request = b.request;
        if (request.fieldMapping() && request.pipelineUuid() != null && !request.streamIds().isEmpty()) {
            stepping.checkCanStep();
            final SteppedPipeline pipeline = stepping.load(request.pipelineUuid());
            final String element = pipeline.defaultOutputs().getFirst();
            // The documentation run steps a copy that marks each Event with its rule; the returned XSLT has no
            // marks.
            final String marked = XsltGenerator.generate(b.mapping, b.schema, b.version, true).xslt();
            final PipelineStepping.Outputs outputs = PipelineStepping.withinTimeLimit(context, () ->
                    stepping.outputs(context, pipeline, request.streamIds(), element, Map.of(element, marked),
                            request.maxRecords()));
            stepping.audit(context, request.streamIds(), "Stepped stream(s) through " + pipeline.name() + " with the "
                                                         + NAME + " tool");
            final List<XmlElement> events = EventFieldDocs.sampledEvents(new ArrayList<>(
                    outputs.outputs().values()));
            b.result.put("field_mapping", EventFieldDocs.fieldMappingMarkdown(b.mapping, b.schema, events,
                    FieldDocs.BY_EVENT_TYPE, null));
            final Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("records", outputs.outputs().size());
            sample.put("events", events.size());
            sample.put("element", element);
            if (events.isEmpty()) {
                sample.put("warning", "The sample produced no events, so the event types table has no values: "
                                      + "check stream_ids are the pipeline's input.");
            }
            b.result.put("field_mapping_sample", sample);
        } else {
            // A table from the mapping alone would show how values are computed, not what they are; it was being
            // copied into documentation as it was. Only a sampled run gives one.
            b.result.put("field_mapping", null);
            b.result.put("field_mapping_needs", "Not needed while fixing the mapping: write_documentation makes the "
                                                + "section. For a preview once the mapping is settled: "
                                                + "field_mapping=true with pipeline_uuid and stream_ids (it steps "
                                                + "the sample: slow).");
        }
    }

    // Saved with build and name (or uuid): rules kept as Unknown agreed with the user first.
    private Optional<Map<String, Object>> save(final ToolCall call, final ToolCallContext context, final Build b) {
        final Request request = b.request;
        if (!ok(b) || (request.build() == null && request.uuid() == null)) {
            return Optional.empty();
        }
        final Map<String, String> agreedNow = new LinkedHashMap<>();
        final Optional<Map<String, Object>> asked = agreeUnknown(call, context, b, agreedNow);
        if (asked.isPresent()) {
            return asked;
        }
        final String said = NullSafe.nonBlankStringElse(b.change, b.applied != null && !b.applied.isEmpty()
                ? "Mapping changes: " + String.join("; ", b.applied)
                : request.uuid() != null
                        ? "Saved again from its mapping"
                        : "Created from its mapping");
        final String xslt = String.valueOf(b.result.get("xslt"));
        Map<String, Object> saved;
        if (request.uuid() != null) {
            saved = translationDocs.updateXslt(call, context, request.uuid(), xslt, null,
                    TranslationDocs.Source.of(b.mapping), said, request.discardHandEdit(),
                    request.handEditChoices());
            if (!(saved.get("uuid") instanceof String)) {
                // The user is asked about fields edited by hand: nothing saved yet.
                return Optional.of(saved);
            }
        } else if (request.name() != null) {
            saved = translationDocs.createXslt(context, request.build(), request.name(), xslt,
                    TranslationDocs.Source.of(b.mapping), said);
        } else {
            throw new ToolRefusal("To save the XSLT give name (a new XSLT in the build) or uuid (the one saved "
                                  + "before)");
        }
        final String uuid = String.valueOf(saved.get("uuid"));
        if (!agreedNow.isEmpty()) {
            saved = new LinkedHashMap<>(saved);
            saved.put("version", builds.rememberUnknownAgreed(uuid, agreedNow));
        }
        final Map<String, Object> summary = new LinkedHashMap<>();
        for (final String key : List.of("type", "uuid", "name", "version")) {
            summary.put(key, saved.get(key));
        }
        b.result.put("saved", summary);
        SentMappings.remember(context, uuid, b.mapping.toJson(DumpMode.COMPACT));
        for (final String key : List.of("next", "done")) {
            if (saved.containsKey(key)) {
                b.result.put(key, saved.get(key));
            }
        }
        if (!request.includeXslt()) {
            b.result.remove("xslt");
        }
        b.saved = saved;
        return Optional.empty();
    }

    // Unknown says what happened is not known: the user agrees to that per rule, seeing what its records hold (a
    // form when the client has them, so the model cannot agree for them). Once per rule: what the user agreed to
    // for this XSLT is kept with it, and only a rule not agreed yet, or whose conditions or reason changed since,
    // is put to them (seen in production: asked again on every save).
    private Optional<Map<String, Object>> agreeUnknown(final ToolCall call,
                                                       final ToolCallContext context,
                                                       final Build b,
                                                       final Map<String, String> agreedNow) {
        final Request request = b.request;
        List<EventRule> kept = MappingFacts.keptUnknown(b.mapping);
        final Map<String, String> agreedBefore = kept.isEmpty()
                ? Map.of()
                : builds.unknownAgreed(request.uuid(), request.build(), request.name());
        kept.forEach(rule -> {
            final String key = TranslationBuilds.unknownKey(rule);
            if (key.equals(agreedBefore.get(rule.name()))) {
                agreedNow.put(rule.name(), key);
            }
        });
        kept = kept.stream().filter(rule -> !agreedNow.containsKey(rule.name())).toList();
        if (!agreedNow.isEmpty()) {
            // Not asked again, but on the record: the XSLT keeps these as Unknown on the user's earlier answer.
            auditLogProvider.get().processed(context.session(), null, "confirmation", NAME + ": rules kept as Unknown "
                                                                                      + "on the user's earlier "
                                                                                      + "answer", "granted (earlier): "
                                                                                                  + agreedNow.keySet(),
                    true);
        }
        if (kept.isEmpty()) {
            return Optional.empty();
        }
        final Map<String, Object> check = b.result.get("sample_check") instanceof final Map<?, ?> map
                ? mapOf(map)
                : Map.of();
        if (!BuildPlan.truthy(check.get("records"))) {
            // The user is asked what they agree to: which records, with which values. Without the sample the form
            // said "no sample records checked", and logoffs went to Unknown unseen.
            throw new ToolRefusal("Keeping a rule as Unknown is put to the user with the sample records it catches: "
                                  + "call again with stream_ids (the sample streams) or sample, so the check can show "
                                  + "them" + (BuildPlan.truthy(check.get("note"))
                    ? " (" + check.get("note") + ")"
                    : ""));
        }
        final Map<String, Map<String, Object>> sampled = new LinkedHashMap<>();
        if (check.get("kept_unknown") instanceof final List<?> list) {
            list.stream().filter(Map.class::isInstance).map(item -> mapOf((Map<?, ?>) item))
                    .forEach(item -> sampled.put(String.valueOf(item.get("rule")), item));
        }
        // A rule keeping none of the sample Unknown (a catch-all for records the sample lacks) holds nothing to
        // agree to: seen, the user asked to keep 'other' as Unknown for "none of the 1000" records.
        kept = kept.stream().filter(rule -> sampled.containsKey(rule.name())).toList();
        if (kept.isEmpty()) {
            return Optional.empty();
        }
        // Shown in the chat first, the form kept short (asked for by the user: "Keep rest as Unknown (what happened
        // is not known) in the saved XSLT: rest: ..." was cryptic, and the detail too much tiny text in a form).
        final String shown = TranslationBuilds.unknownMarkdown(kept, sampled, check.get("records"));
        if (!Consent.wasReviewed(request.reviewed(), shown)) {
            // Passed on only with this id, as promote_build's layout (seen there: reviewed=true from the start).
            final Map<String, Object> review = new LinkedHashMap<>();
            review.put("status", "needs_review");
            review.put("show_user", shown);
            review.put("review_id", Consent.reviewId(shown));
            review.put("hint", "Show the user show_user in the chat exactly as it is (markdown), then, in the same "
                               + "turn, without waiting for a reply, call build_translation_xslt again with the same "
                               + "arguments plus reviewed='" + Consent.reviewId(shown) + "' (its review_id): a short "
                               + "form asks them to agree, or they say what to change (a rule's action instead of "
                               + "Unknown)." + Consent.reviewNote(request.reviewed()));
            return Optional.of(review);
        }
        final Map<String, Object> details = new LinkedHashMap<>();
        kept.forEach(rule -> details.put("rule '" + rule.name() + "'", sampled.get(rule.name()).get("records")
                                                                       + " of " + check.get("records")
                                                                       + " sample records"));
        details.put("as shown in the chat", Consent.reviewId(shown));
        final Optional<Map<String, Object>> gate = consent.require(call, context, Consent.Request.confirmation(NAME,
                "Translate the records shown in the chat as Unknown events", details, request.confirmationId()));
        if (gate.isPresent()) {
            return gate;
        }
        kept.forEach(rule -> agreedNow.put(rule.name(), TranslationBuilds.unknownKey(rule)));
        return Optional.empty();
    }

    private static void withChanges(final Build b) {
        if (b.applied != null && !b.applied.isEmpty()) {
            b.result.put("changes_applied", b.applied);
        }
        if (b.basedOn != null) {
            b.result.put("changes_applied_to", b.basedOn);
        }
    }

    private static void hint(final Build b) {
        final Request request = b.request;
        if (!ok(b)) {
            b.result.put("hint", TranslationBuilds.fixHint(request.uuid(), request.build(), request.name()));
        } else if (b.saved != null) {
            if (request.uuid() != null) {
                // A change to an events pipeline reaches the pipelines that read its Events (asked for by the
                // user).
                b.result.put("follow_on", "This changes an events pipeline: once it has written Events with the "
                                          + "change (step, or reprocess_streams in a build), review_coverage "
                                          + "pipeline_uuid=<its pipeline> events_stream_ids=<those Events> reviews "
                                          + "the pipelines that read its Events (indexing, CEF output): what each "
                                          + "lacks, their documentation, and for Elasticsearch what the cluster's "
                                          + "admin must do before streams are indexed again.");
            }
            b.result.put("hint", "Saved with its mapping as '" + b.saved.get("name") + "' (" + b.saved.get("uuid")
                                 + "). If no pipeline uses it yet, create_pipeline (it takes the build's XSLT); then "
                                 + "step_sample over every sample stream. To change it, call again with uuid='"
                                 + b.saved.get("uuid") + "' and changes= only the rules or entries that change.");
        } else {
            b.result.put("hint", "Save it by calling again with build and name (or uuid to replace the one saved "
                                 + "before): it is kept with this mapping, so write_documentation can regenerate the "
                                 + "Field mapping section from it and later changes start from the mapping, and the "
                                 + "XSLT need not pass through you. Then step_sample. To try code without saving it, "
                                 + "step_sample takes draft_code.");
        }
    }

    private static SplitterSpec inferredSpec(final String sample) {
        try {
            return SplitterDryRun.inferSpec(sample).spec();
        } catch (final SampleRefusal | IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean ok(final Build b) {
        return Boolean.TRUE.equals(b.result.get("ok"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(final Object value) {
        return value instanceof final List<?> list
                ? (List<String>) list
                : List.of();
    }

    private static Map<String, Object> mapOf(final Map<?, ?> map) {
        final Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((key, value) -> out.put(String.valueOf(key), value));
        return out;
    }


    // --------------------------------------------------------------------------------


    /// A call's arguments, read.
    ///
    /// @param mapping         The mapping as given (a map, a list or text), or null.
    /// @param changes         The changes as given, or null.
    /// @param schemaVersion   The event-logging version given, or null.
    /// @param feeds           The feeds the translation is for.
    /// @param pipelineUuid    The pipeline to step for field_mapping, or null.
    /// @param streamIds       The sample streams.
    /// @param maxRecords      The records to step for field_mapping.
    /// @param fieldMapping    True to preview the Field mapping section.
    /// @param samples         The sample's texts given, or null.
    /// @param splitter        The Data Splitter spec given, or null.
    /// @param build           The build to save in, or null.
    /// @param name            The new XSLT's name, or null.
    /// @param uuid            The XSLT to replace, or null.
    /// @param includeXslt     True to return the code when saving.
    /// @param confirmationId  From a needs_confirmation reply, or null.
    /// @param reviewed        What was given as reviewed, or null.
    /// @param change          What this change is, or null.
    /// @param discardHandEdit True when the user said to drop their hand edit.
    /// @param handEditChoices The user's answers about fields edited by hand, or null.
    record Request(Object mapping,
                   Object changes,
                   String schemaVersion,
                   List<String> feeds,
                   String pipelineUuid,
                   List<Long> streamIds,
                   int maxRecords,
                   boolean fieldMapping,
                   List<String> samples,
                   SplitterSpec splitter,
                   String build,
                   String name,
                   String uuid,
                   boolean includeXslt,
                   String confirmationId,
                   Object reviewed,
                   String change,
                   boolean discardHandEdit,
                   Map<String, String> handEditChoices) {

        static Request of(final ToolCall call, final BuildTranslationXsltTool tool) {
            final Integer max = tool.intArg(call, MAX_RECORDS_ARG);
            if (max != null && (max < 1 || max > MAX_RECORDS)) {
                throw new ToolRefusal("max_records is 1 to " + MAX_RECORDS + ", not " + max);
            }
            return new Request(TranslationArgs.value(call, MAPPING_ARG),
                    TranslationArgs.value(call, CHANGES_ARG),
                    tool.stringArg(call, SCHEMA_VERSION_ARG),
                    stringListArg(call, FEEDS_ARG),
                    tool.stringArg(call, PIPELINE_UUID_ARG),
                    StartOnboardingTool.streamIds(call, STREAM_IDS_ARG),
                    max == null
                            ? DEFAULT_RECORDS
                            : max,
                    CreateProcessorFilterTool.flag(call, FIELD_MAPPING_ARG),
                    samples(TranslationArgs.value(call, SAMPLE_ARG)),
                    splitter(TranslationArgs.value(call, SPLITTER_ARG)),
                    tool.stringArg(call, BUILD_ARG),
                    tool.stringArg(call, NAME_ARG),
                    tool.stringArg(call, UUID_ARG),
                    CreateProcessorFilterTool.flag(call, INCLUDE_XSLT_ARG),
                    tool.stringArg(call, CONFIRMATION_ID_ARG),
                    TranslationArgs.value(call, REVIEWED_ARG),
                    tool.stringArg(call, CHANGE_ARG),
                    CreateProcessorFilterTool.flag(call, DISCARD_HAND_EDIT_ARG),
                    TranslationArgs.handEditChoices(call));
        }

        /// @return The XSLT the mapping is sent for: its uuid, `build/name`, or "".
        String target() {
            if (uuid != null) {
                return uuid;
            }
            return build != null && name != null
                    ? build + "/" + name
                    : "";
        }

        // The sample as given: one text, or a list of texts.
        private static List<String> samples(final Object given) {
            if (given == null) {
                return null;
            }
            if (given instanceof final List<?> list) {
                return list.stream().map(item -> item == null
                        ? ""
                        : String.valueOf(item)).toList();
            }
            return List.of(String.valueOf(given));
        }

        // As build_data_splitter gave it (its spec).
        @SuppressWarnings("unchecked")
        static SplitterSpec splitter(final Object given) {
            if (given == null) {
                return null;
            }
            try {
                final Object value = given instanceof final String text
                        ? PyJson.loads(text)
                        : given;
                if (value instanceof final Map<?, ?> map) {
                    return SplitterSpec.fromMap((Map<String, ?>) map);
                }
            } catch (final RuntimeException e) {
                throw new ToolRefusal("splitter is not a Data Splitter spec (build_data_splitter's spec): "
                                      + e.getMessage());
            }
            throw new ToolRefusal("splitter is not a Data Splitter spec (build_data_splitter's spec): give it as an "
                                  + "object");
        }
    }

    // What a call works out as it goes.
    private static final class Build {

        private final Request request;
        private TranslationMapping mapping;
        private List<String> unknownKeys = List.of();
        private List<String> applied;
        private String basedOn;
        private String version;
        private String change;
        private EventSchema schema;
        private Map<String, Object> result;
        private Map<String, Object> saved;

        private Build(final Request request) {
            this.request = request;
            this.change = request.change();
        }
    }
}

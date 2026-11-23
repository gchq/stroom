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
import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.CallProgress;
import stroom.ai.impl.tools.InstructionRules;
import stroom.ai.impl.tools.InstructionRules.RequiredImport;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.LargeResults;
import stroom.ai.impl.tools.ToolArgs;
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
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.draft.DraftMapping;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.draft.SnapshotChanges;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.fielddoc.EventFieldDocs;
import stroom.ai.impl.tools.fielddoc.FieldDocs;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.EventRule;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.MappingChanges;
import stroom.ai.impl.tools.generation.MappingCompaction;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.generation.SeenSourceTypes;
import stroom.ai.impl.tools.generation.SharedFunctions;
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.generation.SourceTypeModule;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.Placeholders;
import stroom.ai.impl.tools.sample.SampleTexts;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.ai.impl.workspace.PatternTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// `build_translation_xslt` - writes the event-logging translation XSLT from a field mapping instead of by hand, and
/// with build and name (or uuid) saves it with its mapping. The mapping is checked against the schema, compiled as
/// Stroom compiles it, and checked against the sample; rules kept as Unknown are agreed with the user, as is splitting
/// the domain off a user's Id whose values carry one ([DomainSplits]); an edit made by hand in Stroom since the tools
/// saved the XSLT is kept ([HandEditGate]).
class BuildTranslationXsltTool extends AbstractBuiltInTool {

    /// How the warning saying which old/new lists were written per property in the snapshots starts.
    static final String SNAPSHOT_CHANGES = "Old/new lists written as a Data per property in each Update snapshot "
                                           + "(as the change form writes them, whatever the mapping said; the mapping "
                                           + "kept says so): ";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BuildTranslationXsltTool.class);
    // The XSLT's switch for logging fields no rule reads (XsltGenerator.LOG_UNMAPPED), as an element's start tag; a
    // source type group's prefixed with its mode.
    private static final Pattern LOG_UNMAPPED_VARIABLE = Pattern.compile(
            "<xsl:variable\\b[^>]*\\bname=[\"'](?:[\\w.-]+_)?" + XsltGenerator.LOG_UNMAPPED + "[\"'][^>]*>");
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
    static final String REVIEWED_ARG = "reviewed";
    static final String CHANGE_ARG = "change";
    static final String CHANGES_ARG = "changes";
    static final String DISCARD_HAND_EDIT_ARG = "discard_hand_edit";
    static final String HAND_EDIT_CHOICES_ARG = "hand_edit_choices";
    static final String GROUP_ARG = "group";
    static final String SOURCE_TYPES_ARG = "source_types";
    static final String OWN_OUTPUT_FEED_ARG = "own_output_feed";
    // The block of UnsavedChoices that keeps where a new group's Events go, by group.
    private static final String PLACEMENT_CHOSEN = "placement chosen";
    static final String REFERENCES_ARG = "references";
    static final String DOMAIN_SPLIT_ARG = "domain_split";

    // The answers suggested when the mapping translates event types the user confirmed otherwise.
    private static final String KEEP_CHANGE = "Keep the mapping's change";
    private static final String FOLLOW_CONFIRMED = "Follow what I confirmed";
    // The block the user's answer to that form is kept in for the session, by the form's id (its review_id).
    private static final String CONFIRMED_ANSWERED = "confirmed event types answered";
    // The most characters of the user's words quoted back to them in a form.
    private static final int MOST_WORDS_QUOTED = 300;
    private static final int DEFAULT_RECORDS = 50;
    private static final int MAX_RECORDS = 1000;
    // The most of the build's own sample streams a save given neither sample nor stream_ids is checked against.
    private static final int MOST_BUILD_SAMPLE_STREAMS = 5;

    private final TranslationDocs translationDocs;
    private final TranslationBuilds builds;
    private final EventVersions eventVersions;
    private final RequiredImports requiredImports;
    private final Consent consent;
    private final SampleStreamTexts sampleStreamTexts;
    private final PipelineStepping stepping;
    private final AgentInstructions agentInstructions;
    private final Provider<AiAuditLog> auditLogProvider;
    private final CurrentTranslations currentTranslations;
    private final SourceTypeGroups groups;
    private final LookupKeyCounts lookupKeyCounts;
    private final BuildPlan plan;
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
                             final Provider<AiAuditLog> auditLogProvider,
                             final CurrentTranslations currentTranslations,
                             final SourceTypeGroups groups,
                             final LookupKeyCounts lookupKeyCounts,
                             final BuildPlan plan) {
        this(translationDocs, builds, eventVersions, requiredImports, consent, sampleStreamTexts, stepping,
                agentInstructions, auditLogProvider, currentTranslations, groups, lookupKeyCounts, plan,
                Clock.systemUTC());
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
                             final CurrentTranslations currentTranslations,
                             final SourceTypeGroups groups,
                             final LookupKeyCounts lookupKeyCounts,
                             final BuildPlan plan,
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
        this.currentTranslations = currentTranslations;
        this.groups = groups;
        this.lookupKeyCounts = lookupKeyCounts;
        this.plan = plan;
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
    public boolean changesOnlyItsBuild() {
        // Through WriteGuard, only what is in the build's folder.
        return true;
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
                             + "System, Device...), and one rule per way of recognising each event type, with its "
                             + "conditions and fields. "
                             + "Text fields that hold several values (a message string with a time, user and "
                             + "action) are parsed by the mapping's extract list, not a function: entries {field, "
                             + "regex, names}, whose regex groups become fields named by names, used then like any "
                             + "input field (by field=, never $name in an xpath); no substring-before/after chains. "
                             + "TypeId names the event type: in the format the standing instructions set for "
                             + "TypeIds, where they set one; else join (fields' values joined, e.g. join: [event, "
                             + "action] for User-Login) the fields that tell the event types apart. "
                             + "An input with several values (a JSON array, a tokenize()) writes one element per "
                             + "value with repeat: true; Data entries are written in a named Data with data_parent "
                             + "(data_parent 'links', data_name 'link', repeat: <Data Name=\"links\"><Data "
                             + "Name=\"link\" Value=\"...\"/>...</Data>). An element written once per item with "
                             + "fields of its own (each recipient a User with Id and Domain) is a group: {path: "
                             + "EventDetail/Send/Destination/User, for_each: {split: recipients, on: ','}, as: "
                             + "recipient, fields: [{path: Id, item: true, transform: strip_domain}, {path: Domain, "
                             + "item: true, transform: domain}]}; for_each also takes {array: <field>} and {zip: "
                             + "{name: field, ...}} (stroom://guide/mapping). "
                             + "JSON held in a string is read with an xpath using json-to-xml(). An xpath reads "
                             + "from the record (.//x, not //x: processing reads a stream's records as one "
                             + "document). Paths are checked against the schema: unknown paths come back with "
                             + "suggestions, constants are checked against allowed values, and elements are "
                             + "written in schema order with empty inputs left out; the XSLT is compiled, and one "
                             + "that doesn't compile is not saved. Fix any problems and call again with changes= "
                             + "only the rules or entries that change, not the whole mapping. With build and name "
                             + "(or uuid, to replace the XSLT saved before) the XSLT is saved with its mapping and "
                             + "the document comes back instead of the code, so step_sample steps it as saved; "
                             + "without them nothing is saved and the code comes back. Before saving, a user's Id "
                             + "whose sample values carry a domain (DOMAIN\\user, user@domain) is put to the user: "
                             + "split (the account name in the Id, the domain in the Domain beside it) or kept as "
                             + "is; changes_applied says what a split changed. The standing instructions "
                             + "(AGENTS docs) that apply come back with the result: check the mapping follows "
                             + "them, and set mapping.style from any XSLT style section in them (naming, "
                             + "variables, xsl:maps). With pipeline_uuid and stream_ids it also returns "
                             + "field_mapping, the Field mapping section of the pipeline's documentation, to read "
                             + "before writing it; the saved doc's section is generated by write_documentation "
                             + "itself from the mapping kept with the XSLT, stepped over the sample streams. "
                             + "An aggregated feed (a source type registry, record_source_types): a build makes "
                             + "one source type group, the one its scope names (group and source_types default to "
                             + "it; another group is refused, the next group being a later build's): "
                             + "the XSLT of a pipeline of its own, translating its source types' records (the "
                             + "registry's rows tried in order) in a mode of its own and passing over the rest, "
                             + "counted; it imports the feed's common module ('<feed> common': EventTime, System, "
                             + "Generator, Device from the wrapper's fields and the site's shared calls, made from "
                             + "this mapping's common entries for the first group, its parts called by every group; "
                             + "a rule mapping one of those elements itself writes its own), which imports the "
                             + "helpers ('<feed> helpers'). With build, the registry's rows are set translated. Each "
                             + "group has a pipeline of its own ('<feed> <group>', made by create_pipeline group=); "
                             + "for a new group a form asks where its Events go (the feed's Events, or an output "
                             + "feed of its own); a change to a group whose pipeline is promoted is saved in a "
                             + "new version of its XSLT, nothing asked (copy_pipeline then makes the pipeline's "
                             + "new version); group.group_next says what follows. A later build changing the "
                             + "common module or the helpers works on working copies of them, imported by the "
                             + "build's own modules. The stepping tools "
                             + "step an unsaved group as draft code for its pipeline's XSLT element, as any "
                             + "translation.")
                .parameters(ObjectSchema.builder()
                        .addProperty(MAPPING_ARG, TranslationArgs.object(
                                "The translation mapping: {input, common: [{path, field|value|...}], events: [{name, "
                                + "when, fields}]}. Start from draft_translation_mapping and edit it. A field "
                                + "inventory is not a mapping. To fix one already sent, give changes instead. "
                                + "Leave both out, with uuid=, to regenerate a saved XSLT from the mapping kept "
                                + "with it (as the generator now writes it: its current style defaults); never "
                                + "send a kept mapping back whole."))
                        .addStringProperty(SCHEMA_VERSION_ARG, "Event-logging version the translation writes: "
                                                               + "the one the user chose (as "
                                                               + "draft_translation_mapping returned it). Left out: "
                                                               + "a saved XSLT's own, else the user is asked which "
                                                               + "of the versions this Stroom holds (the newest "
                                                               + "recommended). Where it holds the version in more "
                                                               + "than one XML schema document, the UUID of the one "
                                                               + "the user chose."
                                                               + " Where it also holds the environment's fork of the "
                                                               + "event-logging schema, the schema is settled first, "
                                                               + "by namespace (event-logging:3 or the fork's), as "
                                                               + "a fork's versions are its own: give the namespace "
                                                               + "chosen (or a fork document's UUID); a version "
                                                               + "alone is read within it.")
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
                                                                   + "the build's Data Splitter reads it, or give "
                                                                   + "splitter."))
                        .addProperty(SPLITTER_ARG, TranslationArgs.object("The Data Splitter spec "
                                                                          + "(build_data_splitter) that parses the "
                                                                          + "sample, for the check. Leave it out "
                                                                          + "when the build has its Data Splitter: "
                                                                          + "the check reads the sample with it, as "
                                                                          + "processing does."))
                        .addStringProperty(BUILD_ARG, "Save the XSLT in this build, with the mapping kept with it, "
                                                      + "and return the document instead of the code: the XSLT "
                                                      + "never has to pass through you. With name for a new XSLT.")
                        .addStringProperty(NAME_ARG, "With build: the new XSLT's name, following the environment's "
                                                     + "naming. A source type group's new XSLT is named for the "
                                                     + "group, as its pipeline is ('<feed> <group>', with ' V1.0' "
                                                     + "where neither has a version), whatever is given here.")
                        .addStringProperty(UUID_ARG, "Replace an XSLT the agent created (the one an earlier call "
                                                     + "saved) with the code generated now; build is then not "
                                                     + "needed.")
                        .addBooleanProperty(INCLUDE_XSLT_ARG, "When saving, also return the code (to read it).")
                        .addStringProperty(REVIEWED_ARG, "The review_id of a needs_review reply, once its show_user "
                                                         + "(the records to translate as Unknown) is shown to the "
                                                         + "user in the chat: the short form to agree to it then "
                                                         + "opens. Not true: what changes gets a new id.")
                        .addStringProperty(CHANGE_ARG, TranslationArgs.CHANGE)
                        .addProperty(CHANGES_ARG, TranslationArgs.object(
                                "Instead of mapping, to fix one: only what changes, merged into the mapping last "
                                + "sent for this XSLT (the same uuid, or build and name; else the one saved with "
                                + "it; else, for a build and name not saved yet, the build's latest draft from "
                                + "draft_translation_mapping, in any session of yours). events: rules by name, a "
                                + "new one added whole; for a rule already there, each key given replaces its own, "
                                + "and its fields change entry by entry: each entry replaces the rule's entry with "
                                + "that path (and data_name), or is added; \"replace\": true replaces the rule "
                                + "whole. common: "
                                + "entries, each replacing the one with that path (and data_name). {name or path, "
                                + "\"remove\": true} removes a rule or an entry; any other key replaces it. Send "
                                + "only the entries that change, never the whole rule again."))
                        .addBooleanProperty(DISCARD_HAND_EDIT_ARG, "Save over an edit made by hand in Stroom's "
                                                                   + "editor since the server saved the XSLT, though "
                                                                   + "the new code undoes it. Only when the user has "
                                                                   + "said to drop their edit: a save that would "
                                                                   + "undo one is refused, saying what it changed, "
                                                                   + "so it can be carried into the mapping "
                                                                   + "(rebuild_mapping) instead.")
                        .addProperty(HAND_EDIT_CHOICES_ARG, TranslationArgs.object(TranslationArgs.HAND_EDIT_CHOICES))
                        .addStringProperty(GROUP_ARG, "An aggregated feed's source type group to build, by name "
                                                      + "(e.g. 'PEGA security'): the XSLT is the group's, the "
                                                      + "XSLT of its own pipeline. Needs the build's "
                                                      + "source type registry. Default the group the build's "
                                                      + "scope names: a build makes one group, and another name "
                                                      + "is refused (the next group is a later build's). A saved "
                                                      + "group's uuid alone regenerates it as a group.")
                        .addProperty(SOURCE_TYPES_ARG, stringArraySchema("For a group: the registry's source types "
                                                                         + "it translates; default the build's "
                                                                         + "scope's, with those the group "
                                                                         + "translates already; given, it "
                                                                         + "holds every one the scope puts in "
                                                                         + "the group."))
                        .addBooleanProperty(OWN_OUTPUT_FEED_ARG, "With group, only where no form can be shown (or "
                                                                 + "the reply says its form couldn't be), for "
                                                                 + "a new group: true when the user wants its "
                                                                 + "Events in an output feed of its own, false for "
                                                                 + "the same feed. Otherwise asked in a form.")
                        .addEnumProperty(DOMAIN_SPLIT_ARG, List.of(DomainSplits.SPLIT, DomainSplits.KEEP),
                                "Only on the user's word in the chat: split the domain off users' Ids, or keep them "
                                + "whole. The latest word, over an answer kept with the XSLT or the feed's groups'.")
                        .addProperty(REFERENCES_ARG, CreatePipelineTool.referencesSchema("With group: the reference "
                                                                                        + "data the group's lookups "
                                                                                        + "read (find_reference_"
                                                                                        + "data), kept in the "
                                                                                        + "registry; the pipeline "
                                                                                        + "running the group loads "
                                                                                        + "every group's (set by "
                                                                                        + "create_pipeline, and at "
                                                                                        + "promotion)."))
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            final Map<String, Object> result = new LinkedHashMap<>(build(call, context));
            // A client outside Stroom takes only so much of a result; Stroom AI's chat keeps a large result itself,
            // and gives its model the outline in the result's stub.
            final String key = LargeResults.keyOf(stringArg(call, BUILD_ARG), stringArg(call, NAME_ARG));
            final Map<String, Object> chatOutline = LargeResults.forCaller(context.caller(), result,
                    (caller, said, limit) -> {
                        // Each rule's Data counted first, then the rest outlined if still too large.
                        final boolean trimmed = BuiltRulesTrim.trimIfLarge(caller, said, limit);
                        return BuiltRulesTrim.outlineIfLarge(caller, context.workspace(), key, said, limit)
                               || trimmed;
                    });
            return JsonResults.of(result).withChatOutline(chatOutline);
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
        try {
            return built(call, context, b);
        } catch (final AiChatRunManager.WaitingForUserException e) {
            // In the chat a form stops the call, which is made again with the answer and merges its changes
            // again: kept now, they were merged twice (seen: "changes.common removes 'EventDetail/Purpose', which
            // the mapping does not have", and the user's answer in the form lost with the call).
            b.sent = null;
            throw e;
        } finally {
            // Only a mapping that parses: seen in testing, changes that left an entry with no source were refused,
            // yet kept as the mapping later changes merged into, so the agent's next changes (written against the
            // mapping before them) were refused as removing what "the mapping does not have".
            if (b.sent != null && b.mapping != null) {
                if (b.saved == null) {
                    // Nothing saved: its changes are what a call naming the XSLT alone starts from next.
                    SentMappings.rememberUnsaved(context, request.target(), b.sent);
                } else {
                    SentMappings.remember(context, request.target(), b.sent);
                }
            }
        }
    }

    // The forms come before the sample check where they can: the check of a large sample takes a minute or more
    // (seen in eval case 36 on Haiku over Claude Code: 61 s on 1,000 records), and a client may give up on a call
    // after a time with nothing sent on it (Claude Code: 60 s, the clock reset by a form or by progress). So,
    // decided by the user (2026-11-20):
    // - before the check: the event-logging version (settleVersion) and, for a save of a new source type group to a
    //   client that shows forms, where its Events go (placement; kept for the next call should this one save
    //   nothing);
    // - after it, as they ask about what it found: the domain split of Ids (splitDomains: the domain-qualified Ids
    //   the sample holds), the stand-ins for none (leavePlaceholdersOut: the values found), the confirmed event
    //   types translated otherwise (agreeConfirmedChanged: the check's lost ones) and the rules kept as Unknown
    //   (agreeUnknown: the records the check found they catch); and those of the save itself (a hand edit's
    //   fields, DNS lookups), on the code as the answers above leave it;
    // - while the check runs, the client is told it is still going, every few seconds (CallProgress.whileWorking),
    //   so nothing is silent for long.
    private Map<String, Object> built(final ToolCall call, final ToolCallContext context, final Build b) {
        final Request request = b.request;
        if (request.build() != null || request.uuid() != null) {
            // A save: refused first, whatever the mapping, while the build's catch-all is unconfirmed.
            refuseUnconfirmedCatchAll(buildOf(request));
        }
        final Optional<Map<String, Object>> early = readMapping(context, b);
        if (early.isPresent()) {
            return early.get();
        }
        final Optional<Map<String, Object>> question = settleVersion(call, context, b);
        if (question.isPresent()) {
            return question.get();
        }
        keepUnmappedLoggingOff(b);
        planGroup(context, b);
        unsplitDomains(b);
        generate(context, b);
        if (!TranslationBuilds.problems(b.result).isEmpty()) {
            // Fast: what the schema says is wrong comes back before the sample is read and checked (seen: minutes
            // a call on a large sample, for a mapping that could not have generated anyway).
            b.result.put("warnings", MappingCompaction.grouped(TranslationBuilds.warnings(b.result)));
            // The same problem in many rules said once, naming them, as the draft's are: problems are never
            // outlined (seen in eval case 68: 300 rules without EventSource/Device, 511 problems, 48,000 characters).
            b.result.put("problems", MappingCompaction.grouped(TranslationBuilds.problems(b.result)));
            // Without what each rule writes, which is for a mapping that generates, so the problems come back whole
            // rather than in a file read in parts (seen in testing: 20-70 KB results each round, and their problems
            // fixed a part at a time: 18 rules, then 10, then 1).
            b.result.remove("events");
            b.result.put("sample_check", "not run: fix the problems first");
            b.result.put("hint", TranslationBuilds.fixHint(request.uuid(), request.build(), request.name())
                                 + " The sample is checked once they are fixed.");
            withChanges(b);
            return b.result;
        }
        askBeforeTheCheck(call, context, b);
        checkSample(context, b);
        final boolean snapshots = writeSnapshotChanges(b);
        final boolean split = splitDomains(call, context, b);
        final boolean leftOut = leavePlaceholdersOut(call, context, b);
        if (split || leftOut || snapshots) {
            // Changed as the user chose (or old/new lists written per property): generated and checked again, so
            // what is saved and said is the mapping as changed.
            generate(context, b);
            checkSample(context, b);
        }
        if (!b.snapshotChanges.isEmpty()) {
            TranslationBuilds.warnings(b.result).add(SNAPSHOT_CHANGES + String.join("; ", b.snapshotChanges) + ".");
        }
        dropAnswered(b);
        checkAgainstNotes(b);
        checkSystem(b);
        checkLookups(b);
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
        b.result.put("problems", MappingCompaction.grouped(TranslationBuilds.problems(b.result)));
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
                    changes = ToolJson.loads(text);
                } catch (final ToolJson.JsonDecodeException e) {
                    throw ToolArgs.notJson("changes", ToolArgs.where(e), "pass it as an object. "
                                                                         + MappingProblems.ESCAPING);
                }
            }
            final TranslationBuilds.Base base = builds.mappingToChange(context, request.target(), keptFrom(request),
                    request.build(), savedName(request));
            b.basedOn = base.basedOn();
            b.fromAnEarlierSession = base.fromAnEarlierSession();
            if (changes instanceof final Map<?, ?> none && none.isEmpty()) {
                // Nothing to change: the mapping as it stands (a draft too large for a client outside Stroom to
                // take whole is built so, as drafted).
                given = base.mapping();
            } else {
                final MappingChanges.Applied applied = MappingChanges.applyChanges(base.mapping(), changes,
                        base.earlier());
                given = applied.merged();
                b.applied = applied.done();
            }
        } else if (given == null) {
            if (request.target().isEmpty()) {
                throw new ToolRefusal("Give mapping (draft_translation_mapping drafts one), changes to fix the one "
                                      + "sent before, or uuid= a saved XSLT alone to regenerate it from the mapping "
                                      + "kept with it");
            }
            // Regenerated from the mapping in hand, as the generator writes it now (seen: an agent asked to restyle
            // an XSLT sent its 1,400-line kept mapping back whole, after a first attempt written from memory): the
            // one sent last this session (seen in eval case 61: source_types given alone, after changes that were
            // refused, took the saved mapping and lost the rules they added), else the one kept with the XSLT, the
            // build's working copy's for a promoted XSLT.
            final TranslationBuilds.Kept kept = builds.keptMapping(context, request.target(), keptFrom(request));
            given = kept.mapping();
            b.version = kept.schemaVersion();
            b.change = NullSafe.nonBlankStringElse(b.change, "Regenerated from its mapping");
        }
        if (given instanceof final String text) {
            try {
                given = ToolJson.loads(text);
            } catch (final ToolJson.JsonDecodeException e) {
                final String around = text.substring(Math.max(0, e.pos() - 30), Math.min(text.length(),
                        e.pos() + 30));
                throw ToolArgs.notJson("mapping", ToolArgs.where(e) + ", around `" + around + "`",
                        "pass the mapping as an object, not a string. " + MappingProblems.ESCAPING);
            }
        }
        final List<String> unknownKeys = new ArrayList<>();
        if (given instanceof final Map<?, ?> map) {
            // Kept as sent once the call ends (build).
            b.sent = (Map<String, Object>) map;
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

    // The name the call's XSLT has in its build: the name given, else the group's. A source type group's XSLT is
    // named for the group's pipeline, whatever name is given (chat 185), so where the build has no XSLT of the name
    // given, the one the registry names for the group is the call's.
    private String savedName(final Request request) {
        final String given = NullSafe.isNonBlankString(request.name())
                ? request.name()
                : request.group();
        if (request.uuid() != null || request.group() == null || !NullSafe.isNonBlankString(request.build())
            || groups.xsltNamed(request.build(), given).isPresent()) {
            return given;
        }
        return groups.groupXsltNameInBuild(request.build(), request.feeds(), request.group()).orElse(given);
    }

    // The XSLT whose kept mapping a call starts from: the build's working copy (or new version) of a promoted XSLT
    // given as uuid, which holds what the build changed of it; else the uuid given.
    private String keptFrom(final Request request) {
        if (request.uuid() == null || !NullSafe.isNonBlankString(request.build())) {
            return request.uuid();
        }
        return groups.buildCopyOf(request.build(), request.uuid()).orElse(request.uuid());
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
        throw new ToolRefusal("mapping is not a translation mapping: " + String.join("; ", problems) + ". Nothing "
                              + "from this call is kept: changes still apply to the mapping as it was before it. "
                              + "Fix these in the mapping you have; to start again, draft_translation_mapping "
                              + "(stream_ids=the sample streams) gives one." + (MappingProblems.structural(e)
                ? " A whole mapping looks like: " + ToolJson.dumps(MappingProblems.MAPPING_EXAMPLE)
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
            b.version = keptPayload(saved)
                    .map(KeptCode::keptVersion)
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

    // The logging of fields no rule reads turned off by hand in the saved XSLT (its log_unmapped_fields variable
    // false()), where the mapping kept with it had it on: kept off, as set in Stroom's editor (asked for by the user:
    // the switch is in the XSLT, so it can be turned off there). Turned on again with the mapping's
    // log_unmapped_fields true once the kept mapping has it off.
    private void keepUnmappedLoggingOff(final Build b) {
        if (b.request.uuid() == null || b.mapping == null || !b.mapping.logUnmappedFields()) {
            return;
        }
        try {
            final XsltDoc saved = translationDocs.readXslt(b.request.uuid());
            final Matcher variable = LOG_UNMAPPED_VARIABLE.matcher(NullSafe.string(saved.getData()));
            if (!variable.find() || !variable.group().replace('\'', '"').contains("select=\"false()\"")) {
                return;
            }
            final boolean keptOn = keptPayload(saved)
                    .map(payload -> payload.get("mapping"))
                    .filter(Map.class::isInstance)
                    .map(mapping -> !Boolean.FALSE.equals(((Map<?, ?>) mapping).get("log_unmapped_fields")))
                    .orElse(true);
            if (keptOn) {
                b.mapping = b.mapping.withLogUnmappedFields(false);
                final List<String> applied = new ArrayList<>(NullSafe.list(b.applied));
                applied.add("log_unmapped_fields kept false, as set in the saved XSLT");
                b.applied = applied;
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No saved XSLT read for log_unmapped_fields: {}", e.getMessage()));
        }
    }

    // The translation mapping's payload kept with a saved XSLT ({schema_version, schema_uuid, mapping}), or null.
    private Map<String, Object> keptPayload(final String uuid) {
        return keptPayload(translationDocs.readXslt(uuid)).orElse(null);
    }

    private static Optional<Map<String, Object>> keptPayload(final XsltDoc saved) {
        return KeptMapping.readMapping(saved.getDescription())
                .filter(kept -> KeptMapping.TRANSLATION.equals(kept.kind()))
                .map(KeptMapping.Kept::payload);
    }

    // A source type group's (group=, the build's scoped group by default, or a saved group regenerated or changed):
    // the mapping split from the feed's common module, which with the helpers is made where the build has none.
    private void planGroup(final ToolCallContext context, final Build b) {
        final Request request = b.request;
        final SourceTypeModule kept = b.mapping.module();
        // A build makes one source type group, the one its scope names: a new translation in it is that group's.
        final String scoped = request.group() == null && kept == null
                ? scopedGroup(request)
                : null;
        if (request.group() == null && scoped == null && (kept == null || !kept.isGroup())) {
            if (!request.sourceTypes().isEmpty()) {
                throw new ToolRefusal("source_types are a source type group's: give group (its name) too");
            }
            if (kept == null) {
                // A whole translation of a feed whose survey confirmed a source type field: the values seen kept with
                // the mapping, so the XSLT logs one it didn't see. Outside a build, the mapping's own are kept.
                final String build = buildOf(request);
                groups.seenSourceTypes(build).ifPresent(seen -> b.mapping = b.mapping.withSeenSourceTypes(seen));
            }
            return;
        }
        final String build = buildOf(request);
        if (build == null) {
            throw new ToolRefusal("A source type group is built in a build, beside the feed's registry and common "
                                  + "module: give build");
        }
        b.build = build;
        b.schema = schemaOf(context, b);
        final String group = NullSafe.nonBlankStringElse(request.group(), kept == null
                ? scoped
                : kept.name());
        // A saved group's source types are its kept mapping's, joined by the scope's where the scope names it.
        final List<String> keptTypes = kept == null
                ? List.of()
                : NullSafe.list(kept.sourceTypes());
        final SourceTypeGroups.Registry registry = groups.registry(build, request.feeds());
        b.group = groups.plan(build, registry, group, request.sourceTypes(), keptTypes,
                b.mapping.withModule(null).withSeenSourceTypes(null), b.schema, b.version);
        b.mapping = b.group.groupMapping();
    }

    // The build the call is in: the one it names, else the one holding the saved XSLT it changes; null for neither
    // (a whole translation outside a build, or a promoted XSLT changed by its uuid). Seen in testing: a promoted
    // XSLT changed by its uuid alone, with a sample cut short, failed with a NullPointerException.
    private String buildOf(final Request request) {
        if (NullSafe.isNonBlankString(request.build())) {
            return request.build();
        }
        return NullSafe.get(request.uuid(), uuid -> groups.buildOf(uuid).orElse(null));
    }

    // The source type group the build's scope names, for a new translation given no group; null for a build without
    // one (a whole translation), or without a build.
    private String scopedGroup(final Request request) {
        final String build = buildOf(request);
        return build == null
                ? null
                : groups.scopedGroup(build, request.feeds()).orElse(null);
    }

    // The event-logging schema the translation writes. A saved XSLT changed or regenerated with no version given
    // stays in the XML schema document kept with it (a fork, say). Seen in testing: a new session remembered no
    // choice, so a change by uuid alone fell back to event-logging's document of the version and moved the fork's
    // translation into event-logging:3.
    private EventSchema schemaOf(final ToolCallContext context, final Build b) {
        final Request request = b.request;
        return request.uuid() != null && request.schemaVersion() == null
                ? eventVersions.keptSchema(context, request.build(), keptPayload(request.uuid()), b.version)
                : eventVersions.eventSchema(context, request.build(), b.version);
    }

    // The mapping as the checks read it: a source type group's with what the feed's common module writes for it.
    private static TranslationMapping checked(final Build b) {
        return b.group == null
                ? b.mapping
                : b.group.withGroupMapping(b.mapping).effective();
    }

    // The XSLT generated, with the imports standing instructions require.
    private void generate(final ToolCallContext context, final Build b) {
        final Request request = b.request;
        b.schema = schemaOf(context, b);
        final List<RequiredImport> required = requiredImports.requiredFor(context.session(),
                InstructionRules.TRANSLATION, request.build(), request.feeds());
        final Set<String> have = new LinkedHashSet<>(b.mapping.imports());
        b.mapping.shared().stream().map(SharedTemplate::href).forEach(have::add);
        b.mapping.functions().stream().map(SharedFunctions::href).forEach(have::add);
        if (b.group != null) {
            // A group's stylesheet imports what its common module imports: never imported again by the group.
            b.group.common().shared().stream().map(SharedTemplate::href).forEach(have::add);
        }
        final List<String> missing = required.stream()
                .map(RequiredImport::href)
                .filter(href -> !have.contains(href))
                .toList();
        if (!missing.isEmpty()) {
            final List<String> imports = new ArrayList<>(b.mapping.imports());
            imports.addAll(missing);
            b.mapping = TranslationBuilds.withImports(b.mapping, imports);
        }
        b.result = b.group == null
                ? builds.generated(b.mapping, b.schema, b.version)
                : builds.generated(b.mapping, b.schema, b.version, b.group.unsaved());
        if (b.group != null) {
            if (!b.group.problems().isEmpty()) {
                TranslationBuilds.problems(b.result).addAll(b.group.problems());
                b.result.put("ok", false);
                b.result.put("xslt", null);
            }
            b.result.put("group", groupSaid(b));
        } else if (b.mapping.seenSourceTypes() != null) {
            final SeenSourceTypes seen = b.mapping.seenSourceTypes();
            b.result.put("seen_source_types", "The survey found source type field " + seen.field() + ": the "
                                              + (seen.values().size() + seen.patterns().size()
                                                 + seen.members().size()) + " value(s), pattern(s) and list "
                                              + "item(s) seen are kept with the mapping, and the XSLT logs (INFO, "
                                              + "once a stream) a value outside them, so review_coverage reports it "
                                              + "(unseen_source_types) and the user can survey the feed again.");
        }
        b.result.put("schema_version", b.version);
        // A version given still needs telling where the user chose the variant in a form the agent didn't see.
        if (request.schemaVersion() == null || eventVersions.variantChosenInForm(context, request.build())) {
            eventVersions.settled(context, request.build(), b.version)
                    .ifPresent(settled -> b.result.put("schema_version_settled", settled));
        }
        if (!required.isEmpty()) {
            b.result.put("required_imports", requiredImports.importNotes(required, true));
        }
        if (Boolean.TRUE.equals(b.result.get("ok"))) {
            // The environment's common elements written as its pipelines write them: with their shared templates.
            final TranslationMapping mapping = checked(b);
            TranslationBuilds.warnings(b.result).addAll(translationDocs.sharedWarnings(context.session(),
                    String.valueOf(b.result.get("xslt")), request.build(), "Events", at -> mapping.allEntries()
                            .stream()
                            .map(FieldMapping::path)
                            .anyMatch(path -> path.equals(at) || path.startsWith(at + "/"))));
        }
        if (!b.unknownKeys.isEmpty()) {
            TranslationBuilds.warnings(b.result).add("mapping keys ignored, as a mapping has no such key: "
                                                     + ToolText.list(b.unknownKeys) + " (it has "
                                                     + ToolText.list(MappingProblems.mappingKeys())
                                                     + ")");
        }
    }

    // The mapping checked against the sample: its text, or its streams'.
    @SuppressWarnings("unchecked")
    private void checkSample(final ToolCallContext context, final Build b) {
        final Request request = b.request;
        final TranslationMapping mapping = checked(b);
        final List<String> warnings = TranslationBuilds.warnings(b.result);
        List<String> samples = request.samples();
        // A save given neither is checked against the build's own sample streams: seen in eval case 36, a save with
        // "sample": [] and no stream_ids checked no record, returned ok, and every form the check drives was skipped.
        final List<Long> streamIds = samples == null && request.streamIds().isEmpty() && saves(request)
                ? buildSampleStreams(request)
                : request.streamIds();
        if (request.streamIds().isEmpty() && !streamIds.isEmpty()) {
            warnings.add("sample: checked against the build's sample streams " + streamIds + " (neither sample "
                         + "nor stream_ids was given)");
        }
        // An aggregated feed's build with a scope: the mapping is checked against the scope's records alone, those of
        // other source types left out as they are read, and only the scope's records are compared with the confirmed
        // event types.
        // A source type group's: its own source types alone, of the build's scope.
        final Optional<SourceTypeScope> scope = b.group == null
                ? builds.scope(buildOf(request))
                : Optional.of(new SourceTypeScope(b.build, b.group.field(), SourceTypeRegistry.rows(
                        b.group.registry().found().state()), b.group.sourceTypes()));
        if (samples == null && !streamIds.isEmpty()) {
            final SampleStreamTexts.Texts read = sampleStreamTexts.read(streamIds);
            final SampleStreamTexts.Scoped scoped = scope.map(found -> sampleStreamTexts.scoped(read, found))
                    .orElse(null);
            final SampleStreamTexts.Texts texts = scoped == null
                    ? read
                    : scoped.texts();
            if (scoped != null) {
                b.result.put("scope", scoped.said());
            }
            samples = new ArrayList<>(texts.texts().values());
            texts.notes().forEach(note -> warnings.add("sample: " + note));
            if (!texts.cut().isEmpty()) {
                // The records the survey found further in than the sample is read to, checked too (the scope's).
                final SurveyExamples.Added added = SurveyExamples.added(read.texts(), texts.cut(),
                        builds.surveyStatesInBuild(buildOf(request)),
                        scope.orElse(null));
                if (!added.texts().isEmpty()) {
                    samples.addAll(added.texts());
                    warnings.add("sample: " + added.records() + " record(s) the survey found further into streams "
                                 + added.streams() + " than the sample is read to are checked too");
                }
            }
        } else if (samples != null) {
            for (final String text : samples) {
                SampleTexts.checkSample(text, SAMPLE_ARG);
            }
        }
        if (!streamIds.isEmpty()) {
            warnings.addAll(builds.metaNotSent(mapping, streamIds));
        }
        warnings.addAll(lookupKeyCounts.mismatches(mapping));
        warnings.addAll(TranslationBuilds.zoneNotKept(mapping));
        warnings.addAll(TranslationBuilds.zoneUnknown(mapping));
        SplitterSpec splitter = request.splitter();
        boolean inferred = false;
        if (samples != null && !samples.isEmpty() && "data_splitter".equals(b.mapping.input()) && splitter == null) {
            // The build's own converter, read by Stroom's parser as processing reads it, where it has one.
            final Optional<Map.Entry<String, String>> saved = builds.dataSplitter(request.build(), request.uuid());
            if (saved.isPresent()) {
                splitter = SplitterSpec.ofConverter(saved.get().getValue());
                warnings.add("sample: read with the build's Data Splitter '" + saved.get().getKey() + "', through "
                             + "Stroom's own parser");
            }
        }
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
        // The build's, a save naming only the XSLT's uuid too (found profiling: a uuid-only save read no catalogue,
        // so a confirmed event type translated otherwise, or its vendor's code lost, was saved unsaid).
        final String build = buildOf(request);
        final Map<String, Object> notes = build == null
                ? Map.of()
                : builds.notesInBuild(build);
        final List<String> texts = samples;
        final SplitterSpec spec = splitter;
        final boolean guessed = inferred;
        // Told as it goes, as it may take a minute or more (see built).
        final Map<String, Object> check = CallProgress.of(context).whileWorking("Checking the mapping against the "
                                                                                + "sample records", () ->
                MappingSampleCheck.check(mapping, texts, spec, b.schema, guessed, clock, SourceNotes.catalogue(notes),
                        scope.orElse(null), b.earlierCheck));
        b.earlierCheck = (MappingSampleCheck.Earlier) check.remove(MappingSampleCheck.EARLIER);
        b.lost = (List<ConfirmedEventTypes.Lost>) check.remove(MappingSampleCheck.CONFIRMED_LOST);
        b.contradictions = (List<String>) check.remove(MappingSampleCheck.CATALOGUE_CONTRADICTIONS);
        b.lost.forEach(one -> warnings.add(lostWarning(one)));
        b.qualified = (List<LocalCheck.QualifiedIds>) check.remove(MappingSampleCheck.DOMAIN_QUALIFIED);
        b.placeholders = (List<Placeholders.Found>) check.remove(MappingSampleCheck.PLACEHOLDERS);
        // What the sample's records all fill is no longer said to be maybe left out (seen in chat 185: the agent
        // asked the user about each and put 'Unknown' in all of them).
        XsltGenerator.withoutRequiredFilled(warnings,
                (Map<String, Set<String>>) check.remove(MappingSampleCheck.REQUIRED_FILLED));
        b.result.put("sample_check", check);
        JsonValues.strings(check.get("warnings")).forEach(warning -> warnings.add("sample: " + warning));
        final List<String> found = new ArrayList<>(JsonValues.strings(check.get("problems")));
        if (saves(request) && !JsonValues.truthy(check.get("records"))) {
            // A check over no record says nothing of the mapping: saved on it, the forms it drives (domain split,
            // placeholders, confirmed event types, rules kept as Unknown) were all skipped.
            found.add("no sample record was checked" + (JsonValues.truthy(check.get("note"))
                    ? " (" + check.get("note") + ")"
                    : "") + ", so nothing is saved: give stream_ids (the sample streams, holding records of the "
                      + "mapping's input) or sample (their text)");
        }
        if (!found.isEmpty()) {
            // A time format no sample value fits fails every record at stepping: as good as a schema problem.
            found.forEach(problem -> TranslationBuilds.problems(b.result).add("sample: " + problem));
            b.result.put("ok", false);
            b.result.put("xslt", null);
        }
    }

    // An Update's old/new lists written as a Data per property of each snapshot whatever the mapping says of them
    // (2026-11-23, eval case 76: targets written as for_each groups of name and value Data, their values JSON text,
    // or in After alone): as the draft writes them, the sample's records telling the lists by shape. True if the
    // mapping was changed, which is then generated and checked again.
    private static boolean writeSnapshotChanges(final Build b) {
        if (!"json".equals(b.mapping.input()) || b.earlierCheck == null) {
            return false;
        }
        final List<Object> records = b.earlierCheck.found().records();
        final Map<String, Object> json = JsonValues.deepCopy(b.mapping.toJson(DumpMode.COMPACT));
        final List<String> written = SnapshotChanges.apply(json, field -> records.stream()
                .filter(Map.class::isInstance)
                .map(record -> ((Map<?, ?>) record).get(field))
                .filter(Objects::nonNull)
                .toList(), Map.of());
        if (written.isEmpty()) {
            return false;
        }
        b.mapping = TranslationMapping.fromJson(json);
        b.snapshotChanges = written;
        return true;
    }

    // With build and name, or uuid, the XSLT is saved.
    private static boolean saves(final Request request) {
        return request.build() != null || request.uuid() != null;
    }

    // The build's own sample streams (the newest), else the newest its survey read: what a save given neither sample
    // nor stream_ids is checked against. None for an XSLT in no build, which is never saved (WriteGuard refuses it,
    // saying why). Refused for a build with neither.
    private List<Long> buildSampleStreams(final Request request) {
        final String build = buildOf(request);
        if (build == null) {
            return List.of();
        }
        final List<Long> own = plan.rawSampleStreams(build, MOST_BUILD_SAMPLE_STREAMS);
        if (!own.isEmpty()) {
            return own;
        }
        final List<Long> surveyed = builds.surveyStatesInBuild(build).stream()
                .flatMap(state -> SurveyDoc.streams(state).stream())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(MOST_BUILD_SAMPLE_STREAMS)
                .toList();
        if (surveyed.isEmpty()) {
            throw new ToolRefusal("A saved XSLT is checked against the sample first, and none was given: build '"
                                  + build + "' has no sample streams of its own and no survey naming any. Give "
                                  + "stream_ids (the sample streams) or sample (their text)");
        }
        return surveyed;
    }

    // EventSource/System/Name and Environment are the user's to give (or a standing instruction's): a draft's
    // placeholder left in isn't saved. Seen in testing: the source's name went in as the System/Name, and the user
    // was never asked for either.
    private static void checkSystem(final Build b) {
        final List<String> left = new ArrayList<>();
        for (final FieldMapping entry : checked(b).allEntries()) {
            final String path = entry.strippedPath();
            if (("EventSource/System/Name".equals(path) || "EventSource/System/Environment".equals(path))
                && entry.value() != null && entry.value().strip().startsWith("TODO")) {
                left.add(path + " ('" + entry.value() + "')");
            }
        }
        if (!left.isEmpty()) {
            TranslationBuilds.problems(b.result).add(String.join(" and ", new LinkedHashSet<>(left))
                                                     + " still a placeholder: ask the user for the system's name "
                                                     + "and environment (unless the standing instructions give "
                                                     + "them), then set the value; if they don't know, a value "
                                                     + "they agree to, such as Unknown");
            b.result.put("ok", false);
            b.result.put("xslt", null);
        }
    }

    // The catalogue's event types not yet confirmed (documentation's, the agent's) that a rule contradicts, compared
    // by the sampled records the rules take (checkSample): what a mapping does with confirmed ones is put to the user
    // apart (agreeConfirmedChanged).
    private void checkAgainstNotes(final Build b) {
        final String build = buildOf(b.request);
        if (build == null || b.contradictions == null || SourceNotes.catalogue(builds.notesInBuild(build)) == null) {
            return;
        }
        b.result.put("source_notes_check", b.contradictions.isEmpty()
                ? "the mapping agrees with the catalogue of event types"
                : b.contradictions);
        b.contradictions.forEach(c -> TranslationBuilds.warnings(b.result).add("catalogue: " + c));
    }

    // Unless the user chose to drop them: a next version written anew loses its predecessor's lookups silently.
    private void checkLookups(final Build b) {
        if (b.request.build() == null) {
            return;
        }
        final Map<String, Object> current = currentTranslations.current(b.request.build()).orElse(null);
        if (CurrentTranslations.lookupsOf(current) && !CurrentTranslations.lookupsKept(current, b.mapping)) {
            TranslationBuilds.warnings(b.result).add("This mapping keeps none of the current translation's "
                                                     + "lookups. " + CurrentTranslations.lookupsNote(current));
        }
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
            final Map<String, String> code = Map.of(element, marked);
            final PipelineStepping.Outputs outputs = PipelineStepping.withinTimeLimit(context, () ->
                    stepping.outputs(context, pipeline, request.streamIds(), element, code,
                            request.maxRecords()));
            stepping.audit(context, request.streamIds(), "Stepped stream(s) through " + pipeline.name() + " with the "
                                                         + NAME + " tool");
            final List<XmlElement> events = EventFieldDocs.sampledEvents(new ArrayList<>(
                    outputs.outputs().values()));
            b.result.put("field_mapping", EventFieldDocs.fieldMappingMarkdown(checked(b), b.schema, events,
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
        final Optional<Map<String, Object>> changed = agreeConfirmedChanged(call, context, b);
        if (changed.isPresent()) {
            return changed;
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
        String xslt = String.valueOf(b.result.get("xslt"));
        // A source type group's imports saved first, so it imports them by their UUIDs.
        SourceTypeGroups.Imports imports = null;
        String target = request.uuid();
        String name = request.name();
        SourceTypeGroups.Placement placement = null;
        String choice = null;
        if (b.group != null) {
            // Where a new group's Events go is the user's choice, asked before anything is saved; a change to a
            // promoted pipeline makes a new version of it.
            placement = b.placementAsked
                    ? b.placement
                    : placement(call, context, b);
            choice = pipelineChoice(b);
            imports = groups.saveImports(context, b.build, b.group.withGroupMapping(b.mapping), b.schema, b.version);
            b.mapping = imports.group();
            final XsltGenerator.Generated regenerated = XsltGenerator.generate(b.mapping, b.schema, b.version);
            if (!regenerated.ok()) {
                throw new ToolRefusal("The group's XSLT doesn't generate importing the saved modules: "
                                      + String.join("; ", regenerated.problems()));
            }
            xslt = regenerated.xslt();
            if (request.includeXslt()) {
                b.result.put("xslt", xslt);
            }
            // A group's new XSLT is named for the group, as its pipeline is, whatever name the agent passed (seen in
            // chat 185: saved under the agent's own name, beside the pipeline create_pipeline named for the group).
            name = SourceTypeGroups.xsltName(b.group);
            if (target == null || !groups.isInBuild(b.build, target)) {
                // A group's promoted XSLT given as uuid is changed through the build's copy of it, never in place.
                target = groups.groupTarget(context, b.build, b.group, name).orElse(null);
            }
            if (target == null && NullSafe.isNonBlankString(request.name()) && !name.equals(request.name())) {
                b.result.put("name_note", "Saved as '" + name + "', named for source type group '"
                                          + b.group.group() + "' as its pipeline is (create_pipeline proposes the "
                                          + "same name), not '" + request.name() + "': a group's new XSLT is named "
                                          + "for the group, whatever name is passed. Use the uuid this result "
                                          + "gives from now on.");
            }
        }
        Map<String, Object> saved;
        if (target != null) {
            saved = translationDocs.updateXslt(call, context, target, xslt, null,
                    TranslationDocs.Source.of(b.mapping), said, request.discardHandEdit(),
                    request.handEditChoices());
            if (!(saved.get("uuid") instanceof String)) {
                // The user is asked about fields edited by hand: nothing saved yet.
                return Optional.of(saved);
            }
        } else if (name != null) {
            final String build = buildOf(request);
            if (build == null) {
                throw new ToolRefusal("A new XSLT is saved in a build: give build (the build's folder) with name");
            }
            saved = translationDocs.createXslt(call, context, build, name, xslt, TranslationDocs.Source.of(b.mapping),
                    said);
        } else {
            throw new ToolRefusal("To save the XSLT give name (a new XSLT in the build) or uuid (the one saved "
                                  + "before)");
        }
        final String savedUuid = String.valueOf(saved.get("uuid"));
        if (imports != null) {
            // The registry's rows for its source types translated, naming the group's XSLT.
            final Map<String, Object> group = groupSaid(b);
            group.put("xslt", Map.of("uuid", savedUuid, "name", String.valueOf(saved.get("name"))));
            group.putAll(imports.saved());
            final Map<String, Object> marked = new LinkedHashMap<>(groups.markTranslated(context, b.build, b.group,
                    imports, savedUuid, String.valueOf(saved.get("name")), placement, choice,
                    request.references()));
            // What placing it said joins what splitting its mapping said.
            final List<String> notes = new ArrayList<>(JsonValues.strings(group.get("notes")));
            notes.addAll(JsonValues.strings(marked.remove("notes")));
            notes.addAll(b.placementNotes);
            group.putAll(marked);
            if (!notes.isEmpty()) {
                group.put("notes", notes);
            }
            groupNext(b, placement, choice).ifPresent(next -> group.put("group_next", next));
            b.result.put("group", group);
        }
        final String uuid = savedUuid;
        if (!agreedNow.isEmpty()) {
            saved = new LinkedHashMap<>(saved);
            saved.put("version", builds.rememberUnknownAgreed(uuid, agreedNow));
        }
        if (!b.confirmedChanges.isEmpty()) {
            saved = new LinkedHashMap<>(saved);
            saved.put("version", builds.rememberAgreed(TranslationBuilds.CONFIRMED_CHANGED, uuid,
                    b.confirmedChanges));
        }
        if (!b.domainChoices.isEmpty()) {
            saved = new LinkedHashMap<>(saved);
            saved.put("version", builds.rememberAgreed(TranslationBuilds.DOMAIN_CHOSEN, uuid, b.domainChoices));
        }
        if (!b.placeholderChoices.isEmpty()) {
            saved = new LinkedHashMap<>(saved);
            saved.put("version", builds.rememberAgreed(TranslationBuilds.PLACEHOLDERS_CHOSEN, uuid,
                    b.placeholderChoices));
        }
        final Map<String, Object> summary = new LinkedHashMap<>();
        for (final String key : List.of("type", "uuid", "name", "version")) {
            summary.put(key, saved.get(key));
        }
        b.result.put("saved", summary);
        SentMappings.remember(context, uuid, b.mapping.toJson(DumpMode.COMPACT));
        if (uuid.equals(request.target())) {
            // What was saved is what changes start from next, not what was sent before it.
            b.sent = null;
        } else if (request.uuid() != null) {
            // Saved in the build's copy of the promoted XSLT given: the next call naming it starts from what was
            // saved too.
            SentMappings.remember(context, request.target(), b.mapping.toJson(DumpMode.COMPACT));
            b.sent = null;
        }
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

    // What a save asks that doesn't depend on the sample check, asked before it (see built): where a new group's
    // Events go, when the client can show the form (one that can't is told to ask in the chat at the save, as
    // before).
    private void askBeforeTheCheck(final ToolCall call, final ToolCallContext context, final Build b) {
        if (saves(b.request) && b.group != null && context.userQuestions().canAsk()) {
            b.placement = placement(call, context, b);
            b.placementAsked = true;
        }
    }

    // Where a new group's Events go: asked in a form whenever a build makes a group (decided 2026-11-13), the same
    // feed the default (its index is its own either way, decided 2026-11-14); every group has a pipeline of its own.
    // Where no form can be shown, the agent asks in the chat and passes the answer (own_output_feed). Null for a
    // group placed already.
    private SourceTypeGroups.Placement placement(final ToolCall call, final ToolCallContext context, final Build b) {
        final Request request = b.request;
        if (!SourceTypeGroups.needsPlacement(b.group)) {
            if (request.ownOutputFeed() != null) {
                b.placementNotes.add(OWN_OUTPUT_FEED_ARG + " was ignored: group '" + b.group.group() + "' is "
                                     + "placed already, and source types added to it join its output feed.");
            }
            return null;
        }
        // Answered in an earlier call that saved nothing (asked before its sample check, see built).
        final String chosenBefore = UnsavedChoices.recalled(context, placementTarget(b), PLACEMENT_CHOSEN)
                .get(b.group.group());
        if (chosenBefore != null) {
            return new SourceTypeGroups.Placement(Boolean.parseBoolean(chosenBefore));
        }
        final String pipeline = SourceTypeGroups.pipelineName(b.group.feed(), b.group.group());
        final List<String> feeds = List.of("Into the same feed (" + b.group.feed() + ")",
                "To an output feed of its own (made next)");
        final String question = "Where do the Events of group '" + b.group.group() + "' go?";
        // The user's answer said in the chat first, where it is theirs (no form can be shown, or this one couldn't
        // be): asked again, a form the client can't show would refuse the call every time.
        if (request.ownOutputFeed() != null && consent.takesChatAnswer(context, NAME, question)) {
            return new SourceTypeGroups.Placement(request.ownOutputFeed());
        }
        final List<String> types = b.group.sourceTypes();
        final String named = types.size() <= Consent.LINES_SHOWN
                ? String.join(", ", types)
                : String.join(", ", types.subList(0, Consent.LINES_SHOWN)) + " and " + (types.size()
                                                                                       - Consent.LINES_SHOWN)
                  + " more";
        final Optional<String> chosen = consent.choose(call, context, NAME, "Source type group '" + b.group.group()
                                                                            + "' (" + named + ") is new. It gets a "
                                                                            + "pipeline of its own, '" + pipeline
                                                                            + "', which reads every stream of the "
                                                                            + "feed and passes over the other source "
                                                                            + "types' records. Choose where its "
                                                                            + "Events go.",
                feeds, question);
        if (chosen.isPresent()) {
            final boolean own = feeds.get(1).equals(chosen.get());
            UnsavedChoices.remember(context, placementTarget(b), PLACEMENT_CHOSEN, Map.of(b.group.group(),
                    String.valueOf(own)));
            return new SourceTypeGroups.Placement(own);
        }
        if (request.ownOutputFeed() == null || !consent.takesChatAnswer(context, NAME, question)) {
            throw new ToolRefusal(consent.whyInChat(context, NAME, question) + ". Group '" + b.group.group()
                                  + "' is new: it gets a pipeline of its own ('" + pipeline + "'). Ask the user "
                                  + "where its Events go: " + String.join("; ", feeds) + ". Then call again with "
                                  + OWN_OUTPUT_FEED_ARG + "=false (the same feed) or true (an output feed of its "
                                  + "own), as they said. Nothing was saved.");
        }
        return new SourceTypeGroups.Placement(request.ownOutputFeed());
    }

    // What the user's choice of where a new group's Events go is kept under until it is saved.
    private static String placementTarget(final Build b) {
        return "group:" + b.build + "/" + b.group.group();
    }

    // What a change to a group whose pipeline is promoted makes: always a new version of it (decided by the user,
    // 2026-11-17: a promoted pipeline and the code it runs are never changed in place), so nothing is asked. Null
    // for a group whose pipeline isn't promoted.
    private String pipelineChoice(final Build b) {
        return groups.promotedPipeline(b.build, b.group).isPresent()
                ? SourceTypeGroups.NEW_VERSION
                : null;
    }

    // What the agent does next for a group placed now (its pipeline made, its output feed first where it has one of
    // its own), or for a change to a promoted pipeline (its working copy, or its new version, copied).
    private static Optional<String> groupNext(final Build b,
                                              final SourceTypeGroups.Placement placement,
                                              final String choice) {
        final SourceTypeGroups.Plan plan = b.group;
        if (placement != null) {
            return Optional.of((placement.ownOutputFeed()
                    ? "Make its output feed first: create_feed build=<this build> name=<a name following the feed's "
                      + "convention> stream_type=Events group='" + plan.group() + "'. Then "
                    : "") + "create_pipeline build=<this build> group='" + plan.group() + "' template=<the "
                               + "translation template>: the group's own pipeline ('"
                               + SourceTypeGroups.pipelineName(plan.feed(), plan.group()) + "' by default), its "
                               + "translationFilter.xslt the group's XSLT, translating this group's source types and "
                               + "passing over the feed's other records, counted" + (placement.ownOutputFeed()
                    ? "; its Events go to the output feed (create_pipeline sets the stream appender's feed)"
                    : "; its Events go into the feed's Events") + ".");
        }
        if (SourceTypeGroups.NEW_VERSION.equals(choice)) {
            return Optional.of("Copy the group's pipeline as its new version: copy_pipeline build=<this build> "
                               + "source_uuid=<the pipeline's uuid> new_name=<the new version's name>. It runs the "
                               + "new version of the group's XSLT saved now, and the registry's rows of the group "
                               + "move to it.");
        }
        return Optional.empty();
    }

    // What a source type group's call says of the group: its mode and source types, what its mapping left to the
    // feed's common module or kept its own, and the modules it imports.
    private static Map<String, Object> groupSaid(final Build b) {
        final Map<String, Object> said = new LinkedHashMap<>();
        said.put("name", b.group.group());
        said.put("mode", b.group.mode());
        said.put("source_types", b.group.sourceTypes());
        said.put("system_name", b.group.systemName());
        said.put("common_parts", b.mapping.module() == null
                ? List.of()
                : b.mapping.module().parts().stream().map(SourceTypeModule.CommonPart::at).toList());
        if (!b.group.notes().isEmpty()) {
            said.put("notes", b.group.notes());
        }
        return said;
    }

    // The warning for an event type the user confirmed that the mapping translates otherwise.
    private static String lostWarning(final ConfirmedEventTypes.Lost one) {
        return "confirmed event type " + one.label() + " (recognised by " + one.recognisedBy() + ", "
               + one.records() + " sample records): the user confirmed " + one.wanted() + ", but " + (one.rule()
                .isEmpty()
                ? "no rule takes it"
                : "rule '" + one.rule() + "' makes it " + one.writes()) + ". Follow what they confirmed, or, saving, "
               + "the user is asked to agree to the change";
    }

    // An event type the user confirmed (catalogue_event_types) that the mapping translates otherwise is put to them
    // before saving
    // (decided with the user after the chat 163 review, where confirmed logoffs went to Unknown and a confirmed
    // Action Other became Logon, unsaid): shown in the chat, then a short form, keep the change or follow what they
    // confirmed. What they keep is kept with the XSLT, so it is asked once; without forms the warnings are left for
    // the agent to put to them.
    //
    // Any other answer is the user's own words, for the agent to act on; and the same form (the same kinds, each
    // still translated as it was) is not put to them again once answered. Seen (evals 58 and 59): a user who typed
    // their own answer was asked the identical form five to eight times, the mapping unchanged in between, until the
    // agent wrote the XSLT by hand. Asked again unchanged, words that chose neither answer are put to them once as a
    // choice of the two; an answer to follow what they confirmed is a refusal saying to act on it.
    private Optional<Map<String, Object>> agreeConfirmedChanged(final ToolCall call,
                                                                final ToolCallContext context,
                                                                final Build b) {
        final Request request = b.request;
        if (b.lost.isEmpty()) {
            return Optional.empty();
        }
        final Map<String, String> before = agreedSoFar(context, request, TranslationBuilds.CONFIRMED_CHANGED);
        final List<ConfirmedEventTypes.Lost> asking = new ArrayList<>();
        for (final ConfirmedEventTypes.Lost one : b.lost) {
            if (one.writes().equals(before.get(one.key()))) {
                b.confirmedChanges.put(one.key(), one.writes());
            } else {
                asking.add(one);
            }
        }
        if (asking.isEmpty()) {
            dropAgreedLost(b);
            return Optional.empty();
        }
        final Object records = b.result.get("sample_check") instanceof final Map<?, ?> check
                ? check.get("records")
                : "?";
        final String shown = ConfirmedEventTypes.markdown(asking, records);
        final String formId = Consent.reviewId(shown);
        final String earlier = UnsavedChoices.recalled(context, request.target(), CONFIRMED_ANSWERED).get(formId);
        final Optional<String> answer;
        if (earlier != null) {
            answer = answeredBefore(call, context, asking, earlier);
        } else if (!Consent.wasReviewed(request.reviewed(), shown)) {
            final Map<String, Object> review = new LinkedHashMap<>();
            review.put("status", "needs_review");
            review.put("show_user", shown);
            review.put("review_id", formId);
            review.put("hint", "Show the user show_user in the chat exactly as it is (markdown), then, in the same "
                               + "turn, without waiting for a reply, call build_translation_xslt again with the same "
                               + "arguments" + TranslationBuilds.CHANGES_APPLIED + " plus reviewed='" + formId
                               + "' (its review_id): a short form asks them to keep the mapping's change or follow "
                               + "what they confirmed."
                               + Consent.reviewNote(request.reviewed()));
            return Optional.of(review);
        } else {
            answer = consent.answer(call, context, NAME,
                    "This mapping translates event types you confirmed otherwise, as shown in the chat.",
                    "Keep the mapping's change, or follow what you confirmed", List.of(KEEP_CHANGE, FOLLOW_CONFIRMED));
        }
        if (answer.isEmpty()) {
            return Optional.empty();
        }
        final String given = answer.get();
        if (startsWith(given, KEEP_CHANGE)) {
            final Map<String, String> kept = ConfirmedEventTypes.keys(asking);
            b.confirmedChanges.putAll(kept);
            UnsavedChoices.remember(context, request.target(), TranslationBuilds.CONFIRMED_CHANGED,
                    b.confirmedChanges);
            dropAgreedLost(b);
            return Optional.empty();
        }
        UnsavedChoices.remember(context, request.target(), CONFIRMED_ANSWERED, Map.of(formId, given));
        return Optional.of(followOrTheirWords(asking, given));
    }

    // The same form answered before, the mapping translating its kinds as it did then: not asked again. Words that
    // chose neither answer are put to the user once more as a choice of the two (empty without forms, refused); an
    // answer that chose to follow what they confirmed is refused, saying to act on it.
    private Optional<String> answeredBefore(final ToolCall call,
                                            final ToolCallContext context,
                                            final List<ConfirmedEventTypes.Lost> asking,
                                            final String earlier) {
        if (!startsWith(earlier, FOLLOW_CONFIRMED)) {
            final Optional<String> chosen = consent.choose(call, context, NAME, "You answered: \""
                            + Consent.heading(earlier, MOST_WORDS_QUOTED) + "\". Of the event types shown in the chat "
                            + "that this mapping translates otherwise than you confirmed, which does that mean?",
                    List.of(KEEP_CHANGE, FOLLOW_CONFIRMED), "Keep the mapping's change, or follow what you confirmed");
            if (chosen.isPresent()) {
                return chosen;
            }
        }
        throw new ToolRefusal("Nothing saved, and not asked again: the user already answered this question for "
                              + "these event types (" + ToolText.list(asking.stream()
                .map(ConfirmedEventTypes.Lost::typeId)
                .distinct().toList()) + "), and the mapping still translates them as it did. Their answer: \""
                              + earlier + "\". Act on it: change the mapping so each event type's rule writes what "
                              + "they confirmed, or, where their answer leaves an event type out of this build (e.g. "
                              + "a source type parked for a later build), drop it; then call again. A changed mapping "
                              + "is checked afresh.");
    }

    // What the agent is told when the user chose to follow what they confirmed, or answered in their own words.
    private static Map<String, Object> followOrTheirWords(final List<ConfirmedEventTypes.Lost> asking,
                                                          final String given) {
        final boolean follow = startsWith(given, FOLLOW_CONFIRMED);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("saved", null);
        if (!given.equalsIgnoreCase(FOLLOW_CONFIRMED)) {
            result.put("user_says", given);
        }
        result.put("confirmed", asking.stream()
                .map(one -> one.label() + " (recognised by " + one.recognisedBy() + "): confirmed as " + one.wanted()
                            + "; now " + one.writes() + (one.rule().isEmpty()
                        ? ""
                        : " in rule '" + one.rule() + "'"))
                .toList());
        final String again = " Called again with the mapping unchanged, this question is not put to the user again.";
        result.put("hint", "Nothing saved. " + (follow
                ? "The user wants what they confirmed followed" + (result.containsKey("user_says")
                        ? ", as they say (user_says)"
                        : "") + ": change the mapping so a rule takes each of these event types' records and writes "
                  + "what they confirmed (or, where they leave an event type out of this build, drop it), then "
                  + "build and save "
                  + "again." + again
                : "The user answered in their own words (user_says), choosing neither suggested answer. Act on "
                  + "them for each event type in confirmed: where they say how it is translated, change its rule to "
                  + "that; where they leave it out of this build (e.g. a source type parked for a later build), "
                  + "drop it. Then build and save again: a changed mapping is checked afresh. If their words don't "
                  + "change the mapping, call again with it unchanged: they are asked once more, to pick keep or "
                  + "follow."));
        return result;
    }

    private static boolean startsWith(final String text, final String prefix) {
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    // The warnings for the changes the user agreed to: answered, they are not the agent's to act on.
    private static void dropAgreedLost(final Build b) {
        final List<String> warnings = TranslationBuilds.warnings(b.result);
        b.lost.stream()
                .filter(one -> b.confirmedChanges.containsKey(one.key()))
                .forEach(one -> warnings.remove(lostWarning(one)));
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
                : builds.unknownAgreed(request.uuid(), request.build(), savedName(request));
        final String catchAllAgreed = kept.isEmpty()
                ? null
                : catchAllAgreed(buildOf(request));
        kept.forEach(rule -> {
            final String key = TranslationBuilds.unknownKey(rule);
            if (key.equals(agreedBefore.get(rule.name()))
                || (rule.catchAll() && catchAllAgreed != null && catchAllAgreed.equals(rule.allowUnknown()))) {
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
        if (!JsonValues.truthy(check.get("records"))) {
            // The user is asked what they agree to: which records, with which values. Without the sample the form
            // said "no sample records checked", and logoffs went to Unknown unseen.
            throw new ToolRefusal("Keeping a rule as Unknown is put to the user with the sample records it catches: "
                                  + "call again with stream_ids (the sample streams) or sample, so the check can show "
                                  + "them" + (JsonValues.truthy(check.get("note"))
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
        final String shown = TranslationBuilds.unknownMarkdown(kept, sampled, check.get("records"), b.lost);
        if (!Consent.wasReviewed(request.reviewed(), shown)) {
            // Passed on only with this id, as promote_build's layout (seen there: reviewed=true from the start).
            final Map<String, Object> review = new LinkedHashMap<>();
            review.put("status", "needs_review");
            review.put("show_user", shown);
            review.put("review_id", Consent.reviewId(shown));
            review.put("hint", "Show the user show_user in the chat exactly as it is (markdown), then, in the same "
                               + "turn, without waiting for a reply, call build_translation_xslt again with the same "
                               + "arguments" + TranslationBuilds.CHANGES_APPLIED + " plus reviewed='"
                               + Consent.reviewId(shown) + "' (its review_id): a short form asks them to agree, "
                               + "or they say what to change (a rule's action instead of Unknown)."
                               + Consent.reviewNote(request.reviewed()));
            return Optional.of(review);
        }
        // The form says which records in plain words; the answer is bound to the table shown by its review id, which
        // the form doesn't show (it meant nothing to the user).
        consent.require(call, context, Consent.Request.confirmation(NAME,
                "Translate these records as Unknown events (what happened in them isn't known; the chat lists them "
                + "in full)", TranslationBuilds.unknownFormDetails(kept, sampled, check.get("records")))
                .boundTo(shown));
        kept.forEach(rule -> agreedNow.put(rule.name(), TranslationBuilds.unknownKey(rule)));
        return Optional.empty();
    }

    // A catalogued build is saved only once its catalogue's catch-all is confirmed: the user agreed in its form
    // what the records no event type recognises become (seen in a Haiku run: the user declined the catch-all, the
    // agent drafted without build= and the translation was saved with the catch-all unconfirmed). Only a catalogue
    // the user is taken through by catalogue_event_types (its notes keep the confirmation's state): one read back
    // from a hand-written translation has no catch-all to put to them (seen in the full e2e run: a production
    // pipeline's next version and a restyle refused).
    private void refuseUnconfirmedCatchAll(final String build) {
        if (NullSafe.isBlankString(build)) {
            return;
        }
        final Catalogue catalogue = SourceNotes.catalogue(builds.notesInBuild(build));
        if (catalogue == null || catalogue.catchAll().confirmed() || !builds.catalogueConfirmedThroughTool(build)) {
            return;
        }
        throw new ToolRefusal("Nothing saved: build '" + build + "' has a catalogue of event types whose catch-all "
                              + "(what the uncatalogued records, those no event type recognises, become) the user "
                              + "hasn't confirmed. Call catalogue_event_types build=" + build + " confirm=true: a "
                              + "short form asks them. If they declined it, ask them in the chat what those records "
                              + "should become and call catalogue_event_types again with their answer. Then "
                              + "draft_translation_mapping build=" + build + ", which follows the catalogue, and "
                              + "save its mapping.");
    }

    // The agreement the user gave the catalogue's catch-all in catalogue_event_types's form (its allow_unknown), or
    // null when the build's catch-all isn't confirmed: the catch-all rule carrying it is not put to them again (seen
    // in eval cases 19 and 58: the same records asked about in two forms).
    private String catchAllAgreed(final String build) {
        if (NullSafe.isBlankString(build)) {
            return null;
        }
        final Map<String, Object> notes = builds.notesInBuild(build);
        final Catalogue catalogue = SourceNotes.catalogue(notes);
        // As the draft writes it on the catch-all rule.
        return catalogue != null && catalogue.catchAll().confirmed()
                ? SourceNotes.catchAllReason(notes, catalogue)
                : null;
    }

    // A user's Id whose sample values carry a domain (domain.com\sam.lee) is put to the user, before saving: split it,
    // the account name in the Id and the domain in the Domain beside it, or keep it whole. Asked for by the user, as
    // telling only the agent let it through (seen: SecretServer's Ids kept the domain). The choice is kept with the
    // XSLT, so a field is asked about once (again only if it goes into other Ids); without forms the warning is left
    // for the agent.
    private boolean splitDomains(final ToolCall call, final ToolCallContext context, final Build b) {
        final Request request = b.request;
        if (b.qualified == null || b.qualified.isEmpty()) {
            return false;
        }
        final Map<String, String> before = new LinkedHashMap<>(agreedSoFar(context, request,
                TranslationBuilds.DOMAIN_CHOSEN));
        // The latest word wins: the user's answer as the call gives it, or the call's changes undoing a split (seen
        // in an eval: the user said in the chat to keep the Ids whole, and the "Split" kept with the XSLT split them
        // again on every save).
        for (final LocalCheck.QualifiedIds found : b.qualified) {
            if (request.domainSplit() != null) {
                before.put(DomainSplits.key(found), request.domainSplit());
            } else if (DomainSplits.undoneIn(request.changes(), found)) {
                before.put(DomainSplits.key(found), DomainSplits.KEEP);
            }
        }
        if (!ok(b) || (request.build() == null && request.uuid() == null)) {
            // Not asked now, but what the user answered before still answers the warning (seen in testing: asked
            // again on each round of fixing other problems).
            b.qualified.stream()
                    .filter(found -> before.containsKey(DomainSplits.key(found)))
                    .forEach(found -> b.domainChoices.put(DomainSplits.key(found),
                            before.get(DomainSplits.key(found))));
            return false;
        }
        // One question for the fields not answered before, the answer for them all (seen: an Id written from two
        // fields, By User in some rules and Target User in others, asked about twice in a row).
        final List<LocalCheck.QualifiedIds> unasked = b.qualified.stream()
                .filter(found -> !before.containsKey(DomainSplits.key(found)))
                .toList();
        // An aggregated feed's groups write their users' Ids one way: the feed's answer, where its groups (and this
        // XSLT for other fields) agree, is applied unasked; the user is asked where they differ or none has answered.
        final Map<String, List<String>> feedAnswers = unasked.isEmpty()
                ? Map.of()
                : feedDomainAnswers(b, unasked, before);
        final Optional<String> answer;
        if (unasked.isEmpty()) {
            answer = Optional.empty();
        } else if (feedAnswers.size() == 1) {
            answer = Optional.of(feedAnswers.keySet().iterator().next());
            b.domainNote = "Not asked: the users' Ids are written as the feed's groups write them (" + answer.get()
                           + ", as in " + ToolText.list(feedAnswers.get(answer.get())) + "). Only if the user "
                           + "says otherwise in the chat, call again with " + DOMAIN_SPLIT_ARG + " as they say.";
        } else {
            answer = consent.choose(call, context, NAME, DomainSplits.question(unasked) + (feedAnswers.isEmpty()
                    ? ""
                    : " The feed's groups differ: " + feedAnswers.entrySet().stream()
                            .map(entry -> entry.getKey() + " in " + ToolText.list(entry.getValue()))
                            .collect(Collectors.joining("; ")) + "."),
                    List.of(DomainSplits.SPLIT, DomainSplits.KEEP), null);
        }
        TranslationMapping mapping = b.mapping;
        final List<String> done = new ArrayList<>();
        for (final LocalCheck.QualifiedIds found : b.qualified) {
            final String key = DomainSplits.key(found);
            final Optional<String> chosen = before.containsKey(key)
                    ? Optional.of(before.get(key))
                    : answer;
            if (chosen.isEmpty()) {
                // No form: the warning is the agent's.
                continue;
            }
            b.domainChoices.put(key, chosen.get());
            if (DomainSplits.KEEP.equals(chosen.get())) {
                // The user's answer: the warning to the agent is answered.
                final List<String> warnings = TranslationBuilds.warnings(b.result);
                found.paths().forEach(path -> warnings.removeIf(warning -> warning.startsWith(
                        "sample: " + path + " takes field '" + found.field() + "', whose values are "
                        + "domain-qualified")));
                continue;
            }
            final DomainSplits.Split split = DomainSplits.split(mapping, found, b.schema);
            mapping = split.mapping();
            split.done().forEach(line -> done.add("the domain split off '" + found.field() + "', as the user chose: "
                                                  + line));
        }
        // Kept now, as this call may yet fail before the XSLT is saved with them.
        UnsavedChoices.remember(context, request.target(), TranslationBuilds.DOMAIN_CHOSEN, b.domainChoices);
        if (done.isEmpty()) {
            return false;
        }
        b.mapping = mapping;
        final List<String> applied = new ArrayList<>(NullSafe.list(b.applied));
        applied.addAll(done);
        b.applied = applied;
        return true;
    }

    // The user said in the chat to keep their Ids whole (domain_split Keep): a split the mapping has is undone, so
    // the sample check finds the Ids again and the answer is kept for them.
    private static void unsplitDomains(final Build b) {
        if (!DomainSplits.KEEP.equals(b.request.domainSplit())) {
            return;
        }
        final DomainSplits.Split unsplit = DomainSplits.unsplit(b.mapping);
        if (unsplit.done().isEmpty()) {
            return;
        }
        b.mapping = unsplit.mapping();
        final List<String> applied = new ArrayList<>(NullSafe.list(b.applied));
        unsplit.done().forEach(line -> applied.add("the users' Ids kept whole, as the user said: " + line));
        b.applied = applied;
    }

    // The answers the feed's groups give the domain question, with this XSLT's own answers for its other Ids: a
    // group's build only. Any other XSLT asks again for a field going into other Ids, as before.
    private Map<String, List<String>> feedDomainAnswers(final Build b,
                                                        final List<LocalCheck.QualifiedIds> unasked,
                                                        final Map<String, String> before) {
        final Map<String, List<String>> answers = new TreeMap<>();
        if (b.group == null) {
            return answers;
        }
        final Set<String> fields = new LinkedHashSet<>();
        b.qualified.forEach(found -> fields.add(found.field()));
        groups.domainAnswers(b.build, b.group, fields).forEach((answer, docs) ->
                answers.computeIfAbsent(answer, key -> new ArrayList<>()).addAll(docs));
        final Set<String> asked = new LinkedHashSet<>();
        unasked.forEach(found -> asked.add(DomainSplits.key(found)));
        before.forEach((key, answer) -> {
            if (!asked.contains(key) && List.of(DomainSplits.SPLIT, DomainSplits.KEEP).contains(answer)) {
                final List<String> docs = answers.computeIfAbsent(answer, k -> new ArrayList<>());
                if (!docs.contains("this XSLT")) {
                    docs.add("this XSLT");
                }
            }
        });
        return answers;
    }

    // Values in the sample that look like stand-ins for none ('-', 'N/A', ...) are put to the user, before saving, all
    // in one form: leave each out (nil_values) or keep it as written. Asked for by the user (seen: W3C logs' '-' for a
    // missing address went on into an Elasticsearch ip field, which refused the documents). The choices are kept with
    // the XSLT, so a value is asked about once; without forms the warning is left for the agent.
    private boolean leavePlaceholdersOut(final ToolCall call, final ToolCallContext context, final Build b) {
        final Request request = b.request;
        if (b.placeholders == null || b.placeholders.isEmpty()) {
            return false;
        }
        final Map<String, String> before = agreedSoFar(context, request, TranslationBuilds.PLACEHOLDERS_CHOSEN);
        if (!ok(b) || (request.build() == null && request.uuid() == null)) {
            // Not asked now, but what the user answered before still answers the warning (seen in testing: asked
            // again on each round of fixing other problems).
            b.placeholders.stream()
                    .filter(found -> before.containsKey(found.value()))
                    .forEach(found -> b.placeholderChoices.put(found.value(), before.get(found.value())));
            return false;
        }
        // A value whose fields are the only source of an element the schema requires, left out, leaves those
        // events without it (asked for by the user; seen in testing: "Leave out" recommended for '-' in the only
        // host field, EventSource/Device's). The sender can stand in for a Device; nothing for another element, so
        // such a value is kept as written, not asked about.
        final Map<String, List<String>> emptied = RequiredSources.emptied(b.mapping, b.schema, b.placeholders);
        final Map<String, List<String>> device = new LinkedHashMap<>();
        final Map<String, List<String>> keptForSchema = new LinkedHashMap<>();
        emptied.forEach((value, paths) -> (List.of(RequiredSources.DEVICE).equals(paths)
                ? device
                : keptForSchema).put(value, paths));
        final List<Placeholders.Found> unasked = b.placeholders.stream()
                .filter(found -> !before.containsKey(found.value()) && !keptForSchema.containsKey(found.value()))
                .toList();
        final Map<String, String> answers = unasked.isEmpty()
                ? Map.of()
                : PlaceholderChoices.byValue(unasked, consent.chooseEach(call, context, NAME, PlaceholderChoices.INTRO,
                        PlaceholderChoices.questions(unasked, PlaceholderChoices.OPTIONS, device)).orElse(Map.of()));
        final List<String> leaveOut = new ArrayList<>();
        final Set<String> fallBack = new LinkedHashSet<>();
        final List<String> applied = new ArrayList<>(NullSafe.list(b.applied));
        for (final Placeholders.Found found : b.placeholders) {
            if (keptForSchema.containsKey(found.value())) {
                b.placeholderChoices.put(found.value(), PlaceholderChoices.KEEP);
                applied.add("'" + found.value() + "' kept as written: its fields are the only source of "
                            + String.join(", ", keptForSchema.get(found.value())) + ", which the schema requires, "
                            + "so left out those events would fail validation");
                continue;
            }
            final String chosen = before.containsKey(found.value())
                    ? before.get(found.value())
                    : answers.get(found.value());
            if (chosen == null) {
                // No form: the warning is the agent's.
                if (device.containsKey(found.value())) {
                    TranslationBuilds.warnings(b.result).add("sample: '" + found.value() + "' is in the only "
                                                             + "fields EventSource/Device is written from: left "
                                                             + "out alone, those events have no Device, which the "
                                                             + "schema requires. Ask the user: keep it, or leave it "
                                                             + "out (nil_values) with meta_fallback ['RemoteHost', "
                                                             + "'RemoteAddress'] on its Device entry, the sender "
                                                             + "standing in");
                }
                continue;
            }
            b.placeholderChoices.put(found.value(), chosen);
            if (PlaceholderChoices.LEAVE_OUT.equals(chosen) || PlaceholderChoices.DEVICE_FALLBACK.equals(chosen)) {
                leaveOut.add(found.value());
            }
            if (PlaceholderChoices.DEVICE_FALLBACK.equals(chosen)) {
                fallBack.addAll(found.fields());
            }
        }
        // Kept now, as this call may yet fail before the XSLT is saved with them.
        UnsavedChoices.remember(context, request.target(), TranslationBuilds.PLACEHOLDERS_CHOSEN,
                b.placeholderChoices);
        b.applied = applied;
        if (leaveOut.isEmpty()) {
            return false;
        }
        b.mapping = PlaceholderChoices.leaveOut(b.mapping, leaveOut);
        leaveOut.forEach(value -> applied.add("'" + value + "' left out where a field holds it (nil_values), as "
                                              + "the user chose"));
        if (!fallBack.isEmpty()) {
            b.mapping = RequiredSources.withDeviceFallback(b.mapping, fallBack);
            applied.add("EventSource/Device written from the sender's name or address (meta_fallback RemoteHost, "
                        + "RemoteAddress) for a record whose " + String.join(", ", fallBack) + " holds none, as the "
                        + "user chose");
        }
        return true;
    }

    // What the user has chosen for the XSLT: what was saved with it, and what they chose since in this session, which
    // the XSLT may not have been saved with yet (see UnsavedChoices).
    private Map<String, String> agreedSoFar(final ToolCallContext context, final Request request, final String block) {
        final Map<String, String> agreed = new LinkedHashMap<>(builds.agreed(block, request.uuid(), request.build(),
                savedName(request)));
        agreed.putAll(UnsavedChoices.recalled(context, request.target(), block));
        return agreed;
    }

    // The warnings to the agent about what the user has answered (a domain kept on Ids, a stand-in for none), which
    // the sample's second check, after the mapping changed as the user chose, brings back (seen: 'N/A' kept, warned
    // about again once '-' was left out).
    private static void dropAnswered(final Build b) {
        final List<String> warnings = TranslationBuilds.warnings(b.result);
        b.placeholderChoices.keySet().forEach(value ->
                warnings.removeIf(warning -> warning.startsWith("sample: placeholder '" + value + "' in ")));
        NullSafe.list(b.qualified).stream()
                .filter(found -> DomainSplits.KEEP.equals(b.domainChoices.get(DomainSplits.key(found))))
                .forEach(found -> found.paths().forEach(path -> warnings.removeIf(warning -> warning.startsWith(
                        "sample: " + path + " takes field '" + found.field() + "', whose values are "
                        + "domain-qualified"))));
    }

    private static void withChanges(final Build b) {
        if (b.applied != null && !b.applied.isEmpty()) {
            b.result.put("changes_applied", b.applied);
        }
        if (b.domainNote != null) {
            b.result.put(DOMAIN_SPLIT_ARG, b.domainNote);
        }
        if (b.basedOn != null) {
            b.result.put("changes_applied_to", b.basedOn);
        }
        if (b.fromAnEarlierSession != null) {
            b.result.put("changes_applied_to_one", b.fromAnEarlierSession);
        }
        if (!Boolean.TRUE.equals(b.result.get("ok"))) {
            b.result.put("next_changes_apply_to", nextChangesBase(b.request,
                    TranslationBuilds.ON_THE_DRAFT.equals(b.basedOn)));
        }
    }

    // What the next changes= start from after a build that failed: this call's mapping, kept for its XSLT, not the
    // draft or the saved XSLT (seen in the chat 163 review: the agent couldn't tell, and re-sent whole mappings).
    private static String nextChangesBase(final Request request, final boolean onTheDraft) {
        if (request.target().isEmpty()) {
            return onTheDraft
                    ? "the session's draft again, as drafted: without uuid, or build and name, this call's changes "
                      + "aren't kept. Give build and name to keep them."
                    : "nothing: without uuid, or build and name, this call's mapping isn't kept. Give the whole "
                      + "mapping again, or build and name.";
        }
        return "this call's mapping (with any changes it applied), kept for " + (request.uuid() != null
                ? "XSLT " + request.uuid() + ": the next changes= with the same uuid"
                : "'" + (request.name() == null
                        ? request.group()
                        : request.name()) + "' in build "
                  + request.build() + ": the next changes= with the same build and " + (request.name() == null
                        ? "group"
                        : "name")) + " start from it, not from the draft or the saved XSLT.";
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
        } catch (final ToolRefusal | IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean ok(final Build b) {
        return Boolean.TRUE.equals(b.result.get("ok"));
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
    /// @param reviewed        What was given as reviewed, or null.
    /// @param change          What this change is, or null.
    /// @param discardHandEdit True when the user said to drop their hand edit.
    /// @param handEditChoices The user's answers about fields edited by hand, or null.
    /// @param group           A source type group's name, or null.
    /// @param sourceTypes     The group's source types given (none: the build's scope's).
    /// @param pipeline        For a group whose pipeline is promoted, change or new_version as the user chose
    ///                        where no form can be shown, or null.
    /// @param ownOutputFeed   For a new group, true when the user wants its Events in a feed of its own, false for
    ///                        the feed's Events; null when not given.
    /// @param references      The reference data a group's lookups read, or null for those the registry has.
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
                   Object reviewed,
                   String change,
                   boolean discardHandEdit,
                   Map<String, String> handEditChoices,
                   String group,
                   List<String> sourceTypes,
                   Boolean ownOutputFeed,
                   List<Map<String, Object>> references,
                   String domainSplit) {

        /// Reads a call's arguments.
        ///
        /// @param call The call.
        /// @param tool The tool, to read its arguments with.
        /// @return The call's request.
        /// @throws ToolRefusal if max_records is out of range, or the splitter isn't a spec.
        static Request of(final ToolCall call, final BuildTranslationXsltTool tool) {
            final Integer max = tool.intArg(call, MAX_RECORDS_ARG);
            if (max != null && (max < 1 || max > MAX_RECORDS)) {
                throw new ToolRefusal("max_records is 1 to " + MAX_RECORDS + ", not " + max);
            }
            return new Request(ToolArgs.raw(call, MAPPING_ARG),
                    ToolArgs.raw(call, CHANGES_ARG),
                    tool.stringArg(call, SCHEMA_VERSION_ARG),
                    stringListArg(call, FEEDS_ARG),
                    tool.stringArg(call, PIPELINE_UUID_ARG),
                    StartOnboardingTool.streamIds(call, STREAM_IDS_ARG),
                    max == null
                            ? DEFAULT_RECORDS
                            : max,
                    ToolArgs.flag(call, FIELD_MAPPING_ARG),
                    samples(ToolArgs.raw(call, SAMPLE_ARG)),
                    splitter(ToolArgs.raw(call, SPLITTER_ARG)),
                    tool.stringArg(call, BUILD_ARG),
                    tool.stringArg(call, NAME_ARG),
                    tool.stringArg(call, UUID_ARG),
                    ToolArgs.flag(call, INCLUDE_XSLT_ARG),
                    ToolArgs.raw(call, REVIEWED_ARG),
                    tool.stringArg(call, CHANGE_ARG),
                    ToolArgs.flag(call, DISCARD_HAND_EDIT_ARG),
                    TranslationArgs.handEditChoices(call),
                    tool.stringArg(call, GROUP_ARG),
                    stringListArg(call, SOURCE_TYPES_ARG),
                    ToolArgs.raw(call, OWN_OUTPUT_FEED_ARG) == null
                            ? null
                            : ToolArgs.flag(call, OWN_OUTPUT_FEED_ARG),
                    references(ToolArgs.raw(call, REFERENCES_ARG)),
                    DomainSplits.answerOf(tool.stringArg(call, DOMAIN_SPLIT_ARG)).orElse(null));
        }

        // The reference data a group's lookups read, as the registry keeps them; null when none were given.
        private static List<Map<String, Object>> references(final Object given) {
            if (given == null) {
                return null;
            }
            final List<Map<String, Object>> out = new ArrayList<>();
            for (final PipelineWrites.ReferenceRequest reference : PipelineWrites.ReferenceRequest.listOf(given,
                    REFERENCES_ARG)) {
                final Map<String, Object> kept = new LinkedHashMap<>();
                kept.put("feed", reference.feed());
                if (reference.loaderPipeline() != null) {
                    kept.put("loader_pipeline", reference.loaderPipeline());
                }
                if (reference.isContext()) {
                    kept.put("stream_type", reference.streamType());
                }
                out.add(kept);
            }
            return out;
        }

        /// @return The XSLT the mapping is sent for: its uuid, `build/name`, a source type group's given no name
        /// `build/group` (its XSLT is saved under the group's pipeline's name), or "".
        String target() {
            if (uuid != null) {
                return uuid;
            }
            // Seen in TestE2eAggregatedFeedWalks: a group's call with no name had no target, so the user's answer to
            // one form was kept nowhere, and two reviews were asked in turn for ever.
            final String named = NullSafe.isNonBlankString(name)
                    ? name
                    : group;
            return build != null && named != null
                    ? build + "/" + named
                    : "";
        }

        // The sample as given: one text, or a list of texts.
        private static List<String> samples(final Object given) {
            if (given == null) {
                return null;
            }
            if (given instanceof final List<?> list) {
                if (list.isEmpty()) {
                    // "sample": [] is no sample (seen in eval case 36: taken as one, 0 records were checked and the
                    // save returned ok).
                    return null;
                }
                return list.stream().map(item -> item == null
                        ? ""
                        : String.valueOf(item)).toList();
            }
            return List.of(String.valueOf(given));
        }

        /// @param given The splitter argument as build_data_splitter gave it (its spec), as JSON text or a map.
        /// @return The spec, or null if none was given.
        /// @throws ToolRefusal if it isn't a Data Splitter spec.
        @SuppressWarnings("unchecked")
        static SplitterSpec splitter(final Object given) {
            if (given == null) {
                return null;
            }
            try {
                final Object value = given instanceof final String text
                        ? ToolJson.loads(text)
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
        private List<LocalCheck.QualifiedIds> qualified;
        private Map<String, Object> sent;
        private final Map<String, String> domainChoices = new LinkedHashMap<>();
        private List<ConfirmedEventTypes.Lost> lost = List.of();
        // The contradictions of the catalogue's event types not yet confirmed, from the sample check; null before it.
        private List<String> contradictions;
        private final Map<String, String> confirmedChanges = new LinkedHashMap<>();
        private List<Placeholders.Found> placeholders;
        private final Map<String, String> placeholderChoices = new LinkedHashMap<>();
        // What the sample check read and worked out, for its check again once the user's answers change the mapping.
        private MappingSampleCheck.Earlier earlierCheck;
        private List<String> applied;
        private String basedOn;
        // Where a new group's Events go, when asked before the sample check (placementAsked).
        private SourceTypeGroups.Placement placement;
        private boolean placementAsked;
        // When the mapping changes= applied to came from an earlier session of the user's, kept with the build.
        private String fromAnEarlierSession;
        private String version;
        private String change;
        private EventSchema schema;
        private Map<String, Object> result;
        private Map<String, Object> saved;
        // A source type group's: how it is built, and its build.
        private SourceTypeGroups.Plan group;
        // What placing a group said of the arguments it was given (one ignored, say).
        private final List<String> placementNotes = new ArrayList<>();
        // Said when the domain question was answered by the feed's groups, not asked.
        private String domainNote;
        // The old/new lists written per property in the snapshots, in words (SnapshotChanges).
        private List<String> snapshotChanges = List.of();
        private String build;

        private Build(final Request request) {
            this.request = request;
            this.change = request.change();
        }
    }
}

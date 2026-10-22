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
import stroom.ai.impl.tools.AgentKnowledge;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.MergedPipeline.TranslationDoc;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.generation.MappingChangeException;
import stroom.ai.impl.tools.generation.MappingChanges;
import stroom.ai.impl.tools.generation.PythonRepr;
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.generation.XsltStyle;
import stroom.ai.impl.tools.indexing.Discovery;
import stroom.ai.impl.tools.indexing.EcsSchema;
import stroom.ai.impl.tools.indexing.ExampleNames;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.FieldPlans;
import stroom.ai.impl.tools.indexing.IndexTemplates;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.ai.impl.tools.indexing.IndexingRefusal;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.indexing.TemplateMappings;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/// `draft_index_mapping` - drafts the index for a build (the Stroom MCP server's `tools/indexing.py`
/// `draft_index_mapping`, with `_discovery_time`, `_records_rows`, `_draft_discovery`, `_amend_plan` and
/// `_replaced`): a field plan from the chosen convention, limited to the paths the sample Events populate, named
/// after the user's example index template where there is one, or a discovery index. Nothing is saved but the
/// example the user gave, kept in the build so the index template follows it.
class DraftIndexMappingTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DraftIndexMappingTool.class);

    static final String NAME = "draft_index_mapping";
    static final String BACKEND_ARG = "backend";
    static final String INDEX_NAME_ARG = "index_name";
    static final String CONVENTION_ARG = "convention";
    static final String EVENTS_STREAM_IDS_ARG = "events_stream_ids";
    static final String EXTRA_FIELDS_ARG = "extra_fields";
    static final String DROP_WHEN_ARG = "drop_when";
    static final String LIKE_INDEX_ARG = "like_index";
    static final String EXAMPLE_TEMPLATE_ARG = "example_template";
    static final String COMPONENT_TEMPLATES_ARG = "component_templates";
    static final String WITHOUT_COMPONENTS_ARG = "without_components";
    static final String SHARED_ARG = "shared";
    static final String DISCOVERY_ARG = "discovery";
    static final String WITHOUT_EXAMPLE_ARG = "without_example";
    static final String CONFIRMATION_ID_ARG = "confirmation_id";
    static final String REPLACES_ARG = "replaces";
    static final String CUSTOM_NAMESPACES_ARG = "custom_namespaces";
    static final String CHANGES_ARG = "changes";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";

    // At most this many fields of an action's own are planned by default.
    private static final int ACTION_FIELDS = 20;
    // The raw streams read for a discovery index's times, and how much of each.
    private static final int DISCOVERY_STREAMS = 3;
    private static final int DISCOVERY_CHARS = 100_000;
    private static final Pattern XML_START = Pattern.compile("(?=<\\?xml)");
    private static final String RECORDS = "records:2";

    private final AgentKnowledge knowledge;
    private final GetFieldConventionsTool conventions;
    private final IndexingInputs inputs;
    private final IndexPlans plans;
    private final IndexExamples examples;
    private final IndexingMemory memory;
    private final Consent consent;
    private final StreamReader streamReader;
    private final PipelineIndex pipelineIndex;
    private final PipelineShapes pipelineShapes;
    private final DocumentAccess documentAccess;

    @Inject
    DraftIndexMappingTool(final AgentKnowledge knowledge,
                          final GetFieldConventionsTool conventions,
                          final IndexingInputs inputs,
                          final IndexPlans plans,
                          final IndexExamples examples,
                          final IndexingMemory memory,
                          final Consent consent,
                          final StreamReader streamReader,
                          final PipelineIndex pipelineIndex,
                          final PipelineShapes pipelineShapes,
                          final DocumentAccess documentAccess) {
        this.knowledge = knowledge;
        this.conventions = conventions;
        this.inputs = inputs;
        this.plans = plans;
        this.examples = examples;
        this.memory = memory;
        this.consent = consent;
        this.streamReader = streamReader;
        this.pipelineIndex = pipelineIndex;
        this.pipelineShapes = pipelineShapes;
        this.documentAccess = documentAccess;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.INDEXING;
    }

    @Override
    public boolean isReadOnly() {
        // It keeps the user's example index template in the build (a Documentation doc), as the Stroom MCP server's
        // did; nothing else is saved.
        return false;
    }

    @Override
    public boolean confirmsItself() {
        // The user confirms a draft without an example, or following an existing index, in a form; keeping the
        // example they gave in the build is the tools' own bookkeeping.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .unlockedBy(AppPermission.VIEW_DATA_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("Reads the Events streams (and a discovery index's raw streams) as the user. The example index "
                      + "template is kept in the build folder, and an existing index to follow is read through "
                      + "Stroom with the index doc's cluster connection; nothing is written to Elasticsearch.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Draft the index for the build: a field plan (name, type and source path per field) "
                             + "from the chosen convention, limited to paths the sample events actually populate, "
                             + "plus StreamId and EventId. With the user's example Elasticsearch index template, "
                             + "fields take the example's names for the same data (User.Id, TypeId...), others are "
                             + "named in its style, and sample paths the example maps are added. Returns the plan "
                             + "rendered for the backend (Lucene field list or Elasticsearch index template) and a "
                             + "draft indexing XSLT in the output form that backend's indexing filter reads, leaving "
                             + "out events drop_when names. With discovery, a discovery index instead: nothing is "
                             + "read; raw JSON records are copied as they are and Elasticsearch maps their fields "
                             + "dynamically. Nothing is saved. The plan is kept for the session: amend it with "
                             + "changes= (only what changes), and save_xslt, create_index_doc and "
                             + "propose_index_template take it from there when not given.")
                .parameters(ObjectSchema.builder()
                        .addEnumProperty(BACKEND_ARG, FieldPlans.BACKENDS, "From the chosen indexing template "
                                                                          + "(find_pipeline_templates).")
                        .addStringProperty(INDEX_NAME_ARG, "Lucene index doc name, or ES index / data stream name.")
                        .addStringProperty(CONVENTION_ARG, "Convention profile the user chose "
                                                           + "(get_field_conventions); none for a discovery index.")
                        .addProperty(EVENTS_STREAM_IDS_ARG, stringArraySchema("Events streams from stage 1, to see "
                                                                              + "which event-logging paths are "
                                                                              + "actually populated. For a discovery "
                                                                              + "index: its raw streams, to find the "
                                                                              + "fields holding times."))
                        .addProperty(EXTRA_FIELDS_ARG, objects("Fields the user asked for beyond the convention's map "
                                                               + "(ECS: one of the source's own under its custom "
                                                               + "namespace). To add one to a plan already drafted: "
                                                               + "changes=, not a new draft."))
                        .addProperty(DROP_WHEN_ARG, stringArraySchema("XPath tests on an Event for events the user "
                                                                      + "wants kept out of the index, e.g. "
                                                                      + "\"EventDetail/TypeId = 'Heartbeat'\"; any "
                                                                      + "that holds drops the event."))
                        .addStringProperty(LIKE_INDEX_ARG, "Elasticsearch: an existing Elastic Index doc (uuid or "
                                                           + "exact name) to follow, when the user has no index "
                                                           + "template to paste. Stroom reads the index template its "
                                                           + "indices take and its component templates (else its "
                                                           + "field names and types), and they serve as the "
                                                           + "example.")
                        .addStringProperty(EXAMPLE_TEMPLATE_ARG, "Elasticsearch: the user's example index template "
                                                                 + "or index mapping (as for "
                                                                 + "propose_index_template); field names then follow "
                                                                 + "it.")
                        .addProperty(COMPONENT_TEMPLATES_ARG, stringArraySchema("Only if the example lists any in "
                                                                                + "composed_of: those component "
                                                                                + "templates, as the user gave them. "
                                                                                + "Kept for the session once given: "
                                                                                + "leave out those given before, "
                                                                                + "never send them again."))
                        .addProperty(WITHOUT_COMPONENTS_ARG, stringArraySchema("Only when the user can't give some "
                                                                               + "of the example's component "
                                                                               + "templates: their names. The "
                                                                               + "template is then built without "
                                                                               + "their fields, which it may repeat "
                                                                               + "or contradict."))
                        .addProperty(SHARED_ARG, objects("Named templates from shared XSLTs that sibling indexing "
                                                         + "XSLTs call (describe_template's shared_xslt): each writes "
                                                         + "a field (at, e.g. 'guid'), which the XSLT then does not "
                                                         + "write."))
                        .addProperty(DISCOVERY_ARG, ObjectSchema.builder()
                                .description("A discovery index instead: raw data (JSON, delimited text or XML) "
                                             + "indexed as it is into Elasticsearch, with no convention and no "
                                             + "Events. Give what the user confirmed: the input, the timestamp field "
                                             + "(and XML's record element), any stream meta to add, fields to drop.")
                                .build())
                        .addBooleanProperty(WITHOUT_EXAMPLE_ARG, "Elasticsearch: only when the user has no example "
                                                                 + "index template and no existing index to follow; "
                                                                 + "they confirm it in a form, and the convention "
                                                                 + "alone names the fields.")
                        .addStringProperty(CONFIRMATION_ID_ARG, "From an earlier needs_confirmation reply.")
                        .addStringProperty(REPLACES_ARG, "When this index replaces an existing one (e.g. its fields "
                                                         + "renamed to ECS): the old indexing XSLT's uuid, or its "
                                                         + "pipeline's. The reply lists the fields the old index "
                                                         + "wrote that this draft doesn't (replaced: with their "
                                                         + "sources and how often the sample populates them), to "
                                                         + "ask the user about; no need to read the old XSLT.")
                        .addProperty(CUSTOM_NAMESPACES_ARG, stringArraySchema("ECS: the namespace(s) for the "
                                                                              + "source's own fields, those ECS has "
                                                                              + "no field for, named after the "
                                                                              + "source or organisation, e.g. "
                                                                              + "'secretserver'. Fields of an "
                                                                              + "event's action element "
                                                                              + "(Authenticate/User/Id) are drafted "
                                                                              + "under the first; defaults to one "
                                                                              + "named after the index. Other "
                                                                              + "top-level names that aren't ECS's "
                                                                              + "are reported (ecs_check)."))
                        .addProperty(CHANGES_ARG, ObjectSchema.builder()
                                .description("Instead of drafting again, to amend the plan already drafted for "
                                             + "index_name in this session (or, with pipeline_uuid, the one kept "
                                             + "with that pipeline's indexing XSLT): only what changes. fields: each "
                                             + "replaces the field of that name whole, or is added; {\"name\": ..., "
                                             + "\"remove\": true} removes one; any other key (drop_when, "
                                             + "custom_namespaces...) replaces it. events_stream_ids, if given, "
                                             + "re-checks unmapped paths.")
                                .build())
                        .addStringProperty(PIPELINE_UUID_ARG, "With changes: the indexing pipeline whose kept plan to "
                                                              + "amend, when it wasn't drafted in this session.")
                        .required(BACKEND_ARG, INDEX_NAME_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(draft(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final IndexingRefusal | IllegalArgumentException e) {
            return JsonResults.refused(e.getMessage());
        }
    }

    // --------------------------------------------------------------------------------
    // The draft.
    // --------------------------------------------------------------------------------

    private Map<String, Object> draft(final ToolCall call, final ToolCallContext context) {
        final String backend = required(call, BACKEND_ARG);
        if (!FieldPlans.BACKENDS.contains(backend)) {
            throw new ToolRefusal("backend is 'lucene' or 'elasticsearch', not '" + backend + "'");
        }
        final String indexName = required(call, INDEX_NAME_ARG);
        final List<Long> streamIds = StartOnboardingTool.streamIds(call, EVENTS_STREAM_IDS_ARG);
        if (IndexingArgs.raw(call, CHANGES_ARG) != null) {
            return amend(call, indexName, streamIds);
        }
        final Discovery discovery = IndexingArgs.discovery(call, DISCOVERY_ARG);
        if (discovery != null) {
            final Object timed = discoveryTime(call, context, indexName, discovery, streamIds);
            if (timed instanceof final Discovery chosen) {
                return draftDiscovery(backend, indexName, chosen);
            }
            return castMap(timed);
        }
        if (!streamIds.isEmpty()) {
            // First, whatever else is asked: the streams must be Events (a Records stream, or a source's own data,
            // is refused with how to index it).
            inputs.requireEvents(streamIds);
        }
        final Draft draft = new Draft(call);
        final Optional<Map<String, Object>> asked = askNaming(call, context, backend, indexName, draft);
        if (asked.isPresent()) {
            return asked.get();
        }
        if (streamIds.isEmpty()) {
            throw new ToolRefusal("Give events_stream_ids: the Events streams the index will hold, to see which paths "
                                  + "they populate");
        }
        inputs.requireEvents(streamIds);
        final Map<String, Map<String, Object>> profiles = IndexingHelpers.conventions(knowledge);
        final String pasted = draft.exampleTemplate;
        if (draft.likeIndex != null && draft.exampleTemplate == null) {
            final Optional<Map<String, Object>> gate = follow(call, context, indexName, draft);
            if (gate.isPresent()) {
                return gate.get();
            }
        }
        if (draft.convention == null && draft.exampleTemplate != null) {
            // The example names the fields; a profile only says which event paths are worth indexing.
            draft.convention = profiles.containsKey("ecs")
                    ? "ecs"
                    : profiles.keySet().stream().findFirst().orElse(null);
        }
        if (!profiles.containsKey(draft.convention)) {
            throw new ToolRefusal("No convention profile '" + (draft.convention == null
                    ? "None"
                    : draft.convention) + "'; ask the user for an example index template (or an existing index to "
                                  + "follow), or a convention: get_field_conventions backend=elasticsearch");
        }
        return planned(call, backend, indexName, streamIds, profiles, draft, pasted);
    }

    // Elasticsearch without an example: the user is asked how fields are named, or confirms drafting from a
    // convention alone. Empty to go on.
    private Optional<Map<String, Object>> askNaming(final ToolCall call,
                                                    final ToolCallContext context,
                                                    final String backend,
                                                    final String indexName,
                                                    final Draft draft) {
        if (!FieldPlan.ELASTICSEARCH.equals(backend) || draft.exampleTemplate != null || draft.likeIndex != null) {
            return Optional.empty();
        }
        if (draft.convention != null && draft.convention.equals(draft.chosen.get("convention"))) {
            // The user chose this convention in get_field_conventions' form: asked once, not again.
            return Optional.empty();
        }
        // Names, types and structure come from what the environment already indexes: the agent asks the user, and
        // indexing from a convention alone is the user's call, made in a form, not the agent's.
        Map<String, Object> picked = Map.of();
        if (!IndexingArgs.flag(call, WITHOUT_EXAMPLE_ARG)) {
            final Map<String, Object> options = conventions.conventions(call, context, null,
                    FieldPlan.ELASTICSEARCH, null);
            // Answered in the form just now: drafted as the user chose (seen in VS Code: the user picked ECS here
            // too, and was told to be asked again, a third time, in the chat).
            picked = "chosen".equals(options.get("status"))
                    ? memory.namingChosen()
                    : Map.of();
            if (!picked.isEmpty()) {
                draft.chosen = picked;
            }
            if (picked.get("like_index") != null) {
                draft.likeIndex = String.valueOf(picked.get("like_index"));
            } else if (picked.get("convention") != null) {
                draft.convention = String.valueOf(picked.get("convention"));
            } else {
                final Map<String, Object> result = new LinkedHashMap<>(options);
                result.put("drafted", false);
                result.put("hint", "Not drafted: ask the user for their example first, offering these options. Then "
                                   + "draft_index_mapping with example_template=... (pasted) or like_index=<uuid>; "
                                   + "only if they have neither, with convention=... and without_example=true, which "
                                   + "they confirm.");
                return Optional.of(result);
            }
        }
        if (!picked.isEmpty()) {
            return Optional.empty();
        }
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("field names and types from", draft.convention != null
                ? "the '" + draft.convention + "' convention"
                : "no convention given");
        details.put("index template", "built from Elasticsearch defaults, not from an index you already have");
        details.put("instead", "paste an example index template, or name an existing index in Stroom to follow");
        return consent.require(call, context, Consent.Request.confirmation(NAME, "Index '" + indexName
                                                                                 + "' into Elasticsearch without an "
                                                                                 + "example index template", details,
                stringArg(call, CONFIRMATION_ID_ARG)));
    }

    // Following another source's index is the user's choice, as the example is. Its index template and component
    // templates, read by Stroom where it can (asked for by the user: pasting them was cumbersome); else its fields,
    // read through Stroom. Either way the user confirms what they'd get first. Empty to go on.
    private Optional<Map<String, Object>> follow(final ToolCall call,
                                                 final ToolCallContext context,
                                                 final String indexName,
                                                 final Draft draft) {
        final Optional<IndexExamples.FromIndex> fetched = examples.templateFromIndex(draft.likeIndex);
        final Map<String, Object> details = new LinkedHashMap<>();
        if (fetched.isPresent() && fetched.get().template() != null) {
            final IndexExamples.FromIndex read = fetched.get();
            draft.followed = read;
            draft.exampleTemplate = read.template();
            final List<Object> given = new ArrayList<>(read.components());
            given.addAll(draft.componentTemplates);
            draft.componentTemplates = given;
            final List<String> without = new ArrayList<>(draft.withoutComponents);
            without.addAll(read.missing());
            draft.withoutComponents = without;
            final List<String> parts = read.composedOf().stream().filter(n -> !read.missing().contains(n)).toList();
            details.put("follow", "Elastic Index doc '" + read.doc() + "'" + (NullSafe.isNonEmptyString(read.index())
                    ? " (index " + read.index() + ")"
                    : ""));
            details.put("read through Stroom", "its index template " + read.name() + (parts.isEmpty()
                    ? ""
                    : ", composed of " + String.join(", ", parts)));
            if (!read.unread().isEmpty()) {
                details.put("not readable through Stroom", "component templates "
                                                           + String.join(", ", new TreeSet<>(read.unread().keySet()))
                                                           + ": you'll be asked to paste them");
            }
            final List<String> readParts = new ArrayList<>(new TreeSet<>(parts.stream()
                    .filter(n -> !read.unread().containsKey(n))
                    .toList()));
            draft.likeNote = "followed Elastic Index doc '" + read.doc() + "': its index template " + read.name()
                             + (parts.isEmpty()
                    ? ""
                    : " and component templates " + PythonRepr.list(readParts)) + ", read through Stroom";
            draft.readDoc = read.doc();
        } else {
            final IndexExamples.FromFields read = examples.exampleFromIndex(draft.likeIndex);
            draft.exampleTemplate = read.template();
            draft.likeNote = read.note();
            draft.readDoc = read.doc();
            details.put("follow", "Elastic Index doc '" + read.doc() + "'" + (NullSafe.isNonEmptyString(read.index())
                    ? " (index " + read.index() + ")"
                    : ""));
            details.put("read through Stroom", read.fields() + " fields and their types, e.g. "
                                               + String.join(", ", read.examples().subList(0,
                    Math.min(3, read.examples().size()))));
            details.put("not readable through Stroom", "its settings, component templates, sub-fields and "
                                                       + "ignore_above: Elasticsearch defaults, unless you paste its "
                                                       + "index template");
            if (fetched.isPresent() && fetched.get().why() != null) {
                details.put("its index template", fetched.get().why());
                draft.likeNote += ". " + fetched.get().why();
            }
        }
        // Picked in get_field_conventions' form: the user isn't asked again (what can't be read through Stroom is
        // said in the result, for the agent to tell them).
        if (!draft.likeIndex.equals(draft.chosen.get("like_index"))) {
            final Optional<Map<String, Object>> gate = consent.require(call, context,
                    Consent.Request.confirmation(NAME, "Name the fields of index '" + indexName + "' after an "
                                                       + "existing index in Stroom", details,
                            stringArg(call, CONFIRMATION_ID_ARG)));
            if (gate.isPresent()) {
                return gate;
            }
        }
        if (draft.followed != null) {
            // Agreed: not asked again when the draft is called again with a component Stroom couldn't read.
            memory.chooseNaming(Map.of("like_index", draft.likeIndex));
            draft.chosen = memory.namingChosen();
        }
        if (draft.likeIndex.equals(draft.chosen.get("like_index")) && draft.followed == null) {
            draft.likeNote = (draft.likeNote == null
                    ? ""
                    : draft.likeNote + " ")
                             + "Not readable through Stroom, so tell the user: its index template itself (settings, "
                             + "component templates, keyword sub-fields, ignore_above), Elasticsearch defaults for "
                             + "those unless they paste it.";
        }
        return Optional.empty();
    }

    // The plan, from the convention's paths the sample populates, named after the example where there is one.
    private Map<String, Object> planned(final ToolCall call,
                                        final String backend,
                                        final String indexName,
                                        final List<Long> streamIds,
                                        final Map<String, Map<String, Object>> profiles,
                                        final Draft draft,
                                        final String pasted) {
        final Map<String, Object> profile = profiles.get(draft.convention);
        final Map<String, Object> events = inputs.summariseEvents(streamIds);
        final Map<String, Double> populated = IndexingInputs.population(events);
        final List<PlannedField> fields = new ArrayList<>(List.of(PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("EventId", "id", "@EventId")));
        final List<String> unused = new ArrayList<>();
        final List<String> mapped = new ArrayList<>();
        fieldMap(profile).forEach((path, spec) -> {
            if (draft.exampleTemplate != null && path.startsWith("EventDetail/*/")) {
                // Any action's Action or Outcome as one field (ECS's event.action): ECS's way, not the example's,
                // which names each action's own (Authenticate.Action).
                return;
            }
            // A name is planned once, from the first of its paths the sample populates (the client's address,
            // else the source address of whichever Network action the event records).
            final String name = String.valueOf(spec.get("name"));
            if (holds(path, populated) && fields.stream().noneMatch(f -> f.name().equals(name))) {
                fields.add(new PlannedField(name, String.valueOf(spec.get("type")), path, null,
                        spec.get("transform") == null
                                ? null
                                : String.valueOf(spec.get("transform")), false));
                mapped.add(path);
            } else if (holds(path, populated)) {
                mapped.add(path);
            } else {
                unused.add(path);
            }
        });
        final EcsSchema ecs = plans.ecs();
        final List<String> namespaces = new ArrayList<>(IndexingArgs.strings(call, CUSTOM_NAMESPACES_ARG));
        final String own = actionNamespace(draft, indexName, ecs, namespaces);
        actionFields(populated, fields, mapped, "nested".equals(profile.get("structure")), own);
        final List<String> ecsData = ecsDataFields(draft, events, fields, ecs);
        final List<String> exampleNotes = new ArrayList<>();
        boolean subobjects = true;
        if (draft.exampleTemplate != null) {
            final Object named = fromExample(draft, profiles, populated, fields, exampleNotes);
            if (named instanceof final Map<?, ?> wanted) {
                return castMap(wanted);
            }
            subobjects = !Boolean.FALSE.equals(named);
        }
        for (final PlannedField extra : IndexingArgs.fields(call, EXTRA_FIELDS_ARG)) {
            if (fields.stream().noneMatch(f -> f.name().equals(extra.name()))) {
                fields.add(extra);
            }
        }
        String timeField = fields.stream()
                .filter(f -> "EventTime/TimeCreated".equals(f.source()))
                .map(PlannedField::name)
                .findFirst()
                .orElse("EventTime");
        if (FieldPlan.ELASTICSEARCH.equals(backend) && fields.stream().noneMatch(f -> "@timestamp".equals(f.name()))) {
            fields.add(PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"));
            timeField = "@timestamp";
        }
        final List<SharedTemplate> shared = IndexingArgs.shared(call, SHARED_ARG);
        sharedFields(shared, fields, exampleNotes);
        if ("ecs".equals(draft.convention) && draft.exampleTemplate != null && ecs != null) {
            // The user's example names the fields: its top-level names are theirs, declared, not reported as un-ECS.
            final Set<String> theirs = new TreeSet<>();
            fields.forEach(f -> theirs.add(f.name().split("\\.", -1)[0]));
            theirs.removeAll(ecs.topLevel());
            theirs.removeAll(namespaces);
            theirs.removeAll(Set.of("StreamId", "EventId"));
            namespaces.addAll(theirs);
        }
        FieldPlan plan = new FieldPlan(backend, indexName, timeField, fields,
                IndexingArgs.strings(call, DROP_WHEN_ARG), null, List.of(), shared, subobjects, draft.convention,
                "ecs".equals(draft.convention)
                        ? namespaces
                        : List.of(), XsltStyle.DEFAULT);
        if ((pasted != null || draft.likeIndex != null) && FieldPlan.ELASTICSEARCH.equals(backend)) {
            keepExample(draft, indexName, streamIds.getFirst(), pasted, exampleNotes);
        }
        plan = plans.withCardinality(plan);
        final String replaces = stringArg(call, REPLACES_ARG);
        final Map<String, Object> replaced = replaces == null
                ? null
                : replaced(replaces, plan, populated);
        final Map<String, Object> reply = new LinkedHashMap<>(plans.reply(plan, populated));
        // No XSLT here: save_xslt index_plan= generates it, and half the reply was code the agent never reads.
        final Map<String, Object> result = new LinkedHashMap<>();
        if (replaced != null) {
            result.put("replaced", replaced);
        }
        for (final String key : List.of("plan", "problems", "rendered")) {
            result.put(key, reply.remove(key));
        }
        result.put("convention_paths_not_in_sample", unused);
        result.putAll(reply);
        if (draft.exampleTemplate != null || !shared.isEmpty()) {
            result.put("from_example", exampleNotes);
        }
        if (!ecsData.isEmpty()) {
            result.put("ecs_data_elements", ecsData);
        }
        return result;
    }

    // ECS has no place for an action's own elements (Authenticate/User/Id): they go under the source's namespace,
    // as ECS asks, not a top-level group per action (seen: authenticate.*, authorise.*, copy.* in an ECS plan).
    private static String actionNamespace(final Draft draft,
                                          final String indexName,
                                          final EcsSchema ecs,
                                          final List<String> namespaces) {
        if (!"ecs".equals(draft.convention) || draft.exampleTemplate != null || ecs == null) {
            return null;
        }
        final List<String> clash = namespaces.stream().filter(n -> ecs.topLevel().contains(n)).toList();
        if (!clash.isEmpty()) {
            throw new ToolRefusal("custom_namespaces " + PythonRepr.list(clash) + ": ECS's own top-level names, so "
                                  + "not a namespace of the source's: name it after the source or organisation (e.g. "
                                  + ecs.sourceNamespace(indexName) + ")");
        }
        if (namespaces.isEmpty()) {
            namespaces.add(ecs.sourceNamespace(indexName));
        }
        return namespaces.getFirst();
    }

    // What happened, from the event's action element (Alert's Type and Severity, Authenticate's Action and
    // Outcome, Process, Update), planned by default: a user asked for these each time. Network's are the profile's.
    private static void actionFields(final Map<String, Double> populated,
                                     final List<PlannedField> fields,
                                     final List<String> mapped,
                                     final boolean nested,
                                     final String own) {
        int added = 0;
        for (final String path : new TreeMap<>(populated).keySet()) {
            if (added >= ACTION_FIELDS || !holds(path, populated) || !path.startsWith("EventDetail/")
                || List.of(path.split("/", -1)).contains("Data") || path.startsWith("EventDetail/Network/")
                || ExampleNames.fieldGroup(path) == null
                || fields.stream().anyMatch(f -> FieldPlans.sourceMatches(f.source(), path))
                || mapped.stream().anyMatch(m -> FieldPlans.sourceMatches(m, path))) {
                continue;
            }
            final Set<String> taken = new LinkedHashSet<>();
            fields.forEach(f -> taken.add(f.name()));
            String name = nested
                    ? ExampleNames.nestedName(path, "ecs")
                    : ExampleNames.derive(path, "pascal", false, taken);
            name = name != null && own != null
                    ? own + "." + name
                    : name;
            if (name == null || name.isEmpty() || taken.contains(name)) {
                continue;
            }
            final String type = path.endsWith("/Success")
                    ? "boolean"
                    : path.endsWith("/Port")
                            ? "long"
                            : "keyword";
            fields.add(PlannedField.of(name, type, path));
            added++;
        }
    }

    // ECS: a Data element whose name is an ECS field's, written another way (source_ip, SourceIp as source.ip),
    // under that name and ECS's type. A draft for the user to review: abbreviations (src_ip) are not guessed.
    private static List<String> ecsDataFields(final Draft draft,
                                              final Map<String, Object> events,
                                              final List<PlannedField> fields,
                                              final EcsSchema ecs) {
        final List<String> ecsData = new ArrayList<>();
        if (!"ecs".equals(draft.convention) || ecs == null
            || !(events.get("data_names") instanceof final Map<?, ?> dataNames)) {
            return ecsData;
        }
        dataNames.forEach((path, names) -> {
            if (!(names instanceof final Map<?, ?> byName)) {
                return;
            }
            for (final Object item : byName.keySet()) {
                final String dataName = String.valueOf(item);
                final String ecsName = ecs.forDataName(dataName);
                if (ecsName != null && !dataName.contains("'")
                    && fields.stream().noneMatch(f -> f.name().equals(ecsName))) {
                    fields.add(PlannedField.of(ecsName, ecs.planType(ecsName),
                            path + "[@Name='" + dataName + "']/@Value"));
                    ecsData.add("Data '" + dataName + "' (" + path + ") as ECS's " + ecsName);
                }
            }
        });
        return ecsData;
    }

    // The fields named after the example: a needs_components reply while its components aren't given, else
    // whether the index takes subobjects (false only when the example says so).
    private Object fromExample(final Draft draft,
                               final Map<String, Map<String, Object>> profiles,
                               final Map<String, Double> populated,
                               final List<PlannedField> fields,
                               final List<String> exampleNotes) {
        final Map<String, Object> example = IndexTemplates.parseTemplate(draft.exampleTemplate).body();
        final Map<String, Map<String, Object>> components = examples.givenComponents(draft.componentTemplates,
                strings(example.get("composed_of")), draft.exampleTemplate);
        final Map<String, Object> wanted = IndexingHelpers.componentsWanted(example, components,
                draft.withoutComponents);
        if (wanted != null) {
            if (draft.followed != null && !draft.followed.unread().isEmpty()) {
                wanted.put("hint", "Stroom read the index template of '" + draft.followed.doc() + "' but not the "
                                   + "component templates " + PythonRepr.list(new TreeSet<>(
                        draft.followed.unread().keySet())) + " (Elasticsearch: "
                                   + draft.followed.unread().values().iterator().next() + "). " + wanted.get("hint"));
            }
            return wanted;
        }
        final Map<String, Object> composed = TemplateMappings.compose(example, components).body();
        final Map<String, Set<String>> known = new LinkedHashMap<>();
        profiles.values().forEach(other -> fieldMap(other).forEach((path, spec) ->
                known.computeIfAbsent(path, k -> new LinkedHashSet<>()).add(String.valueOf(spec.get("name")))));
        final List<String> sample = new ArrayList<>(new TreeSet<>(populated.keySet().stream()
                .filter(path -> holds(path, populated))
                .toList()));
        final ExampleNames.Named named = ExampleNames.namesFromExample(fields.stream().map(PlannedField::toJson)
                .toList(), TemplateMappings.readMappingFields(composed), known, sample);
        fields.clear();
        named.fields().forEach(field -> fields.add(PlannedField.fromJson(field)));
        exampleNotes.addAll(named.notes());
        if (draft.likeNote != null) {
            exampleNotes.addFirst(draft.likeNote);
        }
        // Documents are written nested whatever the example; subobjects: false changes how the index maps them.
        if (composed.get("template") instanceof final Map<?, ?> template
            && template.get("mappings") instanceof final Map<?, ?> mappings
            && Boolean.FALSE.equals(mappings.get("subobjects"))) {
            exampleNotes.add("the example sets subobjects: false: the index maps each dotted name (user.id) as a "
                             + "field of its own; documents are still written nested");
            return false;
        }
        return true;
    }

    // The shared template writes the fields: they stay in the plan (the index maps them) but the XSLT calls the
    // template instead of writing them, so each is written once. Which fields, read from the shared XSLT itself,
    // found by name as Stroom resolves the import.
    private void sharedFields(final List<SharedTemplate> shared,
                              final List<PlannedField> fields,
                              final List<String> exampleNotes) {
        for (final SharedTemplate use : shared) {
            final Optional<XsltDoc> found = pipelineIndex.list(XsltDoc.TYPE).stream()
                    .filter(ref -> use.href().equals(ref.getName()))
                    .findFirst()
                    // Listed, but gone or unreadable: as if not found.
                    .flatMap(ref -> documentAccess.read(ref, XsltDoc.class));
            final Map<String, String> values = new LinkedHashMap<>();
            found.map(doc -> SharedXslt.jsonValues(NullSafe.string(doc.getData()), use.template()))
                    .orElse(Map.of())
                    .forEach((key, element) -> {
                        if (key.equals(use.at()) || key.startsWith(use.at() + ".")) {
                            values.put(key, element);
                        }
                    });
            if (values.isEmpty()) {
                values.put(use.at(), "string");
            }
            values.forEach((name, element) -> {
                if (fields.stream().noneMatch(f -> f.name().equals(name))) {
                    fields.add(PlannedField.of(name, "number".equals(element)
                            ? "long"
                            : "boolean".equals(element)
                                    ? "boolean"
                                    : "keyword", "shared:" + use.template()));
                }
            });
            exampleNotes.add(PythonRepr.list(new TreeSet<>(values.keySet())) + ": written by the shared template "
                             + use.template() + " (" + use.href() + ")" + (found.isPresent()
                    ? ""
                    : "; not found as an XSLT document, so typed as a keyword"));
        }
    }

    // Followed from an existing index too: seen in testing, the user chose to follow one (nothing to paste), and
    // propose_index_template, given no example, asked them to paste one.
    private void keepExample(final Draft draft,
                             final String indexName,
                             final long firstStream,
                             final String pasted,
                             final List<String> exampleNotes) {
        final Optional<String> build = inputs.buildOfStream(firstStream);
        if (build.isEmpty()) {
            return;
        }
        try {
            final List<String> components = pasted == null && draft.followed == null
                    ? List.of()
                    : draft.componentTemplates.stream()
                            .map(c -> c instanceof final String text
                                    ? text
                                    : PyJson.dumps(c))
                            .toList();
            final String how;
            if (pasted != null) {
                how = IndexExamples.PASTED;
            } else if (draft.followed != null) {
                how = "read through Stroom from Elastic Index doc '" + draft.readDoc + "', which the user chose to "
                      + "follow: its index template and component templates";
            } else {
                how = "read through Stroom from Elastic Index doc '" + draft.readDoc + "', which the user chose to "
                      + "follow: " + IndexExamples.FIELDS_ONLY;
            }
            examples.keepExample(build.get(), indexName, pasted != null
                    ? pasted
                    : draft.exampleTemplate, components, how);
            exampleNotes.add("the example is kept in build " + build.get() + " ('" + indexName
                             + IndexExamples.EXAMPLE_SUFFIX + "'): propose_index_template follows it even if it "
                             + "isn't given again");
        } catch (final RuntimeException e) {
            // Keeping it is a convenience; the plan stands without it.
            LOGGER.warn(() -> "Couldn't keep the example index template in build " + build.get() + ": "
                              + e.getMessage());
        }
    }

    // --------------------------------------------------------------------------------
    // A discovery index.
    // --------------------------------------------------------------------------------

    /// The discovery index's time field, confirmed by the user when the records hold more than one field of times
    /// (asked for by the user: it becomes @timestamp, and with it whether the index is a data stream), or when the
    /// one proposed holds no times. The answer is kept for the session, so the user isn't asked again.
    ///
    /// @return The discovery to draft, or the reply asking which field.
    private Object discoveryTime(final ToolCall call,
                                 final ToolCallContext context,
                                 final String indexName,
                                 final Discovery discovery,
                                 final List<Long> streamIds) {
        if (streamIds.isEmpty()) {
            return discovery;
        }
        final StringBuilder read = new StringBuilder();
        for (final long streamId : streamIds.subList(0, Math.min(DISCOVERY_STREAMS, streamIds.size()))) {
            try {
                read.append(stripTrailingNewlines(streamReader.readHead(streamId, 0, DISCOVERY_CHARS).text()))
                        .append('\n');
            } catch (final RuntimeException e) {
                // A stream that can't be read: the others serve.
                LOGGER.debug(() -> "Stream " + streamId + " not read: " + e.getMessage());
            }
        }
        String text = read.toString();
        final String rows = recordsRows(text);
        if (rows != null) {
            // records:2 (a Records stream): each data element is a field by its name, as the delimited input reads
            // them (seen: a Records stream profiled as XML offered only 'data@value', and the agent indexed
            // name/value lists).
            if (!"delimited".equals(discovery.input())) {
                throw new ToolRefusal("These streams hold records:2 records (a Data Splitter's output, a Records "
                                      + "stream): draft with discovery={'input': 'delimited', 'timestamp_field': <a "
                                      + "data name>}, which indexes each data element as a field by its name, on an "
                                      + "indexing template with an XMLParser.");
            }
            text = rows;
        }
        final Optional<String> remembered = memory.timeChosen(indexName);
        if (remembered.isPresent()) {
            return withTimestamp(discovery, remembered.get());
        }
        if (discovery.timestampChosenByUser()) {
            return discovery;
        }
        final List<String> candidates = new ArrayList<>();
        try {
            if (!text.isBlank() && SampleProfiler.profile(text).get("fields") instanceof final List<?> fields) {
                for (final Object item : fields) {
                    if (item instanceof final Map<?, ?> field && field.get("field") != null
                        && String.valueOf(field.get("type")).startsWith("timestamp")) {
                        candidates.add(String.valueOf(field.get("field")));
                    }
                }
            }
        } catch (final RuntimeException e) {
            return discovery;
        }
        final String proposed = discovery.timestampField();
        if (candidates.isEmpty() || candidates.equals(List.of(proposed))) {
            return discovery;
        }
        final List<String> ordered = new ArrayList<>();
        if (candidates.contains(proposed)) {
            ordered.add(proposed);
        }
        candidates.stream().filter(c -> !c.equals(proposed)).forEach(ordered::add);
        final Map<String, String> labels = new LinkedHashMap<>();
        ordered.forEach(c -> labels.put(c.equals(proposed)
                ? c + " (proposed)"
                : c, c));
        final Optional<String> chosen = consent.choose(call, context, NAME, "Which field holds each record's time? It "
                                                                            + "is indexed as @timestamp.",
                List.copyOf(labels.keySet()), null);
        if (chosen.isPresent()) {
            final String field = labels.get(chosen.get());
            memory.chooseTime(indexName, field);
            return withTimestamp(discovery, field);
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "needs_guidance");
        result.put("timestamp_candidates", ordered);
        result.put("hint", "The records hold times in " + ordered.size() + " fields (" + String.join(", ", ordered)
                           + ")" + (candidates.contains(proposed)
                ? ""
                : ", and '" + proposed + "' isn't one of them")
                           + ": ask the user which one is each record's time (it becomes @timestamp), in that order, "
                           + "recommending none. Then draft_index_mapping again with discovery.timestamp_field= their "
                           + "choice and discovery.timestamp_chosen_by_user=true.");
        return result;
    }

    /// records:2 records as JSON lines, one object of data name to value per record.
    ///
    /// @param text Streams' text.
    /// @return The rows, or null for any other text.
    static String recordsRows(final String text) {
        if (!text.substring(0, Math.min(2000, text.length())).contains(RECORDS)) {
            return null;
        }
        final List<String> rows = new ArrayList<>();
        for (final String chunk : XML_START.split(text)) {
            if (chunk.isBlank()) {
                continue;
            }
            XmlDocs.parse(chunk.strip()).ifPresent(root -> {
                for (final Element record : XmlDocs.descendantsAndSelf(root)) {
                    if (!XmlDocs.is(record, RECORDS, "record")) {
                        continue;
                    }
                    final Map<String, Object> row = new LinkedHashMap<>();
                    for (final Element data : XmlDocs.descendantsAndSelf(record)) {
                        if (XmlDocs.is(data, RECORDS, "data") && NullSafe.isNonEmptyString(data.getAttribute("name"))) {
                            row.put(data.getAttribute("name"), data.hasAttribute("value")
                                    ? data.getAttribute("value")
                                    : null);
                        }
                    }
                    if (!row.isEmpty()) {
                        rows.add(PyJson.dumps(row));
                    }
                }
            });
        }
        return rows.isEmpty()
                ? null
                : String.join("\n", rows) + "\n";
    }

    // Nothing is read: Elasticsearch maps the source's fields as documents arrive. The fields Stroom needs are the
    // only explicit ones.
    private Map<String, Object> draftDiscovery(final String backend,
                                               final String indexName,
                                               final Discovery discovery) {
        if (!FieldPlan.ELASTICSEARCH.equals(backend)) {
            throw new ToolRefusal("A discovery index is Elasticsearch: its fields are mapped dynamically as documents "
                                  + "arrive");
        }
        final FieldPlan plan = FieldPlan.forDiscovery(indexName, discovery);
        final String xslt;
        try {
            xslt = plan.xslt();
        } catch (final IllegalArgumentException e) {
            throw new ToolRefusal(e.getMessage());
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("plan", plan.toJson());
        result.put("xslt", xslt);
        result.put("rendered", plan.elasticTemplate(indexName, plans.ecs()));
        result.put("hint", "Save the XSLT with save_xslt index_plan=plan and no code, create_indexing_pipeline from "
                           + "the discovery template, step_sample on the raw streams (the documents show the source's "
                           + "fields), then propose_index_template with the plan and the user's example template for "
                           + "its settings.");
        return result;
    }

    // --------------------------------------------------------------------------------
    // Amending a plan, and the index it replaces.
    // --------------------------------------------------------------------------------

    // draft_index_mapping changes=: the kept plan with only what changes merged in.
    private Map<String, Object> amend(final ToolCall call, final String indexName, final List<Long> streamIds) {
        final Object changes;
        try {
            changes = IndexingArgs.object(call, CHANGES_ARG);
        } catch (final ToolRefusal e) {
            throw new ToolRefusal(e.getMessage());
        }
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        final Map<String, FieldPlan> drafted = plans.drafted();
        FieldPlan base = drafted.get(indexName);
        if (base == null && pipelineUuid != null) {
            base = plans.given(null, List.of(), pipelineUuid);
        }
        if (base == null) {
            final List<String> names = new ArrayList<>(new TreeSet<>(drafted.keySet()));
            throw new ToolRefusal("No plan for '" + indexName + "' to amend: none was drafted for it in this session"
                                  + (pipelineUuid != null
                    ? ""
                    : " (give pipeline_uuid= the indexing pipeline whose saved XSLT keeps one)")
                                  + ". Drafted in this session: " + (names.isEmpty()
                    ? "none"
                    : PythonRepr.list(names)) + ". Else draft it whole.");
        }
        final MappingChanges.Applied applied;
        try {
            applied = MappingChanges.applyPlanChanges(base.toJson(), changes);
        } catch (final MappingChangeException e) {
            throw new ToolRefusal(e.getMessage());
        }
        final FieldPlan plan;
        try {
            plan = FieldPlan.fromJson(applied.merged());
        } catch (final IllegalArgumentException e) {
            throw new ToolRefusal("The amended plan isn't valid: " + e.getMessage());
        }
        Map<String, Double> populated = null;
        if (!streamIds.isEmpty()) {
            inputs.requireEvents(streamIds);
            populated = IndexingInputs.population(inputs.summariseEvents(streamIds));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("changed", applied.done());
        result.putAll(plans.reply(plans.withCardinality(plan), populated));
        return result;
    }

    /// What the index being replaced wrote that the draft doesn't. Seen in VS Code: asked to make an indexing
    /// pipeline write ECS, the agent compared its draft with the old XSLT by reading describe_document's output
    /// from a file its client spilled it to, and its model fell into a loop.
    ///
    /// @param replaces  The old indexing XSLT's uuid, or its pipeline's.
    /// @param plan      The draft.
    /// @param populated How often the sample populates each path.
    /// @return What the old index wrote that the draft doesn't.
    /// @throws ToolRefusal if neither an XSLT nor a pipeline running one can be read.
    Map<String, Object> replaced(final String replaces, final FieldPlan plan, final Map<String, Double> populated) {
        Optional<XsltDoc> doc = documentAccess.read(new DocRef(XsltDoc.TYPE, replaces), XsltDoc.class)
                .filter(xslt -> NullSafe.isNonEmptyString(xslt.getData()));
        if (doc.isEmpty()) {
            // A pipeline's uuid: its indexing XSLT.
            final List<TranslationDoc> entries;
            try {
                entries = pipelineShapes.merged(replaces).translationDocs(replaces).stream()
                        .filter(entry -> XsltDoc.TYPE.equals(entry.doc().getType()))
                        .toList();
            } catch (final RuntimeException e) {
                throw new ToolRefusal("replaces=" + replaces + ": neither an XSLT nor a pipeline this server can read ("
                                      + e.getMessage() + ")");
            }
            if (entries.isEmpty()) {
                throw new ToolRefusal("replaces=" + replaces + ": the pipeline runs no XSLT");
            }
            doc = documentAccess.read(entries.getLast().doc(), XsltDoc.class);
        }
        final XsltDoc xslt = doc.orElseThrow(() -> new ToolRefusal("replaces=" + replaces + ": its XSLT can't be "
                                                                   + "read"));
        return IndexingHelpers.replaced(xslt.getName(), xslt.getDescription(), xslt.getData(), plan, populated);
    }

    // --------------------------------------------------------------------------------
    // Helpers.
    // --------------------------------------------------------------------------------

    private static boolean holds(final String path, final Map<String, Double> populated) {
        final Double share = FieldPlans.populationOf(path, populated);
        return share != null && share != 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> fieldMap(final Map<String, Object> profile) {
        final Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (profile.get("field_map") instanceof final Map<?, ?> fields) {
            fields.forEach((path, spec) -> {
                if (spec instanceof final Map<?, ?> given) {
                    map.put(String.valueOf(path), (Map<String, Object>) given);
                }
            });
        }
        return map;
    }

    private static Discovery withTimestamp(final Discovery discovery, final String field) {
        return new Discovery(discovery.input(), discovery.record(), field, discovery.timestampChosenByUser(),
                discovery.timestampFormat(), discovery.meta(), discovery.drop(), discovery.unpackJson(),
                discovery.ignoreAbove(), discovery.totalFieldsLimit());
    }

    private static String stripTrailingNewlines(final String text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '\n') {
            end--;
        }
        return text.substring(0, end);
    }

    private static List<String> strings(final Object value) {
        return value instanceof final List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(final Object value) {
        return (Map<String, Object>) value;
    }

    private static ArraySchema objects(final String description) {
        return ArraySchema.builder()
                .description(description)
                .items(ObjectSchema.builder().build())
                .build();
    }

    private String required(final ToolCall call, final String name) {
        final String value = stringArg(call, name);
        if (value == null) {
            throw new ToolRefusal("Give " + name);
        }
        return value;
    }


    // --------------------------------------------------------------------------------


    // What a draft goes by, as the user's choices and the example settle it.
    private final class Draft {

        private String convention;
        private String likeIndex;
        private String exampleTemplate;
        private List<Object> componentTemplates;
        private List<String> withoutComponents;
        private Map<String, Object> chosen;
        private IndexExamples.FromIndex followed;
        private String likeNote;
        private String readDoc;

        private Draft(final ToolCall call) {
            convention = stringArg(call, CONVENTION_ARG);
            likeIndex = stringArg(call, LIKE_INDEX_ARG);
            exampleTemplate = rawArg(call, EXAMPLE_TEMPLATE_ARG) == null
                              || rawArg(call, EXAMPLE_TEMPLATE_ARG).isBlank()
                    ? null
                    : rawArg(call, EXAMPLE_TEMPLATE_ARG);
            final Object given = IndexingArgs.raw(call, COMPONENT_TEMPLATES_ARG);
            componentTemplates = given instanceof final List<?> list
                    ? new ArrayList<>(list)
                    : given == null
                            ? new ArrayList<>()
                            : new ArrayList<>(List.of(given));
            withoutComponents = IndexingArgs.strings(call, WITHOUT_COMPONENTS_ARG);
            chosen = memory.namingChosen();
        }
    }
}

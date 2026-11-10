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

import stroom.ai.impl.tools.AgentKnowledge;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.fielddoc.FieldDocs;
import stroom.ai.impl.tools.fielddoc.IndexFieldDocs;
import stroom.ai.impl.tools.indexing.EcsSchema;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.xmlschema.shared.XmlSchemaDoc;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/// The index plans the indexing tools share: the plan a tool was given, else the one kept with the pipeline's indexing
/// XSLT, else the one drafted in the session; a plan's repeatable fields marked from the event-logging schema; and what
/// a drafted or amended plan comes back as. save_xslt takes the plans drafted in the session from here.
class IndexPlans {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexPlans.class);
    private static final String STATUS = "status";
    private static final String NEEDS_SHOWING = "needs_showing";
    private static final String TABLE = "table";

    private final IndexingMemory memory;
    private final KeptMappings keptMappings;
    private final HeldSchemas heldSchemas;
    private final AgentKnowledge knowledge;

    /// @param memory       Keeps the plans drafted in the session.
    /// @param keptMappings Reads the plan kept with a pipeline's indexing XSLT.
    /// @param heldSchemas  Gives the event-logging schema, for the fields that hold several values.
    /// @param knowledge    Bundles the ECS schema.
    @Inject
    IndexPlans(final IndexingMemory memory,
               final KeptMappings keptMappings,
               final HeldSchemas heldSchemas,
               final AgentKnowledge knowledge) {
        this.memory = memory;
        this.keptMappings = keptMappings;
        this.heldSchemas = heldSchemas;
        this.knowledge = knowledge;
    }

    /// The plan a tool was given, else the one kept with the pipeline's indexing XSLT (what it writes), else the
    /// one drafted in this session for one of the index names (or the only one drafted, with no name to go by).
    ///
    /// @param plan         The plan given, or null.
    /// @param indexNames   The index names to go by (nulls ignored).
    /// @param pipelineUuid The indexing pipeline, or null.
    /// @return The plan, or null if there is none to go by.
    FieldPlan given(final FieldPlan plan, final List<String> indexNames, final String pipelineUuid) {
        if (plan != null) {
            return plan;
        }
        return IndexingHelpers.planGiven(null, kept(pipelineUuid).orElse(null), drafted(), indexNames);
    }

    /// @param pipelineUuid An indexing pipeline, or null.
    /// @return The plan kept with its own XSLT, if it has one (none for a pipeline that can't be read).
    Optional<FieldPlan> kept(final String pipelineUuid) {
        if (NullSafe.isBlankString(pipelineUuid)) {
            return Optional.empty();
        }
        try {
            final Optional<KeptMapping.Kept> kept = keptMappings.keptMapping(pipelineUuid);
            return kept.filter(mapping -> "index".equals(mapping.kind()))
                    .map(mapping -> FieldPlan.fromJson(mapping.payload()));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No plan read from pipeline {}: {}", pipelineUuid, e.getMessage()));
            return Optional.empty();
        }
    }

    /// @return The plans draft_index_mapping drafted or amended in this session, by index name (one that no
    /// longer reads as a plan is left out).
    Map<String, FieldPlan> drafted() {
        final Map<String, FieldPlan> out = new LinkedHashMap<>();
        memory.plans().forEach((index, json) -> {
            try {
                out.put(index, FieldPlan.fromJson(json));
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Drafted plan for {} not read: {}", index, e.getMessage()));
            }
        });
        return out;
    }

    /// The plan with each field whose source can hold several values in one event (a repeatable element on its
    /// path, by the event-logging schema: Data, say) marked multiple, so the indexing XSLT writes them all. Seen
    /// (asked about by the user): written with value-of, two ports were indexed as the one number "80 443", two
    /// tags as "a b". Unmarked when the schema can't be read: the fields are then written as single values, as
    /// before.
    ///
    /// @param plan A plan.
    /// @return It, its repeatable fields marked.
    FieldPlan withCardinality(final FieldPlan plan) {
        if (plan.discovery() != null) {
            return plan;
        }
        final EventSchema schema = eventSchema(plan);
        return schema == null
                ? plan
                : IndexingHelpers.withCardinality(plan, schema);
    }

    // The schema of the Events a plan reads: for a fork's namespace, the fork's own (it may add elements, e.g. an
    // activity of its own); else the newest event-logging version held, not yet the build's chosen one (which the
    // translation tools keep). Null for a Stroom without it.
    private EventSchema eventSchema(final FieldPlan plan) {
        try {
            if (plan.eventsNamespace() != null) {
                final List<XmlSchemaDoc> forks = heldSchemas.forksIn(plan.eventsNamespace());
                return forks.isEmpty()
                        ? null
                        : heldSchemas.eventSchema(forks.getFirst());
            }
            return heldSchemas.eventSchema(heldSchemas.chosenOrLatest(null));
        } catch (final RuntimeException e) {
            LOGGER.info(() -> LogUtil.message("Plan's fields not checked against the schema: {}", e.getMessage()));
            return null;
        }
    }

    /// The plan with the convention it was drafted under, when the agent's copy left it out (it decides whether the
    /// template composes ecs@mappings): taken from the plan kept with the pipeline, or drafted this session for the
    /// index.
    ///
    /// @param plan         The plan given.
    /// @param pipelineUuid The indexing pipeline.
    /// @return The plan, with the convention it was drafted under.
    FieldPlan withConvention(final FieldPlan plan, final String pipelineUuid) {
        if (NullSafe.isNonBlankString(plan.convention())) {
            return plan;
        }
        final FieldPlan kept;
        try {
            kept = given(null, List.of(plan.indexName()), pipelineUuid);
        } catch (final RuntimeException e) {
            // Nothing kept to go by: the plan as given.
            return plan;
        }
        return IndexingHelpers.withConvention(plan, kept);
    }

    /// What a drafted or amended plan comes back as; the plan is kept for the session, for changes= and the tools
    /// that take a plan. Seen in Ask Stroom AI: after the client's step cap the agent had lost its earlier
    /// extra_fields and spent eight calls recovering them, and pasted the 50-field plan inline five times over.
    ///
    /// @param plan      The plan.
    /// @param populated How often the sample populates each event path (%), or null when no streams were read.
    /// @return `plan`, `problems`, `rendered`, with the sample `populated_paths_not_mapped`, `fields_not_in_sample`
    /// (if any) and `field_mapping`, `ecs_check` when it departs from ECS, and `hint`.
    /// @throws ToolRefusal if a name is a value and also an object, which no template can map; the plan kept before
    /// stays.
    Map<String, Object> reply(final FieldPlan plan, final Map<String, Double> populated) {
        return reply(plan, populated, source -> false);
    }

    /// As [#reply(FieldPlan, Map)], with what the translation writes: a field whose source no event of the sample
    /// has, but the translation that wrote the Events writes, is `fields_rare` (kept: indexed whenever an event has
    /// it), not `fields_not_in_sample`. Seen in testing: a byte count the Events held early in their stream, missed
    /// by the events read spread over it, was said to be one the index would never get, and to leave out.
    ///
    /// @param plan      The plan.
    /// @param populated How often the sample populates each event path (%), or null when no streams were read.
    /// @param written   Whether the translation writes a source path.
    /// @return As [#reply(FieldPlan, Map)], with `fields_rare` where there are any.
    Map<String, Object> reply(final FieldPlan plan,
                              final Map<String, Double> populated,
                              final Predicate<String> written) {
        // Refused before it is kept: kept, every later change= failed to render it (seen in Ask Stroom AI).
        final List<String> clashes = plan.valueAndObjectClashes();
        if (!clashes.isEmpty()) {
            throw new ToolRefusal(String.join("; ", clashes) + ". Nothing was kept: call again with one of each "
                                  + "renamed (e.g. acme.ssl.bypass_reason beside acme.ssl.bypass), or without it.");
        }
        final EcsSchema ecs = ecs();
        final Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("plan", plan.toJson());
        reply.put("problems", plan.required());
        reply.put("rendered", FieldPlan.LUCENE.equals(plan.backend())
                ? plan.luceneFields()
                : plan.elasticTemplate(plan.indexName(), ecs));
        // Kept once it renders, so a plan that can't be shown never replaces the last good one.
        try {
            memory.keepPlan(plan);
        } catch (final RuntimeException e) {
            // Kept for convenience; the plan is in the reply all the same.
            LOGGER.debug(() -> LogUtil.message("Plan not kept: {}", e.getMessage()));
        }
        if (populated != null) {
            // Network paths once, whichever action: one field for a source address, not one per Permit and Deny.
            reply.put("populated_paths_not_mapped", IndexingHelpers.populatedNotMapped(plan, populated));
            final List<String> empty = IndexingHelpers.plannedNotPopulated(plan, populated);
            final List<String> rare = empty.stream()
                    .filter(field -> written.test(field.substring(field.indexOf(" <- ") + " <- ".length())))
                    .toList();
            final List<String> missing = empty.stream().filter(field -> !rare.contains(field)).toList();
            if (!missing.isEmpty()) {
                reply.put("fields_not_in_sample", missing);
            }
            if (!rare.isEmpty()) {
                reply.put("fields_rare", rare);
            }
            reply.put("field_mapping", IndexFieldDocs.indexFieldMappingMarkdown(plan, populated, null, null,
                    FieldDocs.BY_EVENT_TYPE, null));
        }
        if (plan.discovery() == null) {
            final List<String> unknown = IndexingHelpers.plannedNotInSchema(plan, eventSchema(plan));
            if (!unknown.isEmpty()) {
                reply.put("fields_not_in_schema", unknown);
            }
        }
        final List<String> departures = conventionProblems(plan, ecs);
        if (!departures.isEmpty()) {
            reply.put("ecs_check", departures);
        }
        reply.put("hint", IndexingHelpers.PLAN_HINT + (reply.containsKey("fields_not_in_sample")
                ? " fields_not_in_sample: no event read has their source, and the translation doesn't write it (the "
                  + "tools read part of each stream, so this is no proof the data never has it): ask the user, for "
                  + "each, whether to leave it out (changes= with remove), have the translation write it first (if "
                  + "the source's data has it), or keep it (if the data has it on events the sample didn't reach)."
                : "") + (reply.containsKey("fields_rare")
                ? " fields_rare: the translation writes their source, though no event read has it (a rare one): "
                  + "they stay in the plan, indexed whenever an event has it; nothing to change."
                : "") + (reply.containsKey("ecs_check")
                ? " ecs_check: the plan mixes names ECS has no place for with ECS's own: ask the user, for each, "
                  + "whether to rename it (its ECS name, or under the source's own namespace) or keep it as it is "
                  + "(custom_namespaces), before saving. Seen in testing: flat and ECS names mixed in one template."
                : "") + (reply.containsKey("fields_not_in_schema")
                ? " fields_not_in_schema: the Events schema has no element at their source, so no Event can "
                  + "hold it: correct the source to a path the Events have (summarise_streams' path_population), or "
                  + "leave the field out."
                : ""));
        return reply;
    }

    /// A drafted or amended plan's result as the user is to see it: `status: needs_showing` and a `table` of its
    /// fields (name, type and source path), which Stroom AI's chat shows the user itself, before the agent asks
    /// them to agree to anything built from it (seen in testing: the agent asked the user to approve an index of
    /// fields it had never shown them).
    ///
    /// @param result      The result: one holding a `plan` and no `status` of its own (anything else is left as
    ///                    it is).
    /// @param shownByChat Whether Stroom AI's chat shows the table itself; else the agent is told to show it.
    /// @return The result, with the table first and the hint saying who shows it.
    static Map<String, Object> withShownPlan(final Map<String, Object> result, final boolean shownByChat) {
        if (result == null || result.containsKey(STATUS) || !(result.get("plan") instanceof final Map<?, ?> json)) {
            return result;
        }
        final FieldPlan plan;
        try {
            plan = FieldPlan.fromJson(BuildRecords.asMap(json));
        } catch (final RuntimeException e) {
            // Shown as it was: the plan in the result is still the agent's to show.
            LOGGER.debug(() -> LogUtil.message("Plan not shown as a table: {}", e.getMessage()));
            return result;
        }
        final Map<String, Object> shown = new LinkedHashMap<>();
        shown.put(STATUS, NEEDS_SHOWING);
        shown.put(TABLE, fieldTable(plan));
        shown.putAll(result);
        final String said = shownByChat
                ? "The chat shows the user the plan's fields (table) itself, as it is: don't repeat or restyle it; "
                  + "refer to it when you ask them about it. "
                : "Show the user the plan's fields (table) in your reply, as it is, whole (Markdown your client "
                  + "renders), before asking them about it or creating the index. ";
        shown.put("hint", said + NullSafe.string(NullSafe.get(result.get("hint"), String::valueOf)));
        return shown;
    }

    /// @param plan An index plan.
    /// @return Its fields as a Markdown table: a row each (an object field's parts each a row of their own), with
    /// its name, type and source path.
    static String fieldTable(final FieldPlan plan) {
        final List<String> lines = new ArrayList<>(List.of(
                "## Index fields planned for '" + plan.indexName() + "'", "",
                "| # | Field | Type | Source path |", "| --- | --- | --- | --- |"));
        final List<PlannedField> fields = plan.leafFields();
        for (int i = 0; i < fields.size(); i++) {
            final PlannedField field = fields.get(i);
            lines.add("| " + (i + 1) + " | " + cell(field.name()) + " | " + NullSafe.string(field.type()) + " | "
                      + (JsonValues.truthy(field.source())
                    ? cell(field.source())
                    : "-") + " |");
        }
        return String.join("\n", lines);
    }

    // A value in a table cell: as code, its pipes escaped.
    private static String cell(final String value) {
        return "`" + NullSafe.string(value).replace("`", "'").replace("|", "\\|").replace("\n", " ") + "`";
    }

    /// @return The bundled ECS schema, or null if none is bundled.
    EcsSchema ecs() {
        return EcsSchema.from(knowledge).orElse(null);
    }

    /// @param plan A plan.
    /// @param ecs  The ECS schema, or null.
    /// @return Where the plan departs from its convention (none without the ECS schema).
    static List<String> conventionProblems(final FieldPlan plan, final EcsSchema ecs) {
        return ecs == null
                ? List.of()
                : plan.conventionProblems(ecs);
    }

    // --------------------------------------------------------------------------------


}

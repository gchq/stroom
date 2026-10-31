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
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.indexing.EcsSchema;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.FieldPlans;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// `get_field_conventions` - the field naming conventions for indexes: without a name it puts the choice to the user
/// (the backend, then for Elasticsearch their own example first), in a form where the session can show one, and never
/// picks a convention itself; with a name, the profile's field map and the fields of its reference index docs.
class GetFieldConventionsTool extends AbstractBuiltInTool {

    static final String NAME = "get_field_conventions";
    static final String NAME_ARG = "name";
    static final String BACKEND_ARG = "backend";
    static final String ECS_FIELDS_ARG = "ecs_fields";
    /// What each convention profile is shown as.
    static final Map<String, String> PROFILE_LABELS = Map.of("ecs", "ECS (Elastic Common Schema)",
            "stroom-flat", "Stroom flat");
    /// The question asked about the backend.
    static final String BACKEND_QUESTION = "Which backend should the new index use?";
    /// The question asked about the naming.
    static final String NAMING_QUESTION = "How should the new index's fields be named?";

    // How many Elastic Index docs are offered to follow, and how many fields of a reference index are read.
    private static final int EXISTING = 60;
    private static final int REFERENCE_FIELDS = 500;
    private static final String FOLLOW = "follow an existing index in Stroom";
    private static final String EXAMPLE = "example index template";

    private final AgentKnowledge knowledge;
    private final PipelineTemplates pipelineTemplates;
    private final PipelineIndex pipelineIndex;
    private final DocumentAccess documentAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final Consent consent;
    private final IndexingMemory memory;
    private final BuildPlan plan;

    @Inject
    GetFieldConventionsTool(final AgentKnowledge knowledge,
                            final PipelineTemplates pipelineTemplates,
                            final PipelineIndex pipelineIndex,
                            final DocumentAccess documentAccess,
                            final Provider<QueryService> queryServiceProvider,
                            final Consent consent,
                            final IndexingMemory memory,
                            final BuildPlan plan) {
        this.knowledge = knowledge;
        this.pipelineTemplates = pipelineTemplates;
        this.pipelineIndex = pipelineIndex;
        this.documentAccess = documentAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.consent = consent;
        this.memory = memory;
        this.plan = plan;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.INDEXING;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .note("Lists the indexing templates and Elastic Index docs the user can view, and the fields of a "
                      + "profile's reference index docs as Stroom lists them for the user.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Field naming conventions for indexes. Without a name this lists the profiles and "
                             + "returns needs_guidance: the agent must ask the user which convention to follow, point "
                             + "at reference index docs, or describe one. It never picks a convention itself. With a "
                             + "name it returns the profile's field map plus the fields and types of its reference "
                             + "index docs (Lucene Index or Elastic Index docs, read through Stroom).")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(NAME_ARG, "Convention profile to use; omit to list them.")
                        .addEnumProperty(BACKEND_ARG, FieldPlans.BACKENDS, "The index's backend: for "
                                                                          + "Elasticsearch, the user's example comes "
                                                                          + "first (an index template, or an existing "
                                                                          + "index in Stroom), a convention profile "
                                                                          + "only without one.")
                        .addStringProperty(ECS_FIELDS_ARG, "With name=ecs: the ECS fields under a field set or prefix "
                                                           + "(e.g. 'process', 'user.target'), each with its type and "
                                                           + "what it holds, to name a field ECS's way.")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            final String backend = stringArg(call, BACKEND_ARG);
            if (backend != null && !FieldPlans.BACKENDS.contains(backend)) {
                return JsonResults.refused("backend is 'lucene' or 'elasticsearch', not '" + backend + "'");
            }
            final String name = stringArg(call, NAME_ARG);
            if (name == null && backend == null) {
                final Optional<Map<String, Object>> notYet = notYet(context);
                if (notYet.isPresent()) {
                    return JsonResults.of(notYet.get());
                }
            }
            return JsonResults.of(conventions(call, context, name, backend,
                    stringArg(call, ECS_FIELDS_ARG)));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final RuntimeException e) {
            return JsonResults.refused(StreamSummaries.reason(e));
        }
    }

    // The backend is stage 2's question: until the session's build has Events (its events pipeline processed the
    // sample), it isn't asked. Seen in testing: onboarding a CSV source, the agent's first question was the indexing
    // backend.
    private Optional<Map<String, Object>> notYet(final ToolCallContext context) {
        final Optional<Map<String, Object>> next = plan.currentBuild(context).flatMap(plan::nextBeforeEvents);
        return next.map(step -> {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "not_yet");
            result.put("next", step);
            result.put("hint", "The indexing backend, cluster and field naming are stage 2's decisions, and the "
                               + "build has no Events yet (its events pipeline is at step '" + step.get("step")
                               + "'). Don't ask the user about indexing now: carry on with next, and call "
                               + "get_field_conventions when next reaches the index step. Only if the user has "
                               + "already chosen the backend, call again with backend= their answer.");
            return result;
        });
    }

    /// The conventions, as get_field_conventions gives them; draft_index_mapping puts the same choice to the user.
    ///
    /// @param call    The call asking (its id tells its forms apart).
    /// @param context The call's context.
    /// @param name    The profile asked for, or null.
    /// @param backend The index's backend, or null.
    /// @param prefix  With name=ecs, the ECS field set or prefix to list, or null.
    /// @return The result: `needs_guidance` with the choices, `chosen` with what the user picked in a form, or the
    /// profile.
    /// @throws ToolRefusal for a profile that doesn't exist.
    Map<String, Object> conventions(final ToolCall call,
                                    final ToolCallContext context,
                                    final String name,
                                    final String backend,
                                    final String prefix) {
        final Map<String, Map<String, Object>> profiles = IndexingHelpers.conventions(knowledge);
        String chosenBackend = backend;
        List<Map<String, Object>> templates = null;
        boolean asked = false;
        if (name == null && backend == null) {
            // Seen: an agent asked without the backend, got the profiles, then looked the backend up and asked
            // again. When every indexing template has the same backend, that's the index's backend.
            final Set<String> backends = new LinkedHashSet<>();
            final List<Map<String, Object>> found = indexingTemplates();
            found.forEach(candidate -> {
                if (candidate.get("backend") instanceof final String kind && !kind.isEmpty()) {
                    backends.add(kind);
                }
            });
            if (backends.size() == 1) {
                chosenBackend = backends.iterator().next();
                templates = found.stream().map(GetFieldConventionsTool::template).toList();
            }
            if (chosenBackend == null && backends.containsAll(FieldPlans.BACKENDS)) {
                // Asked here, in a form, and the naming after it: seen in Ask Stroom AI, the agent, given the
                // profiles before the backend, asked the user both in the chat, and draft_index_mapping asked the
                // naming again in its own form (the agent's word isn't the user's).
                final Map<String, String> labels = new LinkedHashMap<>();
                labels.put("Elasticsearch", FieldPlan.ELASTICSEARCH);
                labels.put("Lucene (kept inside Stroom, no cluster)", FieldPlan.LUCENE);
                final Optional<String> picked = consent.choose(call, context, NAME, BACKEND_QUESTION,
                        List.copyOf(labels.keySet()), null);
                if (picked.isEmpty()) {
                    final Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", "needs_guidance");
                    result.put("question", BACKEND_QUESTION);
                    result.put("options", List.copyOf(labels.keySet()));
                    result.put("hint", "Ask the user only which backend, Elasticsearch or Lucene, and call again with "
                                       + "backend= their answer. Don't ask how fields are named yet: the choices for "
                                       + "that follow from the backend (for Elasticsearch, the user's own index "
                                       + "template first), and the server asks them.");
                    return result;
                }
                chosenBackend = labels.get(picked.get());
                asked = true;
                // The chosen backend's templates come back with the naming, so they needn't be looked up.
                final String kind = chosenBackend;
                templates = found.stream()
                        .filter(candidate -> kind.equals(candidate.get("backend")))
                        .map(GetFieldConventionsTool::template)
                        .toList();
            }
        }
        if (name == null && FieldPlan.ELASTICSEARCH.equals(chosenBackend)) {
            return elasticsearchNaming(call, context, profiles, templates);
        }
        if (name == null) {
            return noProfile(call, context, profiles, chosenBackend, templates, asked);
        }
        if (!profiles.containsKey(name)) {
            throw new ToolRefusal("No convention profile '" + name + "'. Profiles: " + (profiles.isEmpty()
                    ? "none"
                    : String.join(", ", profiles.keySet())));
        }
        return profile(name, profiles.get(name), prefix);
    }

    // Field names, types and structure come from what the environment already indexes: the user's example.
    private Map<String, Object> elasticsearchNaming(final ToolCall call,
                                                    final ToolCallContext context,
                                                    final Map<String, Map<String, Object>> profiles,
                                                    final List<Map<String, Object>> templates) {
        final List<Map<String, Object>> existing = new ArrayList<>();
        final List<DocRef> indexes = pipelineIndex.list(ElasticIndexDoc.TYPE);
        for (final DocRef ref : indexes.subList(0, Math.min(EXISTING, indexes.size()))) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", ref.getName());
            entry.put("uuid", ref.getUuid());
            entry.put("path", PipelineIndex.path(documentAccess, ref));
            existing.add(entry);
        }
        // One option per choice the user is offered, in order, each with the label to show: an agent offered
        // "follow an existing index", ECS and Stroom flat, and dropped the template the user had.
        final List<Map<String, Object>> options = new ArrayList<>();
        options.add(option("From an index template", EXAMPLE, "The user pastes the Elasticsearch index template a "
                                                              + "similar source's index uses (Kibana Dev Tools: GET "
                                                              + "_index_template/<name>, or GET <index>/_mapping), "
                                                              + "with any component templates, into the chat: ask for "
                                                              + "it there and end your turn, as a choice form cannot "
                                                              + "carry it. Once pasted: draft_index_mapping "
                                                              + "example_template= it, exactly as given, and keep it "
                                                              + "for propose_index_template. The only choice that "
                                                              + "follows the template's settings and components."));
        final Map<String, Object> follow = option("Follow an existing index in Stroom", FOLLOW, "The user picks one "
                + "of existing_indexes (a similar source's): draft_index_mapping like_index=<its uuid>, with nothing "
                + "to paste. Stroom reads the index template its indices take and its component templates where the "
                + "cluster's credentials allow, else its field names and Elasticsearch types only (then the "
                + "template's settings and components are pasted, the first choice, if wanted). The user confirms "
                + "what was read in a form.");
        follow.put("existing_indexes", existing);
        options.add(follow);
        profiles.forEach((profileName, profile) -> {
            final Map<String, Object> option = new LinkedHashMap<>();
            option.put("choice", PROFILE_LABELS.getOrDefault(profileName, profileName) + " convention");
            option.put("option", "convention profile " + profileName);
            option.put("description", profile.get("description"));
            option.put("how", "Only when the user has no example: draft_index_mapping convention=" + profileName
                              + " without_example=true, which the user confirms in a form.");
            options.add(option);
        });
        final List<String> choices = options.stream().map(o -> String.valueOf(o.get("choice"))).toList();
        final Optional<String> chosen = consent.choose(call, context, NAME, NAMING_QUESTION, choices, null);
        if (chosen.isPresent()) {
            return chosenNaming(call, context, options, existing, templates, chosen.get());
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "needs_guidance");
        result.put("options", options);
        if (templates != null) {
            result.put("backend", "elasticsearch (every indexing template is an Elasticsearch one)");
            result.put("indexing_templates", templates);
        }
        result.put("hint", "Ask the user which, with exactly these " + options.size() + " choices, in this order and "
                           + "with these labels (" + String.join("; ", choices) + "), and recommend none: it is "
                           + "their call, not yours. Draft nothing until they answer; if they choose the index "
                           + "template, wait for them to paste it. like_index and without_example are confirmed by "
                           + "the user in a form.");
        return result;
    }

    // What the user settled in the forms, so it isn't asked again (seen in testing: the user chose Elasticsearch and
    // ECS in these forms, and the agent, told only the naming, asked the backend again with an example template to
    // follow, in its own form).
    private static String settled(final String backend,
                                  final String chosen,
                                  final List<String> others,
                                  final String open) {
        return "The user chose the backend (" + backend + ") and how fields are named (" + chosen + ") in forms: "
               + "don't ask about either again, nor offer the other naming choices (" + String.join("; ", others)
               + "), in ask_user or in the chat. " + open;
    }

    private Map<String, Object> chosenNaming(final ToolCall call,
                                             final ToolCallContext context,
                                             final List<Map<String, Object>> options,
                                             final List<Map<String, Object>> existing,
                                             final List<Map<String, Object>> templates,
                                             final String chosen) {
        final Map<String, Object> option = options.stream()
                .filter(o -> chosen.equals(o.get("choice")))
                .findFirst()
                .orElseThrow();
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "chosen");
        result.put("choice", chosen);
        result.put("how", option.get("how"));
        // What is settled, so it isn't asked again (seen in testing: the user chose Elasticsearch and ECS in these
        // forms, and the agent, told only the naming, asked the backend again with an example template to follow, in
        // its own form).
        result.put("backend", FieldPlan.ELASTICSEARCH);
        if (templates != null) {
            result.put("indexing_templates", templates);
        }
        result.put("settled", settled("Elasticsearch", chosen, options.stream()
                .map(o -> String.valueOf(o.get("choice")))
                .filter(other -> !other.equals(chosen))
                .toList(), "Still open: the cluster, where several are in use (create_index_doc says)."));
        final String kind = String.valueOf(option.get("option"));
        if (FOLLOW.equals(kind) && !existing.isEmpty()) {
            final Map<String, Map<String, Object>> labels = new LinkedHashMap<>();
            existing.forEach(e -> labels.put(e.get("name") + " (" + e.get("path") + ")", e));
            final Map<String, Object> pick = existing.size() == 1
                    ? existing.getFirst()
                    : consent.choose(call, context, NAME, "Which existing index should it follow?",
                            List.copyOf(labels.keySet()), null).map(labels::get).orElse(null);
            if (pick != null) {
                memory.chooseNaming(Map.of("like_index", pick.get("uuid")));
                result.put("like_index", pick.get("uuid"));
                result.put("hint", "The user chose to follow '" + pick.get("name") + "': draft_index_mapping "
                                   + "like_index=" + pick.get("uuid") + " (with the events streams); not asked "
                                   + "again.");
            }
        } else if (EXAMPLE.equals(kind)) {
            result.put("hint", "Ask the user to paste the index template (and any component templates) into the chat, "
                               + "and end your turn: a form can't carry it. Then draft_index_mapping "
                               + "example_template= it, exactly as given.");
        } else if (kind.startsWith("convention profile ")) {
            final String profile = kind.substring("convention profile ".length());
            memory.chooseNaming(Map.of("convention", profile));
            result.put("hint", "draft_index_mapping convention=" + profile + " (with the events streams): the user "
                               + "chose it in the form, so it isn't asked again.");
        }
        return result;
    }

    // Without the backend, an agent took this for the whole choice and went for a convention, though the user had
    // their own index template (seen in VS Code).
    private Map<String, Object> noProfile(final ToolCall call,
                                          final ToolCallContext context,
                                          final Map<String, Map<String, Object>> profiles,
                                          final String backend,
                                          final List<Map<String, Object>> templates,
                                          final boolean asked) {
        final String first;
        if (asked) {
            first = "";
        } else if (templates != null) {
            first = "The backend is " + backend + ": every indexing template is a " + backend + " one. ";
        } else if (backend != null) {
            first = "";
        } else {
            first = "Which backend first (find_pipeline_templates stage=indexing says): for Elasticsearch, call again "
                    + "with backend=elasticsearch, whose choices start with the user's own index template; these "
                    + "profiles are for Lucene, or for Elasticsearch only when the user has no template. ";
        }
        if (FieldPlan.LUCENE.equals(backend) && !profiles.isEmpty()) {
            final Map<String, String> labels = new LinkedHashMap<>();
            profiles.keySet().forEach(n -> labels.put(PROFILE_LABELS.getOrDefault(n, n) + " convention", n));
            final Optional<String> chosen = consent.choose(call, context, NAME, NAMING_QUESTION,
                    List.copyOf(labels.keySet()), null);
            if (chosen.isPresent()) {
                final String profile = labels.get(chosen.get());
                // Remembered, as for Elasticsearch: draft_index_mapping convention= doesn't ask it again.
                memory.chooseNaming(Map.of("convention", profile));
                final Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "chosen");
                result.put("choice", chosen.get());
                result.put("convention", profile);
                result.put("backend", FieldPlan.LUCENE);
                if (templates != null) {
                    result.put("indexing_templates", templates);
                }
                result.put("settled", settled("Lucene", chosen.get(), labels.keySet().stream()
                        .filter(other -> !other.equals(chosen.get()))
                        .toList(), "Lucene keeps the index in Stroom, so there is no cluster to choose."));
                result.put("hint", "get_field_conventions name=" + profile + " for its field map, then "
                                   + "draft_index_mapping convention=" + profile + " (with the events streams): the "
                                   + "user chose it in the form, so it isn't asked again.");
                return result;
            }
        }
        final Map<String, Object> described = new LinkedHashMap<>();
        profiles.forEach((n, profile) -> described.put(n, profile.get("description")));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "needs_guidance");
        result.put("profiles", described);
        if (templates != null) {
            result.put("indexing_templates", templates);
        }
        result.put("hint", first + "Ask the user which convention to use, which existing index docs to follow, or how "
                           + "fields should be named. Do not assume one.");
        return result;
    }

    private Map<String, Object> profile(final String name, final Map<String, Object> profile, final String prefix) {
        final Map<String, Object> reference = new LinkedHashMap<>();
        if (profile.get("reference_index_docs") instanceof final List<?> docs) {
            for (final Object item : docs) {
                referenceFields(String.valueOf(item), reference);
            }
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        out.put("profile", profile);
        out.put("reference_fields", reference);
        final Optional<EcsSchema> ecs = EcsSchema.from(knowledge);
        if ("ecs".equals(name) && ecs.isPresent()) {
            // The full schema, as Elastic publishes it: plans following ECS are checked against it.
            final Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("version", ecs.get().version());
            schema.put("field_sets", ecs.get().fieldSets());
            schema.put("note", "Names in these field sets must be ECS fields (draft_index_mapping and save_xslt check "
                               + "them); a field of the user's own goes outside them. ecs_fields='<set>' lists a "
                               + "set.");
            out.put("ecs", schema);
            if (NullSafe.isNonBlankString(prefix)) {
                out.put("ecs_fields", ecs.get().fieldsIn(prefix, 200));
            }
        }
        return out;
    }

    // The fields Stroom has for each index doc of the name: one Stroom can't list is said, the profile still given.
    private void referenceFields(final String docName, final Map<String, Object> reference) {
        for (final String type : List.of(LuceneIndexDoc.TYPE, ElasticIndexDoc.TYPE)) {
            for (final DocRef ref : pipelineIndex.list(type)) {
                if (!docName.equals(ref.getName())) {
                    continue;
                }
                final String key = type + " " + docName;
                try {
                    final ResultPage<QueryField> found = queryServiceProvider.get().findFields(new FindFieldCriteria(
                            new PageRequest(0, REFERENCE_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, ref, null,
                            null));
                    final Map<String, Object> fields = new LinkedHashMap<>();
                    for (final QueryField field : NullSafe.list(NullSafe.get(found, ResultPage::getValues))) {
                        if (NullSafe.isNonEmptyString(field.getFldName())) {
                            fields.put(field.getFldName(), field.getFldType() == null
                                    ? null
                                    : field.getFldType().name());
                        }
                    }
                    reference.put(key, fields);
                } catch (final RuntimeException e) {
                    reference.put(key, Map.of("(unreadable)", StreamSummaries.reason(e)));
                }
            }
        }
    }

    // The indexing templates (the lookup is a shortcut; without it, the agent is told to find the backend itself).
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> indexingTemplates() {
        try {
            final Object candidates = pipelineTemplates.find("indexing").get("candidates");
            return candidates instanceof final List<?> list
                    ? (List<Map<String, Object>>) list
                    : List.of();
        } catch (final RuntimeException e) {
            return List.of();
        }
    }

    private static Map<String, Object> template(final Map<String, Object> candidate) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        for (final String key : List.of("uuid", "name", "path", "backend")) {
            entry.put(key, candidate.get(key));
        }
        return entry;
    }

    private static Map<String, Object> option(final String choice, final String option, final String how) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("choice", choice);
        entry.put("option", option);
        entry.put("how", how);
        return entry;
    }
}

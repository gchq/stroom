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

package stroom.ai.impl.tools.indexing;

import stroom.ai.impl.tools.AgentKnowledge;
import stroom.ai.impl.tools.Globs;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.KeptMapping.Kept;
import stroom.ai.impl.tools.generation.PythonRepr;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.TranslationDescriptions;
import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.ai.impl.tools.validation.XmlTrees.NotWellFormedException;
import stroom.util.yaml.YamlUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/// The pure parts of the Stroom MCP server's indexing tools (`tools/indexing.py`) that the indexing tools share:
/// marking a plan's fields that hold several values, choosing the plan a tool was given, the convention profiles,
/// which index a pipeline's index name writes to, what to tell the user of fields the index lacks, the component
/// templates an example needs, and what a draft leaves out of the index it replaces. What reads Stroom (the
/// session's drafted plans, the build's docs, the index's fields) is the tools'; these take what they read.
public final class IndexingHelpers {

    /// Each backend's index doc type.
    public static final Map<String, String> INDEX_TYPE = Map.of("lucene", "Index", "elasticsearch", "ElasticIndex");
    /// The hint a drafted or amended plan comes back with.
    public static final String PLAN_HINT = "Review unmapped paths with the user; add any they want with "
                                           + "changes={\"fields\": [...]} (only what changes: the plan is kept for "
                                           + "the session). Save the XSLT with save_xslt and no code: it is "
                                           + "generated from the plan kept for the session (or index_plan=), so "
                                           + "write_documentation generates the Field mapping section.";
    /// The hint when a draft leaves out fields of the index it replaces.
    public static final String REPLACED_HINT = "Ask the user about each field the old index wrote that this draft "
                                               + "doesn't: add it (under which name: an ECS field, "
                                               + "get_field_conventions name=ecs ecs_fields=<set>) with "
                                               + "extra_fields and draft again, or leave it out. Those not in the "
                                               + "sample hold nothing to index from it.";

    private static final Pattern VALUE_PART = Pattern.compile("\\{[^}]*\\}");
    private static final Pattern SEPARATORS = Pattern.compile("([-_.])[-_.]+");
    private static final Pattern DECLARATION = Pattern.compile("^\\s*<\\?xml[^>]*\\?>",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern PATH = Pattern.compile("[\\w@*./:\\[\\]='\"-]+", Pattern.UNICODE_CHARACTER_CLASS);

    private IndexingHelpers() {
        // Static only.
    }

    /// The plan with each field whose source can hold several values in one event (a repeatable element on its
    /// path, by the event-logging schema: Data, say) marked multiple, so the indexing XSLT writes them all. Seen
    /// (asked about by the user): written with value-of, two ports were indexed as the one number "80 443", two
    /// tags as "a b". Unmarked when the schema can't be read: the fields are then written as single values, as
    /// before.
    ///
    /// @param plan   A plan.
    /// @param schema The event-logging schema the Events follow, or null if it can't be read.
    /// @return The plan, its repeatable fields marked (a discovery plan as it is).
    public static FieldPlan withCardinality(final FieldPlan plan, final EventSchema schema) {
        if (plan.discovery() != null || schema == null) {
            return plan;
        }
        final List<PlannedField> fields = plan.fields().stream()
                .map(field -> !field.multiple() && !field.source().startsWith("@")
                              && !field.source().startsWith("shared:") && schema.canRepeat(field.source())
                        ? field.withMultiple(true)
                        : field)
                .toList();
        return plan.withFields(fields);
    }

    /// @param description An XSLT doc's description.
    /// @return The index plan kept with it, or empty if it keeps none (or another kind of mapping).
    /// @throws stroom.ai.impl.tools.generation.MappingValidationException if the plan kept doesn't validate.
    public static Optional<FieldPlan> keptPlan(final String description) {
        final Optional<Kept> kept = KeptMapping.readMapping(description);
        return kept.filter(mapping -> "index".equals(mapping.kind()))
                .map(mapping -> FieldPlan.fromJson(mapping.payload()));
    }

    /// The plan a tool was given, else the one kept with the pipeline's indexing XSLT (what it writes), else the
    /// one drafted in this session for one of the index names (or the only one drafted, with no name to go by).
    ///
    /// @param given      The plan given, or null.
    /// @param kept       The plan kept with the pipeline's indexing XSLT, or null.
    /// @param drafted    The plans drafted in this session, by index name.
    /// @param indexNames The index names to go by (nulls ignored).
    /// @return The plan, or null if there is none to go by.
    public static FieldPlan planGiven(final FieldPlan given,
                                      final FieldPlan kept,
                                      final Map<String, FieldPlan> drafted,
                                      final List<String> indexNames) {
        if (given != null) {
            return given;
        }
        if (kept != null) {
            return kept;
        }
        for (final String name : indexNames) {
            if (name != null && drafted.containsKey(name)) {
                return drafted.get(name);
            }
        }
        final boolean named = indexNames.stream().anyMatch(PyValues::truthy);
        return drafted.size() == 1 && !named
                ? drafted.values().iterator().next()
                : null;
    }

    /// The plan with the convention it was drafted under, when the agent's copy left it out (it decides whether the
    /// template composes ecs@mappings): taken from the plan kept with the pipeline, or drafted this session for the
    /// index.
    ///
    /// @param plan The plan given.
    /// @param kept The plan kept or drafted for it, or null.
    /// @return The plan, with the kept one's convention when it has none and they share a field.
    public static FieldPlan withConvention(final FieldPlan plan, final FieldPlan kept) {
        if (PyValues.truthy(plan.convention()) || kept == null || !PyValues.truthy(kept.convention())) {
            return plan;
        }
        final Set<String> names = new HashSet<>();
        plan.fields().forEach(field -> names.add(field.name()));
        if (kept.fields().stream().noneMatch(field -> names.contains(field.name()))) {
            return plan;
        }
        return plan.withConvention(kept.convention(), plan.customNamespaces().isEmpty()
                ? kept.customNamespaces()
                : plan.customNamespaces());
    }

    /// @param plan      A plan.
    /// @param populated The % of events each path is populated in.
    /// @return The populated paths no planned field names, each Network path once whichever action (one field for
    /// a source address, not one per Permit and Deny), sorted, at most 40.
    public static List<String> populatedNotMapped(final FieldPlan plan, final Map<String, ? extends Number> populated) {
        final Set<String> unmapped = new TreeSet<>();
        populated.forEach((path, share) -> {
            if (share != null && share.doubleValue() != 0
                && plan.fields().stream().noneMatch(field -> FieldPlans.sourceMatches(field.source(), path))) {
                unmapped.add(FieldPlans.anyAction(path));
            }
        });
        return unmapped.stream().limit(40).toList();
    }

    /// @param knowledge The bundled knowledge.
    /// @return The field convention profiles, by name (a profile's own `name`, else its file's).
    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, Object>> conventions(final AgentKnowledge knowledge) {
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (final String name : new TreeSet<>(knowledge.conventionNames())) {
            final Optional<String> text = knowledge.convention(name);
            if (text.isEmpty()) {
                continue;
            }
            final Map<String, Object> read = YamlUtil.getMapper().readValue(text.get(), Map.class);
            final Map<String, Object> profile = read == null
                    ? new LinkedHashMap<>()
                    : read;
            out.put(profile.get("name") == null
                    ? name
                    : String.valueOf(profile.get("name")), profile);
        }
        return out;
    }

    /// Whether a pipeline's Elasticsearch indexName writes to the index an Elastic Index doc names: the pipeline
    /// may build the name from values (`{_suffix}`), or the doc may name a pattern or alias (`ecs-windows*`).
    ///
    /// @param written   The pipeline's indexName.
    /// @param indexName The Elastic Index doc's index name.
    /// @return True if it writes to it.
    public static boolean writesToIndex(final String written, final String indexName) {
        if (written == null || !PyValues.truthy(indexName)) {
            return false;
        }
        final String pattern = VALUE_PART.matcher(written).replaceAll("*");
        return pattern.equals(indexName) || Globs.matches(indexName, pattern)
               || Globs.matches(pattern.replace("*", ""), indexName);
    }

    /// Why a pipeline's index name isn't the one meant: the build has an Elastic Index doc no other indexing
    /// pipeline of the build writes to, and this name writes to no doc. Null otherwise: a build may index into
    /// several indices, and an index's doc may come after its pipeline (a discovery index, a second index for
    /// comparison).
    ///
    /// @param written      The pipeline's indexName.
    /// @param indexDocs    The build's Elastic Index docs and the index name each names.
    /// @param destinations The build's Elasticsearch indexing pipelines.
    /// @param pipelineUuid The pipeline's UUID, or null.
    /// @return Why, or null.
    public static String otherIndex(final String written,
                                    final Map<String, String> indexDocs,
                                    final List<Destination> destinations,
                                    final String pipelineUuid) {
        if (indexDocs.isEmpty() || indexDocs.values().stream().anyMatch(n -> writesToIndex(written, n))) {
            return null;
        }
        final List<Destination> others = destinations.stream()
                .filter(p -> !p.uuid().equals(pipelineUuid))
                .toList();
        final List<String> waiting = new ArrayList<>();
        indexDocs.forEach((doc, name) -> {
            if (others.stream().noneMatch(p -> writesToIndex(p.indexName(), name))) {
                waiting.add("'" + doc + "' names '" + name + "'");
            }
        });
        if (waiting.isEmpty()) {
            return null;
        }
        return "The indexing pipeline writes to '" + written + "', and the build's Elastic Index doc ("
               + String.join(", ", waiting) + "), which no other pipeline of the build writes to, doesn't: an index "
               + "template for '" + written + "' would be for an index Stroom doesn't search.";
    }

    /// The index template's name, and the index name its pattern is made from, for a pipeline's indexName: values
    /// it is built from (`{_suffix}`) are wildcards in the pattern and left out of the name. Seen: a literal
    /// pattern 'ecs-windows{_suffix}v1*' that matched no index, the self-check passing it, the commit button
    /// dropped.
    ///
    /// @param index The pipeline's indexName.
    /// @return The template's name and the pattern's index name.
    public static TemplateTarget templateTarget(final String index) {
        if (!index.contains("{")) {
            return new TemplateTarget(index, index);
        }
        final String name = PyValues.strip(SEPARATORS.matcher(VALUE_PART.matcher(index).replaceAll(""))
                .replaceAll("$1"), "-_.");
        return new TemplateTarget(name.isEmpty()
                ? "index"
                : name, VALUE_PART.matcher(index).replaceAll("*"));
    }

    /// @param expected The fields each pipeline writes, by pipeline.
    /// @param listed   The fields Stroom lists for the index.
    /// @return The fields each pipeline writes that Stroom doesn't list, for those with any.
    public static Map<String, List<String>> fieldsNotListed(final Map<String, List<String>> expected,
                                                            final Set<String> listed) {
        final Map<String, List<String>> missing = new LinkedHashMap<>();
        expected.forEach((pipeline, fields) -> {
            final List<String> absent = fields.stream().filter(f -> !listed.contains(f)).toList();
            if (!absent.isEmpty()) {
                missing.put(pipeline, absent);
            }
        });
        return missing;
    }

    /// @param missing   The fields each pipeline writes that the index doesn't have, by pipeline.
    /// @param indexName The index's name, or null.
    /// @return What the agent tells the user about them.
    public static String missingFieldsSaid(final Map<String, List<String>> missing, final String indexName) {
        final List<String> said = new ArrayList<>();
        missing.forEach((pipeline, fields) -> said.add(String.join(", ", fields) + " (written by '" + pipeline
                                                       + "')"));
        return "The index" + (PyValues.truthy(indexName)
                ? " " + indexName
                : "") + " has no field " + String.join("; ", said) + ": Stroom doesn't list them for it, though the "
               + "pipeline writes them. Tell the user, and ask them to either reindex (the data was indexed before "
               + "the pipeline wrote these fields, or without them: for Elasticsearch, the index template must map "
               + "them before the data is indexed again), or check again in a minute or two (Stroom can be slow to "
               + "list a field that was only just indexed). Then call again.";
    }

    /// A needs_components reply while the example is composed of component templates not given (asked for by the
    /// user): the fields an index template maps are those of it and every component together, and the tools don't
    /// read Elasticsearch, so they can't see them otherwise. Elastic's ecs@mappings is known here; those the user
    /// can't give (without) are let go.
    ///
    /// @param example    The example template's body.
    /// @param components The component templates given, by name.
    /// @param without    The components the user can't give.
    /// @return The reply, or null if none is missing.
    /// @throws IndexingRefusal for an ECS component template Elasticsearch doesn't have.
    public static Map<String, Object> componentsWanted(final Map<String, Object> example,
                                                       final Map<String, ?> components,
                                                       final List<String> without) {
        final List<String> composedOf = PyValues.strings(example.get("composed_of"));
        // Seen in testing: a proposed template composed ecs@settings, which Elasticsearch doesn't have (ecs@mappings
        // is its only ECS component template), let go as one the user couldn't give. Only its content, given,
        // shows it exists.
        final List<String> unknown = composedOf.stream()
                .filter(n -> PyValues.lower(n).startsWith("ecs@") && !EcsSchema.COMPONENT.equals(n)
                             && !components.containsKey(n))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IndexingRefusal(PythonRepr.list(unknown) + ": not a component template Elasticsearch has: "
                                      + EcsSchema.COMPONENT + " is its only ECS one, built in. Don't compose it "
                                      + "(leave it out of include_components). Only if the user's cluster has one by "
                                      + "that name: its content in component_templates (GET _component_template/"
                                      + unknown.getFirst() + ").");
        }
        final List<String> missing = composedOf.stream()
                .filter(n -> !components.containsKey(n) && !EcsSchema.COMPONENT.equals(n) && !without.contains(n))
                .toList();
        if (missing.isEmpty()) {
            return null;
        }
        // One request per name: GET _component_template takes a single name or wildcard, not a list (seen in
        // testing: a comma-separated list found nothing).
        final String requests = String.join("\n", missing.stream().map(n -> "GET _component_template/" + n).toList());
        final Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("status", "needs_components");
        reply.put("ok", false);
        reply.put("composed_of", composedOf);
        reply.put("given", new ArrayList<>(new TreeSet<>(components.keySet())));
        reply.put("missing", missing);
        reply.put("request", requests);
        reply.put("hint", "The example is composed of component templates whose fields can't be seen here without "
                          + "them: the fields it maps are those of the index template and all of its components "
                          + "together. Ask the user to run " + (missing.size() == 1
                ? "the request"
                : "these requests (select them all and run: Dev Tools answers each)")
                          + " in Kibana Dev Tools and paste the whole response, exactly as shown, then call again "
                          + "with component_templates= that response alone: those given before are kept for the "
                          + "session. If a request says resource_not_found, the cluster has no such component: it "
                          + "can't be composed, so leave it out. If the user can't get one that exists, call again "
                          + "with without_components= its name: the template is then built without its fields, which "
                          + "it may repeat or contradict.");
        return reply;
    }

    /// The tools don't read Elasticsearch: component templates the user hasn't given can't be checked.
    ///
    /// @param missing The components of composed_of not given.
    /// @return The note saying so, none if none is missing.
    public static List<String> componentNotes(final List<String> missing) {
        return missing.isEmpty()
                ? List.of()
                : List.of("composed_of " + PythonRepr.list(missing) + " not given, so not checked: their fields "
                          + "aren't seen here. Ask the user for them (GET _component_template/<name>) and check "
                          + "again with component_templates.");
    }

    /// Whether a template built from the user's example leaves the ECS fields to ecs@mappings, with dynamic mapping
    /// on so it maps them: when the example references it (asked for by the user: ecs@mappings only when the user
    /// opts for ECS field mapping, by the ECS convention or an example that composes it). Seen in testing: a
    /// PascalCase example (User.Id), drafted with the ECS profile picking its paths, got ecs@mappings and dynamic
    /// mapping for @timestamp.
    ///
    /// @param example The example template's body.
    /// @return True if it composes ecs@mappings.
    public static boolean ecsBase(final Map<String, Object> example) {
        return PyValues.strings(example.get("composed_of")).contains(EcsSchema.COMPONENT);
    }

    /// What the index being replaced wrote that the draft doesn't. Seen in VS Code: asked to make an indexing
    /// pipeline write ECS, the agent compared its draft with the old XSLT by reading describe_document's output
    /// from a file its client spilled it to, and its model fell into a loop.
    ///
    /// @param xsltName    The old indexing XSLT's name.
    /// @param description Its description (which may keep its plan).
    /// @param code        Its code.
    /// @param plan        The draft.
    /// @param populated   The % of events each path is populated in.
    /// @return `{xslt, fields, not_in_draft, hint?}`, or `{xslt, note}` when no fields can be read from it.
    public static Map<String, Object> replaced(final String xsltName,
                                               final String description,
                                               final String code,
                                               final FieldPlan plan,
                                               final Map<String, ? extends Number> populated) {
        final List<Map<String, String>> old = oldFields(description, code);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("xslt", xsltName);
        if (old.isEmpty()) {
            result.put("note", "No fields read from it: not an indexing XSLT this server can read.");
            return result;
        }
        final List<Map<String, Object>> missing = new ArrayList<>();
        for (final Map<String, String> field : old) {
            final String source = PyValues.strip(Objects.requireNonNullElse(field.get("source"), "")
                    .split(" ! ", -1)[0]);
            if (source.isEmpty() || covered(source, plan)) {
                continue;
            }
            final String said;
            if (PATH.matcher(source).matches()) {
                final Double share = FieldPlans.populationOf(source, populated);
                said = share != null && share != 0
                        ? new BigDecimal(share).setScale(0, RoundingMode.HALF_EVEN).toPlainString() + "%"
                        : "not in the sample";
            } else {
                said = "unknown (an expression)";
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("field", field.get("field"));
            entry.put("source", field.get("source"));
            entry.put("populated", said);
            missing.add(entry);
        }
        result.put("fields", (long) old.size());
        result.put("not_in_draft", missing);
        if (!missing.isEmpty()) {
            result.put("hint", REPLACED_HINT);
        }
        return result;
    }

    // The old index's fields: the plan kept with it, else those its code writes.
    private static List<Map<String, String>> oldFields(final String description, final String code) {
        final Optional<Kept> kept = KeptMapping.readMapping(description);
        if (kept.isPresent() && "index".equals(kept.get().kind())) {
            final List<Map<String, String>> fields = new ArrayList<>();
            for (final Object item : PyValues.listOrEmpty(kept.get().payload().get("fields"))) {
                final Map<String, Object> field = PyValues.mapOrEmpty(item);
                final Map<String, String> entry = new LinkedHashMap<>();
                entry.put("field", (String) field.get("name"));
                entry.put("source", (String) field.get("source"));
                fields.add(entry);
            }
            return fields;
        }
        try {
            final List<Map<String, String>> fields = TranslationDescriptions.indexFields(XmlTrees.parse(
                    DECLARATION.matcher(code == null
                            ? ""
                            : code).replaceFirst("")));
            return fields == null
                    ? List.of()
                    : fields;
        } catch (final NotWellFormedException e) {
            return List.of();
        }
    }

    private static boolean covered(final String source, final FieldPlan plan) {
        return plan.fields().stream().anyMatch(f -> f.source().equals(source)
                                                    || FieldPlans.sourceMatches(f.source(), source)
                                                    || FieldPlans.sourceMatches(source, f.source()));
    }

    // --------------------------------------------------------------------------------


    /// An Elasticsearch indexing pipeline of a build.
    ///
    /// @param name      The pipeline's name.
    /// @param uuid      Its UUID.
    /// @param indexName The index name it writes to (its ElasticIndexingFilter's indexName).
    /// @param element   Its ElasticIndexingFilter element's id.
    public record Destination(String name, String uuid, String indexName, String element) {

    }

    /// Where a pipeline's index template goes.
    ///
    /// @param name      The index template's name.
    /// @param indexName The index name its pattern is made from.
    public record TemplateTarget(String name, String indexName) {

    }
}

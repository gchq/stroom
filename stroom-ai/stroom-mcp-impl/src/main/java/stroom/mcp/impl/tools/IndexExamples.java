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

import stroom.ai.api.ElasticIndexTemplates;
import stroom.ai.api.ElasticIndexTemplates.ComponentTemplate;
import stroom.ai.api.ElasticIndexTemplates.IndexTemplate;
import stroom.ai.api.ElasticIndexTemplates.IndexTemplates;
import stroom.ai.api.ElasticIndexTemplates.ReadException;
import stroom.ai.api.ElasticIndexTemplates.Status;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.indexing.EcsSchema;
import stroom.ai.impl.tools.indexing.IndexTemplates.NamedTemplate;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The user's example index template (the Stroom MCP server's `tools/indexing.py` `_index_doc`,
/// `_template_from_index`, `_example_from_index`, `_keep_example`, `_kept_example`, `_given_components` and
/// `_is_template`): pasted by the user, or read through Stroom from an existing Elastic Index doc they chose to
/// follow (its index template and component templates, read with Stroom's own template code as
/// elastic_index_templates reads them, else its fields and their types); kept in the build, verbatim, so the index
/// template follows it even once the conversation no longer holds it. Nothing is ever written to Elasticsearch.
class IndexExamples {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexExamples.class);

    /// The name a build's kept example takes after its index name.
    static final String EXAMPLE_SUFFIX = " example index template";
    /// Where an example the user pasted came from.
    static final String PASTED = "as the user pasted it";
    /// What an example read from an index's fields holds.
    static final String FIELDS_ONLY = "its fields and their Elasticsearch types";
    /// The role subfolder an example is kept in.
    static final String ROLE = "Indexing";

    // How many Elastic Index docs are looked at for one by name, and how many fields of one are read.
    private static final int NAMED_LOOKUP = 20;
    private static final int MAX_FIELDS = 2_000;
    // Where a reason Stroom gives is cut.
    private static final int REASON_LIMIT = 160;
    private static final Pattern EXAMPLE_BLOCK = Pattern.compile("<!-- stroom-mcp example\\n(.*?)\\n-->",
            Pattern.DOTALL);
    private static final Map<String, String> STROOM_TO_ES = stroomToEs();

    private final DocumentAccess documentAccess;
    private final PipelineIndex pipelineIndex;
    private final Provider<ElasticIndexTemplates> elasticIndexTemplatesProvider;
    private final Provider<QueryService> queryServiceProvider;
    private final WriteGuard guard;
    private final BuildPlan plan;
    private final IndexingMemory memory;
    private final DocumentWrites documentWrites;

    /// @param documentAccess                Reads the docs, as the user.
    /// @param pipelineIndex                 Lists the Elastic Index docs.
    /// @param elasticIndexTemplatesProvider Reads an index's templates through Stroom's cluster connection.
    /// @param queryServiceProvider          Lists an index's fields as Stroom has them.
    /// @param guard                         Lists and makes the build's documents.
    /// @param plan                          Says which build a pipeline is in.
    /// @param memory                        Keeps the component templates given in the session.
    /// @param documentWrites                Writes the example docs, as the user.
    @Inject
    IndexExamples(final DocumentAccess documentAccess,
                  final PipelineIndex pipelineIndex,
                  final Provider<ElasticIndexTemplates> elasticIndexTemplatesProvider,
                  final Provider<QueryService> queryServiceProvider,
                  final WriteGuard guard,
                  final BuildPlan plan,
                  final IndexingMemory memory,
                  final DocumentWrites documentWrites) {
        this.documentAccess = documentAccess;
        this.pipelineIndex = pipelineIndex;
        this.elasticIndexTemplatesProvider = elasticIndexTemplatesProvider;
        this.queryServiceProvider = queryServiceProvider;
        this.guard = guard;
        this.plan = plan;
        this.memory = memory;
        this.documentWrites = documentWrites;
    }

    /// @param index An Elastic Index doc's uuid, or its exact name.
    /// @return It.
    /// @throws ToolRefusal if no one doc the user can view is it.
    ElasticIndexDoc indexDoc(final String index) {
        final Optional<ElasticIndexDoc> byUuid = documentAccess.read(new DocRef(ElasticIndexDoc.TYPE, index),
                ElasticIndexDoc.class);
        if (byUuid.isPresent()) {
            return byUuid.get();
        }
        final List<DocRef> refs = pipelineIndex.list(ElasticIndexDoc.TYPE);
        final List<DocRef> named = refs.stream()
                .filter(ref -> index.equals(ref.getName()))
                .limit(NAMED_LOOKUP)
                .toList();
        final Optional<ElasticIndexDoc> found = named.size() == 1
                ? documentAccess.read(named.getFirst(), ElasticIndexDoc.class)
                : Optional.empty();
        return found.orElseThrow(() -> new ToolRefusal("No single Elastic Index doc named '" + index + "': give its "
                                                       + "uuid (get_field_conventions backend=elasticsearch lists "
                                                       + "them)"));
    }

    /// The index template an Elastic Index doc's indices take, and its component templates, read by Stroom through
    /// the doc's cluster: asked for by the user, as getting them from Kibana to paste was cumbersome.
    ///
    /// @param index An Elastic Index doc's uuid or exact name.
    /// @return The template read (with its components); or why it wasn't (most often the cluster's credentials
    /// lack the Elasticsearch privileges, as the user warned); or empty where the doc can't be found or the
    /// templates can't be read at all (the doc's fields then serve).
    Optional<FromIndex> templateFromIndex(final String index) {
        final ElasticIndexDoc doc;
        try {
            doc = indexDoc(index);
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
        final IndexTemplates found;
        try {
            found = elasticIndexTemplatesProvider.get().read(doc.asDocRef());
        } catch (final ReadException e) {
            if (e.getReason() == ElasticIndexTemplates.Reason.NOT_FOUND) {
                return Optional.empty();
            }
            return Optional.of(FromIndex.unread(doc.getName(), "Stroom couldn't read its index template: "
                                                               + shorten(e.getMessage())));
        } catch (final RuntimeException e) {
            // The read is a convenience: the doc's fields still serve.
            LOGGER.debug(() -> "Templates of " + doc.getName() + " not read: " + e.getMessage());
            return Optional.empty();
        }
        final IndexTemplate template = found == null
                ? null
                : found.indexTemplate();
        if (template == null || template.status() != Status.OK || NullSafe.isBlankString(template.body())) {
            return Optional.of(FromIndex.unread(doc.getName(), whyNot(template)));
        }
        final List<String> components = new ArrayList<>();
        final List<String> missing = new ArrayList<>();
        final Map<String, String> unread = new LinkedHashMap<>();
        for (final ComponentTemplate component : found.componentTemplates()) {
            if (EcsSchema.COMPONENT.equals(component.name())) {
                // Built in (Elasticsearch 8.13 and later), and what it maps is known here.
                continue;
            }
            if (component.status() == Status.OK && component.body() != null) {
                components.add("PUT _component_template/" + component.name() + "\n"
                               + PyJson.dumps(PyJson.loads(component.body())));
            } else if (component.status() == Status.NOT_FOUND) {
                missing.add(component.name());
            } else {
                unread.put(component.name(), shorten(NullSafe.nonBlankStringElse(component.message(),
                        component.status().name())));
            }
        }
        final Object body = PyJson.loads(template.body());
        final List<String> composedOf = body instanceof final Map<?, ?> map && map.get("composed_of") instanceof
                final List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        return Optional.of(new FromIndex(doc.getName(), doc.getIndexName(), template.name(),
                "PUT _index_template/" + template.name() + "\n" + PyJson.dumps(body), components, composedOf,
                missing, unread, null));
    }

    /// An existing Elastic Index doc's fields as an example index template, a note for the draft, and what was read
    /// (for the user's confirmation): where Stroom can't read the index template itself ([#templateFromIndex]).
    /// The doc's own field list holds Elasticsearch's types (keyword, ip, date...); Stroom's field list, the
    /// fallback, only its own (KEYWORD, LONG...). Neither gives the template's settings, component templates,
    /// keyword sub-fields or ignore_above.
    ///
    /// @param index An Elastic Index doc's uuid or exact name.
    /// @return The example, the note and what was read.
    /// @throws ToolRefusal if the doc can't be found, or Stroom lists no fields for it.
    FromFields exampleFromIndex(final String index) {
        final ElasticIndexDoc doc = indexDoc(index);
        final List<String[]> typed = new ArrayList<>();
        for (final ElasticIndexField field : NullSafe.list(doc.getFields())) {
            if (NullSafe.isNonEmptyString(field.getFldName()) && NullSafe.isNonEmptyString(field.getNativeType())) {
                typed.add(new String[]{field.getFldName(), field.getNativeType().toLowerCase(Locale.ROOT)});
            }
        }
        String source = "the index doc's field list, with Elasticsearch's own types";
        if (typed.isEmpty()) {
            for (final QueryField field : stroomFields(doc.asDocRef())) {
                typed.add(new String[]{field.getFldName(), field.getFldType() == null
                        ? null
                        : STROOM_TO_ES.get(field.getFldType().name().toLowerCase(Locale.ROOT))});
            }
            source = "Stroom's field list for it, with Stroom's types (Elasticsearch's own were not in the doc)";
        }
        final Map<String, Object> properties = new LinkedHashMap<>();
        final List<String[]> kept = new ArrayList<>();
        for (final String[] field : typed) {
            final String name = field[0];
            final String kind = field[1];
            if (NullSafe.isEmptyString(kind) || NullSafe.isEmptyString(name) || "object".equals(kind)
                || "nested".equals(kind)) {
                continue;
            }
            put(properties, name, kind);
            kept.add(field);
        }
        if (properties.isEmpty()) {
            throw new ToolRefusal("Stroom lists no fields for Elastic Index doc '" + doc.getName() + "' (its cluster "
                                  + "may be unreachable, or it has never been searched): ask the user to paste its "
                                  + "index template into the chat (GET _index_template/<name>, with any component "
                                  + "templates) and draft with example_template");
        }
        final String name = NullSafe.nonBlankStringElse(doc.getIndexName(), doc.getName());
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("index_patterns", List.of(name + "*"));
        body.put("template", Map.of("mappings", Map.of("properties", properties)));
        final List<String> examples = kept.stream()
                .limit(6)
                .map(field -> field[0] + " (" + field[1] + ")")
                .toList();
        final String note = "followed the fields of Elastic Index doc '" + doc.getName() + "' (" + kept.size()
                            + " fields, read from " + source + "); its index template itself (settings, component "
                            + "templates) is not visible through Stroom";
        return new FromFields("PUT _index_template/" + name + "\n" + PyJson.dumps(body), note, doc.getName(),
                doc.getIndexName(), kept.size(), source, examples);
    }

    /// Keeps the example index template the user pasted (and its components) in the build, verbatim: a summarised
    /// conversation lost it, and the agent proposed a template of its own as the user's example.
    ///
    /// @param build      The build.
    /// @param indexName  The index the example is for.
    /// @param example    The example, as given.
    /// @param components Its component templates, as given.
    /// @param how        Where it came from, when not pasted (an existing index the user chose to follow).
    void keepExample(final String build,
                     final String indexName,
                     final String example,
                     final List<String> components,
                     final String how) {
        final StringBuilder text = new StringBuilder();
        text.append("# Example index template for ").append(indexName).append("\n\n")
                .append(Character.toUpperCase(how.charAt(0))).append(how.substring(1))
                .append(", kept so the index template follows it even if the conversation no longer holds it.\n\n")
                .append("```\n").append(example.strip()).append("\n```\n");
        components.forEach(component -> text.append("\n```\n").append(component.strip()).append("\n```\n"));
        final Map<String, Object> kept = new LinkedHashMap<>();
        kept.put("example", example);
        kept.put("components", components);
        kept.put("how", how);
        text.append("\n<!-- stroom-mcp example\n").append(PyJson.dumps(kept)).append("\n-->\n");
        final String name = indexName + EXAMPLE_SUFFIX;
        final Optional<WriteGuard.BuildDoc> there = guard.folderContents(build).stream()
                .filter(doc -> DocumentationDoc.TYPE.equals(doc.type()) && name.equals(doc.name()))
                .findFirst();
        if (there.isPresent()) {
            documentWrites.writeText(there.get().docRef(), text.toString());
        } else {
            guard.createFilled(DocumentationDoc.TYPE, name, build,
                    ref -> documentWrites.writeText(ref, text.toString()), false, ROLE);
        }
    }

    /// @param pipelineUuid The indexing pipeline.
    /// @param indexNames   The index names the example may be kept under.
    /// @return The example kept in the pipeline's build when the plan was drafted, if any.
    Optional<Kept> keptExample(final String pipelineUuid, final List<String> indexNames) {
        try {
            final Optional<String> build = plan.buildOf(new DocRef("Pipeline", pipelineUuid));
            if (build.isEmpty()) {
                return Optional.empty();
            }
            final List<String> names = indexNames.stream()
                    .filter(NullSafe::isNonEmptyString)
                    .map(name -> name + EXAMPLE_SUFFIX)
                    .toList();
            final Optional<DocumentationDoc> doc = guard.folderContents(build.get()).stream()
                    .filter(entry -> DocumentationDoc.TYPE.equals(entry.type()) && names.contains(entry.name()))
                    .findFirst()
                    .flatMap(entry -> documentAccess.read(entry.docRef(), DocumentationDoc.class));
            if (doc.isEmpty()) {
                return Optional.empty();
            }
            final Matcher found = EXAMPLE_BLOCK.matcher(NullSafe.string(doc.get().getData()));
            if (!found.find()) {
                return Optional.empty();
            }
            final Map<?, ?> kept = (Map<?, ?>) PyJson.loads(found.group(1));
            final List<String> components = kept.get("components") instanceof final List<?> list
                    ? list.stream().map(String::valueOf).toList()
                    : List.of();
            return Optional.of(new Kept(String.valueOf(kept.get("example")), components,
                    NullSafe.nonBlankStringElse(kept.get("how") == null
                            ? null
                            : String.valueOf(kept.get("how")), PASTED)));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "No example read for " + pipelineUuid + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /// The component templates given now, by name, with those of names given earlier in the session: kept so the
    /// agent needn't send them again. The example (or template) pasted from Stroom's elastic_index_templates tool
    /// holds its component templates too: one paste.
    ///
    /// @param given   The component templates given (a text, a JSON object, or a list of either), or null.
    /// @param names   The names the template composes.
    /// @param example The example (or template) given, or null.
    /// @return The component templates, by name.
    /// @throws IllegalArgumentException for one that can't be read.
    Map<String, Map<String, Object>> givenComponents(final Object given,
                                                     final List<String> names,
                                                     final Object example) {
        final List<Object> texts = new ArrayList<>();
        if (given instanceof String || given instanceof Map) {
            texts.add(given);
        } else if (given instanceof final List<?> list) {
            texts.addAll(list);
        }
        if (example != null
            && !stroom.ai.impl.tools.indexing.IndexTemplates.stroomToolTemplates(example).isEmpty()) {
            texts.add(example);
        }
        final Map<String, Map<String, Object>> parsed =
                stroom.ai.impl.tools.indexing.IndexTemplates.parseComponentTemplates(texts);
        return memory.components(parsed, names);
    }

    /// @param text A text.
    /// @return True if it reads as an index template (or an index's mapping).
    static boolean isTemplate(final String text) {
        try {
            final NamedTemplate parsed = stroom.ai.impl.tools.indexing.IndexTemplates.parseTemplate(text);
            return parsed != null;
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    private List<QueryField> stroomFields(final DocRef ref) {
        try {
            final ResultPage<QueryField> found = queryServiceProvider.get().findFields(new FindFieldCriteria(
                    new PageRequest(0, MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, ref, null, null));
            return NullSafe.list(NullSafe.get(found, ResultPage::getValues)).stream()
                    .filter(field -> field.getFldName() != null)
                    .toList();
        } catch (final RuntimeException e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static void put(final Map<String, Object> properties, final String name, final String kind) {
        Map<String, Object> node = properties;
        final String[] parts = name.split("\\.", -1);
        for (int i = 0; i < parts.length - 1; i++) {
            final Map<String, Object> parent = (Map<String, Object>) node.computeIfAbsent(parts[i], k -> {
                final Map<String, Object> made = new LinkedHashMap<>();
                made.put("properties", new LinkedHashMap<String, Object>());
                return made;
            });
            node = (Map<String, Object>) parent.computeIfAbsent("properties", k -> new LinkedHashMap<String, Object>());
        }
        final Map<String, Object> leaf = new LinkedHashMap<>();
        leaf.put("type", kind);
        node.put(parts[parts.length - 1], leaf);
    }

    private static String whyNot(final IndexTemplate template) {
        final Status status = template == null
                ? Status.ERROR
                : template.status();
        final String why = switch (status) {
            case NOT_FOUND -> "no index template on the cluster matches its index";
            case FORBIDDEN -> "the cluster's credentials in Stroom may not read index templates";
            default -> status.name();
        };
        final String message = shorten(template == null
                ? ""
                : NullSafe.string(template.message()));
        return "Stroom couldn't read its index template (" + why + (message.isEmpty()
                ? ""
                : ": " + message) + ")";
    }

    private static String shorten(final String text) {
        final String value = NullSafe.string(text);
        return value.length() <= REASON_LIMIT
                ? value
                : value.substring(0, REASON_LIMIT);
    }

    private static Map<String, String> stroomToEs() {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("keyword", "keyword");
        map.put("text", "text");
        map.put("long", "long");
        map.put("integer", "integer");
        map.put("id", "long");
        map.put("float", "float");
        map.put("double", "double");
        map.put("date", "date");
        map.put("ipv4_address", "ip");
        map.put("boolean", "boolean");
        return Map.copyOf(map);
    }


    // --------------------------------------------------------------------------------


    /// An index template read through Stroom from an Elastic Index doc, or why it wasn't.
    ///
    /// @param doc        The doc's name.
    /// @param index      Its index name, or null.
    /// @param name       The template's name, or null when not read.
    /// @param template   The template as a Dev Tools request, or null when not read.
    /// @param components Its component templates, as Dev Tools requests (ecs@mappings, built in, left out).
    /// @param composedOf The components it is composed of.
    /// @param missing    The components it names that the cluster doesn't have.
    /// @param unread     The components that couldn't be read, with why.
    /// @param why        Why the template wasn't read, or null when it was.
    record FromIndex(String doc,
                     String index,
                     String name,
                     String template,
                     List<String> components,
                     List<String> composedOf,
                     List<String> missing,
                     Map<String, String> unread,
                     String why) {

        static FromIndex unread(final String doc, final String why) {
            return new FromIndex(doc, null, null, null, List.of(), List.of(), List.of(), Map.of(), why);
        }
    }

    /// An existing index's fields as an example.
    ///
    /// @param template The example, as a Dev Tools request.
    /// @param note     What the draft says of it.
    /// @param doc      The doc's name.
    /// @param index    Its index name, or null.
    /// @param fields   How many fields were read.
    /// @param source   Where they were read from.
    /// @param examples Some of them, `name (type)`.
    record FromFields(String template,
                      String note,
                      String doc,
                      String index,
                      int fields,
                      String source,
                      List<String> examples) {

        /// @return What was read, for the user's confirmation, as the Stroom MCP server gave it.
        Map<String, Object> read() {
            final Map<String, Object> read = new LinkedHashMap<>();
            read.put("doc", doc);
            read.put("index", index);
            read.put("fields", fields);
            read.put("source", source);
            read.put("examples", examples);
            return read;
        }
    }

    /// An example kept in a build.
    ///
    /// @param example    The example, verbatim.
    /// @param components Its component templates, verbatim.
    /// @param how        Where it came from.
    record Kept(String example, List<String> components, String how) {

    }
}

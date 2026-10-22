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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.indexing.EcsSchema;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.FieldPlans;
import stroom.ai.impl.tools.indexing.IndexTemplates;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.indexing.TemplateMappings;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.docref.DocRef;
import stroom.index.shared.AddField;
import stroom.index.shared.IndexFieldImpl;
import stroom.index.shared.IndexResource;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.FieldType;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/// The fields Stroom has for an index doc, as the indexing tools check them (the Stroom MCP server's
/// `tools/indexing.py` `_index_fields`, `_unsearchable`, `fields_not_in_index`, `_fields_written_not_indexed`,
/// `_columns_not_in_index`, `_unlisted` and `set_index_fields`): which fields no search finds, which fields a
/// pipeline writes that the index doesn't have, and a Lucene index doc given its plan's fields.
/// create_indexing_pipeline's port adds a plan's fields with [#addLuceneFields]; write_documentation's checks the
/// fields its pipelines write with [#notInIndex] and [#missingFieldsSaid].
class IndexFields {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexFields.class);

    /// The time between looks at the index's fields, for a field Stroom hasn't listed yet.
    static final List<Duration> FIELD_WAITS = List.of(Duration.ofSeconds(5), Duration.ofSeconds(5));
    /// The most fields read of an index.
    static final int MAX_FIELDS = 2_000;
    // How many of the index's fields a refusal of columns names.
    private static final int FIELDS_NAMED = 60;

    private final Provider<QueryService> queryServiceProvider;
    private final Provider<IndexResource> indexResourceProvider;
    private final DocumentAccess documentAccess;
    private final KeptMappings keptMappings;
    private final IndexingInputs inputs;
    private final WriteGuard guard;
    private final IndexPlans plans;
    private final List<Duration> fieldWaits;

    /// @param queryServiceProvider  Lists an index's fields, as a query's data source does.
    /// @param indexResourceProvider Lists and adds a Lucene index's fields.
    /// @param documentAccess        Reads the docs, as the user.
    /// @param keptMappings          Reads the plan kept with a pipeline's indexing XSLT.
    /// @param inputs                Reads the Events.
    /// @param guard                 Checks an index doc is the build's.
    /// @param plans                 Gives the ECS schema.
    @Inject
    IndexFields(final Provider<QueryService> queryServiceProvider,
                final Provider<IndexResource> indexResourceProvider,
                final DocumentAccess documentAccess,
                final KeptMappings keptMappings,
                final IndexingInputs inputs,
                final WriteGuard guard,
                final IndexPlans plans) {
        this(queryServiceProvider, indexResourceProvider, documentAccess, keptMappings, inputs, guard, plans,
                FIELD_WAITS);
    }

    /// As the injected constructor, with the waits between looks given, for tests.
    IndexFields(final Provider<QueryService> queryServiceProvider,
                final Provider<IndexResource> indexResourceProvider,
                final DocumentAccess documentAccess,
                final KeptMappings keptMappings,
                final IndexingInputs inputs,
                final WriteGuard guard,
                final IndexPlans plans,
                final List<Duration> fieldWaits) {
        this.queryServiceProvider = queryServiceProvider;
        this.indexResourceProvider = indexResourceProvider;
        this.documentAccess = documentAccess;
        this.keptMappings = keptMappings;
        this.inputs = inputs;
        this.guard = guard;
        this.plans = plans;
        this.fieldWaits = List.copyOf(fieldWaits);
    }

    /// The fields Stroom has for an index doc; for Lucene, whether each is stored (only stored values can be
    /// shown).
    ///
    /// @param ref An index doc.
    /// @return Each field as `{name, type, stored?, searchable?}` (stored and searchable only when false).
    List<Map<String, Object>> indexFields(final DocRef ref) {
        final List<Map<String, Object>> out = new ArrayList<>();
        if (LuceneIndexDoc.TYPE.equals(ref.getType())) {
            for (final IndexFieldImpl field : luceneFields(ref)) {
                final Map<String, Object> entry = entry(field.getFldName(), field.getFldType());
                if (!field.isStored()) {
                    entry.put("stored", false);
                }
                if (!field.isIndexed()) {
                    entry.put("searchable", false);
                }
                out.add(entry);
            }
            return out;
        }
        final ResultPage<QueryField> found = queryServiceProvider.get().findFields(new FindFieldCriteria(
                new PageRequest(0, MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, ref, null, null));
        for (final QueryField field : NullSafe.list(NullSafe.get(found, ResultPage::getValues))) {
            if (NullSafe.isEmptyString(field.getFldName())) {
                continue;
            }
            final Map<String, Object> entry = entry(field.getFldName(), field.getFldType());
            if (Boolean.FALSE.equals(field.getQueryable())) {
                entry.put("searchable", false);
            }
            out.add(entry);
        }
        return out;
    }

    /// @param ref A Lucene index doc.
    /// @return Its TEXT fields and their analyzers (e.g. KEYWORD); none if they can't be read.
    Map<String, String> luceneText(final DocRef ref) {
        final Map<String, String> text = new LinkedHashMap<>();
        try {
            for (final IndexFieldImpl field : luceneFields(ref)) {
                if (field.getFldType() == FieldType.TEXT && NullSafe.isNonEmptyString(field.getFldName())) {
                    text.put(field.getFldName(), NullSafe.get(field.getAnalyzerType(), AnalyzerType::name) == null
                            ? ""
                            : field.getAnalyzerType().name());
                }
            }
        } catch (final RuntimeException e) {
            // A check on the searches, not a reason for verification to fail.
            LOGGER.debug(() -> "Text fields of " + ref + " not read: " + e.getMessage());
        }
        return text;
    }

    /// Which of an Elastic Index doc's fields are addresses, from the doc's own field list or Stroom's: a wildcard
    /// on them finds nothing.
    ///
    /// @param doc The Elastic Index doc.
    /// @return Each field's type (Elasticsearch's own where the doc lists it), by name.
    Map<String, String> elasticTypes(final ElasticIndexDoc doc) {
        final Map<String, String> typed = new LinkedHashMap<>();
        for (final ElasticIndexField field : NullSafe.list(doc.getFields())) {
            if (field.getFldName() != null) {
                final String type = NullSafe.isNonEmptyString(field.getNativeType())
                        ? field.getNativeType()
                        : NullSafe.get(field.getFldType(), FieldType::name);
                typed.put(field.getFldName(), NullSafe.string(type).toLowerCase(Locale.ROOT));
            }
        }
        if (typed.isEmpty()) {
            try {
                for (final Map<String, Object> field : indexFields(doc.asDocRef())) {
                    typed.put(String.valueOf(field.get("name")), String.valueOf(field.get("type")));
                }
            } catch (final RuntimeException e) {
                // A check on the searches, not a reason for verification to fail.
                LOGGER.debug(() -> "Fields of " + doc.getName() + " not read: " + e.getMessage());
            }
        }
        return typed;
    }

    /// Of these fields, those no search finds, with why: a Lucene field not indexed, one Stroom lists as not
    /// queryable, or one the template agreed with the pipeline doesn't index (index: false, or under an object
    /// with enabled: false). Asked about by the user: a check on such a field finds nothing, as if documents were
    /// missing.
    ///
    /// @param ref          The index doc.
    /// @param names        The fields searched.
    /// @param pipelineUuid The indexing pipeline, or null.
    /// @return Each such field, with why.
    Map<String, String> unsearchable(final DocRef ref, final List<String> names, final String pipelineUuid) {
        final Map<String, String> out = new LinkedHashMap<>();
        try {
            for (final Map<String, Object> field : indexFields(ref)) {
                if (names.contains(field.get("name")) && Boolean.FALSE.equals(field.get("searchable"))) {
                    out.put(String.valueOf(field.get("name")), LuceneIndexDoc.TYPE.equals(ref.getType())
                            ? "the index doc's field isn't indexed"
                            : "Stroom lists it as not searchable");
                }
            }
        } catch (final RuntimeException e) {
            // A check on the searches, not a reason for verification to fail.
            LOGGER.debug(() -> "Fields of " + ref + " not read: " + e.getMessage());
        }
        if (NullSafe.isNonBlankString(pipelineUuid) && ElasticIndexDoc.TYPE.equals(ref.getType())) {
            final Map<String, Object> mappings = agreedMappings(pipelineUuid);
            final List<String> left = names.stream().filter(name -> !out.containsKey(name)).toList();
            for (final String name : TemplateMappings.unsearchable(mappings, left)) {
                out.put(name, "the index template agreed for it doesn't index it (index: false, or an object with "
                              + "enabled: false)");
            }
        }
        return out;
    }

    /// The fields each pipeline writes that Stroom doesn't list for the index, of those expected, after looking
    /// again a few times: a field just indexed can take Stroom a moment to list. Seen in production: the
    /// documentation of an index came out without a field the pipeline writes, though its data was indexed and
    /// refreshed, and nothing said so; a minute later Stroom listed it (asked for by the user: an error, not
    /// silence).
    ///
    /// @param ref      The index doc.
    /// @param expected The fields each pipeline writes, by pipeline.
    /// @return Those Stroom doesn't list, by pipeline, for the pipelines with any.
    /// @throws ToolTimeoutException if the call is cancelled while it waits.
    Map<String, List<String>> notInIndex(final DocRef ref, final Map<String, List<String>> expected) {
        Map<String, List<String>> missing = Map.of();
        for (int attempt = 0; attempt <= fieldWaits.size(); attempt++) {
            final Set<String> listed = new LinkedHashSet<>();
            indexFields(ref).forEach(field -> listed.add(String.valueOf(field.get("name"))));
            missing = IndexingHelpers.fieldsNotListed(expected, listed);
            if (missing.isEmpty() || attempt == fieldWaits.size()) {
                return missing;
            }
            pause(fieldWaits.get(attempt));
        }
        return missing;
    }

    /// The fields an indexing pipeline's plan writes for these Events (their source populated in them: a field no
    /// event fills is never mapped where the mapping is dynamic) that the index doesn't have.
    ///
    /// @param pipelineUuid The indexing pipeline.
    /// @param ref          The index doc.
    /// @param streamIds    The Events streams indexed.
    /// @return Those the index lacks, by the pipeline's name; none when the pipeline keeps no plan (the searches
    /// decide).
    Map<String, List<String>> writtenNotIndexed(final String pipelineUuid,
                                                final DocRef ref,
                                                final List<Long> streamIds) {
        final List<String> written = new ArrayList<>();
        final String name;
        try {
            final FieldPlan plan = keptMappings.keptMapping(pipelineUuid)
                    .filter(kept -> "index".equals(kept.kind()))
                    .map(kept -> FieldPlan.fromJson(kept.payload()))
                    .orElse(null);
            if (plan == null || plan.discovery() != null) {
                return Map.of();
            }
            final Map<String, Double> population = IndexingInputs.population(inputs.summariseEvents(streamIds));
            name = documentAccess.read(new DocRef(PipelineDoc.TYPE, pipelineUuid), PipelineDoc.class)
                    .map(PipelineDoc::getName)
                    .filter(NullSafe::isNonEmptyString)
                    .orElse(pipelineUuid);
            for (final PlannedField field : plan.fields()) {
                final Double share = FieldPlans.populationOf(field.source(), population);
                if (field.source().startsWith("@") || (share != null && share > 0)) {
                    written.add(field.name());
                }
            }
        } catch (final ToolRefusal | PipelineUnreadableException e) {
            // The searches decide; this check needs the plan and the Events.
            return Map.of();
        }
        final Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put(name, written);
        return notInIndex(ref, expected);
    }

    /// Refuses table columns Stroom doesn't list for the index: a column on one is empty in every row. Seen in Ask
    /// Stroom AI: EventSource.User.Id, the Events path, was offered and chosen in place of user.name, the field the
    /// plan indexed it as; the dashboard was saved and verification passed with that column empty. A path the
    /// pipeline's plan indexes is answered with the field it became.
    ///
    /// @param ref          The index doc.
    /// @param columns      The columns.
    /// @param pipelineUuid The indexing pipeline, or null.
    /// @throws ToolRefusal for columns the index doesn't have.
    void refuseColumnsNotInIndex(final DocRef ref, final List<String> columns, final String pipelineUuid) {
        final List<String> missing;
        final List<String> listed;
        try {
            final Map<String, List<String>> expected = new LinkedHashMap<>();
            expected.put("columns", List.copyOf(columns));
            missing = notInIndex(ref, expected).getOrDefault("columns", List.of());
            if (missing.isEmpty()) {
                return;
            }
            listed = new ArrayList<>(new TreeSet<>(indexFields(ref).stream()
                    .map(field -> String.valueOf(field.get("name")))
                    .toList()));
        } catch (final ToolTimeoutException e) {
            throw e;
        } catch (final RuntimeException e) {
            // A check on the columns, not a reason for verification to fail.
            return;
        }
        List<PlannedField> planned = List.of();
        if (NullSafe.isNonBlankString(pipelineUuid)) {
            try {
                planned = keptMappings.keptMapping(pipelineUuid)
                        .filter(kept -> "index".equals(kept.kind()))
                        .map(kept -> FieldPlan.fromJson(kept.payload()).fields())
                        .orElse(List.of());
            } catch (final RuntimeException e) {
                // Without the plan, the index's fields are still listed.
                planned = List.of();
            }
        }
        final EcsSchema ecs = plans.ecs();
        final List<String> said = new ArrayList<>();
        for (final String name : missing) {
            final String path = strip(name.replace('.', '/'));
            final String became = planned.stream()
                    .filter(field -> FieldPlans.sourceMatches(strip(field.source()), path))
                    .map(PlannedField::name)
                    .findFirst()
                    .orElse(null);
            if (became != null && listed.contains(became)) {
                said.add(name + " (an Events path: the plan indexes it as " + became + ")");
            } else if (ElasticIndexDoc.TYPE.equals(ref.getType()) && ecs != null
                       && EcsSchema.STROOM_UNLISTED.contains(ecs.componentType(name))) {
                said.add(unlisted(name, ecs));
            } else {
                said.add(name);
            }
        }
        throw new ToolRefusal("Not fields of index '" + ref.getName() + "' in Stroom, so a dashboard column on them "
                              + "would be empty in every row: " + String.join("; ", said) + ". A column is the "
                              + "index's field name, not the Events path it comes from. Its fields: "
                              + String.join(", ", listed.subList(0, Math.min(FIELDS_NAMED, listed.size())))
                              + ". Confirm the corrected columns with the user, then call again.");
    }

    /// Why Stroom doesn't list a field of an Elasticsearch index.
    ///
    /// @param field The field.
    /// @param ecs   The ECS schema, or null.
    /// @return Why, and what to do.
    static String unlisted(final String field, final EcsSchema ecs) {
        final String kind = ecs == null
                ? null
                : ecs.componentType(field);
        if (kind != null && EcsSchema.STROOM_UNLISTED.contains(kind)) {
            return field + " isn't among the index's fields in Stroom: " + EcsSchema.COMPONENT + " maps it as "
                   + kind + ", a type Stroom leaves out, so Stroom can't search or show it. Map it as "
                   + ("match_only_text".equals(kind) || "search_as_you_type".equals(kind)
                    ? "text"
                    : "keyword") + " in the index template (propose_index_template does, for a new index; an "
                   + "existing index keeps a field's type, so it takes a new index or a reindex), and leave it out "
                   + "of these searches until then";
        }
        return field + " isn't among the index's fields in Stroom: either no document has it yet (a field left to "
               + "dynamic mapping is mapped once a document brings it), or its mapping type is one Stroom leaves "
               + "out (" + String.join(", ", new TreeSet<>(EcsSchema.STROOM_UNLISTED)) + "), so Stroom can't "
               + "search it";
    }

    /// Lucene: adds the plan's fields to the build's index doc (keywords as TEXT with the KEYWORD analyzer).
    ///
    /// @param session The session, for the audit of a refusal.
    /// @param index   The Lucene Index doc.
    /// @param plan    The field plan (backend lucene).
    /// @return `{index, added, already_present}`.
    /// @throws ToolRefusal for a plan that isn't Lucene's, or an index doc that isn't the build's.
    Map<String, Object> addLuceneFields(final ToolSession session, final DocRef index, final FieldPlan plan) {
        if (!FieldPlan.LUCENE.equals(plan.backend())) {
            throw new ToolRefusal("create_index_doc (plan=...) is for Lucene; Elasticsearch fields come from the "
                                  + "index template the user commits (propose_index_template)");
        }
        final LuceneIndexDoc doc = documentAccess.read(index, LuceneIndexDoc.class)
                .orElseThrow(() -> new ToolRefusal("Index " + index.getUuid() + " can't be read: it doesn't exist, "
                                                   + "or you don't have permission to view it"));
        final DocRef ref = new DocRef(LuceneIndexDoc.TYPE, doc.getUuid(), doc.getName());
        guard.checkInBuild(session, ref);
        final Set<String> have = new TreeSet<>();
        luceneFields(ref).forEach(field -> have.add(field.getFldName()));
        final List<String> added = new ArrayList<>();
        for (final Map<String, Object> field : plan.luceneFields()) {
            final String name = String.valueOf(field.get("fldName"));
            if (!have.contains(name)) {
                indexResourceProvider.get().addField(new AddField(ref, IndexFieldImpl.builder()
                        .fldName(name)
                        .fldType(FieldType.valueOf(String.valueOf(field.get("fldType"))))
                        .analyzerType(AnalyzerType.valueOf(String.valueOf(field.get("analyzerType"))))
                        .indexed(Boolean.TRUE.equals(field.get("indexed")))
                        .stored(Boolean.TRUE.equals(field.get("stored")))
                        .caseSensitive(Boolean.TRUE.equals(field.get("caseSensitive")))
                        .build()));
                added.add(name);
            }
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("index", doc.getName());
        result.put("added", added);
        result.put("already_present", new ArrayList<>(have));
        return result;
    }

    /// @param missing   The fields each pipeline writes that the index doesn't have.
    /// @param indexName The index's name, or null.
    /// @return What the agent tells the user about them.
    static String missingFieldsSaid(final Map<String, List<String>> missing, final String indexName) {
        return IndexingHelpers.missingFieldsSaid(missing, indexName);
    }

    // The mappings of the template agreed with the pipeline, or none.
    private Map<String, Object> agreedMappings(final String pipelineUuid) {
        try {
            final Map<String, Object> agreed = documentAccess.read(new DocRef(PipelineDoc.TYPE, pipelineUuid),
                            PipelineDoc.class)
                    .flatMap(doc -> KeptMapping.readAgreedTemplate(doc.getDescription()))
                    .orElse(Map.of());
            final Object devTools = agreed.get("dev_tools");
            if (devTools == null || String.valueOf(devTools).isEmpty()) {
                return Map.of();
            }
            final Map<String, Object> body = IndexTemplates.parseTemplate(String.valueOf(devTools)).body();
            return body.get("template") instanceof final Map<?, ?> template
                   && template.get("mappings") instanceof final Map<?, ?> mappings
                    ? castMap(mappings)
                    : Map.of();
        } catch (final RuntimeException e) {
            return Map.of();
        }
    }

    private List<IndexFieldImpl> luceneFields(final DocRef ref) {
        final ResultPage<IndexFieldImpl> found = indexResourceProvider.get().findFields(new FindFieldCriteria(
                new PageRequest(0, MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, ref, null, null));
        return NullSafe.list(NullSafe.get(found, ResultPage::getValues)).stream()
                .filter(field -> NullSafe.isNonEmptyString(field.getFldName()))
                .toList();
    }

    private static Map<String, Object> entry(final String name, final FieldType type) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("type", type == null
                ? ""
                : type.name().toLowerCase(Locale.ROOT));
        return entry;
    }

    private static void pause(final Duration wait) {
        if (wait.isZero() || wait.isNegative()) {
            return;
        }
        ToolDeadline.check();
        try {
            Thread.sleep(wait.toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolTimeoutException();
        }
    }

    private static String strip(final String path) {
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == '/') {
            start++;
        }
        while (end > start && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(start, end);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(final Map<?, ?> map) {
        return (Map<String, Object>) Objects.requireNonNull(map);
    }
}

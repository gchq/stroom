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
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexField;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.IndexFieldCache;
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

/// The index documents an indexing pipeline writes to, and their fields as Stroom has them, for describe_feed.
class IndexDocs {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexDocs.class);

    /// The most fields read of an index.
    static final int MAX_FIELDS = 2_000;
    /// The most Elastic Index documents read.
    static final int MAX_ELASTIC_INDEXES = 200;


    private final PipelineIndex pipelineIndex;
    private final DocumentAccess documentAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final Provider<IndexFieldCache> indexFieldCacheProvider;

    /// @param pipelineIndex           Lists the Elastic Index documents.
    /// @param documentAccess          Reads each, checking View.
    /// @param queryServiceProvider    Lists an index's fields, as a query's data source does.
    /// @param indexFieldCacheProvider Gives a Lucene field's analyzer and case sensitivity.
    @Inject
    IndexDocs(final PipelineIndex pipelineIndex,
              final DocumentAccess documentAccess,
              final Provider<QueryService> queryServiceProvider,
              final Provider<IndexFieldCache> indexFieldCacheProvider) {
        this.pipelineIndex = pipelineIndex;
        this.documentAccess = documentAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.indexFieldCacheProvider = indexFieldCacheProvider;
    }

    /// The index docs an indexing pipeline writes to: a Lucene IndexingFilter's doc, or the Elastic Index docs
    /// whose index name its Elasticsearch filter writes.
    ///
    /// @param merged The pipeline as Stroom runs it.
    /// @return Each index written.
    List<Index> written(final MergedPipeline merged) {
        final Map<ElementProperty, Object> properties = merged.properties();
        final List<Index> indexes = new ArrayList<>();
        properties.forEach((key, value) -> {
            if ("index".equals(key.name()) && value instanceof final Map<?, ?> doc && doc.get("uuid") != null) {
                indexes.add(new Index(new DocRef(LuceneIndexDoc.TYPE, String.valueOf(doc.get("uuid")),
                        doc.get("name") == null
                                ? null
                                : String.valueOf(doc.get("name"))), null, null));
            }
        });
        if (properties.keySet().stream().anyMatch(key -> "indexName".equals(key.name()))) {
            final List<DocRef> elastic = pipelineIndex.list(ElasticIndexDoc.TYPE);
            for (final DocRef ref : elastic.subList(0, Math.min(MAX_ELASTIC_INDEXES, elastic.size()))) {
                // One doc gone or unreadable: not this pipeline's index, and the others still checked.
                final Optional<ElasticIndexDoc> doc = documentAccess.read(ref, ElasticIndexDoc.class);
                if (doc.isPresent()
                    && writesTo(properties, ElasticIndexDoc.TYPE, ref.getUuid(), doc.get().getIndexName())) {
                    indexes.add(new Index(ref, doc.get().getIndexName(), doc.get()));
                }
            }
        }
        return indexes;
    }

    /// Whether a pipeline's effective properties write to the index: a Lucene IndexingFilter's index doc, or an
    /// Elasticsearch filter's index name (which may build the name from values, {_suffix}, or the doc may name a
    /// pattern or alias, ecs-windows*).
    ///
    /// @param properties The pipeline's properties.
    /// @param docType    The index doc's type.
    /// @param uuid       The index doc's UUID.
    /// @param indexName  An Elastic Index doc's index name, or null.
    /// @return True if the pipeline writes to it.
    static boolean writesTo(final Map<ElementProperty, Object> properties,
                            final String docType,
                            final String uuid,
                            final String indexName) {
        for (final Map.Entry<ElementProperty, Object> entry : properties.entrySet()) {
            final String name = entry.getKey().name();
            final Object value = entry.getValue();
            if (LuceneIndexDoc.TYPE.equals(docType) && "index".equals(name)
                && value instanceof final Map<?, ?> doc && uuid.equals(doc.get("uuid"))) {
                return true;
            }
            if (ElasticIndexDoc.TYPE.equals(docType) && "indexName".equals(name)
                && value instanceof final String text && IndexingHelpers.writesToIndex(text, indexName)) {
                return true;
            }
        }
        return false;
    }

    /// The index's fields as Stroom has them, with Elasticsearch's own type (keyword, text, ip...) where the Elastic
    /// Index doc lists it, and a Lucene field's analyzer and case sensitivity.
    ///
    /// @param index An index doc.
    /// @return Each field as `{name, type, elasticsearch_type?, analyzer?, case_sensitive?}`.
    List<Map<String, Object>> fields(final Index index) {
        final ResultPage<QueryField> found = queryServiceProvider.get().findFields(new FindFieldCriteria(
                new PageRequest(0, MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, index.docRef(), null, null));
        final Map<String, String> nativeTypes = new LinkedHashMap<>();
        if (index.elastic() != null) {
            for (final ElasticIndexField field : NullSafe.list(index.elastic().getFields())) {
                if (field.getFldName() != null && NullSafe.isNonEmptyString(field.getNativeType())) {
                    nativeTypes.put(field.getFldName(), field.getNativeType().toLowerCase(Locale.ROOT));
                }
            }
        }
        final List<Map<String, Object>> fields = new ArrayList<>();
        for (final QueryField field : NullSafe.list(NullSafe.get(found, ResultPage::getValues))) {
            if (field.getFldName() == null) {
                continue;
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", field.getFldName());
            entry.put("type", field.getFldType() == null
                    ? ""
                    : field.getFldType().name().toLowerCase(Locale.ROOT));
            if (Boolean.FALSE.equals(field.getQueryable())) {
                entry.put("searchable", false);
            }
            if (nativeTypes.containsKey(field.getFldName())) {
                entry.put("elasticsearch_type", nativeTypes.get(field.getFldName()));
            }
            if (LuceneIndexDoc.TYPE.equals(index.docRef().getType())) {
                addLuceneDetails(index.docRef(), field.getFldName(), entry);
            }
            fields.add(entry);
        }
        return fields;
    }

    private void addLuceneDetails(final DocRef index, final String name, final Map<String, Object> entry) {
        try {
            final IndexField held = indexFieldCacheProvider.get().get(index, name);
            if (held == null) {
                return;
            }
            if (held.getAnalyzerType() != null) {
                entry.put("analyzer", held.getAnalyzerType().name());
            }
            if (!held.isCaseSensitive()) {
                // Seen: the plan's Lucene fields, where EQUALS Bob finds bob.
                entry.put("case_sensitive", false);
            }
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to read field {} of {}", name, index, e);
        }
    }


    // --------------------------------------------------------------------------------


    /// An index doc a pipeline writes to.
    ///
    /// @param docRef    The doc.
    /// @param indexName An Elastic Index doc's index name, or null.
    /// @param elastic   An Elastic Index doc, or null for a Lucene one.
    record Index(DocRef docRef, String indexName, ElasticIndexDoc elastic) {

        /// @return `elasticsearch` or `lucene`.
        String backend() {
            return ElasticIndexDoc.TYPE.equals(docRef.getType())
                    ? "elasticsearch"
                    : "lucene";
        }
    }
}

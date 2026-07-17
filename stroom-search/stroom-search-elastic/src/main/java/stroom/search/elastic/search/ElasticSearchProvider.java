/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.search.elastic.search;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Query;
import stroom.query.api.SearchRequest;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexField;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.common.v2.IndexFieldProvider;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProvider;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexCache;
import stroom.search.elastic.ElasticIndexService;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticNativeTypes;
import stroom.search.elastic.shared.UnsupportedTypeException;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ExpandWildcard;
import co.elastic.clients.elasticsearch._types.mapping.BooleanProperty;
import co.elastic.clients.elasticsearch._types.mapping.DateProperty;
import co.elastic.clients.elasticsearch._types.mapping.FieldAliasProperty;
import co.elastic.clients.elasticsearch._types.mapping.FieldMapping;
import co.elastic.clients.elasticsearch._types.mapping.IpProperty;
import co.elastic.clients.elasticsearch._types.mapping.KeywordProperty;
import co.elastic.clients.elasticsearch._types.mapping.NumberPropertyBase;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.Property.Kind;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.GetMappingRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Executor;

@SuppressWarnings("unused")
public class ElasticSearchProvider implements SearchProvider, ElasticIndexService, IndexFieldProvider {

    public static final String ENTITY_TYPE = ElasticIndexDoc.TYPE;
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchProvider.class);
    private static final String FIELD_PATH_SEPARATOR = ".";

    private final ElasticIndexCache elasticIndexCache;
    private final SecurityContext securityContext;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final ElasticClientCache elasticClientCache;
    private final ElasticClusterStore elasticClusterStore;
    private final ElasticIndexStore elasticIndexStore;
    private final ElasticSearchExecutor elasticSearchExecutor;
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;

    @Inject
    public ElasticSearchProvider(
            final ElasticIndexCache elasticIndexCache,
            final Executor executor,
            final TaskContextFactory taskContextFactory,
            final Provider<ElasticAsyncSearchTaskHandler> elasticAsyncSearchTaskHandlerProvider,
            final CoprocessorsFactory coprocessorsFactory,
            final ResultStoreFactory resultStoreFactory,
            final ElasticClientCache elasticClientCache,
            final ElasticClusterStore elasticClusterStore,
            final ElasticIndexStore elasticIndexStore,
            final SecurityContext securityContext,
            final ElasticSearchExecutor elasticSearchExecutor,
            final FieldInfoResultPageFactory fieldInfoResultPageFactory) {
        this.elasticIndexCache = elasticIndexCache;
        this.securityContext = securityContext;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.elasticClientCache = elasticClientCache;
        this.elasticClusterStore = elasticClusterStore;
        this.elasticIndexStore = elasticIndexStore;
        this.elasticSearchExecutor = elasticSearchExecutor;
        this.fieldInfoResultPageFactory = fieldInfoResultPageFactory;
    }

    @Override
    public ResultStore createResultStore(final SearchRequest searchRequest) {
        // Replace expression parameters.
        final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);

        // Get the search.
        final Query query = modifiedSearchRequest.getQuery();

        // Load the index.
        final ElasticIndexDoc index = securityContext.useAsReadResult(() ->
                elasticIndexCache.get(query.getDataSource()));

        // Create a coprocessor settings list.
        final List<CoprocessorSettings> coprocessorSettingsList = coprocessorsFactory
                .createSettings(modifiedSearchRequest);

        // Create a handler for search results.
        final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                modifiedSearchRequest.getSearchRequestSource(),
                modifiedSearchRequest.getDateTimeSettings(),
                modifiedSearchRequest.getKey(),
                coprocessorSettingsList,
                modifiedSearchRequest.getQuery().getParams(),
                DataStoreSettings.createBasicSearchResultStoreSettings());

        // Create an asynchronous search task.
        final String searchName = "Search '" + modifiedSearchRequest.getKey().toString() + "'";
        final ElasticAsyncSearchTask asyncSearchTask = new ElasticAsyncSearchTask(
                modifiedSearchRequest.getKey(),
                searchName,
                query,
                coprocessorSettingsList,
                modifiedSearchRequest.getDateTimeSettings());

        // Create the search result collector.
        final ResultStore resultStore = resultStoreFactory.create(
                searchRequest.getSearchRequestSource(),
                coprocessors);
        elasticSearchExecutor.start(asyncSearchTask, resultStore);
        return resultStore;
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        return securityContext.useAsReadResult(() -> {
            final ElasticIndexDoc index = elasticIndexStore.readDocument(criteria.getDataSourceRef());
            if (index == null) {
                return ResultPage.empty();
            }
            final List<QueryField> fields = getDataSourceFields(index);
            return fieldInfoResultPageFactory.create(criteria, fields);
        });
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final ElasticIndexDoc index = elasticIndexStore.readDocument(docRef);
            return NullSafe.get(
                    index,
                    this::getDataSourceFields,
                    List::size);
        });
    }

    @Override
    public IndexField getIndexField(final DocRef docRef, final String fieldName) {
        final ElasticIndexDoc index = elasticIndexStore.readDocument(docRef);
        if (index != null) {
            final Map<String, ElasticIndexField> indexFieldMap = getFieldsMap(index);
            return indexFieldMap.get(fieldName);
        }
        return null;
    }

    @Override
    public Optional<DocRef> fetchDefaultExtractionPipeline(final DocRef dataSourceRef) {
        return securityContext.useAsReadResult(() -> {
            final ElasticIndexDoc index = elasticIndexStore.readDocument(dataSourceRef);
            return Optional.ofNullable(index).map(ElasticIndexDoc::getDefaultExtractionPipeline);
        });
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.ofNullable(elasticIndexStore.readDocument(docRef)).map(ElasticIndexDoc::getDescription);
    }

    @Override
    public Optional<QueryField> getTimeField(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final ElasticIndexDoc index = elasticIndexStore.readDocument(docRef);
            QueryField timeField = null;
            if (index != null && index.getTimeField() != null && !index.getTimeField().isBlank()) {
                timeField = QueryField.createDate(index.getTimeField());
            }
            return Optional.ofNullable(timeField);
        });
    }

    @Override
    public List<QueryField> getDataSourceFields(final ElasticIndexDoc index) {
        final Map<String, FieldMapping> fieldMappings = getFieldMappings(index);

        return fieldMappings
                .entrySet()
                .stream()
                .map(field -> {
                    final String fieldName = field.getKey();
                    final FieldMapping fieldMeta = field.getValue();
                    String nativeType = getFieldTypeFromMapping(fieldName, field.getValue());

                    if (nativeType == null) {
                        // If field type is null, this is a system field, so ignore
                        return null;
                    } else if (Kind.Alias.jsonValue().equals(nativeType)) {
                        // Determine the mapping type of the field the alias is referring to
                        try {
                            final String aliasPath = getAliasPathFromMapping(fieldName, field.getValue());
                            final FieldMapping targetFieldMeta = fieldMappings.get(aliasPath);
                            nativeType = getFieldTypeFromMapping(aliasPath, targetFieldMeta);
                        } catch (final Exception e) {
                            LOGGER.error("Could not determine mapping type for alias field '{}'", fieldName);
                        }
                    }

                    try {
                        final String fullName = fieldMeta.fullName();
                        final FieldType elasticFieldType =
                                ElasticNativeTypes.fromNativeType(fullName, nativeType);

                        return toDataSourceField(elasticFieldType, fieldName, fieldIsIndexed(field.getValue()));
                    } catch (final UnsupportedTypeException e) {
                        LOGGER.debug(e::getMessage, e);
                        return null;
                    } catch (final IllegalArgumentException e) {
                        LOGGER.warn(e::getMessage, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Returns an `AbstractField` instance, based on the field's data type
     */
    private QueryField toDataSourceField(final FieldType elasticIndexFieldType,
                                         final String fieldName,
                                         final Boolean isIndexed)
            throws IllegalArgumentException {
        final ConditionSet conditionSet = ConditionSet.getElastic(elasticIndexFieldType);
        return QueryField
                .builder()
                .fldName(fieldName)
                .fldType(elasticIndexFieldType)
                .conditionSet(conditionSet)
                .queryable(isIndexed && !FieldType.NESTED.equals(elasticIndexFieldType))
                .build();
    }

    private String getFieldTypeFromMapping(final String fieldName, final FieldMapping fieldMeta) {
        final Optional<Property> firstFieldMapping = fieldMeta.mapping().values().stream().findFirst();

        if (firstFieldMapping.isPresent()) {
            return firstFieldMapping.get()._kind().jsonValue();
        } else {
            LOGGER.debug(() -> "Mapping properties for field '" + fieldName +
                               "' were in an unrecognised format. Field ignored.");
        }

        return null;
    }

    private String getAliasPathFromMapping(final String fieldName, final FieldMapping fieldMeta) {
        final Optional<Property> firstFieldMapping = fieldMeta.mapping().values().stream().findFirst();

        if (firstFieldMapping.isPresent()) {
            final Object fieldMappingInstance = firstFieldMapping.get()._get();
            if (fieldMappingInstance instanceof FieldAliasProperty) {
                return ((FieldAliasProperty) fieldMappingInstance).path();
            }
        } else {
            LOGGER.debug(() -> "Mapping properties for field '" + fieldName +
                               "' were in an unrecognised format. Field ignored.");
        }

        return null;
    }

    @Override
    public List<ElasticIndexField> getFields(final ElasticIndexDoc index) {
        return new ArrayList<>(getFieldsMap(index).values());
    }

    @Override
    public Map<String, ElasticIndexField> getFieldsMap(final ElasticIndexDoc index) {
        final Map<String, FieldMapping> fieldMappings = getFieldMappings(index);
        final Map<String, ElasticIndexField> fieldsMap = new HashMap<>();

        fieldMappings.forEach((key, fieldMeta) -> {
            try {
                String nativeType = getFieldTypeFromMapping(key, fieldMeta);
                final String fieldName = fieldMeta.fullName();
                final boolean indexed = fieldIsIndexed(fieldMeta);

                if (nativeType == null) {
                    return;
                } else if (Kind.Alias.jsonValue().equals(nativeType)) {
                    // Determine the mapping type of the field the alias is referring to
                    try {
                        final String aliasPath = getAliasPathFromMapping(fieldName, fieldMeta);
                        final FieldMapping targetFieldMeta = fieldMappings.get(aliasPath);
                        nativeType = getFieldTypeFromMapping(aliasPath, targetFieldMeta);
                    } catch (final Exception e) {
                        LOGGER.error("Could not determine mapping type for alias field '{}'", fieldName);
                    }
                }

                final FieldType type = ElasticNativeTypes.fromNativeType(fieldName, nativeType);
                final ElasticIndexField field = ElasticIndexField
                        .builder()
                        .fldName(fieldName)
                        .fldType(type)
                        .nativeType(nativeType)
                        .indexed(indexed)
                        .build();
                fieldsMap.put(fieldName, field);
            } catch (final UnsupportedTypeException e) {
                LOGGER.debug(e::getMessage, e);
            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
            }
        });

        return fieldsMap;
    }

    /**
     * Tests whether a field has the mapping property `index` set to `true`.
     * This determines whether it is searchable.
     */
    private boolean fieldIsIndexed(final FieldMapping field) {
        try {
            final Map<String, Property> fieldMap = field.mapping();
            final Optional<Property> firstFieldMapping = fieldMap.values().stream().findFirst();
            if (firstFieldMapping.isPresent()) {
                final Object mappingInstance = firstFieldMapping.get()._get();

                // Detect non-indexed fields for common data types. For all others, assume the field is indexed
                if (mappingInstance instanceof KeywordProperty) {
                    return !Boolean.FALSE.equals(((KeywordProperty) mappingInstance).index());
                } else if (mappingInstance instanceof TextProperty) {
                    return !Boolean.FALSE.equals(((TextProperty) mappingInstance).index());
                } else if (mappingInstance instanceof BooleanProperty) {
                    return !Boolean.FALSE.equals(((BooleanProperty) mappingInstance).index());
                } else if (mappingInstance instanceof DateProperty) {
                    return !Boolean.FALSE.equals(((DateProperty) mappingInstance).index());
                } else if (mappingInstance instanceof NumberPropertyBase) {
                    return !Boolean.FALSE.equals(((NumberPropertyBase) mappingInstance).index());
                } else if (mappingInstance instanceof IpProperty) {
                    return !Boolean.FALSE.equals(((IpProperty) mappingInstance).index());
                } else {
                    return true;
                }
            }
        } catch (final Exception e) {
            return false;
        }

        return false;
    }

    private Map<String, FieldMapping> getFieldMappings(final ElasticIndexDoc elasticIndex) {
        Map<String, FieldMapping> result = new TreeMap<>();

        if (elasticIndex.getClusterRef() != null) {
            try {
                final ElasticClusterDoc elasticCluster = elasticClusterStore.readDocument(elasticIndex.getClusterRef());
                result = elasticClientCache.contextResult(elasticCluster.getConnection(), elasticClient ->
                        getFlattenedFieldMappings(elasticIndex, elasticClient));
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        return result;
    }

    /**
     * Reads the full, hierarchical mapping for the index and flattens it into a de-duplicated map keyed by
     * full field name.
     * <p>
     * This uses the get-mapping API ({@code _mapping}) rather than the get-field-mapping API
     * ({@code _mapping/field/*}). The latter flattens its response and does not reliably surface
     * {@code nested} (or {@code object}) container fields, which means the nesting structure of the index is
     * lost. The get-mapping API instead returns the complete property tree, from which the container fields
     * and their descendants can be recovered at every level of nesting - a prerequisite for building multi
     * level nested queries and for reading the grouped values back out of nested search hits.
     */
    private static NavigableMap<String, FieldMapping> getFlattenedFieldMappings(
            final ElasticIndexDoc elasticIndex,
            final ElasticsearchClient elasticClient) {
        // De-duplicated map keyed by full field name, ordered case-insensitively.
        final NavigableMap<String, FieldMapping> mappings = new TreeMap<>((o1, o2) -> {
            if (Objects.equals(o1, o2)) {
                return 0;
            }
            if (o2 == null) {
                return 1;
            }

            return o1.compareToIgnoreCase(o2);
        });

        final String indexName = elasticIndex.getIndexName();
        final GetMappingRequest request = GetMappingRequest.of(r -> r
                .expandWildcards(ExpandWildcard.Open)
                .index(indexName));

        try {
            final GetMappingResponse response = elasticClient.indices().getMapping(request);

            // The response is keyed by concrete index name (a wildcard or alias may resolve to several).
            // Flatten across all matched indices, keeping the first definition seen for any given field name.
            response.mappings().values().forEach(indexMapping -> {
                final TypeMapping typeMapping = indexMapping.mappings();
                if (typeMapping != null && typeMapping.properties() != null) {
                    typeMapping.properties().forEach((fieldName, property) ->
                            registerField(fieldName, property, mappings));
                }
            });
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }

        return mappings;
    }

    /**
     * Registers a field and, if it is an {@code object} or {@code nested} container, recursively registers
     * each of its descendant fields under their full dotted path.
     * <p>
     * Container fields are retained (rather than merely descended into) so that their native type stays
     * discoverable downstream; this is what allows {@code nested} containers such as {@code user} and
     * {@code user.address} to be identified when a query targets a deeply nested field like
     * {@code user.address.city}. Container types that are not usable as data source fields (e.g. plain
     * {@code object}) are filtered out later during native type resolution.
     *
     * @param fullName the full (dot-delimited) name of the field
     * @param property the field's mapping property
     * @param out      the accumulating map of field name to field mapping
     */
    private static void registerField(final String fullName,
                                      final Property property,
                                      final NavigableMap<String, FieldMapping> out) {
        // The synthesised FieldMapping only needs to carry the property; every downstream reader (type
        // resolution, alias lookup, indexed detection) reads the mapping value, not its key.
        out.putIfAbsent(fullName, FieldMapping.of(fm -> fm
                .fullName(fullName)
                .mapping(leafName(fullName), property)));

        // Recurse into the children of object / nested containers, expanding each to its full dotted path.
        // Multi-fields (Property.fields(), e.g. `title.keyword`) are deliberately NOT traversed: they are
        // not part of the returned `_source` and must not be selectable by the user.
        getChildProperties(property).forEach((childLeafName, childProperty) ->
                registerField(fullName + FIELD_PATH_SEPARATOR + childLeafName, childProperty, out));
    }

    /**
     * @return the child properties of an {@code object} or {@code nested} container, or an empty map for any
     * other property type (leaf fields, aliases, etc.).
     */
    private static Map<String, Property> getChildProperties(final Property property) {
        if (property.isNested()) {
            return property.nested().properties();
        } else if (property.isObject()) {
            return property.object().properties();
        }
        return Map.of();
    }

    private static String leafName(final String fullName) {
        final int idx = fullName.lastIndexOf(FIELD_PATH_SEPARATOR);
        return idx < 0
                ? fullName
                : fullName.substring(idx + FIELD_PATH_SEPARATOR.length());
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return elasticIndexStore.list();
    }

    @Override
    public String getDataSourceType() {
        return ElasticIndexDoc.TYPE;
    }
}

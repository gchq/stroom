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
import co.elastic.clients.elasticsearch._types.mapping.PropertyBase;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch.indices.GetFieldMappingRequest;
import co.elastic.clients.elasticsearch.indices.GetFieldMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_field_mapping.TypeFieldMappings;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executor;

@SuppressWarnings("unused")
public class ElasticSearchProvider implements SearchProvider, ElasticIndexService, IndexFieldProvider {

    public static final String ENTITY_TYPE = ElasticIndexDoc.TYPE;
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchProvider.class);

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
                .queryable(isIndexed)
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

    private static NavigableMap<String, FieldMapping> getFlattenedFieldMappings(
            final ElasticIndexDoc elasticIndex,
            final ElasticsearchClient elasticClient) {
        // Flatten the mappings, which are keyed by index, into a de-duplicated list
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
        final GetFieldMappingRequest request = GetFieldMappingRequest.of(r -> r
                .expandWildcards(ExpandWildcard.Open)
                .index(indexName)
                .fields("*"));

        try {
            final GetFieldMappingResponse response = elasticClient.indices().getFieldMapping(request);
            final Map<String, TypeFieldMappings> allMappings = response.fieldMappings();

            // Build a list of all multi fields (i.e. those defined only in the field mapping).
            // These are excluded from the fields the user can pick via the Stroom UI, as they are not part
            // of the returned `_source` field.
            final Set<String> multiFieldMappings = new HashSet<>();
            allMappings.values().forEach(indexMappings -> indexMappings.mappings().forEach((fieldName, mapping) -> {
                final Property source = mapping.mapping().get(fieldName);
                if (source != null && source._get() instanceof final PropertyBase propertyBase) {
                    final Map<String, Property> multiFields = propertyBase.fields();

                    if (!multiFields.isEmpty()) {
                        multiFields.forEach((multiFieldName, multiFieldMapping) -> {
                            final String fullName = mapping.fullName() + "." + multiFieldName;
                            multiFieldMappings.add(fullName);
                        });
                    }
                }
            }));

            allMappings.values().forEach(indexMappings -> indexMappings.mappings().forEach((fieldName, mapping) -> {
                if (!mappings.containsKey(fieldName) && !multiFieldMappings.contains(mapping.fullName())) {
                    mappings.put(fieldName, mapping);
                }
            }));
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }

        return mappings;
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

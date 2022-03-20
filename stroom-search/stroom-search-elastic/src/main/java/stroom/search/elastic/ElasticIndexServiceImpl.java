/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.search.elastic;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.search.elastic.search.ElasticSearchStoreFactory;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldType;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetFieldMappingsRequest;
import org.elasticsearch.client.indices.GetFieldMappingsResponse;
import org.elasticsearch.client.indices.GetFieldMappingsResponse.FieldMappingMetadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ElasticIndexServiceImpl implements ElasticIndexService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexServiceImpl.class);

    private final ElasticClientCache elasticClientCache;
    private final ElasticClusterStore elasticClusterStore;
    private final ElasticIndexStore elasticIndexStore;
    private final SecurityContext securityContext;
    private final SearchResponseCreatorManager searchResponseCreatorManager;
    private final ElasticSearchStoreFactory storeFactory;

    @Inject
    public ElasticIndexServiceImpl(final ElasticClientCache elasticClientCache,
                                   final ElasticClusterStore elasticClusterStore,
                                   final ElasticIndexStore elasticIndexStore,
                                   final SecurityContext securityContext,
                                   final SearchResponseCreatorManager searchResponseCreatorManager,
                                   final ElasticSearchStoreFactory storeFactory) {
        this.elasticClientCache = elasticClientCache;
        this.elasticClusterStore = elasticClusterStore;
        this.elasticIndexStore = elasticIndexStore;
        this.securityContext = securityContext;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.storeFactory = storeFactory;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final ElasticIndexDoc index = elasticIndexStore.readDocument(docRef);
            return new DataSource(getDataSourceFields(index));
        });
    }

    @Override
    public SearchResponse search(final SearchRequest request) {
        return searchResponseCreatorManager.search(storeFactory, request);
    }

    @Override
    public List<AbstractField> getDataSourceFields(ElasticIndexDoc index) {
        final Map<String, FieldMappingMetadata> fieldMappings = getFieldMappings(index);

        return fieldMappings
                .entrySet()
                .stream()
                .map(field -> {
                    final String fieldName = field.getKey();
                    final FieldMappingMetadata fieldMeta = field.getValue();
                    String nativeType = getFieldPropertyFromMapping(fieldName, field.getValue(), "type");

                    if (nativeType == null) {
                        // If field type is null, this is a system field, so ignore
                        return null;
                    } else if (nativeType.equals("alias")) {
                        // Determine the mapping type of the field the alias is referring to
                        try {
                            final String aliasPath = getFieldPropertyFromMapping(fieldName, field.getValue(), "path");
                            final FieldMappingMetadata targetFieldMeta = fieldMappings.get(aliasPath);
                            nativeType = getFieldPropertyFromMapping(aliasPath, targetFieldMeta, "type");
                        } catch (Exception e) {
                            LOGGER.error("Could not determine mapping type for alias field '{}'", fieldName);
                        }
                    }

                    try {
                        final String fullName = fieldMeta.fullName();
                        final ElasticIndexFieldType elasticFieldType =
                                ElasticIndexFieldType.fromNativeType(fullName, nativeType);

                        return elasticFieldType.toDataSourceField(fieldName, fieldIsIndexed(field.getValue()));
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn(e::getMessage);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String getFieldPropertyFromMapping(final String fieldName, final FieldMappingMetadata fieldMeta,
                                               final String propertyName) {
        final Optional<Entry<String, Object>> firstFieldEntry =
                fieldMeta.sourceAsMap().entrySet().stream().findFirst();

        if (firstFieldEntry.isPresent()) {
            final Object properties = firstFieldEntry.get().getValue();
            if (properties instanceof Map) {
                @SuppressWarnings("unchecked") // Need to get at the nested properties, which is always a map
                final Map<String, Object> propertiesMap = (Map<String, Object>) properties;
                return (String) propertiesMap.get(propertyName);
            } else {
                LOGGER.debug(() ->
                        "Mapping properties for field '" + fieldName +
                                "' were in an unrecognised format. Field ignored.");
            }
        }

        return null;
    }

    @Override
    public List<ElasticIndexField> getFields(final ElasticIndexDoc index) {
        return new ArrayList<>(getFieldsMap(index).values());
    }

    @Override
    public Map<String, ElasticIndexField> getFieldsMap(final ElasticIndexDoc index) {
        final Map<String, FieldMappingMetadata> fieldMappings = getFieldMappings(index);
        final Map<String, ElasticIndexField> fieldsMap = new HashMap<>();

        fieldMappings.forEach((key, fieldMeta) -> {
            try {
                String nativeType = getFieldPropertyFromMapping(key, fieldMeta, "type");
                final String fieldName = fieldMeta.fullName();
                final boolean indexed = fieldIsIndexed(fieldMeta);

                if (nativeType == null) {
                    return;
                } else if (nativeType.equals("alias")) {
                    // Determine the mapping type of the field the alias is referring to
                    try {
                        final String aliasPath = getFieldPropertyFromMapping(fieldName, fieldMeta, "path");
                        final FieldMappingMetadata targetFieldMeta = fieldMappings.get(aliasPath);
                        nativeType = getFieldPropertyFromMapping(aliasPath, targetFieldMeta, "type");
                    } catch (Exception e) {
                        LOGGER.error("Could not determine mapping type for alias field '{}'", fieldName);
                    }
                }

                fieldsMap.put(fieldName, new ElasticIndexField(
                        ElasticIndexFieldType.fromNativeType(fieldName, nativeType),
                        fieldName,
                        nativeType,
                        indexed));
            } catch (Exception e) {
                LOGGER.error(e::getMessage, e);
            }
        });

        return fieldsMap;
    }

    /**
     * Tests whether a field has the mapping property `index` set to `true`.
     * This determines whether it is searchable.
     */
    private boolean fieldIsIndexed(final FieldMappingMetadata field) {
        try {
            final Map<String, Object> fieldMap = field.sourceAsMap();
            final Optional<String> fieldKey = fieldMap.keySet().stream().findFirst();
            if (fieldKey.isPresent()) {
                @SuppressWarnings("unchecked") // Need to get at the field mapping properties
                final Map<String, Object> mappingProperties = (Map<String, Object>) fieldMap.get(fieldKey.get());
                return mappingProperties.containsKey("index") ? (Boolean) fieldMap.get("index") : true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, FieldMappingMetadata> getFieldMappings(final ElasticIndexDoc elasticIndex) {
        try {
            final ElasticClusterDoc elasticCluster = elasticClusterStore.readDocument(elasticIndex.getClusterRef());

            return elasticClientCache.contextResult(elasticCluster.getConnection(), elasticClient -> {
                final String indexName = elasticIndex.getIndexName();
                final GetFieldMappingsRequest request = new GetFieldMappingsRequest();
                request.indicesOptions(IndicesOptions.lenientExpand());
                request.indices(indexName);
                request.fields("*");

                try {
                    final GetFieldMappingsResponse response = elasticClient.indices().getFieldMapping(
                            request, RequestOptions.DEFAULT);
                    final Map<String, Map<String, FieldMappingMetadata>> allMappings = response.mappings();

                    // Flatten the mappings, which are keyed by index, into a deduplicated list
                    final TreeMap<String, FieldMappingMetadata> mappings = new TreeMap<>((o1, o2) -> {
                        if (Objects.equals(o1, o2)) {
                            return 0;
                        }
                        if (o2 == null) {
                            return 1;
                        }

                        return o1.compareToIgnoreCase(o2);
                    });

                    // Build a list of all multi fields (i.e. those defined only in the field mapping).
                    // These are excluded from the fields the user can pick via the Stroom UI, as they are not part
                    // of the returned `_source` field.
                    final HashSet<String> multiFieldMappings = new HashSet<>();
                    allMappings.values().forEach(indexMappings -> indexMappings.forEach((fieldName, mapping) -> {
                        if (mapping.sourceAsMap().get(fieldName) instanceof Map) {
                            @SuppressWarnings("unchecked") final Map<String, Object> source =
                                    (Map<String, Object>) mapping.sourceAsMap().get(fieldName);
                            final Object fields = source.get("fields");

                            if (fields instanceof Map) {
                                final Map<String, Object> multiFields = (Map<String, Object>) fields;

                                multiFields.forEach((multiFieldName, multiFieldMapping) -> {
                                    final String fullName = mapping.fullName() + "." + multiFieldName;
                                    multiFieldMappings.add(fullName);
                                });
                            }
                        }
                    }));

                    allMappings.values().forEach(indexMappings -> indexMappings.forEach((fieldName, mapping) -> {
                        if (!mappings.containsKey(fieldName) && !multiFieldMappings.contains(mapping.fullName())) {
                            mappings.put(fieldName, mapping);
                        }
                    }));

                    return mappings;
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                }

                return new TreeMap<>();
            });
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            return new TreeMap<>();
        }
    }

    @Override
    public Boolean keepAlive(final QueryKey queryKey) {
        return searchResponseCreatorManager.keepAlive(queryKey);
    }

    @Override
    public Boolean destroy(final QueryKey queryKey) {
        return searchResponseCreatorManager.remove(queryKey);
    }

    @Override
    public String getType() {
        return ElasticIndexDoc.DOCUMENT_TYPE;
    }
}

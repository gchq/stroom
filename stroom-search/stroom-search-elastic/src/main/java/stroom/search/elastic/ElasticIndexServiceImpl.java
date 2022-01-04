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

    @Inject
    public ElasticIndexServiceImpl(
        final ElasticClientCache elasticClientCache,
        final ElasticClusterStore elasticClusterStore,
        final ElasticIndexStore elasticIndexStore,
        final SecurityContext securityContext
    ) {
        this.elasticClientCache = elasticClientCache;
        this.elasticClusterStore = elasticClusterStore;
        this.elasticIndexStore = elasticIndexStore;
        this.securityContext = securityContext;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final ElasticIndexDoc index = elasticIndexStore.readDocument(docRef);
            return new DataSource(getDataSourceFields(index));
        });
    }

    @Override
    public List<AbstractField> getDataSourceFields(ElasticIndexDoc index) {
        final Map<String, FieldMappingMetadata> fieldMappings = getFieldMappings(index);

        return fieldMappings.entrySet().stream().map(field -> {
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

                return elasticFieldType.toDataSourceField(fieldName, true);
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
                final boolean sourceFieldEnabled = sourceFieldIsEnabled(fieldMappings);
                final boolean stored = fieldIsStored(key, fieldMeta);

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
                        sourceFieldEnabled || stored));
            } catch (Exception e) {
                LOGGER.error(e::getMessage, e);
            }
        });

        return fieldsMap;
    }

    /**
     * Get a filtered list of any field mappings with `stored` equal to `true`
     */
    @Override
    public List<String> getStoredFields(final ElasticIndexDoc index) {
        final Map<String, FieldMappingMetadata> fieldMappings = getFieldMappings(index);
        final boolean sourceFieldEnabled = sourceFieldIsEnabled(fieldMappings);

        return fieldMappings.entrySet().stream()
                .filter(mapping -> sourceFieldEnabled || fieldIsStored(mapping.getKey(), mapping.getValue()))
                .map(mapping -> mapping.getValue().fullName())
                .collect(Collectors.toList());
    }

    /**
     * Check the index mapping for a field `_source` and if enabled, return TRUE.
     * WARNING: This does NOT check whether fields are excluded from `_source`, which is considered an advanced
     * use case. In this situation, the returned value will be empty.
     * @see "https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-source-field.html"
     */
    private boolean sourceFieldIsEnabled(final Map<String, FieldMappingMetadata> fieldMappings) {
        final FieldMappingMetadata sourceField = fieldMappings.get("_source");

        // If the `_source` field is enabled, treat all fields as "stored", as the source data is stored in the index
        try {
            if (!((Boolean) sourceField.sourceAsMap().get("enabled"))) {
                return false;
            }
        } catch (Exception ignored) {
            // Source field mapping does not exist, so _source field is enabled
        }

        return true;
    }

    /**
     * Tests whether a field is a "stored", as per its mapping metadata
     */
    private boolean fieldIsStored(final String fieldName, final FieldMappingMetadata field) {
        try {
            @SuppressWarnings("unchecked") // Need to get at the field mapping properties
            final Map<String, Object> propertiesMap = (Map<String, Object>) field.sourceAsMap().get(fieldName);
            final Boolean stored = (Boolean) propertiesMap.get("store");

            return stored != null && stored;
        } catch (Exception e) {
            return false;
        }
    }

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
                            @SuppressWarnings("unchecked")
                            final Map<String, Object> source =
                                    (Map<String, Object>) mapping.sourceAsMap().get(fieldName);
                            final Object fields = source.get("fields");

                            if (fields instanceof Map) {
                                @SuppressWarnings("unchecked")
                                final Map<String, Object> multiFields = (Map<String, Object>) fields;

                                multiFields.forEach((multiFieldName, multiFieldMapping) -> {
                                    final String fullName = mapping.fullName() + "." + multiFieldName;
                                    multiFieldMappings.add(fullName);
                                });
                            }
                        }
                    }));

                    allMappings.values().forEach(indexMappings -> {
                        indexMappings.forEach((fieldName, mapping) -> {
                            if (!mappings.containsKey(fieldName) && !multiFieldMappings.contains(mapping.fullName())) {
                                mappings.put(fieldName, mapping);
                            }
                        });
                    });

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
}

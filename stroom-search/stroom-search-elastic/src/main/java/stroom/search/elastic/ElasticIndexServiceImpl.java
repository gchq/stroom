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

import stroom.datasource.api.v2.DataSourceField;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.search.elastic.shared.ElasticCluster;
import stroom.search.elastic.shared.ElasticIndex;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetFieldMappingsRequest;
import org.elasticsearch.client.indices.GetFieldMappingsResponse;
import org.elasticsearch.client.indices.GetFieldMappingsResponse.FieldMappingMetadata;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Singleton
public class ElasticIndexServiceImpl implements ElasticIndexService {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexServiceImpl.class);

    private final ElasticClientCache elasticClientCache;
    private final ElasticClusterStore elasticClusterStore;

    @Inject
    public ElasticIndexServiceImpl(
        final ElasticClientCache elasticClientCache,
        final ElasticClusterStore elasticClusterStore
    ) {
        this.elasticClientCache = elasticClientCache;
        this.elasticClusterStore = elasticClusterStore;
    }

    public List<DataSourceField> getDataSourceFields(ElasticIndex index) {
        final Map<String, FieldMappingMetadata> fieldMappings = getFieldMappings(index);

        return fieldMappings.entrySet().stream()
                .map(field -> {
                    final FieldMappingMetadata fieldMeta = field.getValue();
                    final Optional<Entry<String, Object>> firstFieldEntry = fieldMeta.sourceAsMap().entrySet().stream().findFirst();

                    if (firstFieldEntry.isPresent()) {
                        final Object properties = firstFieldEntry.get().getValue();
                        final String fieldName = fieldMeta.fullName();

                        if (properties instanceof Map) {
                            @SuppressWarnings("unchecked") // Need to get at the nested properties, which is always a map
                            final Map<String, Object> propertiesMap = (Map<String, Object>) properties;
                            final String nativeType = (String) propertiesMap.get("type");

                            try {
                                final ElasticIndexFieldType elasticFieldType = ElasticIndexFieldType.fromNativeType(fieldName, nativeType);
                                return new DataSourceField.Builder()
                                        .type(elasticFieldType.getDataSourceFieldType())
                                        .name(fieldName)
                                        .queryable(true)
                                        .addConditions(elasticFieldType.getSupportedConditions().toArray(new Condition[0]))
                                        .build();
                            }
                            catch (IllegalArgumentException e) {
                                LOGGER.warn(e::getMessage);
                                return null;
                            }
                        }
                        else {
                            LOGGER.debug(() -> "Mapping properties for field '" + field.getKey() + "' were in an unrecognised format. Field ignored.");
                            return null;
                        }
                    }

                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<ElasticIndexField> getFields(final ElasticIndex index) {
        return new ArrayList<>(getFieldsMap(index).values());
    }

    @Override
    public Map<String, ElasticIndexField> getFieldsMap(final ElasticIndex index) {
        final Map<String, FieldMappingMetadata> fieldMappings = getFieldMappings(index);
        final Map<String, ElasticIndexField> fieldsMap = new HashMap<>();

        fieldMappings.forEach((key, fieldMeta) -> {
            final Optional<Entry<String, Object>> firstFieldEntry = fieldMeta.sourceAsMap().entrySet().stream().findFirst();
            if (firstFieldEntry.isPresent()) {
                final Object properties = firstFieldEntry.get().getValue();
                if (properties instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked") // Need to get at the nested properties, which is always a map
                        final Map<String, Object> propertiesMap = (Map<String, Object>) properties;
                        final String fieldName = fieldMeta.fullName();
                        final String fieldType = (String) propertiesMap.get("type");
                        final boolean sourceFieldEnabled = sourceFieldIsEnabled(fieldMappings);
                        final boolean stored = fieldIsStored(key, fieldMeta);

                        fieldsMap.put(fieldName, new ElasticIndexField(
                                ElasticIndexFieldType.fromNativeType(fieldName, fieldType),
                                fieldName,
                                fieldType,
                                sourceFieldEnabled || stored,
                                true
                        ));
                    }
                    catch (Exception e) {
                        LOGGER.error(e::getMessage, e);
                    }
                }
                else {
                    LOGGER.debug(() -> "Mapping properties for field '" + key + "' were in an unrecognised format. Field ignored.");
                }
            }
        });

        return fieldsMap;
    }

    /**
     * Get a filtered list of any field mappings with `stored` equal to `true`
     */
    @Override
    public List<String> getStoredFields(final ElasticIndex index) {
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
        }
        catch (Exception ignored) {
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
        }
        catch (Exception e) {
            return false;
        }
    }

    private Map<String, FieldMappingMetadata> getFieldMappings(final ElasticIndex elasticIndex) {
        try {
            final ElasticCluster elasticCluster = elasticClusterStore.readDocument(elasticIndex.getClusterRef());

            return elasticClientCache.contextResult(elasticCluster.getConnectionConfig(), elasticClient -> {
                final String indexName = elasticIndex.getIndexName();
                final GetFieldMappingsRequest request = new GetFieldMappingsRequest();
                request.indices(indexName);
                request.fields("*");

                try {
                    final GetFieldMappingsResponse response = elasticClient.indices().getFieldMapping(request, RequestOptions.DEFAULT);
                    final Map<String, Map<String, FieldMappingMetadata>> allMappings = response.mappings();

                    return allMappings.get(indexName);
                }
                catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                }

                return new HashMap<>();
            });
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            return new HashMap<>();
        }
    }
}

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
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Singleton
public class ElasticIndexServiceImpl implements ElasticIndexService {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexServiceImpl.class);

    private final ElasticClientCache elasticClientCache;

    @Inject
    public ElasticIndexServiceImpl(final ElasticClientCache elasticClientCache) {
        this.elasticClientCache = elasticClientCache;
    }

    public List<DataSourceField> getDataSourceFields(ElasticIndex index) {
        final Map<String, FieldMappingMetadata> fieldMappings = getFieldMappings(index);

        return fieldMappings.entrySet().stream()
                .map(field -> {
                    final Object properties = field.getValue().sourceAsMap().get(field.getKey());

                    if (properties instanceof Map) {
                        @SuppressWarnings("unchecked") // Need to get at the nested properties, which is always a map
                        final Map<String, Object> propertiesMap = (Map<String, Object>) properties;
                        final String nativeType = (String) propertiesMap.get("type");

                        try {
                            final ElasticIndexFieldType elasticFieldType = ElasticIndexFieldType.fromNativeType(field.getValue().fullName(), nativeType);
                            return new DataSourceField.Builder()
                                    .type(elasticFieldType.getDataSourceFieldType())
                                    .name(field.getValue().fullName())
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
                        LOGGER.warn(() -> "Mapping properties for field '" + field.getKey() + "' were in an unrecognised format. Field ignored.");
                        return null;
                    }
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

        fieldMappings.forEach((key, value) -> {
            final Object properties = value.sourceAsMap().get(key);

            if (properties instanceof Map) {
                try {
                    @SuppressWarnings("unchecked") // Need to get at the nested properties, which is always a map
                    final Map<String, Object> propertiesMap = (Map<String, Object>) properties;
                    final String fieldName = value.fullName();
                    final String fieldType = (String) propertiesMap.get("type");
                    final Boolean stored = (Boolean) propertiesMap.get("store");

                    fieldsMap.put(fieldName, new ElasticIndexField(
                            ElasticIndexFieldType.fromNativeType(fieldName, fieldType),
                            fieldName,
                            fieldType,
                            stored != null && stored,
                            true
                    ));
                }
                catch (Exception e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
            else {
                LOGGER.warn(() -> "Mapping properties for field '" + key + "' were in an unrecognised format. Field ignored.");
            }
        });

        return fieldsMap;
    }

    /**
     * Get a filtered list of any field mappings with `stored` equal to `true`
     */
    @Override
    public List<String> getStoredFields(final ElasticIndex index) {
        return getFieldMappings(index).entrySet().stream()
            .filter(mapping -> fieldIsStored(mapping.getKey(), mapping.getValue()))
            .map(mapping -> mapping.getValue().fullName())
            .collect(Collectors.toList());
    }

    /**
     * Tests whether a field is a "stored", as per its mapping metadata
     */
    private boolean fieldIsStored(final String fieldName, final FieldMappingMetadata field) {
        try {
            @SuppressWarnings("unchecked") // Need to get at the field mapping properties
            final Map<String, Object> properties = (Map<String, Object>) field.sourceAsMap().get(fieldName);
            final Boolean stored = (Boolean) properties.get("store");

            return stored != null && stored;
        }
        catch (Exception e) {
            return false;
        }
    }

    private Map<String, FieldMappingMetadata> getFieldMappings(final ElasticIndex elasticIndex) {
        try {
            return elasticClientCache.contextResult(elasticIndex.getConnectionConfig(), elasticClient -> {
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

                return null;
            });
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            return new HashMap<>();
        }
    }
}

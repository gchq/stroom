package stroom.search.elastic;

import stroom.datasource.api.v2.DataSourceField;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionTerm;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ElasticIndexServiceImpl implements ElasticIndexService {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexServiceImpl.class);

    private final ElasticIndexStore elasticIndexStore;
    private final ElasticClientCache elasticClientCache;

    @Inject
    public ElasticIndexServiceImpl(final ElasticIndexStore elasticIndexStore,
                                   final ElasticClientCache elasticClientCache
    ) {
        this.elasticIndexStore = elasticIndexStore;
        this.elasticClientCache = elasticClientCache;
    }

    public List<DataSourceField> getDataSourceFields(ElasticIndex index) {
        final Map<String, FieldMappingMetadata> fieldMappings = getFieldMappings(index);

        return fieldMappings.values().stream()
            .map(field -> {
                final Map<String, Object> properties = field.sourceAsMap();
                final String nativeType = (String)properties.get("type");

                try {
                    ElasticIndexFieldType elasticFieldType = ElasticIndexFieldType.fromNativeType(field.fullName(), nativeType);
                    return new DataSourceField.Builder()
                        .type(elasticFieldType.getDataSourceFieldType())
                        .name(field.fullName())
                        .queryable(true)
                        .addConditions(elasticFieldType.getSupportedConditions().toArray(new Condition[0]))
                        .build();
                }
                catch (IllegalArgumentException e) {
                    LOGGER.warn(e::getMessage);
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

    /**
     * Query field mappings for this index
     */
    public Map<String, ElasticIndexField> getFieldsMap(final DocRef docRef) {
        final ElasticIndex index = elasticIndexStore.read(docRef.getUuid());
        if (index == null) {
            throw new RuntimeException("Elasticsearch index not found for: '" + docRef.getUuid() + "'");
        }

        return getFieldsMap(index);
    }

    @Override
    public Map<String, ElasticIndexField> getFieldsMap(final ElasticIndex index) {
        final Map<String, FieldMappingMetadata> fieldMappings = getFieldMappings(index);
        final Map<String, ElasticIndexField> fieldsMap = new HashMap<>();

        fieldMappings.values().forEach(mapping -> {
            final Map<String, Object> properties = mapping.sourceAsMap();
            final String fieldType = (String) properties.get("type");
            final boolean stored = Boolean.parseBoolean((String) properties.get("stored"));

            try {
                fieldsMap.put(mapping.fullName(), new ElasticIndexField(
                    ElasticIndexFieldType.fromNativeType(mapping.fullName(), fieldType),
                    mapping.fullName(),
                    fieldType,
                    stored,
                    true
                ));
            }
            catch (IllegalArgumentException e) {
                LOGGER.warn(e::getMessage, e);
            }
        });

        return fieldsMap;
    }

    /**
     * Get a filtered list of any field mappings with `stored` equal to `true`
     */
    @Override
    public List<String> getStoredFields(final ElasticIndex index) {
        return getFieldMappings(index).values().stream()
                .filter(field -> field.sourceAsMap().get("stored").equals("true"))
                .map(FieldMappingMetadata::fullName)
                .collect(Collectors.toList());
    }

    private Map<String, FieldMappingMetadata> getFieldMappings(final ElasticIndex elasticIndex) {
        // TODO: Support nested fields through recursion
        try {
            return elasticClientCache.contextResult(elasticIndex.getConnectionConfig(), elasticClient -> {
                final String indexName = elasticIndex.getIndexName();
                final GetFieldMappingsRequest request = new GetFieldMappingsRequest();
                request.indices(indexName);
                request.fields();

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

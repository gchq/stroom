package stroom.search.elastic;

import stroom.query.api.v2.DocRef;
import stroom.search.elastic.shared.ElasticIndex;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetFieldMappingsRequest;
import org.elasticsearch.client.indices.GetFieldMappingsResponse;
import org.elasticsearch.client.indices.GetFieldMappingsResponse.FieldMappingMetadata;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class ElasticIndexServiceImpl implements ElasticIndexService {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexServiceImpl.class);

    private final ElasticIndexStore elasticIndexStore;
    private final ElasticClientCache elasticClientCache;

    public ElasticIndexServiceImpl(final ElasticIndexStore elasticIndexStore,
                                   final ElasticClientCache elasticClientCache) {
        this.elasticIndexStore = elasticIndexStore;
        this.elasticClientCache = elasticClientCache;
    }

    /**
     * Query field mappings for this index
     */
    public Map<String, ElasticIndexField> getFieldsMap(final DocRef docRef) {
        final Map<String, FieldMappingMetadata> fieldMappings = getFieldMappings(docRef);

        // TODO: Convert to fields map

        return null;
    }

    private Map<String, FieldMappingMetadata> getFieldMappings(final DocRef docRef) {
        try {
            final ElasticIndex elasticIndex = elasticIndexStore.read(docRef.getUuid());
            if (elasticIndex != null) {
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
            }
            else {
                throw new RuntimeException("Elasticsearch index not found for: '" + docRef.getUuid() + "'");
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            return null;
        }
    }
}

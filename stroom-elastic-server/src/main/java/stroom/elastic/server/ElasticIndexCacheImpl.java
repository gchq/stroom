package stroom.elastic.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.http.HttpStatus;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.ClientProperties;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.query.api.v2.DocRef;
import stroom.util.cache.CacheManager;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class ElasticIndexCacheImpl implements ElasticIndexCache {
    private static final int MAX_CACHE_ENTRIES = 100;

    private final LoadingCache<DocRef, ElasticIndexConfig> cache;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Function<String, String> explorerFetchUrl;

    @Inject
    ElasticIndexCacheImpl(final CacheManager cacheManager,
                          final StroomPropertyService propertyService) {
        this.explorerFetchUrl = (uuid) ->
                String.format("%s/%s", propertyService.getProperty(ClientProperties.URL_ELASTIC_EXPLORER), uuid);

        final CacheLoader<DocRef, ElasticIndexConfig> cacheLoader = CacheLoader.from(k -> {
            try {
                final HttpResponse<String> response = Unirest
                        .get(explorerFetchUrl.apply(k.getUuid()))
                        .header("accept", MediaType.APPLICATION_JSON)
                        .asString();
                if (response.getStatus() != HttpStatus.SC_OK) {
                    final String msg = String.format("Invalid status returned by Elastic Explorer Service: %d - %s ",
                            response.getStatus(),
                            response.getBody());
                    throw new RuntimeException(msg);
                }

                return objectMapper.readValue(response.getBody(), ElasticIndexConfig.class);
            } catch (UnirestException | IOException e) {
                throw new LoggedException(String.format("Failed to retrieve elastic index config for %s", k.getUuid()), e);
            }
        });

        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(10, TimeUnit.MINUTES);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Elastic Index Config Cache", cacheBuilder, cache);
    }

    @Override
    public ElasticIndexConfig get(final DocRef key) {
        return cache.getUnchecked(key);
    }

    @Override
    public void remove(final DocRef key) {
        cache.invalidate(key);
    }
}

package stroom.elastic.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import net.sf.ehcache.CacheManager;
import org.apache.http.HttpStatus;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.ClientProperties;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.query.api.v2.DocRef;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class ElasticIndexCacheImpl extends AbstractCacheBean<DocRef, ElasticIndexConfig> implements ElasticIndexCache {
    private static final int MAX_CACHE_ENTRIES = 100;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Function<String, String> explorerFetchUrl;

    @Inject
    ElasticIndexCacheImpl(final CacheManager cacheManager,
                          final StroomPropertyService propertyService) {
        super(cacheManager, "Elastic Index Config Cache", MAX_CACHE_ENTRIES);
        this.explorerFetchUrl = (uuid) ->
                String.format("%s/%s", propertyService.getProperty(ClientProperties.URL_ELASTIC_EXPLORER), uuid);

        setMaxIdleTime(10, TimeUnit.MINUTES);
        setMaxLiveTime(10, TimeUnit.MINUTES);
    }

    @Override
    public ElasticIndexConfig getOrCreate(final DocRef key) {
        return computeIfAbsent(key, this::create);
    }

    private ElasticIndexConfig create(final DocRef key) {
        try {
            final HttpResponse<String> response = Unirest
                    .get(explorerFetchUrl.apply(key.getUuid()))
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
            throw new LoggedException(String.format("Failed to retrieve elastic index config for %s", key.getUuid()), e);
        }
    }
}

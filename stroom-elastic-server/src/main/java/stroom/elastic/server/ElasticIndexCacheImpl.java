package stroom.elastic.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.http.HttpStatus;
import org.springframework.stereotype.Component;
import stroom.elastic.shared.ElasticIndex;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.ClientProperties;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.query.api.v2.DocRef;
import stroom.util.cache.CacheManager;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static stroom.entity.server.ExternalDocumentEntityServiceImpl.BASE_URL_PROPERTY;

@Component
public class ElasticIndexCacheImpl implements ElasticIndexCache {
    private static final int MAX_CACHE_ENTRIES = 100;

    private final LoadingCache<DocRef, ElasticIndexConfig> cache;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Function<String, String> fetchDocRefUrl;

    @Inject
    ElasticIndexCacheImpl(final CacheManager cacheManager,
                          final StroomPropertyService propertyService) {
        final String urlPropKey = String.format(BASE_URL_PROPERTY, ElasticIndex.ENTITY_TYPE);
        this.fetchDocRefUrl = (uuid) ->
                String.format("%s/%s", propertyService.getProperty(urlPropKey), uuid);

        final CacheLoader<DocRef, ElasticIndexConfig> cacheLoader = CacheLoader.from(k -> {
            HttpURLConnection connection = null;

            try {
                //Create connection
                URL url = new URL(fetchDocRefUrl.apply(k.getUuid()));
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("accept", MediaType.APPLICATION_JSON);

                //Get Response
                final InputStream is = connection.getInputStream();
                final BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                final StringBuilder response = new StringBuilder();
                String line;
                while ((line = rd.readLine()) != null) {
                    response.append(line);
                    response.append('\r');
                }
                rd.close();
                final String body = response.toString();

                if (connection.getResponseCode() != HttpStatus.SC_OK) {
                    final String msg = String.format("Invalid status returned by Elastic Explorer Service: %d - %s ",
                            connection.getResponseCode(),
                            body);
                    throw new RuntimeException(msg);
                }

                return objectMapper.readValue(body, ElasticIndexConfig.class);
            } catch (Exception e) {
                throw new LoggedException(String.format("Failed to retrieve elastic index config for %s", k.getUuid()), e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
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

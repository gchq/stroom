package stroom.elastic.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.http.HttpStatus;
import org.springframework.stereotype.Component;
import stroom.entity.shared.ExternalDocRefConstants;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.ClientProperties;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.query.api.v2.DocRef;
import stroom.query.audit.client.DocRefResourceHttpClient;
import stroom.query.audit.security.ServiceUser;
import stroom.security.SecurityContext;
import stroom.util.cache.CacheManager;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.concurrent.TimeUnit;

@Component
public class ElasticIndexCacheImpl implements ElasticIndexCache {
    private static final int MAX_CACHE_ENTRIES = 100;

    private final LoadingCache<DocRef, ElasticIndexConfig> cache;

    private final DocRefResourceHttpClient docRefHttpClient;
    private final SecurityContext securityContext;

    @Inject
    ElasticIndexCacheImpl(final CacheManager cacheManager,
                          final SecurityContext securityContext,
                          final StroomPropertyService propertyService) {
        final String urlPropKey = ClientProperties.URL_DOC_REF_SERVICE_BASE + ExternalDocRefConstants.ELASTIC_INDEX;
        docRefHttpClient = new DocRefResourceHttpClient(propertyService.getProperty(urlPropKey));

        this.securityContext = securityContext;

        final CacheLoader<DocRef, ElasticIndexConfig> cacheLoader = CacheLoader.from(k -> {
            try {
                final Response response = docRefHttpClient.get(serviceUser(), k.getUuid());

                if (response.getStatus() != HttpStatus.SC_OK) {
                    final String msg = String.format("Invalid status returned by Elastic Explorer Service: %d - %s ",
                            response.getStatus(),
                            response.readEntity(String.class));
                    throw new RuntimeException(msg);
                }

                return response.readEntity(ElasticIndexConfig.class);
            } catch (Throwable e) {
                throw new LoggedException(String.format("Failed to retrieve elastic index config for %s", k.getUuid()), e);
            }
        });

        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(10, TimeUnit.MINUTES);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Elastic Index Config Cache", cacheBuilder, cache);
    }

    private ServiceUser serviceUser() {
        return new ServiceUser.Builder()
                .jwt(securityContext.getApiToken())
                .name(securityContext.getUserId())
                .build();
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

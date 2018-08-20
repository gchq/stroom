package stroom.elastic.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.eclipse.jetty.http.HttpStatus;
import stroom.docref.DocRef;
import stroom.entity.shared.Clearable;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.query.audit.client.DocRefResourceHttpClient;
import stroom.query.security.ServiceUser;
import stroom.security.SecurityContext;
import stroom.ui.config.shared.UiConfig;
import stroom.util.cache.CacheManager;
import stroom.util.cache.CacheUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;
import java.util.concurrent.TimeUnit;

@Singleton
public class ElasticIndexConfigCacheImpl implements ElasticIndexConfigCache, Clearable {
    private static final int MAX_CACHE_ENTRIES = 100;

    private final LoadingCache<DocRef, ElasticIndexConfigDoc> cache;

    private final DocRefResourceHttpClient<ElasticIndexConfigDoc> docRefHttpClient;
    private final SecurityContext securityContext;

    @Inject
    ElasticIndexConfigCacheImpl(final CacheManager cacheManager,
                                final SecurityContext securityContext,
                                final UiConfig uiConfig) {
        docRefHttpClient = new DocRefResourceHttpClient<>(uiConfig.getUrlConfig().getElastic());
        this.securityContext = securityContext;

        final CacheLoader<DocRef, ElasticIndexConfigDoc> cacheLoader = CacheLoader.from(k -> {
            try {
                final Response response = docRefHttpClient.get(serviceUser(), k.getUuid());

                if (response.getStatus() != HttpStatus.OK_200) {
                    final String msg = String.format("Invalid status returned by Elastic Explorer Service: %d - %s ",
                            response.getStatus(),
                            response.readEntity(String.class));
                    throw new RuntimeException(msg);
                }

                return response.readEntity(ElasticIndexConfigDoc.class);
            } catch (final RuntimeException e) {
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
    public ElasticIndexConfigDoc get(final DocRef key) {
        return cache.getUnchecked(key);
    }

    @Override
    public void remove(final DocRef key) {
        cache.invalidate(key);
    }

    @Override
    public void clear() {
        CacheUtil.clear(cache);
    }
}

package stroom.elastic.impl;

import org.eclipse.jetty.http.HttpStatus;
import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.docref.DocRef;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.query.audit.client.DocRefResourceHttpClient;
import stroom.query.security.ServiceUser;
import stroom.security.api.SecurityContext;
import stroom.ui.config.shared.UiConfig;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;

@Singleton
public class ElasticIndexConfigCacheImpl implements ElasticIndexConfigCache, Clearable {
    private static final String CACHE_NAME = "Elastic Index Config Cache";

    private final DocRefResourceHttpClient<ElasticIndexConfigDoc> docRefHttpClient;
    private final SecurityContext securityContext;
    private final ICache<DocRef, ElasticIndexConfigDoc> cache;

    @Inject
    ElasticIndexConfigCacheImpl(final CacheManager cacheManager,
                                final SecurityContext securityContext,
                                final UiConfig uiConfig,
                                final ElasticConfig elasticConfig) {
        docRefHttpClient = new DocRefResourceHttpClient<>(uiConfig.getUrlConfig().getElastic());
        this.securityContext = securityContext;
        cache = cacheManager.create(CACHE_NAME, elasticConfig::getElasticIndexConfigCache, this::create);
    }

    private ElasticIndexConfigDoc create(final DocRef docRef) {
        try {
            final Response response = docRefHttpClient.get(serviceUser(), docRef.getUuid());

            if (response.getStatus() != HttpStatus.OK_200) {
                final String msg = String.format("Invalid status returned by Elastic Explorer Service: %d - %s ",
                        response.getStatus(),
                        response.readEntity(String.class));
                throw new RuntimeException(msg);
            }

            return response.readEntity(ElasticIndexConfigDoc.class);
        } catch (final RuntimeException e) {
            throw new LoggedException(String.format("Failed to retrieve elastic index config for %s", docRef.getUuid()), e);
        }
    }

    private ServiceUser serviceUser() {
        return new ServiceUser.Builder()
                .jwt(securityContext.getUserIdentity().getJws())
                .name(securityContext.getUserId())
                .build();
    }

    @Override
    public ElasticIndexConfigDoc get(final DocRef key) {
        return cache.get(key);
    }

    @Override
    public void remove(final DocRef key) {
        cache.invalidate(key);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}

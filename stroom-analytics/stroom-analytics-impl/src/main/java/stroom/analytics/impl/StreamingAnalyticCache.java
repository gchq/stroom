package stroom.analytics.impl;

import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.query.api.v2.SearchRequest;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.NullSafe;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.PermissionException;
import stroom.view.shared.ViewDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
@EntityEventHandler(
        type = AnalyticRuleDoc.DOCUMENT_TYPE,
        action = {EntityAction.DELETE, EntityAction.UPDATE, EntityAction.CLEAR_CACHE})
public class StreamingAnalyticCache implements Clearable, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamingAnalyticCache.class);
    private static final String STREAMING_ANALYTIC_CACHE = "Streaming Analytic Cache";

    private final AnalyticHelper analyticHelper;
    private final AnalyticRuleStore analyticRuleStore;
    private final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper;
    private final LoadingStroomCache<DocRef, StreamingAnalytic> cache;
    private final SecurityContext securityContext;

    @Inject
    public StreamingAnalyticCache(final AnalyticHelper analyticHelper,
                                  final AnalyticRuleStore analyticRuleStore,
                                  final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper,
                                  final CacheManager cacheManager,
                                  final Provider<AnalyticsConfig> analyticsConfigProvider,
                                  final SecurityContext securityContext) {
        this.analyticHelper = analyticHelper;
        this.analyticRuleStore = analyticRuleStore;
        this.analyticRuleSearchRequestHelper = analyticRuleSearchRequestHelper;
        this.securityContext = securityContext;
        cache = cacheManager.createLoadingCache(
                STREAMING_ANALYTIC_CACHE,
                () -> analyticsConfigProvider.get().getStreamingAnalyticCache(),
                this::loadStreamingAnalytic);
    }

    public StreamingAnalytic get(final DocRef analyticRuleRef) {
        if (!securityContext.hasDocumentPermission(analyticRuleRef.getUuid(), DocumentPermissionNames.USE)) {
            throw new PermissionException(securityContext.getUserIdentityForAudit(),
                    "You do not have permission to use this analytic doc");
        }
        return cache.get(analyticRuleRef);
    }

    private StreamingAnalytic loadStreamingAnalytic(final DocRef analyticRuleRef) {
        return securityContext.asProcessingUserResult(() -> {
            try {
                LOGGER.debug("Loading streaming analytic: {}", analyticRuleRef);
                final LogExecutionTime logExecutionTime = new LogExecutionTime();
                LOGGER.info(() -> "Loading rule");
                final AnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(analyticRuleRef);

                ViewDoc viewDoc;

                // Try and get view.
                final String ruleIdentity = AnalyticUtil.getAnalyticRuleIdentity(analyticRuleDoc);
                final SearchRequest searchRequest = analyticRuleSearchRequestHelper
                        .create(analyticRuleDoc);
                final DocRef dataSource = searchRequest.getQuery().getDataSource();

                if (dataSource == null || !ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
                    throw new RuntimeException("Error: Rule needs to reference a view");

                } else {
                    // Load view.
                    viewDoc = analyticHelper.loadViewDoc(ruleIdentity, dataSource);
                }

                LOGGER.info(() -> LogUtil.message("Finished loading rules in {}", logExecutionTime));
                return new StreamingAnalytic(
                        ruleIdentity,
                        analyticRuleDoc,
                        searchRequest,
                        viewDoc);
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
                throw e;
            }
        });
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("Received event {}", event);
        final EntityAction eventAction = event.getAction();

        switch (eventAction) {
            case CLEAR_CACHE -> {
                LOGGER.debug("Clearing cache");
                clear();
            }
            case UPDATE, DELETE -> {
                NullSafe.consume(
                        event.getDocRef(),
                        docRef -> {
                            LOGGER.debug("Invalidating docRef {}", docRef);
                            cache.invalidate(docRef);
                        });
            }
            default -> LOGGER.debug("Unexpected event action {}", eventAction);
        }
    }
}

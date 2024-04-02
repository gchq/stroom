package stroom.analytics.impl;

import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.query.api.v2.SearchRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.view.shared.ViewDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class StreamingAnalyticCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamingAnalyticCache.class);
    private static final String STREAMING_ANALYTIC_CACHE = "Streaming Analytic Cache";

    private final AnalyticHelper analyticHelper;
    private final AnalyticRuleStore analyticRuleStore;
    private final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper;
    private final LoadingStroomCache<DocRef, Optional<StreamingAnalytic>> cache;

    @Inject
    public StreamingAnalyticCache(final AnalyticHelper analyticHelper,
                                  final AnalyticRuleStore analyticRuleStore,
                                  final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper,
                                  final CacheManager cacheManager,
                                  final Provider<AnalyticsConfig> analyticsConfigProvider) {
        this.analyticHelper = analyticHelper;
        this.analyticRuleStore = analyticRuleStore;
        this.analyticRuleSearchRequestHelper = analyticRuleSearchRequestHelper;
        cache = cacheManager.createLoadingCache(
                STREAMING_ANALYTIC_CACHE,
                () -> analyticsConfigProvider.get().getStreamingAnalyticCache(),
                this::loadStreamingAnalytic);
    }

    public Optional<StreamingAnalytic> get(final DocRef analyticRuleRef) {
        return cache.get(analyticRuleRef);
    }

    private Optional<StreamingAnalytic> loadStreamingAnalytic(final DocRef analyticRuleRef) {
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
            return Optional.of(new StreamingAnalytic(
                    ruleIdentity,
                    analyticRuleDoc,
                    searchRequest,
                    viewDoc));
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
        return Optional.empty();
    }
}

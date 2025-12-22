/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.analytics.impl;

import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.query.api.SearchRequest;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.view.api.ViewStore;
import stroom.view.shared.ViewDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
@EntityEventHandler(
        type = AnalyticRuleDoc.TYPE,
        action = {EntityAction.DELETE, EntityAction.UPDATE, EntityAction.CLEAR_CACHE})
public class StreamingAnalyticCache implements Clearable, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamingAnalyticCache.class);
    private static final String STREAMING_ANALYTIC_CACHE = "Streaming Analytic Cache";

    private final ViewStore viewStore;
    private final AnalyticRuleStore analyticRuleStore;
    private final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper;
    private final LoadingStroomCache<DocRef, StreamingAnalytic> cache;
    private final SecurityContext securityContext;

    @Inject
    public StreamingAnalyticCache(final ViewStore viewStore,
                                  final AnalyticRuleStore analyticRuleStore,
                                  final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper,
                                  final CacheManager cacheManager,
                                  final Provider<AnalyticsConfig> analyticsConfigProvider,
                                  final SecurityContext securityContext) {
        this.viewStore = viewStore;
        this.analyticRuleStore = analyticRuleStore;
        this.analyticRuleSearchRequestHelper = analyticRuleSearchRequestHelper;
        this.securityContext = securityContext;
        cache = cacheManager.createLoadingCache(
                STREAMING_ANALYTIC_CACHE,
                () -> analyticsConfigProvider.get().getStreamingAnalyticCache(),
                this::loadStreamingAnalytic);
    }

    public StreamingAnalytic get(final DocRef analyticRuleRef) {
        if (!securityContext.hasDocumentPermission(analyticRuleRef, DocumentPermission.USE)) {
            throw new PermissionException(securityContext.getUserRef(),
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
                final AbstractAnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(analyticRuleRef);

                final ViewDoc viewDoc;

                // Try and get view.
                final String ruleIdentity = RuleUtil.getRuleIdentity(analyticRuleDoc);
                final SearchRequest searchRequest = analyticRuleSearchRequestHelper
                        .create(analyticRuleDoc);
                final DocRef dataSource = searchRequest.getQuery().getDataSource();

                if (dataSource == null || !ViewDoc.TYPE.equals(dataSource.getType())) {
                    throw new RuntimeException("Error: Rule needs to reference a view");

                } else {
                    // Load view.
                    viewDoc = loadViewDoc(ruleIdentity, dataSource);
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

    public ViewDoc loadViewDoc(final String ruleIdentity,
                               final DocRef viewDocRef) {
        final ViewDoc viewDoc = viewStore.readDocument(viewDocRef);
        if (viewDoc == null) {
            throw new RuntimeException("Unable to process analytic: " +
                                       ruleIdentity +
                                       " because selected view cannot be found");
        }
        if (viewDoc.getPipeline() == null) {
            throw new RuntimeException("Unable to process analytic: " +
                                       ruleIdentity +
                                       " because view does not specify a pipeline");
        }
        return viewDoc;
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

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

package stroom.proxy.app.handler;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.proxy.feed.remote.FeedStatus;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.receive.common.FeedStatusService;
import stroom.receive.common.GetFeedStatusRequestAdapter;
import stroom.receive.common.ReceiveDataConfig;
import stroom.security.api.CommonSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionSet;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Singleton
public class RemoteFeedStatusService implements FeedStatusService, Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteFeedStatusService.class);

    private static final AppPermissionSet REQUIRED_PERM_SET = AppPermissionSet.oneOf(
            AppPermission.STROOM_PROXY,
            AppPermission.CHECK_RECEIPT_STATUS);

    private static final String CACHE_NAME = "Remote Feed Status Response Cache";

    private final LoadingStroomCache<GetFeedStatusRequestV2, FeedStatusUpdater> updaters;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final Provider<CommonSecurityContext> securityContextProvider;
    private final RemoteFeedStatusClient remoteFeedStatusClient;
    private final GetFeedStatusRequestAdapter getFeedStatusRequestAdapter;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Inject
    RemoteFeedStatusService(final Provider<FeedStatusConfig> feedStatusConfigProvider,
                            final CacheManager cacheManager,
                            final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                            final Provider<CommonSecurityContext> securityContextProvider,
                            final RemoteFeedStatusClient remoteFeedStatusClient,
                            final GetFeedStatusRequestAdapter getFeedStatusRequestAdapter) {
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.securityContextProvider = securityContextProvider;
        this.remoteFeedStatusClient = remoteFeedStatusClient;
        this.getFeedStatusRequestAdapter = getFeedStatusRequestAdapter;
        this.updaters = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> feedStatusConfigProvider.get().getFeedStatusCache(),
                k -> new FeedStatusUpdater(executorService));
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        executorService.shutdownNow();
    }

    /**
     * @deprecated Use {@link FeedStatusService#getFeedStatus(GetFeedStatusRequestV2)}
     */
    @Deprecated
    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest legacyRequest) {
        return securityContextProvider.get().secureResult(REQUIRED_PERM_SET, () -> {
            final GetFeedStatusRequestV2 request = getFeedStatusRequestAdapter.mapLegacyRequest(legacyRequest);
            return getFeedStatus(request);
        });
    }

    @Override
    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequestV2 request) {
        return securityContextProvider.get().secureResult(REQUIRED_PERM_SET, () -> {
            final FeedStatus defaultFeedStatus = getDefaultFeedStatus();

            // If remote feed status checking is disabled then return the default status.
            if (!remoteFeedStatusClient.isDownstreamEnabled()) {
                // We shouldn't come in here anyway as the feed status filter will not be used
                // if feed status check is not enabled in config.
                return GetFeedStatusResponse.createOKResponse(defaultFeedStatus);
            } else {
                final FeedStatusUpdater feedStatusUpdater = updaters.get(request);
                final CachedResponse cachedResponse = feedStatusUpdater.get(lastResponse -> {
                    CachedResponse result;
                    try {
                        final GetFeedStatusResponse response = remoteFeedStatusClient.callFeedStatus(request);
                        result = new CachedResponse(Instant.now(), response);

                    } catch (final Exception e) {
                        LOGGER.debug("Unable to check remote feed service", e);
                        // Get the last response we received.
                        if (lastResponse != null) {
                            result = new CachedResponse(Instant.now(), lastResponse.getResponse());
                            LOGGER.error(
                                    "Unable to check remote feed service ({}).... will use last response ({}) - {}",
                                    request, result, e.getMessage());

                        } else {
                            // Revert to default behaviour.
                            result = new CachedResponse(Instant.now(),
                                    GetFeedStatusResponse.createOKResponse(defaultFeedStatus));
                            LOGGER.error(
                                    "Unable to check remote feed service ({}).... will assume OK ({}) - {}",
                                    request, result, e.getMessage());
                        }
                    }
                    return result;
                });
                return cachedResponse.getResponse();
            }
        });
    }

//    private boolean isFeedStatusCheckEnabled() {
//        final FeedStatusConfig feedStatusConfig = feedStatusConfigProvider.get();
//        final boolean hasUrl = NullSafe.isNonBlankString(feedStatusConfig.getFeedStatusUrl());
//        if (!hasUrl) {
//            LOGGER.debug("Feed status check requested but property '{}' not configured.",
//                    feedStatusConfig.getFullPath(FeedStatusConfig.PROP_NAME_URL));
//        }
//        return hasUrl;
//    }

    private FeedStatus getDefaultFeedStatus() {
        final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();
        return switch (receiveDataConfig.getReceiptCheckMode()) {
            case FEED_STATUS, RECEIPT_POLICY -> switch (receiveDataConfig.getFallbackReceiveAction()) {
                case RECEIVE -> FeedStatus.Receive;
                case REJECT -> FeedStatus.Reject;
                case DROP -> FeedStatus.Drop;
                case null -> FeedStatus.Receive;
            };
            case RECEIVE_ALL -> FeedStatus.Receive;
            case REJECT_ALL -> FeedStatus.Reject;
            case DROP_ALL -> FeedStatus.Drop;
            case null, default -> throw new IllegalStateException(
                    "Not expecting receiptCheckMode " + receiveDataConfig.getReceiptCheckMode());
        };
    }


    // --------------------------------------------------------------------------------


    private static class FeedStatusUpdater {

        private final Executor executor;
        private final AtomicBoolean updating = new AtomicBoolean();
        private volatile CachedResponse cachedResponse;

        public FeedStatusUpdater(final Executor executor) {
            this.executor = executor;
        }

        public CachedResponse get(final Function<CachedResponse, CachedResponse> function) {
            if (cachedResponse == null) {
                synchronized (this) {
                    if (cachedResponse == null) {
                        setCachedResponse(function.apply(cachedResponse));
                    }
                }
            }

            if (cachedResponse.isOld()) {
                LOGGER.debug("Response is old {}", cachedResponse);
                if (updating.compareAndSet(false, true)) {
                    CompletableFuture
                            .runAsync(() ->
                                    setCachedResponse(function.apply(cachedResponse)), executor)
                            .whenComplete((v, t) ->
                                    updating.set(false));
                }
            } else {
                LOGGER.debug("Response is fresh {}", cachedResponse);
            }

            return cachedResponse;
        }

        private synchronized void setCachedResponse(final CachedResponse cachedResponse) {
            LOGGER.debug("Setting cachedResponse to {}", cachedResponse);
            this.cachedResponse = cachedResponse;
        }
    }


    // --------------------------------------------------------------------------------


    private static class CachedResponse {

        private final Instant creationTime;
        private final GetFeedStatusResponse response;

        CachedResponse(final Instant creationTime, final GetFeedStatusResponse response) {
            this.creationTime = creationTime;
            this.response = response;
        }

        public boolean isOld() {
            return creationTime.isBefore(Instant.now().minus(1, ChronoUnit.MINUTES));
        }

        public GetFeedStatusResponse getResponse() {
            return response;
        }

        @Override
        public String toString() {
            return "CachedResponse{" +
                   "creationTime=" + creationTime +
                   ", response=" + response +
                   '}';
        }
    }
}

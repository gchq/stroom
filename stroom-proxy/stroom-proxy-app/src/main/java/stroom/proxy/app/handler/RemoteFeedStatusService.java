/*
 * Copyright 2026 Crown Copyright
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

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;

/**
 * Feed status as the downstream reports it, cached per feed so that receipt never waits on the
 * downstream for a feed it has seen before.
 * <p>
 * The first request for a feed loads its status synchronously, because there is nothing to answer
 * from. After that a request reads what is cached, and {@link #refreshStale()} - a registry
 * schedule, {@code feed-status-refresh}, every {@link #REFRESH_INTERVAL} - reloads any entry
 * older than {@link #MAX_AGE} that has been read since it was loaded, so what is refreshed is what
 * is in use and a feed nobody asks about costs nothing until someone does. A reload that fails
 * keeps the previous answer, or falls back to the configured default, and says so at ERROR. The
 * service owns no threads.
 * </p>
 */
@Singleton
public class RemoteFeedStatusService implements FeedStatusService {

    public static final Duration REFRESH_INTERVAL = Duration.ofSeconds(30);
    static final Duration MAX_AGE = Duration.ofMinutes(1);

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteFeedStatusService.class);

    private static final AppPermissionSet REQUIRED_PERM_SET = AppPermissionSet.oneOf(
            AppPermission.STROOM_PROXY,
            AppPermission.CHECK_RECEIPT_STATUS);

    private static final String CACHE_NAME = "Remote Feed Status Response Cache";

    private final LoadingStroomCache<GetFeedStatusRequestV2, CachedStatus> statuses;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final Provider<CommonSecurityContext> securityContextProvider;
    private final RemoteFeedStatusClient remoteFeedStatusClient;
    private final GetFeedStatusRequestAdapter getFeedStatusRequestAdapter;

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
        this.statuses = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> feedStatusConfigProvider.get().getFeedStatusCache(),
                CachedStatus::new);
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
            if (!remoteFeedStatusClient.isDownstreamEnabled()) {
                // The feed status filter is not used when the check is disabled, so this is a fallback.
                return GetFeedStatusResponse.createOKResponse(getDefaultFeedStatus());
            }
            return statuses.get(request).get();
        });
    }

    /**
     * Reload every cached status older than {@link #MAX_AGE}. Runs on the registry's schedule.
     */
    public void refreshStale() {
        refreshOlderThan(MAX_AGE);
    }

    void refreshOlderThan(final Duration age) {
        if (!remoteFeedStatusClient.isDownstreamEnabled()) {
            return;
        }
        final Instant start = Instant.now();
        final int[] refreshed = {0};
        statuses.forEach((request, status) -> {
            if (status.refreshIfOlderThan(age)) {
                refreshed[0]++;
            }
        });
        LOGGER.debug(() -> "Refreshed " + refreshed[0] + " feed statuses in " + Duration.between(start, Instant.now()));
    }

    private GetFeedStatusResponse fetch(final GetFeedStatusRequestV2 request,
                                        final GetFeedStatusResponse previous) {
        try {
            return remoteFeedStatusClient.callFeedStatus(request);
        } catch (final Exception e) {
            LOGGER.debug("Unable to check remote feed service", e);
            if (previous != null) {
                LOGGER.error("Unable to check remote feed service ({}).... will use last response ({}) - {}",
                        request, previous, e.getMessage());
                return previous;
            }
            final GetFeedStatusResponse fallback = GetFeedStatusResponse.createOKResponse(getDefaultFeedStatus());
            LOGGER.error("Unable to check remote feed service ({}).... will assume OK ({}) - {}",
                    request, fallback, e.getMessage());
            return fallback;
        }
    }

    private FeedStatus getDefaultFeedStatus() {
        final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();
        return switch (receiveDataConfig.getReceiptCheckMode()) {
            case FEED_STATUS, RECEIPT_POLICY, FEED_EXISTENCE -> switch (receiveDataConfig.getFallbackReceiveAction()) {
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


    /**
     * One feed's cached status. The first read loads it under the monitor so concurrent first
     * requests make one call; a refresh runs outside the monitor, on the schedule's thread, so a
     * slow downstream never holds up a request that has an answer to read. Only an entry read since
     * it was loaded is refreshed.
     */
    private class CachedStatus {

        private final GetFeedStatusRequestV2 request;
        private volatile GetFeedStatusResponse response;
        private volatile Instant loadedAt;
        private volatile boolean readSinceLoad;

        CachedStatus(final GetFeedStatusRequestV2 request) {
            this.request = request;
        }

        GetFeedStatusResponse get() {
            GetFeedStatusResponse current = response;
            if (current == null) {
                synchronized (this) {
                    current = response;
                    if (current == null) {
                        current = fetch(request, null);
                        set(current);
                    }
                }
            }
            readSinceLoad = true;
            return current;
        }

        /**
         * @return True if the status was reloaded.
         */
        boolean refreshIfOlderThan(final Duration age) {
            final Instant at = loadedAt;
            if (at == null || !readSinceLoad || at.isAfter(Instant.now().minus(age))) {
                return false;
            }
            LOGGER.debug("Refreshing feed status for {}", request);
            set(fetch(request, response));
            return true;
        }

        private void set(final GetFeedStatusResponse loaded) {
            response = loaded;
            loadedAt = Instant.now();
            readSinceLoad = false;
        }
    }
}

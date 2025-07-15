package stroom.proxy.app.handler;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.proxy.feed.remote.FeedStatus;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.receive.common.FeedStatusService;
import stroom.receive.common.GetFeedStatusRequestAdapter;
import stroom.security.api.UserIdentityFactory;
import stroom.util.HasHealthCheck;
import stroom.util.HealthCheckUtils;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.jersey.JerseyClientName;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.codahale.metrics.health.HealthCheck;
import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.Response.StatusType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Singleton
public class RemoteFeedStatusService implements FeedStatusService, HasHealthCheck, Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteFeedStatusService.class);

    private static final String CACHE_NAME = "Remote Feed Status Response Cache";
    private static final String GET_FEED_STATUS_PATH = "/getFeedStatus";

    private final LoadingStroomCache<GetFeedStatusRequestV2, FeedStatusUpdater> updaters;
    private final Provider<FeedStatusConfig> feedStatusConfigProvider;
    private final JerseyClientFactory jerseyClientFactory;
    private final UserIdentityFactory userIdentityFactory;
    private final GetFeedStatusRequestAdapter getFeedStatusRequestAdapter;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Inject
    RemoteFeedStatusService(final Provider<FeedStatusConfig> feedStatusConfigProvider,
                            final JerseyClientFactory jerseyClientFactory,
                            final UserIdentityFactory userIdentityFactory,
                            final GetFeedStatusRequestAdapter getFeedStatusRequestAdapter,
                            final CacheManager cacheManager) {
        this.feedStatusConfigProvider = feedStatusConfigProvider;
        this.jerseyClientFactory = jerseyClientFactory;
        this.userIdentityFactory = userIdentityFactory;
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
        final GetFeedStatusRequestV2 request = getFeedStatusRequestAdapter.mapLegacyRequest(legacyRequest);
        return getFeedStatus(request);
    }

    @Override
    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequestV2 request) {
        final FeedStatusConfig feedStatusConfig = feedStatusConfigProvider.get();
        final FeedStatus defaultFeedStatus = Objects.requireNonNullElse(
                feedStatusConfig.getDefaultStatus(),
                FeedStatus.Receive);

        // If remote feed status checking is disabled then return the default status.
        if (!feedStatusConfig.getEnabled()
            || NullSafe.isBlankString(feedStatusConfig.getFeedStatusUrl())) {
            return GetFeedStatusResponse.createOKResponse(defaultFeedStatus);
        } else {
            final FeedStatusUpdater feedStatusUpdater = updaters.get(request);
            final CachedResponse cachedResponse = feedStatusUpdater.get(lastResponse -> {
                CachedResponse result;
                try {
                    final GetFeedStatusResponse response = callFeedStatus(request);
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
    }

    private GetFeedStatusResponse callFeedStatus(final GetFeedStatusRequestV2 request) {
        final FeedStatusConfig feedStatusConfig = feedStatusConfigProvider.get();
        final String url = feedStatusConfig.getFeedStatusUrl();
        if (NullSafe.isBlankString(url)) {
            throw new RuntimeException("Missing remote status URL in feed status configuration");
        }

        return sendRequest(request, feedStatusConfig, response -> {
            GetFeedStatusResponse feedStatusResponse = null;
            final StatusType statusInfo = response.getStatusInfo();
            if (statusInfo.getStatusCode() != Status.OK.getStatusCode()) {
                LOGGER.error("Error checking feed status for '{}' using url '{}', got response {} - {}",
                        request.getFeedName(), url, statusInfo.getStatusCode(), statusInfo.getReasonPhrase());
            } else {
                feedStatusResponse = response.readEntity(GetFeedStatusResponse.class);
                LOGGER.info("Got feed status '{}' for '{}' using url '{}'",
                        feedStatusResponse.getStatus(), request.getFeedName(), url);
            }
            if (feedStatusResponse == null) {
                // If we can't get a feed status response then we will assume ok.
                feedStatusResponse = GetFeedStatusResponse.createOKReceiveResponse();
            }
            return feedStatusResponse;
        });
    }

    private GetFeedStatusResponse sendRequest(
            final GetFeedStatusRequestV2 request,
            final FeedStatusConfig feedStatusConfig,
            final Function<Response, GetFeedStatusResponse> responseConsumer) {

        LOGGER.debug(() -> LogUtil.message("Sending {} to {}{}",
                request,
                feedStatusConfig.getFeedStatusUrl(),
                GET_FEED_STATUS_PATH));

        final WebTarget webTarget = getFeedStatusWebTarget(feedStatusConfig);

        final DurationTimer timer = DurationTimer.start();
        try (final Response response = getFeedStatusResponse(feedStatusConfig, webTarget, request)) {
            LOGGER.debug("Received response {}, duration: {}", response, timer);
            return responseConsumer.apply(response);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error sending {} to {}{}, duration: {}, msg: {}",
                    request,
                    feedStatusConfig.getFeedStatusUrl(),
                    GET_FEED_STATUS_PATH,
                    timer,
                    e.getMessage()),
                    e);
        }
    }

    private WebTarget getFeedStatusWebTarget(final FeedStatusConfig feedStatusConfig) {
        return jerseyClientFactory.createWebTarget(
                        JerseyClientName.FEED_STATUS, feedStatusConfig.getFeedStatusUrl())
                .path(GET_FEED_STATUS_PATH);
    }

    private Response getFeedStatusResponse(final FeedStatusConfig feedStatusConfig,
                                           final WebTarget webTarget,
                                           final GetFeedStatusRequestV2 feedStatusRequest) {
        return webTarget
                .request(MediaType.APPLICATION_JSON)
                .headers(getHeaders(feedStatusConfig))
                .post(Entity.json(feedStatusRequest));
    }

    private MultivaluedMap<String, Object> getHeaders(final FeedStatusConfig feedStatusConfig) {
        final Map<String, String> headers;

        if (!NullSafe.isBlankString(feedStatusConfig.getApiKey())) {
            // Intended for when stroom is using its internal IDP. Create the API Key in stroom UI
            // and add it to config.
            LOGGER.debug(() -> LogUtil.message("Using API key from config prop {}",
                    feedStatusConfig.getFullPathStr(FeedStatusConfig.PROP_NAME_API_KEY)));
            headers = userIdentityFactory.getAuthHeaders(feedStatusConfig.getApiKey());
        } else {
            // Use a token from the external IDP
            headers = userIdentityFactory.getServiceUserAuthHeaders();
        }
        return new MultivaluedHashMap<>(headers);
    }

    @Override
    public HealthCheck.Result getHealth() {
        LOGGER.debug("getHealth called");
        final HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();
        final FeedStatusConfig feedStatusConfig = feedStatusConfigProvider.get();
        final String url = feedStatusConfig.getFeedStatusUrl();
        resultBuilder.withDetail("url", getFeedStatusWebTarget(feedStatusConfig).getUri().toString());

        if (NullSafe.isBlankString(url)) {
            // If no url is configured then no feed status checking is required so we consider this healthy
            resultBuilder.healthy();
        } else {
            final GetFeedStatusRequestV2 request = new GetFeedStatusRequestV2(
                    "DUMMY_FEED", null, null);
            try {
                sendRequest(request, feedStatusConfig, response -> {
                    final int responseCode = response.getStatusInfo().getStatusCode();
                    // Even though we have sent a dummy feed we should get back a 200 with something like
                    //{
                    //    "message": "Feed is not defined",
                    //        "status": "Reject",
                    //        "stroomStatusCode": "FEED_IS_NOT_DEFINED"
                    //}
                    final GetFeedStatusResponse feedStatusResponse;
                    if (Status.OK.getStatusCode() == responseCode) {
                        resultBuilder.healthy();
                        feedStatusResponse = null;
                    } else {
                        resultBuilder.withDetail("responseCode", responseCode);
                        feedStatusResponse = response.readEntity(GetFeedStatusResponse.class);
                        resultBuilder
                                .unhealthy()
                                .withDetail("response", HealthCheckUtils.beanToMap(feedStatusResponse));
                    }
                    return feedStatusResponse;
                });
            } catch (final Exception e) {
                resultBuilder.unhealthy(e);
            }
        }
        return resultBuilder.build();
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

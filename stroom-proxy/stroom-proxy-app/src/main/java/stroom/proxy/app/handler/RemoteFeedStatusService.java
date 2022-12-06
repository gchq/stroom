package stroom.proxy.app.handler;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.receive.common.FeedStatusService;
import stroom.security.common.impl.UserIdentityFactory;
import stroom.util.HasHealthCheck;
import stroom.util.HealthCheckUtils;
import stroom.util.NullSafe;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.cache.CacheConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.codahale.metrics.health.HealthCheck;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.dropwizard.lifecycle.Managed;

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
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.StatusType;

@Singleton
public class RemoteFeedStatusService implements FeedStatusService, HasHealthCheck, Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteFeedStatusService.class);

    private static final String GET_FEED_STATUS_PATH = "/getFeedStatus";

    private final LoadingCache<GetFeedStatusRequest, FeedStatusUpdater> updaters;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final Provider<ProxyConfig> proxyConfigProvider;
    private final Provider<FeedStatusConfig> feedStatusConfigProvider;
    private final Provider<Client> jerseyClientProvider;
    private final UserIdentityFactory userIdentityFactory;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Inject
    RemoteFeedStatusService(final Provider<ProxyConfig> proxyConfigProvider,
                            final Provider<FeedStatusConfig> feedStatusConfigProvider,
                            final Provider<Client> jerseyClientProvider,
                            final DefaultOpenIdCredentials defaultOpenIdCredentials,
                            final UserIdentityFactory userIdentityFactory) {
        this.proxyConfigProvider = proxyConfigProvider;
        this.feedStatusConfigProvider = feedStatusConfigProvider;
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
        this.jerseyClientProvider = jerseyClientProvider;
        this.userIdentityFactory = userIdentityFactory;

        final FeedStatusConfig feedStatusConfig = feedStatusConfigProvider.get();
        Objects.requireNonNull(feedStatusConfig, "Feed status config is null");

        final CacheConfig cacheConfig = feedStatusConfig.getFeedStatusCache();
        Objects.requireNonNull(cacheConfig, "Feed status cache config is null");
        this.updaters = createFromConfig(cacheConfig).build(k -> new FeedStatusUpdater(executorService));
    }

    private Caffeine createFromConfig(final CacheConfig cacheConfig) {
        final Caffeine cacheBuilder = Caffeine.newBuilder();
        cacheBuilder.recordStats();

        if (cacheConfig.getMaximumSize() != null) {
            cacheBuilder.maximumSize(cacheConfig.getMaximumSize());
        }
        if (cacheConfig.getExpireAfterAccess() != null) {
            cacheBuilder.expireAfterAccess(cacheConfig.getExpireAfterAccess().getDuration());
        }
        if (cacheConfig.getExpireAfterWrite() != null) {
            cacheBuilder.expireAfterWrite(cacheConfig.getExpireAfterWrite().getDuration());
        }
        return cacheBuilder;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        executorService.shutdownNow();
    }

//    private String getApiKey() {
//        // Allows us to use hard-coded open id creds / token to authenticate with stroom
//        // out of the box. ONLY for use in test/demo environments.
//        final FeedStatusConfig feedStatusConfig = feedStatusConfigProvider.get();
//        if (proxyConfigProvider.get().isUseDefaultOpenIdCredentials()
//                && Strings.isNullOrEmpty(feedStatusConfig.getApiKey())) {
//            LOGGER.info("Using default authentication token, should only be used in test/demo environments.");
//            return Objects.requireNonNull(defaultOpenIdCredentials.getApiKey());
//        } else {
//            return feedStatusConfig.getApiKey();
//        }
//    }

    @Override
    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest request) {
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
                    // Assume ok to receive by default.
                    result = new CachedResponse(Instant.now(), GetFeedStatusResponse.createOKRecieveResponse());
                    LOGGER.error(
                            "Unable to check remote feed service ({}).... will assume OK ({}) - {}",
                            request, result, e.getMessage());
                }
            }
            return result;
        });

        return cachedResponse.getResponse();
    }

    private GetFeedStatusResponse callFeedStatus(final GetFeedStatusRequest request) {
        final FeedStatusConfig feedStatusConfig = feedStatusConfigProvider.get();
        final String url = feedStatusConfig.getFeedStatusUrl();
        if (url == null || url.trim().length() == 0) {
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
                feedStatusResponse = GetFeedStatusResponse.createOKRecieveResponse();
            }
            return feedStatusResponse;
        });
    }

    private GetFeedStatusResponse sendRequest(
            final GetFeedStatusRequest request,
            final FeedStatusConfig feedStatusConfig,
            final Function<Response, GetFeedStatusResponse> responseConsumer) {

        LOGGER.debug("Sending request {}", request);

        final WebTarget webTarget = getFeedStatusWebTarget(feedStatusConfig);

        try (final Response response = getFeedStatusResponse(feedStatusConfig, webTarget, request)) {
            LOGGER.debug("Received response {}", response);
            return responseConsumer.apply(response);
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error sending request {} to {}{}: {}",
                    request, feedStatusConfig.getFeedStatusUrl(), GET_FEED_STATUS_PATH, e.getMessage()), e);
        }
    }

    private WebTarget getFeedStatusWebTarget(final FeedStatusConfig feedStatusConfig) {
        return jerseyClientProvider.get()
                .target(feedStatusConfig.getFeedStatusUrl())
                .path(GET_FEED_STATUS_PATH);
    }

    private Response getFeedStatusResponse(final FeedStatusConfig feedStatusConfig,
                                           final WebTarget webTarget,
                                           final GetFeedStatusRequest feedStatusRequest) {
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
            headers = userIdentityFactory.getAuthHeaders(userIdentityFactory.getServiceUserIdentity());
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

        if (url == null || url.trim().length() == 0) {
            // If no url is configured then no feed status checking is required so we consider this healthy
            resultBuilder.healthy();
        } else {
            final GetFeedStatusRequest request = new GetFeedStatusRequest(
                    "DUMMY_FEED",
                    "dummy DN");
            try {
                sendRequest(request, feedStatusConfig, response -> {
                    int responseCode = response.getStatusInfo().getStatusCode();
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
            } catch (Exception e) {
                resultBuilder.unhealthy(e);
            }
        }
        return resultBuilder.build();
    }

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
                if (updating.compareAndSet(false, true)) {
                    CompletableFuture
                            .runAsync(() -> setCachedResponse(function.apply(cachedResponse)), executor)
                            .whenComplete((v, t) -> updating.set(false));
                }
            }

            return cachedResponse;
        }

        private synchronized void setCachedResponse(final CachedResponse cachedResponse) {
            this.cachedResponse = cachedResponse;
        }
    }

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
    }
}

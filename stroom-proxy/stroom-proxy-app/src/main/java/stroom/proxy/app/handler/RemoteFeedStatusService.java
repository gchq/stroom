package stroom.proxy.app.handler;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.receive.common.FeedStatusService;
import stroom.util.HasHealthCheck;
import stroom.util.HealthCheckUtils;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LogUtil;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@Singleton
public class RemoteFeedStatusService implements FeedStatusService, HasHealthCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteFeedStatusService.class);

    private static final long ONE_MINUTE = 60_000;

    private static final String GET_FEED_STATUS_PATH = "/getFeedStatus";

    private final Map<GetFeedStatusRequest, CachedResponse> lastKnownResponse = new ConcurrentHashMap<>();
    private final WebTarget feedStatusWebTarget;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final Provider<ProxyConfig> proxyConfigProvider;
    private final Provider<FeedStatusConfig> feedStatusConfigProvider;

    @Inject
    RemoteFeedStatusService(final Provider<ProxyConfig> proxyConfigProvider,
                            final Provider<FeedStatusConfig> feedStatusConfigProvider,
                            final Client jerseyClient,
                            final DefaultOpenIdCredentials defaultOpenIdCredentials) {
        this.proxyConfigProvider = proxyConfigProvider;
        this.feedStatusConfigProvider = feedStatusConfigProvider;
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;

        final String url = feedStatusConfigProvider.get().getFeedStatusUrl();

        this.feedStatusWebTarget = jerseyClient
                .target(url)
                .path(GET_FEED_STATUS_PATH);
    }

    private String getApiKey() {
        // Allows us to use hard-coded open id creds / token to authenticate with stroom
        // out of the box. ONLY for use in test/demo environments.
        final FeedStatusConfig feedStatusConfig = feedStatusConfigProvider.get();
        if (proxyConfigProvider.get().isUseDefaultOpenIdCredentials()
                && Strings.isNullOrEmpty(feedStatusConfig.getApiKey())) {
            LOGGER.info("Using default authentication token, should only be used in test/demo environments.");
            return Objects.requireNonNull(defaultOpenIdCredentials.getApiKey());
        } else {
            return feedStatusConfig.getApiKey();
        }
    }

    @Override
    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest request) {
        final CachedResponse cachedResponse = lastKnownResponse.compute(request, (k, v) -> {
            CachedResponse result = v;

            if (result == null || result.getCreationTime() < System.currentTimeMillis() - ONE_MINUTE) {
                try {
                    final GetFeedStatusResponse response = callFeedStatus(request);
                    result = new CachedResponse(System.currentTimeMillis(), response);

                } catch (final Exception e) {
                    LOGGER.debug("Unable to check remote feed service", e);
                    // Get the last response we received.
                    if (result != null) {
                        LOGGER.error(
                                "Unable to check remote feed service ({}).... will use last response ({}) - {}",
                                request, result, e.getMessage());

                    } else {
                        // Assume ok to receive by default.
                        result = new CachedResponse(System.currentTimeMillis(),
                                GetFeedStatusResponse.createOKRecieveResponse());
                        LOGGER.error("Unable to check remote feed service ({}).... will assume OK ({}) - {}",
                                request, result, e.getMessage());
                    }

                    LOGGER.error("Error checking feed status", e);
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

        final String apiKey = getApiKey();
        if (apiKey == null || apiKey.trim().length() == 0) {
            throw new RuntimeException("Missing API key in the feed status configuration");
        }

        LOGGER.info("Checking feed status for {} using url '{}'", request.getFeedName(), url);
        return sendRequest(url, apiKey, request, response -> {
            GetFeedStatusResponse feedStatusResponse = null;
            if (response.getStatusInfo().getStatusCode() != Status.OK.getStatusCode()) {
                LOGGER.error(response.getStatusInfo().getReasonPhrase());
            } else {
                feedStatusResponse = response.readEntity(GetFeedStatusResponse.class);
            }
            if (feedStatusResponse == null) {
                // If we can't get a feed status response then we will assume ok.
                feedStatusResponse = GetFeedStatusResponse.createOKRecieveResponse();
            }
            return feedStatusResponse;
        });
    }

    private GetFeedStatusResponse sendRequest(final String url,
                                              final String apiKey,
                                              final GetFeedStatusRequest request,
                                              final Function<Response, GetFeedStatusResponse> responseConsumer) {
        LOGGER.debug("Sending request {}", request);
        try (final Response response = feedStatusWebTarget
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .post(Entity.json(request))) {
            LOGGER.debug("Received response {}", response);
            return responseConsumer.apply(response);
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error sending request {} to {}{}: {}",
                    request, url, GET_FEED_STATUS_PATH, e.getMessage()), e);
        }
    }


    @Override
    public HealthCheck.Result getHealth() {
        LOGGER.debug("getHealth called");
        final HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();
        final String apiKey = getApiKey();
        final String url = feedStatusConfigProvider.get().getFeedStatusUrl();
        resultBuilder.withDetail("url", feedStatusWebTarget.getUri().toString());

        if (url == null || url.trim().length() == 0) {
            // If no url is configured then no feed status checking is required so we consider this healthy
            resultBuilder.healthy();
        } else if (apiKey == null || apiKey.trim().length() == 0) {
            resultBuilder.unhealthy()
                    .withMessage("Missing API key in the feed status configuration");
        } else {
            final GetFeedStatusRequest request = new GetFeedStatusRequest("DUMMY_FEED", "dummy DN");
            try {
                sendRequest(url, apiKey, request, response -> {
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

    private static class CachedResponse {

        private final long creationTime;
        private final GetFeedStatusResponse response;

        CachedResponse(final long creationTime, final GetFeedStatusResponse response) {
            this.creationTime = creationTime;
            this.response = response;
        }

        public long getCreationTime() {
            return creationTime;
        }

        public GetFeedStatusResponse getResponse() {
            return response;
        }
    }
}

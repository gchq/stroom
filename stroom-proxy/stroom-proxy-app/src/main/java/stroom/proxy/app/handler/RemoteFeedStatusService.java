package stroom.proxy.app.handler;

import com.codahale.metrics.health.HealthCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.server.FeedStatusService;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.util.HasHealthCheck;
import stroom.util.HealthCheckUtils;
import stroom.util.logging.LambdaLogger;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class RemoteFeedStatusService implements FeedStatusService, HasHealthCheck {
    private static Logger LOGGER = LoggerFactory.getLogger(RemoteFeedStatusService.class);

    private static final long ONE_MINUTE = 60000;

    private final static String GET_FEED_STATUS_PATH = "/getFeedStatus";

    private final String url;
    private final String apiKey;
    private final Map<GetFeedStatusRequest, CachedResponse> lastKnownResponse = new ConcurrentHashMap<>();
    private final WebTarget feedStatusWebTarget;


    @Inject
    RemoteFeedStatusService(final FeedStatusConfig feedStatusConfig,
                            final Client jerseyClient) {
        this.url = feedStatusConfig.getFeedStatusUrl();
        this.apiKey = feedStatusConfig.getApiKey();

        this.feedStatusWebTarget = jerseyClient
                .target(url)
                .path(GET_FEED_STATUS_PATH);
    }

    @Override
    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest request) {
        // Assume ok to receive by default.
        GetFeedStatusResponse effectiveFeedStatusResponse = GetFeedStatusResponse.createOKRecieveResponse();

        final CachedResponse cachedResponse = lastKnownResponse.get(request);

        if (cachedResponse != null && cachedResponse.getCreationTime() >= System.currentTimeMillis() - ONE_MINUTE) {
            effectiveFeedStatusResponse = cachedResponse.getResponse();

        } else if (url != null && url.trim().length() > 0) {
            try {
                if (apiKey == null || apiKey.trim().length() == 0) {
                    throw new RuntimeException("Missing API key in the feed status configuration");
                }

                LOGGER.info("Checking feed status for {} using url '{}'", request.getFeedName(), url);
                effectiveFeedStatusResponse = sendRequest(request, response -> {
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
            } catch (final Exception e) {
                LOGGER.debug("Unable to check remote feed service", e);
                // Get the last response we received.
                if (cachedResponse != null) {
                    LOGGER.error(
                            "Unable to check remote feed service ({}).... will use last response ({}) - {}",
                            request, effectiveFeedStatusResponse, e.getMessage());
                    effectiveFeedStatusResponse = cachedResponse.getResponse();

                } else {
                    LOGGER.error("Unable to check remote feed service ({}).... will assume OK ({}) - {}",
                            request, effectiveFeedStatusResponse, e.getMessage());
                }

                LOGGER.error("Error checking feed status", e);
            }

            // Cache the response for next time.
            lastKnownResponse.put(request, new CachedResponse(System.currentTimeMillis(), effectiveFeedStatusResponse));
        }

        return effectiveFeedStatusResponse;
        }

    private GetFeedStatusResponse sendRequest(final GetFeedStatusRequest request,
                             final Function<Response, GetFeedStatusResponse> responseConsumer) {
        LOGGER.debug("Sending request {}", request);
        try {
            final Response response = feedStatusWebTarget
                    .request(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .post(Entity.json(request));
            try {
                LOGGER.debug("Received response {}", response);
                return responseConsumer.apply(response);
            } finally {
                response.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(LambdaLogger.buildMessage(
                    "Error sending request {} to {}{}: {}",
                    request, url, GET_FEED_STATUS_PATH, e.getMessage()), e);
        }
    }


    @Override
    public HealthCheck.Result getHealth() {
        LOGGER.debug("getHealth called");
        final HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();
        resultBuilder.withDetail("url", url);

        if (url == null || url.trim().length() == 0) {
            // If no url is configured then no feed status checking is required so we consider this healthy
            resultBuilder.healthy();
        } else if (apiKey == null || apiKey.trim().length() == 0) {
            resultBuilder.unhealthy()
                    .withMessage("Missing API key in the feed status configuration");
        } else {
            final GetFeedStatusRequest request = new GetFeedStatusRequest("DUMMY_FEED", "dummy DN");
            try {
                sendRequest(request, response -> {
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

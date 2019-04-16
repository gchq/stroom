package stroom.proxy.handler;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.server.FeedStatusService;
import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusResponse;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteFeedStatusService implements FeedStatusService {
    private static Logger LOGGER = LoggerFactory.getLogger(RemoteFeedStatusService.class);

    private static final long ONE_MINUTE = 60000;

    private final String url;
    private final String apiKey;
    private final Map<GetFeedStatusRequest, CachedResponse> lastKnownResponse = new ConcurrentHashMap<>();

    @Inject
    RemoteFeedStatusService(final FeedStatusConfig feedStatusConfig) {
        this.url = feedStatusConfig.getFeedStatusUrl();
        this.apiKey = feedStatusConfig.getApiKey();
    }

    @Override
    public GetFeedStatusResponse getFeedStatus(final GetFeedStatusRequest request) {
        // Assume ok to receive by default.
        GetFeedStatusResponse feedStatusResponse = new GetFeedStatusResponse();

        final CachedResponse cachedResponse = lastKnownResponse.get(request);
        if (cachedResponse != null && cachedResponse.getCreationTime() >= System.currentTimeMillis() - ONE_MINUTE) {
            feedStatusResponse = cachedResponse.getResponse();

        } else if (url != null && url.trim().length() > 0) {
            try {
                LOGGER.info("Checking feed status from '" + url + "'");
                final Response response = createClient(url, "/getFeedStatus").post(Entity.json(request));
                if (response.getStatusInfo().getStatusCode() != Status.OK.getStatusCode()) {
                    LOGGER.error(response.getStatusInfo().getReasonPhrase());
                } else {
                    feedStatusResponse = response.readEntity(GetFeedStatusResponse.class);
                    if (feedStatusResponse == null) {
                        // If we can't get a feed status response then we will assume ok.
                        feedStatusResponse = new GetFeedStatusResponse();
                    }
                }
            } catch (final Exception e) {
                LOGGER.debug("Unable to check remote feed service", e);
                // Get the last response we received.
                if (cachedResponse != null) {
                    LOGGER.error(
                            "Unable to check remote feed service ({}).... will use last response ({}) - {}",
                            request, feedStatusResponse, e.getMessage());
                    feedStatusResponse = cachedResponse.getResponse();

                } else {
                    LOGGER.error("Unable to check remote feed service ({}).... will assume OK ({}) - {}",
                            request, feedStatusResponse, e.getMessage());
                }

                LOGGER.error("Error checking feed status", e);
            }

            // Cache the response for next time.
            lastKnownResponse.put(request, new CachedResponse(System.currentTimeMillis(), feedStatusResponse));
        }

        return feedStatusResponse;
    }

    private Invocation.Builder createClient(final String url, final String path) {
        final Client client = ClientBuilder.newClient(new ClientConfig().register(LoggingFeature.class));
        final WebTarget webTarget = client.target(url).path(path);
        final Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        invocationBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        return invocationBuilder;
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

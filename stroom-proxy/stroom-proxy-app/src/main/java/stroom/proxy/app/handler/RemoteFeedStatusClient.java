package stroom.proxy.app.handler;

import stroom.proxy.app.AbstractDownstreamClient;
import stroom.proxy.app.DownstreamHostConfig;
import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.security.api.UserIdentityFactory;
import stroom.util.HasHealthCheck;
import stroom.util.HealthCheckUtils;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheck.Result;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.Response.StatusType;

import java.util.Optional;

public class RemoteFeedStatusClient extends AbstractDownstreamClient implements HasHealthCheck {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteFeedStatusClient.class);
    private final Provider<FeedStatusConfig> feedStatusConfigProvider;

    @Inject
    public RemoteFeedStatusClient(final JerseyClientFactory jerseyClientFactory,
                                  final UserIdentityFactory userIdentityFactory,
                                  final Provider<DownstreamHostConfig> downstreamHostConfigProvider,
                                  final Provider<FeedStatusConfig> feedStatusConfigProvider) {
        super(jerseyClientFactory, userIdentityFactory, downstreamHostConfigProvider);
        this.feedStatusConfigProvider = feedStatusConfigProvider;
    }

    @Override
    protected Optional<String> getConfiguredUrl() {
        return NullSafe.nonBlank(feedStatusConfigProvider.get().getFeedStatusUrl());
    }

    @Override
    protected String getDefaultPath() {
        return FeedStatusConfig.DEFAULT_URL_PATH;
    }

    public GetFeedStatusResponse callFeedStatus(final GetFeedStatusRequestV2 request) {
        final String url = getFullUrl();
        GetFeedStatusResponse feedStatusResponse = null;
        if (isDownstreamEnabled() && NullSafe.isNonBlankString(url)) {
            try {
                try (final Response response = getResponse(builder -> builder.post(Entity.json(request)))) {
                    final StatusType statusInfo = response.getStatusInfo();
                    if (statusInfo.getStatusCode() != Status.OK.getStatusCode()) {
                        LOGGER.error("Error fetching feed status/existence using url '{}', " +
                                     "got response {} - {}",
                                url, statusInfo.getStatusCode(), statusInfo.getReasonPhrase());
                    } else {
                        feedStatusResponse = response.readEntity(GetFeedStatusResponse.class);
                        LOGGER.debug("Got feed status '{}' for '{}' using url '{}'",
                                feedStatusResponse.getStatus(), request.getFeedName(), url);
                    }
                }
            } catch (final Throwable e) {
                final String fullUrl = getFullUrl();
                LOGGER.errorAndDebug(e, "Error fetching feed status/existence receive using url '{}': {}",
                        fullUrl, LogUtil.exceptionMessage(e));
            }
        } else {
            LOGGER.warn("No url configured for feed status/existence checking.");
        }
        if (feedStatusResponse == null) {
            // If we can't get a feed status response then we will assume ok.
            feedStatusResponse = GetFeedStatusResponse.createOKReceiveResponse();
        }
        return feedStatusResponse;
    }

    @Override
    public HealthCheck.Result getHealth() {
        LOGGER.debug("getHealth() called");
        if (!isDownstreamEnabled()) {
            return HealthCheckUtils.healthy("Downstream host disabled");
        } else {
            final GetFeedStatusRequestV2 request = new GetFeedStatusRequestV2(
                    "DUMMY_FEED", null, null);
            try {
                try (final Response response = getResponse(builder -> builder.post(Entity.json(request)))) {
                    return HealthCheckUtils.fromResponse(response)
                            .build();
                }
            } catch (final Throwable e) {
                return Result.unhealthy(e);
            }
        }
    }
}

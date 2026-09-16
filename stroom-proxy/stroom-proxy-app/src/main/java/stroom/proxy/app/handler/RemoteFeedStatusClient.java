/*
 * Copyright 2025 Crown Copyright
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

import stroom.proxy.app.AbstractDownstreamClient;
import stroom.proxy.app.DownstreamHostConfig;
import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.proxy.feed.remote.GetFeedStatusResponse;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.rules.shared.ReceiptCheckMode;
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
import com.codahale.metrics.health.HealthCheck.ResultBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.Response.StatusType;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class RemoteFeedStatusClient extends AbstractDownstreamClient implements HasHealthCheck {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteFeedStatusClient.class);
    private final Provider<FeedStatusConfig> feedStatusConfigProvider;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;

    private final AtomicLong lastSuccessfulCall = new AtomicLong(0);

    @Inject
    public RemoteFeedStatusClient(final JerseyClientFactory jerseyClientFactory,
                                  final UserIdentityFactory userIdentityFactory,
                                  final Provider<DownstreamHostConfig> downstreamHostConfigProvider,
                                  final Provider<FeedStatusConfig> feedStatusConfigProvider,
                                  final Provider<ReceiveDataConfig> receiveDataConfigProvider) {
        super(jerseyClientFactory, userIdentityFactory, downstreamHostConfigProvider);
        this.feedStatusConfigProvider = feedStatusConfigProvider;
        this.receiveDataConfigProvider = receiveDataConfigProvider;
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
                        lastSuccessfulCall.set(System.currentTimeMillis());
                    }
                }
            } catch (final Throwable e) {
                final String fullUrl = getFullUrl();
                LOGGER.errorAndDebug(e, "Error fetching feed status/existence receive using url '{}': {}",
                        fullUrl, LogUtil.exceptionMessage(e));
                throw new FeedStatusUnavailableException(LogUtil.message(
                        "Error fetching feed status/existence using url '{}': {}",
                        fullUrl, LogUtil.exceptionMessage(e)), e);
            }
        } else {
            LOGGER.warn("No url configured for feed status/existence checking.");
        }
        if (feedStatusResponse == null) {
            // No response is a failure, not an assumed "receive". Failing here does not decide the
            // outcome: the caller falls back to the last good response and then to the operator's
            // fallbackReceiveAction, which is the only thing that may decide it - an operator who set
            // REJECT to fail closed during a downstream outage must get REJECT.
            throw new FeedStatusUnavailableException(LogUtil.message(
                    "No feed status/existence response available using url '{}'", url));
        }
        return feedStatusResponse;
    }

    private Instant getLastSuccessfulCall() {
        final long epochMs = lastSuccessfulCall.get();
        return epochMs != 0
                ? Instant.ofEpochMilli(epochMs)
                : null;
    }

    @Override
    public HealthCheck.Result getHealth() {
        LOGGER.debug("getHealth() called");
        final ResultBuilder builder = HealthCheck.Result.builder();
        HealthCheckUtils.addTime(builder, "lastSuccessfulCall", getLastSuccessfulCall());
        if (!isDownstreamEnabled()) {
            builder.healthy()
                    .withMessage("Downstream host disabled");
        } else if (receiveDataConfigProvider.get().getReceiptCheckMode() != ReceiptCheckMode.FEED_STATUS
                   && receiveDataConfigProvider.get().getReceiptCheckMode() != ReceiptCheckMode.RECEIPT_POLICY) {
            builder.healthy()
                    .withMessage("Feed status checking disabled by receiptCheckMode");
        } else {
            final GetFeedStatusRequestV2 request = new GetFeedStatusRequestV2(
                    "DUMMY_FEED", null, null);
            try {
                try (final Response response = getResponse(requestBuilder ->
                        requestBuilder.post(Entity.json(request)))) {
                    HealthCheckUtils.fromResponse(builder, response, Status.OK, getFullUrl());
                }
            } catch (final Throwable e) {
                builder.unhealthy(e)
                        .withDetail("url", getFullUrl());
                return Result.unhealthy(e);
            }
        }
        return builder.build();
    }

    // --------------------------------------------------------------------------------


    /**
     * Thrown when a feed status could not be obtained from the downstream.
     * <p>
     * The point of this type is that it is <em>not</em> a decision. It tells
     * {@link RemoteFeedStatusService} that the check did not happen, so that the caller can apply
     * the last good response, and failing that the operator's configured
     * {@code fallbackReceiveAction}. Substituting a permissive answer here instead is what made
     * that configuration unreachable.
     * </p>
     */
    public static class FeedStatusUnavailableException extends RuntimeException {

        public FeedStatusUnavailableException(final String message) {
            super(message);
        }

        public FeedStatusUnavailableException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}

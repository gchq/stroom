/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.aws.s3.shared.S3EventResource;
import stroom.aws.s3.shared.S3EventResource.S3EventNotificationRequest;
import stroom.proxy.app.AbstractDownstreamClient;
import stroom.proxy.app.DownstreamHostConfig;
import stroom.security.api.UserIdentityFactory;
import stroom.util.HasHealthCheck;
import stroom.util.HealthCheckUtils;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheck.ResultBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class RemoteS3EventClient extends AbstractDownstreamClient implements HasHealthCheck {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteS3EventClient.class);

    private final AtomicLong lastSuccessfulCall = new AtomicLong(0);

    @Inject
    public RemoteS3EventClient(final JerseyClientFactory jerseyClientFactory,
                               final UserIdentityFactory userIdentityFactory,
                               final Provider<DownstreamHostConfig> downstreamHostConfigProvider) {
        super(jerseyClientFactory, userIdentityFactory, downstreamHostConfigProvider);
    }

    @Override
    protected Optional<String> getConfiguredUrl() {
        // This is mostly for testing so just rely on the downstream host and the hard coded path
        return Optional.empty();
    }

    @Override
    protected String getDefaultPath() {
        return ResourcePaths.buildAuthenticatedApiPath(
                S3EventResource.BASE_RESOURCE_PATH,
                S3EventResource.NOTIFY_PATH_PART);
    }

    public void sendNotification(final S3EventNotificationRequest request) {
        final String url = getFullUrl();
        LOGGER.debug("sendNotification() - url: {}, request: {}", url, request);

        if (isDownstreamEnabled() && NullSafe.isNonBlankString(url)) {
            try (final Response response = getResponse(url, builder -> builder.post(Entity.json(request)))) {
                LOGGER.debug("sendNotification() - url: {}, request: {}, response: {}", url, request, response);
                if (response.getStatus() != HttpServletResponse.SC_OK &&
                    response.getStatus() != HttpServletResponse.SC_NO_CONTENT) {
                    final String error;
                    try {
                        error = response.readEntity(String.class);
                        LOGGER.debug("sendNotification() - url: {}, request: {}, error: {}", url, request, error);
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                    throw new RuntimeException(LogUtil.message(
                            "Error sending S3 notification {} to {} - {} - {}", request, url, response, error));
                }
                lastSuccessfulCall.set(System.currentTimeMillis());
            } catch (final Exception e) {
                LOGGER.error("Error sending S3 notification to {}: {}", url, LogUtil.exceptionMessage(e), e);
                throw e;
            }
        } else {
            LOGGER.warn("Downstream is disabled or no URL configured for S3 notifications.");
        }
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
        } else {
            // We don't have a single URL for health check as it's now per forwarder.
            builder.healthy();
        }
        return builder.build();
    }
}

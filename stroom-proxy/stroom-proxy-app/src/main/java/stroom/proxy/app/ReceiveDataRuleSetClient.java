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

package stroom.proxy.app;

import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.rules.shared.HashedReceiveDataRules;
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
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.client.SyncInvoker;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.Response.StatusType;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Separate the rest call from {@link RemoteReceiveDataRuleSetServiceImpl} to make testing easier
 */
@Singleton
public class ReceiveDataRuleSetClient extends AbstractDownstreamClient implements HasHealthCheck {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceiveDataRuleSetClient.class);

    private final Provider<ProxyReceiptPolicyConfig> proxyReceiptPolicyConfigProvider;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;

    private final AtomicLong lastSuccessfulCall = new AtomicLong(0);

    @Inject
    public ReceiveDataRuleSetClient(final JerseyClientFactory jerseyClientFactory,
                                    final UserIdentityFactory userIdentityFactory,
                                    final Provider<DownstreamHostConfig> downstreamHostConfigProvider,
                                    final Provider<ProxyReceiptPolicyConfig> proxyReceiptPolicyConfigProvider,
                                    final Provider<ReceiveDataConfig> receiveDataConfigProvider) {
        super(jerseyClientFactory, userIdentityFactory, downstreamHostConfigProvider);
        this.proxyReceiptPolicyConfigProvider = proxyReceiptPolicyConfigProvider;
        this.receiveDataConfigProvider = receiveDataConfigProvider;
    }

    @Override
    protected Optional<String> getConfiguredUrl() {
        return NullSafe.nonBlank(proxyReceiptPolicyConfigProvider.get().getReceiveDataRulesUrl());
    }

    @Override
    protected String getDefaultPath() {
        return ProxyReceiptPolicyConfig.DEFAULT_URL_PATH;
    }

    public Optional<HashedReceiveDataRules> getHashedReceiveDataRules() {
        Optional<HashedReceiveDataRules> optHashedReceiveDataRules = Optional.empty();

        final String url = getFullUrl();
        if (NullSafe.isNonBlankString(url)) {
            try {
                try (final Response response = getResponse(Builder::get)) {
                    final StatusType statusInfo = response.getStatusInfo();
                    if (statusInfo.getStatusCode() != Status.OK.getStatusCode()) {
                        LOGGER.error("Error fetching receive data rules using url '{}', " +
                                     "got response {} - {}",
                                url, statusInfo.getStatusCode(), statusInfo.getReasonPhrase());
                    } else {
                        optHashedReceiveDataRules = Optional.ofNullable(
                                response.readEntity(HashedReceiveDataRules.class));
                        LOGGER.debug("getHashedReceiveDataRules() - optHashedReceiveDataRules from upstream: {}",
                                optHashedReceiveDataRules);
                        lastSuccessfulCall.set(System.currentTimeMillis());
                    }
                }
            } catch (final Throwable e) {
                final String fullUrl = getFullUrl();
                LOGGER.errorAndDebug(e, "Error fetching receive data rules using url '{}': {}",
                        fullUrl, LogUtil.exceptionMessage(e));
            }
        } else {
            LOGGER.warn("No url configured for receipt policy rules.");
        }
        LOGGER.debug("getHashedReceiveDataRules() - returning {}", optHashedReceiveDataRules);
        return optHashedReceiveDataRules;
    }

    private Instant getLastSuccessfulCall() {
        final long epochMs = lastSuccessfulCall.get();
        return epochMs != 0
                ? Instant.ofEpochMilli(epochMs)
                : null;
    }

    @Override
    public Result getHealth() {
        LOGGER.debug("getHealth() called");
        final ResultBuilder builder = HealthCheck.Result.builder();
        HealthCheckUtils.addTime(builder, "lastSuccessfulCall", getLastSuccessfulCall());
        if (!isDownstreamEnabled()) {
            builder.healthy()
                    .withMessage("Downstream host disabled");
        } else if (receiveDataConfigProvider.get().getReceiptCheckMode() != ReceiptCheckMode.RECEIPT_POLICY) {
            builder.healthy()
                    .withMessage("Receipt policy checking disabled by receiptCheckMode");
        } else {
            try {
                try (final Response response = getResponse(SyncInvoker::get)) {
                    HealthCheckUtils.fromResponse(builder, response, Status.OK, getFullUrl());
                }
            } catch (final Throwable e) {
                builder.unhealthy(e)
                        .withDetail("url", getFullUrl());
            }
        }
        return builder.build();
    }
}

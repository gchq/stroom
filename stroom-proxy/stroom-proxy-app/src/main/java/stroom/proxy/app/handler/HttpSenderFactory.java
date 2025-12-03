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

package stroom.proxy.app.handler;

import stroom.proxy.app.DownstreamHostConfig;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.ProxyServices;
import stroom.security.api.UserIdentityFactory;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpClientFactory;
import stroom.util.logging.LogUtil;
import stroom.util.metrics.Metrics;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.hc.client5.http.classic.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Singleton
public class HttpSenderFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpSenderFactory.class);

    private static final String USER_AGENT_FORMAT = "stroom-proxy/{} java/{}";

    private final LogStream logStream;
    private final String defaultUserAgent;
    private final UserIdentityFactory userIdentityFactory;
    private final HttpClientFactory httpClientFactory;
    private final ProxyServices proxyServices;
    private final Metrics metrics;
    private final DownstreamHostConfig downstreamHostConfig;

    @Inject
    public HttpSenderFactory(final LogStream logStream,
                             final Provider<BuildInfo> buildInfoProvider,
                             final UserIdentityFactory userIdentityFactory,
                             final HttpClientFactory httpClientFactory,
                             final Metrics metrics,
                             final ProxyServices proxyServices,
                             final DownstreamHostConfig downstreamHostConfig) {
        this.logStream = logStream;
        this.userIdentityFactory = userIdentityFactory;
        this.httpClientFactory = httpClientFactory;
        this.proxyServices = proxyServices;
        this.metrics = metrics;

        // Construct something like
        // stroom-proxy/v6.0-beta.46 java/1.8.0_181
        defaultUserAgent = LogUtil.message(USER_AGENT_FORMAT,
                buildInfoProvider.get().getBuildVersion(),
                System.getProperty("java.version"));
        this.downstreamHostConfig = downstreamHostConfig;
    }

    public HttpSender create(final ForwardHttpPostConfig forwardHttpPostConfig) {

        final String userAgentString = NullSafe.getOrElse(
                forwardHttpPostConfig.getHttpClient(),
                HttpClientConfiguration::getUserAgent,
                defaultUserAgent);

        LOGGER.info("Initialising HTTP Forwarder '{}', user agent string: [{}], url: '{}'",
                forwardHttpPostConfig.getName(),
                userAgentString,
                forwardHttpPostConfig.createForwardUrl(downstreamHostConfig));

        String name = "HttpSender";
        if (forwardHttpPostConfig.getName() != null) {
            name += "-" + forwardHttpPostConfig.getName();
        }
        name += "-" + UUID.randomUUID();

        final HttpClient httpClient = httpClientFactory.get(name, forwardHttpPostConfig.getHttpClient());
        return new HttpSender(
                logStream,
                downstreamHostConfig,
                forwardHttpPostConfig,
                userAgentString,
                userIdentityFactory,
                httpClient,
                metrics,
                proxyServices);
    }
}
